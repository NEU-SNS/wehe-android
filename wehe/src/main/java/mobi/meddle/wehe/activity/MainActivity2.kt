@file:OptIn(ExperimentalFoundationApi::class)

package mobi.meddle.wehe.activity

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialogDefaults.titleContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode.Companion.Color
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabItem
import mobi.meddle.wehe.R
import mobi.meddle.wehe.activity.ui.theme.WeheandroidTheme
import mobi.meddle.wehe.data.AppsListing
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.activity.ui.theme.WEHE_BLUE
import mobi.meddle.wehe.activity.ui.theme.WEHE_GREY
import mobi.meddle.wehe.bean.ApplicationBean

private var appsList = mutableListOf<AppsListing>()
@AndroidEntryPoint
class MainActivity2 : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appsViewModel = ViewModelProvider(this)[AppsViewModel::class.java]
        appsList = appsViewModel.appsList().toMutableList();
        val context: Context = this
        setContent {
            Scaffold(topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { /* do something */ }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Localized description",
                                tint = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    },
                    title = { Text("Wehe") },
                    colors = topAppBarColors(
                        containerColor = WEHE_BLUE,
                        titleContentColor = androidx.compose.ui.graphics.Color.White
                    )
                )
            }) {
                innerPadding ->
                Column (modifier = Modifier.padding(innerPadding)) {
                    AppSelection()
                }
            }
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
                    HomeView(apps = appsList)
                }
            };
        }
        Row(modifier = Modifier.height(70.dp)) {
            Button(
                onClick = {

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
                Text("Differentiation Tests")
            }
        }
    }
}

@Composable
fun AppDataObserver(appsViewModel: AppsViewModel) {
    val apps = appsViewModel.appsList();
}

@Composable
fun AppCard(app: AppsListing) {
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
                painter = painterResource(id = getImageResourceByName(app.image, LocalContext.current)),
                contentDescription = app.name + " icon",
                modifier = Modifier.size(70.dp)
            )

            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = app.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Time: ${app.time} seconds", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Size: 2 x ${app.size} MB", style = MaterialTheme.typography.bodyMedium,
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
                onCheckedChange = { toggleState = it },
                colors = SwitchDefaults.colors(uncheckedTrackColor = WEHE_GREY)
            )

        }
    }
}

@Composable
fun AppsListComponent(apps: List<AppsListing>) {
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
fun HomeView(apps: List<AppsListing>) {
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
                .weight(1f),
            verticalAlignment = Alignment.Top,
        ) { index ->
            if (index == 0) {
                AppsListComponent(apps.filter { it.category == ApplicationBean.Category.VIDEO})
            } else if (index == 1) {
                AppsListComponent(apps.filter { it.category == ApplicationBean.Category.MUSIC})
            } else if (index == 2) {
                AppsListComponent(apps.filter { it.category == ApplicationBean.Category.CONFERENCING})
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