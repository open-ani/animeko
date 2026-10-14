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
 * 客户端有专门展示方式的站内通知类型.
 *
 * 服务端以字符串下发类型, 取值会增加. 客户端不认识的类型用通用展示内容 ([NotificationDisplay]) 展示,
 * 见 [AppNotificationContent.Unknown].
 *
 * @property wireValue 服务端协议中的取值.
 */
enum class NotificationType(val wireValue: String) {
    SYSTEM("system"),
    REPORT_RESULT("report_result"),
    ACCOUNT_SECURITY("account_security"),
    ;

    companion object {
        /**
         * @return 客户端不认识时返回 `null`.
         */
        fun fromWireValue(value: String): NotificationType? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * 通知中心的分组, 对应服务端的分类 (`category`). 每个分组是通知中心的一个标签页.
 *
 * 服务端决定每种类型属于哪个分类, 客户端按分类请求列表、未读数和 "全部标为已读", 所以分类中新增的类型不需要更新客户端.
 * 服务端新增的分类在客户端增加对应的标签页之前不会被请求, 也就不会出现.
 *
 * 公告不属于任何分组: 它是全局的, 有单独的列表.
 *
 * @property wireValue 服务端协议中的分类.
 */
enum class NotificationGroup(val wireValue: String) {
    /**
     * 其他用户对我的内容做了什么, 例如回复, 提及, 回应, 关注.
     */
    INTERACTION("interaction"),

    /**
     * 系统发给用户本人的通知, 例如系统消息, 举报结果, 账号安全.
     */
    NOTICE("notice"),
    ;

    companion object {
        /**
         * 通知中心的标签页, 按显示顺序. 只有一个分组时通知中心不显示标签页.
         */
        val TABS: List<NotificationGroup> = listOf(INTERACTION, NOTICE)

        /**
         * @return 分类不对应任何分组时返回 `null`.
         */
        fun fromWireValue(value: String): NotificationGroup? = entries.firstOrNull { it.wireValue == value }
    }
}
