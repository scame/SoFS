import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.properties.Gen
import io.kotlintest.specs.BehaviorSpec
import java.util.*

class FdBasedCommands : BehaviorSpec() {
    init {
        fun randomName(length: Int = 5): String =
                Gen.string().nextPrintableString(length)

        given("a file system") {
            val fileSystem = CallsDispatcher()
            fileSystem.init()

            val blockSize = HardDriveBlock.BLOCK_SIZE
            val maxFileSize = FileDescriptorsModel.MAX_FILE_SIZE
            val bytesNumbers = listOf(blockSize - 1, blockSize, blockSize + 1,
                    2 * blockSize - 1, 2 * blockSize, 2 * blockSize + 1, maxFileSize)

            fun createAndOpenFile(name: String): Success {
                fileSystem createFile name
                return (fileSystem openFile name) as Success
            }

            `when`("I read file that is not open") {
                then("I should get NoFile error") {
                    fileSystem.read(1, Random().nextInt(50)) shouldBe NoFile
                }
            }

            `when`("I read from empty file") {
                then("I should get empty list of bytes") {
                    val fd = createAndOpenFile(randomName())
                    val buffer: Buffer = fileSystem.read(fd.value, 1 + Random().nextInt(50)) as Buffer
                    buffer.data should beEmpty()
                }
            }

            `when`("I write to file that is not open") {
                then("I should get NoFile error") {
                    fileSystem.write(1, Random().nextInt(50)) shouldBe NoFile

                    val fd = createAndOpenFile(randomName())
                    fileSystem close fd.value
                    fileSystem.write(1, Random().nextInt(50)) shouldBe NoFile
                }
            }

            `when`("I write bytes to file") {
                then("I should get number of bytes that I've written") {
                    val fd = createAndOpenFile(randomName())
                    val bytesNumber = Random().nextInt(50)
                    fileSystem.write(fd.value, bytesNumber) shouldBe Success(bytesNumber)
                }
            }

            `when`("I write to file") {
                then("I should read it back correctly") {
                    bytesNumbers.forEach { bytesNumber ->
                        val name = randomName()
                        val fdWrite = createAndOpenFile(name)
                        val bytes = FdBasedCommandsHandler.allocateByteArray(bytesNumber)

                        fileSystem.write(fdWrite.value, bytesNumber)
                        fileSystem close fdWrite.value

                        val fdRead: Success = (fileSystem openFile name) as Success
                        val buffer: Buffer = fileSystem.read(fdRead.value, bytesNumber) as Buffer
                        buffer.data shouldBe bytes
                        fileSystem close fdRead.value
                    }
                }
            }

            `when`("I write file that will take more than 3 blocks") {
                then("I should get FileTooLarge error") {
                    val fd = createAndOpenFile(randomName())
                    val bytesNumber = maxFileSize + 1
                    fileSystem.write(fd.value, bytesNumber) shouldBe FileTooLarge
                }
            }

            `when`("I write bytes to a file") {
                then("I should get the same bytes when I read the file") {
                    val name = randomName()

                    val bytesNumber = Random().nextInt(50)
                    val fdWrite = createAndOpenFile(name)
                    val bytes = FdBasedCommandsHandler.allocateByteArray(bytesNumber)

                    fileSystem.write(fdWrite.value, bytesNumber)
                    fileSystem close fdWrite.value

                    val fdRead: Success = (fileSystem openFile name) as Success
                    val buffer: Buffer = fileSystem.read(fdRead.value, bytesNumber) as Buffer
                    buffer.data shouldBe bytes
                    fileSystem close fdRead.value
                }
            }

            `when`("I seek in the file that's not open") {
                then("I should get NoFile error") {
                    fileSystem.lseek(1, Random().nextInt(50)) shouldBe NoFile
                }
            }

            `when`("I seek to the position larger than file length") {
                then("I should get SeekOutOfBound error") {
                    val fd = createAndOpenFile(randomName())
                    fileSystem.lseek(fd.value, 10) shouldBe SeekOutOfBounds
                }
            }

            `when`("I write to file") {
                then("I should to able to seek the end of file") {
                    bytesNumbers.forEach { bytesNumber ->
                        val name = randomName()
                        val fdWrite = createAndOpenFile(name)

                        fileSystem.write(fdWrite.value, bytesNumber)
                        fileSystem close fdWrite.value

                        val fdRead: Success = (fileSystem openFile name) as Success
                        fileSystem.lseek(fdRead.value, bytesNumber - 1) shouldBe Success(bytesNumber - 1)
                        fileSystem close fdRead.value
                    }
                }
            }

            `when`("I write to file and seek to beginning") {
                then("I should read the same bytes, that I've written") {
                    val bytesNumber = Random().nextInt(50)
                    val fd = createAndOpenFile(randomName())
                    val bytes = FdBasedCommandsHandler.allocateByteArray(bytesNumber)

                    fileSystem.write(fd.value, bytesNumber)
                    fileSystem.lseek(fd.value, 0)

                    val buffer: Buffer = fileSystem.read(fd.value, bytesNumber) as Buffer
                    buffer.data shouldBe bytes
                }
            }

            `when`("I close file that does not exist") {
                then("I should get NoFile error") {
                    fileSystem close 1 shouldBe NoFile
                }
            }

            `when`("I close file that I opened") {
                then("I should get the same fd as I opened it with") {
                    val fd = createAndOpenFile(randomName())
                    fileSystem close fd.value shouldBe fd
                }
            }

            `when`("I close file that is already closed") {
                then("I should get NoFile error") {
                    val fd = createAndOpenFile(randomName())
                    fileSystem close fd.value shouldBe fd
                    fileSystem close fd.value shouldBe NoFile
                }
            }
        }
    }
}
