/**
 * the first command must be init OR restore
 * to be able to restore FS state later, call save at the end of an interaction
 */

class CallsDispatcher {

    private val dbh: DiskBackupHandler

    private val rootDir: Directory

    private val nameBasedCommandsHandler: NameBasedCommandsHandler

    private val fdBasedCommandsHandler: FdBasedCommandsHandler

    init {
        val hardDrive = HardDrive()
        val bitmapHandler = BitmapHandler(hardDrive)
        val fdh = FileDescriptorsHandler(hardDrive, bitmapHandler)
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
        println("initialization: success")
    }

    fun open(fileName: String) {
        nameBasedCommandsHandler.openFile(fileName)
    }

    fun close(fd: Int) {
        fdBasedCommandsHandler.closeFile(fd)
    }

    fun create(fileName: String) {
        nameBasedCommandsHandler.createFile(fileName)
    }

    fun remove(fileName: String) {
        nameBasedCommandsHandler.removeFile(fileName)
    }

    fun write(fd: Int, bytesNumber: Int) {
        fdBasedCommandsHandler.write(fd, bytesNumber)
    }

    fun read(fd: Int, bytesNumber: Int) {
        fdBasedCommandsHandler.read(fd, bytesNumber)
    }

    fun lseek(fd: Int, position: Int) {
        fdBasedCommandsHandler.lseek(fd, position)
    }

    fun directory() {
        rootDir.printFilesMetaInfo()
    }

    fun save(backFileName: String) {
        dbh.save(backFileName)
    }

    fun restore(backFileName: String) {
        dbh.restore(backFileName)
    }
}