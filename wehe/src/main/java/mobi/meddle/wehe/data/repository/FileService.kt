package mobi.meddle.wehe.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for file operations
 */
@Singleton
class FileService @Inject constructor(
    private val context: Context
) {
    /**
     * Read a file from assets
     */
    suspend fun readAssetFile(filename: String): String = withContext(Dispatchers.IO) {
        context.assets.open(filename).use { inputStream ->
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            String(buffer, Charsets.UTF_8)
        }
    }
}