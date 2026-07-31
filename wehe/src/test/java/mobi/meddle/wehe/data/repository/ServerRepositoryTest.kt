package mobi.meddle.wehe.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Tests for [ServerRepository].
 *
 * A note on the HTTPS testing strategy: [ServerRepository.sendRequest] always casts
 * `url.openConnection()` to [javax.net.ssl.HttpsURLConnection], so a plain-HTTP MockWebServer
 * cannot be used for the success/JSON-parsing paths (it throws an uncaught ClassCastException that
 * kills the background thread, and the test would then have to wait out the 8s failsafe Timer).
 * Instead we generate a throwaway self-signed certificate at test setup time using the `keytool`
 * binary that ships with the JDK (no new Gradle dependency required, e.g. no okhttp-tls), spin up
 * MockWebServer with `useHttps` using that certificate, and configure the repository's
 * `hostnameVerifier`/`sslSocketFactory` to trust it (mirroring what
 * `ReplayRepository.generateServerCertificate` does in production).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerRepositoryTest {

    companion object {
        private lateinit var serverSslContext: SSLContext
        private lateinit var clientSslContext: SSLContext

        @BeforeClass
        @JvmStatic
        fun setupTls() {
            // The JDK's HttpURLConnection keep-alive connection pool is keyed by host+port; since
            // every test spins up a MockWebServer on a fresh ephemeral port, back-to-back tests
            // occasionally have the OS recycle a port fast enough that stale pooled connections
            // from a previous test's (now-shutdown) server get reused, corrupting responses.
            // Disabling keep-alive avoids that cross-test contamination.
            System.setProperty("http.keepAlive", "false")

            val keystoreFile = File.createTempFile("server-repository-test-keystore", ".p12")
            keystoreFile.delete() // keytool refuses to write into a pre-existing (even empty) file
            keystoreFile.deleteOnExit()
            val password = "changeit"

            val keytool = findKeytool()
            val process = ProcessBuilder(
                keytool, "-genkeypair",
                "-alias", "test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "3650",
                "-keystore", keystoreFile.absolutePath,
                "-storetype", "PKCS12",
                "-storepass", password,
                "-keypass", password,
                "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost,ip:127.0.0.1"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            check(exitCode == 0) { "keytool failed to generate test keystore: $output" }

            val keyStore = KeyStore.getInstance("PKCS12")
            keystoreFile.inputStream().use { keyStore.load(it, password.toCharArray()) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password.toCharArray())
            serverSslContext = SSLContext.getInstance("TLS").apply {
                init(kmf.keyManagers, null, null)
            }

            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            clientSslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAll, SecureRandom())
            }
        }

        private fun findKeytool(): String {
            val javaHome = System.getProperty("java.home")
            val candidate = File(javaHome, "bin/keytool")
            return if (candidate.exists()) candidate.absolutePath else "keytool"
        }
    }

    private lateinit var serverRepository: ServerRepository
    private var mockWebServer: MockWebServer? = null

    @Before
    fun setUp() {
        serverRepository = ServerRepository()
    }

    @After
    fun tearDown() {
        mockWebServer?.shutdown()
        mockWebServer = null
    }

    /** Starts an HTTPS MockWebServer trusted by [serverRepository]'s client configuration. */
    private fun startHttpsServer(): MockWebServer {
        val server = MockWebServer()
        server.useHttps(serverSslContext.socketFactory, false)
        server.start()
        mockWebServer = server
        serverRepository.hostnameVerifier = HostnameVerifier { _, _ -> true }
        serverRepository.sslSocketFactory = clientSslContext.socketFactory
        return server
    }

    // ---------------------------------------------------------------------
    // GET / POST success + JSON parsing
    // ---------------------------------------------------------------------

    @Test
    fun `GET success returns parsed JSON body`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"foo":"bar"}"""))

        val result = serverRepository.sendRequest(
            server.url("/test").toString(), "GET", true, null, null
        )

        assertThat(result).isNotNull()
        assertThat(result!!.getString("foo")).isEqualTo("bar")
        assertThat(server.takeRequest().path).isEqualTo("/test")
    }

    @Test
    fun `GET appends data list joined by ampersand without further encoding`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val data = arrayListOf("userID=abc", "command=singleResult")
        serverRepository.sendRequest(server.url("/single").toString(), "GET", true, data, null)

        val recorded = server.takeRequest()
        // urlEncoder just joins with "&" -- it does NOT percent-encode the individual
        // "key=value" strings, unlike paramsToPostData for POST (see finding in report).
        assertThat(recorded.path).isEqualTo("/single?userID=abc&command=singleResult")
    }

    @Test
    fun `POST success sends urlencoded body and returns parsed JSON`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"success":true}"""))

        // LinkedHashMap IS-A HashMap, so pass it directly (unlike `HashMap(pairs)`, which would
        // copy into a plain HashMap and lose the insertion order this test asserts on).
        val pairs = linkedMapOf<String, String?>("command" to "analyze", "userID" to "user 1")
        val result = serverRepository.sendRequest(
            server.url("/analyze").toString(), "POST", true, null, pairs
        )

        assertThat(result).isNotNull()
        assertThat(result!!.getBoolean("success")).isTrue()
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        // URLEncoder.encode turns the space in "user 1" into "+"
        assertThat(recorded.body.readUtf8()).isEqualTo("command=analyze&userID=user+1")
    }

    // ---------------------------------------------------------------------
    // Malformed JSON handling + GET-vs-POST retry asymmetry
    // ---------------------------------------------------------------------

    @Test
    fun `GET malformed JSON retries 3 times total then returns null`() {
        val server = startHttpsServer()
        repeat(3) { server.enqueue(MockResponse().setBody("not valid json")) }

        val result = serverRepository.sendRequest(
            server.url("/bad").toString(), "GET", true, null, null
        )

        assertThat(result).isNull()
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `POST malformed JSON returns null WITHOUT retrying (asymmetric with GET)`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("not valid json"))

        val result = serverRepository.sendRequest(
            server.url("/bad").toString(), "POST", true, null, hashMapOf("a" to "b")
        )

        assertThat(result).isNull()
        // Only one request was ever made -- POST has no retry loop (`for (i in 0..2)` exists
        // only in the GET branch of sendRequest), unlike GET which retries up to 3 times on the
        // exact same class of failure (IOException/JSONException). See ServerRepository.kt:74
        // (GET retry loop) vs the POST branch at :112 which has no such loop.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `GET retries exactly 3 times on IOException then returns null`() {
        val serverSocket = ServerSocket(0)
        val acceptedCount = AtomicInteger(0)
        val acceptor = Thread {
            try {
                while (true) {
                    val socket = serverSocket.accept()
                    acceptedCount.incrementAndGet()
                    socket.close() // reset the connection before any TLS handshake can complete
                }
            } catch (e: Exception) {
                // expected once serverSocket.close() runs below
            }
        }
        acceptor.isDaemon = true
        acceptor.start()

        try {
            val url = "https://localhost:${serverSocket.localPort}/test"
            val result = serverRepository.sendRequest(url, "GET", false, null, null)

            assertThat(result).isNull()
            assertThat(acceptedCount.get()).isEqualTo(3)
        } finally {
            serverSocket.close()
        }
    }

    @Test
    fun `POST attempts only once on IOException (no retry)`() {
        val serverSocket = ServerSocket(0)
        val acceptedCount = AtomicInteger(0)
        val acceptor = Thread {
            try {
                while (true) {
                    val socket = serverSocket.accept()
                    acceptedCount.incrementAndGet()
                    socket.close()
                }
            } catch (e: Exception) {
                // expected
            }
        }
        acceptor.isDaemon = true
        acceptor.start()

        // POST unconditionally assigns hostnameVerifier/sslSocketFactory (see bug note below),
        // so they must be non-null here or the request would blow up before ever touching the
        // network.
        serverRepository.hostnameVerifier = HostnameVerifier { _, _ -> true }
        serverRepository.sslSocketFactory = clientSslContext.socketFactory

        try {
            val url = "https://localhost:${serverSocket.localPort}/test"
            val result = serverRepository.sendRequest(url, "POST", false, null, hashMapOf("a" to "b"))

            assertThat(result).isNull()
            assertThat(acceptedCount.get()).isEqualTo(1)
        } finally {
            serverSocket.close()
        }
    }

    // ---------------------------------------------------------------------
    // BUG: POST unconditionally assigns hostnameVerifier/sslSocketFactory (no null-guard, no
    // `main` check unlike GET), and HttpsURLConnection.setHostnameVerifier(null) throws
    // IllegalArgumentException -- which is NOT caught by sendRequest's `catch (IOException)` /
    // `catch (JSONException)` blocks. The background thread dies silently and the caller only
    // gets `null` back after the full 8-second Timer failsafe fires, instead of failing fast.
    // See ServerRepository.kt:119-120 (POST) vs :80-83 (GET, which guards with
    // `if (main && hostnameVerifier != null && sslSocketFactory != null)`).
    // ---------------------------------------------------------------------
    @Test
    fun `BUG - POST with unset hostnameVerifier throws uncaught IllegalArgumentException, only surfaced via the 8s failsafe timer`() {
        // Deliberately do NOT set hostnameVerifier/sslSocketFactory (both null by default).
        val start = System.currentTimeMillis()
        val result = serverRepository.sendRequest(
            "https://example.invalid/test", "POST", true, null, hashMapOf("a" to "b")
        )
        val elapsed = System.currentTimeMillis() - start

        assertThat(result).isNull()
        // The connection object is created (and IAE thrown) essentially instantly, well before
        // any network I/O, yet sendRequest still blocks for ~8s because readyToReturn is only
        // ever set by the Timer safety net once the background thread has silently died.
        assertThat(elapsed).isAtLeast(7500L)
    }

    // ---------------------------------------------------------------------
    // BUG: paramsToPostData calls URLEncoder.encode(value, "UTF-8") where value can be null
    // (HashMap<String, String?>). URLEncoder.encode(null, ...) throws NullPointerException, NOT
    // UnsupportedEncodingException, so paramsToPostData's own catch clause does not catch it, and
    // sendRequest's catch(IOException)/catch(JSONException) don't either. This is directly
    // reachable from production code: ReplayRepository.ask4analysis() does
    // `pairs["userID"] = id` where `id: String?` is allowed to be null.
    // See ServerRepository.kt:211-230 (paramsToPostData) and ReplayRepository.kt:250.
    // ---------------------------------------------------------------------
    @Test
    fun `BUG - POST with a null pair value throws uncaught NPE in paramsToPostData, only surfaced via the 8s failsafe timer`() {
        // Needs a real reachable server: the crash happens while writing the request body
        // (after the connection is already established), not during connection setup, so an
        // unreachable host would instead fail fast with an ordinary (caught) IOException and
        // wouldn't exercise this bug at all.
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        val start = System.currentTimeMillis()
        val result = serverRepository.sendRequest(
            server.url("/test").toString(), "POST", true, null,
            hashMapOf("userID" to null, "command" to "analyze")
        )
        val elapsed = System.currentTimeMillis() - start

        assertThat(result).isNull()
        assertThat(elapsed).isAtLeast(7500L)
    }

    // ---------------------------------------------------------------------
    // MLab URL rewriting (client_name=wehe-android query param injection)
    // ---------------------------------------------------------------------

    @Test
    fun `mlab substring triggers client_name param with question mark separator`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(server.url("/mlab").toString(), "GET", true, null, null)

        assertThat(server.takeRequest().path).isEqualTo("/mlab?client_name=wehe-android")
    }

    @Test
    fun `mlab substring triggers client_name param with ampersand separator when query already present`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(server.url("/mlab?foo=bar").toString(), "GET", true, null, null)

        assertThat(server.takeRequest().path).isEqualTo("/mlab?foo=bar&client_name=wehe-android")
    }

    @Test
    fun `measurementLab substring (capital L, case-sensitive) triggers client_name param`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(server.url("/measurementLab").toString(), "GET", true, null, null)

        assertThat(server.takeRequest().path).isEqualTo("/measurementLab?client_name=wehe-android")
    }

    @Test
    fun `locate-dot-mlab-staging appspot com substring triggers client_name param`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(
            server.url("/locate-dot-mlab-staging.appspot.com").toString(), "GET", true, null, null
        )

        assertThat(server.takeRequest().path)
            .isEqualTo("/locate-dot-mlab-staging.appspot.com?client_name=wehe-android")
    }

    @Test
    fun `locate measurementlab net substring triggers client_name param`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(
            server.url("/locate.measurementlab.net").toString(), "GET", true, null, null
        )

        assertThat(server.takeRequest().path)
            .isEqualTo("/locate.measurementlab.net?client_name=wehe-android")
    }

    @Test
    fun `non-mlab URL is not rewritten`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(server.url("/normal").toString(), "GET", true, null, null)

        assertThat(server.takeRequest().path).isEqualTo("/normal")
    }

    @Test
    fun `mlab rewrite also applies to POST (rewrite happens before the GET-POST branch)`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(
            server.url("/mlab").toString(), "POST", true, null, hashMapOf("a" to "b")
        )

        // The client_name rewrite block sits above the `if (method.equals("GET"...`
        // check in sendRequest, so it mutates urlString for POST requests too.
        assertThat(server.takeRequest().path).isEqualTo("/mlab?client_name=wehe-android")
    }

    @Test
    fun `mlab client_name and GET data param combine with correct separators`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        serverRepository.sendRequest(
            server.url("/mlab").toString(), "GET", true, arrayListOf("a=1"), null
        )

        assertThat(server.takeRequest().path).isEqualTo("/mlab?client_name=wehe-android&a=1")
    }

    // ---------------------------------------------------------------------
    // cleanup()
    // ---------------------------------------------------------------------

    @Test
    fun `cleanup with zero timers does not throw`() {
        serverRepository.cleanup()
    }

    @Test
    fun `cleanup clears timer list and a subsequent sendRequest still works`() {
        val server = startHttpsServer()
        server.enqueue(MockResponse().setBody("""{"a":1}"""))
        val first = serverRepository.sendRequest(server.url("/1").toString(), "GET", true, null, null)
        assertThat(first).isNotNull()

        serverRepository.cleanup()

        server.enqueue(MockResponse().setBody("""{"b":2}"""))
        val second = serverRepository.sendRequest(server.url("/2").toString(), "GET", true, null, null)
        assertThat(second).isNotNull()
        assertThat(second!!.getInt("b")).isEqualTo(2)
    }

    // ---------------------------------------------------------------------
    // getServerIP
    // ---------------------------------------------------------------------

    @Test
    fun `getServerIP returns IPv4 as plain string`() = runBlocking(Dispatchers.IO) {
        Mockito.mockStatic(InetAddress::class.java).use { mockedStatic ->
            val inet4 = Mockito.mock(Inet4Address::class.java)
            Mockito.`when`(inet4.hostAddress).thenReturn("93.184.216.34")
            mockedStatic.`when`<InetAddress> { InetAddress.getByName(Mockito.anyString()) }
                .thenReturn(inet4)

            val result = serverRepository.getServerIP("example.com")

            assertThat(result).isEqualTo("93.184.216.34")
        }
    }

    @Test
    fun `getServerIP wraps IPv6 in brackets`() = runBlocking(Dispatchers.IO) {
        Mockito.mockStatic(InetAddress::class.java).use { mockedStatic ->
            val inet6 = Mockito.mock(Inet6Address::class.java)
            Mockito.`when`(inet6.hostAddress).thenReturn("2001:db8::1")
            mockedStatic.`when`<InetAddress> { InetAddress.getByName(Mockito.anyString()) }
                .thenReturn(inet6)

            val result = serverRepository.getServerIP("example.com")

            assertThat(result).isEqualTo("[2001:db8::1]")
        }
    }

    @Test
    fun `getServerIP returns empty string after 5 failed DNS attempts`() = runBlocking(Dispatchers.IO) {
        // NOTE: this test genuinely takes ~5 real seconds. getServerIP does a blocking
        // Thread.sleep(1000) between each of its 5 attempts (ServerRepository.kt:258-262), which
        // is real wall-clock time even inside runTest (it's not a `delay()` call that virtual-time
        // skipping can fast-forward). This is a real testability weakness in the source: there's
        // no way to make this path fast without either refactoring the sleep to something
        // injectable/virtual-time-aware, or accepting a slow test.
        Mockito.mockStatic(InetAddress::class.java).use { mockedStatic ->
            mockedStatic.`when`<InetAddress> { InetAddress.getByName(Mockito.anyString()) }
                .thenThrow(UnknownHostException("simulated failure"))

            val start = System.currentTimeMillis()
            val result = serverRepository.getServerIP("this-host-does-not-exist.invalid")
            val elapsed = System.currentTimeMillis() - start

            assertThat(result).isEqualTo("")
            assertThat(elapsed).isAtLeast(4500L)
        }
    }

    // ---------------------------------------------------------------------
    // getPublicIP
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // BUG: getPublicIP crashes with IndexOutOfBoundsException instead of returning "127.0.0.1"
    // when `servers` is empty. The guard condition is `servers.size != 0 && servers[0] != "127.0.0.1"`
    // -- when servers is empty that whole condition is false (short-circuits on the size check), so
    // execution falls into the `else` branch, which unconditionally does
    // `Log.w(..., "server ip is not available: " + servers[0])` -- indexing into the very list it
    // just confirmed might be empty. See ServerRepository.kt:277 (condition) and :332 (crash site).
    // This is reachable any time getPublicIP is called before setServers() has ever been invoked.
    // ---------------------------------------------------------------------
    @Test
    fun `BUG - getPublicIP throws IndexOutOfBoundsException when no servers are set (instead of returning loopback)`() = runBlocking(Dispatchers.IO) {
        try {
            serverRepository.getPublicIP("12345")
            org.junit.Assert.fail("Expected IndexOutOfBoundsException due to servers[0] access on an empty list")
        } catch (e: IndexOutOfBoundsException) {
            // Documents the current (buggy) behavior.
        }
    }

    @Test
    fun `getPublicIP returns loopback immediately when first server is already 127-0-0-1`() = runBlocking(Dispatchers.IO) {
        serverRepository.setServers(arrayListOf("127.0.0.1"))
        val result = serverRepository.getPublicIP("12345")
        assertThat(result).isEqualTo("127.0.0.1")
    }

    @Test
    fun `getPublicIP parses a valid IPv4 response and sets isIPv6 false`() = runBlocking(Dispatchers.IO) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("203.0.113.5"))
        server.start()
        mockWebServer = server

        serverRepository.setServers(arrayListOf(server.hostName))
        val result = serverRepository.getPublicIP(server.port.toString())

        assertThat(result).isEqualTo("203.0.113.5")
        assertThat(serverRepository.isIPv6).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/WHATSMYIPMAN")
    }

    @Test
    fun `getPublicIP parses a valid IPv6 response and sets isIPv6 true`() = runBlocking(Dispatchers.IO) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("2001:db8::1"))
        server.start()
        mockWebServer = server

        serverRepository.setServers(arrayListOf(server.hostName))
        val result = serverRepository.getPublicIP(server.port.toString())

        assertThat(result).isEqualTo("2001:db8::1")
        assertThat(serverRepository.isIPv6).isTrue()
    }

    // ---------------------------------------------------------------------
    // BUG / surprising behavior: a malformed (non-IP, non-resolvable) public-IP response does
    // NOT retry and does NOT return "-1" -- it is treated exactly like the "no server configured"
    // case and returns "127.0.0.1" (see the `catch (e: UnknownHostException)` branch at
    // ServerRepository.kt:313-316). Downstream, `ReplayRepository.checkPortAccess` treats any
    // non-"-1" result as "the port is accessible" (`ipThroughProxy != "-1"`), so a garbled
    // response from the WHATSMYIPMAN server is silently treated as a successful port check
    // instead of a genuine failure.
    // ---------------------------------------------------------------------
    @Test
    fun `BUG - malformed public IP response falls back to loopback instead of retrying or returning -1`() = runBlocking(Dispatchers.IO) {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("not-an-ip-address"))
        server.start()
        mockWebServer = server

        serverRepository.setServers(arrayListOf(server.hostName))

        Mockito.mockStatic(InetAddress::class.java, Mockito.CALLS_REAL_METHODS).use { mockedStatic ->
            mockedStatic.`when`<InetAddress> { InetAddress.getByName("not-an-ip-address") }
                .thenThrow(UnknownHostException("simulated: not a real host or IP"))

            val result = serverRepository.getPublicIP(server.port.toString())

            assertThat(result).isEqualTo("127.0.0.1")
            assertThat(server.requestCount).isEqualTo(1) // no retry attempted
        }
    }

    @Test
    fun `getPublicIP returns -1 after 5 failed connection attempts`() = runBlocking(Dispatchers.IO) {
        // Also genuinely slow (~5 real seconds: 5 attempts, Thread.sleep(1000) between each,
        // ServerRepository.kt:319-323) for the same reason as the getServerIP retry test above.
        val throwawaySocket = ServerSocket(0)
        val deadPort = throwawaySocket.localPort
        throwawaySocket.close() // nothing listens here -> connections are refused immediately

        // "127.0.0.1" is special-cased to skip the network entirely (see test above), so we use
        // "localhost" instead, which is a distinct string but still routes to loopback.
        serverRepository.setServers(arrayListOf("localhost"))

        val start = System.currentTimeMillis()
        val result = serverRepository.getPublicIP(deadPort.toString())
        val elapsed = System.currentTimeMillis() - start

        assertThat(result).isEqualTo("-1")
        assertThat(elapsed).isAtLeast(4500L)
    }
}
