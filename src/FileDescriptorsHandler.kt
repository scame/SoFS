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
 *  consequently, we can have up to 1024 (block size) / 8 = 128 file descriptors
 *  and MAX_FILE_SIZE = 1024 (block size) / 2 (data block index size) = 512 pointers to 1kb data blocks = 512kb
 */

data class FileDescriptor(var fileLength: Int, var pointersBlockIndex: Int, var inUse: Boolean)

fun FileDescriptor.clear() {
    this.inUse = false
    this.fileLength = 0
    this.pointersBlockIndex = -1
}

class FileDescriptorsHandler(private val hardDrive: HardDrive, private val bitmapHandler: BitmapHandler) {

    companion object {
        val DESCRIPTORS_BLOCK_INDEX = 4

        val FD_NUMBER = 128
    }

    private var inMemFileDescriptors: List<FileDescriptor>? = null

    fun persistDescriptors() {
        val descriptorsDataBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)

        inMemFileDescriptors?.forEachIndexed { index, fileDescriptor ->
            val fileLengthBytes = ByteBuffer.allocate(4).putInt(fileDescriptor.fileLength).array().toList()
            val pointersBlockIndex = ByteBuffer.allocate(2).putShort(fileDescriptor.pointersBlockIndex.toShort()).array().toList()
            val isUsed = if (fileDescriptor.inUse) 1.toByte() else 0.toByte()
            writeFdOnDisk(descriptorsDataBlock, index, fileLengthBytes, pointersBlockIndex, isUsed)
        }
    }


    private fun writeFdOnDisk(block: HardDriveBlock, fdIndex: Int, fileLength: List<Byte>,
                              pointersBlockIndex: List<Byte>, isUsed: Byte) {
        block.bytes[fdIndex * 8] = fileLength[0]
        block.bytes[fdIndex * 8 + 1] = fileLength[1]
        block.bytes[fdIndex * 8 + 2] = fileLength[2]
        block.bytes[fdIndex * 8 + 3] = fileLength[3]

        block.bytes[fdIndex * 8 + 4] = pointersBlockIndex[0]
        block.bytes[fdIndex * 8 + 5] = pointersBlockIndex[1]

        block.bytes[fdIndex * 8 + 6] = isUsed
    }

    fun getDataBlockFromFdWithIndex(fdIndex: Int, dataBlockNumber: Int): Pair<Int, HardDriveBlock> {
        val pointersBlock = hardDrive.getBlock(getFileDescriptorByIndex(fdIndex).pointersBlockIndex)
        val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(dataBlockNumber)

        return dataBlockIndex to hardDrive.getBlock(dataBlockIndex)
    }

    fun releaseFileDescriptor(fdIndex: Int) {
        val fd = getFileDescriptors()[fdIndex]
        val pointersBlock = hardDrive.getBlock(fd.pointersBlockIndex)

        var numberOfDataBlocksUsed = fd.fileLength / HardDriveBlock.BLOCK_SIZE
        if (fd.fileLength % HardDriveBlock.BLOCK_SIZE != 0) ++numberOfDataBlocksUsed

        (0 until numberOfDataBlocksUsed).forEach {
            val associatedDataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(it)
            hardDrive.setBlock(associatedDataBlockIndex)
            bitmapHandler.changeBlockInUseState(associatedDataBlockIndex, false)
        }

        fd.clear()
    }

    fun getFileDescriptorByIndex(fdIndex: Int) = getFileDescriptors()[fdIndex]

    fun getFreeFileDescriptorWithIndex(): Pair<Int, FileDescriptor>? {
        val freeFdIndex = getFileDescriptors().indexOfFirst { !it.inUse }
        if (freeFdIndex == -1) return null

        return freeFdIndex to getFileDescriptors()[freeFdIndex]
    }

    private fun getFileDescriptors(): List<FileDescriptor> {
        if (inMemFileDescriptors == null) {
            inMemFileDescriptors = getFileDescriptorsFromDisk()
        }
        return inMemFileDescriptors ?: listOf()
    }

    private fun getFileDescriptorsFromDisk(): List<FileDescriptor> {
        val fileDescriptorsBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)
        val fileDescriptorsList = mutableListOf<FileDescriptor>()

        (0 until FD_NUMBER).forEach {
            val fileDescriptor = parseFdFromBytesSequence(fileDescriptorsBlock.bytes.subList(it * 8, it * 8 + 8))
            fileDescriptorsList.add(fileDescriptor)
        }

        return fileDescriptorsList
    }

    private fun parseFdFromBytesSequence(eightBytesList: List<Byte>): FileDescriptor {
        val fileLength = readLengthValue(eightBytesList.subList(0, 4))
        val pointersBlockIndex = readBlockIndex(eightBytesList.subList(4, 6))
        val isUsed = readIsUsedValue(eightBytesList[6])

        return FileDescriptor(fileLength, pointersBlockIndex, isUsed)
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