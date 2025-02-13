package mobi.meddle.wehe.ui.main.viewmodels

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.telephony.TelephonyManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.constant.Consts
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class SelectionViewModel : ViewModel() {
    private val _appBeans = MutableStateFlow<List<ApplicationBean>>(emptyList())
    val appBeans: StateFlow<List<ApplicationBean>> = _appBeans.asStateFlow()

    private val _selectedApps = MutableStateFlow<ArrayList<ApplicationBean>>(ArrayList())
    val selectedApps: StateFlow<ArrayList<ApplicationBean>> = _selectedApps.asStateFlow()

    private val _payloadSize = MutableStateFlow(0)
    val payloadSize: StateFlow<Int> = _payloadSize.asStateFlow()

    private val _carrierDisplay = MutableStateFlow<String?>(null)
    val carrierDisplay: StateFlow<String?> = _carrierDisplay.asStateFlow()

    private val _appToggleStates = MutableStateFlow<Map<ApplicationBean, Boolean>>(emptyMap())
    val appToggleStates: StateFlow<Map<ApplicationBean, Boolean>> = _appToggleStates.asStateFlow()

    fun initializeApps(context: Context) {
        viewModelScope.launch {
            _appBeans.value = parseAppJSON(context)
            setupCarrierInfo(context)
            initializeToggleStates()
        }
    }

    private fun initializeToggleStates() {
        val newToggleStates = _appBeans.value.associateWith { false }.toMutableMap()
        _appToggleStates.value = newToggleStates
    }

    fun updateAppSelection(app: ApplicationBean, isSelected: Boolean) {
        val currentToggleStates = _appToggleStates.value.toMutableMap()
        currentToggleStates[app] = isSelected
        _appToggleStates.value = currentToggleStates

        val currentSelectedApps = _selectedApps.value
        if (isSelected) {
            currentSelectedApps.add(app)
            updatePayloadSize(app.size)
        } else {
            currentSelectedApps.remove(app)
            updatePayloadSize(-app.size)
        }
        _selectedApps.value = currentSelectedApps
    }

    private fun updatePayloadSize(size: Int) {
        _payloadSize.value += size
    }

    fun setupCarrierInfo(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val carrierName = telephonyManager.networkOperatorName ?: context.getString(R.string.your_carrier)

        val isWifiConnected = networkInfo?.state == NetworkInfo.State.CONNECTED
        if (isWifiConnected) {
            _carrierDisplay.value = "WiFi"
        } else {
            _carrierDisplay.value = carrierName
        }
        return isWifiConnected
    }

    private fun parseAppJSON(context: Context): ArrayList<ApplicationBean> {
        val apps = ArrayList<ApplicationBean>()
        try {
            val buf = StringBuilder()
            val assets = context.assets
            val json = assets.open(Consts.APPS_FILENAME)
            BufferedReader(InputStreamReader(json)).use { reader ->
                var str: String?
                while (reader.readLine().also { str = it } != null) {
                    buf.append(str)
                }
            }

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

                    name = if (category in listOf(ApplicationBean.Category.SMALL_PORT, ApplicationBean.Category.LARGE_PORT)) {
                        String.format(context.getString(R.string.port_name), appObj.getString("name"))
                    } else {
                        appObj.getString("name")
                    }
                }
                apps.add(bean)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return apps
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SelectionViewModel::class.java)) {
                return SelectionViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
