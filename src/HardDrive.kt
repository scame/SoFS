import java.nio.ByteBuffer

/**
 * simple hard drive IO simulation
 *
 * 4096 64bytes blocks totally gives us 256KB of disk space
 *
 * 8 blocks reserved to bitmap
 * 1 block goes for file descriptors table
 */

class HardDrive {

    companion object {
        val BLOCKS_NUMBER = 4096
        val BITMAP_BLOCKS_NUMBER = 8
    }

    private val blocks = MutableList(BLOCKS_NUMBER) { HardDriveBlock() }

    fun restoreFromExternalSource(bytes: ByteArray) {
        bytes.forEachIndexed { index, byte ->
            val blockIndex = index / HardDriveBlock.BLOCK_SIZE
            val indexInBlock = index % HardDriveBlock.BLOCK_SIZE

            blocks[blockIndex].bytes[indexInBlock] = byte
        }
    }

    fun getAllBlocksInByteForm() = ByteArray(BLOCKS_NUMBER * HardDriveBlock.BLOCK_SIZE) {
        val blockIndex = it / HardDriveBlock.BLOCK_SIZE
        val indexInBlock = it % HardDriveBlock.BLOCK_SIZE

        blocks[blockIndex].bytes[indexInBlock]
    }

    fun getBlock(blockIndex: Int): HardDriveBlock = blocks[blockIndex]

    fun setBlock(blockIndex: Int,
                 bytes: List<Byte> = List(HardDriveBlock.BLOCK_SIZE) { 0.toByte() }) {

        bytes.forEachIndexed { index, byte ->
            blocks[blockIndex].bytes[index] = byte
        }

        nullifyBlockEnding(blocks[blockIndex], bytes.size)
    }

    private fun nullifyBlockEnding(block: HardDriveBlock, truncateIndex: Int) {
        (truncateIndex until HardDriveBlock.BLOCK_SIZE)
                .forEach { block.bytes[it] = 0 }
    }
}

class HardDriveBlock {

    companion object {
        val BLOCK_SIZE = 64
    }

    val bytes = MutableList<Byte>(BLOCK_SIZE) { 0 }
}

fun HardDriveBlock.setPointerToFreeDataBlock(indexInPointersBlock: Int,
                                             freeDataBlockIndex: Int) {
    val highByte = freeDataBlockIndex.toByte()
    val lowByte = (freeDataBlockIndex ushr 8).toByte()

    bytes[indexInPointersBlock * 2] = highByte
    bytes[indexInPointersBlock * 2 + 1] = lowByte
}

fun HardDriveBlock.getDataBlockIndexFromPointersBlock(indexInPointersBlock: Int): Int {
    val byteBuffer = ByteBuffer.allocate(2)

    val highByte = bytes[indexInPointersBlock * 2]
    val lowByte = bytes[indexInPointersBlock * 2 + 1]

    byteBuffer.put(lowByte)
    byteBuffer.put(highByte)

    return byteBuffer.getShort(0).toInt()
}

fun Int.getBlockIndexFromPosition(): Int =
        this / HardDriveBlock.BLOCK_SIZE