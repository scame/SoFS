import java.util.*

class BitmapHandler(val hardDrive: HardDrive) {

    fun getFreeBlockWithIndex(): Pair<HardDriveBlock, Int> {
        var hardDriveBlock: HardDriveBlock? = null
        var blockAbsoluteIndex: Int? = null

        kotlin.run runLabel@ {
            (0 until FsCore.BITMAP_BLOCKS_NUMBER).forEach { blockIndex ->
                val bitmapBlock = hardDrive.getBlock(blockIndex)

                bitmapBlock.bytes.forEachIndexed { indexInsideBlock, byte ->
                    if (byte.toInt() == 0) {
                        blockAbsoluteIndex = HardDriveBlock.BLOCK_SIZE * blockIndex + indexInsideBlock
                        hardDriveBlock = hardDrive.getBlock(blockAbsoluteIndex ?: 0)
                        return@runLabel
                    }
                }
            }
        }

        if (hardDriveBlock == null || blockAbsoluteIndex == null) throw NoSuchElementException()

        return hardDriveBlock!! to blockAbsoluteIndex!!
    }

    fun changeBlockInUseState(blockIndex: Int, isInUse: Boolean) {
        val bitmapIndexPair = findBitmapIndexPair(blockIndex)
        hardDrive.getBlock(bitmapIndexPair.first).bytes[bitmapIndexPair.second]= if (isInUse) 1 else 0
    }

    private fun findBitmapIndexPair(blockIndex: Int): Pair<Int, Int> {
        val bitmapBlockIndex = blockIndex / HardDriveBlock.BLOCK_SIZE
        val indexInBitmapBlock = blockIndex % HardDriveBlock.BLOCK_SIZE

        return bitmapBlockIndex to indexInBitmapBlock
    }
}