// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.gif

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class GifTabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    init {
        text = "GIF"
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 12f
        contentDescription = context.getString(R.string.gif_tab_description)
        isClickable = true
        isFocusable = true
        refreshColors()
        setOnClickListener {
            context.startActivity(
                Intent(context, GifSearchActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshColors()
        if (GifBridge.hasPending()) GifBridge.scheduleDelivery()
    }

    private fun refreshColors() {
        val colors = Settings.getValues().mColors
        setTextColor(colors.get(ColorType.EMOJI_CATEGORY))
        setBackgroundColor(colors.get(ColorType.STRIP_BACKGROUND))
    }
}
