
class FsCore(private val fdh: FileDescriptorsHandler, private val bitmapHandler: BitmapHandler,
             private val directory: Directory, private val openFileTable: OpenFileTable,
             private val hardDrive: HardDrive) {

    companion object {
        val MAX_FILE_SIZE_IN_KB = 512

        val PREDEFINED_BLOCKS_NUMBER = 5

        val BITMAP_BLOCKS_NUMBER = 4
    }

    fun createFile(fileName: String) {
        val freeFileDescriptorWithIndex = fdh.getFreeFileDescriptorWithIndex()
        directory.bindDirectoryEntry(fileName, freeFileDescriptorWithIndex.second)
    }

    fun removeFile(fileName: String) {
        val fileDescriptorIndex = (directory.getDirectoryEntry(fileName)?.fdIndex ?: -1)
        if (fileDescriptorIndex == -1) { println("Error: no such file"); return }

        directory.getDirectoryEntry(fileName)?.clear()
        fdh.releaseFileDescriptor(fileDescriptorIndex)
        println("Deletion: success")
    }

    fun openFile(fileName: String): Int {
        val fdIndex = directory.getDirectoryEntry(fileName)?.fdIndex
        if (fdIndex == null) { println("Error: no such file"); return -1 }

        val oftEntryWithIndex = openFileTable.getOftEntryWithIndex()
        if (oftEntryWithIndex == null) { println("Error: no free oft entry"); return -1 }

        oftEntryWithIndex.second.isInUse = true
        oftEntryWithIndex.second.fdIndex = fdIndex
        oftEntryWithIndex.second.readWriteBuffer.put(getFirstDataBlockFromFd(fdIndex).byteArray.toByteArray())

        return oftEntryWithIndex.first
    }

    private fun getFirstDataBlockFromFd(fdIndex: Int): HardDriveBlock {
        val pointersBlock = hardDrive.getBlock(fdh.getFileDescriptorByIndex(fdIndex).pointersBlockIndex)
        val dataBlockIndex = pointersBlock.getDataBlockIndexFromPointersBlock(0)

        return hardDrive.getBlock(dataBlockIndex)
    }

    fun printFilesInfo() = directory.printFilesMetaInfo()
}