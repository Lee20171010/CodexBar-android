package com.codexbar.android.core.network.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface DeepSeekApiService {
    @GET("user/balance")
    suspend fun balance(@Header("Authorization") authorization: String): Response<BalanceResponse>
}

@Serializable
data class BalanceResponse(
    @SerialName("is_available") val available: Boolean,
    @SerialName("balance_infos") val balances: List<Balance>
)

@Serializable
data class Balance(val currency: String, @SerialName("total_balance") val total: String)
