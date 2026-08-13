/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.File


private const val APK_MIME_TYPE = "application/vnd.android.package-archive"


class AndroidUpdateInstaller : UpdateInstaller {
    private companion object {
        private val logger = logger<AndroidUpdateInstaller>()
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        logger.info { "Requesting install APK" }
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Request permission from the user
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse(String.format("package:%s", context.packageName)))
                context.startActivity(intent)
            }.onFailure {
                logger.warn(it) { "Failed to request permission to install APK" }
            }
        } else {
            runCatching {
                installApk(context, file.toFile())
            }.onFailure {
                logger.warn(it) { "Failed to install update APK using installApkLegacy" }
            }
        }
        return InstallationResult.Succeed
    }


    // Function to install APK
    private fun installApk(
        context: Context,
        file: File,
    ) {
        // 权限声明是 "${applicationId}.fileprovider", 而电视变体带 applicationIdSuffix ".tv" ——
        // 用编译期常量 AndroidBuildConfig.APP_APPLICATION_ID 在电视上会拼出不存在的 authority
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = createApkInstallIntent(apkUri, file.name)

        // Some third-party installers are launched through a system installer replacement. In that flow, Android may
        // grant the URI to the original resolved activity instead of the installer that ultimately reads the APK.
        // Grant every discoverable handler read access as well, while retaining the intent grant for whichever
        // activity ultimately receives it.
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .forEach { packageName ->
                runCatching {
                    context.grantUriPermission(packageName, apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }.onFailure {
                    logger.warn(it) { "Failed to grant APK read permission to $packageName" }
                }
            }

        context.startActivity(intent)
    }
}


internal fun createApkInstallIntent(apkUri: Uri, apkName: String): Intent = Intent(Intent.ACTION_VIEW).apply {
    setDataAndType(apkUri, APK_MIME_TYPE)
    clipData = ClipData.newRawUri(apkName, apkUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
