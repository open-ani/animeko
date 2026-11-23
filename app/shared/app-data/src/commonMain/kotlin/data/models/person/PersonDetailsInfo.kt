/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.person

import androidx.compose.runtime.Immutable
import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.nameCn

/**
 * 人物 (声优/制作人员) 详情页数据.
 */
@Immutable
data class PersonDetailsInfo(
    val person: PersonInfo,
    /** Bangumi career 常量, 如 `seiyu`, `mangaka`. */
    val career: List<String>,
    /** 基本信息表 (来自 infobox, 已排除与名字重复的字段). */
    val infobox: List<InfoboxRowInfo>,
    val collects: Int,
    val commentCount: Int,
    /** 参与作品数 (去重后的条目数, 含只配音的条目). */
    val workCount: Int,
    /** 出演角色数 (条目 × 角色). */
    val castCount: Int,
)

/**
 * 角色详情页数据.
 */
@Immutable
data class CharacterDetailsInfo(
    /** [CharacterInfo.actors] 为全部条目下去重后的配音演员. */
    val character: CharacterInfo,
    /** 角色类型: 1=角色, 2=机体, 3=舰船, 4=组织. */
    val role: Int,
    val summary: String,
    /** 基本信息表 (来自 infobox, 已排除与名字重复的字段). */
    val infobox: List<InfoboxRowInfo>,
    val collects: Int,
    val commentCount: Int,
    /** 出演条目数. */
    val subjectCount: Int,
)

/** 基本信息表的一行, 多值已用 `、` 连接. */
@Immutable
data class InfoboxRowInfo(
    val key: String,
    val value: String,
)

/** 人物/角色详情页中关联条目卡片所需的最小条目信息. */
@Immutable
data class PersonSubjectSummary(
    val subjectId: Int,
    val name: String,
    val nameCn: String,
    val imageLarge: String,
) {
    val displayName get() = nameCn.takeIf { it.isNotBlank() } ?: name
}

/** 人物参与的一部作品: 担任的全部职位, 以及配音. */
@Immutable
data class PersonWorkInfo(
    val subject: PersonSubjectSummary,
    /** 制作职位. 只配音的作品为空. */
    val positions: List<PersonPosition>,
    /** 在这部作品中配音的角色数. Bangumi 里配音不是一种职位, 所以不在 [positions] 里. */
    val castCount: Int = 0,
) {
    /**
     * 用于展示的参与方式: 配音在前, 之后是各职位的中文名. 已去重, 没有可展示的名字时为空.
     *
     * 配音排在最前, 因为声优的职位基本都是主题歌演出, 只显示职位会让人以为这位声优没有给作品配音.
     */
    val roleNames: List<String> = buildList {
        if (castCount > 0) add("配音")
        positions.mapNotNullTo(this) { it.nameCn }
    }.distinct()
}

/** 人物在一部作品中配音的一个角色. */
@Immutable
data class PersonCastInfo(
    val subject: PersonSubjectSummary,
    val character: CharacterInfo,
)

/** 角色出演的一部条目及该条目下的配音演员. */
@Immutable
data class CharacterSubjectInfo(
    val subject: PersonSubjectSummary,
    val role: CharacterRole,
    val actors: List<PersonInfo>,
)
