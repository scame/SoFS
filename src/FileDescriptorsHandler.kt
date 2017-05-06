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
 *  and MAX_FILE_SIZE = 1024 (block size) / 16 (data block index) = 64 pointers to 1kb data blocks = 64kb
 */

data class FileDescriptor(var fileLength: Int, var pointersBlockIndex: Int, var inUse: Boolean)

class FileDescriptorsHandler(val hardDrive: HardDrive) {

    companion object {
        val DESCRIPTORS_BLOCK_INDEX = 4

        val FD_NUMBER = 128
    }

    fun getFreeFileDescriptor() = getFileDescriptors().first() { !it.inUse }

    fun getUsedFileDescriptors() = getFileDescriptors().filter { it.inUse }

    private fun getFileDescriptors(): List<FileDescriptor> {
        val fileDescriptorsBlock = hardDrive.getBlock(DESCRIPTORS_BLOCK_INDEX)
        val fileDescriptorsList = arrayListOf<FileDescriptor>()

        (0 until FD_NUMBER).forEach {
            val fileDescriptor = parseFdFromBytesSequence(fileDescriptorsBlock.blockArray.subList(it * 8, it * 8 + 8))
            fileDescriptorsList.add(fileDescriptor)
        }

        return fileDescriptorsList
    }

    private fun parseFdFromBytesSequence(eightBytesList: MutableList<Byte>): FileDescriptor {
        val fileLength = readLengthValue(eightBytesList.subList(0, 4))
        val pointersBlockIndex = readBlockIndex(eightBytesList.subList(4, 6))
        val isUsed = readIsUsedValue(eightBytesList[7])

        return FileDescriptor(fileLength, pointersBlockIndex, isUsed)
    }

    private fun readLengthValue(fourBytesList: MutableList<Byte>): Int {
        val byteBuffer = ByteBuffer.allocate(4)
        byteBuffer.put(fourBytesList[0])
        byteBuffer.put(fourBytesList[1])
        byteBuffer.put(fourBytesList[2])
        byteBuffer.put(fourBytesList[3])

        return byteBuffer.getInt(0)
    }

    private fun readBlockIndex(twoBytesList: MutableList<Byte>): Int {
        val byteBuffer = ByteBuffer.allocate(2)
        byteBuffer.put(twoBytesList[0])
        byteBuffer.put(twoBytesList[1])

        return byteBuffer.getShort(0).toInt()
    }

    private fun readIsUsedValue(oneByteVal: Byte) = oneByteVal.toInt() != 0
}