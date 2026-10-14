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
     * [NotificationGroup.INTERACTION] 的类型. 预留, 客户端不渲染.
     */
    COMMENT_REPLY("comment_reply"),
    MENTION("mention"),
    REACTION("reaction"),
    FOLLOW("follow"),

    /**
     * 私信. 预留, 客户端不渲染, 也不属于任何分组.
     */
    DIRECT_MESSAGE("direct_message"),
    ;

    companion object {
        /**
         * 客户端能渲染的类型. 列表、未读数和 "全部标为已读" 的请求只带这些类型 (再去掉本机关闭的类型).
         * 预留分组 ([NotificationGroup.isReserved]) 的类型不在其中.
         */
        val SUPPORTED: Set<NotificationType> = setOf(SYSTEM, REPORT_RESULT, ACCOUNT_SECURITY)

        fun fromWireValue(value: String): NotificationType? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * 通知中心的分组, 按 "谁在和我说话" 划分. 每个分组是通知中心的一个标签页, 包含一组 [NotificationType].
 *
 * 公告不属于任何分组: 它是全局的, 有单独的列表.
 */
enum class NotificationGroup(
    val types: Set<NotificationType>,
) {
    /**
     * 其他用户对我的内容做了什么: 回复, 提及, 回应, 关注. 预留, 见 [isReserved].
     */
    INTERACTION(
        setOf(
            NotificationType.COMMENT_REPLY,
            NotificationType.MENTION,
            NotificationType.REACTION,
            NotificationType.FOLLOW,
        ),
    ),

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

    /**
     * 本分组中客户端能渲染的类型 ([NotificationType.SUPPORTED]). 本分组的列表和 "全部标为已读" 只请求这些类型.
     */
    val supportedTypes: Set<NotificationType> = types.filterTo(LinkedHashSet()) { it in NotificationType.SUPPORTED }

    /**
     * 预留的分组: 显示标签页, 但客户端不能渲染其中任何类型, 所以不请求, 列表总是空的, 也没有未读数.
     */
    val isReserved: Boolean get() = supportedTypes.isEmpty()

    companion object {
        /**
         * 通知中心的标签页, 按显示顺序. 只有一个分组时通知中心不显示标签页.
         */
        val TABS: List<NotificationGroup> = listOf(INTERACTION, NOTICE)

        /**
         * [type] 所属的分组. 不属于任何分组时为 `null`.
         */
        fun of(type: NotificationType): NotificationGroup? = entries.firstOrNull { type in it.types }
    }
}
