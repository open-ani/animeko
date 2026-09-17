/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.media.selector.MediaSelectorSubtitlePreferences
import me.him188.ani.app.domain.mediasource.web.captcha.createTestWebSessionManager
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.annotations.TestOnly
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@EnabledOnOs(OS.MAC)
@OptIn(TestOnly::class)
class MacOSSubtitleSelectionUiTest {
    @Test
    fun internalSubtitlesCanBeSelected() = runAniComposeUiTest {
        val kinds = listOf(SubtitleKind.CLOSED, SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER, SubtitleKind.EXTERNAL_DISCOVER)
        val media = kinds.map { kind ->
            TestMediaList.first().copy(
                mediaId = kind.name,
                originalTitle = "[Test] Subtitle test - 01 [HEVC-10bit AAC $kind].mkv",
                properties = TestMediaList.first().properties.copy(subtitleKind = kind),
            )
        }
        val selector = DefaultMediaSelector(
            mediaSelectorContextNotCached = flowOf(
                MediaSelectorContext.EmptyForPreview.copy(
                    subtitlePreferences = MediaSelectorSubtitlePreferences.forPlatform(Platform.MacOS(Arch.AARCH64)),
                ),
            ),
            mediaListNotCached = flowOf(media),
            savedUserPreference = flowOf(MediaPreference.Empty),
            savedDefaultPreference = flowOf(MediaPreference.Empty),
            mediaSelectorSettings = flowOf(MediaSelectorSettings.AllVisible),
        )
        lateinit var state: MediaSelectorState
        setContent {
            val scope = rememberCoroutineScope()
            state = remember {
                MediaSelectorState(
                    selector,
                    flowOf(emptyList()),
                    createTestMediaSourceInfoProvider(),
                    flowOf(null),
                    scope,
                    createTestWebSessionManager(scope),
                )
            }
            ProvideCompositionLocalsForPreview(darkMode = DarkMode.LIGHT) {
                CompositionLocalProvider(
                    LocalTimeFormatter provides TimeFormatter(getTimeNow = { Instant.fromEpochMilliseconds(0) }),
                ) {
                    Surface(Modifier.size(800.dp, 600.dp).testTag("selector")) {
                        MediaSelectorView(
                            state = state,
                            viewKind = ViewKind.BT,
                            onViewKindChange = {},
                            fetchRequest = null,
                            onFetchRequestChange = {},
                            sourceResults = MediaSourceResultListPresentation(emptyList()),
                            onRestartSource = {},
                            onRefresh = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { !state.presentationFlow.value.isPlaceholder }
        onNodeWithText(media[2].originalTitle).assertDoesNotExist()
        onNodeWithText(media[0].originalTitle, useUnmergedTree = true).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { state.presentationFlow.value.selected == media[0] }
        runOnIdle { assertEquals(media[0], selector.selected.value) }
        onNodeWithTag("selector").assertScreenshot("/screenshots/macos-internal-subtitle-selected.png")
        onNodeWithText(media[1].originalTitle, useUnmergedTree = true).performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 10_000) { state.presentationFlow.value.selected == media[1] }
        runOnIdle { assertEquals(media[1], selector.selected.value) }
    }
}
