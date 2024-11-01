/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import me.him188.ani.app.data.models.subject.CanonicalTagKind
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.computeTotalEpisodeText
import me.him188.ani.app.data.models.subject.kind
import me.him188.ani.app.data.models.subject.nameCnOrName
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
) {
    companion object {
        fun compute(
            subjectInfo: SubjectInfo,
            relatedPersonList: List<RelatedPersonInfo>?,
            characters: List<RelatedCharacterInfo>?,
            roleSet: RoleSet = RoleSet.Default,
        ): SubjectPreviewItemInfo {
            val airingInfo = SubjectAiringInfo.computeFromSubjectInfo(subjectInfo)
            val tags = buildString {
                if (subjectInfo.airDate.isValid) {
                    append(renderSubjectSeason(subjectInfo.airDate))
                    append(" · ")
                }
                airingInfo.computeTotalEpisodeText()?.let {
                    append("全 ${subjectInfo.totalEpisodes} 话")
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
                buildString {
                    append("制作:  ")
                    relatedPersonList.filter(roleSet).take(4).forEach {
                        append(it.personInfo.displayName)
                        append(" · ")
                    }
                }
            }
            val actors = characters?.let {
                buildString {
                    append("配音:  ")

                    val mainCharacters = characters.asSequence()
                        .filter { it.isMainCharacter() }
                    val nonMainCharacters = characters.asSequence()
                        .filter { !it.isMainCharacter() }

                    append(
                        (mainCharacters + nonMainCharacters)
                            .take(3)
                            // mostSignificantCharacters
                            .flatMap { it.actors }
                            .map { it.displayName }
                            .joinToString(" · "),
                    )
                }
            }

            return SubjectPreviewItemInfo(
                subjectId = subjectInfo.subjectId,
                subjectInfo.imageLarge,
                subjectInfo.nameCnOrName,
                tags,
                staff,
                actors,
                rating = subjectInfo.ratingInfo,
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
