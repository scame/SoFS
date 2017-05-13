import java.nio.ByteBuffer

/**
 * OFT size is limited to 10 values (only 10 files can be opened at the same time)
 */

data class OpenFileTableEntry(val readWriteBuffer: ByteBuffer, var currentPosition: Int,
                              var fdIndex: Int, var isInUse: Boolean)

fun OpenFileTableEntry.updateFileSize(fdh: FileDescriptorsHandler) {
    fdh.getFileDescriptorByIndex(fdIndex).fileLength = currentPosition
}

fun OpenFileTableEntry.allocateDataBlock(bitmapHandler: BitmapHandler, hardDrive: HardDrive, fdh: FileDescriptorsHandler) {
    var alreadyAllocated = if (currentPosition == 0) 0 else currentPosition / HardDriveBlock.BLOCK_SIZE
    val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex)

    val freeBlockWithIndex = bitmapHandler.getFreeBlockWithIndex()
    pointersBlock.setPointerToFreeDataBlock(++alreadyAllocated, freeBlockWithIndex.second)
    bitmapHandler.changeBlockInUseState(freeBlockWithIndex.second, true)
}

fun OpenFileTableEntry.isNewBlockNeeded(fdh: FileDescriptorsHandler): Boolean {
    val fd = fdh.getFileDescriptorByIndex(fdIndex)
    return currentPosition >= fd.fileLength
}

fun OpenFileTableEntry.iterateToNextDataBlock(fdh: FileDescriptorsHandler, hardDrive: HardDrive) {
    val dataBlockWithIndex = fdh.getDataBlockFromFdWithIndex(fdIndex, currentPosition.getBlockIndexFromPosition() - 1)
    hardDrive.setBlock(dataBlockWithIndex.first, readWriteBuffer.array().toList())
    val nextDataBlockWithIndex = fdh.getDataBlockFromFdWithIndex(fdIndex, currentPosition.getBlockIndexFromPosition())

    rewriteBuffer(nextDataBlockWithIndex.second)
}

fun OpenFileTableEntry.readDataBlockIntoBuffer(newPosition: Int, hardDrive: HardDrive, fdh: FileDescriptorsHandler) {
    val currentBlockWithIndex = fdh.getDataBlockFromFdWithIndex(fdIndex, currentPosition.getBlockIndexFromPosition())
    hardDrive.setBlock(currentBlockWithIndex.first, readWriteBuffer.array().toList())
    val newDataBlockWithIndex = fdh.getDataBlockFromFdWithIndex(fdIndex, newPosition.getBlockIndexFromPosition())

    rewriteBuffer(newDataBlockWithIndex.second)
}

fun OpenFileTableEntry.writeBufferToDisk(fdh: FileDescriptorsHandler, hardDrive: HardDrive) {
    val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex)

    val blockIndexFromPosition = currentPosition.getBlockIndexFromPosition()
    val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(blockIndexFromPosition)
    hardDrive.setBlock(dataBlockIndex, readWriteBuffer.array().toList())
}

fun OpenFileTableEntry.clear() {
    this.readWriteBuffer.clear()
    this.currentPosition = 0
    this.fdIndex = -1
    this.isInUse = false
}

fun OpenFileTableEntry.rewriteBuffer(hardDriveBlock: HardDriveBlock) {
    this.readWriteBuffer.clear()
    this.readWriteBuffer.put(hardDriveBlock.bytes.toByteArray())
}

fun OpenFileTableEntry.putIntoBuffer(bufferOffset: Int, byte: Byte) {
    this.readWriteBuffer.put(bufferOffset, byte)
    ++this.currentPosition
}

fun OpenFileTableEntry.getFromBuffer(bufferOffset: Int): Byte {
    ++this.currentPosition
    return readWriteBuffer.get(bufferOffset)
}

class OpenFileTable {

    companion object {
        val OFT_SIZE = 10
    }

    private val openFileTableEntries = mutableListOf<OpenFileTableEntry>()

    init {
        (0 until OFT_SIZE).forEach {
            openFileTableEntries.add(OpenFileTableEntry(ByteBuffer.allocate(HardDriveBlock.BLOCK_SIZE), 0, -1, false))
        }
    }

    fun getOftEntryByFdIndex(fdIndex: Int) = openFileTableEntries.firstOrNull { it.fdIndex == fdIndex }

    fun getFreeOftEntryWithIndex(): Pair<Int, OpenFileTableEntry>? {
        val oftEntry = openFileTableEntries.firstOrNull { !it.isInUse }
        val oftIndex = openFileTableEntries.indexOfFirst { !it.isInUse }

        if (oftEntry == null || oftIndex == -1) return null

        return oftIndex to oftEntry
    }
}