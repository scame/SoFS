import java.nio.ByteBuffer

/**
 * OFT size is limited to 10 values (only 10 files can be opened at the same time)
 */

data class OpenFileTableEntry(val readWriteBuffer: ByteBuffer,
                              var currentPosition: Int = 0,
                              var fdIndex: Int = -1,
                              var isInUse: Boolean = false,
                              var modified: Boolean = false)

fun OpenFileTableEntry.updateFileSize(fdh: FileDescriptorsModel) {
    fdh.getFdByIndex(fdIndex).fileLength = currentPosition
}

fun OpenFileTableEntry.allocateDataBlock(bitmapModel: BitmapModel, fdh: FileDescriptorsModel) {
    val fd = fdh.getFdByIndex(fdIndex)

    val freeBlockIndex = bitmapModel.getFreeBlockWithIndex().index

    fd.dataBlockIndexes.add(freeBlockIndex)
    bitmapModel.changeBlockInUseState(freeBlockIndex, true)
}

fun OpenFileTableEntry.isFileFull(): Boolean {
    println("$currentPosition ${FileDescriptorsModel.MAX_FILE_SIZE}")
    return currentPosition >= FileDescriptorsModel.MAX_FILE_SIZE
}

fun OpenFileTableEntry.isNewBlockNeeded(fdh: FileDescriptorsModel): Boolean {
    val fd = fdh.getFdByIndex(fdIndex)
    return currentPosition >= fd.fileLength
}

fun OpenFileTableEntry.readDataBlockIntoBuffer(newPosition: Int,
                                               hardDrive: HardDrive,
                                               fdh: FileDescriptorsModel) {

    if (modified) writeBufferToDisk(fdh, hardDrive)

    val nextPosition = newPosition.getBlockIndexFromPosition()
    val nextDataBlock = fdh.getDataBlockFromFdWithIndex(fdIndex, nextPosition).value

    rewriteBuffer(nextDataBlock)
}

fun OpenFileTableEntry.iterateToNextDataBlock(hardDrive: HardDrive,
                                              fdh: FileDescriptorsModel) =
        readDataBlockIntoBuffer(currentPosition, hardDrive, fdh)

fun OpenFileTableEntry.writeBufferToDisk(fdh: FileDescriptorsModel,
                                         hardDrive: HardDrive) {

    val fd = fdh.getFdByIndex(fdIndex)

    val blockIndex = currentPosition.getBlockIndexFromPosition()
    hardDrive.setBlock(fd.dataBlockIndexes[blockIndex], readWriteBuffer.array().toList())

    println("written successfully")
}

fun OpenFileTableEntry.clear() {
    readWriteBuffer.clear()
    currentPosition = 0
    fdIndex = -1
    isInUse = false
    modified = false
}

fun OpenFileTableEntry.rewriteBuffer(hardDriveBlock: HardDriveBlock) {
    readWriteBuffer.clear()
    readWriteBuffer.put(hardDriveBlock.bytes.toByteArray())
}

fun OpenFileTableEntry.putIntoBuffer(bufferOffset: Int, byte: Byte) {
    readWriteBuffer.put(bufferOffset, byte)
    ++currentPosition
}

fun OpenFileTableEntry.getFromBuffer(bufferOffset: Int): Byte {
    ++currentPosition
    return readWriteBuffer.get(bufferOffset)
}

class OpenFileTable {

    companion object {
        val OFT_SIZE = 10
    }

    private val entries = mutableListOf<OpenFileTableEntry>()

    init {
        entries.addAll(List(OFT_SIZE) {
            OpenFileTableEntry(ByteBuffer.allocate(HardDriveBlock.BLOCK_SIZE))
        })
    }

    fun getOftEntryByFdIndex(fdIndex: Int): OpenFileTableEntry? =
            entries.find { it.fdIndex == fdIndex }

    fun getFreeOftEntryWithIndex(): IndexedValue<OpenFileTableEntry>? =
            entries.withIndex().firstOrNull { !it.value.isInUse }

    fun isFileOpen(fdIndex: Int): Boolean =
        getOftEntryByFdIndex(fdIndex)?.isInUse ?: false
}