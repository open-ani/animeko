/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch.request

import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * @see me.him188.ani.datasources.api.source.MediaFetchRequest
 */
data class EditingMediaFetchRequest(
    val subjectId: String,
    val episodeId: String,
    val primaryName: String,
    val complementaryNames: List<String>,
    val episodeSort: String,
    val episodeName: String,
    val episodeEp: String,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        @Stable
        val Saver = Saver<EditingMediaFetchRequest, List<Any>>(
            save = {
                with(it) {
                    listOf(
                        subjectId,
                        episodeId,
                        primaryName,
                        complementaryNames,
                        episodeSort,
                        episodeName,
                        episodeEp,
                    )
                }
            },
            restore = {
                EditingMediaFetchRequest(
                    subjectId = it[0] as String,
                    episodeId = it[1] as String,
                    primaryName = it[2] as String,
                    complementaryNames = it[3] as List<String>,
                    episodeSort = it[4] as String,
                    episodeName = it[5] as String,
                    episodeEp = it[6] as String,
                )
            },
        )
    }
}

fun MediaFetchRequest.toEditingMediaFetchRequest(): EditingMediaFetchRequest {
    return EditingMediaFetchRequest(
        subjectId = subjectId,
        episodeId = episodeId,
        primaryName = subjectNames.getOrNull(0) ?: "",
        complementaryNames = subjectNames.drop(1),
        episodeSort = episodeSort.toString(),
        episodeName = episodeName,
        episodeEp = episodeEp?.toString().orEmpty(),
    )
}

/**
 * @param episodes 条目的全部剧集, 编辑器不展示, 沿用原请求的.
 */
fun EditingMediaFetchRequest.toMediaFetchRequestOrNull(
    episodes: List<MediaFetchRequest.Episode> = emptyList(),
): MediaFetchRequest? {
    return MediaFetchRequest(
        subjectId = subjectId.toIntOrNull()?.toString() ?: return null, // ensure valid
        episodeId = episodeId.toIntOrNull()?.toString() ?: return null,
        subjectNameCN = primaryName,
        subjectNames = listOf(primaryName) + complementaryNames,
        episodeSort = EpisodeSort(episodeSort),
        episodeName = episodeName,
        episodeEp = EpisodeSort(episodeEp),
        episodes = episodes,
    )
}

@TestOnly
val TestEditingMediaFetchRequest
    get() = EditingMediaFetchRequest(
        subjectId = "12345",
        episodeId = "67890",
        primaryName = "关于我转生变成史莱姆这档事 第三季",
        complementaryNames = listOf(
            "転生したらスライムだった件 第3期",
            "关于我转生变成史莱姆这档事 第三季",
            "Tensei Shitara Slime Datta Ken Season 3",
        ),
        episodeSort = "49",
        episodeName = "恶魔与阴谋",
        episodeEp = "01",
    )


@TestOnly
val TestMediaFetchRequest
    get() = MediaFetchRequest(
        subjectId = "12345",
        episodeId = "67890",
        subjectNameCN = "关于我转生变成史莱姆这档事 第三季",
        subjectNames = listOf(
            "転生したらスライムだった件 第3期",
            "关于我转生变成史莱姆这档事 第三季",
            "Tensei Shitara Slime Datta Ken Season 3",
        ),
        episodeSort = EpisodeSort("49"),
        episodeName = "恶魔与阴谋",
        episodeEp = EpisodeSort("01"),
    )
