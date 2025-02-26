package mobi.meddle.wehe.data.model

/**
* Represents a set of network requests
*/
data class RequestSet(
    val clientServerPair: String = "",
    val payload: ByteArray = byteArrayOf(),
    val timestamp: Double = 0.0,
    val responseLength: Int? = null,
    val responseHash: String? = null,
    val isEnd: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RequestSet
        if (clientServerPair != other.clientServerPair) return false
        if (!payload.contentEquals(other.payload)) return false
        if (timestamp != other.timestamp) return false
        if (responseLength != other.responseLength) return false
        if (responseHash != other.responseHash) return false
        if (isEnd != other.isEnd) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientServerPair.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (responseLength ?: 0)
        result = 31 * result + (responseHash?.hashCode() ?: 0)
        result = 31 * result + isEnd.hashCode()
        return result
    }
}
