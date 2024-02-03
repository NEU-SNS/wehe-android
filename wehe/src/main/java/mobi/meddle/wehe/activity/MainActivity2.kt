@file:OptIn(ExperimentalFoundationApi::class)

package mobi.meddle.wehe.activity

import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import kotlinx.coroutines.launch
import mobi.meddle.wehe.activity.ui.theme.WeheandroidTheme
import mobi.meddle.wehe.constant.Consts.APPS_FILENAME
import mobi.meddle.wehe.data.AppsList
import mobi.meddle.wehe.data.AppsListing
import mobi.meddle.wehe.data.AppsSource
import mobi.meddle.wehe.data.DefaultAppsRepository


class MainActivity2 : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val am: AssetManager = applicationContext.getAssets()
        val json = am.open(APPS_FILENAME).bufferedReader().use { it.readText() }
        val gson = Gson()
        val appsList = gson.fromJson(json, AppsList::class.java)
        val apps: List<AppsListing> = appsList.apps
        setContent {
            WeheandroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeView(apps)
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppsListing) {
    Card (modifier = Modifier.size(width = 400.dp, height = 100.dp)) {
        Text(text = app.name)
    }
}
@Composable
fun AppsListComponent(apps: List<AppsListing>) {
    Column(){
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

    val repo = DefaultAppsRepository(AppsSource())
    WeheandroidTheme {
        Greeting("Android")
    }
}

@Composable
fun HomeView(apps : List<AppsListing>) {
    val pagerState = rememberPagerState(pageCount = {
        3
    })
    val coroutineScope = rememberCoroutineScope();
    Column {
        HorizontalPager(state = pagerState) { page ->
            val scrollState = rememberScrollState()
            Column (modifier = Modifier.height(700.dp).verticalScroll(scrollState)){
                Greeting("Android")
                AppsListComponent(apps)
            }
        }
        
        TabRow(
            selectedTabIndex = 1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(text = "Video") },
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(text = "Music") },
                unselectedContentColor = Color.Gray
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text(text = "Conferencing") },
                unselectedContentColor = Color.Gray
            )
        }
//        Text(text = "testing")
    }
}