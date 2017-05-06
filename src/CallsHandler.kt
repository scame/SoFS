
class CallsHandler {

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
    }

    enum class InputTypesEnum {
         NUMBERS_SEQUENCE, LETTERS_SEQUENCE
    }

    fun open(fileName: String) {
        println("Open: $fileName")
    }

    fun close(fd: Int) {
        println("Close: $fd")
    }

    fun create(fileName: String) {
        println("Create: $fileName")
    }

    fun remove(fileName: String) {
        println("Remove: $fileName")
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
        println("Directory")
    }

    fun save(backFileName: String) {
        println("Save: $backFileName")
    }

    fun restore(backFileName: String) {
        println("Restore: $backFileName")
    }
}