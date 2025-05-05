package mobi.meddle.wehe.ui.main.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R

/**
 * View Online Dashboard item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_dashboard.xml
 */
@AndroidEntryPoint
class DashboardFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        val menuItem = navigationView.menu.findItem(R.id.nav_dashboard)
//        if (!menuItem.isChecked) {
//            menuItem.setChecked(true)
//        }
        requireActivity().title = menuItem.title
        return inflater.inflate(R.layout.fragment_dashboard, parent, false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dashboardView = view.findViewById<WebView>(R.id.dashboardView)
        dashboardView.settings.javaScriptEnabled = true
        dashboardView.settings.domStorageEnabled = true
        dashboardView.overScrollMode = WebView.OVER_SCROLL_NEVER
        val url = getString(R.string.dashboard_url)
        dashboardView.loadUrl(url)
    }

    override fun onResume() {
        super.onResume()
        if (activity is AppCompatActivity) {
            val actionBar = (activity as AppCompatActivity).supportActionBar
            if (actionBar != null) {
                actionBar.title = "Online Dashboard"
            }
        }

//        // Update navigation drawer selection
//        if (activity is MainActivity) {
//            val mainActivity = activity as MainActivity?
//            val navigationView = mainActivity!!.findViewById<NavigationView>(R.id.nav_view)
//            if (navigationView != null) {
//                // Clear all selections
//                for (i in 0 until navigationView.menu.size()) {
//                    val menuItem = navigationView.menu.getItem(i)
//                    menuItem.setChecked(
//                        Objects.requireNonNull(menuItem.title).toString() == "View Online Dashboard"
//                    )
//                }
//            }
//        }
    }
//
//    companion object {
//        const val TAG: String = "DashboardFragment"
//    }
}
