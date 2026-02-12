/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exprovider

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.SharedFlow
import me.him188.ani.app.platform.Context

/**
 * Provides external content views.
 */
interface ExternalContentProvider {
    val events: SharedFlow<ExternalContentEvent>

    /**
     * Provides the view for displaying external content.
     *
     * @param context the context
     * @param contentId identifier for the content provider
     * @return the view to display
     */
    fun viewProvider(context: Context, contentId: String, expectedWidth: Int, expectedHeight: Int): Any

    /**
     * Initializes and loads the external content.
     *
     * @param context the Android [android.content.Context]
     * @param contentId identifier for the content to load
     */
    suspend fun initialize(context: Context, contentId: String)

    fun dispose(contentId: String)
}

sealed interface ExternalContentEvent {
    data class Shown(val contentId: String) : ExternalContentEvent
    data class LoadFailed(val contentId: String) : ExternalContentEvent
    data class Clicked(val contentId: String) : ExternalContentEvent
    data class Closed(val contentId: String) : ExternalContentEvent
}

val LocalExternalContentProvider = staticCompositionLocalOf<ExternalContentProvider?> {
    null
}