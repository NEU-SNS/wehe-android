package mobi.meddle.wehe.ui.main.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mobi.meddle.wehe.R
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.ApplicationBean
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.SavedStateHandle
import mobi.meddle.wehe.data.model.SelectionUiState
import javax.inject.Inject

class SelectionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(SelectionUiState())
    val uiState: StateFlow<SelectionUiState> = _uiState.asStateFlow()

    private val _selectedApps = mutableStateListOf<ApplicationBean>()
//    val selectedApps: List<ApplicationBean> = _selectedApps

    private val _payloadSize = MutableStateFlow(0)
    val payloadSize: StateFlow<Int> = _payloadSize.asStateFlow()

    private val _carrierDisplay = MutableStateFlow<String?>(null)
    val carrierDisplay: StateFlow<String?> = _carrierDisplay.asStateFlow()

    private val _appToggleStates = mutableStateMapOf<ApplicationBean, Boolean>()
    val appToggleStates: Map<ApplicationBean, Boolean> = _appToggleStates

    private val _isPortTest = MutableStateFlow(false)
//    val isPortTest: StateFlow<Boolean> = _isPortTest.asStateFlow()

    private val _currentTabIndex = MutableStateFlow(0)
    val currentTabIndex: StateFlow<Int> = _currentTabIndex.asStateFlow()

    init {
        // Restore saved state if it exists
        savedStateHandle.get<List<ApplicationBean>>(KEY_SELECTED_APPS)?.let { apps ->
            _selectedApps.addAll(apps)
        }
        savedStateHandle.get<String>(KEY_CARRIER_DISPLAY)?.let { carrier ->
            _carrierDisplay.value = carrier
        }
        savedStateHandle.get<Map<String, Boolean>>(KEY_APP_TOGGLE_STATES)?.let { states ->
            // We need to reconstruct the map with ApplicationBean objects
            viewModelScope.launch {
                uiState.value.apps.forEach { app ->
                    states[app.name]?.let { isSelected ->
                        _appToggleStates[app] = isSelected
                    }
                }
            }
        }
    }

    fun setCurrentTabIndex(index: Int) {
        _currentTabIndex.value = index
        recalculatePayloadSize()
    }

    // Add function to set test type
    fun setTestType(isPortTest: Boolean) {
        _isPortTest.value = isPortTest
        recalculatePayloadSize()
    }

    private fun recalculatePayloadSize() {
        val filteredApps = getFilteredSelectedApps()
        _payloadSize.value = filteredApps.sumOf { it.size }
        savedStateHandle[KEY_PAYLOAD_SIZE] = _payloadSize.value
    }

    // Function to get filtered selected apps based on test type
    fun getFilteredSelectedApps(): List<ApplicationBean> {
        // First filter by test type (port vs differentiation)
        val testTypeFiltered = if (_isPortTest.value) {
            _selectedApps.filter {
                it.category == ApplicationBean.Category.SMALL_PORT ||
                        it.category == ApplicationBean.Category.LARGE_PORT
            }
        } else {
            _selectedApps.filter {
                it.category == ApplicationBean.Category.VIDEO ||
                        it.category == ApplicationBean.Category.MUSIC ||
                        it.category == ApplicationBean.Category.CONFERENCING
            }
        }

        // Then filter by current tab
        return when {
            _isPortTest.value -> {
                when (_currentTabIndex.value) {
                    0 -> testTypeFiltered.filter { it.category == ApplicationBean.Category.SMALL_PORT }
                    1 -> testTypeFiltered.filter { it.category == ApplicationBean.Category.LARGE_PORT }
                    else -> testTypeFiltered
                }
            }
            else -> {
                when (_currentTabIndex.value) {
                    0 -> testTypeFiltered.filter { it.category == ApplicationBean.Category.VIDEO }
                    1 -> testTypeFiltered.filter { it.category == ApplicationBean.Category.MUSIC }
                    2 -> testTypeFiltered.filter { it.category == ApplicationBean.Category.CONFERENCING }
                    else -> testTypeFiltered
                }
            }
        }
    }


    fun loadInitialData(context: Context) {
        if (uiState.value.apps.isEmpty()) {
            viewModelScope.launch {
                try {
                    val apps = parseAppJSON(context)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        apps = apps
                    )
                    // Restore toggle states after loading apps
                    restoreToggleStates()
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    private fun restoreToggleStates() {
        savedStateHandle.get<Map<String, Boolean>>(KEY_APP_TOGGLE_STATES)?.let { states ->
            uiState.value.apps.forEach { app ->
                states[app.name]?.let { isSelected ->
                    _appToggleStates[app] = isSelected
                    if (isSelected) {
                        _selectedApps.add(app)
                        updatePayloadSize(app.size)
                    }
                }
            }
            recalculatePayloadSize()
        }
    }

    fun setCarrierDisplay(carrier: String) {
        _carrierDisplay.value = carrier
        savedStateHandle[KEY_CARRIER_DISPLAY] = carrier
    }

    fun toggleApp(app: ApplicationBean, isSelected: Boolean) {
        _appToggleStates[app] = isSelected
        if (isSelected) {
            _selectedApps.add(app)
            updatePayloadSize(app.size)
        } else {
            _selectedApps.remove(app)
            updatePayloadSize(-app.size)
        }

        recalculatePayloadSize()

        // Save current state
        savedStateHandle[KEY_SELECTED_APPS] = _selectedApps.toMutableList()
        savedStateHandle[KEY_APP_TOGGLE_STATES] = _appToggleStates.mapKeys { it.key.name }
    }

    private fun updatePayloadSize(size: Int) {
        _payloadSize.value += size
        savedStateHandle[KEY_PAYLOAD_SIZE] = _payloadSize.value
    }

    private fun parseAppJSON(context: Context): ArrayList<ApplicationBean> {
        val apps = ArrayList<ApplicationBean>()
        var `in`: BufferedReader? = null
        try {
            val buf = StringBuilder()
            val assets = context.assets
            val json: InputStream = assets.open(Consts.APPS_FILENAME)
            `in` = BufferedReader(InputStreamReader(json))
            var str: String?

            while ((`in`.readLine().also { str = it }) != null) {
                buf.append(str)
            }
            `in`.close()

            val jObject = JSONObject(buf.toString())
            val jArray = jObject.getJSONArray("apps")
            val port443SmallFile = jObject.getString("port443small")
            val port443LargeFile = jObject.getString("port443large")

            for (i in 0 until jArray.length()) {
                val appObj = jArray.getJSONObject(i)
                val bean = ApplicationBean().apply {
                    dataFile = appObj.getString("datafile")
                    size = appObj.getInt("size")
                    time = appObj.getInt("time") * 2
                    image = appObj.getString("image")
                    isEnglishOnly = appObj.optBoolean("englishOnly", false)
                    isFrenchOnly = appObj.optBoolean("frenchOnly", false)

                    category = ApplicationBean.Category.valueOf(appObj.getString("category"))
                    randomDataFile = when (category) {
                        ApplicationBean.Category.SMALL_PORT -> port443SmallFile
                        ApplicationBean.Category.LARGE_PORT -> port443LargeFile
                        else -> appObj.getString("randomdatafile")
                    }

                    name = if (category == ApplicationBean.Category.SMALL_PORT ||
                        category == ApplicationBean.Category.LARGE_PORT) {
                        context.getString(R.string.port_name, appObj.getString("name"))
                    } else {
                        appObj.getString("name")
                    }
                }
                apps.add(bean)
            }
        } catch (ex: Exception) {
            Log.e("SelectionViewModel", "Error parsing JSON", ex)
            throw ex
        } finally {
            `in`?.close()
        }
        return apps
    }

    companion object {
        private const val KEY_SELECTED_APPS = "selected_apps"
        private const val KEY_PAYLOAD_SIZE = "payload_size"
        private const val KEY_CARRIER_DISPLAY = "carrier_display"
        private const val KEY_APP_TOGGLE_STATES = "app_toggle_states"
    }
}