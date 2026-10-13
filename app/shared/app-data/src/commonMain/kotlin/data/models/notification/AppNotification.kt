/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.notification

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.comment.CommentReportReason
import me.him188.ani.app.data.models.comment.CommentReportTargetType

/**
 * 一条发给当前用户的站内通知.
 *
 * 命名为 `AppNotification` 以免与各平台的系统通知类型混淆.
 *
 * @property id 服务端 ID (UUID 字符串).
 * @property createdAtMillis 创建时间, 毫秒时间戳. 列表按它倒序.
 * @property read 服务端记录的已读状态.
 */
@Immutable
data class AppNotification(
    val id: String,
    val createdAtMillis: Long,
    val read: Boolean,
    val content: AppNotificationContent,
) {
    val type: NotificationType get() = content.type
}

/**
 * 每种 [NotificationType] 各自的内容.
 */
@Immutable
sealed class AppNotificationContent {
    abstract val type: NotificationType

    /**
     * 用户举报的评论的处理结果.
     *
     * @property targetType 被举报评论的类型. 服务端以后可能增加新的类型, 客户端不认识时为 `null`.
     * @property reason 举报原因. 客户端不认识的原因为 [CommentReportReason.OTHER].
     * @property reportedAtMillis 该用户提交举报的时间.
     * @property handledAtMillis 审核处理的时间.
     * @property contentRemoved 这次处理是否删除了被举报的评论.
     * @property contentSnapshot 举报时保存的评论快照, 格式为 `昵称：正文` (全角冒号), 可能被截断.
     * @property episodeSort 剧集序号的展示文本, 不补零, 例如 `12`, `12.5`, `SP1`.
     * @property personId 人物评论所属的人物.
     * @property characterId 角色评论所属的角色.
     * @property targetName 人物或角色的名字.
     */
    @Immutable
    data class ReportResult(
        val targetType: CommentReportTargetType?,
        val reason: CommentReportReason,
        val reportedAtMillis: Long,
        val handledAtMillis: Long,
        val contentRemoved: Boolean,
        val contentSnapshot: String? = null,
        val subjectId: Long? = null,
        val subjectName: String? = null,
        val subjectNameCn: String? = null,
        val episodeId: Long? = null,
        val episodeSort: String? = null,
        val personId: Long? = null,
        val characterId: Long? = null,
        val targetName: String? = null,
    ) : AppNotificationContent() {
        override val type: NotificationType get() = NotificationType.REPORT_RESULT
    }

    /**
     * 账号安全事件.
     *
     * @property event 已知事件. 客户端不认识的事件为 [AccountSecurityEvent.UNKNOWN], 原始值见 [rawEvent].
     * @property loginMethod 登录方式, 只有 [AccountSecurityEvent.NEW_LOGIN] 有. 不认识的值为 [LoginMethod.UNKNOWN].
     * @property clientPlatform 登录设备的平台, 例如 `macos-aarch64`, `android-tv-arm64-v8a`.
     * @property deviceName 扫码登录时登录设备上报的名字.
     * @property email 打码后的邮箱, 例如 `a***@example.com`.
     * @property provider 第三方平台, 已知值为 `github` 和 `bangumi`.
     */
    @Immutable
    data class AccountSecurity(
        val event: AccountSecurityEvent,
        val rawEvent: String,
        val occurredAtMillis: Long,
        val loginMethod: LoginMethod? = null,
        val clientPlatform: String? = null,
        val clientVersion: String? = null,
        val deviceName: String? = null,
        val email: String? = null,
        val provider: String? = null,
    ) : AppNotificationContent() {
        override val type: NotificationType get() = NotificationType.ACCOUNT_SECURITY
    }

    /**
     * 管理员发给用户的系统消息.
     *
     * @property body 纯文本, 保留换行. 其中的网址由客户端自动转换为链接.
     * @property linkUrl 可选的 http(s) 链接.
     */
    @Immutable
    data class System(
        val title: String,
        val body: String,
        val linkUrl: String? = null,
    ) : AppNotificationContent() {
        override val type: NotificationType get() = NotificationType.SYSTEM
    }
}

/**
 * 账号安全事件. 服务端以字符串下发, 以后可能增加新的事件, 客户端不认识的值映射为 [UNKNOWN].
 *
 * @property wireValue 服务端协议中的取值. [UNKNOWN] 没有对应的取值.
 */
enum class AccountSecurityEvent(val wireValue: String?) {
    NEW_LOGIN("new_login"),
    EMAIL_BOUND("email_bound"),
    EMAIL_CHANGED("email_changed"),
    EMAIL_UNBOUND("email_unbound"),

    /**
     * 用户的第三方账号 (见 [AppNotificationContent.AccountSecurity.provider]) 登录了另一个 Animeko 账号, 因此从当前账号解绑.
     */
    THIRD_PARTY_UNBOUND("third_party_unbound"),

    /**
     * Bangumi 拒绝刷新授权, 需要用户重新授权.
     */
    BANGUMI_AUTHORIZATION_EXPIRED("bangumi_authorization_expired"),
    UNKNOWN(null),
    ;

    companion object {
        fun fromWireValue(value: String): AccountSecurityEvent =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * 登录方式. 服务端以字符串下发, 客户端不认识的值映射为 [UNKNOWN].
 *
 * @property wireValue 服务端协议中的取值. [UNKNOWN] 没有对应的取值.
 */
enum class LoginMethod(val wireValue: String?) {
    EMAIL_OTP("email_otp"),
    GITHUB("github"),
    BANGUMI("bangumi"),
    QR_CODE("qr_code"),
    UNKNOWN(null),
    ;

    companion object {
        /**
         * @return [value] 为 `null` 时返回 `null`.
         */
        fun fromWireValue(value: String?): LoginMethod? {
            if (value == null) return null
            return entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
        }
    }
}
