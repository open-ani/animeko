/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import me.him188.ani.app.domain.media.DroppedFileMedia
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind

/** 自动恢复遵守换源开关与资源类型偏好，用户拖入的文件由用户自行处理。 */
internal object PlaybackRecoveryPolicy {
    fun canSwitchMedia(media: Media?, preferKind: MediaSourceKind?, enabled: Boolean = true): Boolean =
        enabled && preferKind == MediaSourceKind.WEB && (media == null || !DroppedFileMedia.isDroppedFile(media))

    /** 网络恢复只重载已选中的在线资源；尚未选源的流程由播放页单独重建。 */
    fun canRetryAfterNetworkChange(media: Media?, preferKind: MediaSourceKind?, enabled: Boolean): Boolean =
        media?.kind == MediaSourceKind.WEB && canSwitchMedia(media, preferKind, enabled)
}
