

class BitmapHandler(val hardDrive: HardDrive) {

    fun getFreeBlock(): HardDriveBlock? {
        var hardDriveBlock: HardDriveBlock? = null

        kotlin.run runLabel@ {
            (0 until FsCore.BITMAP_BLOCKS_NUMBER).forEach { blockIndex ->
                val bitmapBlock = hardDrive.getBlock(blockIndex)

                bitmapBlock.byteArray.forEachIndexed { indexInsideBlock, byte ->
                    if (byte.toInt() == 0) {
                        val currentBlockIndex = HardDriveBlock.BLOCK_SIZE * blockIndex + indexInsideBlock
                        hardDriveBlock = hardDrive.getBlock(currentBlockIndex)
                        return@runLabel
                    }
                }
            }
        }

        return hardDriveBlock
    }

    fun changeBlockInUseState(blockIndex: Int, isInUse: Boolean) {
        val bitmapIndexPair = findBitmapIndexPair(blockIndex)
        hardDrive.getBlock(bitmapIndexPair.first).byteArray[bitmapIndexPair.second]= if (isInUse) 1 else 0
    }

    private fun findBitmapIndexPair(blockIndex: Int): Pair<Int, Int> {
        val bitmapBlockIndex = blockIndex / HardDriveBlock.BLOCK_SIZE
        val indexInBitmapBlock = blockIndex % HardDriveBlock.BLOCK_SIZE

        return bitmapBlockIndex to indexInBitmapBlock
    }
}