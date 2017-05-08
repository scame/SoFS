/**
 * operations like create/remove/open
 */

class NameBasedCommandsHandler(private val fdh: FileDescriptorsHandler, private val directory: Directory,
                               private val openFileTable: OpenFileTable) {

    fun createFile(fileName: String) {
        val freeFileDescriptorWithIndex = fdh.getFreeFileDescriptorWithIndex()
        if (freeFileDescriptorWithIndex == null) {
            println("Error: no free file descriptors"); return
        }

        directory.bindDirectoryEntry(fileName, freeFileDescriptorWithIndex.first)
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

        val oftEntryWithIndex = openFileTable.getFreeOftEntryWithIndex()
        if (oftEntryWithIndex == null) {
            println("Error: no free oft entry"); return -1
        }

        oftEntryWithIndex.second.isInUse = true
        oftEntryWithIndex.second.fdIndex = fdIndex
        oftEntryWithIndex.second.readWriteBuffer.put(fdh.getDataBlockFromFdWithIndex(fdIndex, 0).second.bytes.toByteArray())

        return oftEntryWithIndex.first
    }
}