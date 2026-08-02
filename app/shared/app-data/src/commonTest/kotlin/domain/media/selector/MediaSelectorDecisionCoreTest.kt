/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_MIKAN
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorDecisionCoreTest {
    @Test
    fun `TRY-08 只移除到候选非空即停`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliance = "组A",
            resolution = "1080P",
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))

        assertEquals(emptyList(), selector.preferredCandidatesMedia.first())

        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        // PINNED: TRY-08 alliance 移除后候选即非空, 立即停止, resolution 偏好保留
        assertTrue(selector.alliance.userSelected.first().isPreferNoValue)
        assertEquals("1080P", selector.resolution.finalSelected.first())
        assertEquals("1080P", selector.resolution.userSelected.first().preferredValueOrNull)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
        assertEquals(
            listOf(MediaPreference.Empty.copy(resolution = "1080P")),
            collected.onChangePreference.map { it.preference },
        )
        assertEquals(1, collected.records.size)
    }

    @Test
    fun `TRY-08 四项全不匹配时全部移除并恰好广播 4 次偏好事件`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliance = "组A",
            resolution = "720P",
            subtitleLanguageId = "CHT",
            mediaSourceId = SOURCE_MIKAN,
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))

        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        // PINNED: TRY-08 按 alliance -> resolution -> subtitleLanguageId -> mediaSourceId 顺序逐项移除
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    resolution = "720P",
                    subtitleLanguageId = "CHT",
                    mediaSourceId = SOURCE_MIKAN,
                ),
                MediaPreference.Empty.copy(subtitleLanguageId = "CHT", mediaSourceId = SOURCE_MIKAN),
                MediaPreference.Empty.copy(mediaSourceId = SOURCE_MIKAN),
                MediaPreference.Empty,
            ),
            collected.onChangePreference.map { it.preference },
        )
        assertEquals(4, collected.records.size)
        assertTrue(selector.alliance.userSelected.first().isPreferNoValue)
        assertTrue(selector.resolution.userSelected.first().isPreferNoValue)
        assertTrue(selector.subtitleLanguageId.userSelected.first().isPreferNoValue)
        assertTrue(selector.mediaSourceId.userSelected.first().isPreferNoValue)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `TRY-08 候选已非空时不移除任何偏好也不广播事件`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "组B")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        mediaApi.addMedia(media(alliance = "组B", subtitleLanguages = listOf("CHS")))

        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        collected.expectNoEvents()
        assertEquals("组B", selector.alliance.userSelected.first().preferredValueOrNull)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `TRY-08 caching 开启时移除后复查走未缓存流仍只移除 alliance`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
        buildTest = {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
                alliance = "组A",
                resolution = "1080P",
            )
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
            mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        },
    ) {
        val collected = selector.collectEvents {
            selector.removePreferencesUntilFirstCandidate()
        }

        // PINNED: TRY-08 每步复查读未缓存流, 同步看到刚移除的偏好, 不会多移除
        assertEquals(1, collected.onChangePreference.size)
        assertEquals(1, collected.records.size)
        // MERGE-02: 载荷必须是移除 alliance *之后* 的合并结果. caching 开启时若载荷来自 replay=1 的
        // 缓存流 (整体错一拍), 这里会读到 alliance="组A"; 只断次数捕捉不到该 mutant.
        assertEquals(
            listOf(MediaPreference.Empty.copy(resolution = "1080P")),
            collected.onChangePreference.map { it.preference },
        )
        assertTrue(selector.alliance.userSelected.first().isPreferNoValue)
        assertEquals("1080P", selector.resolution.finalSelected.first())
        // PINNED: TRY-08 caching 开启时这里也**不需要**推进调度器.
        // 原因: removePreference 内的 withContext(flowCoroutineContext) 会让出到 test dispatcher,
        // 返回时 shareIn(replay=1) 的上游已按新偏好重算完毕, 因此缓存流同步就是新快照.
        // (实测: 删掉 advanceUntilIdle()+runCurrent() 本断言依旧绿; 保留它们反而会掩盖重构引入的传播延迟.)
        assertEquals(1, selector.preferredCandidatesMedia.first().size)
    }

    @Test
    fun `FIND-04 不为 4K 换语言`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            fallbackResolutions = listOf("2160P", "1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )
        mediaApi.addMedia(media(alliance = "组A", resolution = "2160P", subtitleLanguages = listOf("JPN")))
        val chs1080P = mediaApi.addMedia(
            media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        // PINNED: FIND-04 2160P 层存在资源但无想要的字幕语言, 跳过该分辨率选 1080P CHS
        assertEquals(chs1080P, selector.trySelectDefault())
        assertEquals(chs1080P, selector.selected.value)
    }

    @Test
    fun `FIND-04 对照组 2160P 有想要语言时分辨率优先`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            fallbackResolutions = listOf("2160P", "1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )
        // 故意把 1080P 放在首位: 期望值 chs2160P 不是候选列表首项, 这样"直接返回首项"的退化实现也会被排除
        mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        val chs2160P = mediaApi.addMedia(
            media(alliance = "组A", resolution = "2160P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(chs2160P, selector.trySelectDefault())
        assertEquals(chs2160P, selector.selected.value)
    }

    @Test
    fun `FIND-04 caching 开启时不为 4K 换语言`() = runSimpleMediaSelectorTestSuite(
        cachingEnabled = true,
        buildTest = {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
                fallbackResolutions = listOf("2160P", "1080P"),
                fallbackSubtitleLanguageIds = listOf("CHS"),
            )
            mediaApi.addMedia(media(alliance = "组A", resolution = "2160P", subtitleLanguages = listOf("JPN")))
            mediaApi.addMedia(media(alliance = "组B", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        },
    ) {
        // 生产默认 enableCaching=true, 决策核也必须在缓存路径下跑一遍
        val chs1080P = mediaApi.mediaList.value[1]

        // PINNED: FIND-04 缓存路径下结论不变: 2160P 层无想要语言时跳过该分辨率选 1080P CHS
        assertEquals(chs1080P, selector.trySelectDefault())
        assertEquals(chs1080P, selector.selected.value)
    }

    @Test
    fun `FIND-05 偏好字幕组未命中时不降级语言`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("桜都"),
            fallbackResolutions = listOf("1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS", "CHT"),
        )
        val chsOtherAlliance = mediaApi.addMedia(
            media(alliance = "LoliHouse", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHT")))

        // PINNED: FIND-05 CHS 层无偏好字幕组时放弃字幕组保住语言, 不换到 CHT/桜都
        assertEquals(chsOtherAlliance, selector.trySelectDefault())
    }

    @Test
    fun `FIND-05 对照组 同语言存在偏好字幕组时选它`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("桜都"),
            fallbackResolutions = listOf("1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS", "CHT"),
        )
        mediaApi.addMedia(media(alliance = "LoliHouse", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHT")))
        val chsPreferredAlliance = mediaApi.addMedia(
            media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(chsPreferredAlliance, selector.trySelectDefault())
    }

    @Test
    fun `FIND-10 alliancePatterns 匹配池为全量列表而候选子集命中为空时回落`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(resolution = "1080P")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("^桜都"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "720P", subtitleLanguages = listOf("CHS")))
        val fallback = mediaApi.addMedia(
            media(alliance = "LoliHouse", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(listOf(fallback), selector.preferredCandidatesMedia.first())
        assertTrue("桜都字幕组" in selector.alliance.available.first())

        // PINNED: FIND-10 alliances 序列由全量池生成(含被 resolution 偏好滤掉的桜都), 子集内过滤为空, 回落到放弃字幕组分支
        assertEquals(fallback, selector.trySelectDefault())
        assertEquals(fallback, selector.selected.value)
    }

    @Test
    fun `FIND-10 单个 pattern 多命中时按全量池的字典序尝试而非候选列表原序`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("^桜都"),
            fallbackResolutions = listOf("1080P"),
            fallbackSubtitleLanguageIds = listOf("CHS"),
        )
        // 两个字幕组都匹配 ^桜都, 同分辨率同语言, 都在候选子集内, 唯一区别是尝试顺序.
        // 候选列表原序 = 添加顺序 [桜都字幕组, 桜都动漫] (sortMediaList 在其余 key 全相同时稳定);
        // 全量池 alliance.available 经 sortedBy 后是 [桜都动漫, 桜都字幕组] (UTF-16: 动 U+52A8 < 字 U+5B57).
        mediaApi.addMedia(media(alliance = "桜都字幕组", resolution = "1080P", subtitleLanguages = listOf("CHS")))
        val sakuraDonghua = mediaApi.addMedia(
            media(alliance = "桜都动漫", resolution = "1080P", subtitleLanguages = listOf("CHS")),
        )

        assertEquals(listOf("桜都动漫", "桜都字幕组"), selector.alliance.available.first())
        assertEquals(
            listOf("桜都字幕组", "桜都动漫"),
            selector.preferredCandidatesMedia.first().map { it.properties.alliance },
        )

        // PINNED: FIND-10 alliances 序列由全量池 (alliance.available, 已字典序排序) 生成, 故先尝试 桜都动漫.
        // 任何改为"按候选子集原序派生匹配池"的实现都会选中 桜都字幕组 -> 此断言变红.
        assertEquals(sakuraDonghua, selector.trySelectDefault())
        assertEquals(sakuraDonghua, selector.selected.value)
    }
}
