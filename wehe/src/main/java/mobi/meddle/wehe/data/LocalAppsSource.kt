package mobi.meddle.wehe.data
import android.content.Context
import android.content.res.AssetManager
import mobi.meddle.wehe.constant.Consts.APPS_FILENAME
import java.io.File
import com.google.gson.Gson
import javax.inject.Inject

//class AppsSource(private val applicationContext: Context) {
class AppsSource() {
    fun loadApps(): List<AppsListing> {
        return emptyList()
//        val am: AssetManager = applicationContext.assets
//        val json = am.open(APPS_FILENAME).bufferedReader().use { it.readText() }
//        val gson = Gson()
//        val appsList = gson.fromJson(json, AppsList::class.java)
//        val apps: List<AppsListing> = appsList.apps
//        return apps
    }
}