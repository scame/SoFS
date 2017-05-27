/**
 * operations like create/remove/open
 */

class NameBasedCommandsHandler(private val fdh: FileDescriptorsModel,
                               private val directory: Directory,
                               private val openFileTable: OpenFileTable) {

    fun createFile(fileName: String) {
        if (fileName.toByteArray().size > 19) {
            println("Error: filename too long, try another one"); return
        }

        val freeFdIndex = fdh.getFreeFdWithIndex()?.index
        if (freeFdIndex == null) {
            println("Error: no free file descriptors"); return
        }

        fdh.allocateFd(freeFdIndex)
        directory.bindDirectoryEntry(fileName, freeFdIndex)
    }

    fun removeFile(fileName: String) {
        val fdIndex = (directory.getDirectoryEntry(fileName)?.fdIndex ?: -1)
        if (fdIndex == -1) {
            println("Error: no such file"); return
        }

        val oftEntry = openFileTable.getOftEntryByFdIndex(fdIndex)
        if (oftEntry != null) {
            println("Error: you can't delete open file"); return
        }

        directory.getDirectoryEntry(fileName)?.clear()
        fdh.releaseFd(fdIndex)
        println("Deletion successful")
    }

    fun openFile(fileName: String) {
        val fdIndex = directory.getDirectoryEntry(fileName)?.fdIndex
        if (fdIndex == null) {
            println("Error: no such file"); return
        }

        if (openFileTable.isFileOpen(fdIndex)) {
            println("Error: file already open"); return
        }

        val oftEntryWithIndex = openFileTable.getFreeOftEntryWithIndex()
        if (oftEntryWithIndex == null) {
            println("Error: no free oft entry"); return
        }

        oftEntryWithIndex.value.isInUse = true
        oftEntryWithIndex.value.fdIndex = fdIndex

        val dataBlock = fdh.getDataBlockFromFdWithIndex(fdIndex, 0).value
        oftEntryWithIndex.value.readWriteBuffer.put(dataBlock.bytes.toByteArray())

        println("Opened file fd: ${oftEntryWithIndex.index}")
    }
}