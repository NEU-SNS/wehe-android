@file:OptIn(ExperimentalFoundationApi::class)

package mobi.meddle.wehe.fragment

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import mobi.meddle.wehe.R
import mobi.meddle.wehe.activity.MainActivity
import mobi.meddle.wehe.activity.ReplayActivity
import mobi.meddle.wehe.activity.ui.theme.WEHE_BLUE
import mobi.meddle.wehe.activity.ui.theme.WEHE_GREY
import mobi.meddle.wehe.activity.ui.theme.WeheandroidTheme
import mobi.meddle.wehe.bean.ApplicationBean
import mobi.meddle.wehe.constant.Consts
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

class SelectionFragment : Fragment() {
    private var app_beans: ArrayList<ApplicationBean> = ArrayList<ApplicationBean>()//all the apps/ports to display on the page
//    val TAG: String = "SelectionFragment"
    private var context: Context? = null
    private var runPortTests = false
    private var carrierDisplay: String? = null //cell carrier or "Wi-Fi"
    private val selectedApps: java.util.ArrayList<ApplicationBean> = ArrayList<ApplicationBean>()
    val currentLocale = Locale.getDefault()
    val countryCode = currentLocale.country
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("SelectionFragment", "onCreate called")
        super.onCreate(savedInstanceState)
        val bundle = requireArguments()
        TAG = bundle.getString("TAG") ?: Consts.TAG_DIFFERENTIATION_TESTS

//        try {
//            context = getContext()
//
//            //caller passes whether this fragment should show apps or ports
//            val bundle = requireArguments()
//            runPortTests = bundle.getBoolean("runPortTest")
//
//            // This method parses JSON file which contains details for different
//            // Applications and returns HashMap of ApplicationBean type
//            app_beans = parseAppJSON()
//
//            // Display a warning if the user is on wifi
//            val connectivityManager = context
//                ?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//            val telephonyManager = requireContext()
//                .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//
//            if (connectivityManager == null) {
//                return
//            }
//
//            //TODO: Switch to non-deprecated library without increasing minSDK?
//            val networkInfo = connectivityManager
//                .getNetworkInfo(ConnectivityManager.TYPE_WIFI)
//            val carrierName = if (telephonyManager != null) {
//                telephonyManager.networkOperatorName
//            } else {
//                getString(R.string.your_carrier)
//            }
//
//            if (networkInfo != null && networkInfo.state == NetworkInfo.State.CONNECTED) {
//                carrierDisplay = "WiFi"
//                showWifiToast(carrierName)
//            } else {
//                carrierDisplay = carrierName
//            }
//        } catch (e: Exception) {
//            Log.e("selectionFragment", "Something went wrong creating selectionFragment", e)
//        }
    }

    /**
     * Display a warning if the user is on Wi-Fi.
     * @param carrier the user's phone carrier
     */
    private fun showWifiToast(carrier: String) {
        var carrier = carrier
        if (carrier == "") {
            carrier = getString(R.string.your_carrier).lowercase(Locale.getDefault())
        }
        val text: CharSequence = String.format(getString(R.string.wifiWarning), carrier)
        val toast = Toast.makeText(context, text, Toast.LENGTH_LONG)
        toast.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("SelectionFragment", "onCreateView called")
        try {
            context = getContext()

            //caller passes whether this fragment should show apps or ports
            val bundle = requireArguments()
            runPortTests = bundle.getBoolean("runPortTest")

            // This method parses JSON file which contains details for different
            // Applications and returns HashMap of ApplicationBean type
            app_beans = parseAppJSON()

            // Display a warning if the user is on wifi
            val connectivityManager = context
                ?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val telephonyManager = requireContext()
                .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            //TODO: Switch to non-deprecated library without increasing minSDK?
            val networkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            val carrierName = if (telephonyManager != null) {
                telephonyManager.networkOperatorName
            } else {
                getString(R.string.your_carrier)
            }

            if (networkInfo != null && networkInfo.state == NetworkInfo.State.CONNECTED) {
                carrierDisplay = "WiFi"
                showWifiToast(carrierName)
            } else {
                carrierDisplay = carrierName
            }
        } catch (e: Exception) {
            Log.e("selectionFragment", "Something went wrong creating selectionFragment", e)
        }
        return ComposeView(requireContext()).apply {
            setContent {
                AppSelection()
            }
        }
    }
    @Composable
    fun AppSelection() {
        val context = LocalContext.current

        // Use LaunchedEffect for cleanup when the composable is removed
        DisposableEffect(key1 = true) {
            onDispose {
                // Perform any cleanup here if needed
                // For example, clear resources or stop any ongoing operations
                // You can log or call any cleanup functions here if necessary
                Toast.makeText(context, "AppSelection composable is being disposed", Toast.LENGTH_SHORT).show()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.weight(1f)) {
                WeheandroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeView(apps = app_beans)
                    }
                }
            }
            // Button for running the tests
            Row(modifier = Modifier.height(70.dp)) {
                Button(
                    onClick = {
                        if (selectedApps.size == 0) {
                            Toast.makeText(
                                context,
                                getString(R.string.select_at_least_one),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        val intent = Intent(context, ReplayActivity::class.java)
                        intent.putParcelableArrayListExtra(
                            "selectedApps",
                           selectedApps
                        )
                        intent.putExtra("runPortTests", runPortTests)
                        intent.putExtra("carrier", carrierDisplay)
                        startActivity(intent)
                        requireActivity().overridePendingTransition(
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WEHE_BLUE,
                        contentColor = Color.White,
                        disabledContainerColor = WEHE_GREY,
                        disabledContentColor = Color.White,
                    )
                ) {
                    if (runPortTests) Text("Port Tests") else
                    Text("Differentiation Tests")
                }
            }
        }
    }
    @Composable
    fun AppCard(app: ApplicationBean,
                updatePayloadSize: (Int) -> Unit,
                appToggleStates: MutableMap<ApplicationBean, Boolean>) {

        fun getImageResourceByName(resourceName: String, context: Context): Int {
            return context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Image(
                    painter = painterResource(
                        id = getImageResourceByName(
                            app.image,
                            LocalContext.current
                        )
                    ),
                    contentDescription = app.name + " icon",
                    modifier = Modifier.size(70.dp)
                )

                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Time: ${app.time} seconds",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Size: 2 x ${app.size} MB",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val toggleState = appToggleStates[app] ?: false
                Spacer(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )

                Switch(
                    onCheckedChange = { newChekcedState ->
                        appToggleStates[app] = newChekcedState
                        if (newChekcedState) {
                            selectedApps.add(app)
                            updatePayloadSize(app.size)
                        }
                        if (!newChekcedState) {
                            selectedApps.remove(app)
                            updatePayloadSize(-app.size)
                        }
                        },
                    checked = toggleState,
                    colors = SwitchDefaults.colors(uncheckedTrackColor = WEHE_GREY)
                )
            }
        }
    }

    @Composable
    fun AppsListComponent(apps: List<ApplicationBean>, updatePayloadSize: (Int) -> Unit, appToggleStates: MutableMap<ApplicationBean, Boolean>) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            for (app in apps) {
                if (app.isEnglishOnly) {
                    if ( countryCode != "US") {
                        continue
                    }
                }
                else if (app.isFrenchOnly) {
                    if (countryCode != "FR") {
                        continue
                    }
                }
                AppCard(app, updatePayloadSize, appToggleStates)
            }
        }
    }

    @Composable
    fun HomeView(apps: List<ApplicationBean>) {
        var payloadSize by remember { mutableStateOf(0) }
        val updatePayloadSize = { newSize: Int -> payloadSize += newSize }
        val tabItems: List<TabItem>
        val appToggleStates = remember { mutableStateMapOf<ApplicationBean, Boolean>() }

        LaunchedEffect(apps) {
            apps.forEach { app ->
                appToggleStates[app] = false
            }
        }

        if (runPortTests) {
            tabItems = listOf(
                TabItem(
                    title = "10 MB files",
                ),
                TabItem(
                    title = "50 MB files"
                ),
            )
        } else {
           tabItems = listOf(
                TabItem(
                    title = "Video",
                ),
                TabItem(
                    title = "Music"
                ),
                TabItem(
                    title = "Conferencing"
                ),
            )
        }

        var selectedTabIndex by remember { mutableStateOf(0) }
        val pagerState = rememberPagerState {
            tabItems.size
        }
        LaunchedEffect(selectedTabIndex) {
            pagerState.animateScrollToPage(selectedTabIndex)
        }
        LaunchedEffect(pagerState.currentPage) {
            selectedTabIndex = pagerState.currentPage
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top,
            ) { index ->
                if (runPortTests) {
                    if (index == 0) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.SMALL_PORT }, updatePayloadSize, appToggleStates)
                    } else if (index == 1) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.LARGE_PORT }, updatePayloadSize, appToggleStates)
                    }

                } else {
                    if (index == 0) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.VIDEO }, updatePayloadSize, appToggleStates)
                    } else if (index == 1) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.MUSIC }, updatePayloadSize, appToggleStates)
                    } else if (index == 2) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.CONFERENCING }, updatePayloadSize, appToggleStates)
                    }
                }
            }
            Row (Modifier.background(color = Color.LightGray).fillMaxWidth()) {
               Text(text = "Payload size: ${payloadSize} MB")
            }
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabItems.forEachIndexed { index, item ->
                    Tab(
                        selected = index == selectedTabIndex,
                        onClick = { selectedTabIndex = index },
                        text = { Text(item.title) },
                    )
                }
            }
        }

    }

    data class TabItem(val title: String)

    /**
     * This method parses apps_list.json file located in assets folder.
     * This file has all the basic details of apps for replay.
     *
     * @return ArrayList of all apps/ports in the JSON file
     */
    fun parseAppJSON(): java.util.ArrayList<ApplicationBean> {
        val apps = java.util.ArrayList<ApplicationBean>()
        var `in`: BufferedReader? = null
        try {
            val buf = StringBuilder()
            if (context == null) {
                throw NullPointerException("context is null")
            }
            val assets = requireContext().assets
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

            var appObj: JSONObject
            var bean: ApplicationBean
            for (i in 0 until jArray.length()) {
                appObj = jArray.getJSONObject(i)
                bean = ApplicationBean()

                bean.dataFile = appObj.getString("datafile")
                bean.size = appObj.getInt("size") //JSON size only for 1 replay
                bean.time = appObj.getInt("time") * 2 //JSON time only for 1 replay
                bean.image = appObj.getString("image")
                if (appObj.has("englishOnly")) {
                    bean.isEnglishOnly = appObj.getBoolean("englishOnly")
                } else if (appObj.has("frenchOnly")) {
                    bean.isFrenchOnly = appObj.getBoolean("frenchOnly")
                }

                val cat = ApplicationBean.Category.valueOf(appObj.getString("category"))
                bean.category = cat
                //"random" test for ports is port 443
                if (cat == ApplicationBean.Category.SMALL_PORT) {
                    bean.randomDataFile = port443SmallFile
                } else if (cat == ApplicationBean.Category.LARGE_PORT) {
                    bean.randomDataFile = port443LargeFile
                } else {
                    bean.randomDataFile = appObj.getString("randomdatafile")
                }

                if (cat == ApplicationBean.Category.SMALL_PORT || cat == ApplicationBean.Category.LARGE_PORT) {
                    bean.name =
                        String.format(
                            getString(R.string.port_name),
                            appObj.getString(("name"))
                        )
                } else {
                    bean.name = appObj.getString("name") //app names stored in JSON file
                }

                apps.add(bean)
            }
        } catch (ex: IOException) {
            Log.e("selectionFragment", "IOException reading file " + Consts.APPS_FILENAME, ex)
        } catch (ex: JSONException) {
            Log.e(
                "selectionFragment",
                "JSONException parsing JSON file " + Consts.APPS_FILENAME,
                ex
            )
        } finally {
            if (`in` != null) {
                try {
                    `in`.close()
                } catch (e: IOException) {
                    Log.w("selectionFragment", "Issue closing file", e)
                }
            }
        }
        return apps
    }

    companion object {
        lateinit var TAG : String

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SelectionFragment", "onDestroy called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("SelectionFragment", "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d("SelectionFragment", "onStop called")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("SelectionFragment", "onDestroyView called")
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
//            override fun handleOnBackPressed() {
//                if (shouldInterceptBackPress()) {
//                    // Perform back navigation function
//                    isEnabled = false
//                    Toast.makeText(context, "Back pressed in Fragment", Toast.LENGTH_SHORT).show()
//                    requireActivity().onBackPressed()
//                } else {
//                    // Allow default behavior
//                    isEnabled = false
//                    requireActivity().onBackPressed()
//                }
//            }
//        })
//    }
//
//    /**
//     * Intercepts back press only if navigating to other fragments
//     */
//    private fun shouldInterceptBackPress(): Boolean {
//        return true
//    }

    override fun onResume() {
        super.onResume()
        if (runPortTests) {
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Port Tests"

            // Update navigation drawer selection
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
                    // Clear all selections
                    for (i in 0 until navigationView.menu.size()) {
                        navigationView.menu.getItem(i).isChecked = navigationView.menu.getItem(i).title == "Port Tests"
                    }
                }
            }

        }
        else {
            (activity as? AppCompatActivity)?.supportActionBar?.title = "Differentiation Tests"

            // Update navigation drawer selection
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
                    // Clear all selections
                    for (i in 0 until navigationView.menu.size()) {
                        navigationView.menu.getItem(i).isChecked = navigationView.menu.getItem(i).title == "Differentiation Tests"
                    }
                }
            }
        }
    }

}
