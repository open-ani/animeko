/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.engine

import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.selector.MatchMetadata
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 纯策略函数 [decideWebAutoSelect] 的表驱动测试: 构造快照直接喂函数, 不涉及 flow 与时间.
 */
class WebAutoSelectPolicyTest {
    private var counter = 0

    private fun media(
        sourceId: String,
        kind: MediaSourceKind = MediaSourceKind.WEB,
        alliance: String = "字幕组",
        subjectName: String = "test",
    ): Media = createTestDefaultMedia(
        mediaId = "$sourceId.${counter++}",
        mediaSourceId = sourceId,
        originalUrl = "https://example.com/$counter",
        download = ResourceLocation.WebVideo("https://example.com/$counter"),
        originalTitle = subjectName,
        publishedTime = 0,
        properties = createTestMediaProperties(subjectName = subjectName, alliance = alliance),
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        location = if (kind == MediaSourceKind.LocalCache) MediaSourceLocation.Local else MediaSourceLocation.Online,
        kind = kind,
    )

    private fun included(media: Media, exact: Boolean = true) = MaybeExcludedMedia.Included(
        media,
        MatchMetadata(
            subjectMatchKind = if (exact) MatchMetadata.SubjectMatchKind.EXACT else MatchMetadata.SubjectMatchKind.FUZZY,
            episodeMatchKind = MatchMetadata.EpisodeMatchKind.SORT,
            similarity = if (exact) 100 else 60,
        ),
    )

    private fun source(
        id: String,
        vararg results: Media,
        state: MediaSourceFetchState = MediaSourceFetchState.Succeed(0),
        kind: MediaSourceKind = MediaSourceKind.WEB,
    ) = SourceSnapshot(id, kind, state, results.toList())

    private val working = MediaSourceFetchState.Working

    private val loadedContext = MediaSelectorTestSuite.createMediaSelectorContextFromEmpty()

    private fun snapshot(
        sources: List<SourceSnapshot>,
        candidates: List<MaybeExcludedMedia.Included>,
        preferred: List<MaybeExcludedMedia.Included> = candidates,
        preference: MediaPreference = MediaPreference.Any,
        context: MediaSelectorContext = loadedContext,
    ) = AutoSelectSnapshot(
        sources = sources,
        candidates = candidates,
        preferred = preferred,
        mergedPreference = preference,
        settings = MediaSelectorSettings.Default,
        context = context,
    )

    private fun tiers(vararg pairs: Pair<String, Int>) = MediaSelectorSourceTiers(
        tiers = pairs.associate { (id, tier) -> id to MediaSourceTier(tier.toUInt()) },
    )

    private fun config(sourceTiers: MediaSelectorSourceTiers, block: WebAutoSelectConfig.() -> WebAutoSelectConfig = { this }) =
        WebAutoSelectConfig(sourceTiers = sourceTiers).block()

    private fun assertSelects(expected: Media, decision: WebAutoSelectDecision) {
        val select = assertIs<WebAutoSelectDecision.Select>(decision, "expected Select but was $decision")
        assertEquals(expected.mediaId, select.media.mediaId)
    }

    ///////////////////////////////////////////////////////////////////////////
    // INSTANT
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `instant selects exact t0 as soon as its source succeeded`() {
        val m = media("t0")
        val snap = snapshot(listOf(source("t0", m), source("t2", state = working)), listOf(included(m)))
        assertSelects(m, decideWebAutoSelect(snap, config(tiers("t0" to 0, "t2" to 2)), WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `instant waits for fuzzy t0 and for exact t1`() {
        val fuzzyT0 = media("t0")
        val exactT1 = media("t1")
        val snap = snapshot(
            listOf(source("t0", fuzzyT0), source("t1", exactT1), source("pending", state = working)),
            listOf(included(fuzzyT0, exact = false), included(exactT1)),
        )
        assertEquals(
            WebAutoSelectDecision.Wait,
            decideWebAutoSelect(snap, config(tiers("t0" to 0, "t1" to 1)), WebAutoSelectStage.INSTANT),
        )
    }

    @Test
    fun `instant uses channel tier over source tier`() {
        val demoted = media("t0", alliance = "bad-channel")
        val snap = snapshot(listOf(source("t0", demoted), source("pending", state = working)), listOf(included(demoted)))
        val sourceTiers = MediaSelectorSourceTiers(
            tiers = mapOf("t0" to MediaSourceTier(0u)),
            channelTiers = mapOf("t0" to mapOf("bad-channel" to MediaSourceTier(2u))),
        )
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(snap, config(sourceTiers), WebAutoSelectStage.INSTANT))
    }

    ///////////////////////////////////////////////////////////////////////////
    // EXACT_ONLY / FUZZY ordering
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `exact only picks the lowest tier regardless of source order`() {
        val t2 = media("t2")
        val t1 = media("t1")
        val snap = snapshot(
            listOf(source("t2", t2), source("t1", t1), source("pending", state = working)),
            listOf(included(t2), included(t1)), // t2 在前
        )
        assertSelects(t1, decideWebAutoSelect(snap, config(tiers("t2" to 2, "t1" to 1)), WebAutoSelectStage.EXACT_ONLY))
    }

    @Test
    fun `exact only never selects fuzzy even at tier 0`() {
        val fuzzyT0 = media("t0")
        val snap = snapshot(listOf(source("t0", fuzzyT0), source("pending", state = working)), listOf(included(fuzzyT0, exact = false)))
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(snap, config(tiers("t0" to 0)), WebAutoSelectStage.EXACT_ONLY))
    }

    @Test
    fun `fuzzy prefers exact of any tier over fuzzy t0 then orders fuzzy by tier`() {
        val fuzzyT0 = media("t0")
        val exactT2 = media("t2")
        val fuzzyT1 = media("t1")
        val sources = listOf(source("t0", fuzzyT0), source("t2", exactT2), source("t1", fuzzyT1), source("pending", state = working))
        val cfg = config(tiers("t0" to 0, "t1" to 1, "t2" to 2))

        assertSelects(
            exactT2,
            decideWebAutoSelect(
                snapshot(sources, listOf(included(fuzzyT0, false), included(exactT2), included(fuzzyT1, false))),
                cfg, WebAutoSelectStage.FUZZY,
            ),
        )
        assertSelects(
            fuzzyT0,
            decideWebAutoSelect(
                snapshot(sources, listOf(included(fuzzyT1, false), included(fuzzyT0, false))),
                cfg, WebAutoSelectStage.FUZZY,
            ),
        )
    }

    @Test
    fun `candidates from sources that have not succeeded are ignored`() {
        val m = media("t0")
        // 源还在 Working, 但结果已经流出来了 (分页源): 不能选
        val snap = snapshot(listOf(source("t0", m, state = working)), listOf(included(m)))
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(snap, config(tiers("t0" to 0)), WebAutoSelectStage.FUZZY))
    }

    ///////////////////////////////////////////////////////////////////////////
    // all web sources final
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `all web sources final evaluates as fuzzy regardless of stage`() {
        val fuzzyT2 = media("t2")
        val snap = snapshot(listOf(source("t2", fuzzyT2)), listOf(included(fuzzyT2, exact = false)))
        assertSelects(fuzzyT2, decideWebAutoSelect(snap, config(tiers("t2" to 2)), WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `all web sources final with nothing selectable finishes unless default is allowed`() {
        val bt = media("bt", kind = MediaSourceKind.BitTorrent)
        val snap = snapshot(
            listOf(source("web", state = MediaSourceFetchState.Failed(IllegalStateException(), 0)), source("bt", bt, kind = MediaSourceKind.BitTorrent)),
            listOf(included(bt)),
        )
        assertEquals(WebAutoSelectDecision.Finish, decideWebAutoSelect(snap, config(tiers()), WebAutoSelectStage.INSTANT))
        assertSelects(bt, decideWebAutoSelect(snap, config(tiers()) { copy(defaultWhenAllCompleted = true) }, WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `default after all completed is limited to preferred candidates`() {
        val bt = media("bt", kind = MediaSourceKind.BitTorrent)
        val snap = snapshot(
            listOf(source("web", state = MediaSourceFetchState.Failed(IllegalStateException(), 0)), source("bt", bt, kind = MediaSourceKind.BitTorrent)),
            candidates = listOf(included(bt)),
            preferred = emptyList(), // 用户偏好把它滤掉了
        )
        assertEquals(
            WebAutoSelectDecision.Finish,
            decideWebAutoSelect(snap, config(tiers()) { copy(defaultWhenAllCompleted = true) }, WebAutoSelectStage.INSTANT),
        )
    }

    @Test
    fun `all web final relaxes user preference within the best group`() {
        val m = media("t2", alliance = "组B")
        val snap = snapshot(
            listOf(source("t2", m)),
            candidates = listOf(included(m)),
            preferred = emptyList(), // 偏好字幕组 "组A" 不在候选中
            preference = MediaPreference.Any.copy(alliance = "组A"),
        )
        assertSelects(m, decideWebAutoSelect(snap, config(tiers("t2" to 2)), WebAutoSelectStage.EXACT_ONLY))
    }

    ///////////////////////////////////////////////////////////////////////////
    // preferred web source
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `preferred source stage waits for it then selects only from it`() {
        val other = media("t0")
        val remembered = media("mem")
        val cfg = config(tiers("t0" to 0)) { copy(preferredWebSourceId = "mem") }

        val pending = snapshot(listOf(source("t0", other), source("mem", state = working)), listOf(included(other)))
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(pending, cfg, WebAutoSelectStage.PREFERRED_SOURCE))

        val done = snapshot(listOf(source("t0", other), source("mem", remembered)), listOf(included(other), included(remembered, exact = false)))
        assertSelects(remembered, decideWebAutoSelect(done, cfg, WebAutoSelectStage.PREFERRED_SOURCE))
    }

    @Test
    fun `preferred source without result releases the gate or finishes`() {
        val other = media("t0")
        val snap = snapshot(listOf(source("t0", other), source("mem")), listOf(included(other)))
        assertEquals(
            WebAutoSelectDecision.ReleasePreferredSourceGate,
            decideWebAutoSelect(snap, config(tiers()) { copy(preferredWebSourceId = "mem") }, WebAutoSelectStage.PREFERRED_SOURCE),
        )
        assertEquals(
            WebAutoSelectDecision.Finish,
            decideWebAutoSelect(
                snap, config(tiers()) { copy(preferredWebSourceId = "mem", stopAfterPreferredSource = true) },
                WebAutoSelectStage.PREFERRED_SOURCE,
            ),
        )
        // 记忆源不在会话中: 立即放行
        assertEquals(
            WebAutoSelectDecision.ReleasePreferredSourceGate,
            decideWebAutoSelect(snap, config(tiers()) { copy(preferredWebSourceId = "missing") }, WebAutoSelectStage.PREFERRED_SOURCE),
        )
    }

    @Test
    fun `preferred source only considers preferred candidates`() {
        val remembered = media("mem")
        val snap = snapshot(
            listOf(source("mem", remembered)),
            candidates = listOf(included(remembered)),
            preferred = emptyList(), // 例如用户 JSON 偏好的 mediaSourceId 是别的源
        )
        assertEquals(
            WebAutoSelectDecision.ReleasePreferredSourceGate,
            decideWebAutoSelect(snap, config(tiers()) { copy(preferredWebSourceId = "mem") }, WebAutoSelectStage.PREFERRED_SOURCE),
        )
    }

    ///////////////////////////////////////////////////////////////////////////
    // cache, blacklist, override, context
    ///////////////////////////////////////////////////////////////////////////

    @Test
    fun `local cache wins in any stage when enabled`() {
        val cache = media("cache", kind = MediaSourceKind.LocalCache)
        val t0 = media("t0")
        val snap = snapshot(
            listOf(source("cache", cache, kind = MediaSourceKind.LocalCache), source("t0", t0), source("mem", state = working)),
            listOf(included(t0), included(cache)),
        )
        val cfg = config(tiers("t0" to 0)) { copy(preferredWebSourceId = "mem", selectCache = true) }
        assertSelects(cache, decideWebAutoSelect(snap, cfg, WebAutoSelectStage.PREFERRED_SOURCE))
        assertSelects(t0, decideWebAutoSelect(snap, cfg.copy(selectCache = false, preferredWebSourceId = null), WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `blacklisted media are skipped`() {
        val a = media("t0")
        val b = media("t0")
        val snap = snapshot(listOf(source("t0", a, b)), listOf(included(a), included(b)))
        assertSelects(b, decideWebAutoSelect(snap, config(tiers("t0" to 0)) { copy(blacklistMediaIds = setOf(a.mediaId)) }, WebAutoSelectStage.INSTANT))
        assertEquals(
            WebAutoSelectDecision.Finish,
            decideWebAutoSelect(snap, config(tiers("t0" to 0)) { copy(blacklistMediaIds = setOf(a.mediaId, b.mediaId)) }, WebAutoSelectStage.INSTANT),
        )
    }

    @Test
    fun `override mode keeps current selection when it belongs to the best group`() {
        val current = media("t0")
        val sibling = media("t0")
        val worse = media("t1")
        val snap = snapshot(
            listOf(source("t0", current, sibling), source("t1", worse)),
            listOf(included(sibling), included(current), included(worse)),
        )
        val cfg = config(tiers("t0" to 0, "t1" to 1)) { copy(currentSelection = current) }
        assertSelects(current, decideWebAutoSelect(snap, cfg, WebAutoSelectStage.FUZZY))
        // 当前选择被拉黑后才会换到同组的另一个
        assertSelects(sibling, decideWebAutoSelect(snap, cfg.copy(blacklistMediaIds = setOf(current.mediaId)), WebAutoSelectStage.FUZZY))
    }

    @Test
    fun `within a group preferred candidates win over relaxed candidates`() {
        val p720 = media("t0")
        val p1080 = media("t0")
        // 排序结果 720P 在前; 用户偏好 1080P, 只有 p1080 在 preferred 里
        val snap = snapshot(
            listOf(source("t0", p720, p1080), source("pending", state = working)),
            candidates = listOf(included(p720), included(p1080)),
            preferred = listOf(included(p1080)),
            preference = MediaPreference.Any.copy(resolution = "1080P"),
        )
        assertSelects(p1080, decideWebAutoSelect(snap, config(tiers("t0" to 0)), WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `no enabled web source waits for all sources then selects default`() {
        val bt = media("bt", kind = MediaSourceKind.BitTorrent)
        val cfg = config(tiers()) { copy(defaultWhenAllCompleted = true) }
        val disabledWeb = source("web", state = MediaSourceFetchState.Disabled)

        val pending = snapshot(listOf(disabledWeb, source("bt", state = working, kind = MediaSourceKind.BitTorrent)), emptyList())
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(pending, cfg, WebAutoSelectStage.INSTANT))

        val done = snapshot(listOf(disabledWeb, source("bt", bt, kind = MediaSourceKind.BitTorrent)), listOf(included(bt)))
        assertSelects(bt, decideWebAutoSelect(done, cfg, WebAutoSelectStage.INSTANT))

        // 快速选择入口 (不做默认选择) 则维持旧语义: 没有可选的 WEB 资源就结束
        assertEquals(WebAutoSelectDecision.Finish, decideWebAutoSelect(pending, config(tiers()), WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `waits for context when there is something to choose from`() {
        val m = media("t0")
        val snap = snapshot(listOf(source("t0", m)), listOf(included(m)), context = MediaSelectorContext.Initial)
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(snap, config(tiers("t0" to 0)), WebAutoSelectStage.INSTANT))
        // 没有任何候选时不等 context, 直接结束
        val empty = snapshot(listOf(source("t0")), emptyList(), context = MediaSelectorContext.Initial)
        assertEquals(WebAutoSelectDecision.Finish, decideWebAutoSelect(empty, config(tiers("t0" to 0)), WebAutoSelectStage.INSTANT))
    }

    @Test
    fun `fast select disabled only decides after all web sources are final`() {
        val m = media("t0")
        val cfg = config(tiers("t0" to 0)) { copy(fastSelect = false) }
        val pending = snapshot(listOf(source("t0", m), source("t2", state = working)), listOf(included(m)))
        assertEquals(WebAutoSelectDecision.Wait, decideWebAutoSelect(pending, cfg, WebAutoSelectStage.FUZZY))
        val done = snapshot(listOf(source("t0", m), source("t2")), listOf(included(m)))
        assertSelects(m, decideWebAutoSelect(done, cfg, WebAutoSelectStage.INSTANT))
    }
}
