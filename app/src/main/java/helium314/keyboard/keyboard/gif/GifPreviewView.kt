// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.AttributeSet
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GifPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {
    private val requestGeneration = AtomicInteger(0)

    init {
        scaleType = ScaleType.CENTER_CROP
        adjustViewBounds = false
    }

    fun load(url: String?) {
        val generation = requestGeneration.incrementAndGet()
        setImageDrawable(null)
        if (url.isNullOrBlank()) return

        IO.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7_500
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                }
                if (connection.responseCode !in 200..299) return@execute
                val bytes = connection.inputStream.use { it.readBytes() }
                if (generation != requestGeneration.get()) return@execute

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val drawable = ImageDecoder.decodeDrawable(
                        ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                    )
                    post {
                        if (generation != requestGeneration.get()) return@post
                        setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) drawable.start()
                    }
                } else {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    post {
                        if (generation == requestGeneration.get()) setImageBitmap(bitmap)
                    }
                }
            } catch (_: Exception) {
                // Leave the cell empty on preview failures; selection still uses the full media URL.
            } finally {
                connection?.disconnect()
            }
        }
    }

    override fun onDetachedFromWindow() {
        requestGeneration.incrementAndGet()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (drawable as? AnimatedImageDrawable)?.stop()
        }
        super.onDetachedFromWindow()
    }

    companion object {
        private val IO = Executors.newFixedThreadPool(4)
    }
}
