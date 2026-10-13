/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

/**
 * 服务端的站内通知类型.
 *
 * 客户端只渲染 [SUPPORTED] 中的类型. 列表和未读数接口总是带上这些类型, 服务端只返回和统计它们,
 * 所以服务端以后新增的类型不会出现在旧版本客户端里.
 *
 * @property wireValue 服务端协议中的取值.
 */
enum class NotificationType(val wireValue: String) {
    SYSTEM("system"),
    REPORT_RESULT("report_result"),
    ACCOUNT_SECURITY("account_security"),

    /**
     * 互动分组, 预留.
     */
    COMMENT_REPLY("comment_reply"),
    MENTION("mention"),
    REACTION("reaction"),
    FOLLOW("follow"),

    /**
     * 私信分组, 预留.
     */
    DIRECT_MESSAGE("direct_message"),
    ;

    companion object {
        /**
         * 客户端能渲染的类型, 即 [NotificationGroup.SUPPORTED] 中所有分组的类型.
         *
         * 用 lazy 是因为 [NotificationGroup] 的枚举值引用了本类的枚举值, 两个类的静态初始化不能互相依赖.
         */
        val SUPPORTED: Set<NotificationType> by lazy {
            NotificationGroup.SUPPORTED.flatMapTo(LinkedHashSet()) { it.types }
        }

        fun fromWireValue(value: String): NotificationType? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * 通知中心的分组, 按 "谁在和我说话" 划分. 每个分组渲染一组 [NotificationType].
 *
 * 公告不属于任何分组: 它是全局的, 有单独的列表.
 */
enum class NotificationGroup(
    val types: Set<NotificationType>,
) {
    /**
     * 系统发给用户本人的通知: 系统消息, 举报结果, 账号安全.
     */
    NOTICE(
        setOf(
            NotificationType.SYSTEM,
            NotificationType.REPORT_RESULT,
            NotificationType.ACCOUNT_SECURITY,
        ),
    ),
    ;

    companion object {
        /**
         * 客户端展示的分组, 按标签页顺序. 只有一个分组时通知中心不显示标签页.
         */
        val SUPPORTED: List<NotificationGroup> = listOf(NOTICE)
    }
}
