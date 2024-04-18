package mobi.meddle.wehe.data

import android.app.Application
import android.content.res.AssetManager
import com.google.gson.Gson
import mobi.meddle.wehe.constant.Consts.APPS_FILENAME

class LocalAppsSource(private val applicationContext: Application) {
    fun loadApps(): List<AppsListing> {
        val am: AssetManager = applicationContext.assets
        val json = am.open(APPS_FILENAME).bufferedReader().use { it.readText() }
        val gson = Gson()
        val appsList = gson.fromJson(json, AppsList::class.java)
        val apps: List<AppsListing> = appsList.apps
        return apps
    }
}