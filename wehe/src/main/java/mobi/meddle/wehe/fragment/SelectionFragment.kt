@file:OptIn(ExperimentalFoundationApi::class)

package mobi.meddle.wehe.activity

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import mobi.meddle.wehe.R
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
    val TAG: String = "SelectionFragment"
    private var context: Context? = null
    private var runPortTests = false
    private var carrierDisplay: String? = null //cell carrier or "Wi-Fi"
    private val selectedApps: java.util.ArrayList<ApplicationBean> = ArrayList<ApplicationBean>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

            if (connectivityManager == null) {
                return
            }

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
        return ComposeView(requireContext()).apply {
            setContent {
                AppSelection()
            }
        }
    }
    @Composable
    fun AppSelection() {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.height(720.dp)) {
                WeheandroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        HomeView(apps = app_beans!!)
                    }
                };
            }
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
                        contentColor = androidx.compose.ui.graphics.Color.White,
                        disabledContainerColor = WEHE_GREY,
                        disabledContentColor = androidx.compose.ui.graphics.Color.White,
                    )
                ) {
                    if (runPortTests) Text("Port Tests") else
                    Text("Differentiation Tests")
                }
            }
        }
    }
    @Composable
    fun AppCard(app: ApplicationBean) {
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

                var toggleState by remember { mutableStateOf(false) }
                Spacer(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) // height and background only for demonstration


                Switch(
                    checked = toggleState,
                    onCheckedChange = { newChekcedState ->
                        if (newChekcedState) {
                            selectedApps.add(app)
                        }
                        if (!newChekcedState) {
                            selectedApps.remove(app)
                        }
                        toggleState = newChekcedState },
                    colors = SwitchDefaults.colors(uncheckedTrackColor = WEHE_GREY)
                )
            }
        }
    }

    @Composable
    fun AppsListComponent(apps: List<ApplicationBean>) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            for (app in apps) {
                AppCard(app)
            }
        }
    }

    @Composable
    fun HomeView(apps: List<ApplicationBean>) {
        var tabItems: List<TabItem>
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
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.SMALL_PORT })
                    } else if (index == 1) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.LARGE_PORT })
                    }

                } else {
                    if (index == 0) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.VIDEO })
                    } else if (index == 1) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.MUSIC })
                    } else if (index == 2) {
                        AppsListComponent(apps.filter { it.category == ApplicationBean.Category.CONFERENCING })
                    }
                }
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
    private fun parseAppJSON(): java.util.ArrayList<ApplicationBean> {
        val apps = java.util.ArrayList<ApplicationBean>()
        var `in`: BufferedReader? = null
        try {
            val buf = StringBuilder()
            if (context == null) {
                throw NullPointerException("context is null");
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
                        kotlin.String.format(
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
        lateinit var TAG: String
    }
}
