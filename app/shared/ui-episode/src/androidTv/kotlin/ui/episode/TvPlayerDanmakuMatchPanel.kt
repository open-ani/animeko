/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.him188.ani.leanback.ui.foundation.focus.TvFocusKey
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal

private enum class MatchFocus : TvFocusKey { Search, Submit, EpisodeEntry }
private data class MatchSubjectKey(val id: String) : TvFocusKey

/** Each matching level owns its scroll position; only business requests leave this component. */
@Composable
internal fun TvDanmakuMatchPanel(
    state: TvDanmakuMatchState,
    onIntent: (TvEpisodeIntent) -> Boolean,
    entryModifier: Modifier,
) {
    val fields = rememberTvFocusScope()
    fields.Resolver()
    val subjectsList = rememberLazyListState()
    val episodesList = rememberLazyListState()
    var returnSubjectId by remember { mutableStateOf(state.selectedSubject?.id) }
    LaunchedEffect(state.selectedSubject?.id) {
        fields.requestPrepared {
            if (state.selectedSubject != null) {
                episodesList.scrollToItem(0)
                MatchFocus.EpisodeEntry
            } else {
                val index = state.subjects.indexOfFirst { it.id == returnSubjectId }
                if (index >= 0) {
                    subjectsList.scrollToItem(index + 2)
                    MatchSubjectKey(state.subjects[index].id)
                } else {
                    subjectsList.scrollToItem(0)
                    MatchFocus.Search
                }
            }
        }
    }
    LazyColumn(
        Modifier.tvFocusNavSignal(fields),
        state = if (state.selectedSubject == null) subjectsList else episodesList,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.selectedSubject == null) {
            item(key = "query") {
                TvOptionTextField(
                    state.query, "番剧名称", { onIntent(TvEpisodeIntent.DanmakuQuery(it)) },
                    entryModifier.tvFocusAnchor(fields, MatchFocus.Search)
                        .tvFocusHotkey(fields, Key.DirectionDown to MatchFocus.Submit),
                )
            }
            item(key = "search") {
                TvOptionRow(
                    if (state.loading) "正在搜索…" else "搜索",
                    enabled = !state.loading,
                    modifier = Modifier.tvFocusAnchor(fields, MatchFocus.Submit),
                    icon = Icons.Rounded.Search,
                    filled = true,
                ) {
                    returnSubjectId = null
                    onIntent(TvEpisodeIntent.SearchDanmaku)
                }
            }
            items(state.subjects, key = { "subject-${it.id}" }) { subject ->
                TvOptionRow(subject.name, modifier = Modifier.tvFocusAnchor(fields, MatchSubjectKey(subject.id))) {
                    returnSubjectId = subject.id
                    onIntent(TvEpisodeIntent.SelectDanmakuSubject(subject.id))
                }
            }
            if (state.searched && !state.loading && state.subjects.isEmpty()) item {
                Text("没有匹配结果，请修改名称后重试", color = Color.LightGray)
            }
        } else {
            item(key = "back") {
                TvOptionRow("返回番剧列表", modifier = entryModifier.tvFocusAnchor(fields, MatchFocus.EpisodeEntry)) {
                    onIntent(TvEpisodeIntent.BackDanmakuMatch)
                }
            }
            item(key = "subject") { Text(state.selectedSubject.name, color = Color.White) }
            if (state.loading) item(key = "loading") { Text("正在加载…", color = Color.LightGray) }
            items(state.episodes, key = { "episode-${it.id}" }) { episode ->
                TvOptionRow(episode.name, enabled = !state.loading) { onIntent(TvEpisodeIntent.SelectDanmakuEpisode(episode.id)) }
            }
        }
        state.error?.let { error ->
            item(key = "error") {
                Text(error, color = Color(0xFFFFC5AA))
                TvOptionRow("重新搜索") {
                    returnSubjectId = null
                    onIntent(TvEpisodeIntent.SearchDanmaku)
                }
            }
        }
    }
}
