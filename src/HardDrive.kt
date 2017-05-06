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

    fun setBlock(blockIndex: Int, bytesToSet: ArrayList<Byte>) {
        bytesToSet.forEachIndexed { index, byte -> blocksList[blockIndex].byteArray[index] = byte }
        nullifyBlockEnding(blocksList[blockIndex], bytesToSet.size)
    }

    private fun nullifyBlockEnding(block: HardDriveBlock, truncateIndex: Int) {
        (truncateIndex until HardDriveBlock.BLOCK_SIZE).forEach { block.byteArray[0] = 0 }
    }
}

class HardDriveBlock {

    companion object {
        val BLOCK_SIZE = 1024
    }

    val byteArray = MutableList<Byte>(BLOCK_SIZE) { 0 }
}