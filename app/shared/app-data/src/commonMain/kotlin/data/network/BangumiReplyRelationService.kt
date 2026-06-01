/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.episode.EpisodeComment
import me.him188.ani.app.data.models.episode.EpisodeCommentSource
import me.him188.ani.datasources.bangumi.next.apis.EpisodeBangumiNextApi
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 直接问 Bangumi "这条楼内回复回复的是谁" (`relatedID`), 补上服务端合并接口不给的那层关系.
 *
 * 服务端的 `listEpisodeComments` 只给评论与回复本身, 没有回复之间的指向 (见
 * [EpisodeComment.replyToCommentId]), 而电视上的完整评论弹窗要用它显示"回复 @某人".
 * 缺了这层关系时还有正文引用的推断兜着 (见 `quotedAuthorNicknameOrNull`), 但用户把引用删掉再发
 * 的那种就认不出来了 —— 所以能连上 Bangumi 就用它给的真值.
 *
 * 对号入座靠的是 [EpisodeComment.sourceCommentId] 与 Bangumi 那边 `id` 是同一个值 (服务端合并时
 * 就是照原样带过来的). 哪天服务端换了这个字段的格式, 这里会静悄悄地一条都对不上 —— 表现是"回复
 * @某人"退回到只由正文引用推断, 不会出错, 但排查时记得先看这一点.
 *
 * **连不上就当没有这回事**: 不额外做连通性探测 (那是又一个会超时的请求), 直接试, 失败就记下
 * [UNREACHABLE_BACKOFF] 这么久不再试 —— 否则每翻一页都要为一个注定失败的请求等上一次超时.
 * 单次也压了 [FETCH_TIMEOUT], 评论列表不能为了这层装饰卡在那里.
 */
class BangumiReplyRelationService internal constructor(
    private val nowMillis: () -> Long,
    /** 取一集的关系表; `null` = 这次没取到. 抽成参数是为了让缓存与退避那部分可测. */
    private val fetchRelations: suspend (episodeId: Long) -> Map<String, String>?,
) {
    constructor(
        client: ScopedHttpClient,
        ioDispatcher: CoroutineContext = Dispatchers.IO_,
    ) : this(
        nowMillis = { currentTimeMillis() },
        fetchRelations = bangumiFetcher(client, ioDispatcher),
    )

    private val mutex = Mutex()

    /** 上次取到的那一集的关系表: 回复的 id → 被回复的那条回复的 id. 只留一集 (用户一次看一集). */
    private var cachedEpisodeId: Long? = null
    private var cachedRelations: Map<String, String> = emptyMap()

    /** 在这个时刻之前不再尝试请求, 见类文档. */
    private var unreachableUntilMillis: Long = 0

    /**
     * 给一页评论里的楼内回复补上 [EpisodeComment.replyToCommentId]; 补不了就原样返回.
     */
    suspend fun fillInReplyTargets(episodeId: Long, comments: List<EpisodeComment>): List<EpisodeComment> {
        if (comments.none { it.mayHaveUnknownReplyTarget() }) return comments
        val relations = relationsOf(episodeId)
        if (relations.isEmpty()) return comments
        return comments.map { comment ->
            if (comment.mayHaveUnknownReplyTarget()) {
                comment.copy(replies = comment.replies.map { it.withReplyTarget(relations) })
            } else {
                comment
            }
        }
    }

    /** 已经从正文引用认出来的不动: 那是同一件事的另一个来源, 换成这里的没有意义. */
    private fun EpisodeComment.withReplyTarget(relations: Map<String, String>): EpisodeComment {
        if (replyToCommentId != null) return this
        val target = relations[sourceCommentId] ?: return this
        return copy(replyToCommentId = target)
    }

    /**
     * 只有 Bangumi 来源、且楼内有多条回复时才谈得上"回复了谁"; 全都已经认出来了也不必再问.
     */
    private fun EpisodeComment.mayHaveUnknownReplyTarget(): Boolean =
        source == EpisodeCommentSource.BANGUMI &&
                replies.size >= 2 &&
                replies.any { it.replyToCommentId == null }

    private suspend fun relationsOf(episodeId: Long): Map<String, String> = mutex.withLock {
        if (cachedEpisodeId == episodeId) return cachedRelations
        if (nowMillis() < unreachableUntilMillis) return emptyMap()
        val relations = fetchRelations(episodeId)
        if (relations == null) {
            // 失败按"连不上"处理并退避. 注意别把失败写进 cachedRelations —— 那会把这一集钉成"没有关系",
            // 连退避到期后的重试都不会再发生
            unreachableUntilMillis = nowMillis() + UNREACHABLE_BACKOFF.inWholeMilliseconds
            return emptyMap()
        }
        unreachableUntilMillis = 0
        cachedEpisodeId = episodeId
        cachedRelations = relations
        return relations
    }

    private companion object {
        /** 失败之后这么久内不再试 */
        private val UNREACHABLE_BACKOFF: Duration = 10.minutes
    }
}

/**
 * 真正去 next.bgm.tv 取关系的那份实现.
 *
 * @return `null` 表示这次没取到 (连不上 / 超时 / 这集在 Bangumi 上不存在), 交给调用方退避.
 */
private fun bangumiFetcher(
    client: ScopedHttpClient,
    ioDispatcher: CoroutineContext,
): suspend (Long) -> Map<String, String>? {
    val api = ApiInvoker(client) { EpisodeBangumiNextApi(BANGUMI_NEXT_API_HOST, it) }
    return { episodeId ->
        withContext(ioDispatcher) {
            runCatching {
                withTimeoutOrNull(FETCH_TIMEOUT) {
                    api {
                        getEpisodeComments(episodeId).body().flatMap { comment ->
                            comment.replies.mapNotNull { reply ->
                                // relatedID 指向主楼 (或自身/缺失) 时只是普通的楼内回复, 不算指向某条回复
                                reply.relatedID
                                    .takeIf { it != 0 && it != reply.mainID && it != comment.id && it != reply.id }
                                    ?.let { reply.id.toString() to it.toString() }
                            }
                        }.toMap()
                    }
                }
            }.onFailure {
                logger.debug(it) { "Failed to fetch Bangumi reply relations for episode $episodeId" }
            }.getOrNull()
        }
    }
}

private const val BANGUMI_NEXT_API_HOST = "https://next.bgm.tv"

/** 单次请求最多等这么久: 补不上只是少一行字, 不值得让评论列表干等 */
private val FETCH_TIMEOUT: Duration = 3.seconds

private val logger = logger<BangumiReplyRelationService>()
