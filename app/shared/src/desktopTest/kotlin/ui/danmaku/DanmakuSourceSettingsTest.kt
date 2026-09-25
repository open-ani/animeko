/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(InternalResourceApi::class)

package me.him188.ani.app.ui.danmaku

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.ui.episode.danmaku.DanmakuSourceItem
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.subject.episode.details.DanmakuListContent
import me.him188.ani.app.ui.subject.episode.details.DanmakuListState
import me.him188.ani.app.ui.subject.episode.video.components.DanmakuSettingsSheet
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheets
import me.him188.ani.app.ui.subject.episode.video.settings.DanmakuSourceSettings
import me.him188.ani.app.ui.subject.episode.video.settings.EpisodeVideoSettings
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.ui.DanmakuConfig
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.rememberResourceEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

class DanmakuSourceSettingsTest {
    private val ani = DanmakuServiceId.Animeko
    private val dandan = DanmakuServiceId.Dandanplay

    private fun sources() = listOf(
        DanmakuSourceItem(ani, true, DanmakuMatchMethod.ExactId(1, 1), 0, 176),
        DanmakuSourceItem(dandan, true, DanmakuMatchMethod.ExactId(1, 1), 2500, 4439),
    )

    @Test
    fun `settings and list share source enable state`() = runAniComposeUiTest {
        var sources by mutableStateOf(sources())
        val onEnable: (DanmakuServiceId, Boolean) -> Unit = { id, enabled ->
            sources = sources.map { if (it.serviceId == id) it.copy(enabled = enabled) else it }
        }
        setContent {
            ProvideCompositionLocalsForPreview {
                Column(Modifier.width(400.dp)) {
                    DanmakuSourceSettings(sources, false, onEnable, {}, { _, _ -> })
                    DanmakuListContent(
                        DanmakuListState(emptyList(), sources, isLoading = false, isEmpty = true),
                        onEnable, {}, {}, Modifier.testTag("list"),
                    )
                }
            }
        }
        val sourceTag = "danmaku-source-${dandan.value}"
        onNode(hasTestTag(sourceTag) and hasAnyAncestor(hasTestTag("danmaku-source-settings"))).performClick()
        onNodeWithTag("danmaku-source-toggle").performClick()
        onNode(hasTestTag(sourceTag) and hasAnyAncestor(hasTestTag("list"))).assertIsNotSelected().performClick()
        onNode(hasTestTag(sourceTag) and hasAnyAncestor(hasTestTag("danmaku-source-settings"))).assertIsDisplayed()
        runOnIdle { assertEquals(true, sources.first { it.serviceId == dandan }.enabled) }
    }

    @Test
    fun `calibration commits only selected source and cancel leaves values intact`() = runAniComposeUiTest {
        var sources by mutableStateOf(sources())
        setContent {
            ProvideCompositionLocalsForPreview {
                DanmakuSourceSettings(
                    sources, false, { _, _ -> }, {},
                    onAdjustShift = { id, shift ->
                        sources = sources.map { if (it.serviceId == id) it.copy(shiftMillis = shift) else it }
                    },
                    modifier = Modifier.width(400.dp),
                )
            }
        }
        onNodeWithTag("danmaku-source-${dandan.value}").performClick()
        onNodeWithTag("danmaku-source-shift").performClick()
        onNodeWithTag("danmaku-shift-increase").performClick()
        onNodeWithTag("danmaku-shift-confirm").performClick()
        runOnIdle {
            assertEquals(3000L, sources.first { it.serviceId == dandan }.shiftMillis)
            assertEquals(0L, sources.first { it.serviceId == ani }.shiftMillis)
        }
        onNodeWithTag("danmaku-source-${ani.value}").performClick()
        onNodeWithTag("danmaku-source-shift").performClick()
        onNodeWithTag("danmaku-shift-increase").performClick()
        onNodeWithTag("danmaku-shift-cancel").performClick()
        onNodeWithTag("danmaku-time-shift-dialog").assertDoesNotExist()
        runOnIdle { assertEquals(0L, sources.first { it.serviceId == ani }.shiftMillis) }
    }

    @Test
    fun `rematch targets provider backed source and keeps settings available`() = runAniComposeUiTest {
        var rematched: DanmakuServiceId? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                DanmakuSourceSettings(sources(), false, { _, _ -> }, { rematched = it }, { _, _ -> })
            }
        }
        onNodeWithTag("danmaku-source-${dandan.value}").performClick()
        onNodeWithTag("danmaku-source-rematch").performClick()
        runOnIdle { assertEquals(dandan, rematched) }
        onNodeWithTag("danmaku-source-settings").assertIsDisplayed()
        onNodeWithTag("danmaku-source-${ani.value}").performClick()
        onNodeWithTag("danmaku-source-rematch").assertDoesNotExist()
    }

    @Test
    fun `sources appear before display settings at narrow width`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.width(320.dp)) {
                    EpisodeVideoSettings(
                        DanmakuConfig.Default, {}, false, {}, {},
                        sources = { SourceSection() },
                    )
                }
            }
        }
        onNodeWithTag("danmaku-source-${dandan.value}").assertIsDisplayed().performClick()
        onNodeWithTag("danmaku-source-shift").assertIsDisplayed().performClick()
        onNodeWithTag("danmaku-time-shift-dialog").assertIsDisplayed()
    }

    @Test
    fun `loading is replaced by sources and disabled sources remain manageable`() = runAniComposeUiTest {
        var loading by mutableStateOf(true)
        var sources by mutableStateOf(emptyList<DanmakuSourceItem>())
        setContent {
            ProvideCompositionLocalsForPreview {
                DanmakuSourceSettings(sources, loading, { _, _ -> }, {}, { _, _ -> })
            }
        }
        onNodeWithTag("danmaku-sources-loading").assertIsDisplayed()
        runOnIdle {
            loading = false
            sources = this@DanmakuSourceSettingsTest.sources().map { it.copy(enabled = false) }
        }
        onNodeWithTag("danmaku-sources-loading").assertDoesNotExist()
        onNodeWithTag("danmaku-source-${dandan.value}").assertIsDisplayed().performClick()
        onNodeWithTag("danmaku-source-toggle").assertIsDisplayed()
    }

    @Test
    fun `fullscreen sheet exposes source actions without dismissing settings`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Box(Modifier.width(900.dp).height(440.dp)) {
                    EpisodeVideoSideSheets.DanmakuSettingsSheet(
                        DanmakuConfig.Default, {}, false, {}, {},
                        onDismissRequest = { error("Source actions must keep settings open") },
                        sources = { SourceSection() },
                    )
                }
            }
        }
        onNodeWithTag("danmaku-source-${dandan.value}").assertIsDisplayed().performClick()
        onNodeWithTag("danmaku-source-shift").performClick()
        onNodeWithTag("danmaku-shift-cancel").performClick()
        onNodeWithTag("danmaku-source-${dandan.value}").assertIsDisplayed()
    }

    @Test
    fun `fuzzy source uses an explicit text status`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                DanmakuSourceSettings(
                    sources().map { it.copy(matchMethod = DanmakuMatchMethod.Fuzzy("Subject", "Episode")) },
                    false, { _, _ -> }, {}, { _, _ -> },
                    modifier = Modifier.width(320.dp),
                )
            }
        }
        onNodeWithTag("danmaku-source-${dandan.value}")
            .assertTextContains("Fuzzy match", substring = true)
            .performClick()
        onNodeWithTag("danmaku-source-rematch").assertIsDisplayed()
    }

    @Test
    fun `unmatched and semi fuzzy sources retain distinct statuses`() = runAniComposeUiTest {
        var method by mutableStateOf<DanmakuMatchMethod>(DanmakuMatchMethod.NoMatch)
        setContent {
            ProvideCompositionLocalsForPreview {
                DanmakuSourceSettings(
                    sources().map { it.copy(matchMethod = method) },
                    false, { _, _ -> }, {}, { _, _ -> },
                    modifier = Modifier.width(320.dp),
                )
            }
        }
        onNodeWithTag("danmaku-source-${dandan.value}").assertTextContains("No match", substring = true)
        runOnIdle { method = DanmakuMatchMethod.ExactSubjectFuzzyEpisode("Subject", "Episode") }
        onNodeWithTag("danmaku-source-${dandan.value}").assertTextContains("Semi-fuzzy match", substring = true)
    }

    @Test
    fun `source section narrow screenshot`() = runAniComposeUiTest {
        setContent {
            ChinesePreview(DarkMode.LIGHT) {
                Surface(Modifier.width(320.dp), color = MaterialTheme.colorScheme.surface) {
                    SourceSection()
                }
            }
        }
        onNodeWithTag("danmaku-source-settings").assertScreenshot("/screenshots/DanmakuSourceSettingsTest.narrow.png")
    }

    @Test
    fun `source settings match display settings in dark panel`() = runAniComposeUiTest {
        setContent {
            ChinesePreview(DarkMode.DARK) {
                Surface(
                    Modifier.width(400.dp).height(720.dp).testTag("settings-panel"),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    EpisodeVideoSettings(
                        DanmakuConfig.Default, {}, false, {}, {},
                        sources = { SourceSection() },
                    )
                }
            }
        }
        onNodeWithTag("settings-panel").assertScreenshot("/screenshots/DanmakuSourceSettingsTest.dark-panel.png")
    }

    @Composable
    private fun ChinesePreview(darkMode: DarkMode, content: @Composable () -> Unit) {
        val environment = rememberResourceEnvironment()
        val chineseEnvironment = remember(environment) {
            object : ComposeEnvironment {
                @Composable
                override fun rememberEnvironment() = ResourceEnvironment(
                    language = LanguageQualifier("zh"),
                    script = environment.script,
                    region = RegionQualifier("CN"),
                    theme = environment.theme,
                    density = environment.density,
                )
            }
        }
        CompositionLocalProvider(LocalComposeEnvironment provides chineseEnvironment) {
            ProvideCompositionLocalsForPreview(darkMode = darkMode, content = content)
        }
    }

    @Composable
    private fun SourceSection() {
        DanmakuSourceSettings(sources(), false, { _, _ -> }, {}, { _, _ -> })
    }
}
