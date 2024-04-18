package mobi.meddle.wehe.activity

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import mobi.meddle.wehe.data.AppsListing
import mobi.meddle.wehe.data.DefaultAppsRepository
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(private val appsRepository: DefaultAppsRepository) : ViewModel() {
// write a getter function
    fun appsList() : List<AppsListing> {
        val apps = appsRepository.getApps()
        return apps
    }
}