package com.codexbar.android.core.data

import com.codexbar.android.core.domain.model.*
import com.codexbar.android.core.domain.repository.CredentialSession
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Public numeric balance API; pinned native CLI only exposes a formatted balance description. */
class DeepSeekRepositoryImpl(private val api: DeepSeekApiService) : QuotaRepository {
    override suspend fun fetchQuota(session: CredentialSession): Result<QuotaInfo, AppError> {
        require(session.connection.service == AiService.DEEPSEEK)
        val credential = session.credential as? Credential.DeepSeekCredential
            ?: return Result.Failure(AppError.CredentialNotFound(AiService.DEEPSEEK))
        return try {
            val response = api.balance("Bearer ${credential.accessToken}")
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return Result.Failure(when (response.code()) {
                    401, 403 -> AppError.AuthError(AiService.DEEPSEEK, isTerminal = true)
                    429 -> AppError.RateLimited
                    in 500..599 -> AppError.ServiceUnavailable
                    else -> AppError.NetworkError("DeepSeek balance request failed.")
                })
            }
            val body = requireNotNull(response.body())
            val balances = body.balances.map { balance ->
                require(balance.currency in setOf("USD", "CNY"))
                val amount = requireNotNull(balance.total.toDoubleOrNull())
                require(amount.isFinite() && amount >= 0)
                balance.currency to amount
            }
            require(balances.isNotEmpty() && balances.map { it.first }.distinct().size == balances.size)
            // Same preference as upstream: do not hide funded CNY behind an empty USD row.
            val selected = balances.firstOrNull { it.first == "USD" && it.second > 0 }
                ?: balances.firstOrNull { it.second > 0 }
                ?: balances.firstOrNull { it.first == "USD" } ?: balances.first()
            val now = Instant.now()
            Result.Success(QuotaInfo(AiService.DEEPSEEK, emptyList(), null,
                if (body.available) "API balance" else "Balance insufficient for API calls", now,
                ReportedMoney(selected.second, null, selected.first, null, now)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.Failure(AppError.NetworkError("DeepSeek connection failed."))
        } catch (_: Exception) {
            Result.Failure(AppError.ParseError("Unexpected DeepSeek balance response."))
        }
    }
}
