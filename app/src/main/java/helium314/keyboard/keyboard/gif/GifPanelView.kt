// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.content.ClipDescription
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
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
 * In-keyboard GIF surface.
 *
 * GIF discovery intentionally stays inside the IME window. Search uses a tiny inline keyboard so
 * we never launch an Activity or hand focus away from the host editor.
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
    private val searchField = TextView(context)
    private val inlineKeyboard = LinearLayout(context)
    private val adapter = GifAdapter()

    private var previousStripVisibility = View.VISIBLE
    private var mode = Mode.RECENTS
    private var searchQuery = ""

    private val searchRunnable = Runnable {
        val query = searchQuery.trim()
        if (query.isNotEmpty()) loadSearch(query)
    }

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
        searchField.apply {
            text = context.getString(R.string.gif_search_button)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { showInlineSearchKeyboard() }
        }
        top.addView(abc, LayoutParams(dp(64), dp(44)))
        top.addView(searchField, LayoutParams(0, dp(44), 1f))

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
            setOnItemClickListener { _, _, position, _ ->
                insertGif(this@GifPanelView.adapter.getItem(position))
            }
        }

        buildInlineKeyboard()

        addView(top)
        addView(tabs)
        addView(progress, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))
        addView(status, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        addView(grid, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(inlineKeyboard, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(146)))
        refreshColors()
    }

    private fun buildInlineKeyboard() {
        inlineKeyboard.orientation = VERTICAL
        inlineKeyboard.visibility = GONE
        inlineKeyboard.setPadding(dp(4), dp(3), dp(4), dp(3))

        addKeyRow("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        addKeyRow("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ")
        addKeyRow("z", "x", "c", "v", "b", "n", "m")

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(searchKey("⌫", 1.0f) { backspaceSearch() })
        actions.addView(searchKey(context.getString(R.string.gif_space), 2.5f) { appendSearch(" ") })
        actions.addView(searchKey(context.getString(R.string.gif_clear), 1.0f) { clearSearch() })
        actions.addView(searchKey(context.getString(R.string.gif_done), 1.2f) { hideInlineSearchKeyboard() })
        inlineKeyboard.addView(actions, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun addKeyRow(vararg labels: String) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        labels.forEach { label -> row.addView(searchKey(label, 1f) { appendSearch(label) }) }
        inlineKeyboard.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun searchKey(label: String, weight: Float, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
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
        mainHandler.removeCallbacks(searchRunnable)
        requestGeneration.incrementAndGet()
        inlineKeyboard.visibility = GONE
        visibility = GONE
        val root = rootView
        root.findViewById<View>(R.id.keyboard_view)?.visibility = VISIBLE
        root.findViewById<View>(R.id.strip_container)?.visibility = previousStripVisibility
    }

    private fun showRecents() {
        mode = Mode.RECENTS
        hideInlineSearchKeyboard()
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
        hideInlineSearchKeyboard()
        updateTabs()
        loadTrending()
    }

    private fun updateTabs() {
        recentTab.setTypeface(recentTab.typeface, if (mode == Mode.RECENTS) Typeface.BOLD else Typeface.NORMAL)
        popularTab.setTypeface(popularTab.typeface, if (mode == Mode.POPULAR) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun showInlineSearchKeyboard() {
        inlineKeyboard.visibility = VISIBLE
        mode = Mode.SEARCH
        updateTabs()
        updateSearchField()
        if (searchQuery.isNotBlank()) scheduleSearch()
    }

    private fun hideInlineSearchKeyboard() {
        inlineKeyboard.visibility = GONE
    }

    private fun appendSearch(value: String) {
        if (searchQuery.length >= MAX_QUERY_LENGTH) return
        searchQuery += value
        updateSearchField()
        scheduleSearch()
    }

    private fun backspaceSearch() {
        if (searchQuery.isEmpty()) return
        searchQuery = searchQuery.dropLast(1)
        updateSearchField()
        if (searchQuery.isBlank()) {
            mainHandler.removeCallbacks(searchRunnable)
            showPopularKeepingKeyboard()
        } else {
            scheduleSearch()
        }
    }

    private fun clearSearch() {
        searchQuery = ""
        updateSearchField()
        mainHandler.removeCallbacks(searchRunnable)
        showPopularKeepingKeyboard()
    }

    private fun updateSearchField() {
        searchField.text = if (searchQuery.isBlank()) {
            context.getString(R.string.gif_search_button)
        } else {
            "🔍  $searchQuery"
        }
    }

    private fun scheduleSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        mode = Mode.SEARCH
        updateTabs()
        mainHandler.removeCallbacks(searchRunnable)
        mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
    }

    private fun showPopularKeepingKeyboard() {
        mode = Mode.SEARCH
        updateTabs()
        loadTrending()
    }

    private fun loadTrending() {
        loadFeed("$API_BASE/gifs/trending?page=1&limit=$RESULT_LIMIT")
    }

    private fun loadSearch(query: String) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        loadFeed("$API_BASE/gifs/search?q=$encoded&page=1&limit=$RESULT_LIMIT")
    }

    private fun loadFeed(requestUrl: String) {
        val generation = requestGeneration.incrementAndGet()
        progress.visibility = VISIBLE
        status.visibility = GONE
        io.execute {
            try {
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
            setRequestProperty("User-Agent", "HeliKeyboard-GIF/1.0")
        }
        try {
            if (connection.responseCode !in 200..299) error("GIF API HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).getJSONArray("data")
            return buildList {
                for (index in 0 until data.length()) {
                    val gif = data.optJSONObject(index) ?: continue
                    val mediaUrl = gif.optString("url")
                    val previewUrl = gif.optString("preview_url").ifBlank { mediaUrl }
                    if (mediaUrl.isBlank() || previewUrl.isBlank()) continue
                    val selection = GifSelection(
                        id = gif.optString("id", index.toString()),
                        title = gif.optString("title", "GIF"),
                        mediaUrl = mediaUrl,
                        pageUrl = null
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
        val background = colors.get(ColorType.MAIN_BACKGROUND)
        val strip = colors.get(ColorType.STRIP_BACKGROUND)
        val foreground = if (ColorUtils.calculateLuminance(background) < 0.48) 0xFFF5F5F7.toInt() else 0xFF1C1C1E.toInt()
        val keyBackground = if (ColorUtils.calculateLuminance(strip) < 0.48) 0xFF3A3A3C.toInt() else 0xFFF2F2F7.toInt()

        setBackgroundColor(background)
        searchField.setTextColor(foreground)
        searchField.setBackgroundColor(strip)
        recentTab.setTextColor(foreground)
        popularTab.setTextColor(foreground)
        status.setTextColor(foreground)

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is LinearLayout && child !== inlineKeyboard) {
                for (j in 0 until child.childCount) {
                    (child.getChildAt(j) as? TextView)?.apply {
                        setTextColor(foreground)
                        setBackgroundColor(strip)
                    }
                }
            }
        }
        colorSearchKeyboard(inlineKeyboard, foreground, keyBackground)
    }

    private fun colorSearchKeyboard(group: LinearLayout, foreground: Int, keyBackground: Int) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            when (child) {
                is TextView -> {
                    child.setTextColor(foreground)
                    child.setBackgroundColor(keyBackground)
                }
                is LinearLayout -> colorSearchKeyboard(child, foreground, keyBackground)
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
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(116))
            }
            preview.contentDescription = items[position].selection.title
            preview.load(items[position].previewUrl)
            return preview
        }
    }

    private data class GifItem(val selection: GifSelection, val previewUrl: String)
    private enum class Mode { RECENTS, POPULAR, SEARCH }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val API_BASE = "https://gifsnap.com/api/v1"
        private const val RESULT_LIMIT = 30
        private const val SEARCH_DEBOUNCE_MS = 260L
        private const val MAX_QUERY_LENGTH = 50

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
