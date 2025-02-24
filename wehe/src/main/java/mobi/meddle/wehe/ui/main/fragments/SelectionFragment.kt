package mobi.meddle.wehe.ui.main.fragments

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import mobi.meddle.wehe.R
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.ui.main.MainActivity
import mobi.meddle.wehe.ui.main.viewmodels.SelectionViewModel
import mobi.meddle.wehe.ui.replay.ReplayActivity
import mobi.meddle.wehe.ui.theme.WEHE_BLUE
import mobi.meddle.wehe.ui.theme.WEHE_GREY
import mobi.meddle.wehe.ui.theme.WeheandroidTheme
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@AndroidEntryPoint
class SelectionFragment : Fragment() {
    private val viewModel: SelectionViewModel by activityViewModels()
    private var runPortTests = false
    private var TAG = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val bundle = arguments // Use arguments instead of requireArguments()
            TAG = bundle?.getString("TAG") ?: Consts.TAG_DIFFERENTIATION_TESTS
            runPortTests = bundle?.getBoolean("runPortTest") ?: false
            viewModel.setTestType(runPortTests)
        } catch (e: Exception) {
            Log.e("SelectionFragment", "Error in onCreate", e)
            // Set default values if bundle is null
            TAG = Consts.TAG_DIFFERENTIATION_TESTS
            runPortTests = false
            viewModel.setTestType(false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                // Wrap content in error boundary
                WeheandroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppSelection()
                    }
                }
            }
        }.also {
            // Move network check and data loading after view creation
            lifecycleScope.launch {
                try {
                    checkNetworkAndCarrier()
                    viewModel.loadInitialData(requireContext())
                } catch (e: Exception) {
                    Log.e("SelectionFragment", "Error in initialization", e)
                }
            }
        }
    }


    private fun checkNetworkAndCarrier() {
        try {
            val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val telephonyManager = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            if (connectivityManager == null || telephonyManager == null) {
                Log.e("SelectionFragment", "System services not available")
                return
            }

            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
            val carrierName = telephonyManager.networkOperatorName.takeIf { it.isNotEmpty() }
                ?: getString(R.string.unknown_carrier)

            if (networkInfo?.state == NetworkInfo.State.CONNECTED) {
                viewModel.setCarrierDisplay("WiFi")
                showWifiToast(carrierName)
            } else {
                viewModel.setCarrierDisplay(carrierName)
            }
        } catch (e: Exception) {
            Log.e("SelectionFragment", "Error checking network", e)
            viewModel.setCarrierDisplay(getString(R.string.unknown_carrier))
        }
    }

    private fun showWifiToast(carrier: String) {
        try {
            context?.let {
                val carrierDisplay = carrier.ifEmpty {
                    getString(R.string.your_carrier).lowercase(Locale.getDefault())
                }
                val text = String.format(getString(R.string.wifiWarning), carrierDisplay)
                Toast.makeText(it, text, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("SelectionFragment", "Error showing toast", e)
        }
    }

    @Composable
    fun AppSelection() {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> {
                    Row(modifier = Modifier.weight(1f)) {
                        WeheandroidTheme {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                HomeView(apps = uiState.apps)
                            }
                        }
                    }
                    RunTestsButton()
                }
            }
        }
    }

    @Composable
    fun HomeView(apps: List<ApplicationBean>) {
        val tabItems = getTabItems()
        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { tabItems.size }
        )
        val scope = rememberCoroutineScope()
        val payloadSize by viewModel.payloadSize.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                val filteredApps = filterAppsByTab(apps, index)
                AppsListComponent(filteredApps)
            }

            Row(
                Modifier
                    .background(color = Color.LightGray)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Payload size: $payloadSize MB",
                    modifier = Modifier.padding(8.dp)
                )
            }

            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabItems.forEachIndexed { index, item ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(item.title) }
                    )
                }
            }
        }
    }

    @Composable
    fun AppsListComponent(apps: List<ApplicationBean>) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            apps.forEach { app ->
                if (shouldShowApp(app)) {
                    AppCard(app)
                }
            }
        }
    }

    @Composable
    fun AppCard(app: ApplicationBean) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(4.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(
                        id = getImageResourceByName(app.image, LocalContext.current)
                    ),
                    contentDescription = "${app.name} icon",
                    modifier = Modifier.size(70.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Time: ${app.time} seconds",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Size: 2 x ${app.size} MB",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Switch(
                    checked = viewModel.appToggleStates[app] ?: false,
                    onCheckedChange = { viewModel.toggleApp(app, it) },
                    colors = SwitchDefaults.colors(
                        uncheckedTrackColor = WEHE_GREY
                    )
                )
            }
        }
    }

    @Composable
    fun RunTestsButton() {
        val context = LocalContext.current
        Button(
            onClick = {
                // Get filtered apps based on test type
                val filteredApps = viewModel.getFilteredSelectedApps()

                if (filteredApps.isEmpty()) {
                    Toast.makeText(
                        context,
                        getString(R.string.select_at_least_one),
                        Toast.LENGTH_LONG
                    ).show()
                    return@Button
                }

                val intent = Intent(context, ReplayActivity::class.java).apply {
                    putParcelableArrayListExtra(
                        "selectedApps",
                        ArrayList(filteredApps)
                    )
                    putExtra("runPortTests", runPortTests)
                    putExtra("carrier", viewModel.carrierDisplay.value)
                }
                startActivity(intent)
                requireActivity().overridePendingTransition(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = WEHE_BLUE,
                contentColor = Color.White
            )
        ) {
            Text(if (runPortTests) "Run Port Tests" else "Run Differentiation Tests")
        }
    }

    private fun getTabItems(): List<TabItem> =
        if (runPortTests) {
            listOf(
                TabItem("10 MB files"),
                TabItem("50 MB files")
            )
        } else {
            listOf(
                TabItem("Video"),
                TabItem("Music"),
                TabItem("Conferencing")
            )
        }

    private fun filterAppsByTab(
        apps: List<ApplicationBean>,
        tabIndex: Int
    ): List<ApplicationBean> =
        if (runPortTests) {
            when (tabIndex) {
                0 -> apps.filter { it.category == ApplicationBean.Category.SMALL_PORT }
                1 -> apps.filter { it.category == ApplicationBean.Category.LARGE_PORT }
                else -> emptyList()
            }
        } else {
            when (tabIndex) {
                0 -> apps.filter { it.category == ApplicationBean.Category.VIDEO }
                1 -> apps.filter { it.category == ApplicationBean.Category.MUSIC }
                2 -> apps.filter { it.category == ApplicationBean.Category.CONFERENCING }
                else -> emptyList()
            }
        }

    private fun shouldShowApp(app: ApplicationBean): Boolean {
        val countryCode = Locale.getDefault().country
        return when {
            app.isEnglishOnly -> countryCode == "US"
            app.isFrenchOnly -> countryCode == "FR"
            else -> true
        }
    }

    private fun getImageResourceByName(resourceName: String, context: Context): Int {
        return context.resources.getIdentifier(
            resourceName,
            "drawable",
            context.packageName
        )
    }

    data class TabItem(val title: String)

    override fun onResume() {
        super.onResume()
        updateActionBarAndNavigation()
    }

    private fun updateActionBarAndNavigation() {
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            if (runPortTests) "Port Tests" else "Differentiation Tests"

        (activity as? MainActivity)?.findViewById<NavigationView>(R.id.nav_view)?.let { nav ->
            val targetTitle = if (runPortTests) "Port Tests" else "Differentiation Tests"
            for (i in 0 until nav.menu.size()) {
                nav.menu.getItem(i).isChecked = nav.menu.getItem(i).title == targetTitle
            }
        }
    }

    companion object {
        /**
         * Creates a new instance of SelectionFragment with the specified parameters
         *
         * @param tag The tag for the fragment
         * @param runPortTest Whether to run port tests
         * @return A new instance of SelectionFragment
         */
        fun newInstance(tag: String, runPortTest: Boolean): SelectionFragment {
            val fragment = SelectionFragment()
            val args = Bundle()
            args.putString("TAG", tag)
            args.putBoolean("runPortTest", runPortTest)
            fragment.arguments = args
            return fragment
        }
    }
}