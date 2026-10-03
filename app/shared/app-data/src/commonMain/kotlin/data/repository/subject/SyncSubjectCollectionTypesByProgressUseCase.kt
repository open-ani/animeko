/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.isCompleted
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

interface SyncSubjectCollectionTypesByProgressUseCase {
    fun requestSubjectSync(subjectId: Int)
}

class SyncSubjectCollectionTypesByProgressUseCaseImpl(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase,
    private val autoAdvanceEnabled: Flow<Boolean>,
    private val backgroundScope: CoroutineScope,
) : SyncSubjectCollectionTypesByProgressUseCase {
    private val syncMutex = Mutex()

    override fun requestSubjectSync(subjectId: Int) {
        backgroundScope.launch(CoroutineName("SubjectCollectionProgressSyncer[$subjectId]")) {
            syncMutex.withLock {
                try {
                    syncSubject(subjectId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) {
                        "Automatic subject collection progress sync failed for subject $subjectId"
                    }
                }
            }
        }
    }

    private suspend fun syncSubject(subjectId: Int) {
        if (!autoAdvanceEnabled.first()) {
            logger.info {
                "Skipping automatic subject collection progress sync for subject $subjectId because it is disabled"
            }
            return
        }

        val collection = subjectCollectionRepository.subjectCollectionFlow(subjectId).first()
        val targetType = collection.syncedCollectionTypeByEpisodeProgress()
        if (targetType == null) {
            logger.info { "No automatic collection type advancement needed for subject $subjectId" }
            return
        }
        setSubjectCollectionTypeOrDeleteUseCase(subjectId, targetType)
        logger.info {
            "Automatically advanced subject $subjectId from ${collection.collectionType} to $targetType"
        }
    }

    private companion object {
        val logger = logger<SyncSubjectCollectionTypesByProgressUseCase>()
    }
}

internal fun SubjectCollectionInfo.syncedCollectionTypeByEpisodeProgress(): UnifiedCollectionType? {
    val mainStoryEpisodes = episodes
        .filter { it.episodeInfo.type == EpisodeType.MainStory }
        .sortedBy { it.episodeInfo.sort }

    if (mainStoryEpisodes.isEmpty()) return null

    val hasWatchedMainStory = mainStoryEpisodes.any { it.collectionType == UnifiedCollectionType.DONE }
    val hasFinishedMainStory = airingInfo.isCompleted &&
        mainStoryEpisodes.last().collectionType == UnifiedCollectionType.DONE

    return when (collectionType) {
        UnifiedCollectionType.WISH -> when {
            hasFinishedMainStory -> UnifiedCollectionType.DONE
            hasWatchedMainStory -> UnifiedCollectionType.DOING
            else -> null
        }

        UnifiedCollectionType.DOING -> UnifiedCollectionType.DONE.takeIf { hasFinishedMainStory }

        else -> null
    }
}
