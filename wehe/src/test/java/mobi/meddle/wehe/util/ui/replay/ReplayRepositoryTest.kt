package mobi.meddle.wehe.util.ui.replay;

import android.content.Context
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import mobi.meddle.wehe.R
import mobi.meddle.wehe.ui.replay.ReplayRepository

@RunWith(MockitoJUnitRunner::class)
class AnalysisRequesterTest {

    @Mock
    private lateinit var analysisRequester: ReplayRepository

    @InjectMocks
    private lateinit var context: Context

    @Before
    fun setUp() {
        Mockito.`when`(context.getString(R.string.error_analysis_fail)).thenReturn("Analysis failed")
    }

    @Test
    fun requestAnalysis_AllServersReturnSuccess_ReturnsSuccess() {
        val serverUrls = listOf("server1", "server2")
        Mockito.`when`(analysisRequester.getAnalyzerServerUrls()).thenReturn(serverUrls)

        val successResult = JSONObject().apply { put("success", true) }
        Mockito.`when`(analysisRequester.ask4analysis(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(successResult)

        val result = analysisRequester.requestAnalysis("randomID", 10)

        assert(result.isSuccess)
        assert(result.getOrNull()?.size == serverUrls.size)
    }

    @Test
    fun requestAnalysis_SomeServersReturnFailure_ReturnsFailure() {
        val serverUrls = listOf("server1", "server2")
        Mockito.`when`(analysisRequester.getAnalyzerServerUrls()).thenReturn(serverUrls)

        val successResult = JSONObject().apply { put("success", true) }
        val failureResult = JSONObject().apply { put("success", false) }
        Mockito.`when`(analysisRequester.ask4analysis(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(successResult, failureResult)

        val result = analysisRequester.requestAnalysis("randomID", 10)

        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message == "Analysis failed")
    }

    @Test
    fun requestAnalysis_AllServersReturnNull_ReturnsFailure() {
        val serverUrls = listOf("server1", "server2")
        Mockito.`when`(analysisRequester.getAnalyzerServerUrls()).thenReturn(serverUrls)

        Mockito.`when`(analysisRequester.ask4analysis(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(null)

        val result = analysisRequester.requestAnalysis("randomID", 10)

        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message == "Analysis failed")
    }

    @Test
    fun requestAnalysis_SomeServersReturnNull_ReturnsFailure() {
        val serverUrls = listOf("server1", "server2")
        Mockito.`when`(analysisRequester.getAnalyzerServerUrls()).thenReturn(serverUrls)

        val successResult = JSONObject().apply { put("success", true) }
        Mockito.`when`(analysisRequester.ask4analysis(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(successResult, null)

        val result = analysisRequester.requestAnalysis("randomID", 10)

        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message == "Analysis failed")
    }

    @Test
    fun requestAnalysis_RetryMechanism_SuccessOnRetry() {
        val serverUrls = listOf("server1", "server2")
        Mockito.`when`(analysisRequester.getAnalyzerServerUrls()).thenReturn(serverUrls)

        val successResult = JSONObject().apply { put("success", true) }
        Mockito.`when`(analysisRequester.ask4analysis(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(null, null, successResult)

        val result = analysisRequester.requestAnalysis("randomID", 10)

        assert(result.isSuccess)
        assert(result.getOrNull()?.size == serverUrls.size)
    }
}
