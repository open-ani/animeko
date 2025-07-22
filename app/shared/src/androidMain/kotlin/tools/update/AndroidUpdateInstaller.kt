/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import me.him188.ani.BuildConfig
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.File


class AndroidUpdateInstaller : UpdateInstaller {
    private companion object {
        private val logger = logger<AndroidUpdateInstaller>()
    }

    override fun install(file: SystemPath, context: ContextMP): InstallationResult {
        logger.info { "Requesting install APK" }
        if (!context.packageManager.canRequestPackageInstalls()) {
            // Request permission from the user
            kotlin.runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse(String.format("package:%s", context.packageName)))
                context.startActivity(intent)
            }.onFailure {
                logger.warn(it) { "Failed to request permission to install APK" }
            }
        } else {
            kotlin.runCatching {
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
        val intent = Intent(Intent.ACTION_VIEW)
//        val externalFile = Environment.getExternalStorageDirectory().resolve("Download/api-update.apk")
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            MediaStore.createWriteRequest(
//                context.contentResolver,
//                listOf(Uri.fromFile(externalFile)),
//            ).apply {
//                startActivity(context, intent, null)
//            }
//        }
//        file.copyTo(externalFile)
        //intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        val apkUri = FileProvider.getUriForFile(context, "${BuildConfig.APP_APPLICATION_ID}.fileprovider", file)
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        //val resInfoList: List<ResolveInfo> =
        //    context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        //for (resolveInfo in resInfoList) {
        //    val packageName = resolveInfo.activityInfo.packageName
        //    context.grantUriPermission(
        //        packageName,
        //        apkUri,
        //        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
        //    )
        //}
        context.startActivity(intent)
    }
}
