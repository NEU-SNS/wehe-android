package mobi.meddle.wehe.data

import mobi.meddle.wehe.bean.ApplicationBean.Category


data class AppsListing(
    val name: String,
    val size: Int,
    val time: Int,
    val image: String,
    val datafile: String,
    val randomdatafile: String,
    val category: Category,
    val frenchOnly: Boolean? = false,
    val englishOnly: Boolean? = false) {
}

data class AppsList(
    val apps: List<AppsListing>
)