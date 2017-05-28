import java.nio.ByteBuffer

/**
 *  all file descriptors go into one disk block
 *
 *  each descriptor contains:
 *                           one 8bit value (file len in bytes)
 *                           three 16bit values (data block indexes)
 *                           one 8bit val (used/unused fd)
 *
 *  consequently, we can have up to 64 (block size) / 8 = 8 file descriptors
 *  and MAX_FILE_SIZE = 64 (block size) * 3 (data blocks number) = 192
 */

data class FileDescriptor(var fileLength: Int,
                          var dataBlockIndices: MutableList<Int> =
                          MutableList(FileDescriptorsModel.DATA_BLOCKS_NUMBER) { -1 },
                          var inUse: Boolean)

fun FileDescriptor.clear() {
    inUse = false
    fileLength = 0
    (0 until dataBlockIndices.size).forEach { dataBlockIndices[it] = -1 }
}

class FileDescriptorsModel(private val hardDrive: HardDrive,
                           private val bitmapModel: BitmapModel) {
    companion object {
        val FD_NUMBER = 8
        val FD_SIZE_IN_BYTES = 8
        val DESCRIPTORS_BLOCK_INDEX = 8

        val DATA_BLOCKS_NUMBER = 3
        val MAX_FILE_SIZE = HardDriveBlock.BLOCK_SIZE * DATA_BLOCKS_NUMBER
    }

    private val fileDescriptors by lazy { getFileDescriptorsFromDisk() }

    fun persistDescriptors() {
        val descriptorsDataBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)

        fileDescriptors.forEachIndexed { index, (fileLength, dataBlockIndexes, inUse) ->
            val fileLengthBytes = fileLength.toByte()

            val dataBlockIndexesBytes = mutableListOf<Byte>()
            (0 until DATA_BLOCKS_NUMBER).forEach {
                val buffer = ByteBuffer.allocate(2)
                buffer.putShort(dataBlockIndexes[it].toShort())
                dataBlockIndexesBytes += buffer.array().toMutableList()
            }

            val isUsed: Byte = if (inUse) 1 else 0

            writeFdOnDisk(descriptorsDataBlock, index, fileLengthBytes, dataBlockIndexesBytes.toList(), isUsed)
        }
    }

    private fun writeFdOnDisk(block: HardDriveBlock, fdIndex: Int, fileLength: Byte,
                              dataBlockIndexesBytes: List<Byte>, isUsed: Byte) {
        val fdOffset = fdIndex * FD_SIZE_IN_BYTES

        block.bytes[fdOffset] = fileLength

        dataBlockIndexesBytes.forEachIndexed { index, byte ->
            block.bytes[fdOffset + index + 1] = byte
        }

        block.bytes[fdOffset + 7] = isUsed
    }

    fun getDataBlockFromFdWithIndex(fdIndex: Int, dataBlockNumber: Int): IndexedValue<HardDriveBlock> {
        val fd = getFdByIndex(fdIndex)
        val dataBlockIndex = fd.dataBlockIndices[dataBlockNumber]

        return IndexedValue(dataBlockIndex, hardDrive.getBlock(dataBlockIndex))
    }

    fun releaseFd(fdIndex: Int) {
        val fd = getFdByIndex(fdIndex)

        (0 until DATA_BLOCKS_NUMBER).forEach {
            val dataBlockIndex = fd.dataBlockIndices[it]

            if (dataBlockIndex != -1) {
                hardDrive.setBlock(dataBlockIndex)
                bitmapModel.changeBlockInUseState(dataBlockIndex, false)
                fd.dataBlockIndices[it] = -1
            }
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
        val dataBlockIndex = bitmapModel.getFreeBlockWithIndex().index
        bitmapModel.changeBlockInUseState(dataBlockIndex, true)

        val fd = getFdByIndex(fdIndex)
        fd.inUse = true

        val index = fd.dataBlockIndices.withIndex().first { it.value == 0 }.index
        fd.dataBlockIndices[index] = dataBlockIndex
    }

    private fun getFileDescriptorsFromDisk(): List<FileDescriptor> {
        val fileDescriptorsBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)

        return List(FD_NUMBER, { it ->
            val bytes = fileDescriptorsBlock.bytes.subList(
                    it * FD_SIZE_IN_BYTES, it * FD_SIZE_IN_BYTES + FD_SIZE_IN_BYTES)
            parseFdFromBytes(bytes)
        })
    }

    private fun parseFdFromBytes(eightBytesList: List<Byte>): FileDescriptor {
        val fileLength = eightBytesList[0]
        val dataBlockIndexes = readBlockIndexes(eightBytesList.subList(1, 7))
        val inUse = readIsUsedValue(eightBytesList[7])

        return FileDescriptor(fileLength.toInt(), dataBlockIndexes, inUse)
    }

    private fun readBlockIndexes(sixBytesList: List<Byte>) = MutableList(FileDescriptorsModel.DATA_BLOCKS_NUMBER) {
        fromTwoBytesToInt(listOf(sixBytesList[it * 2], sixBytesList[it * 2 + 1]))
    }

    private fun fromTwoBytesToInt(twoBytesList: List<Byte>): Int {
        val byteBuffer = ByteBuffer.allocate(2)
        byteBuffer.put(twoBytesList[0])
        byteBuffer.put(twoBytesList[1])

        return byteBuffer.getShort(0).toInt()
    }

    private fun readIsUsedValue(oneByteVal: Byte) = oneByteVal.toInt() != 0
}
