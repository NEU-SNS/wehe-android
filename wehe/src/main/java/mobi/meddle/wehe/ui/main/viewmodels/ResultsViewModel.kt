package mobi.meddle.wehe.ui.main.viewmodels

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.bean.Result
import mobi.meddle.wehe.ui.replay.ReplayActivity
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ResultsViewModel : ViewModel() {
    private val _results = MutableLiveData<List<Result>>()
    val results: LiveData<List<Result>> = _results

    fun loadResults(context: Context) {
        viewModelScope.launch {
            try {
                val history = context.getSharedPreferences(
                    ReplayActivity.STATUS, Context.MODE_PRIVATE)
                val resultsWithDate = JSONObject(history.getString("lastResult", "{}"))
                val resultsList = ArrayList<Result>()

                if (resultsWithDate.length() > 0) {
                    val iter = resultsWithDate.keys()
                    while (iter.hasNext()) {
                        val currentDate = iter.next()
                        val responses = resultsWithDate.getJSONArray(currentDate)
                        for (i in 0 until responses.length()) {
                            val response = responses.getJSONObject(i)
                            resultsList.add(parseResult(response, context))
                        }
                    }
                }
                resultsList.reverse()
                _results.value = resultsList
            } catch (e: JSONException) {
                Log.e("ResultsViewModel", "Error loading results", e)
            }
        }
    }

    private fun parseResult(response: JSONObject, context: Context): Result {
        return Result(
            isPortTest = response.getBoolean("isPort"),
            resultNameText = response.getString("appName"),
            appImage = response.getString("appImage"),
            dateText = formatDate(Date(response.getString("date").toLong()), context),
            differentiationText = response.getString("status"),
            appThroughput = response.getDouble("xput_avg_original"),
            nonAppThroughput = response.getDouble("xput_avg_test"),
            ipType = if (response.getBoolean("isIPv6")) "IPv6" else "IPv4",
            server = response.getString("server"),
            carrier = response.getString("carrier"),
            isTomography = response.has("tomographyNetwork"),
            differentiationNetwork = response.optString("tomographyNetwork", "")
        )
    }

    private fun formatDate(date: Date, context: Context): String {
        return if (DateUtils.isToday(date.time)) {
            val df = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
            String.format(context.getString(R.string.today), df.format(date))
        } else {
            val df = DateFormat.getDateTimeInstance(
                DateFormat.LONG, DateFormat.SHORT, Locale.getDefault())
            df.format(date)
        }
    }
}
