
import CallsHandler.COMMANDS.CLOSE
import CallsHandler.COMMANDS.CREATE
import CallsHandler.COMMANDS.DIRECTORY
import CallsHandler.COMMANDS.LSEEK
import CallsHandler.COMMANDS.OPEN
import CallsHandler.COMMANDS.READ
import CallsHandler.COMMANDS.REMOVE
import CallsHandler.COMMANDS.RESTORE
import CallsHandler.COMMANDS.SAVE
import CallsHandler.COMMANDS.WRITE

class Interactor {

    private val callsHandler = CallsHandler()

    fun run() {
        while (true) {
            parseInputStr(readLine() ?: "")
        }
    }

    private fun parseInputStr(inputString: String) {
        val tokens = inputString.split(" ")

        when (tokens[0]) {
            CREATE -> callsHandler.create(tokens[1])
            REMOVE -> callsHandler.remove(tokens[1])
            OPEN -> callsHandler.open(tokens[1])
            CLOSE -> callsHandler.close(Integer.valueOf(tokens[1]))
            WRITE -> callsHandler.write(Integer.valueOf(tokens[1]), when (tokens[2]) {
                "ns" -> CallsHandler.InputTypesEnum.NUMBERS_SEQUENCE
                "ls" -> CallsHandler.InputTypesEnum.LETTERS_SEQUENCE
                else -> CallsHandler.InputTypesEnum.NUMBERS_SEQUENCE
            }, Integer.valueOf(tokens[3]))
            READ -> callsHandler.read(Integer.valueOf(tokens[1]), Integer.valueOf(tokens[2]))
            LSEEK -> callsHandler.lseek(Integer.valueOf(tokens[1]), Integer.valueOf(tokens[2]))
            DIRECTORY -> callsHandler.directory()
            SAVE -> callsHandler.save(tokens[1])
            RESTORE -> callsHandler.restore(tokens[1])
        }
    }
}

fun main(args: Array<String>) {
    Interactor().run()
}