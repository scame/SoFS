/**
 * operations like read/write/lseek/close
 */

class FdBasedCommandsHandler(val openFileTable: OpenFileTable,
                             val fdh: FileDescriptorsModel,
                             val hardDrive: HardDrive,
                             val bitmapModel: BitmapModel) {

    fun lseek(fdIndex: Int, position: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        val fd = fdh.getFdByIndex(fdIndex)
        if (fd.fileLength < position) {
            println("Error: file length is smaller than specified position"); return
        }

        if (!isPositionWithinCurrentBlock(oftEntry.currentPosition, position))
            oftEntry.readDataBlockIntoBuffer(position, hardDrive, fdh)

        oftEntry.currentPosition = position
        println("Lseek-$fdIndex: new position $position")
    }

    private fun isPositionWithinCurrentBlock(currentPosition: Int, newPosition: Int) =
            currentPosition.getBlockIndexFromPosition() ==
                    newPosition.getBlockIndexFromPosition()

    fun closeFile(fdIndex: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: no such open file"); return
        }

        if (oftEntry.modified) oftEntry.writeBufferToDisk(fdh, hardDrive)
        oftEntry.clear()
    }

    fun write(fdIndex: Int, bytesToWriteNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        oftEntry.modified = true

        writeEachByte(oftEntry, bytesToWriteNumber)
        oftEntry.updateFileSize(fdh)
    }

    private fun writeEachByte(oftEntry: OpenFileTableEntry, bytesToWriteNumber: Int) {
        val randomBytes = allocateRandomByteArray(bytesToWriteNumber)
        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE

        for (byte in randomBytes) {
            oftEntry.putIntoBuffer(bufferOffset++, byte)


            if (bufferOffset == HardDriveBlock.BLOCK_SIZE - 1) {
                if (oftEntry.isFileFull()) {
                    println("Error: file size out of bounds"); break
                }

                if (oftEntry.isNewBlockNeeded(fdh))
                    oftEntry.allocateDataBlock(bitmapModel, fdh)

                oftEntry.iterateToNextDataBlock(hardDrive, fdh)
                bufferOffset = 0
            }
        }
    }

    private fun allocateRandomByteArray(size: Int) =
        List(size) { ((it + 1) % 126).toByte() }

    fun read(fdIndex: Int, bytesNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

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
        println(inMemBuffer)
    }

    fun isSomethingLeftToRead(counter: Int, bytesNumber: Int, oftEntry: OpenFileTableEntry): Boolean =
            counter < bytesNumber &&
                    oftEntry.currentPosition < fdh.getFdByIndex(oftEntry.fdIndex).fileLength
}