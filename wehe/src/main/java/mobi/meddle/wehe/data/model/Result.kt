package mobi.meddle.wehe.data.model

/**
 * Represents a previous result of a test, which is used to display the results in the
 * previous results tab
 */
data class Result(
    var isPortTest: Boolean,
    var resultNameText: String,
    var appImage: String,
    var dateText: String,
    var differentiationText: String,
    var appThroughput: Double,
    var nonAppThroughput: Double,
    var ipType: String,
    var server: String,
    var carrier: String,
    var isTomography: Boolean,
    var differentiationNetwork: String
)