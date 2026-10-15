/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.manual

import kotlin.time.Duration

/**
 * 手动查找中一次请求 (搜索 / 打开条目) 的结果分类.
 */
enum class ManualBrowseOutcome {
    /** 成功且有结果. */
    SUCCESS,

    /** 成功但结果为空. */
    EMPTY,

    /** 请求失败. */
    FAILED,

    /** 被验证码拦住, 用户解决后重试成功. */
    CAPTCHA_SOLVED_THEN_SUCCESS,

    /** 被验证码拦住, 用户解决后重试仍失败. */
    CAPTCHA_SOLVED_THEN_FAILED,

    /** 被验证码拦住, 用户没有解决 (失败或取消). */
    CAPTCHA_FAILED,

    /** 被验证码拦住, 当前平台不支持交互式验证. */
    CAPTCHA_UNSUPPORTED,
}

/**
 * [ManualBrowseState.play] 的结果分类.
 */
enum class ManualBrowsePlayResult {
    /** 已交给宿主播放. */
    PLAYED,

    /** 当前状态不完整 (没有选中源、条目、线路或剧集), 无法播放. */
    INVALID_STATE,

    /** 数据源构造资源失败. */
    CREATE_MEDIA_FAILED,

    /** 宿主播放失败. */
    ON_PLAY_FAILED,
}

/**
 * 宿主观察手动查找的进展, 供埋点使用. 不包含关键字、条目标题与 URL.
 */
interface ManualBrowseListener {
    /**
     * 一次搜索结束. [resultCount] 为搜到的条目数.
     */
    fun onSearchFinished(mediaSourceId: String, outcome: ManualBrowseOutcome, resultCount: Int, elapsed: Duration) {}

    /**
     * 打开条目结束. [channelCount] 为线路数.
     */
    fun onSubjectOpened(mediaSourceId: String, outcome: ManualBrowseOutcome, channelCount: Int, elapsed: Duration) {}

    /**
     * 用户切换到某条线路.
     */
    fun onChannelSelected(mediaSourceId: String, episodeCount: Int) {}

    /**
     * 用户点击剧集播放的结果.
     */
    fun onPlay(mediaSourceId: String, remember: Boolean, result: ManualBrowsePlayResult) {}

    companion object Noop : ManualBrowseListener
}
