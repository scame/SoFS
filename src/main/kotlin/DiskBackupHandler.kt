import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class DiskBackupHandler(private val hardDrive: HardDrive,
                        private val fdh: FileDescriptorsModel,
                        private val dir: Directory) {

    companion object {
        val FILE_PATH = "/home/swan/IdeaProjects/SoFSa/src"
    }

    fun save(backupFileName: String) {
        dir.close()
        fdh.persistDescriptors()

        val fos = FileOutputStream(File("$FILE_PATH/$backupFileName"))
        fos.write(hardDrive.getAllBlocksInByteForm())
        fos.close()
    }

    fun restore(backupFileName: String) {
        val path = Paths.get("$FILE_PATH/$backupFileName")
        val hardDriveBackup = Files.readAllBytes(path)
        hardDrive.restoreFromExternalSource(hardDriveBackup)
        dir.restoreFromDisk()
    }
}