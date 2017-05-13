/**
 * operations like read/write/lseek/close
 */

class FdBasedCommandsHandler(val openFileTable: OpenFileTable, val fdh: FileDescriptorsHandler,
                             val hardDrive: HardDrive, val bitmapHandler: BitmapHandler) {

    fun lseek(fdIndex: Int, position: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        oftEntry.updateFileSize(fdh)

        if (position > fdh.getFileDescriptorByIndex(fdIndex).fileLength) {
            println("Error: file length is less than specified position"); return
        }

        if (!isPositionWithinCurrentBlock(oftEntry.currentPosition, position)) {
            oftEntry.readDataBlockIntoBuffer(position, hardDrive, fdh)
        }

        oftEntry.currentPosition = position
        println("Lseek: new position $position")
    }


    private fun isPositionWithinCurrentBlock(currentPosition: Int, newPosition: Int) =
            currentPosition.getBlockIndexFromPosition() == newPosition.getBlockIndexFromPosition()


    fun closeFile(fdIndex: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: no such open file"); return
        }

        oftEntry.updateFileSize(fdh)
        oftEntry.writeBufferToDisk(fdh, hardDrive)
        oftEntry.clear()
    }


    fun write(fdIndex: Int, bytesToWriteNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        writeEachByte(oftEntry, bytesToWriteNumber)
    }

    private fun writeEachByte(oftEntry: OpenFileTableEntry, bytesToWriteNumber: Int) {
        val randomBytes = allocateRandomByteArray(bytesToWriteNumber)
        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE

        randomBytes.forEach { byte ->
            oftEntry.putIntoBuffer(bufferOffset++, byte)

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE) {
                if (oftEntry.isNewBlockNeeded(fdh)) {
                    oftEntry.allocateDataBlock(bitmapHandler, hardDrive, fdh)
                }

                oftEntry.iterateToNextDataBlock(fdh, hardDrive)
                bufferOffset = 0
            }
        }
    }


    private fun allocateRandomByteArray(size: Int): List<Byte> {
        val randomArray = mutableListOf<Byte>()
        (0 until size).forEach { randomArray.add(((it + 1) % 126).toByte()) }
        return randomArray.toList()
    }

    fun read(fdIndex: Int, bytesNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        oftEntry.updateFileSize(fdh)

        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE
        var counter = 0

        val inMemBuffer = mutableListOf<Byte>()

        while (bufferOffset < HardDriveBlock.BLOCK_SIZE && isSomethingToReadLeft(counter, bytesNumber, oftEntry)) {
            inMemBuffer.add(oftEntry.getFromBuffer(bufferOffset++))
            ++counter

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE && isSomethingToReadLeft(counter, bytesNumber, oftEntry)) {
                // it's possible that block should be written back to disk when bufferOffset goes beyond the limit
                // (if some modifications were done before)
                // better solution is to use modified bit
                oftEntry.iterateToNextDataBlock(fdh, hardDrive)
                bufferOffset = 0
            }
        }
        println(inMemBuffer)
    }

    fun isSomethingToReadLeft(counter: Int, bytesNumber: Int, oftEntry: OpenFileTableEntry) =
            counter < bytesNumber &&
                    counter + oftEntry.currentPosition < fdh.getFileDescriptorByIndex(oftEntry.fdIndex).fileLength
}