package mobi.meddle.wehe.data.model

/**
 * Server configuration
 */
data class ServerConfig(
    val mainServer: String,
    val metadataServer: String,
    val analyzerServerUrls: List<String> = emptyList(),
    val isMLabServerUsed: Boolean = false
)