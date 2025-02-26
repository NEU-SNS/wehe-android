package mobi.meddle.wehe.data.model

/**
 * App data model for testing
 */
data class AppData(
    val name: String,
    val replayName: String,
    val tcpCSPs: List<String> = emptyList(),
    val udpClientPorts: List<String> = emptyList(),
    val requestSets: List<RequestSet> = emptyList()
)
