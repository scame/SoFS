
class FsCore(private val fdh: FileDescriptorsHandler, private val bitmapHandler: BitmapHandler,
             private val directory: Directory) {

    companion object {
        val MAX_FILE_SIZE_IN_KB = 64

        val PREDEFINED_BLOCKS_NUMBER = 5

        val BITMAP_BLOCKS_NUMBER = 4
    }

    fun createFile(fileName: String) {
        val freeFileDescriptorWithIndex = fdh.getFreeFileDescriptorWithIndex()
        directory.bindDirectoryEntry(fileName, freeFileDescriptorWithIndex.second)
    }
}