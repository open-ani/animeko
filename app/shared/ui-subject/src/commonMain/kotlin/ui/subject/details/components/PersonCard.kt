/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.foundation.avatar.AvatarImage

/** 人物卡行: 方圆角头像 + 名字 + `职位/角色 (· CV)`. 用于角色/制作人员的"查看全部"列表. */
@Composable
fun PersonCard(info: RelatedPersonInfo, modifier: Modifier = Modifier) {
    PersonCard(
        avatarUrl = info.personInfo.imageMedium,
        name = info.personInfo.displayName,
        relation = info.position.nameCn ?: "",
        modifier = modifier,
    )
}

@Composable
fun PersonCard(info: RelatedCharacterInfo, modifier: Modifier = Modifier) {
    PersonCard(
        avatarUrl = info.character.imageMedium,
        name = info.character.displayName,
        relation = info.role.nameCn,
        modifier = modifier,
        actorName = remember(info) { getFirstName(info.character.actors) },
    )
}

private fun getFirstName(actors: List<PersonInfo>): String {
    if (actors.isEmpty()) return ""
    val actor = actors.first()
    return actor.displayName
}

@Composable
fun PersonCard(
    avatarUrl: String?,
    name: String,
    relation: String,
    modifier: Modifier = Modifier,
    actorName: String? = null,
) {
    Row(modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clip(MaterialTheme.shapes.small).size(48.dp)) {
                AvatarImage(avatarUrl, Modifier.matchParentSize(), alignment = Alignment.TopCenter)
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Text(name, Modifier.basicMarquee(), softWrap = false, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        Text(relation, softWrap = false, maxLines = 1)

                        if (actorName != null) {
                            Text(" · ", softWrap = false, maxLines = 1)
                            Text(actorName, Modifier.basicMarquee(), softWrap = false, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
