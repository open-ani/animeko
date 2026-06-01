/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.playback_session_cause_manual_selection
import me.him188.ani.app.ui.lang.playback_session_cause_no_media
import me.him188.ani.app.ui.lang.playback_session_cause_player_error
import me.him188.ani.app.ui.lang.playback_session_problem_toast
import me.him188.ani.app.ui.lang.playback_session_ready_toast
import me.him188.ani.app.ui.subject.episode.video.loading.VideoLoadingCauseLabels
import me.him188.ani.app.ui.subject.episode.video.loading.renderCause
import me.him188.ani.app.ui.subject.episode.video.loading.videoLoadingCauseLabels
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * [RetainedPlaybackNotice] 的文案.
 *
 * 提示是在协程里发的 (holder 的 [RetainedPlaybackSessionHolder.notices]), 而 `stringResource`
 * 只能在组合里调用, 所以在组合期间就把每种提示的成品文案取好, 发的时候只查表.
 *
 * 失败原因用的就是播放画面上那套 ([VideoLoadingCauseLabels] / [renderCause]): 同一件事在画面上和
 * 提示里说法一致, 也不必在两处各维护一份 `when`.
 */
@Immutable
internal class RetainedPlaybackNoticeTexts(
    private val ready: String,
    /** 每种解析失败的原因, 已经套进"后台播放遇到问题: ……"的模板. */
    private val causes: VideoLoadingCauseLabels,
    private val playerError: String,
    private val noMedia: String,
    private val manualSelection: String,
) {
    fun textOf(notice: RetainedPlaybackNotice): String = when (notice) {
        RetainedPlaybackNotice.Ready -> ready
        RetainedPlaybackNotice.PlayerError -> playerError
        RetainedPlaybackNotice.NoMediaFound -> noMedia
        RetainedPlaybackNotice.NeedsManualSelection -> manualSelection
        // Cancelled 是换源的中间态, holder 不当成问题, 所以那一句在这里用不到
        is RetainedPlaybackNotice.LoadFailed -> renderCause(notice.cause, causes)
    }
}

@Composable
internal fun rememberRetainedPlaybackNoticeTexts(): RetainedPlaybackNoticeTexts {
    val ready = stringResource(Lang.playback_session_ready_toast)
    val problemTemplate = stringResource(Lang.playback_session_problem_toast, CAUSE_PLACEHOLDER)
    val causes = videoLoadingCauseLabels().map { problemTemplate.replace(CAUSE_PLACEHOLDER, it) }
    val playerError = problemText(Lang.playback_session_cause_player_error)
    val noMedia = problemText(Lang.playback_session_cause_no_media)
    val manualSelection = problemText(Lang.playback_session_cause_manual_selection)
    return remember(ready, causes, playerError, noMedia, manualSelection) {
        RetainedPlaybackNoticeTexts(
            ready = ready,
            causes = causes,
            playerError = playerError,
            noMedia = noMedia,
            manualSelection = manualSelection,
        )
    }
}

/**
 * 取模板时先塞这个占位, 再换成真的原因.
 *
 * 原因这一批是 [videoLoadingCauseLabels] 给的**成品字符串**, 没有 [StringResource] 可以直接传给
 * `stringResource(模板, 原因)`, 所以先取一次带占位的模板再替换. 占位挑一段任何译文里都不会出现的
 * 字面量 —— 拿空格之类的当占位会替换到模板自己的文字里去.
 */
private const val CAUSE_PLACEHOLDER = "%%ANI_CAUSE%%"

/** 把一句原因套进"后台播放遇到问题: ……"的模板里. */
@Composable
private fun problemText(cause: StringResource): String =
    stringResource(Lang.playback_session_problem_toast, stringResource(cause))
