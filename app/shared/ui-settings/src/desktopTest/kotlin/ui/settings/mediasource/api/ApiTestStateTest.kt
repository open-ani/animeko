/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.domain.mediasource.api.ApiDetail
import me.him188.ani.app.domain.mediasource.api.ApiMediaSource
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiPlayback
import me.him188.ani.app.domain.mediasource.api.ApiRequest
import me.him188.ani.app.domain.mediasource.api.ApiSearch
import me.him188.ani.app.ui.framework.runComposeStateTest
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ApiTestStateTest {
    private val arguments = ApiMediaSourceArguments(
        name = "Test API",
        search = ApiSearch(
            request = ApiRequest(url = "https://api.test/search?q={keyword}"),
            subjectUrl = "https://site.test/{subjectId}",
        ),
        detail = ApiDetail(
            request = ApiRequest(url = "https://api.test/detail/{subjectId}"),
            episodeUrl = "https://site.test/{subjectId}/{episodeId}",
        ),
        playback = ApiPlayback(request = ApiRequest(url = "https://api.test/play/{episodeId}")),
    )

    /**
     * 两个条目, 每个条目两条线路各一集. 记录请求路径以断言请求次数.
     */
    private class FakeSite(private val failDetail: Boolean = false) {
        val calls = mutableListOf<String>()
        val client = HttpClient(
            MockEngine { request ->
                val path = request.url.encodedPath
                calls += path
                val body = when {
                    path == "/search" -> """[{"id":1,"title":"Title"},{"id":2,"title":"Title 2"}]"""
                    path.startsWith("/detail/") -> {
                        if (failDetail) throw IOException("offline")
                        """{"sources":[
                            {"id":"A","name":"Main","episodes":[{"id":9,"title":"One","number":1}]},
                            {"id":"B","name":"Backup","episodes":[{"id":10,"title":"One","number":1}]}
                        ]}"""
                    }

                    path.startsWith("/play/") -> """{"url":"https://video.test/${path.substringAfterLast('/')}.m3u8"}"""
                    else -> error("Unexpected request $path")
                }
                respond(body, headers = headersOf("Content-Type", "application/json"))
            },
        )

        fun count(path: String) = calls.count { it == path }
    }

    private fun TestScope.createState(
        site: FakeSite,
        argumentsState: MutableState<ApiMediaSourceArguments?>,
    ) = ApiTestState(
        argumentsState = argumentsState,
        createSource = { args ->
            ApiMediaSource(
                mediaSourceId = "test",
                config = MediaSourceConfig(
                    serializedArguments = Json.encodeToJsonElement(ApiMediaSourceArguments.serializer(), args),
                ),
                client = site.client.asScopedHttpClient(),
            )
        },
        backgroundScope = backgroundScope,
    )

    @Test
    fun `search chains into episodes, selection drives detail requests, and video resolves with the same config`() =
        runComposeStateTest {
            val site = FakeSite()
            val argumentsState = mutableStateOf<ApiMediaSourceArguments?>(arguments)
            val state = createState(site, argumentsState)
            state.searchKeyword = "Title"
            val observer = backgroundScope.launch { state.observeChanges() }

            val loaded = state.presentation.first { it.episodeResult is ApiTestEpisodeResult.Success }
            assertEquals(2, assertIs<ApiTestSubjectResult.Success>(loaded.subjectResult).subjects.size)
            assertEquals(0, loaded.selectedSubjectIndex)
            assertEquals(listOf("Main", "Backup"), loaded.channels.map { it.name })
            assertEquals(listOf("/search", "/detail/1"), site.calls)

            state.selectSubject(1)
            val second = state.presentation.first {
                it.selectedSubjectIndex == 1 && it.episodeResult is ApiTestEpisodeResult.Success
            }
            assertEquals(listOf("/search", "/detail/1", "/detail/2"), site.calls)

            state.selectChannel(1)
            val channel = assertNotNull(state.presentation.first { it.selectedChannelIndex == 1 }.selectedChannel)
            state.resolveVideo(assertNotNull(second.selectedSubject), channel, channel.episodes.single())
            val resolved = state.presentation.first { it.videoResult is ApiTestVideoResult.Success }
            assertEquals(
                "https://video.test/10.m3u8",
                assertIs<ApiTestVideoResult.Success>(resolved.videoResult).video.m3u8Url,
            )

            state.restartVideoResolution()
            state.presentation.first { it.videoResult == null }
            state.presentation.first { it.videoResult is ApiTestVideoResult.Success }
            assertEquals(2, site.count("/play/10"))

            // 改配置: 条目搜索重来一次, 保留选中的条目, 线路也只重新请求一次
            val before = site.calls.size
            argumentsState.value = arguments.copy(name = "Renamed")
            Snapshot.sendApplyNotifications()
            state.presentation.first { it.subjectResult == null }
            val afterChange = state.presentation.first {
                it.subjectResult is ApiTestSubjectResult.Success && it.episodeResult is ApiTestEpisodeResult.Success
            }
            assertEquals(1, afterChange.selectedSubjectIndex)
            assertEquals(listOf("/search", "/detail/2"), site.calls.drop(before))

            // 非法配置: 不发请求
            val beforeInvalid = site.calls.size
            argumentsState.value = ApiMediaSourceArguments.Default
            Snapshot.sendApplyNotifications()
            val invalid = state.presentation.first { it.subjectResult is ApiTestSubjectResult.InvalidConfig }
            assertEquals(null, invalid.selectedSubject)
            assertEquals(beforeInvalid, site.calls.size)

            observer.cancel()
        }

    @Test
    fun `network failures are reported as api errors`() = runComposeStateTest {
        val site = FakeSite(failDetail = true)
        val state = createState(site, mutableStateOf(arguments))
        state.searchKeyword = "Title"
        val observer = backgroundScope.launch { state.observeChanges() }

        val failed = state.presentation.first { it.episodeResult != null }
        assertIs<RepositoryNetworkException>(assertIs<ApiTestEpisodeResult.ApiError>(failed.episodeResult).exception)

        observer.cancel()
    }
}
