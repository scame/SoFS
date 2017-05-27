
import java.nio.ByteBuffer

/**
 *  all file descriptors go into one disk block
 *
 *  each descriptor contains:
 *                           one 32bit value (file len in bytes)
 *                           one 16bit value (index of a block that points to data blocks)
 *                           one 8bit val (used/unused fd)
 *                           one empty byte (to have power of two values)
 *
 *  consequently, we can have up to 64 (block size) / 8 = 8 file descriptors
 *  and MAX_FILE_SIZE = 64 (block size) / 2 (data block index size) = 32 pointers to 64bytes data blocks = 2kb
 */

data class FileDescriptor(var fileLength: Int,
                          var pointersBlockIndex: Int,
                          var inUse: Boolean)

fun FileDescriptor.clear() {
    inUse = false
    fileLength = 0
    pointersBlockIndex = -1
}

class FileDescriptorsModel(private val hardDrive: HardDrive,
                           private val bitmapModel: BitmapModel) {
    companion object {
        val FD_NUMBER = 8
        val DESCRIPTORS_BLOCK_INDEX = 8

        val PTR_BLOCK_SIZE = 64
    }

    private val fileDescriptors by lazy { getFileDescriptorsFromDisk() }

    fun persistDescriptors() {
        val descriptorsDataBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)

        fileDescriptors.forEachIndexed { index, (fileLength, pointersBlockIndex, inUse) ->
            val fileLengthBytes = ByteBuffer.allocate(4).putInt(fileLength).array().toList()
            val pointersBlockIndexBytes = ByteBuffer.allocate(2).putShort(pointersBlockIndex.toShort()).array().toList()
            val isUsed: Byte = if (inUse) 1 else 0

            writeFdOnDisk(descriptorsDataBlock, index, fileLengthBytes, pointersBlockIndexBytes, isUsed)
        }
    }

    private fun writeFdOnDisk(block: HardDriveBlock, fdIndex: Int, fileLength: List<Byte>,
                              pointersBlockIndex: List<Byte>, isUsed: Byte) {
        for (i in 0 until 4)
            block.bytes[fdIndex * 8 + i] = fileLength[i]

        block.bytes[fdIndex * 8 + 4] = pointersBlockIndex[0]
        block.bytes[fdIndex * 8 + 5] = pointersBlockIndex[1]

        block.bytes[fdIndex * 8 + 6] = isUsed
    }

    fun getDataBlockFromFdWithIndex(fdIndex: Int,
                                    dataBlockNumber: Int): IndexedValue<HardDriveBlock> {
        val fd = getFdByIndex(fdIndex)
        val pointersBlock = hardDrive.getBlock(fd.pointersBlockIndex)
        val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(dataBlockNumber)

        return IndexedValue(dataBlockIndex, hardDrive.getBlock(dataBlockIndex))
    }

    fun releaseFd(fdIndex: Int) {
        val fd = getFdByIndex(fdIndex)
        val pointersBlock = hardDrive.getBlock(fd.pointersBlockIndex)

        var numberOfDataBlocksUsed = fd.fileLength / HardDriveBlock.BLOCK_SIZE
        if (fd.fileLength % HardDriveBlock.BLOCK_SIZE != 0)
            ++numberOfDataBlocksUsed

        (0 until numberOfDataBlocksUsed).forEach {
            val associatedDataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(it)
            hardDrive.setBlock(associatedDataBlockIndex)
            bitmapModel.changeBlockInUseState(associatedDataBlockIndex, false)
        }

        fd.clear()
    }

    fun getFdByIndex(fdIndex: Int) = fileDescriptors[fdIndex]

    fun getFreeFdWithIndex(): IndexedValue<FileDescriptor>? {
        val freeFdIndex = fileDescriptors.indexOfFirst { !it.inUse }
        if (freeFdIndex == -1) return null

        return IndexedValue(freeFdIndex, getFdByIndex(freeFdIndex))
    }

    fun allocateFd(fdIndex: Int) {
        val pointersBlock = bitmapModel.getFreeBlockWithIndex()
        bitmapModel.changeBlockInUseState(pointersBlock.index, true)

        val dataBlockIndex = bitmapModel.getFreeBlockWithIndex().index
        pointersBlock.value.setPointerToFreeDataBlock(0, dataBlockIndex)
        bitmapModel.changeBlockInUseState(dataBlockIndex, true)

        val fd = getFdByIndex(fdIndex)
        fd.inUse = true
        fd.pointersBlockIndex = pointersBlock.index
    }

    private fun getFileDescriptorsFromDisk(): List<FileDescriptor> {
        val fileDescriptorsBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)

        return List(FD_NUMBER, { it ->
            val bytes = fileDescriptorsBlock.bytes.subList(it * 8, it * 8 + 8)
            parseFdFromBytes(bytes)
        })
    }

    private fun parseFdFromBytes(eightBytesList: List<Byte>): FileDescriptor {
        val fileLength = readLengthValue(eightBytesList.subList(0, 4))
        val pointersBlockIndex = readBlockIndex(eightBytesList.subList(4, 6))
        val inUse = readIsUsedValue(eightBytesList[6])

        return FileDescriptor(fileLength, pointersBlockIndex, inUse)
    }

    private fun readLengthValue(fourBytesList: List<Byte>): Int {
        val byteBuffer = ByteBuffer.allocate(4)
        byteBuffer.put(fourBytesList[0])
        byteBuffer.put(fourBytesList[1])
        byteBuffer.put(fourBytesList[2])
        byteBuffer.put(fourBytesList[3])

        return byteBuffer.getInt(0)
    }

    private fun readBlockIndex(twoBytesList: List<Byte>): Int {
        val byteBuffer = ByteBuffer.allocate(2)
        byteBuffer.put(twoBytesList[0])
        byteBuffer.put(twoBytesList[1])

        return byteBuffer.getShort(0).toInt()
    }

    private fun readIsUsedValue(oneByteVal: Byte) = oneByteVal.toInt() != 0
}
