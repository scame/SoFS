import java.util.*

class BitmapModel(val hardDrive: HardDrive) {

    init {
        for (i in 0 until HardDrive.BITMAP_BLOCKS_NUMBER)
            changeBlockInUseState(i, true)

        changeBlockInUseState(FileDescriptorsModel.DESCRIPTORS_BLOCK_INDEX, true)
    }

    fun getFreeBlockWithIndex(): IndexedValue<HardDriveBlock> {
        var hardDriveBlock: HardDriveBlock? = null
        var blockAbsoluteIndex: Int? = null

        for (blockIndex in 0 until HardDrive.BITMAP_BLOCKS_NUMBER) {
            val bitmapBlock = hardDrive.getBlock(blockIndex)

            val indexInBlock = bitmapBlock.bytes.withIndex()
                    .firstOrNull { it.value.toInt() == 0}?.index

            if (indexInBlock != null) {
                blockAbsoluteIndex = HardDriveBlock.BLOCK_SIZE * blockIndex + indexInBlock
                hardDriveBlock = hardDrive.getBlock(blockAbsoluteIndex)
                return IndexedValue(blockAbsoluteIndex, hardDriveBlock)
            }
        }

        if (hardDriveBlock == null || blockAbsoluteIndex == null)
            throw NoSuchElementException()

        return IndexedValue(blockAbsoluteIndex, hardDriveBlock)
    }

    fun changeBlockInUseState(blockIndex: Int, isInUse: Boolean) {
        val bitmapIndexPair = findBitmapIndexPair(blockIndex)
        hardDrive.getBlock(bitmapIndexPair.first)
                .bytes[bitmapIndexPair.second] = if (isInUse) 1 else 0
    }

    private fun findBitmapIndexPair(blockIndex: Int): Pair<Int, Int> {
        val bitmapBlockIndex = blockIndex / HardDriveBlock.BLOCK_SIZE
        val indexInBitmapBlock = blockIndex % HardDriveBlock.BLOCK_SIZE

        return bitmapBlockIndex to indexInBitmapBlock
    }
}