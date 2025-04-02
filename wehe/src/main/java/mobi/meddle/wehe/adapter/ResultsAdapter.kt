package mobi.meddle.wehe.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.model.Result
import java.util.Locale

/**
 * Used to display previous results in the previous results list view.
 */
class ResultsAdapter(private val context: Context) : BaseAdapter() {
    private var results: List<Result> = emptyList()

    fun updateResults(newResults: List<Result>) {
        results = newResults
        notifyDataSetChanged()
    }

    override fun getCount(): Int = results.size
    override fun getItem(position: Int): Result = results[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        val holder: ViewHolder

        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.results_item, parent, false)
            holder = ViewHolder()
            holder.apply {
                resultNameText = view.findViewById(R.id.resultNameText)
                dateText = view.findViewById(R.id.dateText)
                differentiationText = view.findViewById(R.id.differentiationText)
                appThroughput = view.findViewById(R.id.appThroughput)
                nonAppThroughput = view.findViewById(R.id.nonappThroughput)
                appImageView = view.findViewById(R.id.appImageView)
                ipType = view.findViewById(R.id.ipTypeValue)
                server = view.findViewById(R.id.serverValue)
                carrier = view.findViewById(R.id.carrierValue)
            }
            view.tag = holder
        } else {
            holder = view.tag as ViewHolder
        }

        val current = getItem(position)

        // Set basic information
        holder.apply {
            dateText.text = current.dateText
            ipType.text = current.ipType
            server.text = current.server
            carrier.text = current.carrier
            resultNameText.text = current.resultNameText
        }

        // Handle throughput labels and visibility
        val appLabel = view!!.findViewById<TextView>(R.id.appThroughputNameText)
        val nonAppLabel = view.findViewById<TextView>(R.id.nonappThroughputNameText)

        holder.nonAppThroughput.visibility = View.VISIBLE
        holder.appThroughput.visibility = View.VISIBLE
        nonAppLabel.visibility = View.VISIBLE

        when {
            current.isTomography -> {
                if (current.differentiationNetwork.isEmpty()) {
                    appLabel.setText(R.string.tomo_failed_desc)
                } else {
                    appLabel.text = String.format(
                        context.getString(R.string.tomo_succ_desc),
                        current.differentiationNetwork
                    )
                }
                nonAppLabel.visibility = View.GONE
                holder.appThroughput.visibility = View.GONE
                holder.nonAppThroughput.visibility = View.GONE
            }
            current.isPortTest -> {
                appLabel.text = String.format(
                    context.getString(R.string.port_throughput),
                    current.resultNameText.split(" ")[1]
                )
                nonAppLabel.text = String.format(
                    context.getString(R.string.port_throughput),
                    "443"
                )
            }
            else -> {
                appLabel.setText(R.string.app_throughput)
                nonAppLabel.setText(R.string.nonapp_throughput)
            }
        }

        // Set app image
        val appImageDrawableId = context.resources.getIdentifier(
            current.appImage,
            "drawable",
            context.packageName
        )
        holder.appImageView.setImageDrawable(
            ContextCompat.getDrawable(context, appImageDrawableId)
        )

        // Set differentiation status and color
        when (current.differentiationText) {
            "has diff" -> {
                holder.differentiationText.setText(R.string.has_diff)
                holder.differentiationText.setTextColor(
                    ContextCompat.getColor(context, R.color.red)
                )
            }
            "no diff" -> {
                holder.differentiationText.setText(R.string.no_diff)
                holder.differentiationText.setTextColor(
                    ContextCompat.getColor(context, R.color.forestGreen)
                )
            }
            "inconclusive" -> {
                holder.differentiationText.text =
                    context.getString(R.string.inconclusive).split(",")[0]
                holder.differentiationText.setTextColor(
                    ContextCompat.getColor(context, R.color.orange2)
                )
            }
            "tomo failed" -> {
                holder.differentiationText.setText(R.string.tomo_failed)
                holder.differentiationText.setTextColor(
                    ContextCompat.getColor(context, R.color.red)
                )
            }
            "tomo succ" -> {
                holder.differentiationText.setText(R.string.tomo_succ)
                holder.differentiationText.setTextColor(
                    ContextCompat.getColor(context, R.color.forestGreen)
                )
            }
            else -> {
                holder.differentiationText.text = current.differentiationText
                holder.differentiationText.setTextColor(
                    ContextCompat.getColor(context, R.color.orange2)
                )
            }
        }

        // Set throughput values
        holder.appThroughput.text = String.format(
            Locale.getDefault(),
            context.getString(R.string.throughput),
            current.appThroughput
        )
        holder.nonAppThroughput.text = String.format(
            Locale.getDefault(),
            context.getString(R.string.throughput),
            current.nonAppThroughput
        )

        return view
    }

    private class ViewHolder {
        lateinit var resultNameText: TextView
        lateinit var appImageView: ImageView
        lateinit var dateText: TextView
        lateinit var differentiationText: TextView
        lateinit var appThroughput: TextView
        lateinit var nonAppThroughput: TextView
        lateinit var ipType: TextView
        lateinit var server: TextView
        lateinit var carrier: TextView
    }
}
