// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GifSearchActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(3)
    private val requestGeneration = AtomicInteger(0)

    private lateinit var searchField: EditText
    private lateinit var grid: GridView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: GifAdapter

    private val searchRunnable = Runnable { loadGifs(searchField.text?.toString().orEmpty()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                mainHandler.removeCallbacks(searchRunnable)
                mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }
        })

        searchField.requestFocus()
        searchField.postDelayed({
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        }, 150L)

        loadGifs("")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(searchRunnable)
        requestGeneration.incrementAndGet()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing && GifBridge.hasPending()) {
            // onStop happens after focus has returned to the host app; a short delay lets the
            // IME receive the host EditorInfo before committing rich content.
            GifBridge.scheduleDelivery(120L)
        }
    }

    private fun buildUi() {
        val colors = Settings.getValues().mColors
        val foreground = colors.get(ColorType.EMOJI_KEY_TEXT)
        val background = colors.get(ColorType.MAIN_BACKGROUND)
        val strip = colors.get(ColorType.STRIP_BACKGROUND)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 24f
            setTextColor(foreground)
            setBackgroundColor(strip)
            setOnClickListener { finish() }
            contentDescription = getString(R.string.gif_back)
        }
        val title = TextView(this).apply {
            text = getString(R.string.gif_search_title)
            textSize = 18f
            setTextColor(foreground)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        val apiButton = Button(this).apply {
            text = getString(R.string.gif_api_button)
            setTextColor(foreground)
            setBackgroundColor(strip)
            setOnClickListener { showApiKeyDialog(firstRun = false) }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(48), dp(42)))
        toolbar.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        toolbar.addView(apiButton, LinearLayout.LayoutParams(dp(64), dp(42)))

        searchField = EditText(this).apply {
            hint = getString(R.string.gif_search_hint)
            isSingleLine = true
            setTextColor(foreground)
            setHintTextColor((foreground and 0x00ffffff) or 0x88000000.toInt())
            setBackgroundColor(strip)
            setPadding(dp(12), 0, dp(12), 0)
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val attribution = TextView(this).apply {
            text = getString(R.string.gif_powered_by_giphy)
            setTextColor(foreground)
            textSize = 11f
            gravity = Gravity.END
            setPadding(0, dp(3), dp(2), dp(3))
        }

        progress = ProgressBar(this).apply {
            visibility = View.GONE
        }
        status = TextView(this).apply {
            setTextColor(foreground)
            gravity = Gravity.CENTER
            textSize = 14f
            visibility = View.GONE
        }

        adapter = GifAdapter()
        grid = GridView(this).apply {
            numColumns = 2
            horizontalSpacing = dp(6)
            verticalSpacing = dp(6)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = this@GifSearchActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                val item = this@GifSearchActivity.adapter.getItem(position)
                val selection = GifSelection(
                    id = item.id,
                    title = item.title,
                    mediaUrl = item.mediaUrl,
                    pageUrl = item.pageUrl
                )
                GifRecentsStore.add(this@GifSearchActivity, selection)
                GifBridge.select(selection)
                finish()
            }
        }

        root.addView(toolbar)
        root.addView(searchField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { topMargin = dp(4) })
        root.addView(attribution)
        root.addView(progress, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(28)
        ))
        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ))
        root.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun showApiKeyDialog(firstRun: Boolean) {
        val input = EditText(this).apply {
            hint = getString(R.string.gif_api_key_hint)
            isSingleLine = true
            setText(apiKey())
            selectAll()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.gif_api_key_title)
            .setMessage(R.string.gif_api_key_explanation)
            .setView(input)
            .setPositiveButton(R.string.gif_api_key_save, null)
            .setNegativeButton(if (firstRun) R.string.gif_close else android.R.string.cancel) { _, _ ->
                if (firstRun && apiKey().isBlank()) finish()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val key = input.text?.toString()?.trim().orEmpty()
                if (key.isEmpty()) {
                    input.error = getString(R.string.gif_api_key_required)
                    return@setOnClickListener
                }
                prefs().edit().putString(PREF_GIPHY_API_KEY, key).apply()
                dialog.dismiss()
                loadGifs(searchField.text?.toString().orEmpty())
            }
        }
        dialog.show()
    }

    private fun apiKey(): String = prefs()
        .getString(PREF_GIPHY_API_KEY, GIPHY_BETA_API_KEY)
        ?.trim().orEmpty()
        .ifBlank { GIPHY_BETA_API_KEY }

    private fun loadGifs(query: String) {
        val key = apiKey()
        if (key.isBlank()) return

        val generation = requestGeneration.incrementAndGet()
        progress.visibility = View.VISIBLE
        status.visibility = View.GONE

        io.execute {
            try {
                val encodedKey = encode(key)
                val requestUrl = if (query.isBlank()) {
                    "https://api.giphy.com/v1/gifs/trending?api_key=$encodedKey&limit=$RESULT_LIMIT&rating=pg-13"
                } else {
                    "https://api.giphy.com/v1/gifs/search?api_key=$encodedKey&q=${encode(query)}&limit=$RESULT_LIMIT&rating=pg-13&lang=es"
                }
                val items = request(requestUrl)
                mainHandler.post {
                    if (generation != requestGeneration.get() || isFinishing) return@post
                    progress.visibility = View.GONE
                    adapter.replace(items)
                    status.text = if (items.isEmpty()) getString(R.string.gif_no_results) else ""
                    status.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (generation != requestGeneration.get() || isFinishing) return@post
                    progress.visibility = View.GONE
                    adapter.replace(emptyList())
                    status.setText(R.string.gif_network_error)
                    status.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun request(requestUrl: String): List<GifItem> {
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("GIPHY HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONArray("data")
            return buildList {
                for (index in 0 until data.length()) {
                    val gif = data.optJSONObject(index) ?: continue
                    val images = gif.optJSONObject("images") ?: continue
                    val previewUrl =
                        images.optJSONObject("fixed_width_small")?.optString("url").orEmpty()
                            .ifBlank { images.optJSONObject("fixed_width")?.optString("url").orEmpty() }
                            .ifBlank { images.optJSONObject("downsized")?.optString("url").orEmpty() }
                    val mediaUrl =
                        images.optJSONObject("downsized")?.optString("url").orEmpty()
                            .ifBlank { images.optJSONObject("original")?.optString("url").orEmpty() }
                    if (previewUrl.isBlank() || mediaUrl.isBlank()) continue
                    add(
                        GifItem(
                            id = gif.optString("id", index.toString()),
                            title = gif.optString("title", "GIF"),
                            previewUrl = previewUrl,
                            mediaUrl = mediaUrl,
                            pageUrl = gif.optString("url").takeIf { it.startsWith("https://") }
                        )
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private inner class GifAdapter : BaseAdapter() {
        private val items = mutableListOf<GifItem>()

        fun replace(newItems: List<GifItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): GifItem = items[position]
        override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val preview = (convertView as? GifPreviewView) ?: GifPreviewView(this@GifSearchActivity).apply {
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(132)
                )
            }
            preview.contentDescription = items[position].title
            preview.load(items[position].previewUrl)
            return preview
        }
    }

    private data class GifItem(
        val id: String,
        val title: String,
        val previewUrl: String,
        val mediaUrl: String,
        val pageUrl: String?
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    companion object {
        private const val PREF_GIPHY_API_KEY = "ios_glass_giphy_api_key"
        private const val GIPHY_BETA_API_KEY = "dc6zaTOxFJmzC"
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val RESULT_LIMIT = 24
    }
}
