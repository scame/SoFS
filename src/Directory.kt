/**
 * fundamentally, directory is just a file, so it's size limited to 512KB
 * let's limit file name to 16bits, fd will take 8bits and 8bits will be given to in use flag
 *
 * accordingly, directory can have up to 524288 (max file size in bytes) / 4 = 131072 entries
 */

data class DirectoryEntry(var fileName: String = "", var fdIndex: Int = -1, var isInUse: Boolean)

fun DirectoryEntry.clear() {
    this.fileName = ""
    this.fdIndex = -1
    this.isInUse = false
}

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

        freeDirectoryEntry.fileName = fileName
        freeDirectoryEntry.fdIndex = fdIndex
        freeDirectoryEntry.isInUse = true

        fdh.getFileDescriptorByIndex(fdIndex).inUse = true
        fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex = bitmapHandler.getFreeBlockWithIndex().second

        println("Bind: success")
    }

    fun getDirectoryEntry(fileName: String) = directoryEntriesList.firstOrNull() { it.fileName == fileName }

    fun printFilesMetaInfo() {
        directoryEntriesList.filter { it.isInUse }.forEach {
            val fileLength = fdh.getFileDescriptorByIndex(it.fdIndex).fileLength
            println("file name: ${it.fileName}; file length (bytes): $fileLength")
        }
    }
}