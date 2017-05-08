class FsCore(private val fdh: FileDescriptorsHandler, private val bitmapHandler: BitmapHandler,
             private val directory: Directory, private val openFileTable: OpenFileTable,
             private val hardDrive: HardDrive) {

    companion object {
        val MAX_FILE_SIZE_IN_KB = 512

        val PREDEFINED_BLOCKS_NUMBER = 5

        val BITMAP_BLOCKS_NUMBER = 4
    }

    fun write(fdIndex: Int, bytesToWriteNumber: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: file is not opened"); return
        }

        val randomArray = mutableListOf<Byte>()
        (0 until bytesToWriteNumber).forEach { randomArray.add(((it + 1) % 120).toByte()) }

        var positionInBuffer = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE

        if (oftEntry.currentPosition + bytesToWriteNumber > fdh.getFileDescriptorByIndex(fdIndex).fileLength) {
            val numberOfBlocksToAllocate = (oftEntry.currentPosition + bytesToWriteNumber -
                    fdh.getFileDescriptorByIndex(fdIndex).fileLength) / HardDriveBlock.BLOCK_SIZE + 1
            preallocateDataBlocks(numberOfBlocksToAllocate, fdIndex)
        }

        randomArray.forEach { byte ->
            oftEntry.readWriteBuffer.put(positionInBuffer++, byte)
            ++oftEntry.currentPosition

            if (positionInBuffer >= HardDriveBlock.BLOCK_SIZE) {
                val dataBlockWithIndex = getDataBlockFromFdWithIndex(fdIndex, oftEntry.currentPosition.getBlockIndexFromPosition() - 1)
                hardDrive.setBlock(dataBlockWithIndex.first, oftEntry.readWriteBuffer.array().toList())
                val nextDataBlockWithIndex = getDataBlockFromFdWithIndex(fdIndex, oftEntry.currentPosition.getBlockIndexFromPosition())
                oftEntry.readWriteBuffer.clear()
                oftEntry.readWriteBuffer.put(nextDataBlockWithIndex.second.byteArray.toByteArray())
                positionInBuffer = 0
            }
        }
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
        val inMemBuffer = mutableListOf<Byte>()

        var positionInBuffer = oftEntry.currentPosition % HardDriveBlock.BLOCK_SIZE
        var counter = 0

        while (positionInBuffer < HardDriveBlock.BLOCK_SIZE &&
                isSomethingToReadLeft(counter, bytesNumber, oftEntry, fdIndex)) {

            inMemBuffer.add(oftEntry.readWriteBuffer[positionInBuffer++])
            ++oftEntry.currentPosition
            ++counter

            if (positionInBuffer >= HardDriveBlock.BLOCK_SIZE &&
                    isSomethingToReadLeft(counter, bytesNumber, oftEntry, fdIndex)) {
                oftEntry.readWriteBuffer.clear()
                oftEntry.readWriteBuffer.put(getNextDataBlock(oftEntry.currentPosition, fdIndex))
                positionInBuffer = 0
            }
        }
        println(inMemBuffer)
    }

    fun isSomethingToReadLeft(counter: Int, bytesNumber: Int, oftEntry: OpenFileTableEntry, fdIndex: Int) =
            counter < bytesNumber && counter + oftEntry.currentPosition < fdh.getFileDescriptorByIndex(fdIndex).fileLength


    fun getNextDataBlock(positionInFile: Int, fdIndex: Int): ByteArray {
        val dataBlockNumber = positionInFile / HardDriveBlock.BLOCK_SIZE
        val nextDataBlock = getDataBlockFromFdWithIndex(fdIndex, dataBlockNumber)

        return nextDataBlock.second.byteArray.toByteArray()
    }

    fun createFile(fileName: String) {
        val freeFileDescriptorWithIndex = fdh.getFreeFileDescriptorWithIndex()
        directory.bindDirectoryEntry(fileName, freeFileDescriptorWithIndex.second)
    }

    fun removeFile(fileName: String) {
        val fileDescriptorIndex = (directory.getDirectoryEntry(fileName)?.fdIndex ?: -1)
        if (fileDescriptorIndex == -1) {
            println("Error: no such file"); return
        }

        directory.getDirectoryEntry(fileName)?.clear()
        fdh.releaseFileDescriptor(fileDescriptorIndex)
        println("Deletion: success")
    }

    fun openFile(fileName: String): Int {
        val fdIndex = directory.getDirectoryEntry(fileName)?.fdIndex
        if (fdIndex == null) {
            println("Error: no such file"); return -1
        }

        val oftEntryWithIndex = openFileTable.getOftEntryWithIndex()
        if (oftEntryWithIndex == null) {
            println("Error: no free oft entry"); return -1
        }

        oftEntryWithIndex.second.isInUse = true
        oftEntryWithIndex.second.fdIndex = fdIndex
        oftEntryWithIndex.second.readWriteBuffer.put(getDataBlockFromFdWithIndex(fdIndex, 0).second.byteArray.toByteArray())

        return oftEntryWithIndex.first
    }

    fun closeFile(fdIndex: Int) {
        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry == null) {
            println("Error: no such open file"); return
        }

        writeBufferToDisk(oftEntry)
        oftEntry.clear()
    }

    private fun writeBufferToDisk(oftEntry: OpenFileTableEntry) {
        val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(oftEntry.fdIndex).pointersBlockIndex)

        val blockIndexFromPosition = oftEntry.currentPosition.getBlockIndexFromPosition()
        val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(blockIndexFromPosition)
        hardDrive.setBlock(dataBlockIndex, oftEntry.readWriteBuffer.array().toList())
    }

    private fun getDataBlockFromFdWithIndex(fdIndex: Int, dataBlockNumber: Int): Pair<Int, HardDriveBlock> {
        val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex)
        val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(dataBlockNumber)

        return dataBlockIndex to hardDrive.getBlock(dataBlockIndex)
    }

    fun printFilesInfo() = directory.printFilesMetaInfo()
}