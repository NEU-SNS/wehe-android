package mobi.meddle.wehe.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import mobi.meddle.wehe.BuildConfig
import mobi.meddle.wehe.R
import mobi.meddle.wehe.activity.MainActivity

/**
 * Why Wehe item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_about.xml
 */
class AboutFragment : Fragment() {
    val TAG: String = "AboutFragment"
    override fun onCreateView(
        inflater: LayoutInflater, parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
      return ComposeView(requireContext()).apply {
          setContent {
              AboutScreen()
          }
      }
    }

//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        //set version number
//        val about = requireActivity().findViewById<TextView>(R.id.aboutView)
//        about.text = String.format(getString(R.string.about_text), BuildConfig.VERSION_NAME)
//    }
    @Composable
    fun AboutScreen() {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = String.format(getString(R.string.about_text), BuildConfig.VERSION_NAME), style= MaterialTheme.typography.bodyLarge)
        }
    }

    companion object {
        const val TAG: String = "AboutFragment"
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = "About"

        // Update navigation drawer selection
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
                // Clear all selections
                for (i in 0 until navigationView.menu.size()) {
                    navigationView.menu.getItem(i).isChecked = navigationView.menu.getItem(i).title == "Why Wehe"
                }
            }
        }
    }
}
