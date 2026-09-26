// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.utils.isBrightColor

class MonetKeyboardColors(
    context: Context,
    private val schemeType: KeyboardDynamicSchemeType,
    override val themeStyle: String,
    override val hasKeyBorders: Boolean,
    private val isDark: Boolean,
    keyboardBackground: Drawable? = null,
) : Colors {
    private val seed = DynamicColorSeedProvider.getSeed(context, isDark)
    private val scheme = DynamicSchemeProvider.create(seed, schemeType, isDark)

    private val background = scheme.background
    private val keyBackground = if (hasKeyBorders) scheme.surfaceContainerHigh else scheme.surface
    private val functionalKey = scheme.surfaceVariant
    private val spaceBar = if (hasKeyBorders) scheme.surfaceContainer else keyBackground
    private val keyText = readable(scheme.onSurface, keyBackground, scheme.onBackground)
    private val keyHintText = readable(scheme.onSurfaceVariant, keyBackground, keyText)
    private val suggestionText = readable(scheme.primary, background, scheme.onSurface)
    private val spaceBarText = ColorUtils.setAlphaComponent(readable(scheme.onSurfaceVariant, spaceBar, keyText), 160)
    private val gesture = scheme.primary

    private val delegate = DefaultColors(
        themeStyle = themeStyle,
        hasKeyBorders = hasKeyBorders,
        accent = scheme.primary,
        background = background,
        keyBackground = keyBackground,
        functionalKey = functionalKey,
        spaceBar = spaceBar,
        keyText = keyText,
        keyHintText = keyHintText,
        suggestionText = suggestionText,
        spaceBarText = spaceBarText,
        gesture = gesture,
        keyboardBackground = keyboardBackground,
    )

    override fun haveColorsChanged(context: Context): Boolean =
        DynamicColorSeedProvider.hasSeedChanged(context, isDark, seed)

    override fun get(color: ColorType): Int = delegate.get(color)

    override fun setColor(drawable: Drawable, color: ColorType) = delegate.setColor(drawable, color)

    override fun setColor(view: ImageView, color: ColorType) = delegate.setColor(view, color)

    override fun setBackground(view: View, color: ColorType) = delegate.setBackground(view, color)

    private fun readable(foreground: Int, background: Int, fallback: Int): Int {
        if (hasContrast(foreground, background)) return foreground
        if (hasContrast(fallback, background)) return fallback
        val black = Color.BLACK
        val white = Color.WHITE
        return when {
            hasContrast(black, background) && hasContrast(white, background) ->
                if (isBrightColor(background)) black else white
            hasContrast(black, background) -> black
            hasContrast(white, background) -> white
            isBrightColor(background) -> black
            else -> white
        }
    }

    private fun hasContrast(foreground: Int, background: Int): Boolean =
        runCatching { ColorUtils.calculateContrast(foreground, background) >= MIN_TEXT_CONTRAST }
            .getOrDefault(false)

    companion object {
        private const val MIN_TEXT_CONTRAST = 4.5
    }
}
