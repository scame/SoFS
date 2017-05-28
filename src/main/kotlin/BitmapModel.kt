import java.util.*
import kotlin.experimental.and
import kotlin.experimental.or

class BitmapModel(val hardDrive: HardDrive) {

    init {
        for (i in 0 until HardDrive.BITMAP_BLOCKS_NUMBER)
            changeBlockInUseState(i, true)

        changeBlockInUseState(FileDescriptorsModel.DESCRIPTORS_BLOCK_INDEX, true)
    }

    fun getFreeBlockWithIndex(): IndexedValue<HardDriveBlock> {
        for (blockIndex in 0 until HardDrive.BITMAP_BLOCKS_NUMBER) {
            val bitmapBlock = hardDrive.getBlock(blockIndex)

            bitmapBlock.bytes.forEachIndexed { index, byte ->
                (0 until 8).forEach { bitPosition ->
                    if (!isBitSet(byte, bitPosition)) {
                        val indexInBlock = index * 8 + bitPosition
                        val blockAbsoluteIndex = HardDriveBlock.BLOCK_SIZE * blockIndex * 8 + indexInBlock
                        val hardDriveBlock = hardDrive.getBlock(blockAbsoluteIndex)

                        return IndexedValue(blockAbsoluteIndex, hardDriveBlock)
                    }
                }
            }
        }

        throw NoSuchElementException()
    }

    private fun isBitSet(byte: Byte, position: Int) =
            (byte.toInt() ushr (7 - position)).and(1) == 1

    fun changeBlockInUseState(blockIndex: Int, isInUse: Boolean) {
        val bitmapIndexPair = findBitmapIndexPair(blockIndex)

        val byteToChange = hardDrive.getBlock(bitmapIndexPair.first).bytes[bitmapIndexPair.second / 8]
        if (isInUse) {
            hardDrive.getBlock(bitmapIndexPair.first).bytes[bitmapIndexPair.second / 8] =
                    byteToChange or ((1 shl (7 - bitmapIndexPair.second % 8)).toByte())
        } else {
            hardDrive.getBlock(bitmapIndexPair.first).bytes[bitmapIndexPair.second / 8] =
                    byteToChange and ((1 shl (7 - bitmapIndexPair.second % 8)).inv().toByte())
        }
    }

    private fun findBitmapIndexPair(blockIndex: Int): Pair<Int, Int> {
        val blockSizeInBits = HardDriveBlock.BLOCK_SIZE * 8
        val bitmapBlockIndex = blockIndex / blockSizeInBits
        val indexInBitmapBlock = blockIndex % blockSizeInBits

        return bitmapBlockIndex to indexInBitmapBlock
    }
}
