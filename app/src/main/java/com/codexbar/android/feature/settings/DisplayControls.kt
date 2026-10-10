package com.codexbar.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.UsageWindow
import com.codexbar.android.core.presentation.DisplayOptions

@Composable
internal fun DisplayControls(windows: List<UsageWindow>, options: DisplayOptions, onChange: (DisplayOptions, String) -> Unit,
    onReset: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.display_options), style = MaterialTheme.typography.titleSmall)
        windows.forEach { window ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(window.id !in options.hiddenWindows, { visible ->
                    onChange(options.copy(hiddenWindows = if (visible) options.hiddenWindows - window.id else options.hiddenWindows + window.id), "hiddenWindows")
                })
                Text(window.label, Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(options.showAmounts, { onChange(options.copy(showAmounts = it), "showAmounts") })
            Text(stringResource(R.string.display_amounts), Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(options.absoluteReset, { onChange(options.copy(absoluteReset = it), "absoluteReset") })
            Text(stringResource(R.string.display_absolute_reset), Modifier.weight(1f))
        }
        TextButton(onClick = onReset) { Text(stringResource(R.string.restore_defaults)) }
    }
}
