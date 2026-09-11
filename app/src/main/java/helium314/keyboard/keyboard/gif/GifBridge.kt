// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.content.ClipDescription
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings

data class GifSelection(
    val id: String,
    val title: String,
    val mediaUrl: String,
    val pageUrl: String?
)

object GifBridge {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var pending: GifSelection? = null
    private var attempts = 0

    fun select(selection: GifSelection) {
        pending = selection
        attempts = 0
    }

    fun hasPending(): Boolean = pending != null

    fun scheduleDelivery(delayMillis: Long = 120L) {
        handler.removeCallbacks(deliverRunnable)
        handler.postDelayed(deliverRunnable, delayMillis)
    }

    private val deliverRunnable = object : Runnable {
        override fun run() {
            val selection = pending ?: return
            val switcher = KeyboardSwitcher.getInstance()
            val wrapper = switcher.wrapperView
            if (wrapper == null) {
                retry(this)
                return
            }

            // While GifSearchActivity owns the editor, do not send content to our own search field.
            val targetPackage = Settings.getValues().mInputAttributes?.mTargetApplicationPackageName
            if (targetPackage == null || targetPackage == wrapper.context.packageName) {
                retry(this)
                return
            }

            val clipboardView = wrapper.findViewById<ClipboardHistoryView>(R.id.clipboard_history_view)
            val listener = clipboardView?.keyboardActionListener
            if (listener == null) {
                retry(this)
                return
            }

            val uri = GifContentProvider.uriFor(
                wrapper.context.packageName,
                selection.id,
                selection.mediaUrl
            )
            val description = ClipDescription(
                selection.title.ifBlank { "GIF" },
                arrayOf("image/gif")
            )
            val linkUri = selection.pageUrl?.takeIf { it.startsWith("https://") }?.let(Uri::parse)
            listener.onContent(InputContentInfoCompat(uri, description, linkUri))
            pending = null
            attempts = 0
        }
    }

    private fun retry(runnable: Runnable) {
        attempts += 1
        if (attempts <= 12) {
            handler.postDelayed(runnable, 120L)
        } else {
            pending = null
            attempts = 0
        }
    }
}
