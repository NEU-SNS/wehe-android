package mobi.meddle.wehe.data.model

/**
 * Test result model
 */
data class TestResult(
    val appName: String,
    val replayName: String,
    val date: String,
    val userId: String,
    val historyCount: Int,
    val testId: Int,
    val areaTest: Boolean,
    val ks2RatioTest: Boolean,
    val throughputAvgOriginal: Double,
    val throughputAvgTest: Double,
    val ks2dVal: Double,
    val ks2pVal: Double,
    val hasDifferentiation: Boolean
)