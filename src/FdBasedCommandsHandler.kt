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

        if (position > fdh.getFileDescriptorByIndex(fdIndex).fileLength) {
            println("Error: file length is less than specified position"); return
        }

        if (!isPositionWithinCurrentBlock(oftEntry.currentPosition, position)) {
            updateBufferData(oftEntry, position)
        }

        oftEntry.currentPosition = position
        println("Lseek: new position $position")
    }

    private fun updateBufferData(oftEntry: OpenFileTableEntry, newPosition: Int) {
        val currentBlockWithIndex = fdh.getDataBlockFromFdWithIndex(oftEntry.fdIndex, oftEntry.currentPosition.getBlockIndexFromPosition())
        hardDrive.setBlock(currentBlockWithIndex.first, oftEntry.readWriteBuffer.array().toList())
        val newDataBlockWithIndex = fdh.getDataBlockFromFdWithIndex(oftEntry.fdIndex, newPosition.getBlockIndexFromPosition())

        oftEntry.rewriteBuffer(newDataBlockWithIndex.second)
    }

    private fun isPositionWithinCurrentBlock(currentPosition: Int, newPosition: Int) =
            currentPosition.getBlockIndexFromPosition() == newPosition.getBlockIndexFromPosition()



    fun closeFile(fdIndex: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: no such open file"); return
        }

        fdh.getFileDescriptorByIndex(fdIndex).fileLength = oftEntry.currentPosition
        writeBufferToDisk(oftEntry)
        oftEntry.clear()
    }

    private fun writeBufferToDisk(oftEntry: OpenFileTableEntry) {
        val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(oftEntry.fdIndex).pointersBlockIndex)

        val blockIndexFromPosition = oftEntry.currentPosition.getBlockIndexFromPosition()
        val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(blockIndexFromPosition)
        hardDrive.setBlock(dataBlockIndex, oftEntry.readWriteBuffer.array().toList())
    }

    fun write(fdIndex: Int, bytesToWriteNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        processBlocksPreallocation(oftEntry, bytesToWriteNumber)
        writeEachByte(oftEntry, bytesToWriteNumber)
    }

    private fun processBlocksPreallocation(oftEntry: OpenFileTableEntry, bytesToWriteNumber: Int) {
        val currBlockOffset = fdh.getFileDescriptorByIndex(oftEntry.fdIndex).fileLength % HardDriveBlock.BLOCK_SIZE
        val newDataOffset = bytesToWriteNumber % HardDriveBlock.BLOCK_SIZE

        val numberOfBlocksToAllocate = if (currBlockOffset + newDataOffset < HardDriveBlock.BLOCK_SIZE) {
            bytesToWriteNumber / HardDriveBlock.BLOCK_SIZE
        } else {
            bytesToWriteNumber / HardDriveBlock.BLOCK_SIZE + 1
        }

        preallocateDataBlocks(numberOfBlocksToAllocate, oftEntry.fdIndex)
    }

    private fun writeEachByte(oftEntry: OpenFileTableEntry, bytesToWriteNumber: Int) {
        val randomBytes = allocateRandomByteArray(bytesToWriteNumber)
        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE

        randomBytes.forEach { byte ->
            oftEntry.putIntoBuffer(bufferOffset++, byte)
            ++oftEntry.currentPosition

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE) {
                iterateToNextDataBlock(oftEntry)
                bufferOffset = 0
            }
        }
    }


    private fun iterateToNextDataBlock(oftEntry: OpenFileTableEntry) {
        val dataBlockWithIndex = fdh.getDataBlockFromFdWithIndex(oftEntry.fdIndex, oftEntry.currentPosition.getBlockIndexFromPosition() - 1)
        hardDrive.setBlock(dataBlockWithIndex.first, oftEntry.readWriteBuffer.array().toList())
        val nextDataBlockWithIndex = fdh.getDataBlockFromFdWithIndex(oftEntry.fdIndex, oftEntry.currentPosition.getBlockIndexFromPosition())

        oftEntry.rewriteBuffer(nextDataBlockWithIndex.second)
    }

    private fun allocateRandomByteArray(size: Int): List<Byte> {
        val randomArray = mutableListOf<Byte>()
        (0 until size).forEach { randomArray.add(((it + 1) % 126).toByte()) }
        return randomArray.toList()
    }

    private fun preallocateDataBlocks(numberOfBlocksToAllocate: Int, fdIndex: Int) {
        val fileLength = fdh.getFileDescriptorByIndex(fdIndex).fileLength
        var alreadyAllocated = if (fileLength == 0) 0 else fileLength / HardDriveBlock.BLOCK_SIZE + 1
        val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex)

        (0 until numberOfBlocksToAllocate).forEach {
            pointersBlock.setPointerToFreeDataBlock(++alreadyAllocated, bitmapHandler.getFreeBlockWithIndex().second)
        }
    }

    fun read(fdIndex: Int, bytesNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        var bufferOffset = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE
        var counter = 0

        val inMemBuffer = mutableListOf<Byte>()

        while (bufferOffset < HardDriveBlock.BLOCK_SIZE && isSomethingToReadLeft(counter, bytesNumber, oftEntry)) {
            inMemBuffer.add(oftEntry.readWriteBuffer[bufferOffset++])
            ++oftEntry.currentPosition
            ++counter

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE && isSomethingToReadLeft(counter, bytesNumber, oftEntry)) {
                // it's possible that block should be written back to disk when bufferOffset goes beyond the limit
                // (if some modifications were done before)
                // better solution is to use modified bit
                iterateToNextDataBlock(oftEntry)
                bufferOffset = 0
            }
        }
        println(inMemBuffer)
    }

    fun isSomethingToReadLeft(counter: Int, bytesNumber: Int, oftEntry: OpenFileTableEntry) =
            counter < bytesNumber &&
                    counter + oftEntry.currentPosition < fdh.getFileDescriptorByIndex(oftEntry.fdIndex).fileLength


    fun getNextDataBlock(oftEntry: OpenFileTableEntry): HardDriveBlock {
        val dataBlockNumber = oftEntry.currentPosition / HardDriveBlock.BLOCK_SIZE
        val nextDataBlock = fdh.getDataBlockFromFdWithIndex(oftEntry.fdIndex, dataBlockNumber)

        return nextDataBlock.second
    }
}