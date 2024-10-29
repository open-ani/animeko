/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.MyCollectionsSettings
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.repository.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.EpisodeProgressRepository
import me.him188.ani.app.data.repository.SettingsRepository
import me.him188.ani.app.data.repository.SubjectCollectionRepository
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.userInfo
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.AuthState
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeState
import me.him188.ani.app.ui.subject.collection.progress.EpisodeListStateFactory
import me.him188.ani.app.ui.subject.collection.progress.SubjectProgressStateFactory
import me.him188.ani.datasources.api.topic.isDoneOrDropped
import me.him188.ani.utils.logging.info
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class UserCollectionsViewModel : AbstractViewModel(), KoinComponent {
    lateinit var navigator: AniNavigator

    private val subjectCollectionRepository: SubjectCollectionRepository by inject()
    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val episodeProgressRepository: EpisodeProgressRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val sessionManager: SessionManager by inject()

    val authState: AuthState = AuthState()

    private val episodeListStateFactory: EpisodeListStateFactory = EpisodeListStateFactory(
        settingsRepository,
        episodeCollectionRepository,
        episodeProgressRepository,
        backgroundScope,
    )
    private val subjectProgressStateFactory: SubjectProgressStateFactory = SubjectProgressStateFactory(
        episodeProgressRepository,
        onPlay = { subjectId: Int, episodeId ->
            navigator.navigateEpisodeDetails(subjectId, episodeId)
        },
    )

    val myCollectionsSettings: MyCollectionsSettings by settingsRepository.uiSettings.flow
        .map { it.myCollections }
        .produceState(MyCollectionsSettings.Default)

    @OptIn(OpaqueSession::class)
    val state = UserCollectionsState(
        startSearch = { subjectCollectionRepository.subjectCollectionsPager(it) },
        authState,
        sessionManager.userInfo.produceState(null),
        collectionCountsState = subjectCollectionRepository.subjectCollectionCountsFlow().produceState(null),
        episodeListStateFactory,
        subjectProgressStateFactory,
        createEditableSubjectCollectionTypeState = {
            createEditableSubjectCollectionTypeState(it)
        },
    )

    private fun createEditableSubjectCollectionTypeState(collection: SubjectCollectionInfo): EditableSubjectCollectionTypeState =
        // 必须不能有后台持续任务
        EditableSubjectCollectionTypeState(
            selfCollectionType = stateOf(collection.collectionType),
            hasAnyUnwatched = hasAnyUnwatched@{
                val collections =
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(collection.subjectId)
                        .firstOrNull() ?: return@hasAnyUnwatched true
                collections.any { !it.collectionType.isDoneOrDropped() }
            },
            onSetSelfCollectionType = {
                subjectCollectionRepository.setSubjectCollectionTypeOrDelete(collection.subjectId, it)
            },
            onSetAllEpisodesWatched = {
                episodeCollectionRepository.setAllEpisodesWatched(collection.subjectId)
            },
            backgroundScope,
        )

    override fun init() {
//        // 获取第一页, 得到数量
//        // 不要太快, 测试到的如果全并行就会导致 "在看" 没有数据, 不清楚是哪边问题.
//        launchInBackground {
//            // 按实用顺序加载
//            listOf(
//                UnifiedCollectionType.DOING,
//                UnifiedCollectionType.WISH,
//                UnifiedCollectionType.ON_HOLD,
//                UnifiedCollectionType.DONE,
//                UnifiedCollectionType.DROPPED,
//            ).forEach { type ->
//                collectionsByType(type).cache.let { cache ->
//                    if (cache.getCachedData().isEmpty()) {
//                        cache.requestMore()
//                    }
//                }
//            }
//        }

        launchInBackground {
            sessionManager.events.filter {
                when (it) {
                    SessionEvent.SwitchToGuest -> false
                    SessionEvent.TokenRefreshed -> false
                    SessionEvent.Login -> true
                    SessionEvent.Logout -> true
                }
            }.collectLatest {
                logger.info { "登录信息变更, 清空缓存" }
                // 如果有变更登录, 清空缓存
                state.refresh()
            }
        }
    }
}
