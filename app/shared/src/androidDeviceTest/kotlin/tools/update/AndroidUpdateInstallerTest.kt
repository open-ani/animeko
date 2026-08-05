/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import android.content.Intent
import android.net.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class AndroidUpdateInstallerTest {
    @Test
    fun installIntentGrantsReadAccessToApkUri() {
        val apkUri = Uri.parse("content://me.him188.ani.fileprovider/cache_files/updates/download/update.apk")

        val intent = createApkInstallIntent(apkUri, "update.apk")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(apkUri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        val clipData = assertNotNull(intent.clipData)
        assertEquals(1, clipData.itemCount)
        assertEquals(apkUri, clipData.getItemAt(0).uri)
    }
}
