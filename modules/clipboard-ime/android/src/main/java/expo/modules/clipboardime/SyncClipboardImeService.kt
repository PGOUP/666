package expo.modules.clipboardime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputMethodSubtype
import android.widget.LinearLayout
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.File

class SyncClipboardImeService : InputMethodService() {

    companion object {
        const val ACTION_CLIPBOARD_CHANGED = "expo.modules.clipboardime.CLIPBOARD_CHANGED"
        const val EXTRA_HAS_TEXT = "hasText"
        const val EXTRA_HAS_IMAGE = "hasImage"
        const val EXTRA_TIMESTAMP = "timestamp"

        private const val POLL_INTERVAL_MS = 1500L
        private const val CLIP_FILE_NAME = "ime_clipboard_cache.txt"
        private const val MAX_TEXT_CACHE = 50000
    }

    private var clipboardManager: ClipboardManager? = null
    private var lastClipText: String? = null
    private var lastTimestamp: Long = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPolling = false

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        onClipboardDataChanged()
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPolling) return
            checkClipboardForChanges()
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                200
            )
        }

        val titleText = TextView(this).apply {
            text = "SyncClipboard \u526a\u8d34\u677f\u540c\u6b65 IME"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 8)
        }
        layout.addView(titleText)

        val statusText = TextView(this).apply {
            text = "\u2714 \u526a\u8d34\u677f\u76d1\u542c\u5df2\u542f\u7528\uff0c\u8bf7\u5207\u56de\u60a8\u7684\u5e38\u7528\u8f93\u5165\u6cd5"
            textSize = 12f
            setTextColor(Color.parseColor("#4CAF50"))
            gravity = Gravity.CENTER
            setPadding(16, 4, 16, 16)
        }
        layout.addView(statusText)

        val helpText = TextView(this).apply {
            text = "\u5f00\u542f\u540e\u5373\u53ef\u5207\u6362\u56de\u539f\u8f93\u5165\u6cd5\uff0cSyncClipboard IME \u4f1a\u5728\u540e\u53f0\u6301\u7eed\u76d1\u542c\u526a\u8d34\u677f"
            textSize = 11f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 12)
        }
        layout.addView(helpText)

        return layout
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        startClipboardMonitoring()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopClipboardMonitoring()
        super.onDestroy()
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        startClipboardMonitoring()
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    override fun onBindInput() {
        super.onBindInput()
    }

    override fun onUnbindInput() {
        super.onUnbindInput()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        startClipboardMonitoring()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            requestHideSelf(0)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startClipboardMonitoring() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
            clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}

        if (!isPolling) {
            isPolling = true
            mainHandler.post(pollRunnable)
        }
    }

    private fun stopClipboardMonitoring() {
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
        isPolling = false
    }

    private fun onClipboardDataChanged() {
        val cm = clipboardManager ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return

        val now = System.currentTimeMillis()
        val item = clip.getItemAt(0)
        val hasText = item.text != null || clip.description.hasMimeType("text/*")
        val hasImage = clip.description.hasMimeType("image/*") || item.uri != null

        var cachedText: String? = null
        if (hasText) {
            cachedText = item.coerceToText(this)?.toString()
            if (cachedText != null) {
                val dedupKey = cachedText + hasImage.toString()
                if (dedupKey == lastClipText && (now - lastTimestamp) < 3000) {
                    return
                }
                lastClipText = dedupKey
                lastTimestamp = now
            }
        } else if (hasImage) {
            val dedupKey = "image:" + item.uri.toString()
            if (dedupKey == lastClipText && (now - lastTimestamp) < 3000) {
                return
            }
            lastClipText = dedupKey
            lastTimestamp = now
        } else {
            return
        }

        saveClipCache(cachedText)

        val intent = Intent(ACTION_CLIPBOARD_CHANGED).apply {
            putExtra(EXTRA_HAS_TEXT, hasText)
            putExtra(EXTRA_HAS_IMAGE, hasImage)
            putExtra(EXTRA_TIMESTAMP, now)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun checkClipboardForChanges() {
        val cm = clipboardManager ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return

        val now = System.currentTimeMillis()
        val item = clip.getItemAt(0)
        val hasText = item.text != null || clip.description.hasMimeType("text/*")
        val hasImage = clip.description.hasMimeType("image/*") || item.uri != null

        var cachedText: String? = null
        if (hasText) {
            cachedText = item.coerceToText(this)?.toString() ?: ""
        }

        val dedupKey = if (hasText) cachedText + hasImage.toString() else "image:" + item.uri.toString()
        if (dedupKey == lastClipText) return
        lastClipText = dedupKey
        lastTimestamp = now

        saveClipCache(cachedText)

        val intent = Intent(ACTION_CLIPBOARD_CHANGED).apply {
            putExtra(EXTRA_HAS_TEXT, hasText)
            putExtra(EXTRA_HAS_IMAGE, hasImage)
            putExtra(EXTRA_TIMESTAMP, now)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun saveClipCache(text: String?) {
        try {
            val file = File(filesDir, CLIP_FILE_NAME)
            file.writeText(text?.take(MAX_TEXT_CACHE) ?: "")
        } catch (_: Exception) {}
    }

    fun readCachedClipText(): String? {
        return try {
            val file = File(filesDir, CLIP_FILE_NAME)
            if (file.exists()) {
                val text = file.readText()
                if (text.isEmpty()) null else text
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun getLatestClipboardContent(): ClipData? {
        return clipboardManager?.primaryClip
    }

    fun isImeEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledImes = imm.enabledInputMethodList
        val packageName = context.packageName
        return enabledImes.any { ime -> ime.packageName == packageName }
    }

    fun openImeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openImePicker(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}
