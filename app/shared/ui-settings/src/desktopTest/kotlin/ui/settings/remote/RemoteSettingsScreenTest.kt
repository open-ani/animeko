/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.data.models.preference.DanmakuCacheStrategy
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.domain.settings.remote.OperationResult
import me.him188.ani.app.domain.settings.remote.PreferenceRequest
import me.him188.ani.app.domain.settings.remote.RemoteOperationPayload
import me.him188.ani.app.domain.settings.remote.RemotePreference
import me.him188.ani.app.domain.settings.remote.RemotePreferenceRegistry
import me.him188.ani.app.domain.settings.remote.RemoteSettingsRevision
import me.him188.ani.app.domain.settings.remote.RemoteSettingsSession
import me.him188.ani.app.domain.settings.remote.SettingsSnapshot
import me.him188.ani.app.domain.settings.remote.VersionedValue
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.framework.assertScreenshot
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.remote.settings.RemoteSettingsLink
import me.him188.ani.remote.settings.RemoteSettingsProtocol
import me.him188.ani.remote.settings.generated.models.PingResponse

class RemoteSettingsScreenTest {
    @Test
    fun unconnectedRouteScansAndSystemBackReturnsToOrigin() = runAniComposeUiTest {
        val vm = RemoteSettingsViewModel()
        var exited by mutableStateOf(false)
        lateinit var backDispatcher: OnBackPressedDispatcher
        try {
            setContent {
                ProvideCompositionLocalsForPreview {
                    val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                    SideEffect { backDispatcher = owner.onBackPressedDispatcher }
                    if (!exited)
                        RemoteSettingsScreen(
                            vm,
                            onNavigateBack = { exited = true },
                            scanner = { _, _ -> Text("Remote settings scanner") },
                        )
                    else Text("Local settings")
                }
            }
            onNodeWithText("Remote settings scanner").assertIsDisplayed()
            onNodeWithTag("settings-remote-bubble").assertDoesNotExist()
            runOnIdle { backDispatcher.onBackPressed() }
            onNodeWithText("Remote settings scanner").assertDoesNotExist()
            onNodeWithText("Local settings").assertIsDisplayed()
        } finally {
            vm.backgroundScope.cancel()
        }
    }

    @Test fun chineseRemoteSettings() = verifyRemoteSettings(Locale.SIMPLIFIED_CHINESE)

    @Test fun englishRemoteSettings() = verifyRemoteSettings(Locale.ENGLISH)

    private fun verifyRemoteSettings(locale: Locale) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            runRemoteSettings(locale.language == "en")
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    private fun runRemoteSettings(english: Boolean) = runAniComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val tv = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))
        val phone = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))
        val phoneKernelBefore = runBlocking { phone.playerKernelConfig.flow.first() }
        runBlocking {
            tv.mediaCacheSettings.update {
                copy(
                    danmakuCacheStrategy = DanmakuCacheStrategy.CACHE_ON_COLLECTION_DOING_MEDIA_PLAY
                )
            }
        }
        val registry =
            RemotePreferenceRegistry(tv, RemoteSettingsRevision { name, value -> "$name:$value" })
        val writes = CopyOnWriteArrayList<String>()
        val json = RemoteSettingsProtocol.json

        val sources =
            VersionedValue(
                "sources",
                List(12) { index ->
                    MediaSourceSave(
                        "source-$index",
                        "source-$index",
                        SelectorMediaSource.FactoryId,
                        true,
                        MediaSourceConfig.Default,
                    )
                },
            )
        val key = "820f62ee-3a31-4491-b4b7-1e91f6bb12dd"
        val client =
            HttpClient(
                MockEngine { request ->
                    assertEquals("Bearer $key", request.headers[HttpHeaders.Authorization])
                    val content =
                        when (request.url.encodedPath) {
                            "/ping" ->
                                json.encodeToString(
                                    PingResponse.serializer(),
                                    PingResponse("tv-process", "6.1", "客厅电视", 1, 1, emptyList()),
                                )
                            "/state" ->
                                json.encodeToString(
                                    SettingsSnapshot.serializer(),
                                    SettingsSnapshot(
                                        registry.snapshot(),
                                        sources,
                                        VersionedValue("empty", emptyList()),
                                        VersionedValue("empty", emptyList()),
                                        emptyList(),
                                    ),
                                )
                            "/preference" -> {
                                val body =
                                    json.decodeFromString(
                                        PreferenceRequest.serializer(),
                                        (request.body as TextContent).text,
                                    )
                                registry.write(body.baseRevision, body.value)
                                writes += when (body.value) {
                                    is RemotePreference.MediaCache -> "mediaCacheSettings"
                                    is RemotePreference.PlayerKernel -> "playerKernelConfig"
                                    else -> error("Unexpected preference write")
                                }
                                json.encodeToString(
                                    OperationResult.serializer(),
                                    OperationResult(
                                        body.operationId,
                                        "succeeded",
                                        RemoteOperationPayload.Applied,
                                    ),
                                )
                            }
                            else -> error("Unexpected remote request")
                        }
                    respond(
                        content,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            ) {
                install(ContentNegotiation) { json(json) }
            }
        val session = runBlocking {
            RemoteSettingsSession.connect(
                RemoteSettingsLink("192.168.1.2", 12345, key, "6.1", ""),
                "6.1",
                scope,
                client,
            )
        }
        val vm = RemoteSettingsViewModel(session)
        var exited by mutableStateOf(0)
        val navigator =
            AniNavigator().apply {
                setBackStack(mutableStateListOf(NavRoutes.Settings(), NavRoutes.RemoteSettings()))
            }
        lateinit var backDispatcher: OnBackPressedDispatcher
        try {
            setContent {
                ProvideCompositionLocalsForPreview(darkMode = DarkMode.DARK) {
                    val backOwner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                    SideEffect { backDispatcher = backOwner.onBackPressedDispatcher }
                    CompositionLocalProvider(
                        LocalWindowInfo provides
                            object : WindowInfo {
                                override val isWindowFocused = true
                                override val containerSize = IntSize(420, 760)
                            },
                        LocalDensity provides Density(1f),
                        LocalNavigator provides navigator,
                    ) {
                        Box(Modifier.size(420.dp, 760.dp)) {
                            if (exited == 0)
                                RemoteSettingsScreen(
                                    vm,
                                    onNavigateBack = { exited++ },
                                    scanner = { _, _ ->
                                        Text("Settings scanner")
                                    },
                                )
                        }
                    }
                }
            }
            onNodeWithTag("settings-remote-bubble").assertIsDisplayed()
            mainClock.advanceTimeBy(500)
            waitForIdle()
            // 像素基线使用 Windows 系统字体；交互与持久化断言在所有 desktop 平台上执行。
            if (System.getProperty("os.name").startsWith("Windows")) {
                onNodeWithTag("remote-settings-screen")
                    .assertScreenshot(
                        "/screenshots/settings/remote-${if (english) "en" else "zh"}-windows.png"
                    )
            }
            onNodeWithTag("settings-tab-APPEARANCE").assertDoesNotExist()
            onNodeWithTag("settings-tab-PROFILE").assertDoesNotExist()
            onNodeWithContentDescription(if (english) "Scan to configure TV" else "扫码配置电视")
                .assertDoesNotExist()
            runOnIdle { vm.scanConnection("invalid-code") }
            onNodeWithText(
                    if (english) "This is not a valid TV settings QR code." else "不是有效的电视设置二维码"
                )
                .assertIsDisplayed()
            onNodeWithText(if (english) "Confirm" else "确认").performClick()
            onNodeWithTag("settings-tab-STORAGE").performScrollTo().performClick()
            onNodeWithTag("settings-danmaku-cache").performClick()
            onNodeWithText("MEDIA", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithText("NONE").performClick()
            waitUntil { writes.size == 1 && !session.busy.value }
            assertEquals(listOf("mediaCacheSettings"), writes.toList())
            assertEquals(
                DanmakuCacheStrategy.DON_NOT_CACHE,
                runBlocking { tv.mediaCacheSettings.flow.first().danmakuCacheStrategy },
            )
            assertEquals(
                DanmakuCacheStrategy.CACHE_ON_MEDIA_CACHE,
                runBlocking { phone.mediaCacheSettings.flow.first().danmakuCacheStrategy },
            )
            onNodeWithTag("settings-back-detail").performClick()
            onNodeWithText(if (english) "Exit remote settings?" else "退出远程配置？").assertDoesNotExist()
            onNodeWithTag("settings-remote-bubble").assertIsDisplayed()
            onNodeWithTag("settings-tab-PLAYER").performScrollTo().performClick()
            onNodeWithText(
                if (english) "Automatically go fullscreen on screen rotation" else "旋转屏幕时自动全屏"
            ).assertDoesNotExist()
            val kernelBefore = runBlocking { tv.playerKernelConfig.flow.first() }
            onNodeWithText(
                if (english) "Preload video enhancement shaders" else "预先加载画质增强着色器"
            ).performScrollTo().performClick()
            waitUntil { writes.size == 2 && !session.busy.value }
            assertEquals(
                !kernelBefore.exoPlayerInitEffectGraphInAdvance,
                runBlocking { tv.playerKernelConfig.flow.first().exoPlayerInitEffectGraphInAdvance },
            )
            assertEquals(phoneKernelBefore, runBlocking { phone.playerKernelConfig.flow.first() })
            onNodeWithTag("settings-back-detail").performClick()
            onNodeWithTag("settings-tab-MEDIA_SOURCE").performScrollTo().performClick()
            onNodeWithTag("media_source_item_source-11").performScrollTo().performClick()
            assertEquals(
                NavRoutes.EditMediaSource(SelectorMediaSource.FactoryId.value, "source-11"),
                navigator.backStack.last(),
            )
            navigator.popBackStack()
            onNodeWithTag("media_source_item_source-11").assertIsDisplayed()
            onNodeWithText(if (english) "Exit remote settings?" else "退出远程配置？").assertDoesNotExist()
            onNodeWithTag("settings-back-detail").performClick()
            runOnIdle { backDispatcher.onBackPressed() }
            onNodeWithText(if (english) "Exit remote settings?" else "退出远程配置？").assertIsDisplayed()
            onNodeWithText(if (english) "Continue configuring" else "继续配置").performClick()
            assertEquals(0, exited)
            onNodeWithTag("remote-settings-back").performClick()
            onNodeWithTag("remote-settings-confirm-exit").performClick()
            waitForIdle()
            onNodeWithTag("settings-remote-bubble").assertDoesNotExist()
            assertEquals(1, exited)
            assertEquals(listOf("mediaCacheSettings", "playerKernelConfig"), writes.toList())
            assertEquals(
                DanmakuCacheStrategy.CACHE_ON_MEDIA_CACHE,
                runBlocking { phone.mediaCacheSettings.flow.first().danmakuCacheStrategy },
            )
        } finally {
            vm.disconnectRemote()
            vm.backgroundScope.cancel()
            scope.cancel()
        }
    }
}
