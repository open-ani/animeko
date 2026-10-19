/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import me.him188.ani.app.data.models.notification.NotificationGroup

object NotificationTestTags {
    const val CENTER_LIST_PREFIX = "notification-center-list-"
    const val TAB_ROW = "notification-tab-row"
    const val TAB_PREFIX = "notification-tab-"
    const val TAB_BADGE = "notification-tab-badge"
    const val ITEM_PREFIX = "notification-item-"
    const val UNREAD_DOT = "notification-unread-dot"
    const val ROW_OVERLINE = "notification-row-overline"
    const val LEADING_IMAGE = "notification-leading-image"
    const val INLINE_ACTION = "notification-inline-action"
    const val MARK_ALL_READ = "notification-mark-all-read"
    const val SETTINGS = "notification-settings"
    const val ANNOUNCEMENT_ENTRY = "notification-announcement-entry"
    const val ANNOUNCEMENT_ENTRY_DOT = "notification-announcement-entry-dot"
    const val EMPTY = "notification-empty"

    /**
     * [NotificationEmptyState] 中的圆形图标. 所有空状态共用, 用祖先节点的标签区分.
     */
    const val EMPTY_STATE_ICON = "notification-empty-state-icon"

    const val UPDATE_BANNER = "notification-update-banner"
    const val UPDATE_BANNER_ACTION = "notification-update-banner-action"
    const val DETAIL = "notification-detail"

    /**
     * 宽屏双栏中详情栏顶栏下方的内容区域. 通知中心和公告列表共用.
     */
    const val DETAIL_PANE = "notification-detail-pane"

    const val DETAIL_PLACEHOLDER = "notification-detail-placeholder"
    const val DETAIL_NOT_FOUND = "notification-detail-not-found"
    const val DETAIL_ACTION = "notification-detail-action"
    const val DETAIL_TOP_BAR_ACTION = "notification-detail-top-bar-action"
    const val DETAIL_LINK = "notification-detail-link"
    const val DETAIL_OVERLINE = "notification-detail-overline"
    const val DETAIL_UPDATE_HINT = "notification-detail-update-hint"
    const val DETAIL_CHECK_UPDATE = "notification-detail-check-update"

    const val REPORT_REASON = "notification-report-reason"
    const val REPORT_TARGET = "notification-report-target"
    const val REPORT_OUTCOME = "notification-report-outcome"
    const val REPORT_SNAPSHOT_AUTHOR = "notification-report-snapshot-author"
    const val REPORT_SNAPSHOT_TIME = "notification-report-snapshot-time"
    const val REPORT_SNAPSHOT_TEXT = "notification-report-snapshot-text"

    fun item(id: String) = ITEM_PREFIX + id

    /**
     * [group] 的标签页中的列表.
     */
    fun centerList(group: NotificationGroup) = CENTER_LIST_PREFIX + group.name.lowercase()

    fun tab(group: NotificationGroup) = TAB_PREFIX + group.name.lowercase()
}

object AnnouncementTestTags {
    const val LIST = "announcement-list"
    const val ITEM_PREFIX = "announcement-item-"
    const val PIN = "announcement-pin"
    const val TAG = "announcement-tag"
    const val UNREAD_DOT = "announcement-unread-dot"
    const val EMPTY = "announcement-empty"
    const val DETAIL = "announcement-detail"
    const val DETAIL_PLACEHOLDER = "announcement-detail-placeholder"
    const val DETAIL_NOT_FOUND = "announcement-detail-not-found"
    const val DETAIL_LINK = "announcement-detail-link"

    fun item(id: String) = ITEM_PREFIX + id
}
