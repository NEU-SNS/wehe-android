package mobi.meddle.wehe.data

interface AppsRepository {
    fun getApps(): List<AppsListing>
}
class DefaultAppsRepository(private val source: AppsSource) : AppsRepository {
    override fun getApps(): List<AppsListing> {
        return source.loadApps();
    }
}