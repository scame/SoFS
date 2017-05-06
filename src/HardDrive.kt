/**
 * simple hard drive IO simulation
 *
 * 4096 1KB blocks totally gives us 4MB disk space
 */

class HardDrive {

    companion object {
        val BLOCKS_NUMBER = 4096
    }

    private val blocksList = ArrayList<HardDriveBlock>(BLOCKS_NUMBER)

    fun getBlock(blockIndex: Int) = blocksList[blockIndex]

    fun setBlock(blockIndex: Int, bytesToSet: ArrayList<Byte>) {
        bytesToSet.forEachIndexed { index, byte -> blocksList[blockIndex].blockArray[index] = byte }
        nullifyBlockEnding(blocksList[blockIndex], bytesToSet.size)
    }

    private fun nullifyBlockEnding(block: HardDriveBlock, truncateIndex: Int) {
        (truncateIndex until HardDriveBlock.BLOCK_SIZE).forEach { block.blockArray[it] = 0 }
    }
}

class HardDriveBlock {

    companion object {
        val BLOCK_SIZE = 1024
    }

    val blockArray = ArrayList<Byte>(BLOCK_SIZE)
}