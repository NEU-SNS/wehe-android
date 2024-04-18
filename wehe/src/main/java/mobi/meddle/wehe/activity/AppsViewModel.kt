package mobi.meddle.wehe.activity.ui.theme

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import mobi.meddle.wehe.data.AppsListing
import mobi.meddle.wehe.data.AppsRepository

class AppsViewModel(private val appsRepository: AppsRepository) : ViewModel() {

    val appsList: LiveData<List<AppsListing>> = liveData(Dispatchers.IO) { // Using coroutines
        val apps = appsRepository.getApps()
        emit(apps)
    }
}