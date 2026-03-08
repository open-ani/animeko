/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContext
import me.him188.ani.app.domain.mediasource.web.WebCaptchaCoordinator
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.WebCaptchaRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaSolveResult
import me.him188.ani.app.ui.mediaselect.selector.WebSource
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaSelectorCaptchaStateTest {
    @Test
    fun `captcha state is exposed to simple mode presentation`() = runTest {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Cloudflare,
        )
        val stateScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val state = createState(
            sourceResults = listOf(
                FakeMediaSourceFetchResult(
                    initialState = MediaSourceFetchState.CaptchaRequired(request, id = 1),
                ),
            ),
            backgroundScope = stateScope,
        )
        try {
            val presentation = state.presentationFlow.first { !it.isPlaceholder }
            val source = presentation.webSources.single()

            assertEquals(request, source.captchaRequest)
            assertEquals("需要处理Cloudflare 验证", source.captchaMessage)
            assertTrue(source.isCaptchaRequired)
            assertFalse(source.isError)
        } finally {
            stateScope.cancel()
        }
    }

    @Test
    fun `resolveCaptcha delegates to coordinator and returns solved state`() = runTest {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.CloudflareTurnstile,
        )
        val requests = mutableListOf<WebCaptchaRequest>()
        val coordinator = FakeWebCaptchaCoordinator { incoming ->
            requests += incoming
            WebCaptchaSolveResult.Solved(
                finalUrl = request.pageUrl,
                cookies = emptyList(),
            )
        }
        val stateScope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob())
        val state = createState(
            sourceResults = emptyList(),
            coordinator = coordinator,
            backgroundScope = stateScope,
        )
        try {
            val resolved = state.resolveCaptcha(
                WebSource(
                    instanceId = "source-1",
                    mediaSourceId = "source-1",
                    iconUrl = "",
                    name = "source-1",
                    channels = emptyList(),
                    isLoading = false,
                    isError = false,
                    isPreferred = false,
                    captchaRequest = request,
                ),
            )

            assertTrue(resolved)
            assertEquals(listOf(request), requests)
        } finally {
            stateScope.cancel()
        }
    }

    @Test
    fun `detailed mode presentation keeps captcha action separate from failure`() {
        val request = WebCaptchaRequest(
            mediaSourceId = "source-1",
            pageUrl = "https://example.com/search",
            kind = WebCaptchaKind.Image,
        )

        val presentation = MediaSourceResultPresentation(
            instanceId = "source-1",
            mediaSourceId = "source-1",
            state = MediaSourceFetchState.CaptchaRequired(request, id = 2),
            info = MediaSourceInfo(displayName = "source-1"),
            kind = MediaSourceKind.WEB,
            totalCount = 0,
            isPreferred = false,
        )

        assertTrue(presentation.isCaptchaRequired)
        assertFalse(presentation.isFailedOrAbandoned)
        assertEquals("需要处理图片验证码", presentation.captchaMessage)
        assertIs<WebCaptchaRequest>(presentation.captchaRequest)
    }

    private fun createState(
        sourceResults: List<MediaSourceFetchResult>,
        backgroundScope: CoroutineScope,
        coordinator: WebCaptchaCoordinator = FakeWebCaptchaCoordinator { WebCaptchaSolveResult.Unsupported },
        mediaList: List<Media> = emptyList(),
    ): MediaSelectorState {
        return MediaSelectorState(
            mediaSelector = DefaultMediaSelector(
                mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
                mediaListNotCached = MutableStateFlow(mediaList),
                savedUserPreference = flowOf(MediaPreference.Empty),
                savedDefaultPreference = flowOf(MediaPreference.Empty),
                mediaSelectorSettings = flowOf(MediaSelectorSettings.Default),
            ),
            mediaSourceFetchResults = flowOf(sourceResults),
            mediaSourceInfoProvider = createTestMediaSourceInfoProvider(),
            preferredWebMediaSource = flowOf(null),
            backgroundScope = backgroundScope,
            webCaptchaCoordinator = coordinator,
        )
    }
}

private class FakeMediaSourceFetchResult(
    override val instanceId: String = "source-1",
    override val mediaSourceId: String = instanceId,
    override val sourceInfo: MediaSourceInfo = MediaSourceInfo(displayName = instanceId),
    override val kind: MediaSourceKind = MediaSourceKind.WEB,
    initialState: MediaSourceFetchState,
    initialResults: List<Media> = emptyList(),
) : MediaSourceFetchResult {
    override val state = MutableStateFlow(initialState)
    override val results: Flow<List<Media>> = MutableStateFlow(initialResults)

    override fun restart() {
        state.value = MediaSourceFetchState.Working
    }

    override fun enable() {
        if (state.value is MediaSourceFetchState.Disabled) {
            state.value = MediaSourceFetchState.Idle
        }
    }
}

private class FakeWebCaptchaCoordinator(
    private val interactiveSolve: suspend (WebCaptchaRequest) -> WebCaptchaSolveResult,
) : WebCaptchaCoordinator {
    override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
        return WebCaptchaSolveResult.Unsupported
    }

    override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
        return interactiveSolve(request)
    }
}
