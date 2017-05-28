import io.kotlintest.matchers.shouldBe
import io.kotlintest.properties.Gen
import io.kotlintest.specs.BehaviorSpec

class NameBasedCommands : BehaviorSpec() {
    init {
        fun randomName(length: Int = 5): String =
                Gen.string().nextPrintableString(length)

        given("a file system") {
            val fileSystem = CallsDispatcher()
            fileSystem.init()

            fun createAndOpenFile(name: String): Message {
                fileSystem createFile name
                return fileSystem openFile name
            }

            `when`("I create file with name too long") {
                then("I should get FileTooLong error") {
                    fileSystem createFile randomName(
                            Directory.MAX_FILENAME_LENGTH + 1) shouldBe FileNameTooLong
                }
            }

            `when`("I create file that already exists") {
                then("I should get FileExists error") {
                    val name = randomName()
                    fileSystem createFile name shouldBe Success(1)
                    fileSystem createFile name shouldBe FileExists
                }
            }

            `when`("I create more than 7 files") {
                then("I should get NoFreeFD error") {
                    for (i in 1 until FileDescriptorsModel.FD_NUMBER)
                        fileSystem createFile randomName()

                    fileSystem createFile randomName() shouldBe NoFreeFD
                }
            }

            `when`("I create files") {
                then("I should get corresponding FD") {
                    for (i in 1 until FileDescriptorsModel.FD_NUMBER)
                        fileSystem createFile randomName() shouldBe Success(i)
                }
            }

            `when`("I open file that doesn't exist") {
                then("I should get NoFile error") {
                    fileSystem openFile randomName() shouldBe NoFile
                }
            }

            `when`("I open file that is already open") {
                then("I should get FileOpen error") {
                    val name = randomName()
                    createAndOpenFile(name) shouldBe Success(1)
                    fileSystem openFile name shouldBe FileOpen
                }
            }

            `when`("I open more than 5 files") {
                then("I should get NoFreeOFTEntry") {
                    for (i in 1 until OpenFileTable.OFT_SIZE)
                        createAndOpenFile(randomName())

                    createAndOpenFile(randomName()) shouldBe NoFreeOFTEntry
                }
            }

            `when`("I create and open files") {
                then("I should get corresponding FD") {
                    for (i in 1 until OpenFileTable.OFT_SIZE)
                        createAndOpenFile(randomName()) shouldBe Success(i)
                }
            }

            `when`("I remove file that doesn't exist") {
                then("I should get NoFile error") {
                    fileSystem removeFile randomName() shouldBe NoFile
                }
            }

            `when`("I remove file that is open") {
                then("I should get DeleteOpenFile error") {
                    val name = randomName()
                    createAndOpenFile(name)
                    fileSystem removeFile name shouldBe DeleteOpenFile
                }
            }

            `when`("I create file and remove it") {
                then("I should get the same fd index") {
                    val name = randomName()
                    val fdIndex = fileSystem createFile name
                    fileSystem removeFile name shouldBe fdIndex
                }
            }
        }
    }
}
