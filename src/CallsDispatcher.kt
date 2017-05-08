class CallsDispatcher {

    private val dbh: DiskBackupHandler

    private val fsCore: FsCore

    init {
        val hardDrive = HardDrive()
        val bitmapHandler = BitmapHandler(hardDrive)
        val fdh = FileDescriptorsHandler(hardDrive, bitmapHandler)
        val openFileTable = OpenFileTable()
        dbh = DiskBackupHandler(hardDrive)
        fsCore = FsCore(fdh, BitmapHandler(hardDrive), Directory(fdh, bitmapHandler), openFileTable, hardDrive)
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

    enum class InputTypesEnum {
         NUMBERS_SEQUENCE, LETTERS_SEQUENCE
    }

    fun init() {

    }

    fun open(fileName: String) {
        fsCore.openFile(fileName)
    }

    fun close(fd: Int) {
        fsCore.closeFile(fd)
    }

    fun create(fileName: String) {
        fsCore.createFile(fileName)
    }

    fun remove(fileName: String) {
        fsCore.removeFile(fileName)
    }

    fun write(fd: Int, inputTypesEnum: InputTypesEnum, bytesNumber: Int) {
        println("Write: $fd ${inputTypesEnum.name} $bytesNumber")
    }

    fun read(fd: Int, bytesNumber: Int) {
        println("Read: $fd $bytesNumber")
    }

    fun lseek(fd: Int, position: Int) {
        println("Lseek: $fd $position")
    }

    fun directory() {
        fsCore.printFilesInfo()
    }

    fun save(backFileName: String) {
        dbh.save(backFileName)
    }

    fun restore(backFileName: String) {
        dbh.restore(backFileName)
    }
}