package com.codexbar.android.di

import android.content.Context
import android.util.Log
import com.codexbar.android.core.security.EncryptedPrefsManager
import com.codexbar.android.core.widget.WidgetPrefsManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches the application-scoped managers from components Hilt cannot inject into, such as the
 * Glance widget composition and static initialization helpers.
 *
 * Constructing those managers directly gives every caller its own credential cache, repeats the
 * legacy-preferences migration, and re-warms Android Keystore-backed state on surfaces that run
 * under a broadcast deadline.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppSingletonEntryPoint {
    fun widgetPrefsManager(): WidgetPrefsManager

    fun encryptedPrefsManager(): EncryptedPrefsManager
}

/**
 * Returns the singleton entry point, or `null` when the application component is not ready yet.
 */
fun appSingletonEntryPointOrNull(context: Context): AppSingletonEntryPoint? {
    return runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppSingletonEntryPoint::class.java
        )
    }.getOrElse { error ->
        Log.w(TAG, "Application dependencies are not available yet", error)
        null
    }
}

private const val TAG = "CodexBarDi"
