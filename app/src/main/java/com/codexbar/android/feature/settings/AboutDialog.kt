package com.codexbar.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codexbar.android.BuildConfig
import com.codexbar.android.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    var selected by remember { mutableStateOf<String?>(null) }
    val errorText = stringResource(R.string.licenses_unavailable)
    var content by remember(assets, selected) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(assets, selected, errorText) {
        content = withContext(Dispatchers.IO) {
            try {
                val file = selected
                if (file == null) assets.list("licenses").orEmpty().filter { it.endsWith(".txt") }.sorted()
                else assets.open("licenses/$file").bufferedReader().use { it.readText() }.split("\n\n")
            } catch (_: IOException) {
                listOf(errorText)
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (selected != null) selected = null else onDismiss() },
        title = { Text(selected?.removeSuffix(".txt") ?: stringResource(R.string.about_licenses)) },
        text = {
            Column {
                if (selected == null) {
                    Text(stringResource(R.string.unofficial_port), style = MaterialTheme.typography.bodyMedium)
                    Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall)
                }
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(content) { text ->
                        if (selected == null && text != errorText) {
                            TextButton(onClick = { selected = text }, modifier = Modifier.fillMaxWidth()) {
                                Text(text.removeSuffix(".txt"))
                            }
                        } else {
                            SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            if (selected != null) TextButton(onClick = { selected = null }) { Text(stringResource(R.string.back)) }
        }
    )
}
