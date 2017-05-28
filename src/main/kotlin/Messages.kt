interface Message

interface Error : Message

object FileOpen: Error
object FileExists: Error

object NoFile : Error
object NoFreeFD : Error
object NoFreeOFTEntry : Error
object NoFreeDirEntry: Error

object FileTooLarge: Error
object FileNameTooLong : Error

object DeleteOpenFile: Error
object SeekOutOfBounds: Error

interface Result : Message

data class Buffer(val data: List<Byte>): Result
data class Success(val value: Int = 0) : Result
data class DirEntries(val entries: List<Pair<String, Int>>) : Result
