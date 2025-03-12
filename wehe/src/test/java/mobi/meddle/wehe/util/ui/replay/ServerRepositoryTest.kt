import mobi.meddle.wehe.ui.replay.ServerRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mockStatic
import org.mockito.MockitoAnnotations
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.io.UnsupportedEncodingException
import java.net.UnknownHostException
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.ArrayList
import java.util.HashMap
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.mockito.MockedStatic

@RunWith(MockitoJUnitRunner::class)
class ServerRepositoryTest {

    @InjectMocks
    private lateinit var serverRepository: ServerRepository

    @Mock
    private lateinit var httpsURLConnection: HttpsURLConnection

    @Mock
    private lateinit var inputStream: InputStream

    @Mock
    private lateinit var outputStream: OutputStream

    @Mock
    private lateinit var bufferedReader: BufferedReader

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun sendRequest_ValidGetRequest_ReturnsResponse() {
        val url = "https://example.com"
        val data = ArrayList<String>()
        data.add("key=value")
        val expectedResponse = "{\"key\":\"value\"}"

        `when`(httpsURLConnection.inputStream).thenReturn(inputStream)
        `when`(httpsURLConnection.outputStream).thenReturn(outputStream)
        `when`(bufferedReader.readLine()).thenReturn(expectedResponse, null)
        `when`(bufferedReader.close()).thenReturn(Unit)

        val mockStatic = mockStatic(ServerRepository::class.java)
        mockStatic.`when`<HttpsURLConnection> { ServerRepository::class.java.getMethod("openConnection", URL::class.java).invoke(any(), any()) }
            .thenReturn(httpsURLConnection)

        val result = serverRepository.sendRequest(url, "GET", true, data, null)

        assertNotNull(result)
        assertEquals(expectedResponse, result.toString())
        mockStatic.close()
    }

    @Test
    fun sendRequest_ValidPostRequest_ReturnsResponse() {
        val url = "https://example.com"
        val pairs = HashMap<String, String>()
        pairs["key"] = "value"
        val expectedResponse = "{\"key\":\"value\"}"

        `when`(httpsURLConnection.inputStream).thenReturn(inputStream)
        `when`(httpsURLConnection.outputStream).thenReturn(outputStream)
        `when`(bufferedReader.readLine()).thenReturn(expectedResponse, null)
        `when`(bufferedReader.close()).thenReturn(Unit)

        val mockStatic = mockStatic(ServerRepository::class.java)
        mockStatic.`when`<HttpsURLConnection> { ServerRepository::class.java.getMethod("openConnection", URL::class.java).invoke(any(), any()) }
            .thenReturn(httpsURLConnection)

        val result = serverRepository.sendRequest(url, "POST", true, null, pairs)

        assertNotNull(result)
        assertEquals(expectedResponse, result.toString())
        mockStatic.close()
    }

    @Test
    fun sendRequest_InvalidRequest_ReturnsNull() {
        val url = "https://example.com"
        val data = ArrayList<String>()
        data.add("key=value")

        `when`(httpsURLConnection.inputStream).thenThrow(IOException("IO Exception"))
        `when`(httpsURLConnection.outputStream).thenReturn(outputStream)

        val mockStatic = mockStatic(ServerRepository::class.java)
        mockStatic.`when`<HttpsURLConnection> { ServerRepository::class.java.getMethod("openConnection", URL::class.java).invoke(any(), any()) }
            .thenReturn(httpsURLConnection)

        val result = serverRepository.sendRequest(url, "GET", true, data, null)

        assertNull(result)
        mockStatic.close()
    }

    @Test
    fun sendRequest_Timeout_ReturnsNull() {
        val url = "https://example.com"
        val data = ArrayList<String>()
        data.add("key=value")

        `when`(httpsURLConnection.inputStream).thenReturn(inputStream)
        `when`(httpsURLConnection.outputStream).thenReturn(outputStream)
        `when`(bufferedReader.readLine()).thenThrow(IOException("Timeout Exception"))

        val mockStatic = mockStatic(ServerRepository::class.java)
        mockStatic.`when`<HttpsURLConnection> { ServerRepository::class.java.getMethod("openConnection", URL::class.java).invoke(any(), any()) }
            .thenReturn(httpsURLConnection)

        val result = serverRepository.sendRequest(url, "GET", true, data, null)

        assertNull(result)
        mockStatic.close()
    }
}
