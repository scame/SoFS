import java.nio.ByteBuffer

/**
 * simple hard drive IO simulation
 *
 * 4096 1KB blocks totally gives us 4MB disk space
 *
 * 4 blocks reserved to bitmap (bytes are used as bits to avoid low-level bits arithmetic)
 */

class HardDrive {

    companion object {
        val BLOCKS_NUMBER = 4096
    }

    private val blocksList = MutableList(BLOCKS_NUMBER) { HardDriveBlock() }

    fun restoreFromExternalSource(bytes: ByteArray) {
        println("Got ${bytes.size} bytes")

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

fun HardDriveBlock.setPointerToFreeDataBlock(pointerBlockIndex: Int, freeDataBlockIndex: Int) {
    val highByte = freeDataBlockIndex.toByte()
    val lowByte = freeDataBlockIndex.ushr(8).toByte()

    this.bytes[pointerBlockIndex * 2] = highByte
    this.bytes[pointerBlockIndex * 2 + 1] = lowByte
}

fun HardDriveBlock.getDataBlockIndexFromPointersBlock(dataBlockIndex: Int): Int {
    val byteBuffer = ByteBuffer.allocate(2)

    val highByte = this.bytes[dataBlockIndex * 2]
    val lowByte = this.bytes[dataBlockIndex * 2 + 1]
    byteBuffer.put(highByte)
    byteBuffer.put(lowByte)

    return byteBuffer.getShort(0).toInt()
}

fun Int.getBlockIndexFromPosition() = this / HardDriveBlock.BLOCK_SIZE

class HardDriveBlock {

    companion object {
        val BLOCK_SIZE = 1024
    }

    val bytes = MutableList<Byte>(BLOCK_SIZE) { 0 }
}