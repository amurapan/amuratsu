package org.skepsun.kototoro.core.os

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import org.skepsun.kototoro.parsers.util.suspendlazy.suspendLazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppValidator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("InlinedApi")
    val isOriginalApp = suspendLazy(Dispatchers.Default) {
        val certificates = mapOf(CERT_SHA256.hexToByteArray() to PackageManager.CERT_INPUT_SHA256)
        PackageInfoCompat.hasSignatures(context.packageManager, context.packageName, certificates, false)
    }

    private companion object {
        // SHA-256 of the Amuratsu public release certificate.
        private const val CERT_SHA256 = "760433ea58d692ec5beab576857b485af53ccff953312ff1c12ef80ac7c6d9d1"
    }
}
