package io.github.andreygladkov.inkphone.calllog

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkSegments
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.twoLines
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.TabScreen
import io.github.andreygladkov.inkphone.call.CallLauncher
import io.github.andreygladkov.inkphone.call.callButton
import io.github.andreygladkov.inkphone.contacts.ContactCardActivity
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class CallLogScreen(private val activity: Activity, private val onMissedRead: () -> Unit) : LinearLayout(activity), TabScreen {
    private val repository = CallLogRepository(activity.contentResolver)
    private val labels = CallLabels(activity)
    private val permission = PermissionGate(activity, Manifest.permission.READ_CALL_LOG, REQUEST_CALL_LOG)
    private val main = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val list = InkPagedList(activity)
    private val message = InkTextView(activity).apply { gravity = Gravity.CENTER }
    private val action = InkButton(activity).apply { visibility = GONE }
    private val state = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        visibility = GONE
        setBackgroundColor(InkStyle.of(activity).background)
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = padding
        })
    }
    private var missedOnly = false
    private var entries: List<CallLogEntry> = emptyList()
    private var isLoaded = false
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(reload)
            main.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    init {
        orientation = VERTICAL
        addView(InkSegments(activity, listOf(R.string.filter_all, R.string.filter_missed)) { index ->
            missedOnly = index == 1
            show()
        })
        addView(View(activity).apply { setBackgroundColor(InkStyle.of(activity).foreground) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1)))
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(state)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        activity.contentResolver.unregisterContentObserver(observer)
        main.removeCallbacks(reload)
    }

    override fun onResume() = refresh()

    override fun onShown() {
        if (isShown && permission.isGranted) markMissedRead()
    }

    override fun onPermissionsResult() = refresh()

    private fun refresh() {
        if (!permission.isGranted) {
            state.visibility = VISIBLE
            message.setText(R.string.call_log_permission_rationale)
            action.visibility = VISIBLE
            permission.bind(action)
            return
        }
        activity.contentResolver.unregisterContentObserver(observer)
        activity.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        load()
    }

    private fun load() {
        loader.execute {
            val loaded = repository.load()
            main.post {
                if (isLoaded && loaded == entries) return@post
                isLoaded = true
                entries = loaded
                show()
            }
        }
    }

    // TelecomManager lets the default dialer cancel the notification without MODIFY_PHONE_STATE.
    @SuppressLint("MissingPermission")
    private fun markMissedRead() {
        val canWrite = activity.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        loader.execute {
            if (canWrite) runCatching { repository.markMissedRead() }
            if (CallLauncher.isDefaultDialer(activity)) {
                runCatching { activity.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification() }
            }
            main.post(onMissedRead)
        }
    }

    private fun show() {
        val visible = if (missedOnly) entries.filter { it.type == CallType.MISSED } else entries
        val groups = CallLogGrouping.group(visible, ZoneId.systemDefault())
        val anchor = list.firstPosition.takeIf { it < groups.size } ?: 0
        list.setAdapter(adapter(groups), anchor)
        if (groups.isEmpty()) {
            state.visibility = VISIBLE
            message.setText(R.string.call_log_empty)
            action.visibility = GONE
        } else {
            state.visibility = GONE
        }
    }

    private fun adapter(groups: List<CallLogGroup>) = object : InkPagedList.Adapter {
        override val count = groups.size
        override fun itemId(position: Int) = groups[position].latest.id
        override fun createView(position: Int): View = row(groups[position])
    }

    private fun row(group: CallLogGroup): View {
        val entry = group.latest
        val title = labels.title(entry) + if (group.count > 1) " (${group.count})" else ""
        return LinearLayout(activity).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(InkButton(activity).apply {
                text = twoLines(title, labels.summary(entry))
                isBold = entry.type == CallType.MISSED
                isBordered = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(paddingLeft, dp(InkPagedList.ROW_PADDING_DP), paddingRight, dp(InkPagedList.ROW_PADDING_DP))
                if (entry.number.isEmpty()) isClickable = false
                else setOnClickListener { activity.startActivity(ContactCardActivity.intentForNumber(activity, entry.number)) }
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (entry.number.isNotEmpty()) {
                addView(callButton(activity, entry.number), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(GAP_DP) })
            }
        }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        const val REQUEST_CALL_LOG = 2
        private const val PADDING_DP = 24f
        private const val GAP_DP = 8f
        private const val RELOAD_DEBOUNCE_MS = 500L
        private val loader = Executors.newSingleThreadExecutor()
    }
}
