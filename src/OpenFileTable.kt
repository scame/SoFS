import java.nio.ByteBuffer

/**
 * OFT size is limited to 10 values (only 10 files can be opened at the same time)
 */

data class OpenFileTableEntry(val readWriteBuffer: ByteBuffer, var currentPosition: Int,
                              var fdIndex: Int, var isInUse: Boolean)

fun OpenFileTableEntry.clear() {
    this.readWriteBuffer.clear()
    this.currentPosition = 0
    this.fdIndex = -1
    this.isInUse = false
}

fun OpenFileTableEntry.rewriteBuffer(hardDriveBlock: HardDriveBlock) {
    this.readWriteBuffer.clear()
    this.readWriteBuffer.put(hardDriveBlock.bytes.toByteArray())
}

fun OpenFileTableEntry.putIntoBuffer(bufferOffset: Int, byte: Byte) {
    this.readWriteBuffer.put(bufferOffset, byte)
    ++this.currentPosition
}

class OpenFileTable {

    companion object {
        val OFT_SIZE = 10
    }

    private val openFileTableEntries = mutableListOf<OpenFileTableEntry>()

    init {
        (0 until OFT_SIZE).forEach {
            openFileTableEntries.add(OpenFileTableEntry(ByteBuffer.allocate(HardDriveBlock.BLOCK_SIZE), 0, -1, false))
        }
    }

    fun getOftEntryByFdIndex(fdIndex: Int) = openFileTableEntries.firstOrNull { it.fdIndex == fdIndex }

    fun getFreeOftEntryWithIndex(): Pair<Int, OpenFileTableEntry>? {
        val oftEntry = openFileTableEntries.firstOrNull { !it.isInUse }
        val oftIndex = openFileTableEntries.indexOfFirst { !it.isInUse }

        if (oftEntry == null || oftIndex == -1) return null

        return oftIndex to oftEntry
    }
}