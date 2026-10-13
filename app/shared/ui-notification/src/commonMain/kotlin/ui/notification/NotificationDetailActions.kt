/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.SettingsTab
import me.him188.ani.app.navigation.SubjectDetailPlaceholder

/**
 * 通知详情中可以跳转的位置.
 */
@Immutable
class NotificationDetailActions(
    val onOpenEpisode: (subjectId: Int, episodeId: Int) -> Unit,
    val onOpenSubject: (subjectId: Int, placeholder: SubjectDetailPlaceholder?) -> Unit,
    val onOpenPerson: (personId: Int) -> Unit,
    val onOpenCharacter: (characterId: Int) -> Unit,
    val onReauthorizeBangumi: () -> Unit,
    val onOpenAccountSettings: () -> Unit,
) {
    companion object {
        val Noop = NotificationDetailActions(
            onOpenEpisode = { _, _ -> },
            onOpenSubject = { _, _ -> },
            onOpenPerson = {},
            onOpenCharacter = {},
            onReauthorizeBangumi = {},
            onOpenAccountSettings = {},
        )
    }
}

@Composable
fun rememberNotificationDetailActions(navigator: AniNavigator): NotificationDetailActions = remember(navigator) {
    NotificationDetailActions(
        onOpenEpisode = { subjectId, episodeId -> navigator.navigateEpisodeDetails(subjectId, episodeId) },
        onOpenSubject = { subjectId, placeholder -> navigator.navigateSubjectDetails(subjectId, placeholder) },
        onOpenPerson = { navigator.navigatePersonDetails(it) },
        onOpenCharacter = { navigator.navigateCharacterDetails(it) },
        onReauthorizeBangumi = { navigator.navigateBangumiAuthorize() },
        onOpenAccountSettings = { navigator.navigateSettings(SettingsTab.PROFILE) },
    )
}
