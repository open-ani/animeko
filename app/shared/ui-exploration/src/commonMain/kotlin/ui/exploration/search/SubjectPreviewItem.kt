/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.computeTotalEpisodeText
import me.him188.ani.app.data.models.subject.kind
import me.him188.ani.app.data.models.subject.nameCnOrName
import me.him188.ani.app.data.network.LightRelatedCharacterInfo
import me.him188.ani.app.data.network.LightRelatedPersonInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.paneVerticalPadding
import me.him188.ani.app.ui.rating.RatingText
import me.him188.ani.app.ui.subject.renderSubjectSeason
import me.him188.ani.utils.platform.annotations.TestOnly

@Immutable
class SubjectPreviewItemInfo(
    val subjectId: Int,
    val imageUrl: String,
    val title: String,
    val tags: String,
    val staff: String?,
    val actors: String?,
    val rating: RatingInfo,
    val nsfw: Boolean,
    /**
     * 此条目的 NSFW 显示状态
     */
    val nsfwMode: NsfwMode,
    /**
     * 隐藏此条目. 用于 workaround bangumi 搜出来不满足条件的条目
     */
    val hide: Boolean = false,
) {
    companion object {
        /**
         * @param nsfwModeSettings 用户设置的 NSFW 显示模式
         */
        fun compute(
            subjectInfo: SubjectInfo,
            mainEpisodeCount: Int,
            nsfwModeSettings: NsfwMode,
            relatedPersonList: List<LightRelatedPersonInfo>?,
            characters: List<LightRelatedCharacterInfo>?,
            roleSet: RoleSet = RoleSet.Default,
            hide: Boolean = false,
        ): SubjectPreviewItemInfo {
            val airingInfo = SubjectAiringInfo.computeFromSubjectInfo(subjectInfo, mainEpisodeCount)
            val tags = buildString {
                if (subjectInfo.airDate.isValid) {
                    append(renderSubjectSeason(subjectInfo.airDate))
                    append(" · ")
                }
                airingInfo.computeTotalEpisodeText()?.let {
                    append("全 $mainEpisodeCount 话")
                    append(" · ")
                }

                val sourceTag = subjectInfo.tags
                    .firstOrNull { it.kind == CanonicalTagKind.Source }
                    ?.let { sequenceOf(it) }.orEmpty()

                val genreTags = subjectInfo.tags
                    .filterTo(ArrayList(10)) { it.kind == CanonicalTagKind.Genre }
                    .apply { sortByDescending { it.count } }
                    .asSequence()

                append(
                    (sourceTag + genreTags)
                        .take(3)
                        .joinToString(" / ") { it.name },
                )
            }
            val staff = relatedPersonList?.let {
                val persons = relatedPersonList.asSequence()
                    .filterByRoleSet(roleSet)
                    .sortedWithRoleSet(roleSet)
                    .take(4)
                    .toList()

                if (persons.isEmpty()) return@let null

                buildString {
                    append("制作:  ")
                    persons.forEachIndexed { index, relatedPersonInfo ->
                        append(relatedPersonInfo.name)
                        if (index != persons.lastIndex) {
                            append(" · ")
                        }
                    }
                }
            }
//            val actors = characters?.takeIf { it.isNotEmpty() }?.let {
//                buildString {
//                    append("配音:  ")
//
//                    val mainCharacters = characters.asSequence()
//                        .filter { it.role == CharacterRole.MAIN }
//                    val nonMainCharacters = characters.asSequence()
//                        .filter { it.role != CharacterRole.MAIN }
//
//                    append(
//                        (mainCharacters + nonMainCharacters)
//                            .take(3)
//                            // mostSignificantCharacters
//                            .flatMap { it.actor }
//                            .map { it.name }
//                            .joinToString(" · "),
//                    )
//                }
//            }
            val actors = null

            return SubjectPreviewItemInfo(
                subjectId = subjectInfo.subjectId,
                subjectInfo.imageLarge,
                subjectInfo.nameCnOrName,
                tags,
                staff,
                actors,
                rating = subjectInfo.ratingInfo,
                nsfw = subjectInfo.nsfw,
                nsfwMode = if (subjectInfo.nsfw) nsfwModeSettings else NsfwMode.DISPLAY,
                hide = hide,
            )
        }
    }
}

@TestOnly
@Stable
internal val TestSubjectPreviewItemInfos
    get() = listOf(
        SubjectPreviewItemInfo(
            subjectId = 1,
            imageUrl = "https://example.com/image.jpg",
            title = "关于我转生变成史莱姆这档事 第三季",
            tags = "2024 年 10 月 · 全 24 话 · 奇幻 / 战斗",
            staff = "制作:  8bit · 中山敦史 · 泽野弘之",
            actors = "配音:  岡咲美保 · 前野智昭 · 古川慎",
            rating = RatingInfo(
                rank = 123,
                total = 100,
                count = RatingCounts.Zero,
                score = "6.7",
            ),
            nsfw = false,
            nsfwMode = NsfwMode.DISPLAY,
        ),
        SubjectPreviewItemInfo(
            subjectId = 2,
            imageUrl = "https://example.com/image.jpg",
            title = "关于我转生变成史莱姆这档事 第三季",
            tags = "2024 年 10 月 · 全 24 话 · 奇幻 / 战斗",
            staff = "制作:  8bit · 中山敦史 · 泽野弘之",
            actors = "配音:  岡咲美保 · 前野智昭 · 古川慎",
            rating = RatingInfo(
                rank = 123,
                total = 100,
                count = RatingCounts.Zero,
                score = "6.7",
            ),
            nsfw = true,
            nsfwMode = NsfwMode.BLUR,
        ),
    )

@Composable
fun SubjectPreviewItem(
    selected: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    info: SubjectPreviewItemInfo,
    modifier: Modifier = Modifier,
    image: @Composable () -> Unit = {
        SubjectItemDefaults.Image(info.imageUrl)
    },
    title: @Composable (Int) -> Unit = { maxLines ->
        Text(info.title, maxLines = maxLines)
    },
) {
    SubjectItemLayout(
        selected = selected,
        onClick = onClick,
        image = image,
        title = title,
        tags = {
            Text(info.tags, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        extraInfo = {
            info.staff?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            info.actors?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        rating = {
            RatingText(info.rating)
        },
        actions = {
//            SubjectItemDefaults.ActionPlay(onPlay)
        },
        modifier,
    )
}


@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewSubjectPreviewItem() {
    val info = TestSubjectPreviewItemInfos[0]
    SubjectPreviewItem(
        selected = false,
        onClick = { },
        onPlay = { },
        info = info,
        Modifier
            .fillMaxWidth()
            .padding(vertical = currentWindowAdaptiveInfo1().windowSizeClass.paneVerticalPadding / 2),
        image = {
            SubjectItemDefaults.Image(
                info.imageUrl,
            )
        },
        title = { maxLines ->
            Text(
                info.title,
                maxLines = maxLines,
            )
        },
    )
}

@Composable
@PreviewLightDark
private fun PreviewSubjectItemLayout() = ProvideCompositionLocalsForPreview {
    SubjectItemLayout(
        selected = false,
        {},
        image = { SubjectItemDefaults.Image("a", Modifier.fillMaxSize(), null) },
        title = { maxLines ->
            Text("关于我转生变成史莱姆这档事 第三季", maxLines = maxLines)
        },
        tags = { Text("2024 年 10 月 · 全 24 话 · 奇幻 / 战斗") },
        extraInfo = {
            Text("配音:  岡咲美保 · 前野智昭 ·  古川慎")
            Text("制作:  8bit · 中山敦史 · 泽野弘之 ")
        },
        rating = {},
        actions = {
            SubjectItemDefaults.ActionPlay({})
        },
    )
}
