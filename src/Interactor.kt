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
        while (true) parseInputStr(readLine() ?: "")
    }

    private fun parseInputStr(inputString: String) {
        val tokens = inputString.split(" ")

        callsHandler.apply {
            when (tokens[0]) {
                CREATE -> create(tokens[1])
                REMOVE -> remove(tokens[1])
                OPEN -> open(tokens[1])

                CLOSE -> close(tokens[1].toInt())
                WRITE -> write(tokens[1].toInt(), tokens[2].toInt())
                READ -> read(tokens[1].toInt(), tokens[2].toInt())
                LSEEK -> lseek(tokens[1].toInt(), tokens[2].toInt())

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