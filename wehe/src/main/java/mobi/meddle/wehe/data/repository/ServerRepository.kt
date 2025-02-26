package mobi.meddle.wehe.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.ServerConfig
import mobi.meddle.wehe.data.remote.CertificateManager
import mobi.meddle.wehe.data.remote.NetworkService
import mobi.meddle.wehe.data.remote.WebSocketManager
import mobi.meddle.wehe.util.Config
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Repository for server operations
 */
@Singleton
class ServerRepository @Inject constructor(
    private val networkService: NetworkService,
    private val webSocketManager: WebSocketManager,
    private val certificateManager: CertificateManager
) {
    private val _serverConfig = MutableStateFlow(
        ServerConfig(
            mainServer = "",
            metadataServer = ""
        )
    )
    val serverConfig: StateFlow<ServerConfig> = _serverConfig

    /**
     * Setup server connection
     */
    suspend fun setupServers(serverDisplay: String, metadataServer: String?): Boolean {
        // Special case for wehe4.meddle.mobi
        val serverHostname = if (serverDisplay == "wehe3.meddle.mobi") "wehe4.meddle.mobi" else serverDisplay

        val serversList = mutableListOf<String>()

        // Hack for French DNS lookup issue
        if (serverHostname == "wehe4.meddle.mobi") {
            serversList.add("10.0.0.0")
        } else {
            try {
                val serverIp = networkService.resolveHostToIp(serverHostname)
                if (serverIp.isEmpty()) return false
                serversList.add(serverIp)
            } catch (e: Exception) {
                return false
            }
        }

        // Check if IPv6
        val serverIpIsV6 = serversList[0].contains(":")

        // Setup for MLab servers if needed
        val isMLabServerUsed = serversList[0] == "10.0.0.0" || serverIpIsV6

        if (isMLabServerUsed) {
            // Setup MLab servers
            serversList.clear()
            webSocketManager.closeAll()

            try {
                val mLabResponse = networkService.performGetRequest(Consts.MLAB_SERVERS)
                val mLabServers = mLabResponse.getJSONArray("results")

                for (i in 0 until mLabServers.length()) {
                    try {
                        val serverObj = mLabServers.getJSONObject(i)
                        val serverName = "wehe-" + serverObj.getString("machine")
                        val mLabUrl = serverObj.getJSONObject("urls").getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY)

                        // Connect to WebSocket
                        // This is simplified - actual implementation would need to handle WebSocket connections

                        val serverIp = networkService.resolveHostToIp(serverName)
                        if (serverIp.isNotEmpty()) {
                            serversList.add(serverIp)
                        }
                    } catch (e: Exception) {
                        // Try next server
                    }
                }

                if (serversList.isEmpty()) {
                    // Fallback to Amazon server
                    serversList.add(networkService.resolveHostToIp("wehe2.meddle.mobi"))
                }
            } catch (e: Exception) {
                return false
            }
        }

        // Setup certificates and analyzerServerUrls
        certificateManager.setupMainServerCertificate()

        val port = Config.get("result_port").toInt()
        val analyzerUrls = serversList.map { "https://$it:$port/Results" }

        var metadataServerIp = ""
        if (metadataServer != null) {
            metadataServerIp = networkService.resolveHostToIp(metadataServer)
            if (metadataServerIp.isEmpty()) return false
            certificateManager.setupMetadataServerCertificate()
        }

        // Update server config
        _serverConfig.value = ServerConfig(
            mainServer = serversList.joinToString(","),
            metadataServer = metadataServerIp,
            analyzerServerUrls = analyzerUrls,
            isMLabServerUsed = isMLabServerUsed
        )

        return true
    }
}
