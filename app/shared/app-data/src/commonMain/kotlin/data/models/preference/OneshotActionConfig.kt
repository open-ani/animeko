/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

/**
 * 用于存储一次性提示的开关状态
 */
@Serializable
@Immutable
data class OneshotActionConfig(
    /**
     * 在搜索标签页显示长按删除标签的 Tip
     */
    val deleteSearchTagTip: Boolean = true,
    val horizontalScrollTip: Boolean = true,

    /**
     * Migrate media cache metadata storage from file to datastore.
     *
     * @since 4.8
     */
    val metadataMigratedFor408: Boolean = false,

    /**
     * Migrate all stored path in media cache metadata datastore and m3u downloader datastore
     * to relative path.
     *
     * @since 4.11
     */
    val metadataMigratedFor411: Boolean = false,

    /**
     * 用户更新到 5.0 版本之后需要重新登录
     *
     * @since 5.0
     */
    val needReLoginAfter500: Boolean = true,
) {
    companion object {
        @Stable
        val Default = OneshotActionConfig()
    }
}