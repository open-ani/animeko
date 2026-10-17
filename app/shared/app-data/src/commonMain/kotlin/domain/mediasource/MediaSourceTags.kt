/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

/**
 * 订阅可以给数据源打的标签, 见 [me.him188.ani.datasources.api.source.MediaSourceConfig.tags].
 *
 * 标签写在订阅文件里, 由第三方维护, 已发布的值不能改名.
 */
object MediaSourceTags {
    /**
     * 这个数据源的 HLS 播放列表可能插有广告.
     * 广告过滤为 [me.him188.ani.app.data.models.preference.HlsAdFilteringMode.SMART] 时只对有这个标签的数据源生效.
     */
    const val HLS_ADS = "hls-ads"
}
