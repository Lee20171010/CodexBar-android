package com.codexbar.android.core.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DeviceAuthReply(val code: Int, val body: JsonObject)

/** Bounded, cancellable transport; callers supply a no-redirect, no-logging client. */
internal suspend fun OkHttpClient.postDeviceAuth(url: HttpUrl, body: RequestBody): DeviceAuthReply =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(Request.Builder().url(url).header("Accept", "application/json").post(body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Sign-in connection failed. Try again."))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val source = response.body?.source() ?: throw IOException()
                        source.request(65_537)
                        if (source.buffer.size > 65_536) throw IOException()
                        val text = source.readUtf8()
                        val json = try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) {
                            if (response.isSuccessful) throw IOException() else JsonObject(emptyMap())
                        }
                        if (continuation.isActive) continuation.resume(DeviceAuthReply(response.code, json))
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(IOException("Unexpected sign-in response. Try again."))
                    }
                }
            }
        })
    }
