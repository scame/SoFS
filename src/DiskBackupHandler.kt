import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class DiskBackupHandler(private val hardDrive: HardDrive) {

    companion object {
        val FILE_PATH = "/home/scame/IdeaProjects/SoFS/src"
    }

    fun save(backupFileName: String) {
        val fos = FileOutputStream(File("$FILE_PATH/$backupFileName"))
        fos.write(hardDrive.getAllBlocksInByteForm())
        fos.close()
    }

    fun restore(backupFileName: String) {
        val path = Paths.get("$FILE_PATH/$backupFileName")
        val hardDriveBackup = Files.readAllBytes(path)
        hardDrive.restoreFromExternalSource(hardDriveBackup)
    }
}