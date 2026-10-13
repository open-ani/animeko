/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.install

import kotlinx.serialization.Serializable

/**
 * 本机安装的状态, 由 [InstallReporter] 维护.
 *
 * 单独存储而不放进设置, 因为设置会随备份恢复到其他设备, 而这些信息只属于这一次安装.
 */
@Serializable
data class InstallInfo(
    /**
     * 本机安装的随机 UUID, `null` 表示还没有生成. 见 [InstallIdRepository].
     */
    val installId: String? = null,
    /**
     * 本机第一次启动的时间, `null` 表示还没有初始化.
     *
     * 早于此字段的安装无法知道真实值, 由 [InstallReporter] 根据本机最早的痕迹估计. 估计值可能比真实值晚, 但不会更早.
     */
    val firstLaunchAtMillis: Long? = null,
    /**
     * 本机是否登录过任何账号.
     */
    val hasLoggedIn: Boolean = false,
    /**
     * 已经生成但还没有上报成功的报告.
     */
    val pendingReport: PendingInstallReport? = null,
    /**
     * 已经上报过 (包括被服务器拒绝) 的用户. 这些用户再次登录不会生成新的报告.
     */
    val reportedUserIds: Set<String> = emptySet(),
) {
    companion object {
        val Initial = InstallInfo()
    }
}

/**
 * 登录时生成的报告. 字段含义见服务器的 `PUT /users/me/install-report`.
 */
@Serializable
data class PendingInstallReport(
    /**
     * 登录的用户. 只有当前登录的还是这个用户时才发送.
     */
    val userId: String,
    val firstLaunchAtMillis: Long,
    /**
     * 登录时本机的播放记录数, 包含已删除的记录.
     */
    val localEpisodesBeforeLogin: Int,
    /**
     * 这次登录之前本机是否登录过. 为 `true` 时 [localEpisodesBeforeLogin] 可能包含别的账号同步下来的记录.
     */
    val hadPreviousLogin: Boolean,
)
