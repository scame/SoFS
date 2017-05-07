/**
 * fundamentally, directory is just a file, so it's size limited to 64KB
 * let's limit file name to 16bits, fd will take 8bits and 8bits given to in use flag
 *
 * accordingly, directory can have up to 65536 / 4 = 16384 entries
 */

data class DirectoryEntry(var fileName: String = "", var fdIndex: Int = -1, var isInUse: Boolean)

class Directory(private val fdh: FileDescriptorsHandler, private val bitmapHandler: BitmapHandler) {

    companion object {
        val MAX_ENTRIES = 16384
    }

    private val directoryEntriesList = mutableListOf<DirectoryEntry>()

    private var directoryFd: FileDescriptor

    // we assume that this call happens before any file allocations, so FD index will be 0
    init {
        val freeFileDescriptor = fdh.getFreeFileDescriptor()
        directoryFd = freeFileDescriptor
        directoryFd.inUse = true
        assignPointersBlockToDirectoryFd()

        allocateEmptyDirectoryEntries()
    }

    private fun assignPointersBlockToDirectoryFd() {
        val freeDiskBlockWithIndex = bitmapHandler.getFreeBlockWithIndex()
        directoryFd.pointersBlockIndex = freeDiskBlockWithIndex.second
    }

    private fun allocateEmptyDirectoryEntries() {
        (0 until MAX_ENTRIES).forEach {
            directoryEntriesList.add(DirectoryEntry(isInUse = false))
        }
    }

    fun bindDirectoryEntry(fileName: String, fdIndex: Int) {
        if (directoryEntriesList.firstOrNull { it.fileName == fileName } != null) {
            println("Error: file already exists"); return
        }

        val freeDirectoryEntry = directoryEntriesList.firstOrNull { !it.isInUse }
        if (freeDirectoryEntry == null) {
            println("Error: no free directory slots"); return
        }

        fdh.getFileDescriptorByIndex(fdIndex).inUse = true
        freeDirectoryEntry.fileName = fileName
        freeDirectoryEntry.fdIndex = fdIndex
        freeDirectoryEntry.isInUse = true
        println("Bind: success")
    }

    fun getDirectoryEntry(fileName: String) = directoryEntriesList.first { it.fileName == fileName }

    fun printFilesMetaInfo() {
        directoryEntriesList.filter { it.isInUse }.forEach {
            val fileLength = fdh.getFileDescriptorByIndex(it.fdIndex).fileLength
            println("file name: ${it.fileName}; file length (bytes): $fileLength")
        }
    }
}