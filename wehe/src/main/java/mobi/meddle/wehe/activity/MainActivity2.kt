@file:OptIn(ExperimentalFoundationApi::class)

package mobi.meddle.wehe.activity

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import mobi.meddle.wehe.R
import mobi.meddle.wehe.activity.ui.theme.WeheandroidTheme
import mobi.meddle.wehe.data.AppsListing
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appsViewModel = ViewModelProvider(this)[AppsViewModel::class.java]
        setContent {
           AppDataObserver(appsViewModel = appsViewModel)
        }
    }
}

@Composable
fun AppDataObserver(appsViewModel: AppsViewModel) {
    val apps = appsViewModel.appsList();
    WeheandroidTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            HomeView(apps)
        }
    }
}

@Composable
fun AppCard(app: AppsListing) {
    fun getImageResourceByName(resourceName: String, context: Context): Int {
        return context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    }
    Card (
        modifier = Modifier.size(width = 400.dp, height = 100.dp)
    ) {
        Row {
            Text(text = app.name)
            Image(
                painter = painterResource(id = R.drawable.whatsapp),
                contentDescription = app.name + " icon",
                modifier = Modifier.size(50.dp)
            )
        }
    }
}
@Composable
fun AppsListComponent(apps: List<AppsListing>) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ){
        for (app in apps) {
            AppCard(app)
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {

    WeheandroidTheme {
        Greeting("Android")
    }
}

@Composable
fun HomeView(apps : List<AppsListing>) {
    val tabItems = listOf(
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
                .weight(1f)
        ) {index ->
            AppsListComponent(apps)
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