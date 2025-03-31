package mobi.meddle.wehe.ui.replay

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of Logger that uses Android's Log system
 */
@Singleton
class AndroidLogger @Inject constructor() : Logger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun warning(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}

/**
 * Interface for abstracting logging functionality
 */
interface Logger {
    fun debug(tag: String, message: String)
    fun warning(tag: String, message: String, throwable: Throwable? = null)
    fun error(tag: String, message: String, throwable: Throwable? = null)
}