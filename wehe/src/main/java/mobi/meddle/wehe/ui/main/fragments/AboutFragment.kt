package mobi.meddle.wehe.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import mobi.meddle.wehe.R
import mobi.meddle.wehe.BuildConfig

/**
 * AboutFragment displays information about the application,
 * including the current version number.
 *
 * Responsibilities:
 * - Inflate the about fragment layout.
 * - Retrieve and display the app version from BuildConfig.
 * - Set the toolbar title to "Why Wehe" when the fragment is visible.
 *
 * This fragment is typically accessed from the navigation drawer
 * and provides users with details about the app's purpose and version.
 */
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
