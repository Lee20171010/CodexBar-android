package com.codexbar.android.core.domain.model

@kotlinx.serialization.Serializable
enum class AiService(
    val displayName: String,
    val brandColor: Long,
    val baseUrl: String,
    val requiresManualCredentials: Boolean,
    // Official page where this provider's credential is created. Null when no verified page
    // exists for the credential this app actually accepts (OAuth tokens, PATs, uncertain pages).
    val keyPageUrl: String? = null
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
        requiresManualCredentials = true,
        keyPageUrl = "https://opencode.ai/auth"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        brandColor = 0xFF4D6BFE,
        baseUrl = "https://api.deepseek.com/",
        requiresManualCredentials = true,
        keyPageUrl = "https://platform.deepseek.com/api_keys"
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
        requiresManualCredentials = true,
        keyPageUrl = "https://openrouter.ai/keys"
    ),
    VENICE(
        displayName = "Venice",
        brandColor = 0xFF3C8FDD,
        baseUrl = "https://venice.ai/",
        requiresManualCredentials = true,
        keyPageUrl = "https://venice.ai/settings/api"
    ),
    // Amp: no verified web key page upstream; guidance text only.
    AMP(
        displayName = "Amp",
        brandColor = 0xFFF34E3F,
        baseUrl = "https://ampcode.com/",
        requiresManualCredentials = true,
        keyPageUrl = "https://ampcode.com/settings"
    ),
    ZAI(
        displayName = "z.ai / GLM",
        brandColor = 0xFFE85A6A,
        baseUrl = "https://api.z.ai/",
        requiresManualCredentials = true,
        keyPageUrl = "https://z.ai/manage-apikey"
    ),
    FACTORY(
        displayName = "Factory",
        brandColor = 0xFFFF6B35,
        baseUrl = "https://app.factory.ai/",
        requiresManualCredentials = true,
        keyPageUrl = "https://app.factory.ai/settings/api-keys"
    ),
    // Coding Plan key comes from the Coding Plan console page, not the pay-as-you-go key page.
    ALIBABA_CODING_PLAN(
        displayName = "Alibaba",
        brandColor = 0xFFFF6A00,
        baseUrl = "https://modelstudio.console.alibabacloud.com/",
        requiresManualCredentials = true,
        keyPageUrl = "https://modelstudio.console.alibabacloud.com/ap-southeast-1/?tab=coding-plan#/efm/coding_plan"
    ),
    HYPER(
        displayName = "Charm Hyper",
        brandColor = 0xFFFF60FF,
        baseUrl = "https://hyper.charm.land/",
        requiresManualCredentials = true,
        keyPageUrl = "https://hyper.charm.land"
    ),
    // China-site only: the CLI defaults to kimi.com/api.kimi.com and Android wires no region
    // override, so international kimi.ai keys are not supported yet. Console page is linked
    // for navigation only; the exact key-issuing page is unverified, hence the neutral label.
    KIMI(
        displayName = "Kimi",
        brandColor = 0xFF6157FF,
        baseUrl = "https://www.kimi.com/",
        requiresManualCredentials = true,
        keyPageUrl = "https://www.kimi.com/code/console"
    ),
    // Console root is linked for navigation only (exact key-issuing page unverified), hence the
    // neutral "Open console" label. The native engine defaults to the global region and retries
    // the China mainland host once on credential rejection (MiniMaxUsageFetcher.swift:101-139);
    // Android exposes no region selection. Token Plan Subscription Keys (sk-cp-) only — the
    // upstream .api strategy rejects pay-as-you-go sk-api- keys (MiniMaxProviderDescriptor.swift:161-166).
    MINIMAX(
        displayName = "MiniMax",
        brandColor = 0xFFFE603C,
        baseUrl = "https://platform.minimax.io/",
        requiresManualCredentials = true,
        keyPageUrl = "https://platform.minimax.io"
    );

    val usesNativeApiKey: Boolean get() = this == OPENCODE_GO || this == OPENROUTER || this == COPILOT || this == VENICE || this == AMP || this == ZAI || this == FACTORY || this == ALIBABA_CODING_PLAN || this == HYPER || this == KIMI || this == MINIMAX
    val usesApiKey: Boolean get() = usesNativeApiKey || this == DEEPSEEK
}
