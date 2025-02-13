package mobi.meddle.wehe.data.bean

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
