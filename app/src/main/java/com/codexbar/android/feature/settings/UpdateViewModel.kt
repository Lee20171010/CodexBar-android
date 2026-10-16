package com.codexbar.android.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codexbar.android.BuildConfig
import com.codexbar.android.core.domain.model.Result
import com.codexbar.android.core.update.ApkDownloader
import com.codexbar.android.core.update.ApkVerifier
import com.codexbar.android.core.update.UpdateChecker
import com.codexbar.android.core.update.CallHandle
import com.codexbar.android.di.UpdateClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: UpdateChecker.UpdateRelease) : UpdateUiState
    data class Downloading(val fraction: Float?) : UpdateUiState
    data object Verifying : UpdateUiState
    data class Ready(val release: UpdateChecker.UpdateRelease) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

/**
 * Manual-only update flow: check GitHub latest release for this fork, download
 * the matching APK to app-private storage, verify, then hand a content URI to
 * the system installer. Nothing runs at startup and nothing auto-installs.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    @UpdateClient client: OkHttpClient,
    json: Json
) : ViewModel() {

    private val checker = UpdateChecker(client, json)
    private val downloader = ApkDownloader(client)
    private val verifier = ApkVerifier(context)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var work: Job? = null
    private var handle: CallHandle? = null

    private fun newHandle(): CallHandle = CallHandle().also { handle = it }

    fun check() {
        if (work?.isCompleted == false) return
        work = viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            when (val result = checker.check(newHandle())) {
                is Result.Success -> _state.value =
                    if (UpdateChecker.isNewer(result.value.version, BuildConfig.VERSION_NAME)) {
                        UpdateUiState.Available(result.value)
                    } else {
                        UpdateUiState.UpToDate
                    }
                is Result.Failure -> _state.value = UpdateUiState.Failed(result.error)
            }
        }
    }

    fun download(release: UpdateChecker.UpdateRelease) {
        if (work?.isCompleted == false) return
        work = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(null)
            val target = File(updatesDir(), release.asset.name)
            when (val result = downloader.download(release.asset, target, newHandle()) { fraction ->
                _state.value = UpdateUiState.Downloading(fraction)
            }) {
                is Result.Success -> {
                    _state.value = UpdateUiState.Verifying
                    when (val verified = verifier.verify(target)) {
                        is Result.Success -> _state.value = UpdateUiState.Ready(release)
                        is Result.Failure -> {
                            target.delete()
                            _state.value = UpdateUiState.Failed(verified.error)
                        }
                    }
                }
                is Result.Failure -> _state.value = UpdateUiState.Failed(result.error)
            }
        }
    }

    fun cancel() {
        handle?.cancel()
        work?.cancel()
        _state.value = UpdateUiState.Idle
    }

    fun hasInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /** Installer intent; null when the verified file is gone (caller re-downloads). */
    fun installIntent(release: UpdateChecker.UpdateRelease): Intent? {
        val file = File(updatesDir(), release.asset.name)
        if (!file.exists()) {
            _state.value = UpdateUiState.Failed("missing-file")
            return null
        }
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun updatesDir(): File = File(context.filesDir, "updates")

    override fun onCleared() {
        handle?.cancel()
        work?.cancel()
    }
}
