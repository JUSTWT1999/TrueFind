package com.truefind.app.dispatch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.truefind.app.model.AiTargetApp
import com.truefind.app.model.KnownAiTargets

class AiAppRegistry(private val context: Context) {

    fun detectTargets(): List<AiTargetApp> {
        val pm = context.packageManager
        return KnownAiTargets.ALL.map { target ->
            val supportsImg = canHandleSend(pm, target.packageName, "image/png")
            val supportsTxt = canHandleSend(pm, target.packageName, "text/plain")
            val installed = supportsImg || supportsTxt || isPackageInstalled(pm, target.packageName)
            target.copy(
                isInstalled = installed,
                supportsImageShare = supportsImg,
                supportsTextShare = supportsTxt
            )
        }.sortedByDescending { it.isInstalled }
    }

    private fun canHandleSend(pm: PackageManager, pkg: String, mimeType: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            setPackage(pkg)
        }
        val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return matches.isNotEmpty()
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        }.getOrDefault(false)
    }
}
