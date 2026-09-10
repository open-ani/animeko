/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_bilibili
import me.him188.ani.app.ui.lang.subject_episode_danmaku_service_dandanplay
import me.him188.ani.danmaku.api.DanmakuServiceId
import org.jetbrains.compose.resources.stringResource

@Composable
fun renderDanmakuServiceId(serviceId: DanmakuServiceId): String = when (serviceId) {
    DanmakuServiceId.Animeko -> "Animeko"
    DanmakuServiceId.AcFun -> "AcFun"
    DanmakuServiceId.Baha -> "Baha"
    DanmakuServiceId.Bilibili -> stringResource(Lang.subject_episode_danmaku_service_bilibili)
    DanmakuServiceId.Dandanplay -> stringResource(Lang.subject_episode_danmaku_service_dandanplay)
    DanmakuServiceId.Tucao -> "Tucao"

    // `else` should not reach in production
    else -> serviceId.value
}
