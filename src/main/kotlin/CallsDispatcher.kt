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
        val OPEN = "openFile"
        val CLOSE = "close"
        val CREATE = "createFile"
        val REMOVE = "removeFile"
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


    infix fun createFile(fileName: String): Message =
            nameBasedCommandsHandler.createFile(fileName)

    infix fun openFile(fileName: String): Message =
            nameBasedCommandsHandler.openFile(fileName)

    infix fun removeFile(fileName: String): Message =
            nameBasedCommandsHandler.removeFile(fileName)

    fun read(fd: Int, bytesNumber: Int): Message =
            fdBasedCommandsHandler.read(fd, bytesNumber)

    fun write(fd: Int, bytesNumber: Int): Message =
            fdBasedCommandsHandler.write(fd, bytesNumber)

    fun lseek(fd: Int, position: Int): Message =
            fdBasedCommandsHandler.lseek(fd, position)

    infix fun close(fd: Int): Message =
            fdBasedCommandsHandler.closeFile(fd)

    fun directory(): DirEntries =
            rootDir.getFilesMetaInfo()

    infix fun save(backFileName: String) {
        dbh.save(backFileName)
    }

    infix fun restore(backFileName: String) {
        dbh.restore(backFileName)
    }
}
