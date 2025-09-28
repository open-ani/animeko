/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.app.platform

import android.net.Uri
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue


abstract class BaseComponentActivity : AppCompatActivity() {
    @Stable
    val snackbarHostState = SnackbarHostState()

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

suspend fun BaseComponentActivity.showSnackbar(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = SnackbarDuration.Short
): SnackbarResult {
    return snackbarHostState.showSnackbar(message, actionLabel, withDismissAction, duration)
}

fun BaseComponentActivity.showSnackbarAsync(
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
