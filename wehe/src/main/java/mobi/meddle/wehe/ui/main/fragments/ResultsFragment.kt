package mobi.meddle.wehe.ui.main.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.adapter.ResultsAdapter
import mobi.meddle.wehe.ui.main.MainActivity
import mobi.meddle.wehe.ui.main.viewmodels.ResultsViewModel
@AndroidEntryPoint
class ResultsFragment : Fragment() {
    private lateinit var viewModel: ResultsViewModel
    private lateinit var resultsAdapter: ResultsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setupNavigationSelection()
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ResultsViewModel::class.java]
        resultsAdapter = ResultsAdapter(requireContext())

        val resultsList = view.findViewById<ListView>(R.id.resultsListView)
        resultsList.adapter = resultsAdapter

        viewModel.results.observe(viewLifecycleOwner) { results ->
            resultsAdapter.updateResults(results)
        }

        viewModel.loadResults(requireContext())
    }

    private fun setupNavigationSelection() {
        val navigationView = requireActivity().findViewById<NavigationView>(R.id.nav_view)
        val menuItem = navigationView.menu.findItem(R.id.nav_results)
        if (!menuItem.isChecked) {
            menuItem.isChecked = true
        }
        requireActivity().title = menuItem.title
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Previous Results"

        // Update navigation drawer selection
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.findViewById<NavigationView>(R.id.nav_view)?.let { navigationView ->
                // Clear all selections
                for (i in 0 until navigationView.menu.size()) {
                    navigationView.menu.getItem(i).isChecked = navigationView.menu.getItem(i).title == "Previous Results"
                }
            }
        }
    }

    companion object {
        const val TAG: String = "ResultsFragment"
    }
}
