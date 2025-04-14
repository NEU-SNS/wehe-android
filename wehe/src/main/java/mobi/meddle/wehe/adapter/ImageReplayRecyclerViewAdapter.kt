//package mobi.meddle.wehe.adapter
//
//import android.app.AlertDialog
//import android.content.Intent
//import android.net.Uri
//import android.util.TypedValue
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Button
//import android.widget.ImageButton
//import android.widget.ImageView
//import android.widget.TextView
//import androidx.core.content.res.ResourcesCompat
//import androidx.recyclerview.widget.RecyclerView
//import mobi.meddle.wehe.R
//import mobi.meddle.wehe.constant.Consts
//import mobi.meddle.wehe.data.model.ApplicationBean
//import mobi.meddle.wehe.ui.replay.ReplayActivity
//import java.util.Locale
//import kotlin.math.min
//
///**
// * Used to display the apps/ports while the replays are running.
// * XML layout: list_item_replay.xml; Goes in appsRecyclerView in activity_replay.xml
// */
//class ImageReplayRecyclerViewAdapter(//list of apps/ports
//    private var dataList: List<ApplicationBean>, //replay activity, to get resources
//    private val replayAct: ReplayActivity,
//    private val runPortTests: Boolean
//) : RecyclerView.Adapter<ImageReplayRecyclerViewAdapter.ViewHolder>() {
//    private var isTomography = false
//
//    fun setTomography(isTomography: Boolean) {
//        this.isTomography = isTomography
//        notifyDataSetChanged()
//    }
//
//    override fun getItemCount(): Int {
//        return dataList.size + 1
//    }
//
//    // Update the app list
//    fun updateApps(newApps: ArrayList<ApplicationBean>) {
//        this.dataList = newApps
//        notifyDataSetChanged()
//    }
//
//    //runs every time an app/port scrolls onto screen to load that app/port's view
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        holder.xputOriginalTextView.visibility = View.GONE
//        holder.xputOriginalValueTextView.visibility = View.GONE
//        holder.xputTestTextView.visibility = View.GONE
//        holder.xputTestValueTextView.visibility = View.GONE
//        holder.arcepLogo.visibility = View.GONE
//        holder.alertArcep.visibility = View.GONE
//        holder.alertFCC.visibility = View.GONE
//        holder.imageButton.visibility = View.GONE
//
//        if (position == 0) {
//            holder.tvAppStatus.visibility = View.GONE
//            holder.tvAppTime.visibility = View.GONE
//            holder.tvAppSize.visibility = View.GONE
//            holder.img.visibility = View.GONE
//            var description = if (isTomography) {
//                replayAct.getString(R.string.tomo_test_desc)
//            } else if (runPortTests) {
//                replayAct.getString(R.string.normal_port_desc)
//            } else {
//                replayAct.getString(R.string.normal_app_desc)
//            }
//            description = "" //TODO: comment out when time to add test descriptions
//            holder.tvAppName.text = description
//            holder.tvAppName.setTextSize(
//                TypedValue.COMPLEX_UNIT_PX,
//                replayAct.resources.getDimension(R.dimen.text_medium)
//            )
//            return
//        } else if (holder.tvAppStatus.visibility == View.GONE) {
//            holder.tvAppName.visibility = View.VISIBLE
//            holder.tvAppStatus.visibility = View.VISIBLE
//            holder.tvAppTime.visibility = View.VISIBLE
//            holder.img.visibility = View.VISIBLE
//            holder.tvAppName.setTextSize(
//                TypedValue.COMPLEX_UNIT_PX,
//                replayAct.resources.getDimension(R.dimen.text_small)
//            )
//        }
//        val app = dataList[position - 1]
//        val res = replayAct.resources
//        //load name
//        holder.tvAppName.text = app.name
//        //load time for only apps
//        if (runPortTests) {
//            holder.tvAppTime.visibility = View.GONE
//        } else {
//            val time = if (Consts.TIMEOUT_ENABLED) min(
//                app.time.toDouble(),
//                (Consts.REPLAY_APP_TIMEOUT * 2).toDouble()
//            )
//                .toInt() else
//                app.time
//            holder.tvAppTime.text = String.format(
//                Locale.getDefault(),
//                res.getString(R.string.replay_time), time
//            )
//            holder.tvAppTime.visibility = View.VISIBLE
//        }
//        //load size
//        val numTests = 2 * (if (app.isTomography) Consts.NUM_TOMOGRAPHY_TESTS else 1)
//        holder.tvAppSize.text = String.format(
//            Locale.getDefault(),
//            res.getString(R.string.replay_size), numTests, app.size
//        )
//        holder.tvAppSize.visibility = View.VISIBLE
//
//        // here we set different color for different results
//        val red = res.getColor(R.color.red)
//        val green = res.getColor(R.color.forestGreen)
//        val yellow = res.getColor(R.color.orange2)
//        val blue = res.getColor(R.color.blue0)
//
//        //load arcep alert button and logo
//        if (app.arcepNeedsAlerting) {
//            holder.arcepLogo.visibility = View.VISIBLE
//            holder.alertArcep.visibility = View.VISIBLE
//            holder.alertArcep.setOnClickListener {
//                app.arcepNeedsAlerting = false
//                holder.arcepLogo.visibility = View.GONE
//                holder.alertArcep.visibility = View.GONE
//
//                //open arcep site in browser; tests will continue running in background
//                val i = Intent(Intent.ACTION_VIEW)
//                i.setData(Uri.parse(Consts.ARCEP_URL))
//                replayAct.startActivity(i)
//            }
//        } else if (app.isAlertFCC) {
//            holder.alertFCC.visibility = View.VISIBLE
//            holder.alertFCC.setOnClickListener {
//                app.isAlertFCC = false
//                holder.alertFCC.visibility = View.GONE
//
//                //open arcep site in browser; tests will continue running in background
//                val i = Intent(Intent.ACTION_VIEW)
//                i.setData(Uri.parse(Consts.FCC_URL))
//                replayAct.startActivity(i)
//            }
//        }
//        //load status
//        holder.tvAppStatus.text = app.status
//        val status = app.status.trim { it <= ' ' }
//        if (status == res.getString(R.string.no_diff)) {
//            holder.tvAppStatus.setTextColor(green)
//            holder.xputOriginalTextView.visibility = View.VISIBLE
//            holder.xputOriginalValueTextView.visibility = View.VISIBLE
//            holder.xputTestTextView.visibility = View.VISIBLE
//            holder.xputTestValueTextView.visibility = View.VISIBLE
//        } else if (status == res.getString(R.string.has_diff)) {
//            holder.tvAppStatus.setTextColor(red)
//            if (app.error != res.getString(R.string.not_all_tcp_sent_text)) {
//                holder.xputOriginalTextView.visibility = View.VISIBLE
//                holder.xputOriginalValueTextView.visibility = View.VISIBLE
//                holder.xputTestTextView.visibility = View.VISIBLE
//                holder.xputTestValueTextView.visibility = View.VISIBLE
//            }
//            holder.imageButton.visibility = View.VISIBLE
//            holder.imageButton.setOnClickListener {
//                makeInfoBox(
//                    res.getString(R.string.has_diff),
//                    app.error
//                )
//            }
//        } else if (status == res.getString(R.string.inconclusive)) {
//            holder.tvAppStatus.setTextColor(yellow)
//            if (app.error == "") {
//                holder.xputOriginalTextView.visibility = View.VISIBLE
//                holder.xputOriginalValueTextView.visibility = View.VISIBLE
//                holder.xputTestTextView.visibility = View.VISIBLE
//                holder.xputTestValueTextView.visibility = View.VISIBLE
//            } else {
//                holder.imageButton.visibility = View.VISIBLE
//                holder.imageButton.setOnClickListener {
//                    makeInfoBox(
//                        res.getString(R.string.inconclusive).split(",".toRegex())
//                            .dropLastWhile { it.isEmpty() }.toTypedArray()[0],
//                        app.error
//                    )
//                }
//            }
//        } else if (status == res.getString(R.string.tomo_failed)) {
//            holder.tvAppStatus.setTextColor(red)
//            holder.xputOriginalTextView.visibility = View.VISIBLE
//        } else if (status == res.getString(R.string.tomo_succ)) {
//            holder.tvAppStatus.setTextColor(green)
//            holder.xputOriginalTextView.visibility = View.VISIBLE
//        } else {
//            holder.tvAppStatus.setTextColor(blue)
//        }
//
//        //load throughputs
//        val appName = if (runPortTests) String.format(res.getString(R.string.port_name),
//            app.name.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
//                .toTypedArray()[1]) else app.name
//        val port443 = res.getString(R.string.port_name, "443")
//        val xputOriginalLabel = res.getString(R.string.xputOriginal, appName)
//        val xputTestLabel = if (runPortTests) res.getString(
//            R.string.xputOriginal,
//            port443
//        ) else res.getString(R.string.xputTest, appName)
//        val throughputFormat = replayAct.resources.getString(R.string.throughput)
//        val xputOriginal =
//            String.format(Locale.getDefault(), throughputFormat, app.originalThroughput)
//        val xputTest = String.format(Locale.getDefault(), throughputFormat, app.randomThroughput)
//
//        if (app.isTomography) {
//            if (app.differentiationNetwork == "") {
//                holder.xputOriginalTextView.setText(R.string.tomo_failed_desc)
//            } else {
//                holder.xputOriginalTextView.text = String.format(
//                    res.getString(R.string.tomo_succ_desc),
//                    app.differentiationNetwork
//                )
//            }
//        } else {
//            holder.xputOriginalTextView.text = xputOriginalLabel
//            holder.xputTestTextView.text = xputTestLabel
//            holder.xputOriginalValueTextView.text = xputOriginal
//            holder.xputTestValueTextView.text = xputTest
//        }
//
//        holder.img.setImageDrawable(
//            ResourcesCompat.getDrawable(
//                replayAct.resources,
//                res.getIdentifier(
//                    app.image,
//                    "drawable", replayAct.packageName
//                ), null
//            )
//        )
//    }
//
//    override fun onCreateViewHolder(
//        parent: ViewGroup, viewType: Int
//    ): ViewHolder {
//        val view = LayoutInflater.from(parent.context).inflate(
//            R.layout.list_item_replay, parent, false
//        )
//
//        return ViewHolder(view)
//    }
//
//    /**
//     * Display an information box if user clicks the "i" icon.
//     *
//     * @param title title of the info box
//     * @param msg   the message to display to the user
//     */
//    private fun makeInfoBox(title: String, msg: String) {
//        AlertDialog.Builder(replayAct, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
//            .setTitle(title)
//            .setMessage(msg)
//            .setNeutralButton(
//                android.R.string.ok
//            ) { dialog, which ->
//                // do nothing
//            }.show()
//    }
//
//    /**
//     * The view that the user sees for each app/port being run in the tests.
//     */
//    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        // each data item is just a string in this case
//        val tvAppName: TextView = view.findViewById(R.id.resultNameText)
//        val tvAppSize: TextView = view.findViewById(R.id.appSize)
//        val tvAppTime: TextView = view.findViewById(R.id.appTime)
//        val tvAppStatus: TextView = view.findViewById(R.id.appStatusTextView)
//        val xputOriginalTextView: TextView = view.findViewById(R.id.xputOriginal)
//        val xputOriginalValueTextView: TextView =
//            view.findViewById(R.id.xputOriginalValue)
//        val xputTestTextView: TextView = view.findViewById(R.id.xputTest)
//        val xputTestValueTextView: TextView = view.findViewById(R.id.xputTestValue)
//        val img: ImageView = view.findViewById(R.id.appImageView)
//        val imageButton: ImageButton = view.findViewById(R.id.ib_reverse_engineer_info)
//        val arcepLogo: ImageView = view.findViewById(R.id.arcepLogoImageView)
//        val alertArcep: Button = view.findViewById(R.id.reportToArcep)
//        val alertFCC: Button = view.findViewById(R.id.alertFcc)
//    }
//}


package mobi.meddle.wehe.adapter

import android.app.AlertDialog
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
import mobi.meddle.wehe.ui.replay.ReplayActivity
import java.util.Locale
import kotlin.math.min

/**
 * Used to display the apps/ports while the replays are running.
 * XML layout: list_item_replay.xml; Goes in appsRecyclerView in activity_replay.xml
 */
class ImageReplayRecyclerViewAdapter(
    private var dataList: List<ApplicationBean>, //replay activity, to get resources
    private val replayAct: ReplayActivity,
    private val runPortTests: Boolean
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
        val red = ContextCompat.getColor(this, R.color.red)
        val green = ContextCompat.getColor(this, R.color.forestGreen)
        val yellow = ContextCompat.getColor(this, R.color.orange2)
        val blue = ContextCompat.getColor(this, R.color.blue0)

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
        AlertDialog.Builder(replayAct, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle(title)
            .setMessage(msg)
            .setNeutralButton(
                android.R.string.ok
            ) { dialog, which ->
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