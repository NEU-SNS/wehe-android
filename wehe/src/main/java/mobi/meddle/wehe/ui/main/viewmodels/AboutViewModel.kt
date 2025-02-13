package mobi.meddle.wehe.ui.main.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import mobi.meddle.wehe.BuildConfig

class AboutViewModel : ViewModel() {
    // Expose the version name
    val versionName: String = BuildConfig.VERSION_NAME

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AboutViewModel::class.java)) {
                return AboutViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
