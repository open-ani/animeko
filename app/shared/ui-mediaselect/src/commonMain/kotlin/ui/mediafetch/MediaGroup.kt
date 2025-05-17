/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind

@Immutable // only after build
class MediaGroup(
    val groupId: MediaGroupId,
    val list: List<MaybeExcludedMedia>,
) {
    @Stable
    val first: MaybeExcludedMedia = list.first()

    @Stable
    val isExcluded = list.all { it is MaybeExcludedMedia.Excluded }

    @Stable
    val exclusionReason: MediaExclusionReason? = list.firstOrNull { it.exclusionReason != null }?.exclusionReason

}

class MediaGroupBuilder(
    val id: String,
) {
    private val _list: ArrayList<MaybeExcludedMedia> = ArrayList(4)

    fun add(media: MaybeExcludedMedia) {
        _list.add(media)
    }

    fun build(): MediaGroup = MediaGroup(id, _list) // we don't copy it to save memory. This is called very frequently.
}

internal typealias MediaGroupId = String

object MediaGrouper {
    fun getGroupId(media: Media): String {
        // 添加一个 prefix, 这样在 UI crash LazyColumn key 时能知道.
        return "media-group-" + when (media.kind) {
            MediaSourceKind.BitTorrent -> {
                var title = media.originalTitle
                if (title.startsWith('[')) {
                    title = title.substringAfter(']')
                }
                title
            }

            MediaSourceKind.WEB,
            MediaSourceKind.LocalCache -> media.mediaId
        }
    }

    fun getItemIdWithinGroup(media: Media): String {
        val alliance = media.properties.alliance
        if (alliance.isNotEmpty()) return alliance

        val title = media.originalTitle
        if (title.startsWith('[')) {
            val index = title.indexOf(']')
            if (index != -1) {
                return title.substring(1, index)
            }
        }

        return title
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    fun buildGroups(list: List<MaybeExcludedMedia>): List<MediaGroup> {
        val groups = LinkedHashMap<String, MediaGroupBuilder>() // keep order
        for (media in list) {
            val groupId = getGroupId(media.original)
            groups.getOrPut(groupId) { MediaGroupBuilder(groupId) }
                .add(media)
        }
        return groups.values.map { it.build() }
    }
}
