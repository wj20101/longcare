package com.ytone.longcare.platform.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import com.ytone.longcare.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verifies an update APK before it can reach an installer intent.
 *
 * Android's package installer performs its own signature checks, but validating here prevents a
 * wrong, downgraded, truncated, or tampered package from ever being presented to the user.
 */
@Singleton
class ApkPackageVerifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun verify(
        apkFile: File,
        expectedVersionCode: Long = 0L,
    ): ApkVerificationResult {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.FILE_MISSING,
                message = context.getString(R.string.update_apk_missing),
            )
        }

        val packageManager = context.packageManager
        val archiveInfo = packageManager.readArchivePackageInfo(apkFile)
            ?: return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.INVALID_APK,
                message = context.getString(R.string.update_apk_invalid),
            )
        if (archiveInfo.packageName != context.packageName) {
            return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.PACKAGE_MISMATCH,
                message = context.getString(R.string.update_apk_package_mismatch),
            )
        }

        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo)
        if (expectedVersionCode > 0L && archiveVersionCode != expectedVersionCode) {
            return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.VERSION_MISMATCH,
                message = context.getString(R.string.update_apk_version_mismatch),
            )
        }

        val installedInfo = packageManager.readInstalledPackageInfo(context.packageName)
            ?: return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.CURRENT_PACKAGE_UNAVAILABLE,
                message = context.getString(R.string.update_current_signature_unavailable),
            )
        val installedVersionCode = PackageInfoCompat.getLongVersionCode(installedInfo)
        if (archiveVersionCode <= installedVersionCode) {
            return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.VERSION_NOT_NEWER,
                message = context.getString(R.string.update_apk_not_newer),
            )
        }

        val installedSigningIdentity = installedInfo.signingIdentity()
        val archiveSigningIdentity = archiveInfo.signingIdentity()
        if (
            installedSigningIdentity == null ||
            archiveSigningIdentity == null ||
            !isSigningIdentityCompatible(installedSigningIdentity, archiveSigningIdentity)
        ) {
            return ApkVerificationResult.Invalid(
                reason = ApkVerificationFailure.SIGNATURE_MISMATCH,
                message = context.getString(R.string.update_apk_signature_mismatch),
            )
        }

        return ApkVerificationResult.Valid(
            versionCode = archiveVersionCode,
            sha256 = apkFile.sha256Hex(),
        )
    }
}

sealed interface ApkVerificationResult {
    data class Valid(
        val versionCode: Long,
        val sha256: String,
    ) : ApkVerificationResult

    data class Invalid(
        val reason: ApkVerificationFailure,
        val message: String,
    ) : ApkVerificationResult
}

enum class ApkVerificationFailure {
    FILE_MISSING,
    INVALID_APK,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    VERSION_NOT_NEWER,
    CURRENT_PACKAGE_UNAVAILABLE,
    SIGNATURE_MISMATCH,
}

internal data class SigningIdentity(
    val currentSigners: Set<String>,
    val signingHistory: Set<String>,
    val hasMultipleSigners: Boolean,
)

internal fun isSigningIdentityCompatible(
    installed: SigningIdentity,
    candidate: SigningIdentity,
): Boolean {
    if (installed.currentSigners.isEmpty() || candidate.currentSigners.isEmpty()) return false
    return if (installed.hasMultipleSigners || candidate.hasMultipleSigners) {
        installed.currentSigners == candidate.currentSigners
    } else {
        installed.currentSigners.all(candidate.signingHistory::contains)
    }
}

private fun PackageManager.readArchivePackageInfo(file: File): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        getPackageArchiveInfo(file.absolutePath, signingInfoFlags())
    }

private fun PackageManager.readInstalledPackageInfo(packageName: String): PackageInfo? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, signingInfoFlags())
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

private fun signingInfoFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

private fun PackageInfo.signingIdentity(): SigningIdentity? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingIdentityFromModernInfo()
    } else {
        @Suppress("DEPRECATION")
        signatures?.toSigningIdentity()
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun PackageInfo.signingIdentityFromModernInfo(): SigningIdentity? {
    val info = signingInfo ?: return null
    val hasMultipleSigners = info.hasMultipleSigners()
    val currentSigners = info.apkContentsSigners?.digestSet().orEmpty()
    val signingHistory = if (hasMultipleSigners) {
        currentSigners
    } else {
        info.signingCertificateHistory?.digestSet().orEmpty().ifEmpty { currentSigners }
    }
    return SigningIdentity(currentSigners, signingHistory, hasMultipleSigners)
}

private fun Array<Signature>.toSigningIdentity(): SigningIdentity {
    val signers = digestSet()
    return SigningIdentity(
        currentSigners = signers,
        signingHistory = signers,
        hasMultipleSigners = size > 1,
    )
}

private fun Array<Signature>.digestSet(): Set<String> = mapTo(linkedSetOf()) { signature ->
    MessageDigest.getInstance(SHA_256).digest(signature.toByteArray()).toHexString()
}

private fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance(SHA_256)
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte)
}

private const val SHA_256 = "SHA-256"
