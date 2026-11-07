// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

/** One UI's circular thumb, with a separate 52x48dp accessible interaction target. */
@Composable
fun OneUiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(uncheckedTrackColor = Color(0xff999999)),
) {
    val position by animateFloatAsState(if (checked) 1f else 0f, label = "switch")
    // Material's unchecked container can equal our card surface; keep the off track visible.
    val track = if (checked) colors.checkedTrackColor
        else if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xff555555)
        else Color(0xff999999)
    Box(
        modifier.size(52.dp, 48.dp).toggleable(
            value = checked, enabled = enabled && onCheckedChange != null,
            role = Role.Switch, onValueChange = { onCheckedChange?.invoke(it) },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(46.dp, 28.dp)) {
            val alpha = if (enabled) 1f else .38f
            drawRoundRect(track.copy(alpha = alpha), cornerRadius = CornerRadius(size.height / 2))
            val radius = size.height / 2 - 2.dp.toPx()
            val fraction = if (layoutDirection == LayoutDirection.Rtl) 1f - position else position
            val x = size.height / 2 + (size.width - size.height) * fraction
            drawCircle(Color.White.copy(alpha = alpha), radius, Offset(x, size.height / 2))
        }
    }
}
