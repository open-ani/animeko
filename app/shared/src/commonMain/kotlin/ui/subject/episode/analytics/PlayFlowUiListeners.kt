/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.analytics

import me.him188.ani.app.domain.media.selector.SelectTrigger
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.player.analytics.PlayFlowEpisode
import me.him188.ani.app.domain.player.analytics.PlayFlowRecorder
import me.him188.ani.app.domain.player.analytics.PlayFlowStep
import me.him188.ani.app.ui.mediafetch.MediaSelectorStateListener
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseListener
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseOutcome
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowsePlayResult
import me.him188.ani.datasources.api.Media
import kotlin.time.Duration

/**
 * 用户在播放页为推进播放做的操作, 记为 `play_flow user_action` 的 `action`.
 */
enum class PlayFlowUserAction(val value: String) {
    /** 刷新单个数据源. */
    REFRESH_SOURCE("refresh_source"),

    /** 重新查询全部数据源. */
    REFRESH_ALL("refresh_all"),

    /** 编辑查询条件. */
    EDIT_QUERY("edit_query"),

    /** 重试加载剧集信息. */
    RETRY_INFO("retry_info"),

    /** 重新加载当前集 (TV). */
    RETRY_PLAYBACK("retry_playback"),

    /** 处理数据源的验证码. */
    SOLVE_CAPTCHA("solve_captcha"),
}

/**
 * 记录一次 `user_action`.
 */
fun PlayFlowRecorder.recordUserAction(
    episode: PlayFlowEpisode?,
    action: PlayFlowUserAction,
    mediaSourceId: String? = null,
    outcome: String? = null,
) {
    record(PlayFlowStep.USER_ACTION, episode) {
        put("action", action.value)
        put("media_source_id", mediaSourceId)
        put("outcome", outcome)
    }
}

fun SolveOutcome.toPlayFlowOutcome(): String = when (this) {
    SolveOutcome.Solved -> "solved"
    is SolveOutcome.Failed -> "failed"
    SolveOutcome.Cancelled -> "cancelled"
    SolveOutcome.Unsupported -> "unsupported"
}

/**
 * 把选择器里的用户操作记入播放流程埋点. 点选本身由 `onSelect` 事件记录 (`user_select`), 这里只记验证码处理.
 *
 * @param currentEpisode 操作发生时的剧集尝试.
 * @param onUserSelect 宿主在用户点选后的额外处理 (播放页: 结束浏览记忆).
 */
class PlayFlowMediaSelectorListener(
    private val recorder: PlayFlowRecorder,
    private val currentEpisode: () -> PlayFlowEpisode?,
    private val onUserSelect: suspend (Media, SelectTrigger) -> Unit = { _, _ -> },
) : MediaSelectorStateListener {
    override suspend fun onUserSelect(media: Media, trigger: SelectTrigger) {
        onUserSelect.invoke(media, trigger)
    }

    override fun onCaptchaResolved(instanceId: String, mediaSourceId: String, outcome: SolveOutcome) {
        recorder.recordUserAction(
            currentEpisode(), PlayFlowUserAction.SOLVE_CAPTCHA, mediaSourceId, outcome.toPlayFlowOutcome(),
        )
    }
}

/**
 * 把手动查找的每一步记为 `play_flow manual_search`.
 */
class PlayFlowManualBrowseListener(
    private val recorder: PlayFlowRecorder,
    private val currentEpisode: () -> PlayFlowEpisode?,
) : ManualBrowseListener {
    override fun onSearchFinished(mediaSourceId: String, outcome: ManualBrowseOutcome, resultCount: Int, elapsed: Duration) {
        record("search", mediaSourceId, outcome.toPlayFlowOutcome(), resultCount, elapsed = elapsed)
    }

    override fun onSubjectOpened(mediaSourceId: String, outcome: ManualBrowseOutcome, channelCount: Int, elapsed: Duration) {
        record("open_subject", mediaSourceId, outcome.toPlayFlowOutcome(), channelCount, elapsed = elapsed)
    }

    override fun onChannelSelected(mediaSourceId: String, episodeCount: Int) {
        record("open_channel", mediaSourceId, "ok", episodeCount)
    }

    override fun onPlay(mediaSourceId: String, remember: Boolean, result: ManualBrowsePlayResult) {
        record(
            "play", mediaSourceId,
            when (result) {
                ManualBrowsePlayResult.PLAYED -> "ok"
                ManualBrowsePlayResult.INVALID_STATE -> "invalid_state"
                ManualBrowsePlayResult.CREATE_MEDIA_FAILED -> "create_media_failed"
                ManualBrowsePlayResult.ON_PLAY_FAILED -> "failed"
            },
            count = null,
            remember = remember,
        )
    }

    private fun record(
        stage: String,
        mediaSourceId: String,
        outcome: String,
        count: Int?,
        remember: Boolean? = null,
        elapsed: Duration? = null,
    ) {
        recorder.record(PlayFlowStep.MANUAL_SEARCH, currentEpisode()) {
            put("stage", stage)
            put("outcome", outcome)
            put("media_source_id", mediaSourceId)
            put("count", count)
            put("remember", remember)
            put("duration_ms", elapsed)
        }
    }

    private fun ManualBrowseOutcome.toPlayFlowOutcome(): String = when (this) {
        ManualBrowseOutcome.SUCCESS -> "ok"
        ManualBrowseOutcome.EMPTY -> "empty"
        ManualBrowseOutcome.FAILED -> "failed"
        ManualBrowseOutcome.CAPTCHA_SOLVED_THEN_SUCCESS -> "captcha_solved"
        ManualBrowseOutcome.CAPTCHA_SOLVED_THEN_FAILED -> "captcha_solved_failed"
        ManualBrowseOutcome.CAPTCHA_FAILED -> "captcha_failed"
        ManualBrowseOutcome.CAPTCHA_UNSUPPORTED -> "captcha_unsupported"
    }
}
