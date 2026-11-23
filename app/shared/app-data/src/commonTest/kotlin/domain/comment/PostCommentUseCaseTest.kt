/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.comment

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.models.person.PersonCommentTarget
import me.him188.ani.app.data.network.AniEpisodeCommentService
import me.him188.ani.app.data.network.AniPersonCommentService
import me.him188.ani.client.apis.CharactersAniApi
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.client.apis.PersonsAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PostCommentUseCaseTest {
    @Test
    fun `send top-level comment succeeds`() = runTest {
        var postedEpisodeId: Long? = null
        var postedContent: String? = null
        val sender = PostCommentUseCaseImpl(
            createCommentService(
                onCreateEpisodeComment = { episodeId, content ->
                    postedEpisodeId = episodeId
                    postedContent = content
                },
            ),
            createPersonCommentService(),
            context = coroutineContext,
        )

        val result = sender(CommentContext.Episode(1, 2L), COMMENT_CONTENT)

        assertIs<CommentSendResult.Ok>(result)
        assertEquals(2L, postedEpisodeId)
        assertEquals(COMMENT_CONTENT, postedContent)
    }

    @Test
    fun `send reply succeeds`() = runTest {
        var replyCommentId: String? = null
        val sender = PostCommentUseCaseImpl(
            createCommentService(
                onCreateEpisodeReply = { _, commentId, _ ->
                    replyCommentId = commentId
                },
            ),
            createPersonCommentService(),
            context = coroutineContext,
        )

        val result = sender(CommentContext.EpisodeReply(1, 2L, "reply-target"), COMMENT_CONTENT)

        assertIs<CommentSendResult.Ok>(result)
        assertEquals("reply-target", replyCommentId)
    }

    @Test
    fun `comment service network error`() = runTest {
        val sender = PostCommentUseCaseImpl(
            createCommentService(onCreateEpisodeComment = { _, _ -> throw IOException() }),
            createPersonCommentService(),
            context = coroutineContext,
        )

        val result = sender(CommentContext.Episode(1, 2L), COMMENT_CONTENT)
        assertIs<CommentSendResult.NetworkError>(result)
    }

    @Test
    fun `comment service unknown error`() = runTest {
        val sender = PostCommentUseCaseImpl(
            createCommentService(onCreateEpisodeComment = { _, _ -> throw IllegalStateException("boom") }),
            createPersonCommentService(),
            context = coroutineContext,
        )

        val result = sender(CommentContext.Episode(1, 2L), COMMENT_CONTENT)
        assertIs<CommentSendResult.UnknownError>(result)
    }

    @Test
    fun `send person comment goes to the person service`() = runTest {
        var postedTarget: PersonCommentTarget? = null
        var postedContent: String? = null
        val sender = PostCommentUseCaseImpl(
            createCommentService(onCreateEpisodeComment = { _, _ -> error("episode service must not be used") }),
            createPersonCommentService(
                onCreateComment = { target, content ->
                    postedTarget = target
                    postedContent = content
                },
            ),
            context = coroutineContext,
        )

        val result = sender(CommentContext.PersonComment(PersonCommentTarget.Person(7)), COMMENT_CONTENT)

        assertIs<CommentSendResult.Ok>(result)
        assertEquals(PersonCommentTarget.Person(7), postedTarget)
        assertEquals(COMMENT_CONTENT, postedContent)
    }

    @Test
    fun `send character reply goes to the person service`() = runTest {
        var repliedTarget: PersonCommentTarget? = null
        var repliedCommentId: String? = null
        val sender = PostCommentUseCaseImpl(
            createCommentService(),
            createPersonCommentService(
                onCreateReply = { target, commentId, _ ->
                    repliedTarget = target
                    repliedCommentId = commentId
                },
            ),
            context = coroutineContext,
        )

        val result = sender(
            CommentContext.PersonCommentReply(PersonCommentTarget.Character(9), "reply-target"),
            COMMENT_CONTENT,
        )

        assertIs<CommentSendResult.Ok>(result)
        assertEquals(PersonCommentTarget.Character(9), repliedTarget)
        assertEquals("reply-target", repliedCommentId)
    }

    @Test
    fun `person comment service network error`() = runTest {
        val sender = PostCommentUseCaseImpl(
            createCommentService(),
            createPersonCommentService(onCreateComment = { _, _ -> throw IOException() }),
            context = coroutineContext,
        )

        val result = sender(CommentContext.PersonComment(PersonCommentTarget.Person(1)), COMMENT_CONTENT)
        assertIs<CommentSendResult.NetworkError>(result)
    }

    @Test
    fun `invalid image error code is reported`() = runTest {
        val sender = PostCommentUseCaseImpl(
            episodeServiceResponding(HttpStatusCode.UnprocessableEntity, "HTTP 422: COMMENT_IMAGE_INVALID"),
            createPersonCommentService(),
            context = coroutineContext,
        )

        assertIs<CommentSendResult.InvalidImage>(sender(CommentContext.Episode(1, 2L), COMMENT_CONTENT))
        assertIs<CommentSendResult.InvalidImage>(
            sender(CommentContext.EpisodeReply(1, 2L, "reply-target"), COMMENT_CONTENT),
        )
    }

    @Test
    fun `too many images error code is reported`() = runTest {
        val sender = PostCommentUseCaseImpl(
            episodeServiceResponding(HttpStatusCode.UnprocessableEntity, "HTTP 422: COMMENT_IMAGE_TOO_MANY"),
            createPersonCommentService(),
            context = coroutineContext,
        )

        assertIs<CommentSendResult.TooManyImages>(sender(CommentContext.Episode(1, 2L), COMMENT_CONTENT))
    }

    @Test
    fun `image processing error code is reported`() = runTest {
        val sender = PostCommentUseCaseImpl(
            episodeServiceResponding(HttpStatusCode.UnprocessableEntity, "HTTP 422: COMMENT_IMAGE_PROCESSING"),
            createPersonCommentService(),
            context = coroutineContext,
        )

        assertIs<CommentSendResult.ImageProcessing>(sender(CommentContext.Episode(1, 2L), COMMENT_CONTENT))
    }

    @Test
    fun `person comment image error codes are reported`() = runTest {
        var body = "HTTP 422: COMMENT_IMAGE_INVALID"
        val personsApi = PersonsAniApi(
            "http://test",
            textEngine(HttpStatusCode.UnprocessableEntity) { body },
            EXPECT_SUCCESS,
        )
        val sender = PostCommentUseCaseImpl(
            createCommentService(),
            AniPersonCommentService(apiInvoker(personsApi), UnusedCharactersApi, ioDispatcher = coroutineContext),
            context = coroutineContext,
        )

        assertIs<CommentSendResult.InvalidImage>(
            sender(CommentContext.PersonComment(PersonCommentTarget.Person(7)), COMMENT_CONTENT),
        )
        body = "HTTP 422: COMMENT_IMAGE_TOO_MANY"
        assertIs<CommentSendResult.TooManyImages>(
            sender(CommentContext.PersonCommentReply(PersonCommentTarget.Person(7), "reply-target"), COMMENT_CONTENT),
        )
        body = "HTTP 422: COMMENT_IMAGE_PROCESSING"
        assertIs<CommentSendResult.ImageProcessing>(
            sender(CommentContext.PersonComment(PersonCommentTarget.Person(7)), COMMENT_CONTENT),
        )
    }

    @Test
    fun `client errors without image error code keep their mapping`() = runTest {
        suspend fun send(status: HttpStatusCode, body: String): CommentSendResult =
            PostCommentUseCaseImpl(
                episodeServiceResponding(status, body),
                createPersonCommentService(),
                context = coroutineContext,
            ).invoke(CommentContext.Episode(1, 2L), COMMENT_CONTENT)

        assertIs<CommentSendResult.UnknownError>(send(HttpStatusCode.UnprocessableEntity, "HTTP 422: Something else"))
        assertIs<CommentSendResult.UnknownError>(send(HttpStatusCode.UnprocessableEntity, ""))
        assertIs<CommentSendResult.UnknownError>(send(HttpStatusCode.BadRequest, "HTTP 400: COMMENT_IMAGE_INVALID"))
        assertIs<CommentSendResult.UnknownError>(
            send(HttpStatusCode.BadRequest, "HTTP 400: Comment content cannot be empty"),
        )
        assertIs<CommentSendResult.NetworkError>(
            send(HttpStatusCode.TooManyRequests, "Too Many Requests: comment limited to once per 30 seconds"),
        )
        assertIs<CommentSendResult.NetworkError>(send(HttpStatusCode.InternalServerError, "oops"))
    }

    /**
     * 真实的 [AniEpisodeCommentService], 所有请求都以纯文本 [body] 和 [status] 响应.
     */
    private fun TestScope.episodeServiceResponding(status: HttpStatusCode, body: String): AniEpisodeCommentService {
        return AniEpisodeCommentService(
            apiInvoker(EpisodesAniApi("http://test", textEngine(status) { body }, EXPECT_SUCCESS)),
            ioDispatcher = coroutineContext,
        )
    }

    private fun textEngine(status: HttpStatusCode, body: () -> String) = MockEngine {
        respond(body(), status, headersOf("Content-Type", "text/plain"))
    }

    private fun <Api> apiInvoker(api: Api): ApiInvoker<Api> = object : ApiInvoker<Api> {
        override suspend fun <R> invoke(action: suspend Api.() -> R): R = action(api)
    }

    private fun createPersonCommentService(
        onCreateComment: suspend (target: PersonCommentTarget, content: String) -> Unit = { _, _ -> },
        onCreateReply: suspend (target: PersonCommentTarget, commentId: String, content: String) -> Unit = { _, _, _ -> },
    ): AniPersonCommentService {
        return object : AniPersonCommentService(UnusedPersonsApi, UnusedCharactersApi) {
            override suspend fun createComment(target: PersonCommentTarget, contentBbcode: String) {
                onCreateComment(target, contentBbcode)
            }

            override suspend fun createReply(target: PersonCommentTarget, commentId: String, contentBbcode: String) {
                onCreateReply(target, commentId, contentBbcode)
            }
        }
    }

    private fun createCommentService(
        onCreateEpisodeComment: suspend (episodeId: Long, content: String) -> Unit = { _, _ -> },
        onCreateEpisodeReply: suspend (episodeId: Long, commentId: String, content: String) -> Unit = { _, _, _ -> },
    ): AniEpisodeCommentService {
        return object : AniEpisodeCommentService(UnusedEpisodesApi) {
            override suspend fun createEpisodeComment(episodeId: Long, contentBbcode: String) {
                onCreateEpisodeComment(episodeId, contentBbcode)
            }

            override suspend fun createEpisodeReply(episodeId: Long, commentId: String, contentBbcode: String) {
                onCreateEpisodeReply(episodeId, commentId, contentBbcode)
            }
        }
    }

    private companion object {
        private const val COMMENT_CONTENT = "小祈好可爱我要死了(bgm38)"

        /**
         * 与生产的 HttpClient 一致: 4xx/5xx 抛出 ClientRequestException / ServerResponseException, 由服务包装成 RepositoryException.
         */
        private val EXPECT_SUCCESS: (HttpClientConfig<*>) -> Unit = { it.expectSuccess = true }
    }
}

private object UnusedEpisodesApi : ApiInvoker<EpisodesAniApi> {
    override suspend fun <R> invoke(action: suspend EpisodesAniApi.() -> R): R {
        error("Unused in test")
    }
}

private object UnusedPersonsApi : ApiInvoker<PersonsAniApi> {
    override suspend fun <R> invoke(action: suspend PersonsAniApi.() -> R): R {
        error("Unused in test")
    }
}

private object UnusedCharactersApi : ApiInvoker<CharactersAniApi> {
    override suspend fun <R> invoke(action: suspend CharactersAniApi.() -> R): R {
        error("Unused in test")
    }
}
