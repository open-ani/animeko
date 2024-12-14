/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.rss.test

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceEngine
import me.him188.ani.app.domain.mediasource.rss.RssSearchConfig
import me.him188.ani.app.domain.mediasource.rss.RssSearchQuery
import me.him188.ani.app.ui.settings.mediasource.AbstractMediaSourceTestState
import me.him188.ani.app.ui.settings.mediasource.BackgroundSearcher
import me.him188.ani.app.ui.settings.mediasource.rss.EditRssMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.rss.detail.RssViewingItem
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import kotlin.coroutines.cancellation.CancellationException

/**
 * 测试功能的状态
 *
 * @see EditRssMediaSourceState
 * @see RssTestResult
 * @see RssTestData
 * @see RssItemPresentation.compute
 */
@Stable
class RssTestPaneState(
    private val searchConfigState: State<RssSearchConfig>,
    private val engine: RssMediaSourceEngine,
    backgroundScope: CoroutineScope,
) : AbstractMediaSourceTestState() {
    val showPage by derivedStateOf {
        searchConfigState.value.searchUrl.contains("{page}")
    }
    var pageString: String by mutableStateOf("0")
    private val page by derivedStateOf {
        pageString.toIntOrNull()
    }
    val pageIsError by derivedStateOf {
        page == null
    }

    // 持有 state 以保证切换页面时仍然有滚动状态
    val pagerState = PagerState(RssTestPaneTab.Overview.ordinal) { RssTestPaneTab.entries.size }
    val overallTabGridState = LazyGridState()
    val rssTabGridState = LazyStaggeredGridState()
    val finalResultsTabGridState = LazyStaggeredGridState()

    ///////////////////////////////////////////////////////////////////////////
    // searching
    ///////////////////////////////////////////////////////////////////////////

    var viewingItem: RssViewingItem? by mutableStateOf(null)

    fun viewDetails(media: Media) {
        viewingItem = RssViewingItem.ViewingMedia(media)
    }

    fun viewDetails(rssItem: RssItemPresentation) {
        viewingItem = RssViewingItem.ViewingRssItem(rssItem)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Testing
    ///////////////////////////////////////////////////////////////////////////
    val testDataState = derivedStateOf {
        val finalKeyword = searchKeyword.ifEmpty { searchKeywordPlaceholder }
        val searchUrl = searchConfigState.value
        RssTestData(
            page = page,
            keyword = finalKeyword,
            searchUrl,
        )
    }

    val searcher = BackgroundSearcher<RssTestData, RssTestResult>(
        backgroundScope,
        search = { testData ->
            val query = RssSearchQuery(
                subjectName = testData.keyword,
                episodeSort = EpisodeSort(sort),
                allSubjectNames = setOf(testData.keyword),
                episodeName = null,
                episodeEp = null,
            )
            viewingItem = null
            launchRequestInBackground {
                doSearch(testData, query)
            }
        },
    )

    // 只会抛出 CancellationException
    private suspend fun doSearch(
        testData: RssTestData,
        query: RssSearchQuery,
    ): RssTestResult {
        return try {
            convertResult(
                engine.search(
                    testData.searchConfig, query, testData.page,
                    mediaSourceId = "test",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is RepositoryException) {
                RssTestResult.ApiError(e)
            } else {
                RssTestResult.UnknownError(e)
            }
        }
    }

    private fun convertResult(result: RssMediaSourceEngine.Result): RssTestResult {
        val (encodedUrl, query, document, channel, matchedMediaList) = result
        document ?: return RssTestResult.EmptyResult
        channel ?: return RssTestResult.EmptyResult
        matchedMediaList ?: return RssTestResult.EmptyResult

        return RssTestResult.Success(
            encodedUrl.toString(),
            channel,
            channel.items.map {
                RssItemPresentation.compute(it, searchConfigState.value, query)
            },
            matchedMediaList,
            origin = document,
        )
    }
}

/**
 * 用于测试的数据
 */
@Serializable
data class RssTestData(
    val page: Int?,
    val keyword: String,
    val searchConfig: RssSearchConfig,
)
