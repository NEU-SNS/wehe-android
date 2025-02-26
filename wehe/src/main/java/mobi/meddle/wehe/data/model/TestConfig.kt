package mobi.meddle.wehe.data.model

/**
 * Test configuration
 */
data class TestConfig(
    val confirmationReplays: Boolean = true,
    val useDefaultThresholds: Boolean = true,
    val aThreshold: Int = 10,
    val ks2pValueThreshold: Int = 5,
    val doTest: Boolean = false,
    val isTomography: Boolean = false
)