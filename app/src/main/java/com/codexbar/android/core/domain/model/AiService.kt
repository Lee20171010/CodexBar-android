package com.codexbar.android.core.domain.model

@kotlinx.serialization.Serializable
enum class AiService(
    val displayName: String,
    val brandColor: Long,
    val baseUrl: String,
    val requiresManualCredentials: Boolean
) {
    CLAUDE(
        displayName = "Claude",
        brandColor = 0xFFD4A574,
        baseUrl = "https://api.anthropic.com/",
        requiresManualCredentials = false
    ),
    CODEX(
        displayName = "Codex",
        brandColor = 0xFF10A37F,
        baseUrl = "https://chatgpt.com/",
        requiresManualCredentials = false
    ),
    GEMINI(
        displayName = "Gemini",
        brandColor = 0xFF4285F4,
        baseUrl = "https://cloudcode-pa.googleapis.com/",
        requiresManualCredentials = true
    ),
    OPENCODE_GO(
        displayName = "OpenCode Go",
        brandColor = 0xFF3B82F6,
        baseUrl = "https://opencode.ai/",
        requiresManualCredentials = true
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        brandColor = 0xFF4D6BFE,
        baseUrl = "https://api.deepseek.com/",
        requiresManualCredentials = true
    ),
    COPILOT(
        displayName = "GitHub Copilot",
        brandColor = 0xFF6E40C9,
        baseUrl = "https://api.github.com/",
        requiresManualCredentials = true
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        brandColor = 0xFF6467F2,
        baseUrl = "https://openrouter.ai/",
        requiresManualCredentials = true
    );

    val usesNativeApiKey: Boolean get() = this == OPENCODE_GO || this == OPENROUTER || this == COPILOT
    val usesApiKey: Boolean get() = usesNativeApiKey || this == DEEPSEEK
}
