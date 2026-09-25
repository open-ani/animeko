/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.torrent.pikpak.PikPakDriveItem
import kotlin.coroutines.cancellation.CancellationException

@Stable
class PikPakLegacyNoticeState(
    private val backgroundScope: CoroutineScope,
    private val fetchItems: suspend () -> List<PikPakDriveItem>,
    private val deleteItems: suspend (ids: List<String>) -> Unit,
) {
    var items: List<PikPakDriveItem> by mutableStateOf(emptyList())
        private set

    var deleting: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    private var checked = false

    fun check() {
        if (checked) return
        checked = true
        backgroundScope.launch {
            items = fetchItems()
        }
    }

    fun deleteAll(onAnswered: () -> Unit) {
        if (deleting) return
        val targets = items.map { it.id }
        deleting = true
        error = null
        backgroundScope.launch {
            try {
                deleteItems(targets)
                items = emptyList()
                onAnswered()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
            } finally {
                deleting = false
            }
        }
    }

    fun keep(onAnswered: () -> Unit) {
        items = emptyList()
        error = null
        onAnswered()
    }
}
