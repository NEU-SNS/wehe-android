package mobi.meddle.wehe.data.model

/**
 * Represents a packet in a replay to be sent
 * Fields in this class correspond with the fields in the replay files in the assets directory
 */
data class RequestSet(
    var cSPair: String? = null, // client-server pair in the form {client_IP}.{client_port}-{server_IP}.{server_port}
    var timestamp: Double = 0.0, // time when packet should be sent
    var payload: ByteArray? = null, // the stuff to send
    var responseLen: Int = -1, // expected length of response to a TCP packet being sent
    var responseHash: String? = null, // expected hash of response
    var end: Boolean = false // for UDP
) {
    fun isUDP(): Boolean = responseLen == -1

    override fun toString(): String {
        return "RequestSet(cSPair=$cSPair, responseLen=$responseLen, timestamp=$timestamp)"
    }
}