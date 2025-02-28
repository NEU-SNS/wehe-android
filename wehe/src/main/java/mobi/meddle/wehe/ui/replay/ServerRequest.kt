package mobi.meddle.wehe.ui.replay

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class ServerRequest {
    // Class properties
    private var hostnameVerifier: HostnameVerifier? = null
    private var sslSocketFactory: SSLSocketFactory? = null
    private val timers = ArrayList<Timer>()

    // Constructor to initialize with SSL configuration if needed
    constructor(hostnameVerifier: HostnameVerifier?, sslSocketFactory: SSLSocketFactory?) {
        this.hostnameVerifier = hostnameVerifier
        this.sslSocketFactory = sslSocketFactory
    }

    // Default constructor
    constructor() {
        // Initialize without SSL configuration
        this.hostnameVerifier = null
        this.sslSocketFactory = null
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
     * Clean up any active timers
     */
    fun cleanup() {
        for (timer in timers) {
            timer.cancel()
        }
        timers.clear()
    }
}