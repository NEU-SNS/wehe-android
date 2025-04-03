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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import mobi.meddle.wehe.R
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.ui.main.MainActivity
import mobi.meddle.wehe.ui.main.viewmodels.SelectionViewModel
import mobi.meddle.wehe.ui.replay.ReplayActivity
import mobi.meddle.wehe.ui.theme.WEHE_BLUE
import mobi.meddle.wehe.ui.theme.WEHE_GREY
import mobi.meddle.wehe.ui.theme.WeheandroidTheme
import java.util.Locale

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
            viewModel.setCurrentTabIndex(0)
        } catch (e: Exception) {
            Log.e("SelectionFragment", "Error in onCreate", e)
            // Set default values if bundle is null
            TAG = Consts.TAG_DIFFERENTIATION_TESTS
            runPortTests = false
            viewModel.setTestType(false)
            viewModel.setCurrentTabIndex(0)
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
                val carrierDisplay = carrier.ifEmpty{
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

        // Track page changes and update ViewModel
        LaunchedEffect(pagerState.currentPage) {
            viewModel.setCurrentTabIndex(pagerState.currentPage)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                val filteredApps = filterAppsByTab(apps, index)
                AppsGridView(
                    apps = filteredApps,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = "Total size: $payloadSize MB",
                    modifier = Modifier.padding(8.dp),
                    color = (MaterialTheme.colorScheme.primary),
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
    fun AppsGridView(apps: List<ApplicationBean>, modifier: Modifier = Modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
        ) {
            val visibleApps = apps.filter { shouldShowApp(it) }
            items(
                items = visibleApps,
                key = { it.name }
            ) { app ->
                AppGridItem(app = app)
            }
        }
    }

    @Composable
    fun AppGridItem(app: ApplicationBean) {
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
                ) {
                    Image(
                        painter = painterResource(
                            id = getImageResourceByName(app.image, LocalContext.current)
                        ),
                        contentDescription = "${app.name} icon",
                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(16.dp))
                    )

                    Switch(
                        checked = viewModel.appToggleStates[app] ?: false,
                        onCheckedChange = { viewModel.toggleApp(app, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedTrackColor = Color.LightGray
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 12.dp, end = 2.dp)
                    )
                }
        }
    }



//
//    @Composable
//    fun AppCard(app: ApplicationBean) {
//        Card(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(100.dp)
//                .padding(4.dp),
//            shape = MaterialTheme.shapes.medium,
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.surface
//            )
//        ) {
//            Row(
//                modifier = Modifier
//                    .padding(16.dp)
//                    .fillMaxWidth(),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Image(
//                    painter = painterResource(
//                        id = getImageResourceByName(app.image, LocalContext.current)
//                    ),
//                    contentDescription = "${app.name} icon",
//                    modifier = Modifier.size(70.dp)
//                )
//
//                Column(
//                    modifier = Modifier
//                        .weight(1f)
//                        .padding(start = 16.dp)
//                ) {
//                    Text(
//                        text = app.name,
//                        style = MaterialTheme.typography.titleMedium
//                    )
//                    Text(
//                        text = "Time: ${app.time} seconds",
//                        style = MaterialTheme.typography.bodyMedium
//                    )
//                    Text(
//                        text = "Size: 2 x ${app.size} MB",
//                        style = MaterialTheme.typography.bodyMedium
//                    )
//                }
//
//                Switch(
//                    checked = viewModel.appToggleStates[app] ?: false,
//                    onCheckedChange = { viewModel.toggleApp(app, it) },
//                    colors = SwitchDefaults.colors(
//                        uncheckedTrackColor = WEHE_GREY
//                    )
//                )
//            }
//        }
//    }

    @Composable
    fun RunTestsButton() {
        val context = LocalContext.current
        val currentTabIndex by viewModel.currentTabIndex.collectAsState()

        Button(
            onClick = {
                // Get filtered apps based on test type AND current tab
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
        updateViewModelValues()
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

    private fun updateViewModelValues() {
        // Reset the ViewModel to match current test type
        viewModel.setTestType(runPortTests)
        // Make sure UI is refreshed with the correct data for this test type
        lifecycleScope.launch {
            try {
                // This will reload app data filtered by the current test type
                viewModel.loadInitialData(requireContext())
            } catch (e: Exception) {
                Log.e("SelectionFragment", "Error reloading data", e)
            }
        }
    }
}

//package mobi.meddle.wehe.ui.main.fragments
//
//import android.content.Context
//import android.content.Intent
//import android.net.ConnectivityManager
//import android.net.NetworkInfo
//import android.os.Bundle
//import android.telephony.TelephonyManager
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.grid.GridCells
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.foundation.lazy.grid.items
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.ComposeView
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.res.painterResource
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.activityViewModels
//import androidx.lifecycle.lifecycleScope
//import com.google.android.material.navigation.NavigationView
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.launch
//import mobi.meddle.wehe.R
//import mobi.meddle.wehe.constant.Consts
//import mobi.meddle.wehe.data.model.ApplicationBean
//import mobi.meddle.wehe.ui.main.MainActivity
//import mobi.meddle.wehe.ui.main.viewmodels.SelectionViewModel
//import mobi.meddle.wehe.ui.replay.ReplayActivity
//import mobi.meddle.wehe.ui.theme.WEHE_BLUE
//import mobi.meddle.wehe.ui.theme.WeheandroidTheme
//import java.util.Locale
//
//@AndroidEntryPoint
//class SelectionFragment : Fragment() {
//    private val viewModel: SelectionViewModel by activityViewModels()
//    private var runPortTests = false
//    private var TAG = ""
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        try {
//            val bundle = arguments
//            TAG = bundle?.getString("TAG") ?: Consts.TAG_DIFFERENTIATION_TESTS
//            runPortTests = bundle?.getBoolean("runPortTest") ?: false
//            viewModel.setTestType(runPortTests)
//            viewModel.setCurrentTabIndex(0)
//        } catch (e: Exception) {
//            Log.e("SelectionFragment", "Error in onCreate", e)
//            TAG = Consts.TAG_DIFFERENTIATION_TESTS
//            runPortTests = false
//            viewModel.setTestType(false)
//            viewModel.setCurrentTabIndex(0)
//        }
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        return ComposeView(requireContext()).apply {
//            setContent {
//                WeheandroidTheme {
//                    Surface(
//                        modifier = Modifier.fillMaxSize(),
//                        color = MaterialTheme.colorScheme.background
//                    ) {
//                        AppSelection()
//                    }
//                }
//            }
//        }.also {
//            lifecycleScope.launch {
//                try {
//                    checkNetworkAndCarrier()
//                    viewModel.loadInitialData(requireContext())
//                } catch (e: Exception) {
//                    Log.e("SelectionFragment", "Error in initialization", e)
//                }
//            }
//        }
//    }
//
//    private fun checkNetworkAndCarrier() {
//        try {
//            val connectivityManager = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
//            val telephonyManager = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
//
//            if (connectivityManager == null || telephonyManager == null) {
//                Log.e("SelectionFragment", "System services not available")
//                return
//            }
//
//            val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
//            val carrierName = telephonyManager.networkOperatorName.takeIf { it.isNotEmpty() }
//                ?: getString(R.string.unknown_carrier)
//
//            if (networkInfo?.state == NetworkInfo.State.CONNECTED) {
//                viewModel.setCarrierDisplay("WiFi")
//                showWifiToast(carrierName)
//            } else {
//                viewModel.setCarrierDisplay(carrierName)
//            }
//        } catch (e: Exception) {
//            Log.e("SelectionFragment", "Error checking network", e)
//            viewModel.setCarrierDisplay(getString(R.string.unknown_carrier))
//        }
//    }
//
//    private fun showWifiToast(carrier: String) {
//        try {
//            context?.let {
//                val carrierDisplay = carrier.ifEmpty{
//                    getString(R.string.your_carrier).lowercase(Locale.getDefault())
//                }
//                val text = String.format(getString(R.string.wifiWarning), carrierDisplay)
//                Toast.makeText(it, text, Toast.LENGTH_LONG).show()
//            }
//        } catch (e: Exception) {
//            Log.e("SelectionFragment", "Error showing toast", e)
//        }
//    }
//
//    @Composable
//    fun AppSelection() {
//        val uiState by viewModel.uiState.collectAsState()
//        val context = LocalContext.current
//
//        Column(
//            modifier = Modifier.fillMaxSize(),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            when {
//                uiState.isLoading -> {
//                    CircularProgressIndicator(
//                        modifier = Modifier.align(Alignment.CenterHorizontally)
//                    )
//                }
//                uiState.error != null -> {
//                    Text(
//                        text = "Error: ${uiState.error}",
//                        color = Color.Red,
//                        modifier = Modifier.padding(16.dp)
//                    )
//                }
//                else -> {
//                    AppsGridView(
//                        apps = uiState.apps,
//                        modifier = Modifier.weight(1f)
//                    )
//                    TotalSizeDisplay()
//                    CategoryTabs()
//                    RunTestsButton()
//                }
//            }
//        }
//    }
//
//    @Composable
//    fun AppsGridView(apps: List<ApplicationBean>, modifier: Modifier = Modifier) {
//        val currentTabIndex by viewModel.currentTabIndex.collectAsState()
//        val filteredApps = filterAppsByTab(apps, currentTabIndex)
//        val visibleApps = filteredApps.filter { shouldShowApp(it) }
//
//        LazyVerticalGrid(
//            columns = GridCells.Fixed(2),
//            modifier = modifier.fillMaxWidth(),
//            contentPadding = PaddingValues(8.dp)
//        ) {
//            items(
//                items = visibleApps,
//                key = { it.name }
//            ) { app ->
//                AppGridItem(app = app)
//            }
//        }
//    }
//
//    @Composable
//    fun AppGridItem(app: ApplicationBean) {
//        val isChecked = remember { mutableStateOf(viewModel.appToggleStates[app] ?: false) }
//
//        // Update viewModel when state changes
//        LaunchedEffect(isChecked.value) {
//            viewModel.toggleApp(app, isChecked.value)
//        }
//
//        // Update local state when viewModel changes
//        LaunchedEffect(viewModel.appToggleStates[app]) {
//            viewModel.appToggleStates[app]?.let {
//                isChecked.value = it
//            }
//        }
//
//        Column(
//            horizontalAlignment = Alignment.CenterHorizontally,
//            modifier = Modifier.padding(8.dp)
//        ) {
//            Image(
//                painter = painterResource(
//                    id = getImageResourceByName(app.image, LocalContext.current)
//                ),
//                contentDescription = "${app.name} icon",
//                modifier = Modifier.size(50.dp)
//            )
//
//            Switch(
//                checked = isChecked.value,
//                onCheckedChange = { isChecked.value = it },
//                colors = SwitchDefaults.colors(
//                    checkedThumbColor = Color.White,
//                    checkedTrackColor = Color(0xFF4CAF50), // Green color
//                    uncheckedTrackColor = Color.LightGray
//                ),
//                modifier = Modifier.padding(vertical = 4.dp)
//            )
//        }
//    }
//
//    @Composable
//    fun TotalSizeDisplay() {
//        val selectedApps = viewModel.getFilteredSelectedApps()
//        val totalSize = selectedApps.sumOf { it.size } * 2
//
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(vertical = 8.dp)
//        ) {
//            Text(
//                text = "Total size: $totalSize MB",
//                color = Color.Blue,
//                modifier = Modifier.align(Alignment.Center),
//                fontSize = 16.sp
//            )
//        }
//    }
//
//    @OptIn(ExperimentalMaterial3Api::class)
//    @Composable
//    fun CategoryTabs() {
//        val currentTabIndex by viewModel.currentTabIndex.collectAsState()
//        val tabItems = getTabItems()
//        val scope = rememberCoroutineScope()
//
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(horizontal = 4.dp, vertical = 4.dp),
//            horizontalArrangement = Arrangement.SpaceEvenly
//        ) {
//            tabItems.forEachIndexed { index, item ->
//                ElevatedFilterChip(
//                    selected = currentTabIndex == index,
//                    onClick = {
//                        scope.launch {
//                            viewModel.setCurrentTabIndex(index)
//                        }
//                    },
//                    label = { Text(item.title) },
//                    colors = FilterChipDefaults.elevatedFilterChipColors(
//                        selectedContainerColor = Color.LightGray,
//                        selectedLabelColor = Color.Black
//                    )
//                )
//            }
//        }
//    }
//
//    @Composable
//    fun RunTestsButton() {
//        val context = LocalContext.current
//
//        Button(
//            onClick = {
//                val filteredApps = viewModel.getFilteredSelectedApps()
//
//                if (filteredApps.isEmpty()) {
//                    Toast.makeText(
//                        context,
//                        getString(R.string.select_at_least_one),
//                        Toast.LENGTH_LONG
//                    ).show()
//                    return@Button
//                }
//
//                val intent = Intent(context, ReplayActivity::class.java).apply {
//                    putParcelableArrayListExtra(
//                        "selectedApps",
//                        ArrayList(filteredApps)
//                    )
//                    putExtra("runPortTests", runPortTests)
//                    putExtra("carrier", viewModel.carrierDisplay.value)
//                }
//                startActivity(intent)
//                requireActivity().overridePendingTransition(
//                    R.anim.slide_in_right,
//                    R.anim.slide_out_left
//                )
//            },
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(48.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = WEHE_BLUE,
//                contentColor = Color.White
//            )
//        ) {
//            Text(
//                text = if (runPortTests) "Run tests" else "Run tests",
//                textAlign = TextAlign.Center
//            )
//        }
//    }
//
//    private fun getTabItems(): List<TabItem> =
//        if (runPortTests) {
//            listOf(
//                TabItem("10 MB files"),
//                TabItem("50 MB files")
//            )
//        } else {
//            listOf(
//                TabItem("Video streaming"),
//                TabItem("Music streaming"),
//                TabItem("Videoconferencing")
//            )
//        }
//
//    private fun filterAppsByTab(
//        apps: List<ApplicationBean>,
//        tabIndex: Int
//    ): List<ApplicationBean> =
//        if (runPortTests) {
//            when (tabIndex) {
//                0 -> apps.filter { it.category == ApplicationBean.Category.SMALL_PORT }
//                1 -> apps.filter { it.category == ApplicationBean.Category.LARGE_PORT }
//                else -> emptyList()
//            }
//        } else {
//            when (tabIndex) {
//                0 -> apps.filter { it.category == ApplicationBean.Category.VIDEO }
//                1 -> apps.filter { it.category == ApplicationBean.Category.MUSIC }
//                2 -> apps.filter { it.category == ApplicationBean.Category.CONFERENCING }
//                else -> emptyList()
//            }
//        }
//
//    private fun shouldShowApp(app: ApplicationBean): Boolean {
//        val countryCode = Locale.getDefault().country
//        return when {
//            app.isEnglishOnly -> countryCode == "US"
//            app.isFrenchOnly -> countryCode == "FR"
//            else -> true
//        }
//    }
//
//    private fun getImageResourceByName(resourceName: String, context: Context): Int {
//        return context.resources.getIdentifier(
//            resourceName,
//            "drawable",
//            context.packageName
//        )
//    }
//
//    data class TabItem(val title: String)
//
//    override fun onResume() {
//        super.onResume()
//        updateActionBarAndNavigation()
//        updateViewModelValues()
//    }
//
//    private fun updateActionBarAndNavigation() {
//        (activity as? AppCompatActivity)?.supportActionBar?.title =
//            if (runPortTests) "Port Tests" else "Differentiation Tests"
//
//        (activity as? MainActivity)?.findViewById<NavigationView>(R.id.nav_view)?.let { nav ->
//            val targetTitle = if (runPortTests) "Port Tests" else "Differentiation Tests"
//            for (i in 0 until nav.menu.size()) {
//                nav.menu.getItem(i).isChecked = nav.menu.getItem(i).title == targetTitle
//            }
//        }
//    }
//
//    private fun updateViewModelValues() {
//        viewModel.setTestType(runPortTests)
//        lifecycleScope.launch {
//            try {
//                viewModel.loadInitialData(requireContext())
//            } catch (e: Exception) {
//                Log.e("SelectionFragment", "Error reloading data", e)
//            }
//        }
//    }
//}