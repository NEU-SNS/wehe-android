package mobi.meddle.wehe.ui.main.fragments

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.ui.main.MainActivity

/**
 * How it Works item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_functionality.xml
 */
@AndroidEntryPoint
class FunctionalityFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, parent: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        val menuItem = navigationView.menu.findItem(R.id.nav_functionality)
//        if (!menuItem.isChecked) {
//            menuItem.setChecked(true)
//        }
        requireActivity().title = menuItem.title
        return inflater.inflate(R.layout.fragment_functionality, parent, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val textView = view.findViewById<TextView>(R.id.functionalityView)
        textView.movementMethod = ScrollingMovementMethod()
    }

    companion object {
        const val TAG: String = "FunctionalityFragment"
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = "How it Works"

//        // Update navigation drawer selection
//        (activity as? MainActivity)?.let { mainActivity ->
//            mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
//                // Clear all selections
//                for (i in 0 until navigationView.menu.size()) {
//                    navigationView.menu.getItem(i).isChecked =
//                        navigationView.menu.getItem(i).title == "How it Works"
//                }
//            }
//        }
    }
}

