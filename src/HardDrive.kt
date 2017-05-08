import java.nio.ByteBuffer

/**
 * simple hard drive IO simulation
 *
 * 4096 1KB blocks totally gives us 4MB disk space
 *
 * 4 blocks reserved to bitmap (bytes are used as bits to avoid low-level bits arithmetic)
 * 1 block goes for file descriptors table
 */

class HardDrive {

    companion object {
        val BLOCKS_NUMBER = 4096

        val BITMAP_BLOCKS_NUMBER = 4
    }

    private val blocksList = MutableList(BLOCKS_NUMBER) { HardDriveBlock() }

    fun restoreFromExternalSource(bytes: ByteArray) {
        bytes.forEachIndexed { index, byte ->
            val blockIndex = index / HardDriveBlock.BLOCK_SIZE
            val indexInsideBlock = index % HardDriveBlock.BLOCK_SIZE

            blocksList[blockIndex].bytes[indexInsideBlock] = byte
        }
    }

    fun getAllBlocksInByteForm(): ByteArray {
        return kotlin.ByteArray(BLOCKS_NUMBER * HardDriveBlock.BLOCK_SIZE) {
            val blockIndex = it / HardDriveBlock.BLOCK_SIZE
            val indexInsideBlock = it % HardDriveBlock.BLOCK_SIZE

            blocksList[blockIndex].bytes[indexInsideBlock]
        }
    }

    fun getBlock(blockIndex: Int) = blocksList[blockIndex]

    fun setBlock(blockIndex: Int, bytesToSet: List<Byte> = List<Byte>(HardDriveBlock.BLOCK_SIZE) { 0 }) {
        bytesToSet.forEachIndexed { index, byte -> blocksList[blockIndex].bytes[index] = byte }
        nullifyBlockEnding(blocksList[blockIndex], bytesToSet.size)
    }

    private fun nullifyBlockEnding(block: HardDriveBlock, truncateIndex: Int) {
        (truncateIndex until HardDriveBlock.BLOCK_SIZE).forEach { block.bytes[0] = 0 }
    }
}

class HardDriveBlock {

    companion object {
        val BLOCK_SIZE = 1024
    }

    val bytes = MutableList<Byte>(BLOCK_SIZE) { 0 }
}

fun HardDriveBlock.setPointerToFreeDataBlock(indexInsidePointersBlock: Int, freeDataBlockIndex: Int) {
    val highByte = freeDataBlockIndex.toByte()
    val lowByte = freeDataBlockIndex.ushr(8).toByte()

    this.bytes[indexInsidePointersBlock * 2] = highByte
    this.bytes[indexInsidePointersBlock * 2 + 1] = lowByte
}

fun HardDriveBlock.getDataBlockIndexFromPointersBlock(indexInsidePointersBlock: Int): Int {
    val byteBuffer = ByteBuffer.allocate(2)

    val highByte = this.bytes[indexInsidePointersBlock * 2]
    val lowByte = this.bytes[indexInsidePointersBlock * 2 + 1]
    byteBuffer.put(highByte)
    byteBuffer.put(lowByte)

    return byteBuffer.getShort(0).toInt()
}

fun Int.getBlockIndexFromPosition() = this / HardDriveBlock.BLOCK_SIZE