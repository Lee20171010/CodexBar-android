package com.codexbar.android.core.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.codexbar.android.core.domain.model.Result
import java.io.File

/**
 * Pre-install validation: the APK must be for this exact package, strictly newer,
 * and signed by the same certificate as the installed app. The system installer
 * re-checks all of this; failing here avoids handing the user a doomed install.
 */
class ApkVerifier(private val context: Context) {

    fun verify(apk: File): Result<Unit, String> {
        if (Build.VERSION.SDK_INT < 28) {
            return Result.Failure("Requires API 28")
        }
        val archive = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return Result.Failure("Cannot read APK")
        val current = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_SIGNING_CERTIFICATES
        )
        val error = verifyArchive(
            archivePackage = archive.packageName,
            archiveVersionCode = archive.longVersionCode,
            archiveCerts = archive.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() },
            currentPackage = context.packageName,
            currentVersionCode = current.longVersionCode,
            currentCerts = current.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
        )
        return if (error == null) Result.Success(Unit) else Result.Failure(error)
    }

    companion object {
        /** Pure core of [verify]; certificates are DER-encoded signer certs. */
        fun verifyArchive(
            archivePackage: String?,
            archiveVersionCode: Long,
            archiveCerts: List<ByteArray>,
            currentPackage: String,
            currentVersionCode: Long,
            currentCerts: List<ByteArray>
        ): String? = when {
            archivePackage != currentPackage -> "Package mismatch"
            archiveVersionCode <= currentVersionCode -> "Not newer"
            archiveCerts.isEmpty() || currentCerts.isEmpty() -> "No signing info"
            // Every signer of the new APK must already be a signer of the
            // installed app; partial overlap on multi-signer APKs is rejected.
            archiveCerts.size != currentCerts.size || !archiveCerts.all { archiveCert ->
                currentCerts.any { it.contentEquals(archiveCert) }
            } -> "Signature mismatch"
            else -> null
        }
    }
}
