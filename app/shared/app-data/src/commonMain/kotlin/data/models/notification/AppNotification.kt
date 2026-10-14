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
 * 客户端认识的类型 ([type] 不为 `null`) 有各自的内容 ([content]), 用客户端自己的文案展示.
 * 每条通知还带有服务端填写的通用展示内容 ([display]), 用于展示客户端不认识的类型 ([AppNotificationContent.Unknown]),
 * 以及认识的类型中客户端不认识的事件或对象.
 *
 * @property id 服务端 ID (UUID 字符串).
 * @property rawType 服务端协议中的类型. 取值会增加.
 * @property rawCategory 服务端为这个类型分配的分类, 对应通知中心的一个分组 ([NotificationGroup]).
 * @property createdAtMillis 创建时间, 毫秒时间戳. 列表按它倒序.
 * @property read 服务端记录的已读状态.
 */
@Immutable
data class AppNotification(
    val id: String,
    val rawType: String,
    val rawCategory: String,
    val createdAtMillis: Long,
    val read: Boolean,
    val display: NotificationDisplay,
    val content: AppNotificationContent,
) {
    /**
     * 客户端有专门展示方式的类型. 不认识的类型为 `null`, 此时 [content] 为 [AppNotificationContent.Unknown],
     * 当前版本可能无法完整展示这条通知.
     */
    val type: NotificationType? get() = NotificationType.fromWireValue(rawType)

    /**
     * 所属的分组. 分类不对应任何分组时为 `null`.
     */
    val group: NotificationGroup? get() = NotificationGroup.fromWireValue(rawCategory)
}

/**
 * 服务端为每条通知填写的通用展示内容. 文本是服务端生成的中文.
 *
 * 客户端不认识的类型完全用它展示; 认识的类型可以用客户端自己的本地化文案代替.
 *
 * @property title 列表行的主文本.
 * @property body 列表行的辅助文本, 也是详情的正文. 纯文本, 保留换行.
 * @property overline 主文本上方的一行, 例如条目与剧集.
 * @property icon 图标. 没有或客户端不认识时为 `null`, 使用默认图标.
 * @property imageUrl 图片, 例如对方的头像. 有值时代替 [icon] 显示.
 * @property target 点击后前往的位置, 见 [NotificationTarget.destination].
 */
@Immutable
data class NotificationDisplay(
    val title: String,
    val body: String? = null,
    val overline: String? = null,
    val icon: NotificationIcon? = null,
    val imageUrl: String? = null,
    val target: NotificationTarget? = null,
)

/**
 * 通用展示内容的图标. 服务端以字符串下发, 以后可能增加新的取值, 客户端不认识的值为 `null`.
 *
 * @property wireValue 服务端协议中的取值.
 */
enum class NotificationIcon(val wireValue: String) {
    REPORT("report"),
    SECURITY("security"),
    INFO("info"),
    REPLY("reply"),
    MENTION("mention"),
    REACTION("reaction"),
    FOLLOW("follow"),
    ;

    companion object {
        /**
         * @return [value] 为 `null` 或客户端不认识时返回 `null`.
         */
        fun fromWireValue(value: String?): NotificationIcon? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * 通知指向的位置. 各字段都可能为空, 以后可能增加新的字段.
 *
 * @property subjectId 条目. 有 [episodeId] 时是剧集所在的条目.
 * @property episodeId 剧集, 与 [subjectId] 一起使用.
 * @property url 网页链接, 在浏览器中打开.
 */
@Immutable
data class NotificationTarget(
    val subjectId: Long? = null,
    val episodeId: Long? = null,
    val personId: Long? = null,
    val characterId: Long? = null,
    val url: String? = null,
) {
    /**
     * 客户端支持的最具体的位置: 剧集 (需要 [episodeId] 和 [subjectId]) > 条目 > 人物 > 角色 > 网页.
     * 只接受 http(s) 链接. 都没有时为 `null`.
     */
    val destination: NotificationDestination?
        get() {
            if (subjectId != null && episodeId != null) return NotificationDestination.Episode(subjectId, episodeId)
            if (subjectId != null) return NotificationDestination.Subject(subjectId)
            if (personId != null) return NotificationDestination.Person(personId)
            if (characterId != null) return NotificationDestination.Character(characterId)
            val url = url?.trim()?.takeIf { HttpUrlPattern.matches(it) } ?: return null
            return NotificationDestination.Url(url)
        }

    private companion object {
        val HttpUrlPattern = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    }
}

/**
 * 点击通知后前往的位置, 由 [NotificationTarget.destination] 选出.
 */
@Immutable
sealed interface NotificationDestination {
    @Immutable
    data class Episode(val subjectId: Long, val episodeId: Long) : NotificationDestination

    @Immutable
    data class Subject(val subjectId: Long) : NotificationDestination

    @Immutable
    data class Person(val personId: Long) : NotificationDestination

    @Immutable
    data class Character(val characterId: Long) : NotificationDestination

    /**
     * 在浏览器中打开的 http(s) 链接.
     */
    @Immutable
    data class Url(val url: String) : NotificationDestination
}

/**
 * 客户端认识的类型 ([NotificationType]) 各自的内容, 或 [Unknown].
 */
@Immutable
sealed class AppNotificationContent {
    /**
     * 用户举报的评论的处理结果.
     *
     * @property targetType 被举报评论的类型. 服务端以后可能增加新的类型, 客户端不认识时为 `null`,
     * 此时被举报的对象和跳转的位置使用通用展示内容 ([AppNotification.display]).
     * @property reason 举报原因. 客户端不认识的原因为 [CommentReportReason.OTHER].
     * @property reportedAtMillis 该用户提交举报的时间.
     * @property handledAtMillis 审核处理的时间.
     * @property contentRemoved 这次处理是否删除了被举报的评论.
     * @property contentSnapshot 举报时保存的评论快照, 格式为 `昵称：正文` (全角冒号), 可能被截断.
     * @property contentCreatedAtMillis 被举报评论的发表时间. 没有时为 `null`.
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
        val contentCreatedAtMillis: Long? = null,
        val subjectId: Long? = null,
        val subjectName: String? = null,
        val subjectNameCn: String? = null,
        val episodeId: Long? = null,
        val episodeSort: String? = null,
        val personId: Long? = null,
        val characterId: Long? = null,
        val targetName: String? = null,
    ) : AppNotificationContent()

    /**
     * 账号安全事件.
     *
     * @property event 已知事件. 客户端不认识的事件为 [AccountSecurityEvent.UNKNOWN], 原始值见 [rawEvent].
     * 不认识的事件使用通用展示内容 ([AppNotification.display]).
     * @property provider 第三方平台, 已知值为 `github` 和 `bangumi`.
     */
    @Immutable
    data class AccountSecurity(
        val event: AccountSecurityEvent,
        val rawEvent: String,
        val occurredAtMillis: Long,
        val provider: String? = null,
    ) : AppNotificationContent()

    /**
     * 系统消息: 管理员发给用户的消息, 或服务端发出的提醒.
     *
     * 客户端认识 [event] 时使用自己的文案和操作. 没有事件或不认识的事件展示通用展示内容 ([AppNotification.display]):
     * 标题, 正文 (其中的网址由客户端自动转换为链接), 以及前往 [NotificationTarget.destination] 的可选按钮.
     *
     * @property event 已知事件. 没有事件或客户端不认识时为 `null`.
     */
    @Immutable
    data class System(
        val event: SystemNotificationEvent? = null,
    ) : AppNotificationContent()

    /**
     * 客户端没有专门展示方式的通知, 完全用通用展示内容 ([AppNotification.display]) 展示.
     *
     * 包括客户端不认识的类型 ([AppNotification.type] 为 `null`), 以及缺少该类型内容的通知.
     */
    @Immutable
    data object Unknown : AppNotificationContent()
}

/**
 * 账号安全事件. 服务端以字符串下发, 以后可能增加新的事件, 客户端不认识的值映射为 [UNKNOWN].
 *
 * @property wireValue 服务端协议中的取值. [UNKNOWN] 没有对应的取值.
 */
enum class AccountSecurityEvent(val wireValue: String?) {
    /**
     * 用户的第三方账号 (见 [AppNotificationContent.AccountSecurity.provider]) 登录了另一个 Animeko 账号, 因此从当前账号解绑.
     */
    THIRD_PARTY_UNBOUND("third_party_unbound"),
    UNKNOWN(null),
    ;

    companion object {
        fun fromWireValue(value: String): AccountSecurityEvent =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * 客户端有专门展示方式的系统消息事件. 服务端以字符串下发, 以后可能增加新的事件.
 *
 * @property wireValue 服务端协议中的取值.
 */
enum class SystemNotificationEvent(val wireValue: String) {
    /**
     * Bangumi 授权已过期, 需要用户重新授权.
     */
    BANGUMI_AUTHORIZATION_EXPIRED("bangumi_authorization_expired"),
    ;

    companion object {
        /**
         * @return [value] 为 `null` 或客户端不认识时返回 `null`.
         */
        fun fromWireValue(value: String?): SystemNotificationEvent? =
            entries.firstOrNull { it.wireValue == value }
    }
}
