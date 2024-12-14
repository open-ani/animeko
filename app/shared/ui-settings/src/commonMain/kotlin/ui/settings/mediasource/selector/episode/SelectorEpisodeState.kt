/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.selector.episode

import androidx.compose.runtime.*
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.platform.Context
import me.him188.ani.app.ui.settings.mediasource.BackgroundSearcher
import me.him188.ani.app.ui.settings.mediasource.launchCollectedInBackground
import me.him188.ani.app.ui.settings.mediasource.selector.test.SelectorTestEpisodePresentation
import me.him188.ani.datasources.api.matcher.WebVideo
import me.him188.ani.datasources.api.matcher.WebViewConfig
import me.him188.ani.datasources.api.matcher.videoOrNull
import me.him188.ani.utils.platform.Uuid
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 测试 [me.him188.ani.datasources.api.matcher.WebVideoMatcher]
 */
@Stable
class SelectorEpisodeState(
    private val itemState: State<SelectorTestEpisodePresentation?>,
    /**
     * null means loading. Should finally have one.
     */
    matchVideoConfigState: State<SelectorSearchConfig.MatchVideoConfig?>,
    /**
     * null means loading. Should finally have one.
     */
    private val webViewVideoExtractor: State<WebViewVideoExtractor?>,
    private val engine: SelectorMediaSourceEngine,
    backgroundScope: CoroutineScope,
    context: Context,
    flowDispatcher: CoroutineContext = Dispatchers.Default,
) {
    private var _lastNonNullId: Uuid = Uuid.Companion.random() // 当取消选择时, 仍然需要保持 ID, 才能有 container transform 动画
    val lastNonNullId by derivedStateOf {
        itemState.value?.id?.also { _lastNonNullId = it } ?: _lastNonNullId
    }

    val episodeName: String by derivedStateOf { itemState.value?.name ?: "" }
    val episodeUrl: String by derivedStateOf { itemState.value?.playUrl ?: "" }

    /**
     * 该页面的所有链接
     */
    val searcher =
        BackgroundSearcher(
            backgroundScope,
            testDataState = derivedStateOf {
                Pair(itemState.value?.playUrl, webViewVideoExtractor.value)
            },
        ) { (episodeUrl, extractor) ->
            // Dot trigger re-fetch on config change.
            val config = matchVideoConfigState.value
            launchCollectedInBackground<SelectorTestWebUrl, _>(
                updateState = { SelectorEpisodeResult.InProgress(it) },
            ) { flow ->
                try {
                    if (episodeUrl != null && extractor != null && config != null) {
                        withTimeoutOrNull(30.seconds) { // timeout considered as success
                            extractor.getVideoResourceUrl(
                                context,
                                episodeUrl,
                                WebViewConfig.Empty.copy(config.cookies.lines().filter { it.isNotBlank() }),
                            ) {
                                val shouldLoadPage = engine.shouldLoadPage(it, config)
                                collect(SelectorTestWebUrl(it, didLoadNestedPage = shouldLoadPage))

                                if (shouldLoadPage) {
                                    WebViewVideoExtractor.Instruction.LoadPage
                                } else {
                                    WebViewVideoExtractor.Instruction.Continue
                                }
                            }
                        }
                    }
                    SelectorEpisodeResult.Success(flow)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    SelectorEpisodeResult.UnknownError(e)
                }
            }
        }

    @Immutable
    @Stable
    data class MatchResult(
        val webUrl: SelectorTestWebUrl,
        val video: WebVideo?,
    ) {
        val originalUrl get() = webUrl.url
        val parsedUrl = runCatching { Url(originalUrl) }.getOrNull()

        // ui
        val key get() = originalUrl

        @Stable
        fun isMatchedVideo() = video != null

        val highlight get() = isMatchedVideo() || webUrl.didLoadNestedPage
    }

    val isSearchingInProgress get() = searcher.isSearching

    /**
     * 不断更新的匹配结果
     */
    val rawMatchResults: Flow<List<MatchResult>> by derivedStateOf {
        val matchVideoConfig = matchVideoConfigState.value ?: return@derivedStateOf emptyFlow()
        val searchResult = searcher.searchResult ?: return@derivedStateOf emptyFlow()
        val flow = when (searchResult) {
            is SelectorEpisodeResult.ApiError,
            is SelectorEpisodeResult.UnknownError,
            is SelectorEpisodeResult.InvalidConfig,
                -> return@derivedStateOf emptyFlow()

            is SelectorEpisodeResult.InProgress -> searchResult.flow
            is SelectorEpisodeResult.Success -> searchResult.flow
        }

        flow.map { list ->
            list.asSequence()
                .map { original ->
                    MatchResult(original, engine.matchWebVideo(original.url, matchVideoConfig).videoOrNull)
                }
                .distinctBy { it.key } // O(n) extra space, O(1) time
                .toMutableList() // single list instance construction
                .apply {
                    // sort in-place for better performance
                    sortByDescending { it.isMatchedVideo() } // 优先展示匹配的
                }
        }.flowOn(flowDispatcher) // possibly significant computation
    }

    val filteredResults: Flow<List<MatchResult>> by derivedStateOf {
        // read states in UI thread
        val hideImages = hideImages
        val hideCss = hideCss
        val hideScripts = hideScripts
        val hideData = hideData

        rawMatchResults.map { list ->
            // In background
            list.filter { shouldIncludeUrl(it, hideImages, hideCss, hideScripts, hideData) }
        }.flowOn(flowDispatcher)
    }

    var hideImages: Boolean by mutableStateOf(true)
    var hideCss: Boolean by mutableStateOf(true)
    var hideScripts: Boolean by mutableStateOf(true)
    var hideData: Boolean by mutableStateOf(true)

    companion object {
        private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "ico", "svg")
        private val cssExtensions = setOf("css", "ttf", "woff2")
        private val scriptsExtensions = setOf("js", "wasm")

        private fun shouldIncludeUrl(
            result: MatchResult,
            hideImages: Boolean,
            hideCss: Boolean,
            hideScripts: Boolean,
            hideData: Boolean,
        ): Boolean {
            val lastSegment = result.parsedUrl?.pathSegments?.lastOrNull() ?: return true
            val extension = lastSegment.substringAfterLast('.', "")
            if (extension.isNotEmpty()) {
                if (hideImages) {
                    if (imageExtensions.any { extension.equals(it, ignoreCase = true) }) return false
                }
                if (hideCss) {
                    if (cssExtensions.any { extension.equals(it, ignoreCase = true) }) return false
                }
                if (hideScripts) {
                    if (scriptsExtensions.any { extension.equals(it, ignoreCase = true) }) return false
                }
            }
            if (hideData) {
                if (result.originalUrl.startsWith("data:")) return false
            }
            return true
        }
    }
}

