/**
 * fundamentally, directory is just a file, so it's size limited to 512KB
 * let's limit file name to 160bits (even if every character would take 2bytes we still can have 10chars file names)
 * fd will take 8bits and 8bits will be given to in use flag
 *
 * accordingly, directory can have up to 524288 (max file size in bytes) / 22 = 23831 entries
 */

data class DirectoryEntry(var fileName: String = "",
                          var fdIndex: Int = -1,
                          var isInUse: Boolean = false)

fun DirectoryEntry.clear() {
    fileName = ""
    fdIndex = -1
    isInUse = false
}

class Directory(private val fdh: FileDescriptorsModel,
                private val bitmapModel: BitmapModel,
                private val hardDrive: HardDrive,
                private val openFileTable: OpenFileTable) {

    companion object {
        val MAX_ENTRIES = 23831

        val DIR_FD_INDEX = 0

        val FD_OFFSET = 20
        val IN_USE_FLAG_OFFSET = FD_OFFSET + 1
        val ENTRY_SIZE_IN_BYTES = IN_USE_FLAG_OFFSET + 1
    }

    private lateinit var oftEntry: OpenFileTableEntry

    private val entries = mutableListOf<DirectoryEntry>()

    private lateinit var directoryFd: FileDescriptor

    // we assume that this call happens before any file allocations, so FD index will be 0
    fun initCleanDirectory() {
        val freeFd = fdh.getFreeFdWithIndex()
        directoryFd = freeFd!!.value
        directoryFd.inUse = true

        allocateDirectoryFd()
        bindOftEntry()
        allocateEmptyDirectoryEntries()
    }

    private fun allocateDirectoryFd() {
        val pointersBlock = bitmapModel.getFreeBlockWithIndex()
        bitmapModel.changeBlockInUseState(pointersBlock.index, true)

        val dataBlockIndex = bitmapModel.getFreeBlockWithIndex().index
        pointersBlock.value.setPointerToFreeDataBlock(0, dataBlockIndex)
        bitmapModel.changeBlockInUseState(dataBlockIndex, true)

        directoryFd.pointersBlockIndex = pointersBlock.index
    }

    private fun bindOftEntry() {
        oftEntry = openFileTable.getFreeOftEntryWithIndex()!!.value

        val dataBlock = fdh.getDataBlockFromFdWithIndex(DIR_FD_INDEX, 0).value
        oftEntry.readWriteBuffer.put(dataBlock.bytes.toByteArray())
        oftEntry.fdIndex = DIR_FD_INDEX
        oftEntry.isInUse = true
    }

    // should be read and parsed from disk
    private fun allocateEmptyDirectoryEntries(allocateFrom: Int = 0) {
        val size = MAX_ENTRIES - allocateFrom
        entries.addAll(List(size) { DirectoryEntry() })
    }

    // file descriptors must be already restored
    fun restoreFromDisk() {
        bindOftEntry()

        val fileLength = fdh.getFdByIndex(DIR_FD_INDEX).fileLength
        val numberOfDirectories = fileLength / ENTRY_SIZE_IN_BYTES
        val dirEntries = List(numberOfDirectories) { parseEachDirectory(it) }
        entries.addAll(dirEntries)

        allocateEmptyDirectoryEntries(numberOfDirectories)
    }

    private fun parseEachDirectory(dirIndex: Int): DirectoryEntry {
        oftEntry.readDataBlockIntoBuffer(dirIndex * ENTRY_SIZE_IN_BYTES, hardDrive, fdh)
        oftEntry.currentPosition = dirIndex * ENTRY_SIZE_IN_BYTES

        val parsedFileName = parseFileName(dirIndex)
        val parsedFdIndex = parseFdIndex(dirIndex)
        val parsedUsageFlag = parseUsageByte(dirIndex)

        return DirectoryEntry(parsedFileName, parsedFdIndex, parsedUsageFlag)
    }

    private fun parseFileName(dirIndex: Int): String {
        var bufferOffset = ENTRY_SIZE_IN_BYTES * dirIndex % HardDriveBlock.BLOCK_SIZE
        val fileNameBytes = mutableListOf<Byte>()

        for (i in 0 until FD_OFFSET) {
            val char = oftEntry.getFromBuffer(bufferOffset++)
            if (char == 0.toByte()) break
            else fileNameBytes.add(char)

            if (bufferOffset >= HardDriveBlock.BLOCK_SIZE) {
                oftEntry.iterateToNextDataBlock(hardDrive, fdh)
                bufferOffset = 0
            }
        }

        return String(fileNameBytes.toByteArray())
    }

    private fun parseFdIndex(dirIndex: Int): Int {
        val offset = dirIndex * ENTRY_SIZE_IN_BYTES + FD_OFFSET
        return oftEntry.readWriteBuffer[offset].toInt()
    }

    private fun parseUsageByte(dirIndex: Int): Boolean {
        val offset = dirIndex * ENTRY_SIZE_IN_BYTES + IN_USE_FLAG_OFFSET
        return oftEntry.readWriteBuffer[offset].toInt() != 0
    }

    fun close() {
        val fd = fdh.getFdByIndex(oftEntry.fdIndex)
        fd.fileLength = oftEntry.currentPosition

        oftEntry.writeBufferToDisk(fdh, hardDrive)
        oftEntry.clear()
    }

    fun bindDirectoryEntry(fileName: String, fdIndex: Int) {
        val directoryEntry = entries.find { it.fileName == fileName }
        if (directoryEntry != null) {
            println("Error: file already exists"); return
        }

        val freeDirectoryEntry = entries.withIndex().firstOrNull { !it.value.isInUse }
        if (freeDirectoryEntry == null) {
            println("Error: no free directory slots"); return
        }

        freeDirectoryEntry.value.fileName = fileName
        freeDirectoryEntry.value.fdIndex = fdIndex
        freeDirectoryEntry.value.isInUse = true

        persistDirectoryEntry(freeDirectoryEntry.value, freeDirectoryEntry.index)
        println("Binding successful")
    }

    private fun persistDirectoryEntry(directoryEntry: DirectoryEntry, entryIndex: Int) {
        persistFileName(directoryEntry, entryIndex)
        persistFdIndex(directoryEntry, entryIndex)
        persistInUseFlag(directoryEntry, entryIndex)
    }

    private fun persistFileName(fileDirectoryEntry: DirectoryEntry, entryIndex: Int) {

        val fileName = fileDirectoryEntry.fileName
        val fileNameBytesWithZeroEnding = fileName.toByteArray() +
                ByteArray(FD_OFFSET - fileName.length) { 0 }

        val bytesOffset = entryIndex * ENTRY_SIZE_IN_BYTES
        var positionWithinBlock = bytesOffset % HardDriveBlock.BLOCK_SIZE
        oftEntry.currentPosition

        fileNameBytesWithZeroEnding.forEach { byte ->
            oftEntry.putIntoBuffer(positionWithinBlock++, byte)

            if (positionWithinBlock >= HardDriveBlock.BLOCK_SIZE) {
                if (oftEntry.isNewBlockNeeded(fdh))
                    oftEntry.allocateDataBlock(bitmapModel, hardDrive, fdh)

                oftEntry.iterateToNextDataBlock(hardDrive, fdh)
                positionWithinBlock = 0
            }
        }
    }

    private fun persistFdIndex(fileDirectoryEntry: DirectoryEntry, entryIndex: Int) {

        val fdIndexByte = fileDirectoryEntry.fdIndex.toByte()
         val bytesOffset = entryIndex * ENTRY_SIZE_IN_BYTES + FD_OFFSET
        var positionWithinBlock = bytesOffset % HardDriveBlock.BLOCK_SIZE

        oftEntry.putIntoBuffer(positionWithinBlock++, fdIndexByte)
        if (positionWithinBlock >= HardDriveBlock.BLOCK_SIZE)
            oftEntry.iterateToNextDataBlock(hardDrive, fdh)
    }

    private fun persistInUseFlag(fileDirectoryEntry: DirectoryEntry, entryIndex: Int) {

        val inUseFlag: Byte = if (fileDirectoryEntry.isInUse) 1 else 0
        val bytesOffset = entryIndex * ENTRY_SIZE_IN_BYTES + IN_USE_FLAG_OFFSET
        var positionWithinBlock = bytesOffset % HardDriveBlock.BLOCK_SIZE

        oftEntry.putIntoBuffer(positionWithinBlock++, inUseFlag)
        if (positionWithinBlock >= HardDriveBlock.BLOCK_SIZE)
            oftEntry.iterateToNextDataBlock(hardDrive, fdh)
    }

    fun getDirectoryEntry(fileName: String): DirectoryEntry? =
            entries.find { it.fileName == fileName }

    fun printFilesMetaInfo() {
        entries.filter { it.isInUse }.forEach {
            val fd = fdh.getFdByIndex(it.fdIndex)
            println("file name: ${it.fileName}; file length (bytes): ${fd.fileLength}")
        }
    }
}