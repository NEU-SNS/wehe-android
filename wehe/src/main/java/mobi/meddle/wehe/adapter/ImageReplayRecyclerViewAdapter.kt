package mobi.meddle.wehe.adapter

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import mobi.meddle.wehe.R
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.ui.replay.activity.BackgroundReplayActivity
import mobi.meddle.wehe.util.THEME_DEVICE_DEFAULT_LIGHT_DIALOG
import java.util.Locale
import kotlin.math.min

/**
 * Used to display the apps/ports while the replays are running.
 * XML layout: list_item_replay.xml; Goes in appsRecyclerView in activity_replay.xml
 */
class ImageReplayRecyclerViewAdapter(
    private val context: Context,
    private var dataList: List<ApplicationBean>, //replay activity, to get resources
    private val replayAct: BackgroundReplayActivity,
    private val runPortTests: Boolean,
) : RecyclerView.Adapter<ImageReplayRecyclerViewAdapter.ViewHolder>() {
    private var isTomography = false

    fun setTomography(isTomography: Boolean) {
        this.isTomography = isTomography
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        // Return just the actual number of items in the list
        return dataList.size
    }

    // Update the app list
    fun updateApps(newApps: ArrayList<ApplicationBean>) {
        this.dataList = newApps
        notifyDataSetChanged()
    }

    //runs every time an app/port scrolls onto screen to load that app/port's view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.xputOriginalTextView.visibility = View.GONE
        holder.xputOriginalValueTextView.visibility = View.GONE
        holder.xputTestTextView.visibility = View.GONE
        holder.xputTestValueTextView.visibility = View.GONE
        holder.arcepLogo.visibility = View.GONE
        holder.alertArcep.visibility = View.GONE
        holder.alertFCC.visibility = View.GONE
        holder.imageButton.visibility = View.GONE

        // Make all these views visible by default
        holder.tvAppName.visibility = View.VISIBLE
        holder.tvAppStatus.visibility = View.VISIBLE
        holder.tvAppTime.visibility = View.VISIBLE
        holder.img.visibility = View.VISIBLE

        // Set text size to normal size
        holder.tvAppName.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            replayAct.resources.getDimension(R.dimen.text_small)
        )

        val app = dataList[position]
        val res = replayAct.resources
        //load name
        holder.tvAppName.text = app.name
        //load time for only apps
        if (runPortTests) {
            holder.tvAppTime.visibility = View.GONE
        } else {
            val time = if (Consts.TIMEOUT_ENABLED) min(
                app.time.toDouble(),
                (Consts.REPLAY_APP_TIMEOUT * 2).toDouble()
            )
                .toInt() else
                app.time
            holder.tvAppTime.text = String.format(
                Locale.getDefault(),
                res.getString(R.string.replay_time), time
            )
            holder.tvAppTime.visibility = View.VISIBLE
        }
        //load size
        val numTests = 2 * (if (app.isTomography) Consts.NUM_TOMOGRAPHY_TESTS else 1)
        holder.tvAppSize.text = String.format(
            Locale.getDefault(),
            res.getString(R.string.replay_size), numTests, app.size
        )
        holder.tvAppSize.visibility = View.VISIBLE

        // here we set different color for different results
        val red = ContextCompat.getColor(context, R.color.red)
        val green = ContextCompat.getColor(context, R.color.forestGreen)
        val yellow = ContextCompat.getColor(context, R.color.orange2)
        val blue = ContextCompat.getColor(context, R.color.blue0)

        //load arcep alert button and logo
        if (app.arcepNeedsAlerting) {
            holder.arcepLogo.visibility = View.VISIBLE
            holder.alertArcep.visibility = View.VISIBLE
            holder.alertArcep.setOnClickListener {
                app.arcepNeedsAlerting = false
                holder.arcepLogo.visibility = View.GONE
                holder.alertArcep.visibility = View.GONE

                //open arcep site in browser; tests will continue running in background
                val i = Intent(Intent.ACTION_VIEW)
                i.setData(Uri.parse(Consts.ARCEP_URL))
                replayAct.startActivity(i)
            }
        } else if (app.isAlertFCC) {
            holder.alertFCC.visibility = View.VISIBLE
            holder.alertFCC.setOnClickListener {
                app.isAlertFCC = false
                holder.alertFCC.visibility = View.GONE

                //open arcep site in browser; tests will continue running in background
                val i = Intent(Intent.ACTION_VIEW)
                i.setData(Uri.parse(Consts.FCC_URL))
                replayAct.startActivity(i)
            }
        }
        //load status
        holder.tvAppStatus.text = app.status
        val status = app.status.trim { it <= ' ' }
        if (status == res.getString(R.string.no_diff)) {
            holder.tvAppStatus.setTextColor(green)
            holder.xputOriginalTextView.visibility = View.VISIBLE
            holder.xputOriginalValueTextView.visibility = View.VISIBLE
            holder.xputTestTextView.visibility = View.VISIBLE
            holder.xputTestValueTextView.visibility = View.VISIBLE
        } else if (status == res.getString(R.string.has_diff)) {
            holder.tvAppStatus.setTextColor(red)
            if (app.error != res.getString(R.string.not_all_tcp_sent_text)) {
                holder.xputOriginalTextView.visibility = View.VISIBLE
                holder.xputOriginalValueTextView.visibility = View.VISIBLE
                holder.xputTestTextView.visibility = View.VISIBLE
                holder.xputTestValueTextView.visibility = View.VISIBLE
            }
            holder.imageButton.visibility = View.VISIBLE
            holder.imageButton.setOnClickListener {
                makeInfoBox(
                    res.getString(R.string.has_diff),
                    app.error
                )
            }
        } else if (status == res.getString(R.string.inconclusive)) {
            holder.tvAppStatus.setTextColor(yellow)
            if (app.error == "") {
                holder.xputOriginalTextView.visibility = View.VISIBLE
                holder.xputOriginalValueTextView.visibility = View.VISIBLE
                holder.xputTestTextView.visibility = View.VISIBLE
                holder.xputTestValueTextView.visibility = View.VISIBLE
            } else {
                holder.imageButton.visibility = View.VISIBLE
                holder.imageButton.setOnClickListener {
                    makeInfoBox(
                        res.getString(R.string.inconclusive).split(",".toRegex())
                            .dropLastWhile { it.isEmpty() }.toTypedArray()[0],
                        app.error
                    )
                }
            }
        } else if (status == res.getString(R.string.tomo_failed)) {
            holder.tvAppStatus.setTextColor(red)
            holder.xputOriginalTextView.visibility = View.VISIBLE
        } else if (status == res.getString(R.string.tomo_succ)) {
            holder.tvAppStatus.setTextColor(green)
            holder.xputOriginalTextView.visibility = View.VISIBLE
        } else {
            holder.tvAppStatus.setTextColor(blue)
        }

        //load throughputs
        val appName = if (runPortTests) String.format(res.getString(R.string.port_name),
            app.name.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[1]) else app.name
        val port443 = res.getString(R.string.port_name, "443")
        val xputOriginalLabel = res.getString(R.string.xputOriginal, appName)
        val xputTestLabel = if (runPortTests) res.getString(
            R.string.xputOriginal,
            port443
        ) else res.getString(R.string.xputTest, appName)
        val throughputFormat = replayAct.resources.getString(R.string.throughput)
        val xputOriginal =
            String.format(Locale.getDefault(), throughputFormat, app.originalThroughput)
        val xputTest = String.format(Locale.getDefault(), throughputFormat, app.randomThroughput)

        if (app.isTomography) {
            if (app.differentiationNetwork == "") {
                holder.xputOriginalTextView.setText(R.string.tomo_failed_desc)
            } else {
                holder.xputOriginalTextView.text = String.format(
                    res.getString(R.string.tomo_succ_desc),
                    app.differentiationNetwork
                )
            }
        } else {
            holder.xputOriginalTextView.text = xputOriginalLabel
            holder.xputTestTextView.text = xputTestLabel
            holder.xputOriginalValueTextView.text = xputOriginal
            holder.xputTestValueTextView.text = xputTest
        }

        holder.img.setImageDrawable(
            ResourcesCompat.getDrawable(
                replayAct.resources,
                res.getIdentifier(
                    app.image,
                    "drawable", replayAct.packageName
                ), null
            )
        )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup, viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.list_item_replay, parent, false
        )

        return ViewHolder(view)
    }

    /**
     * Display an information box if user clicks the "i" icon.
     *
     * @param title title of the info box
     * @param msg   the message to display to the user
     */
    private fun makeInfoBox(title: String, msg: String) {
        AlertDialog.Builder(replayAct, THEME_DEVICE_DEFAULT_LIGHT_DIALOG)
            .setTitle(title)
            .setMessage(msg)
            .setNeutralButton(
                android.R.string.ok
            ) { _, _ ->
                // do nothing
            }.show()
    }

    /**
     * The view that the user sees for each app/port being run in the tests.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // each data item is just a string in this case
        val tvAppName: TextView = view.findViewById(R.id.resultNameText)
        val tvAppSize: TextView = view.findViewById(R.id.appSize)
        val tvAppTime: TextView = view.findViewById(R.id.appTime)
        val tvAppStatus: TextView = view.findViewById(R.id.appStatusTextView)
        val xputOriginalTextView: TextView = view.findViewById(R.id.xputOriginal)
        val xputOriginalValueTextView: TextView =
            view.findViewById(R.id.xputOriginalValue)
        val xputTestTextView: TextView = view.findViewById(R.id.xputTest)
        val xputTestValueTextView: TextView = view.findViewById(R.id.xputTestValue)
        val img: ImageView = view.findViewById(R.id.appImageView)
        val imageButton: ImageButton = view.findViewById(R.id.ib_reverse_engineer_info)
        val arcepLogo: ImageView = view.findViewById(R.id.arcepLogoImageView)
        val alertArcep: Button = view.findViewById(R.id.reportToArcep)
        val alertFCC: Button = view.findViewById(R.id.alertFcc)
    }
}