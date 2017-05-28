/**
 * operations like createFile/removeFile/openFile
 */

class NameBasedCommandsHandler(private val fdh: FileDescriptorsModel,
                               private val directory: Directory,
                               private val openFileTable: OpenFileTable) {

    fun createFile(fileName: String): Message {
        if (fileName.toByteArray().size > Directory.MAX_FILENAME_LENGTH)
            return FileNameTooLong

        val freeFdIndex = fdh.getFreeFdWithIndex()?.index ?: return NoFreeFD

        fdh.allocateFd(freeFdIndex)
        val bound = directory.bindDirectoryEntry(fileName, freeFdIndex)

        when (bound) {
            is Result -> return Success(freeFdIndex)
            else -> return bound
        }
    }

    fun openFile(fileName: String): Message {

        val fdIndex = directory.getDirectoryEntry(fileName)?.fdIndex ?: return NoFile

        if (openFileTable.isFileOpen(fdIndex)) return FileOpen

        val oftEntryWithIndex = openFileTable.getFreeOftEntryWithIndex() ?: return NoFreeOFTEntry

        oftEntryWithIndex.value.isInUse = true
        oftEntryWithIndex.value.fdIndex = fdIndex

        val dataBlock = fdh.getDataBlockFromFdWithIndex(fdIndex, 0).value
        oftEntryWithIndex.value.readWriteBuffer.put(dataBlock.bytes.toByteArray())

        return Success(fdIndex)
    }

    fun removeFile(fileName: String): Message {

        val fdIndex = directory.getDirectoryEntry(fileName)?.fdIndex ?: return NoFile

        if (openFileTable.getOftEntryByFdIndex(fdIndex) != null)
            return DeleteOpenFile

        directory.getDirectoryEntry(fileName)?.clear()
        fdh.releaseFd(fdIndex)

        return Success(fdIndex)
    }
}
