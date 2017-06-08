/**
 * the first command must be init OR restore
 * to be able to restore FS state later, call save at the end of an interaction
 */
class CallsDispatcher {

    private var dbh: DiskBackupHandler

    private var rootDir: Directory

    private var nameBasedCommandsHandler: NameBasedCommandsHandler

    private var fdBasedCommandsHandler: FdBasedCommandsHandler

    init {
        val hardDrive = HardDrive()
        val bitmapHandler = BitmapModel(hardDrive)
        val fdh = FileDescriptorsModel(hardDrive, bitmapHandler)
        val openFileTable = OpenFileTable()

        rootDir = Directory(fdh, bitmapHandler, hardDrive, openFileTable)
        dbh = DiskBackupHandler(hardDrive, fdh, rootDir)
        nameBasedCommandsHandler = NameBasedCommandsHandler(fdh, rootDir, openFileTable)
        fdBasedCommandsHandler = FdBasedCommandsHandler(openFileTable, fdh, hardDrive, bitmapHandler)
    }

    object COMMANDS {
        val WRITE = "write"
        val READ = "read"
        val OPEN = "open"
        val CLOSE = "close"
        val CREATE = "create"
        val REMOVE = "remove"
        val LSEEK = "lseek"
        val DIRECTORY = "dir"
        val SAVE = "save"
        val RESTORE = "restore"
        val INIT = "init"
    }

    fun init() {
        // real initialization goes into init block to avoid optional types handling
        // file descriptors will be initialized lazily (accordingly to disk state)
        rootDir.initCleanDirectory()
        println("Directory initialized successfully")
    }

    fun kill() {
        val hardDrive = HardDrive()
        val bitmapHandler = BitmapModel(hardDrive)
        val fdh = FileDescriptorsModel(hardDrive, bitmapHandler)
        val openFileTable = OpenFileTable()

        rootDir = Directory(fdh, bitmapHandler, hardDrive, openFileTable)
        dbh = DiskBackupHandler(hardDrive, fdh, rootDir)
        nameBasedCommandsHandler = NameBasedCommandsHandler(fdh, rootDir, openFileTable)
        fdBasedCommandsHandler = FdBasedCommandsHandler(openFileTable, fdh, hardDrive, bitmapHandler)

        rootDir.initCleanDirectory()
    }


    infix fun createFile(fileName: String): Message {
        val msg = nameBasedCommandsHandler.createFile(fileName)
        when (msg) {
            FileNameTooLong -> println("Error: filename too long")
            NoFreeFD -> println("Error: no free fd")
            FileExists -> println("Error: file with such name already exists")
            NoFreeDirEntry -> println("Error: no free directory entry")
            else -> println("File created successfully")
        }

        return msg
    }

    infix fun openFile(fileName: String): Message {
        val msg = nameBasedCommandsHandler.openFile(fileName)
        when (msg) {
            NoFile -> println("Error: no file with such name")
            FileOpen -> println("Error: file with such name is already open")
            NoFreeOFTEntry -> println("Error: no free OFT entry")
            is Success -> println("File open successfully - fd ${msg.value}")
        }

        return msg
    }

    infix fun removeFile(fileName: String): Message {
        val msg = nameBasedCommandsHandler.removeFile(fileName)
        when (msg) {
            NoFile -> println("Error: no file with such name")
            DeleteOpenFile -> println("Error: can't delete file, while it's open")
            else -> println("File removed successfully")
        }

        return msg
    }

    fun read(fd: Int, bytesNumber: Int): Message {
        val msg = fdBasedCommandsHandler.read(fd, bytesNumber)
        when (msg) {
            NoFile -> println("Error: no file with such FD")
            is Buffer -> println(msg.data)
        }

        return msg
    }

    fun write(fd: Int, bytesNumber: Int): Message {
        val msg = fdBasedCommandsHandler.write(fd, bytesNumber)
        when (msg) {
            FileTooLarge -> println("Error: file size too large")
            NoFile -> println("Error: no file with such FD")
            else -> println("$bytesNumber bytes written successfully")
        }

        return msg
    }

    fun lseek(fd: Int, position: Int): Message {
        val msg = fdBasedCommandsHandler.lseek(fd, position)
        when (msg) {
            NoFile -> println("Error: no file with such FD")
            SeekOutOfBounds -> println("Error: you can't seek beyond file length")
            else -> println("Lseek to position $position successful")
        }

        return msg
    }

    infix fun close(fd: Int): Message {
        val msg = fdBasedCommandsHandler.closeFile(fd)
        when (msg) {
            NoFile -> println("Error: no file with such FD")
            else -> println("File closed successfully")
        }

        return msg
    }

    fun directory(): DirEntries = rootDir.getFilesMetaInfo()

    infix fun save(backFileName: String) {
        dbh.save(backFileName)
        println("Directory has been successfully backed up to $backFileName")
    }

    infix fun restore(backFileName: String) {
        dbh.restore(backFileName)
        println("Directory has been successfully restored from $backFileName")
    }
}
