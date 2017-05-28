import io.kotlintest.matchers.shouldBe
import io.kotlintest.properties.Gen
import io.kotlintest.specs.BehaviorSpec
import java.util.*

class OtherCommands : BehaviorSpec() {
    init {
        fun randomName(length: Int = 5): String =
                Gen.string().nextPrintableString(length)

        given("a file system") {
            val fileSystem = CallsDispatcher()
            fileSystem.init()

            `when`("I create files and write to them") {
                then("I should get corresponding info in directory") {
                    val count = Random().nextInt(FileDescriptorsModel.FD_NUMBER)
                    val names = List(count) { randomName() }
                    val lengths = List(count) { Random().nextInt(192) }

                    names.zip(lengths).forEach { (name, length) ->
                        fileSystem createFile name
                        val fd = (fileSystem openFile name) as Success
                        fileSystem.write(fd.value, length)
                        fileSystem close fd.value
                    }

                    fileSystem.directory().entries.forEachIndexed { index, (name, length) ->
                        names[index] shouldBe name
                        lengths[index] shouldBe length
                    }
                }
            }

            `when`("I save and restore file system") {
                then("I should get the same elements") {
                    val fileName = "check"

                    val count = Random().nextInt(FileDescriptorsModel.FD_NUMBER)
                    val names = List(count) { randomName() }
                    val lengths = List(count) { Random().nextInt(192) }
                    val data = List(count) { FdBasedCommandsHandler.allocateByteArray(lengths[it]) }

                    names.zip(lengths).forEach { (name, length) ->
                        fileSystem createFile name
                        val fd = (fileSystem openFile name) as Success
                        fileSystem.write(fd.value, length)
                        fileSystem close fd.value
                    }

                    fileSystem save fileName
                    fileSystem.kill()
                    fileSystem restore fileName

                    fileSystem.directory().entries.forEachIndexed { index, (name, length) ->
                        names[index] shouldBe name
                        lengths[index] shouldBe length

                        val fd = (fileSystem openFile name) as Success
                        fileSystem openFile name
                        val buffer = (fileSystem.read(fd.value, length)) as Buffer
                        buffer.data shouldBe data[index]
                        fileSystem close fd.value
                    }
                }
            }
        }
    }
}
