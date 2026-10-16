package com.codexbar.android.core.update

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Locale

/**
 * Enforces HTTPS on every network hop of an update request, including OkHttp
 * redirect follow-ups (network interceptors see each redirected request).
 * No userinfo, no custom ports, GitHub asset hosts only.
 */
class UpdateRedirectPolicy : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isAllowed(chain.request().url)) {
            throw IOException("Blocked update host")
        }
        return chain.proceed(chain.request())
    }

    companion object {
        val ALLOWED_HOSTS = setOf(
            "github.com",
            "api.github.com",
            "raw.githubusercontent.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )

        fun isAllowed(url: HttpUrl): Boolean =
            url.scheme == "https" &&
                url.username.isEmpty() && url.password.isEmpty() &&
                (url.port == -1 || url.port == 443) &&
                url.host.lowercase(Locale.US) in ALLOWED_HOSTS
    }
}
