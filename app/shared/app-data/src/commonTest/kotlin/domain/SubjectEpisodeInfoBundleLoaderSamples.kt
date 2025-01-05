/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.shareIn
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundle
import me.him188.ani.app.domain.episode.SubjectEpisodeInfoBundleLoader
import me.him188.ani.test.Sample
import org.koin.core.Koin

@Sample
fun getBundleFlow(subjectId: Int, koin: Koin, backgroundScope: CoroutineScope) {
    val episodeIdFlow = MutableStateFlow(2)

    val loader = SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)

    val bundleFlow: SharedFlow<SubjectEpisodeInfoBundle?> = loader.infoBundleFlowFlatten
        .shareIn(backgroundScope, started = WhileSubscribed(), replay = 1)
}
