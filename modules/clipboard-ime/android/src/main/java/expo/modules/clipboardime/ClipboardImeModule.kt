package expo.modules.clipboardime

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ClipboardImeModule : Module() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var broadcastReceiver: BroadcastReceiver? = null
    private var isReceiverRegistered = false

    override fun definition() = ModuleDefinition {
        Name("ClipboardImeModule")

        Events("onClipboardChanged")

        Function("isImeEnabled") {
            val context = appContext.reactContext ?: return@Function false
            SyncClipboardImeService().isImeEnabled(context)
        }

        Function("openImeSettings") {
            val context = appContext.reactContext ?: return@Function
            SyncClipboardImeService().openImeSettings(context)
        }

        Function("openImePicker") {
            val context = appContext.reactContext ?: return@Function
            SyncClipboardImeService().openImePicker(context)
        }

        AsyncFunction("hasImeClipboardText") { promise: Promise ->
            val context = appContext.reactContext
            if (context == null) {
                promise.resolve(false)
                return@AsyncFunction
            }
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val desc = clip.description
                    val hasText = desc.hasMimeType("text/*") || clip.getItemAt(0).text != null
                    promise.resolve(hasText)
                } else {
                    promise.resolve(false)
                }
            } catch (e: Exception) {
                promise.resolve(false)
            }
        }

        AsyncFunction("hasImeClipboardImage") { promise: Promise ->
            val context = appContext.reactContext
            if (context == null) {
                promise.resolve(false)
                return@AsyncFunction
            }
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val desc = clip.description
                    val hasImage = desc.hasMimeType("image/*") || clip.getItemAt(0).uri != null
                    promise.resolve(hasImage)
                } else {
                    promise.resolve(false)
                }
            } catch (e: Exception) {
                promise.resolve(false)
            }
        }

        AsyncFunction("getImeClipboardText") { promise: Promise ->
            val context = appContext.reactContext
            if (context == null) {
                promise.resolve("")
                return@AsyncFunction
            }
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                    promise.resolve(text)
                } else {
                    promise.resolve("")
                }
            } catch (e: Exception) {
                promise.resolve("")
            }
        }

        AsyncFunction("getImeClipboardImageUri") { promise: Promise ->
            val context = appContext.reactContext
            if (context == null) {
                promise.resolve(null)
                return@AsyncFunction
            }
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val uri = clip.getItemAt(0).uri
                    promise.resolve(uri?.toString())
                } else {
                    promise.resolve(null)
                }
            } catch (e: Exception) {
                promise.resolve(null)
            }
        }

        AsyncFunction("saveImeClipboardImageToFile") { destDirPath: String, promise: Promise ->
            val context = appContext.reactContext
            if (context == null) {
                promise.resolve(null)
                return@AsyncFunction
            }
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    promise.resolve(null)
                    return@AsyncFunction
                }

                val item = clip.getItemAt(0)
                val uri = item.uri
                if (uri == null) {
                    promise.resolve(null)
                    return@AsyncFunction
                }

                val mimeType = context.contentResolver.getType(uri)
                if (mimeType == null || !mimeType.startsWith("image/")) {
                    promise.resolve(null)
                    return@AsyncFunction
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    promise.resolve(null)
                    return@AsyncFunction
                }

                val ext = when {
                    mimeType.contains("png") -> "png"
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                    mimeType.contains("gif") -> "gif"
                    mimeType.contains("webp") -> "webp"
                    mimeType.contains("bmp") -> "bmp"
                    else -> "png"
                }

                val path = if (destDirPath.startsWith("file://", ignoreCase = true)) {
                    Uri.parse(destDirPath).path ?: destDirPath.removePrefix("file://")
                } else {
                    destDirPath
                }
                val dir = File(path)
                dir.mkdirs()
                val fileName = "ime_${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}.$ext"
                val file = File(dir, fileName)
                FileOutputStream(file).use { fos ->
                    inputStream.copyTo(fos, bufferSize = 8192)
                }
                inputStream.close()

                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)

                val result = mapOf(
                    "width" to (opts.outWidth),
                    "height" to (opts.outHeight),
                    "filePath" to "file://" + file.absolutePath,
                    "mimeType" to mimeType
                )
                promise.resolve(result)
            } catch (e: Exception) {
                promise.resolve(null)
            }
        }

        OnCreate {
            registerClipboardReceiver()
        }

        OnDestroy {
            unregisterClipboardReceiver()
        }
    }

    private fun registerClipboardReceiver() {
        if (isReceiverRegistered) return
        val context = appContext.reactContext ?: return

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SyncClipboardImeService.ACTION_CLIPBOARD_CHANGED) {
                    val hasText = intent.getBooleanExtra(SyncClipboardImeService.EXTRA_HAS_TEXT, false)
                    val hasImage = intent.getBooleanExtra(SyncClipboardImeService.EXTRA_HAS_IMAGE, false)
                    val timestamp = intent.getLongExtra(SyncClipboardImeService.EXTRA_TIMESTAMP, 0)

                    sendEvent("onClipboardChanged", mapOf(
                        "hasText" to hasText,
                        "hasImage" to hasImage,
                        "timestamp" to timestamp
                    ))
                }
            }
        }

        val filter = IntentFilter(SyncClipboardImeService.ACTION_CLIPBOARD_CHANGED)
        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver!!, filter)
        isReceiverRegistered = true
    }

    private fun unregisterClipboardReceiver() {
        if (!isReceiverRegistered) return
        val context = appContext.reactContext ?: return
        try {
            broadcastReceiver?.let {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(it)
            }
        } catch (_: Exception) {}
        broadcastReceiver = null
        isReceiverRegistered = false
    }
}
