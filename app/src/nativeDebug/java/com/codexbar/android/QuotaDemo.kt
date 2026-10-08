package com.codexbar.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.presentation.*
import com.codexbar.android.feature.dashboard.*
import com.codexbar.android.ui.theme.CodexBarTheme
import java.time.Instant

/** Fixed synthetic UI data. Acceptance sources only; no account storage or network. */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Phone", widthDp = 360, heightDp = 720)
@Composable
internal fun QuotaDemo(dark: Boolean = false, large: Boolean = false) {
    val now = Instant.parse("2026-10-08T12:00:00Z")
    val quota = QuotaInfo(AiService.CODEX, listOf(
        UsageWindow("5-Hour", .57, now.plusSeconds(7200), "primary"),
        UsageWindow("7-Day", .03, now.plusSeconds(172800), "secondary"),
        UsageWindow("Demo long model-specific code-review quota pool", 1.15, null, "model", supplemental = true)
    ), null, "Demo plan", now.minusSeconds(7200), source = "synthetic-demo")
    val account = AccountConnection("CODEX", AiService.CODEX, "Demo account with a long readable name",
        "00000000-0000-0000-0000-000000000001")
    val card = ServiceCardData(account, emptyList(), null, quota.tier,
        snapshot = QuotaSnapshot(quota, now, QuotaFailure.NETWORK))
    var details by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
        CodexBarTheme(darkTheme = dark, dynamicColor = false) {
            Surface(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val wide = maxWidth >= 840.dp
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Synthetic demo", style = MaterialTheme.typography.titleLarge)
                            ServiceCard(card, { details = true }, Modifier.fillMaxWidth(), now)
                        }
                        if (wide) QuotaDetail(card, now, {}, {}, Modifier.weight(1f))
                    }
                    if (details && !wide) ModalBottomSheet(onDismissRequest = { details = false }) {
                        QuotaDetail(card, now, {}, {}, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Preview(name = "Large text", widthDp = 360, heightDp = 900)
@Composable private fun LargeDemo() = QuotaDemo(large = true)
@Preview(name = "Dark", widthDp = 360, heightDp = 720)
@Composable private fun DarkDemo() = QuotaDemo(dark = true)
@Preview(name = "Wide", widthDp = 1000, heightDp = 720)
@Composable private fun WideDemo() = QuotaDemo()
