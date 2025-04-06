//package mobi.meddle.wehe.ui.main.fragments
//
//import android.os.Bundle
//import android.util.Log
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.foundation.background
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.material3.darkColorScheme
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.toArgb
//import androidx.compose.ui.platform.ComposeView
//import androidx.compose.ui.unit.dp
//import androidx.fragment.app.Fragment
//import androidx.fragment.app.viewModels
//import com.google.android.material.navigation.NavigationView
//import dagger.hilt.android.AndroidEntryPoint
//import mobi.meddle.wehe.R
//import mobi.meddle.wehe.ui.main.MainActivity
//import mobi.meddle.wehe.ui.main.viewmodels.AboutViewModel
//
///**
// * Why Wehe item in navigation bar (menu.drawer_view.xml)
// * XML layout: fragment_about.xml
// */
//@AndroidEntryPoint
//class AboutFragment : Fragment() {
//
//
//    private val viewModel: AboutViewModel by viewModels { AboutViewModel.Factory()}
//    override fun onCreateView(
//        inflater: LayoutInflater, parent: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//      return ComposeView(requireContext()).apply {
//          setContent {
//              AboutScreen(viewModel)
//          }
//      }
//    }
//
//    @Composable
//    fun AboutScreen(viewModel: AboutViewModel) {
//
//        Column(
//            modifier = Modifier.fillMaxSize().padding(16.dp),
//            verticalArrangement = Arrangement.Top,
//            horizontalAlignment = Alignment.CenterHorizontally,
//        ) {
//            Text(text = String.format(getString(R.string.about_text), viewModel.versionName), style= MaterialTheme.typography.bodyLarge)
//        }
//    }
//
//    override fun onResume() {
//        super.onResume()
//        (activity as? AppCompatActivity)?.supportActionBar?.title = "Why Wehe"
//
//        // Update navigation drawer selection
//        (activity as? MainActivity)?.let { mainActivity ->
//            mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
//                // Clear all selections
//                for (i in 0 until navigationView.menu.size()) {
//                    navigationView.menu.getItem(i).isChecked = navigationView.menu.getItem(i).title == "Why Wehe"
//                }
//            }
//        }
//    }
//
//    companion object {
//        const val TAG: String = "AboutFragment"
//    }
//}

package mobi.meddle.wehe.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import mobi.meddle.wehe.R
import mobi.meddle.wehe.ui.main.MainActivity
import com.google.android.material.navigation.NavigationView
import mobi.meddle.wehe.BuildConfig

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the version name from BuildConfig
        val versionName = BuildConfig.VERSION_NAME

        // Get the TextView and set formatted text
        val aboutTextView: TextView = view.findViewById(R.id.aboutView)
        aboutTextView.text = getString(R.string.about_text, versionName)
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Why Wehe"

//        // Update navigation drawer selection
//        (activity as? MainActivity)?.let { mainActivity ->
//            mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
//                for (i in 0 until navigationView.menu.size()) {
//                    navigationView.menu.getItem(i).isChecked = navigationView.menu.getItem(i).title == "Why Wehe"
//                }
//            }
//        }
    }
}
