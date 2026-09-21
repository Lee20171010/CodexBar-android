package com.codexbar.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SsidChart
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.ui.graphics.vector.ImageVector
import com.codexbar.android.core.domain.model.AiService

fun AiService.providerIcon(): ImageVector {
    return when (this) {
        AiService.CLAUDE -> Icons.Rounded.Psychology
        AiService.CODEX -> Icons.Rounded.Terminal
        AiService.GEMINI -> Icons.Rounded.AutoAwesome
        AiService.COPILOT -> Icons.Rounded.Code
        AiService.CURSOR -> Icons.Rounded.Mouse
        AiService.ZAI -> Icons.Rounded.DataUsage
        AiService.ZENMUX -> Icons.Rounded.Hub
        AiService.KIMI -> Icons.Rounded.Bolt
        AiService.ELEVENLABS -> Icons.Rounded.RecordVoiceOver
        AiService.OPENROUTER -> Icons.AutoMirrored.Rounded.AltRoute
        AiService.SYNTHETIC -> Icons.Rounded.Science
        AiService.CHUTES -> Icons.Rounded.SsidChart
        AiService.DEEPSEEK -> Icons.Rounded.Water
        AiService.VENICE -> Icons.Rounded.Explore
        AiService.MOONSHOT -> Icons.Rounded.AutoAwesome
        AiService.CLINEPASS -> Icons.Rounded.Code
        AiService.IBM_BOB -> Icons.Rounded.DataUsage
        AiService.FIREWORKS -> Icons.Rounded.Bolt
        AiService.OPENCODE_GO -> Icons.Rounded.Bolt
    }
}
