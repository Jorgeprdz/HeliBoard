// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common.dynamic

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import helium314.keyboard.keyboard.KeyboardTheme.Companion.STYLE_MATERIAL
import helium314.keyboard.latin.common.AllColors
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import helium314.keyboard.latin.utils.isGoodContrast
import java.util.EnumMap

class MonetKeyboardColors(
    context: Context,
    override val themeStyle: String,
    override val hasKeyBorders: Boolean,
    private val schemeType: DynamicSchemeType,
    private val isDark: Boolean,
    backgroundImage: Drawable? = null,
    seedOverride: Int? = null,
) : Colors {
    private val tracksSeedProvider = seedOverride == null
    private val seedSnapshot = if (seedOverride == null) {
        DynamicColorSeedProvider.snapshot(context, isDark)
    } else {
        DynamicColorSeedProvider.Snapshot(seedOverride, seedOverride.toLong(), false)
    }
    private val scheme = DynamicSchemeProvider.get(seedSnapshot.seed, schemeType, isDark)
    internal val mappedColors = MonetColorMapper.map(scheme, themeStyle, hasKeyBorders)
    private val delegate = AllColors(mappedColors, themeStyle, hasKeyBorders, backgroundImage)

    override fun haveColorsChanged(context: Context): Boolean =
        tracksSeedProvider && DynamicColorSeedProvider.snapshot(context, isDark).fingerprint != seedSnapshot.fingerprint

    override fun get(color: ColorType): Int = delegate.get(color)
    override fun setColor(drawable: Drawable, color: ColorType) = delegate.setColor(drawable, color)
    override fun setColor(view: android.widget.ImageView, color: ColorType) = delegate.setColor(view, color)
    override fun setBackground(view: android.view.View, color: ColorType) = delegate.setBackground(view, color)
}

internal object MonetColorMapper {
    fun map(
        scheme: KeyboardDynamicScheme,
        themeStyle: String,
        hasKeyBorders: Boolean,
    ): EnumMap<ColorType, Int> {
        val colors = EnumMap<ColorType, Int>(ColorType::class.java)

        val mainBackground = scheme.background
        val keyBackground = if (hasKeyBorders) scheme.surfaceContainerLow else mainBackground
        val functionalBackground = if (hasKeyBorders) scheme.secondaryContainer else mainBackground
        val spaceBackground = if (hasKeyBorders) scheme.surfaceContainer else mainBackground
        val stripBackground =
            if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) scheme.surfaceContainer else mainBackground

        val keyText = readable(scheme.onSurface, keyBackground, scheme)
        val functionalText = readable(scheme.onSecondaryContainer, functionalBackground, scheme)
        val spaceText = readable(scheme.onSurfaceVariant, spaceBackground, scheme)
        val actionText = readable(scheme.onPrimary, scheme.primary, scheme)
        val popupText = readable(scheme.onPrimaryContainer, scheme.primaryContainer, scheme)
        val previewText = readable(scheme.onSecondaryContainer, scheme.secondaryContainer, scheme)
        val stripText = readable(scheme.onSurface, stripBackground, scheme)
        val hintText = readable(scheme.onSurfaceVariant, keyBackground, scheme)

        colors[ColorType.MAIN_BACKGROUND] = mainBackground
        colors[ColorType.KEY_BACKGROUND] = keyBackground
        colors[ColorType.FUNCTIONAL_KEY_BACKGROUND] = functionalBackground
        colors[ColorType.SPACE_BAR_BACKGROUND] = spaceBackground
        colors[ColorType.ACTION_KEY_BACKGROUND] = scheme.primary

        colors[ColorType.KEY_TEXT] = keyText
        colors[ColorType.FUNCTIONAL_KEY_TEXT] = functionalText
        colors[ColorType.SPACE_BAR_TEXT] = spaceText
        colors[ColorType.KEY_HINT_TEXT] = hintText

        colors[ColorType.SUGGESTION_AUTO_CORRECT] = readable(scheme.primary, stripBackground, scheme)
        colors[ColorType.SUGGESTION_TYPED_WORD] = stripText
        colors[ColorType.SUGGESTION_VALID_WORD] = readable(scheme.onSurfaceVariant, stripBackground, scheme)
        colors[ColorType.SUGGESTED_WORD] = stripText
        colors[ColorType.MORE_SUGGESTIONS_HINT] = readable(scheme.onSurfaceVariant, scheme.surfaceContainerHigh, scheme)
        colors[ColorType.MORE_SUGGESTIONS_BACKGROUND] = scheme.surfaceContainerHigh
        colors[ColorType.MORE_SUGGESTIONS_WORD_BACKGROUND] = scheme.surfaceContainerLow

        colors[ColorType.STRIP_BACKGROUND] = stripBackground

        colors[ColorType.TOOL_BAR_KEY] = stripText
        colors[ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND] = scheme.primaryContainer
        colors[ColorType.TOOL_BAR_EXPAND_KEY] = readable(scheme.onPrimaryContainer, scheme.primaryContainer, scheme)
        colors[ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND] = scheme.primaryContainer

        colors[ColorType.POPUP_KEYS_BACKGROUND] = scheme.primaryContainer
        colors[ColorType.ACTION_KEY_POPUP_KEYS_BACKGROUND] = scheme.primaryContainer
        colors[ColorType.POPUP_KEY_TEXT] = popupText
        colors[ColorType.POPUP_KEY_ICON] = popupText

        colors[ColorType.KEY_PREVIEW_BACKGROUND] = scheme.secondaryContainer
        colors[ColorType.KEY_PREVIEW_TEXT] = previewText

        colors[ColorType.EMOJI_CATEGORY] = readable(scheme.onSurfaceVariant, stripBackground, scheme)
        colors[ColorType.EMOJI_CATEGORY_SELECTED] = readable(scheme.primary, stripBackground, scheme)
        colors[ColorType.EMOJI_KEY_TEXT] = keyText
        colors[ColorType.EMOJI_SEARCH_BACKGROUND] = scheme.surfaceContainerHigh
        colors[ColorType.EMOJI_SEARCH_TEXT] = readable(scheme.onSurface, scheme.surfaceContainerHigh, scheme)

        colors[ColorType.CLIPBOARD_PIN] = readable(scheme.primary, scheme.surfaceContainerLow, scheme)
        colors[ColorType.CLIPBOARD_SUGGESTION_BACKGROUND] = scheme.surfaceContainerLow
        colors[ColorType.CLIPBOARD_SUGGESTION_ICON] = readable(scheme.onSurface, scheme.surfaceContainerLow, scheme)

        colors[ColorType.GESTURE_TRAIL] = readable(scheme.tertiary, mainBackground, scheme, 3.0)
        colors[ColorType.GESTURE_PREVIEW] = scheme.tertiaryContainer

        colors[ColorType.SHIFT_KEY_ICON] = readable(scheme.primary, keyBackground, scheme)
        colors[ColorType.KEY_ICON] = keyText
        colors[ColorType.ACTION_KEY_ICON] = actionText
        colors[ColorType.REMOVE_SUGGESTION_ICON] = readable(scheme.onSurfaceVariant, stripBackground, scheme)
        colors[ColorType.ONE_HANDED_MODE_BUTTON] = stripText

        colors[ColorType.AUTOFILL_BACKGROUND_CHIP] = scheme.secondaryContainer
        colors[ColorType.NAVIGATION_BAR] = mainBackground

        check(colors.size == ColorType.entries.size) {
            "Dynamic color map is incomplete: " +
                ColorType.entries.filterNot(colors::containsKey).joinToString()
        }
        return colors
    }

    private fun readable(
        preferred: Int,
        background: Int,
        scheme: KeyboardDynamicScheme,
        minimumContrast: Double = 4.5,
    ): Int {
        if (ColorUtils.calculateContrast(preferred, background) >= minimumContrast &&
            isGoodContrast(preferred, background)
        ) return preferred

        val candidates = intArrayOf(
            preferred,
            scheme.onSurface,
            scheme.onBackground,
            scheme.onPrimary,
            scheme.onPrimaryContainer,
            scheme.onSecondary,
            scheme.onSecondaryContainer,
            scheme.onTertiary,
            scheme.onTertiaryContainer,
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
        )
        return candidates.maxBy { ColorUtils.calculateContrast(it, background) }
    }
}
