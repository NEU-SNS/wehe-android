package mobi.meddle.wehe.data.repository

import android.content.pm.ApplicationInfo


class AppsRepository {


}

interface ReplaysApi {
    val app = AppInfo()
    fun fetchApps(): List<ApplicationInfo>
