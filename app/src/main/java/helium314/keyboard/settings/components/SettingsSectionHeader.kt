// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
