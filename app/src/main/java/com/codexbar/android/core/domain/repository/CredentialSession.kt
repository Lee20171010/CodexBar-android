package com.codexbar.android.core.domain.repository

import com.codexbar.android.core.domain.model.AccountConnection
import com.codexbar.android.core.domain.model.Credential
import kotlinx.coroutines.CancellationException

/** One request's credentials. A draft's default writer changes memory only. */
class CredentialSession(
    val connection: AccountConnection,
    credential: Credential?,
    private val persist: (Credential, Credential) -> Boolean = { _, _ -> true }
) {
    var credential: Credential? = credential
        private set

    fun replace(updated: Credential) {
        val previous = checkNotNull(credential)
        require(previous.javaClass == updated.javaClass && updated.accessToken.isNotBlank())
        if (!persist(previous, updated)) throw CancellationException("Connection changed during request")
        credential = updated
    }
}
