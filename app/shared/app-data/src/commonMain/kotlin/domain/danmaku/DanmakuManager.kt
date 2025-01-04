/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.network.AniDanmakuProvider
import me.him188.ani.app.data.network.AniDanmakuSender
import me.him188.ani.app.data.network.AniDanmakuSenderImpl
import me.him188.ani.app.data.network.SendDanmakuException
import me.him188.ani.app.data.network.protocol.DanmakuInfo
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.verifiedAccessToken
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.danmaku.api.Danmaku
import me.him188.ani.danmaku.api.DanmakuFetchResult
import me.him188.ani.danmaku.api.DanmakuMatchInfo
import me.him188.ani.danmaku.api.DanmakuMatchMethod
import me.him188.ani.danmaku.api.DanmakuProvider
import me.him188.ani.danmaku.api.DanmakuProviderConfig
import me.him188.ani.danmaku.api.DanmakuSearchRequest
import me.him188.ani.danmaku.dandanplay.DandanplayDanmakuProvider
import me.him188.ani.utils.coroutines.mapAutoClose
import me.him188.ani.utils.coroutines.mapAutoCloseCollection
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 管理多个弹幕源 [DanmakuProvider]
 */
interface DanmakuManager {
    val selfId: Flow<String?>

    suspend fun fetch(
        request: DanmakuSearchRequest,
    ): CombinedDanmakuFetchResult

    @Throws(SendDanmakuException::class, CancellationException::class)
    suspend fun post(episodeId: Int, danmaku: DanmakuInfo): Danmaku
}

class CombinedDanmakuFetchResult(
    val matchInfos: List<DanmakuMatchInfo>,
    val list: Sequence<Danmaku>,
)

object DanmakuProviderLoader {
    fun load(
        config: (id: String) -> DanmakuProviderConfig,
    ): List<DanmakuProvider> {
        // 待 https://youtrack.jetbrains.com/issue/KT-65362/Cannot-resolve-declarations-from-a-dependency-when-there-are-multiple-JVM-only-project-dependencies-in-a-JVM-Android-MPP
        // 解决后, 才能切换使用 ServiceLoader, 否则 resources META-INF/services 会冲突
//        val factories = ServiceLoader.load(DanmakuProviderFactory::class.java).toList()
        val factories = listOf(
            DandanplayDanmakuProvider.Factory(),
            AniDanmakuProvider.Factory(),
        )
        return factories
            .map { factory -> factory.create(config(factory.id)) }
    }
}

class DanmakuManagerImpl(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
) : DanmakuManager, KoinComponent, HasBackgroundScope by BackgroundScope(parentCoroutineContext) {
    private val settingsRepository: SettingsRepository by inject()
    private val sessionManager: SessionManager by inject()

    private val config = settingsRepository.danmakuSettings.flow.map { config ->
        DanmakuProviderConfig(
            userAgent = getAniUserAgent(),
            useGlobal = config.useGlobal,
        )
    }

    /**
     * @see DanmakuProviderLoader
     */
    private val providers: Flow<List<DanmakuProvider>> = config.mapAutoCloseCollection { config ->
        DanmakuProviderLoader.load { config }
    }.shareInBackground(started = SharingStarted.Lazily)

    @OptIn(OpaqueSession::class)
    private val sender: Flow<AniDanmakuSender> = config.mapAutoClose { config ->
        AniDanmakuSenderImpl(
            config,
            sessionManager.verifiedAccessToken, // TODO: Handle danmaku sender errors 
            backgroundScope.coroutineContext,
        )
    }.shareInBackground(started = SharingStarted.Lazily)

    private companion object {
        private val logger = logger<DanmakuManagerImpl>()
    }

    override val selfId: Flow<String?> = sender.flatMapLatest { it.selfId }

    override suspend fun fetch(
        request: DanmakuSearchRequest,
    ): CombinedDanmakuFetchResult {
        logger.info { "Search for danmaku with filename='${request.filename}'" }
        val flows = providers.first().map { provider ->
            flow {
                emit(
                    withTimeout(60.seconds) {
                        provider.fetch(request = request)
                    },
                )
            }.retry(1) {
                if (it is CancellationException && !currentCoroutineContext().isActive) {
                    // collector was cancelled
                    return@retry false
                }
                logger.error(it) { "Failed to fetch danmaku from provider '${provider.id}'" }
                true
            }.catch {
                emit(
                    listOf(
                        DanmakuFetchResult(
                            DanmakuMatchInfo(
                                provider.id,
                                0,
                                DanmakuMatchMethod.NoMatch,
                            ),
                            list = emptySequence(),
                        ),
                    ),
                )// 忽略错误, 否则一个源炸了会导致所有弹幕都不发射了
                // 必须要 emit 一个, 否则下面 .first 会出错
            }
        }
        if (flows.isEmpty()) {
            return CombinedDanmakuFetchResult(emptyList(), emptySequence())
        }
        val results = combine(flows) {
            it.asSequence().flatten().toList()
        }.first()
        return CombinedDanmakuFetchResult(
            results.map { it.matchInfo },
            results.asSequence().flatMap { it.list },
        )
    }

    override suspend fun post(episodeId: Int, danmaku: DanmakuInfo): Danmaku {
        return try {
            sender.first().send(episodeId, danmaku)
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
