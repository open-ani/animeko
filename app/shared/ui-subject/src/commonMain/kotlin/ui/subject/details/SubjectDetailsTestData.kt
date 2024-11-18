/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import me.him188.ani.app.data.models.subject.CharacterInfo
import me.him188.ani.app.data.models.subject.CharacterRole
import me.him188.ani.app.data.models.subject.Images
import me.him188.ani.app.data.models.subject.PersonCareer
import me.him188.ani.app.data.models.subject.PersonInfo
import me.him188.ani.app.data.models.subject.PersonPosition
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.random.Random


@TestOnly
internal val TestRatingInfo
    get() = RatingInfo(
        rank = 123,
        total = 100,
        count = RatingCounts(IntArray(10) { it * 10 }),
        score = "6.7",
    )

@TestOnly
internal val TestCollectionStats
    get() = SubjectCollectionStats(
        wish = 100,
        doing = 200,
        done = 300,
        onHold = 400,
        dropped = 500,
    )

@TestOnly
internal val TestSubjectInfo
    get() = SubjectInfo.Empty.copy(
        nameCn = "孤独摇滚！",
        name = "ぼっち・ざ・ろっく！",
        airDate = PackedDate(2023, 10, 1),
        summary = """
        作为网络吉他手“吉他英雄”而广受好评的后藤一里，在现实中却是个什么都不会的沟通障碍者。一里有着组建乐队的梦想，但因为不敢向人主动搭话而一直没有成功，直到一天在公园中被伊地知虹夏发现并邀请进入缺少吉他手的“结束乐队”。可是，完全没有和他人合作经历的一里，在人前完全发挥不出原本的实力。为了努力克服沟通障碍，一里与“结束乐队”的成员们一同开始努力……
    """.trimIndent(),
        tags = listOf(
            Tag("芳文社", 7098),
            Tag("音乐", 5000),
            Tag("CloverWorks", 5000),
            Tag("轻百合", 4000),
            Tag("日常", 3758),
        ),
        ratingInfo = TestRatingInfo,
        collectionStats = TestCollectionStats,
    )

@TestOnly
internal const val TestCoverImage = "https://ui-avatars.com/api/?name=John+Doe"


@TestOnly
internal fun testPersonInfo(
    name: String,
    type: PersonType = PersonType.Individual,
    careers: List<PersonCareer> = emptyList(),
    summary: String = """一个测试人物""",
    locked: Boolean = false,
    images: Images? = null,
) = PersonInfo(
    id = Random.nextInt(),
    name = name,
    type = type,
    careers = careers,
    imageLarge = images?.large ?: TestCoverImage,
    imageMedium = images?.medium ?: TestCoverImage,
    summary = summary,
    locked = locked,
)


@TestOnly
internal fun testRelatedPersonInfo(
    index: Int,
    name: String,
    position: PersonPosition,
    type: PersonType = PersonType.Individual,
    careers: List<PersonCareer> = emptyList(),
    shortSummary: String = """一个测试人物""",
    locked: Boolean = false,
    images: Images? = null,
) = RelatedPersonInfo(
    index = 0,
    personInfo = testPersonInfo(name, type, careers, shortSummary, locked, images),
    position = position,
)

@TestOnly
internal val TestSubjectStaffInfo
    get() = listOf(
        testRelatedPersonInfo(
            0,
            "CloverWorks",
            position = PersonPosition.AnimationWork,
            type = PersonType.Corporation,
        ),
        testRelatedPersonInfo(1, "はまじあき", position = PersonPosition.OriginalWork),
        testRelatedPersonInfo(2, "斎藤圭一郎", position = PersonPosition.Director),
        testRelatedPersonInfo(3, "吉田恵里香", position = PersonPosition.CharacterDesign),
        testRelatedPersonInfo(4, "菊谷知樹", position = PersonPosition.Music),
        testRelatedPersonInfo(5, "けろりら", position = PersonPosition.CharacterDesign),
    )

@TestOnly
internal fun testRelatedCharacterInfo(
    index: Int,
    name: String,
    role: CharacterRole = CharacterRole.MAIN,
    id: Int = 0,
    nameCn: String = name,
    actors: List<PersonInfo> = emptyList(),
): RelatedCharacterInfo =
    RelatedCharacterInfo(
        index,
        CharacterInfo(id, name, nameCn, actors, imageMedium = "", imageLarge = ""),
        role,
    )

@TestOnly
internal fun testRelatedCharacterInfo(
    index: Int,
    name: String,
    actorName: String,
    role: CharacterRole = CharacterRole.MAIN,
    id: Int = 0,
    nameCn: String = name,
): RelatedCharacterInfo = RelatedCharacterInfo(
    index,
    CharacterInfo(
        id, name, nameCn,
        listOf(testPersonInfo(actorName, careers = listOf(PersonCareer.SEIYU))), imageMedium = "", imageLarge = "",
    ),
    role,
)

@TestOnly
internal val TestSubjectCharacterList
    get() = listOf(
        testRelatedCharacterInfo(0, "後藤ひとり", "青山吉能"),
        testRelatedCharacterInfo(1, "伊地知虹夏", "鈴代紗弓"),
        testRelatedCharacterInfo(2, "山田リョウ山田リョウ山田リョウ", "水野朔"),
        testRelatedCharacterInfo(3, "喜多郁代", "長谷川育美"),
        testRelatedCharacterInfo(4, "後藤直樹", "間島淳司"),
        testRelatedCharacterInfo(5, "後藤美智代", "末柄里恵"),
    )

@TestOnly
internal fun testRelatedSubjectInfo(
    nameCn: String,
    relation: SubjectRelation?,
    id: Int = Random.nextInt(),
    name: String? = null,
    image: String? = null,
) = RelatedSubjectInfo(id, relation, name, nameCn, image)

@TestOnly
internal val TestRelatedSubjects
    get() = listOf(
        testRelatedSubjectInfo("孤独摇滚 第二季", SubjectRelation.SEQUEL),
        testRelatedSubjectInfo("孤独摇滚 第零季", SubjectRelation.PREQUEL),
        testRelatedSubjectInfo("孤独摇滚 外传", SubjectRelation.DERIVED),
        testRelatedSubjectInfo("孤独摇滚 OAD", SubjectRelation.SPECIAL),
    )
