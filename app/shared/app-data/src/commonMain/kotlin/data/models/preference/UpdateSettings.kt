/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import kotlinx.serialization.Transient
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isIos

@Immutable
@Serializable
data class UpdateSettings(
    val autoCheckUpdate: Boolean = true,
    val releaseClass: ReleaseClass = ReleaseClass.STABLE,
    val autoDownloadUpdate: Boolean = false,
    /**
     * 是否在应用内下载更新
     */
    val inAppDownload: Boolean = !currentPlatform().isIos(),
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0,
) {
    // 有关默认的更新策略:
    // 3.0.0-rc05 时期发起过一个投票, 10 人参与
    // https://uk.surveymonkey.com/analyze/J384Ec5GDMJDT1a9quO1xHsDQuCCh1R4qNPOY1VPegE_3D?tab_clicked=1
    // - 当没有自动更新时, 5 人选择 alpha, 4 人选择 stable, 1 人选择 beta
    // - 当有自动更新时, 4 人选择 stable, 3 人选择 alpha, 3 人选择 beta
    // - 9/10 人愿意开启自动更新
    //
    // 发现大家对于希望收到更新提示和自动下载更新的版本类型是有区别的, 
    // 多数人希望收到测试版的提示, 但是更希望自动更新更新到更加稳定的版本. 
    // 但根据 tg 群内讨论, 为了简化设置, 仍然只采用一个版本类型设置项.
    // 因此我们默认自动更新 (下载) 到稳定版本.
    //
    // 由此也可以知道, 大家愿意**自动**更新到稳定版是因为它经过了 alpha/beta 测试. 因此我们不能删除 alpha/beta 流程.

    companion object {
        @Stable
        val Default = UpdateSettings()
    }
}