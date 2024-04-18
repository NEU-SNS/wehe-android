package mobi.meddle.wehe.data

import javax.inject.Inject

class DefaultAppsRepository @Inject constructor(private val dataSource: LocalAppsSource) {
    fun getApps(): List<AppsListing> {
        return dataSource.loadApps()
    }
}

