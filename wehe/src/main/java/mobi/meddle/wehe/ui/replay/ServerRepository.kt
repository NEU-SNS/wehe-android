package mobi.meddle.wehe.ui.replay

import android.content.Context
import android.util.Log
import mobi.meddle.wehe.R
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Timer
import java.util.TimerTask
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

class ServerRepository// Generate the certificate for main server on initialization
    (context: Context) {
    // Class properties
    private var hostnameVerifier: HostnameVerifier? = null
    var sslSocketFactory: SSLSocketFactory? = null
    private val timers = ArrayList<Timer>()
    private var servers = ArrayList<String?>() //servers to run the replays to
    private var isIPv6 = false
    private var activity : Context = context


    init {
        generateServerCertificate(true)
    }

    /**
     * Gets the certificates for the servers
     *
     * @param main true if main server; false if metadata server
     */
    fun generateServerCertificate(main: Boolean) {
        try {
            val server = if (main) "main" else "metadata"
            val cf = CertificateFactory.getInstance("X.509")
            var ca: Certificate
            activity.resources.openRawResource(if (main) R.raw.main else R.raw.metadata)
                .use { caInput ->
                    ca = cf.generateCertificate(caInput)
                    Log.d("Certificate", server + "=" + (ca as X509Certificate).issuerDN)
                }
            // Create a KeyStore containing our trusted CAs
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, null)
            keyStore.setCertificateEntry(server, ca)

            // Create a TrustManager that trusts the CAs in our KeyStore
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
            tmf.init(keyStore)

            // Create an SSLContext that uses our TrustManager
            val context = SSLContext.getInstance("TLS")
            context.init(null, tmf.trustManagers, null)
            if (main) {
                sslSocketFactory = context.socketFactory
                hostnameVerifier =
                    HostnameVerifier() { hostname: String?, session: SSLSession? -> true }
            }
        } catch (e: CertificateException) {
            Log.e("Certificates", "Error generating certificates", e)
        } catch (e: NoSuchAlgorithmException) {
            Log.e("Certificates", "Error generating certificates", e)
        } catch (e: KeyStoreException) {
            Log.e("Certificates", "Error generating certificates", e)
        } catch (e: KeyManagementException) {
            Log.e("Certificates", "Error generating certificates", e)
        } catch (e: IOException) {
            Log.e("Certificates", "Error generating certificates", e)
        }
    }

    /**
     * Send a GET or POST request to the server.
     *
     * @param url    URL to the server
     * @param method either GET or POST
     * @param main   true if request is to main server; false otherwise
     * @param data   data to send to server in a GET request, null if a POST request or if no
     * data to send to server
     * @param pairs  data to send to server in a POST request, null if a GET request
     * @return a response from the server in the form of a JSONObject, null if error
     */
    fun sendRequest(
        url: String, method: String, main: Boolean,
        data: ArrayList<String>?, pairs: HashMap<String, String?>?
    ): JSONObject? {
        val json = arrayOf<JSONObject?>(null)
        val conn = arrayOfNulls<HttpsURLConnection>(1)
        val readyToReturn = booleanArrayOf(false)
        val serverComm = Thread {
            var url_string = url
            if (method.equals("GET", ignoreCase = true)) {
                if (data != null) {
                    val dataURL = URLEncoder(data)
                    url_string += "?$dataURL"
                }
                Log.d("Send GET Request", url_string)

                for (i in 0..2) {
                    try {
                        //connect to server
                        val u = URL(url_string)
                        //send data to server
                        conn[0] = u.openConnection() as HttpsURLConnection
                        if (main && hostnameVerifier != null && sslSocketFactory != null) {
                            conn[0]!!.hostnameVerifier = hostnameVerifier
                            conn[0]!!.sslSocketFactory = sslSocketFactory
                        }
                        conn[0]!!.connectTimeout = 8000
                        conn[0]!!.readTimeout = 8000
                        val `in` = BufferedReader(
                            InputStreamReader(
                                conn[0]!!.inputStream
                            )
                        )
                        val buffer = StringBuilder()
                        var input: String?

                        // parse BufferReader rd to StringBuilder res
                        while ((`in`.readLine()
                                .also { input = it }) != null
                        ) { //read response from server
                            buffer.append(input)
                        }

                        `in`.close()
                        conn[0]!!.disconnect()
                        json[0] = JSONObject(buffer.toString()) // parse String to json file
                        break
                    } catch (e: IOException) {
                        Log.e("Send Request", "sendRequest GET failed", e)
                    } catch (e: JSONException) {
                        Log.e("Send Request", "JSON Parse failed", e)
                    }
                }
            } else if (method.equals("POST", ignoreCase = true)) {
                Log.d("Send POST Request", url_string)

                try {
                    //connect to server
                    val u = URL(url_string)
                    conn[0] = u.openConnection() as HttpsURLConnection
                    conn[0]!!.hostnameVerifier = hostnameVerifier
                    conn[0]!!.sslSocketFactory = sslSocketFactory
                    conn[0]!!.connectTimeout = 5000
                    conn[0]!!.readTimeout = 5000
                    conn[0]!!.requestMethod = "POST"
                    conn[0]!!.doInput = true
                    conn[0]!!.doOutput = true

                    val os = conn[0]!!.outputStream
                    val writer = BufferedWriter(
                        OutputStreamWriter(os, StandardCharsets.UTF_8)
                    )
                    writer.write(pairs?.let { paramsToPostData(it) }) //send data to server

                    writer.flush()
                    writer.close()
                    os.close()

                    val `in` = BufferedReader(
                        InputStreamReader(
                            conn[0]!!.inputStream
                        )
                    )
                    val buffer = StringBuilder()
                    var input: String?

                    // parse BufferReader rd to StringBuilder res
                    while ((`in`.readLine()
                            .also { input = it }) != null
                    ) { //read response from server
                        buffer.append(input)
                    }
                    `in`.close()
                    conn[0]!!.disconnect()
                    json[0] = JSONObject(buffer.toString()) // parse String to json file.
                } catch (e: JSONException) {
                    Log.e("Send Request", "convert string to json failed", e)
                    json[0] = null
                } catch (e: IOException) {
                    Log.e("Send Request", "sendRequest POST failed", e)
                    json[0] = null
                }
            }
            readyToReturn[0] = true
        }
        serverComm.start()
        val t = Timer()
        timers.add(t)
        //timeout server after 8 sec; server timeout field times out only when nothing is sent;
        //if stuff sends too slowly, it could take forever, so this external timer prevents that
        t.schedule(object : TimerTask() {
            override fun run() { //set timer to timeout the thread if max time has been reached for replay
                if (conn[0] != null) {
                    conn[0]!!.disconnect()
                }
                readyToReturn[0] = true
            }
        }, 8000)
        //wait until ready to move on (i.e. when result retrieved or timeout), as threads don't
        //block execution
        while (!readyToReturn[0]) {
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.w("Send Request", "Interrupted", e)
            }
        }
        return json[0]
    }

    /**
     * Helper method to encode URL parameters
     * @param map List of strings to encode
     * @return Encoded URL string
     */
    private fun URLEncoder(map: ArrayList<String>): String {
        val data = StringBuilder()
        for (s in map) {
            if (data.isNotEmpty()) {
                data.append("&")
            }
            data.append(s)
        }
        return data.toString()
    }

    /**
     * Encodes data into a string to send POST request to server.
     *
     * @param params data to convert into string to send to server
     * @return an encoded string to send to the server
     */
    private fun paramsToPostData(params: HashMap<String, String?>): String {
        val result = StringBuilder()
        var first = true
        for ((key, value) in params) {
            if (first) {
                first = false
            } else {
                result.append("&")
            }

            try {
                result.append(java.net.URLEncoder.encode(key, "UTF-8"))
                result.append("=")
                result.append(java.net.URLEncoder.encode(value, "UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                Log.e("paramsToPostData", "Encoding error", e)
            }
        }
        return result.toString()
    }

    /**
     * Does a DNS lookup on a hostname.
     *
     * @param server the hostname to be resolved
     * @return the IP of the host; empty string if there is an error doing so.
     */
    fun getServerIP(server: String): String? {
        var server = server
        Log.d("getServerIP", "Server hostname: $server")
        var address: InetAddress?
        for (i in 0..4) { //5 attempts to lookup the IP
            try {
                server = InetAddress.getByName(server).hostAddress //DNS lookup
                address = InetAddress.getByName(server)
                if (address is Inet4Address) {
                    return server
                }
                if (address is Inet6Address) {
                    return "[$server]"
                }
            } catch (e: UnknownHostException) {
                if (i == 4) {
                    Log.e("getServerIP", "Failed to get IP of server", e)
                } else {
                    Log.w("getServerIP", "Failed to get IP of server, trying again")
                }
                try {
                    Thread.sleep(1000)
                } catch (ex: InterruptedException) {
                    Log.w("getServerIP", "Sleep interrupted", ex)
                }
            }
        }
        return ""
    }

    /**
     * Get IP of user's device.
     *
     * @param port port to run replays
     * @return user's public IP or -1 if cannot connect to the server
     */
    fun getPublicIP(port: String): String {
        var publicIP = "127.0.0.1"

        if (servers.size != 0 && servers[0] != "127.0.0.1") {
            val url = "http://" + servers[0] + ":" + port + "/WHATSMYIPMAN"
            Log.d("getPublicIP", "url: $url")

            var numFails = 0
            while (publicIP == "127.0.0.1") {
                try {
                    val u = URL(url)
                    //go to server
                    val conn = u.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 5000
                    val `in` = BufferedReader(
                        InputStreamReader(
                            conn.inputStream
                        )
                    )
                    val buffer = StringBuilder()
                    var input: String?

                    while ((`in`.readLine().also { input = it }) != null) { //read IP address
                        buffer.append(input)
                    }
                    `in`.close()
                    conn.disconnect()
                    publicIP = buffer.toString()
                    val address = InetAddress.getByName(publicIP)
                    if (address !is Inet4Address && address !is Inet6Address) {
                        Log.e("getPublicIP", "wrong format of public IP: $publicIP")
                        throw UnknownHostException()
                    }
                    isIPv6 = address is Inet6Address
                    if (publicIP == "") {
                        publicIP = "-1"
                    }
                    Log.d("getPublicIP", "public IP: $publicIP")
                } catch (e: UnknownHostException) {
                    Log.w("getPublicIP", "failed to get public IP!", e)
                    publicIP = "127.0.0.1"
                    break
                } catch (e: IOException) {
                    Log.w("getPublicIP", "Can't connect to server")
                    try {
                        Thread.sleep(1000)
                    } catch (e1: InterruptedException) {
                        Log.w("getPublicIP", "Sleep interrupted", e1)
                    }
                    if (++numFails == 5) { //Cannot connect to server after 5 tries
                        Log.w("getPublicIP", "Returning -1", e)
                        publicIP = "-1"
                        break
                    }
                }
            }
        } else {
            Log.w("getPublicIP", "server ip is not available: " + servers[0])
        }
        return publicIP
    }

    fun setServers(servers : ArrayList<String?>) {
        this.servers = servers
    }

    /**
     * Clean up any active timers
     */
    fun cleanup() {
        for (timer in timers) {
            timer.cancel()
        }
        timers.clear()
    }
}