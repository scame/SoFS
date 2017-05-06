import CallsDispatcher.COMMANDS.CLOSE
import CallsDispatcher.COMMANDS.CREATE
import CallsDispatcher.COMMANDS.DIRECTORY
import CallsDispatcher.COMMANDS.INIT
import CallsDispatcher.COMMANDS.LSEEK
import CallsDispatcher.COMMANDS.OPEN
import CallsDispatcher.COMMANDS.READ
import CallsDispatcher.COMMANDS.REMOVE
import CallsDispatcher.COMMANDS.RESTORE
import CallsDispatcher.COMMANDS.SAVE
import CallsDispatcher.COMMANDS.WRITE

class Interactor {

    private val callsHandler = CallsDispatcher()

    fun run() {
        while (true) {
            parseInputStr(readLine() ?: "")
        }
    }

    private fun parseInputStr(inputString: String) {
        val tokens = inputString.split(" ")

        callsHandler.apply {
            when (tokens[0]) {
                CREATE -> create(tokens[1])
                REMOVE -> remove(tokens[1])
                OPEN -> open(tokens[1])
                CLOSE -> close(Integer.valueOf(tokens[1]))
                WRITE -> write(Integer.valueOf(tokens[1]), when (tokens[2]) {
                    "ns" -> CallsDispatcher.InputTypesEnum.NUMBERS_SEQUENCE
                    "ls" -> CallsDispatcher.InputTypesEnum.LETTERS_SEQUENCE
                    else -> CallsDispatcher.InputTypesEnum.NUMBERS_SEQUENCE
                }, Integer.valueOf(tokens[3]))
                READ -> read(Integer.valueOf(tokens[1]), Integer.valueOf(tokens[2]))
                LSEEK -> lseek(Integer.valueOf(tokens[1]), Integer.valueOf(tokens[2]))
                DIRECTORY -> directory()
                SAVE -> save(tokens[1])
                RESTORE -> restore(tokens[1])
                INIT -> init()
            }
        }
    }
}

fun main(args: Array<String>) {
    Interactor().run()
}