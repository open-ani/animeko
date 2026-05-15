/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.TestSubjectProgressInfos
import me.him188.ani.app.data.models.subject.isOnAir
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.platform.annotations.TestOnly

// Test: AiringProgressTests
@Stable
class AiringLabelState(
    airingInfoState: State<SubjectAiringInfo?>, // null means loading
    progressInfoState: State<SubjectProgressInfo?>, // null means loading
) {
    private val airingInfo by airingInfoState
    private val progressInfo by progressInfoState

    val isLoading by derivedStateOf {
        airingInfo == null || progressInfo == null
    }

    /**
     * 显示当前看到的剧集, 或者最新连载到的剧集.
     */
    fun progressText(strings: SubjectStatusStrings): String? {
        // Hi, 如果你修改这里, 务必在 AiringLabelStateTest 增加测试

        val airingInfo = airingInfo
        val progressInfo = progressInfo
        if (airingInfo == null || progressInfo == null) {
            return null
        }
        return when (airingInfo.kind) {
            SubjectAiringKind.UPCOMING -> {
                strings.upcoming
//                if (airingInfo.airDate.isInvalid) {
//                    "未开播"
//                } else {
//                    airingInfo.airDate.toStringExcludingSameYear() + " 开播"
//                }
            }

            SubjectAiringKind.ON_AIR -> {
                when (val s = progressInfo.continueWatchingStatus) {
                    ContinueWatchingStatus.Done -> strings.done
                    is ContinueWatchingStatus.Watched -> strings.watched(renderEpAndSort(s.episodeEp, s.episodeSort))

                    is ContinueWatchingStatus.Continue,
                    is ContinueWatchingStatus.NotOnAir,
                    is ContinueWatchingStatus.Start,
                        ->
                        if (airingInfo.latestSort == null) {
                            strings.onAir
                        } else {
                            strings.onAirTo(renderEpAndSort(airingInfo.latestEp, airingInfo.latestSort))
                        }
                }
            }

            SubjectAiringKind.COMPLETED -> {
                when (val s = progressInfo.continueWatchingStatus) {
                    ContinueWatchingStatus.Done -> strings.done

                    is ContinueWatchingStatus.Watched -> strings.watched(renderEpAndSort(s.episodeEp, s.episodeSort))
                    is ContinueWatchingStatus.Continue ->
                        strings.watched(renderEpAndSort(s.watchedEpisodeEp, s.watchedEpisodeSort))

                    is ContinueWatchingStatus.NotOnAir,
                    ContinueWatchingStatus.Start,
                        -> strings.completed
                }
            }
        }
    }

    val highlightProgress by derivedStateOf {
        val continueWatchingStatus = progressInfo?.continueWatchingStatus
        airingInfo?.isOnAir == true
                &&
                when (continueWatchingStatus) {
                    is ContinueWatchingStatus.Continue -> true

                    ContinueWatchingStatus.Start,
                    ContinueWatchingStatus.Done,
                    is ContinueWatchingStatus.NotOnAir,
                    is ContinueWatchingStatus.Watched,
                    null,
                        -> false
                }
    }

    /**
     * "全 xx 话"
     */
    fun totalEpisodesText(strings: SubjectStatusStrings): String? {
        val airingInfo = airingInfo ?: return null
        return renderTotalEpisodeText(airingInfo, strings)
    }
}

/**
 * ```
 * 已完结 · 全 28 话
 * ```
 *
 * ```
 * 连载至第 28 话 · 全 34 话
 * ```
 *
 * @sample
 */
@Composable
fun AiringLabel(
    state: AiringLabelState,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    progressColor: Color = if (state.highlightProgress) MaterialTheme.colorScheme.primary else Color.Unspecified,
) {
    val strings = rememberSubjectStatusStrings()
    ProvideTextStyle(style) {
        FlowRow(
            modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            state.progressText(strings)?.let {
                Text(
                    it,
                    color = progressColor,
                    softWrap = false,
                )
            }
            state.totalEpisodesText(strings)?.let {
                Text(" · ", softWrap = false)
                Text(it, softWrap = false)
            }
        }
    }
}

fun renderEpAndSort(ep: EpisodeSort?, sort: EpisodeSort?) = when {
    ep == null -> sort.toString()
    sort == null -> ep.toString()
    ep.number == 0f -> sort.toString() // 有些 SP 只有 sort, ep 为 0. #1677
    ep != sort -> "$ep ($sort)"
    else -> sort.toString()
}


@TestOnly
fun createTestAiringLabelState(
    airingInfo: SubjectAiringInfo = TestSubjectAiringInfo,
    progressInfo: SubjectProgressInfo = TestSubjectProgressInfos.ContinueWatching2,
): AiringLabelState = AiringLabelState(stateOf(airingInfo), stateOf(progressInfo))


@TestOnly
val TestSubjectAiringInfo get() = SubjectAiringInfo.EmptyCompleted
