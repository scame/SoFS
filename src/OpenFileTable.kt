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

class OpenFileTable {

    companion object {
        val OFT_SIZE = 10
    }

    val openFileTableEntries = mutableListOf<OpenFileTableEntry>()

    init {
        (0 until OFT_SIZE).forEach {
            openFileTableEntries.add(OpenFileTableEntry(ByteBuffer.allocate(HardDriveBlock.BLOCK_SIZE), 0, -1, false))
        }
    }

    fun getOftEntryWithIndex(): Pair<Int, OpenFileTableEntry>? {
        val oftEntry = openFileTableEntries.firstOrNull { !it.isInUse }
        val oftIndex = openFileTableEntries.indexOfFirst { !it.isInUse }

        if (oftEntry == null || oftIndex == -1) return null

        return oftIndex to oftEntry
    }
}