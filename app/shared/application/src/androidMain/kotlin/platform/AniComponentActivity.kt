/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue


abstract class AniComponentActivity : ComponentActivity() {
    @Stable
    val snackbarHostState = SnackbarHostState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeViewTreeOwners()
        // FileKit.openFileSaver 等非 Compose 对话框需要 Activity 的 ActivityResultRegistry (图片查看器的保存)
        FileKit.init(this)
    }

    private val requestPermissionHandlers: MutableCollection<(Boolean) -> Unit> = ConcurrentLinkedQueue()
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requestPermissionHandlers.forEach { it.invoke(granted) }
        }
    private val requestPermissionLock = Mutex()

    private val requestExternalDocumentTreeHandler: AtomicRef<((Uri?) -> Unit)?> = atomic(null)
    private val requestExternalDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val handler by requestExternalDocumentTreeHandler
            handler?.invoke(uri)
        }

    suspend fun requestPermission(permission: String): Boolean {
        val res = CompletableDeferred<Boolean>()
        return requestPermissionLock.withLock {
            val handler: (Boolean) -> Unit = { res.complete(it) }
            requestPermissionHandlers.add(handler)
            try {
                requestPermissionLauncher.launch(permission)
                res.await()
            } finally {
                requestPermissionHandlers.remove(handler)
            }
        }
    }

    /**
     * Get a document tree with rw permission via DocumentUI.
     */
    suspend fun requestExternalDocumentTree(): String? {
        val res = CompletableDeferred<String?>()
        val handler: (Uri?) -> Unit = { uri: Uri? -> res.complete(uri?.toString()) }

        if (!requestExternalDocumentTreeHandler.compareAndSet(null, handler)) {
            return null
        }

        return try {
            requestExternalDocumentTreeLauncher.launch(null)
            res.await()
        } finally {
            requestExternalDocumentTreeHandler.compareAndSet(handler, null)
        }
    }

    fun enableDrawingToSystemBars() {
        enableEdgeToEdge(
            SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

suspend fun AniComponentActivity.showSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short
): SnackbarResult {
    return snackbarHostState.showSnackbar(message, actionLabel, withDismissAction, duration)
}

fun AniComponentActivity.showSnackbarAsync(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short
) {
    lifecycleScope.launch(Dispatchers.Main) {
        try {
            snackbarHostState.showSnackbar(message, actionLabel, withDismissAction, duration)
        } catch (e: Exception) { // exception will crash app
            e.printStackTrace()
        }
    }
}
