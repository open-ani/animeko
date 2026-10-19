/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import android.graphics.Rect
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.player.ChunkState
import me.him188.ani.app.domain.media.player.staticMediaCacheProgressState
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.platform.DeviceOrientation
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.videoplayer.ui.ControllerVisibility
import me.him188.ani.app.videoplayer.ui.MutablePlayerFullscreenState
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.gesture.NoOpLevelController
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.openani.mediamp.exoplayer.ExoPlayerMediampPlayer
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Checks the real Android video surface with synthetic orientation and cutout insets.
 * The player composition stays alive across layout changes; no media or network is needed.
 */
class EpisodeVideoCenteringTest {
    @BeforeTest
    fun setUp() {
        startKoin {
            modules(module {
                single<SettingsRepository> { PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences())) }
            })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun surfaceRemainsCenteredAfterPortraitCutout() = checkSurfaceBounds(portraitCutout = 144)

    @Test
    fun surfaceRemainsCenteredWithoutCutout() = checkSurfaceBounds(portraitCutout = 0)

    private fun checkSurfaceBounds(portraitCutout: Int) = runAniComposeUiTest {
        var orientation by mutableStateOf(DeviceOrientation.PORTRAIT)
        lateinit var composeView: View
        var observedCutout = -1
        var viewport = Rect()
        setContent {
            ProvideCompositionLocalsForPreview {
                val window = remember(orientation) { PlatformWindow(orientation, true) }
                CompositionLocalProvider(
                    LocalIsPreviewing provides false,
                    LocalPlatformWindow provides window,
                ) {
                    composeView = LocalView.current
                    val topCutout = WindowInsets.displayCutout.getTop(LocalDensity.current)
                    SideEffect { observedCutout = topCutout }
                    val landscape = orientation == DeviceOrientation.LANDSCAPE
                    Box(
                        Modifier.size(
                            width = if (landscape) 240.dp else 120.dp,
                            height = if (landscape) 120.dp else 240.dp,
                        ).onGloballyPositioned {
                            val bounds = it.boundsInWindow()
                            viewport = Rect(
                                bounds.left.roundToInt(), bounds.top.roundToInt(),
                                bounds.right.roundToInt(), bounds.bottom.roundToInt(),
                            )
                        },
                    ) {
                        Player()
                    }
                }
            }
        }

        fun dispatchCutout(top: Int, left: Int = 0, right: Int = 0) {
            ViewCompat.dispatchApplyWindowInsets(
                composeView,
                WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(left, top, right, 0))
                    .build(),
            )
        }

        runOnIdle { dispatchCutout(top = portraitCutout) }
        waitForIdle()
        runOnIdle { assertEquals(portraitCutout, observedCutout, "Portrait cutout reaches Compose") }
        val surface = runOnIdle { composeView.surfaceViews().single() }

        fun assertSurfaceBounds() {
            runOnIdle {
                assertSame(surface, composeView.surfaceViews().single(), "Orientation preserves the video surface")
                val location = IntArray(2)
                surface.getLocationInWindow(location)
                val actual = Rect(location[0], location[1], location[0] + surface.width, location[1] + surface.height)
                assertEquals(viewport, actual, "Video surface fills the player viewport in $orientation")
            }
        }

        assertSurfaceBounds()
        runOnIdle {
            orientation = DeviceOrientation.LANDSCAPE
            dispatchCutout(top = 0, left = portraitCutout)
        }
        waitForIdle()
        assertSurfaceBounds()
        runOnIdle {
            orientation = DeviceOrientation.PORTRAIT
            dispatchCutout(top = portraitCutout)
        }
        waitForIdle()
        assertSurfaceBounds()
        runOnIdle {
            orientation = DeviceOrientation.LANDSCAPE
            dispatchCutout(top = 0, right = portraitCutout)
        }
        waitForIdle()
        assertSurfaceBounds()
    }

    private fun View.surfaceViews(): Sequence<SurfaceView> = sequence {
        if (this@surfaceViews is SurfaceView) yield(this@surfaceViews)
        if (this@surfaceViews is ViewGroup) {
            for (child in children) yieldAll(child.surfaceViews())
        }
    }

    @Composable
    private fun Player() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val player = remember { ExoPlayerMediampPlayer(context, scope.coroutineContext) }
        DisposableEffect(player) { onDispose { player.close() } }
        EpisodeVideoImpl(
            playerState = player,
            expanded = true,
            hasNextEpisode = false,
            onClickNextEpisode = {},
            playerControllerState = remember { PlayerControllerState(ControllerVisibility.Invisible) },
            title = {},
            danmakuHost = {},
            danmakuEnabled = false,
            onToggleDanmaku = {},
            videoLoadingStateFlow = remember {
                MutableStateFlow(VideoLoadingState.Succeed(MediaCacheEngineKey.Anitorrent))
            },
            fullscreenState = remember { MutablePlayerFullscreenState(true) },
            danmakuEditor = {},
            onClickScreenshot = null,
            detachedProgressSlider = {},
            sidebarVisible = false,
            onToggleSidebar = {},
            progressSliderState = remember {
                PlayerProgressSliderState({ 0L }, { 100_000L }, { persistentListOf() }, {}, {})
            },
            cacheProgressInfoFlow = staticMediaCacheProgressState(ChunkState.NONE).flow,
            audioController = NoOpLevelController,
            brightnessController = NoOpLevelController,
            playbackSpeedControllerState = null,
            videoAspectRatioControllerState = null,
            leftBottomTips = {},
            fullscreenSwitchButton = {},
            sideSheets = {},
            shareData = MediaShareData(null, null),
            onClickCache = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
