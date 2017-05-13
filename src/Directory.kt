/**
 * fundamentally, directory is just a file, so it's size limited to 512KB
 * let's limit file name to 160bits (even if every character would take 2bytes we still can have 10chars file names)
 * fd will take 8bits and 8bits will be given to in use flag
 *
 * accordingly, directory can have up to 524288 (max file size in bytes) / 22 = 23831 entries
 */

data class DirectoryEntry(var fileName: String = "", var fdIndex: Int = -1, var isInUse: Boolean)

fun DirectoryEntry.clear() {
    this.fileName = ""
    this.fdIndex = -1
    this.isInUse = false
}

class Directory(private val fdh: FileDescriptorsHandler, private val bitmapHandler: BitmapHandler,
                private val hardDrive: HardDrive, private val openFileTable: OpenFileTable) {

    companion object {
        val MAX_ENTRIES = 23831

        val DIR_FD_INDEX = 0

        val FD_OFFSET = 20
        val IN_USE_FLAG_OFFSET = 21
        val ENTRY_SIZE_IN_BYTES = 22
    }

    private lateinit var oftEntry: OpenFileTableEntry

    private val directoryEntriesList = mutableListOf<DirectoryEntry>()

    private lateinit var directoryFd: FileDescriptor

    // we assume that this call happens before any file allocations, so FD index will be 0
    fun initCleanDirectory() {
        val freeFd = fdh.getFreeFileDescriptorWithIndex()
        directoryFd = freeFd!!.second
        directoryFd.inUse = true
        assignPointersBlockToDirectoryFd()

        bindOftEntry()

        allocateEmptyDirectoryEntries()
    }

    private fun assignPointersBlockToDirectoryFd() {
        val freeDiskBlockWithIndex = bitmapHandler.getFreeBlockWithIndex()
        directoryFd.pointersBlockIndex = freeDiskBlockWithIndex.second
    }

    // should be read and parsed from disk
    private fun allocateEmptyDirectoryEntries() {
        (0 until MAX_ENTRIES).forEach {
            directoryEntriesList.add(DirectoryEntry(isInUse = false))
        }
    }


    // file descriptors must be already restored
    fun restoreFromDisk() {
        bindOftEntry()

        val numberOfDirectories = fdh.getFileDescriptorByIndex(DIR_FD_INDEX).fileLength / ENTRY_SIZE_IN_BYTES

        (0 until numberOfDirectories).forEach { dirIndex ->
            directoryEntriesList.add(parseEachDirectory(dirIndex))
        }
    }

    private fun parseEachDirectory(dirIndex: Int): DirectoryEntry {
        val parsedFileName = parseFileName(dirIndex)
        val parsedFdIndex = parseFdIndex(dirIndex)
        val parsedUsageFlag = parseUsageByte(dirIndex)

        return DirectoryEntry(parsedFileName, parsedFdIndex, parsedUsageFlag)
    }


    private fun parseFileName(dirIndex: Int): String {
        var bufferOffset = ENTRY_SIZE_IN_BYTES * dirIndex % HardDriveBlock.BLOCK_SIZE
        val fileNameBytes = mutableListOf<Byte>()
        oftEntry.readDataBlockIntoBuffer(dirIndex * ENTRY_SIZE_IN_BYTES, hardDrive, fdh)
        oftEntry.currentPosition = dirIndex * ENTRY_SIZE_IN_BYTES

        (0 until FD_OFFSET).forEach {
            fileNameBytes.add(oftEntry.getFromBuffer(bufferOffset++))

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE) {
                oftEntry.iterateToNextDataBlock(fdh, hardDrive)
                bufferOffset = 0
            }
        }

        return String(fileNameBytes.toByteArray())
    }

    private fun parseFdIndex(dirIndex: Int): Int {
        val bufferOffset = ENTRY_SIZE_IN_BYTES * dirIndex % HardDriveBlock.BLOCK_SIZE
        oftEntry.readDataBlockIntoBuffer(dirIndex * ENTRY_SIZE_IN_BYTES + FD_OFFSET, hardDrive, fdh)

        return oftEntry.readWriteBuffer[bufferOffset].toInt()
    }

    private fun parseUsageByte(dirIndex: Int): Boolean {
        val bufferOffset = ENTRY_SIZE_IN_BYTES * dirIndex % HardDriveBlock.BLOCK_SIZE
        oftEntry.readDataBlockIntoBuffer(dirIndex * ENTRY_SIZE_IN_BYTES + IN_USE_FLAG_OFFSET, hardDrive, fdh)

        return oftEntry.readWriteBuffer[bufferOffset].toInt() != 0
    }

    private fun bindOftEntry() {
        oftEntry = openFileTable.getFreeOftEntryWithIndex()!!.second
        oftEntry.readWriteBuffer.put(fdh.getDataBlockFromFdWithIndex(DIR_FD_INDEX, 0).second.bytes.toByteArray())
        oftEntry.fdIndex = DIR_FD_INDEX
        oftEntry.isInUse = true
    }

    fun close() {
        fdh.getFileDescriptorByIndex(oftEntry.fdIndex).fileLength = oftEntry.currentPosition
        oftEntry.writeBufferToDisk(fdh, hardDrive)
        oftEntry.clear()
    }

    fun bindDirectoryEntry(fileName: String, fdIndex: Int) {
        if (directoryEntriesList.firstOrNull { it.fileName == fileName } != null) {
            println("Error: file already exists"); return
        }

        val freeDirectoryEntry = directoryEntriesList.firstOrNull { !it.isInUse }
        val freeDirectoryIndex = directoryEntriesList.indexOfFirst { !it.isInUse }
        if (freeDirectoryEntry == null) {
            println("Error: no free directory slots"); return
        }

        freeDirectoryEntry.fileName = fileName
        freeDirectoryEntry.fdIndex = fdIndex
        freeDirectoryEntry.isInUse = true

        fdh.getFileDescriptorByIndex(fdIndex).inUse = true
        fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex = bitmapHandler.getFreeBlockWithIndex().second


        persistDirectoryEntry(freeDirectoryEntry, freeDirectoryIndex)
        println("Bind: success")
    }

    private fun persistDirectoryEntry(directoryEntry: DirectoryEntry, entryIndex: Int) {
        persistFileName(directoryEntry, entryIndex)
        persistFdIndex(directoryEntry, entryIndex)
        persistInUseFlag(directoryEntry, entryIndex)
    }

    private fun persistFileName(fileDirectoryEntry: DirectoryEntry, entryIndex: Int) {
        val fileNameBytesWithZeroEnding = fileDirectoryEntry.fileName.toByteArray() + ByteArray(1) { 0 }

        val bytesOffset = entryIndex * ENTRY_SIZE_IN_BYTES
        var positionWithinBlock = bytesOffset % HardDriveBlock.BLOCK_SIZE

        fileNameBytesWithZeroEnding.forEach { byte ->
            oftEntry.putIntoBuffer(positionWithinBlock++, byte)

            if (positionWithinBlock >= HardDriveBlock.BLOCK_SIZE) {
                if (oftEntry.isNewBlockNeeded(fdh)) {
                    oftEntry.allocateDataBlock(bitmapHandler, hardDrive, fdh)
                }

                oftEntry.iterateToNextDataBlock(fdh, hardDrive)
                positionWithinBlock = 0
            }
        }
    }

    private fun persistFdIndex(fileDirectoryEntry: DirectoryEntry, entryIndex: Int) {
        val fdIndexByte = fileDirectoryEntry.fdIndex.toByte()

        val bytesOffset = entryIndex * ENTRY_SIZE_IN_BYTES + FD_OFFSET
        val positionWithinBlock = bytesOffset % HardDriveBlock.BLOCK_SIZE

        oftEntry.putIntoBuffer(positionWithinBlock, fdIndexByte)
        if (positionWithinBlock >= HardDriveBlock.BLOCK_SIZE) {
            oftEntry.iterateToNextDataBlock(fdh, hardDrive)
        }
    }

    private fun persistInUseFlag(fileDirectoryEntry: DirectoryEntry, entryIndex: Int) {
        val inUseFlagByte = if (fileDirectoryEntry.isInUse) 1.toByte() else 0.toByte()

        val bytesOffset = entryIndex * ENTRY_SIZE_IN_BYTES + IN_USE_FLAG_OFFSET
        val positionWithinBlock = bytesOffset % HardDriveBlock.BLOCK_SIZE

        oftEntry.putIntoBuffer(positionWithinBlock, inUseFlagByte)
        if (positionWithinBlock >= HardDriveBlock.BLOCK_SIZE) {
            oftEntry.iterateToNextDataBlock(fdh, hardDrive)
        }
    }

    fun getDirectoryEntry(fileName: String) = directoryEntriesList.firstOrNull { it.fileName == fileName }

    fun printFilesMetaInfo() {
        directoryEntriesList.filter { it.isInUse }.forEach {
            val fileLength = fdh.getFileDescriptorByIndex(it.fdIndex).fileLength
            println("file name: ${it.fileName}; file length (bytes): $fileLength")
        }
    }
}