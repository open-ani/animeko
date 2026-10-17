/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.jsonapi

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.jsonapi.JsonApiMediaSourceEngine
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.mediasource.editor.editMediaSourceArguments
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class EditJsonApiMediaSourceViewModel(
    initialInstanceId: String,
) : AbstractViewModel(), KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val codecManager: MediaSourceCodecManager by inject()
    private val clientProvider: HttpClientProvider by inject()

    private val instanceId: MutableStateFlow<String> = MutableStateFlow(initialInstanceId)

    val state: Flow<EditJsonApiMediaSourcePageState> = mediaSourceManager.editMediaSourceArguments(
        this.instanceId,
        JsonApiMediaSourceArguments.serializer(),
        JsonApiMediaSourceArguments.Default,
    ) { _, argumentsStorage, allowEditState ->
        EditJsonApiMediaSourcePageState(
            argumentsStorage = argumentsStorage,
            allowEditState = allowEditState,
            engine = JsonApiMediaSourceEngine(clientProvider.get(ScopedHttpClientUserAgent.BROWSER)),
            codecManager = codecManager,
            backgroundScope = this,
        )
    }
}
