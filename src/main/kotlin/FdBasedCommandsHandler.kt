/**
 * operations like read/write/lseek/close
 */

class FdBasedCommandsHandler(val openFileTable: OpenFileTable,
                             val fdh: FileDescriptorsModel,
                             val hardDrive: HardDrive,
                             val bitmapModel: BitmapModel) {

    companion object {
        fun allocateByteArray(size: Int) =
                List(size) { ((it + 1) % 126).toByte() }
    }

    fun read(fdIndex: Int, bytesNumber: Int): Message {

        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex) ?: return NoFile

        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE
        var counter = 0

        val inMemBuffer = mutableListOf<Byte>()

        while (bufferOffset < HardDriveBlock.BLOCK_SIZE && isSomethingLeftToRead(counter, bytesNumber, oftEntry)) {
            inMemBuffer.add(oftEntry.getFromBuffer(bufferOffset++))
            counter += 1

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE && isSomethingLeftToRead(counter, bytesNumber, oftEntry)) {
                oftEntry.iterateToNextDataBlock(hardDrive, fdh)
                bufferOffset = 0
            }
        }

        return Buffer(inMemBuffer)
    }

    fun write(fdIndex: Int, bytesToWriteNumber: Int): Message {

        if (bytesToWriteNumber > FileDescriptorsModel.MAX_FILE_SIZE)
            return FileTooLarge

        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex) ?: return NoFile

        oftEntry.modified = true

        writeEachByte(oftEntry, bytesToWriteNumber)
        oftEntry.updateFileSize(fdh)

        return Success(bytesToWriteNumber)
    }

    fun lseek(fdIndex: Int, position: Int): Message {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex) ?: return NoFile

        val fd = fdh.getFdByIndex(fdIndex)
        if (fd.fileLength < position)
            return SeekOutOfBounds

        if (!isPositionWithinCurrentBlock(oftEntry.currentPosition, position))
            oftEntry.readDataBlockIntoBuffer(position, hardDrive, fdh)

        oftEntry.currentPosition = position
        return Success(position)
    }

    fun closeFile(fdIndex: Int): Message {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex) ?: return NoFile

        if (oftEntry.modified) oftEntry.writeBufferToDisk(fdh, hardDrive)
        oftEntry.clear()

        return Success(fdIndex)
    }

    private fun isPositionWithinCurrentBlock(currentPosition: Int, newPosition: Int) =
            currentPosition.getBlockIndexFromPosition() ==
                    newPosition.getBlockIndexFromPosition()

    private fun writeEachByte(oftEntry: OpenFileTableEntry, bytesToWriteNumber: Int) {
        val randomBytes = allocateByteArray(bytesToWriteNumber)
        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE

        for (byte in randomBytes) {
            if (bufferOffset == HardDriveBlock.BLOCK_SIZE) {
                if (oftEntry.isNewBlockNeeded(fdh))
                    oftEntry.allocateDataBlock(bitmapModel, fdh)

                oftEntry.iterateToNextDataBlock(hardDrive, fdh)
                bufferOffset = 0
            }

            oftEntry.putIntoBuffer(bufferOffset++, byte)
        }
    }

    fun isSomethingLeftToRead(counter: Int, bytesNumber: Int, oftEntry: OpenFileTableEntry): Boolean =
            counter < bytesNumber &&
                    oftEntry.currentPosition < fdh.getFdByIndex(oftEntry.fdIndex).fileLength
}