// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.inputmethod.InputContentInfoCompat
import helium314.keyboard.keyboard.clipboard.ClipboardHistoryView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-keyboard GIF surface. It deliberately lives beside the emoji/clipboard surfaces so opening
 * GIFs never changes the text engine or keyboard state machine.
 */
class GifPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val requestGeneration = AtomicInteger(0)

    private val grid = GridView(context)
    private val progress = ProgressBar(context)
    private val status = TextView(context)
    private val recentTab = TextView(context)
    private val popularTab = TextView(context)
    private val adapter = GifAdapter()

    private var previousStripVisibility = View.VISIBLE
    private var mode = Mode.RECENTS

    init {
        orientation = VERTICAL
        visibility = GONE
        buildUi()
    }

    private fun buildUi() {
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val abc = actionText(context.getString(R.string.gif_back_to_keyboard)).apply {
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { closePanel() }
        }
        val search = actionText(context.getString(R.string.gif_search_button)).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener { launchSearch() }
        }
        top.addView(abc, LayoutParams(dp(64), dp(44)))
        top.addView(search, LayoutParams(0, dp(44), 1f))

        val tabs = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        recentTab.apply {
            text = context.getString(R.string.gif_recents)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { showRecents() }
        }
        popularTab.apply {
            text = context.getString(R.string.gif_popular)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { showPopular() }
        }
        tabs.addView(recentTab, LayoutParams(0, dp(36), 1f))
        tabs.addView(popularTab, LayoutParams(0, dp(36), 1f))

        val attribution = TextView(context).apply {
            text = context.getString(R.string.gif_powered_by_giphy)
            textSize = 10f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(8), 0)
        }

        progress.visibility = GONE
        status.apply {
            gravity = Gravity.CENTER
            textSize = 14f
            visibility = GONE
        }

        grid.apply {
            numColumns = 2
            horizontalSpacing = dp(6)
            verticalSpacing = dp(6)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            adapter = this@GifPanelView.adapter
            setPadding(dp(6), dp(3), dp(6), dp(6))
            clipToPadding = false
            setOnItemClickListener { _, _, position, _ -> insertGif(adapter.getItem(position)) }
        }

        addView(top)
        addView(tabs)
        addView(attribution, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)))
        addView(progress, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)))
        addView(status, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        refreshColors()
    }

    private fun actionText(label: String) = TextView(context).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
    }

    fun openPanel() {
        val root = rootView
        val keyboard = root.findViewById<View>(R.id.keyboard_view) ?: return
        val strip = root.findViewById<View>(R.id.strip_container)
        previousStripVisibility = strip?.visibility ?: View.VISIBLE
        keyboard.visibility = GONE
        strip?.visibility = GONE
        visibility = VISIBLE
        refreshColors()

        if (GifRecentsStore.load(context).isNotEmpty()) showRecents() else showPopular()
    }

    fun closePanel() {
        requestGeneration.incrementAndGet()
        visibility = GONE
        val root = rootView
        root.findViewById<View>(R.id.keyboard_view)?.visibility = VISIBLE
        root.findViewById<View>(R.id.strip_container)?.visibility = previousStripVisibility
    }

    private fun showRecents() {
        mode = Mode.RECENTS
        requestGeneration.incrementAndGet()
        progress.visibility = GONE
        val items = GifRecentsStore.load(context).map { GifItem(it, it.mediaUrl) }
        adapter.replace(items)
        status.text = context.getString(R.string.gif_no_recents)
        status.visibility = if (items.isEmpty()) VISIBLE else GONE
        updateTabs()
    }

    private fun showPopular() {
        mode = Mode.POPULAR
        updateTabs()
        loadTrending()
    }

    private fun updateTabs() {
        recentTab.setTypeface(recentTab.typeface, if (mode == Mode.RECENTS) Typeface.BOLD else Typeface.NORMAL)
        popularTab.setTypeface(popularTab.typeface, if (mode == Mode.POPULAR) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun launchSearch() {
        context.startActivity(
            Intent(context, GifSearchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        )
    }

    private fun loadTrending() {
        val generation = requestGeneration.incrementAndGet()
        progress.visibility = VISIBLE
        status.visibility = GONE
        io.execute {
            try {
                val requestUrl = "https://api.giphy.com/v1/gifs/trending?api_key=${encode(apiKey())}&limit=$RESULT_LIMIT&rating=pg-13"
                val items = request(requestUrl)
                mainHandler.post {
                    if (generation != requestGeneration.get() || visibility != VISIBLE) return@post
                    progress.visibility = GONE
                    adapter.replace(items)
                    status.text = context.getString(R.string.gif_no_results)
                    status.visibility = if (items.isEmpty()) VISIBLE else GONE
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (generation != requestGeneration.get() || visibility != VISIBLE) return@post
                    progress.visibility = GONE
                    adapter.replace(emptyList())
                    status.setText(R.string.gif_network_error)
                    status.visibility = VISIBLE
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
                    val previewUrl = images.optJSONObject("fixed_width_small")?.optString("url").orEmpty()
                        .ifBlank { images.optJSONObject("fixed_width")?.optString("url").orEmpty() }
                        .ifBlank { images.optJSONObject("downsized")?.optString("url").orEmpty() }
                    val mediaUrl = images.optJSONObject("downsized")?.optString("url").orEmpty()
                        .ifBlank { images.optJSONObject("original")?.optString("url").orEmpty() }
                    if (previewUrl.isBlank() || mediaUrl.isBlank()) continue
                    val selection = GifSelection(
                        id = gif.optString("id", index.toString()),
                        title = gif.optString("title", "GIF"),
                        mediaUrl = mediaUrl,
                        pageUrl = gif.optString("url").takeIf { it.startsWith("https://") }
                    )
                    add(GifItem(selection, previewUrl))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun insertGif(item: GifItem) {
        val selection = item.selection
        GifRecentsStore.add(context, selection)
        val clipboardView = rootView.findViewById<ClipboardHistoryView>(R.id.clipboard_history_view)
        val listener = clipboardView?.keyboardActionListener ?: return
        val uri = GifContentProvider.uriFor(context.packageName, selection.id, selection.mediaUrl)
        val description = ClipDescription(selection.title.ifBlank { "GIF" }, arrayOf("image/gif"))
        val linkUri = selection.pageUrl?.takeIf { it.startsWith("https://") }?.let(Uri::parse)
        listener.onContent(InputContentInfoCompat(uri, description, linkUri))
    }

    private fun refreshColors() {
        val colors = Settings.getValues().mColors
        val foreground = colors.get(ColorType.EMOJI_KEY_TEXT)
        val background = colors.get(ColorType.MAIN_BACKGROUND)
        val strip = colors.get(ColorType.STRIP_BACKGROUND)
        setBackgroundColor(background)
        recentTab.setTextColor(foreground)
        popularTab.setTextColor(foreground)
        status.setTextColor(foreground)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is LinearLayout) {
                for (j in 0 until child.childCount) {
                    (child.getChildAt(j) as? TextView)?.apply {
                        setTextColor(foreground)
                        setBackgroundColor(strip)
                    }
                }
            } else if (child is TextView) {
                child.setTextColor(foreground)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = ResourceUtils.getSecondaryKeyboardHeight(resources, Settings.getValues())
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
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
        override fun getItemId(position: Int): Long = items[position].selection.id.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val preview = (convertView as? GifPreviewView) ?: GifPreviewView(context).apply {
                layoutParams = GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116))
            }
            preview.contentDescription = items[position].selection.title
            preview.load(items[position].previewUrl)
            return preview
        }
    }

    private data class GifItem(val selection: GifSelection, val previewUrl: String)
    private enum class Mode { RECENTS, POPULAR }

    private fun apiKey(): String = context.prefs()
        .getString(PREF_GIPHY_API_KEY, GIPHY_BETA_API_KEY)
        ?.trim().orEmpty()
        .ifBlank { GIPHY_BETA_API_KEY }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val PREF_GIPHY_API_KEY = "ios_glass_giphy_api_key"
        private const val GIPHY_BETA_API_KEY = "dc6zaTOxFJmzC"
        private const val RESULT_LIMIT = 30

        @JvmStatic
        fun openFromToolbar(source: View) {
            source.rootView.findViewById<GifPanelView>(R.id.gif_panel_view)?.openPanel()
        }
    }
}

object GifRecentsStore {
    private const val PREF_RECENTS = "ios_glass_gif_recents_v1"
    private const val MAX_RECENTS = 30

    fun load(context: Context): List<GifSelection> {
        val raw = context.prefs().getString(PREF_RECENTS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val media = item.optString("mediaUrl")
                    if (id.isBlank() || media.isBlank()) continue
                    add(
                        GifSelection(
                            id = id,
                            title = item.optString("title", "GIF"),
                            mediaUrl = media,
                            pageUrl = item.optString("pageUrl").takeIf { it.startsWith("https://") }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, selection: GifSelection) {
        val items = buildList {
            add(selection)
            addAll(load(context).filterNot { it.id == selection.id })
        }.take(MAX_RECENTS)
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("mediaUrl", item.mediaUrl)
                put("pageUrl", item.pageUrl ?: "")
            })
        }
        context.prefs().edit { putString(PREF_RECENTS, array.toString()) }
    }
}
