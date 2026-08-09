/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_MIKAN
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import me.him188.ani.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorPreferencePayloadTest {
    @Test
    fun `SEL-05 会话未选维度的载荷携带全局默认值`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("X"),
            showWithoutSubtitle = true,
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.PlatformDefault.copy(
            subtitleLanguageId = "CHS",
        )

        val collected = selector.collectEvents {
            selector.resolution.prefer("1080P")
        }

        assertEquals(1, collected.records.size)
        assertEquals(
            // PINNED: SEL-05 subtitleLanguageId 携带全局默认值 CHS, alliancePatterns/showWithoutSubtitle/fallback 列表保留 savedUserPreference 原值
            MediaPreference.Empty.copy(
                alliancePatterns = listOf("X"),
                showWithoutSubtitle = true,
                resolution = "1080P",
                subtitleLanguageId = "CHS",
            ),
            collected.onChangePreference.single().preference,
        )
    }

    @Test
    fun `SEL-05 载荷的四字段取 savedUserPreference 而非 savedDefaultPreference`() =
        runSimpleMediaSelectorTestSuite {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "DB组")
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(alliance = "默认组")

            val collected = selector.collectEvents {
                selector.resolution.prefer("1080P")
            }

            assertEquals(1, collected.records.size)
            assertEquals(
                // PINNED: SEL-05 载荷的四个核心字段来自 newPreferences, 而 newPreferences 里每一项都是
                // finalSelected = userSelected(savedUser 层) orElse defaultSelected(savedDefault 层);
                // 因此没有会话 override 时 alliance 是 savedUser 的 "DB组", 不是 savedDefault 的 "默认组".
                MediaPreference.Empty.copy(
                    alliance = "DB组",
                    resolution = "1080P",
                ),
                collected.onChangePreference.single().preference,
            )
        }

    @Test
    fun `SEL-05 select 单字幕语言 media 后四字段全为 media 值`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(
            alliancePatterns = listOf("X"),
            showWithoutSubtitle = true,
        )
        preferenceApi.savedDefaultPreference.value = MediaPreference.PlatformDefault.copy(
            subtitleLanguageId = "CHS",
        )
        val target = media(
            sourceId = SOURCE_MIKAN,
            alliance = "桜都字幕组",
            resolution = "720P",
            subtitleLanguages = listOf("CHT"),
        )
        mediaApi.addMedia(target)

        val collected = selector.collectEvents {
            selector.resolution.prefer("1080P")
            assertTrue(selector.select(target))
        }

        // 约束事件总量: prefer 与 select 各广播一次偏好, 且 BT media 不发 onPreferWebSource
        collected.assertOrder(
            OnChangePreference::class,
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnSelect::class,
        )
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliancePatterns = listOf("X"),
                    showWithoutSubtitle = true,
                    resolution = "1080P",
                    subtitleLanguageId = "CHS",
                ),
                MediaPreference.Empty.copy(
                    alliancePatterns = listOf("X"),
                    showWithoutSubtitle = true,
                    alliance = "桜都字幕组",
                    resolution = "720P",
                    subtitleLanguageId = "CHT",
                    mediaSourceId = SOURCE_MIKAN,
                ),
            ),
            collected.onChangePreference.map { it.preference },
        )
    }

    /**
     * 构造一个与 [SimpleMediaSelectorTestSuite.selector] 配置相同, 但 savedUserPreference 可计数的 selector:
     * 每当有人真正 collect savedUserPreference 时 [collectCount] 自增.
     *
     * `broadcastChangePreference` 一旦真正开始计算载荷, 就必然会读 `savedUserPreferenceNotCached.first()`
     * 以及 `newPreferences.first()` (后者的每个 finalSelected 又会 collect savedUserPreference),
     * 所以 [collectCount] 是 "有没有发生计算" 的可观测代理.
     */
    private fun SimpleMediaSelectorTestSuite.createSelectorCountingSavedUserPreference(
        collectCount: MutableStateFlow<Int>,
    ): DefaultMediaSelector = DefaultMediaSelector(
        mediaSelectorContextNotCached = preferenceApi.mediaSelectorContext,
        mediaListNotCached = mediaApi.mediaList,
        savedUserPreference = flow {
            collectCount.update { it + 1 }
            emit(preferenceApi.savedUserPreference.value)
        },
        savedDefaultPreference = preferenceApi.savedDefaultPreference,
        mediaSelectorSettings = preferenceApi.mediaSelectorSettings,
        flowCoroutineContext = Dispatchers.Default,
        enableCaching = false,
        cachingScope = testScope.backgroundScope,
    )

    @Test
    fun `SAVE-02 无订阅者时 select 跳过 onChangePreference 的计算`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val savedUserCollectCount = MutableStateFlow(0)
        val spySelector = createSelectorCountingSavedUserPreference(savedUserCollectCount)
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(target)

        // PINNED: SAVE-02 onChangePreference.subscriptionCount == 0 时 broadcastChangePreference 直接返回,
        // 连 savedUserPreference 都不会被读一次 —— 这次偏好变更被静默丢弃.
        // (删掉 MediaSelector.broadcastChangePreference 的这行早返回, 下面的 0 会变成非 0)
        assertTrue(spySelector.select(target))
        assertEquals(0, savedUserCollectCount.value, "无订阅者时不应读取 savedUserPreference, 即不应计算载荷")

        // 对照: 有订阅者时同样的偏好变更会真正计算并广播
        val collected = spySelector.collectEvents {
            spySelector.alliance.prefer("另一个字幕组")
        }
        assertTrue(
            savedUserCollectCount.value > 0,
            "有订阅者时应读取 savedUserPreference 以计算载荷, 实际读取次数=${savedUserCollectCount.value}",
        )
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliance = "另一个字幕组",
                    resolution = target.properties.resolution,
                    subtitleLanguageId = "CHS",
                    mediaSourceId = target.mediaSourceId,
                ),
            ),
            collected.onChangePreference.map { it.preference },
        )
    }

    @Test
    fun `EVT-01 晚订阅者收不到 select 期间的事件 且 ITEM-02 会话 override 跨调用保留`() =
        runSimpleMediaSelectorTestSuite {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
            val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
            mediaApi.addMedia(target)

            assertTrue(selector.select(target))

            val collected = selector.collectEvents {
                selector.alliance.prefer("另一个字幕组")
            }

            // PINNED: EVT-01 replay=0, 晚订阅者只收得到订阅之后的事件 —— 上面 select 期间的
            // onBeforeSelect/onSelect (以及可能的 onChangePreference) 都不会补发给这个收集器
            collected.assertOrder(OnChangePreference::class)
            assertEquals(
                listOf(
                    // PINNED: ITEM-02 select 写入的三项会话 override (resolution/subtitleLanguageId/mediaSourceId)
                    // 在下一次 prefer 时仍然生效, 因此载荷里带的是上一次 select 的 media 值
                    MediaPreference.Empty.copy(
                        alliance = "另一个字幕组",
                        resolution = target.properties.resolution,
                        subtitleLanguageId = "CHS",
                        mediaSourceId = target.mediaSourceId,
                    ),
                ),
                collected.onChangePreference.map { it.preference },
            )
        }

    @Test
    fun `SAVE-02 订阅存在时 select 广播一次偏好事件`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(target)

        val collected = selector.collectEvents {
            assertTrue(selector.select(target))
        }

        // 约束事件总量: 恰好一次 onChangePreference, 且 BT media 不发 onPreferWebSource
        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnSelect::class,
        )
        assertEquals(
            listOf(
                MediaPreference.Empty.copy(
                    alliance = "字幕组",
                    resolution = target.properties.resolution,
                    subtitleLanguageId = "CHS",
                    mediaSourceId = target.mediaSourceId,
                ),
            ),
            collected.onChangePreference.map { it.preference },
        )
    }
}
