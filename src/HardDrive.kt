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

    fun getBlock(blockIndex: Int) = blocksList[blockIndex]

    fun setBlock(blockIndex: Int, bytesToSet: List<Byte> = List<Byte>(HardDriveBlock.BLOCK_SIZE) { 0 }) {
        bytesToSet.forEachIndexed { index, byte -> blocksList[blockIndex].byteArray[index] = byte }
        nullifyBlockEnding(blocksList[blockIndex], bytesToSet.size)
    }

    private fun nullifyBlockEnding(block: HardDriveBlock, truncateIndex: Int) {
        (truncateIndex until HardDriveBlock.BLOCK_SIZE).forEach { block.byteArray[0] = 0 }
    }
}

fun HardDriveBlock.setPointerToFreeDataBlock(pointerBlockIndex: Int, freeDataBlockIndex: Int) {
    val highByte = freeDataBlockIndex.toByte()
    val lowByte = freeDataBlockIndex.ushr(8).toByte()

    this.byteArray[pointerBlockIndex * 2] = highByte
    this.byteArray[pointerBlockIndex * 2 + 1] = lowByte
}

fun HardDriveBlock.getDataBlockIndexFromPointersBlock(dataBlockIndex: Int): Int {
    val byteBuffer = ByteBuffer.allocate(2)

    val highByte = this.byteArray[dataBlockIndex * 2]
    val lowByte = this.byteArray[dataBlockIndex * 2 + 1]
    byteBuffer.put(highByte)
    byteBuffer.put(lowByte)

    return byteBuffer.getShort(0).toInt()
}

fun Int.getBlockIndexFromPosition() = this / HardDriveBlock.BLOCK_SIZE

class HardDriveBlock {

    companion object {
        val BLOCK_SIZE = 1024
    }

    val byteArray = MutableList<Byte>(BLOCK_SIZE) { 0 }
}