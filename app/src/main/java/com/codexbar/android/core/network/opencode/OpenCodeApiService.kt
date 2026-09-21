package com.codexbar.android.core.network.opencode

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenCodeApiService {
    @GET("_server")
    suspend fun getServerFunction(
        @Query("id") serverId: String,
        @Header("Cookie") cookie: String,
        @Header("Origin") origin: String,
        @Header("Referer") referer: String,
        @Header("User-Agent") userAgent: String,
        @Header("Accept") accept: String,
        @Header("X-Server-Instance") serverInstance: String,
        @Header("X-Server-Id") serverHeaderId: String = serverId,
        @Query("args") args: String? = null
    ): Response<ResponseBody>

    @POST("_server")
    suspend fun postServerFunction(
        @Header("Cookie") cookie: String,
        @Header("Origin") origin: String,
        @Header("Referer") referer: String,
        @Header("User-Agent") userAgent: String,
        @Header("Accept") accept: String,
        @Header("X-Server-Id") serverId: String,
        @Header("X-Server-Instance") serverInstance: String,
        @Body args: RequestBody
    ): Response<ResponseBody>

    @GET("workspace/{id}/go")
    suspend fun getUsage(
        @Path("id") workspaceId: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("Accept") accept: String
    ): Response<ResponseBody>

    @GET("workspace/{id}")
    suspend fun getWorkspace(
        @Path("id") workspaceId: String,
        @Header("Cookie") cookie: String,
        @Header("User-Agent") userAgent: String,
        @Header("Accept") accept: String
    ): Response<ResponseBody>
}
