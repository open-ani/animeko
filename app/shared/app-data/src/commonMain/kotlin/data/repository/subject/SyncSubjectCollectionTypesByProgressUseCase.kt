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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
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
    fun requestFullSync(reason: SubjectCollectionProgressSyncReason)

    fun requestSubjectSync(subjectId: Int)
}

enum class SubjectCollectionProgressSyncReason {
    MAIN_SCREEN_ENTERED,
    SETTING_ENABLED,
}

class SyncSubjectCollectionTypesByProgressUseCaseImpl(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase,
    private val autoAdvanceEnabled: Flow<Boolean>,
    private val backgroundScope: CoroutineScope,
) : SyncSubjectCollectionTypesByProgressUseCase {
    private val syncMutex = Mutex()

    init {
        backgroundScope.launch(CoroutineName("SubjectCollectionProgressSyncer.settings")) {
            autoAdvanceEnabled
                .distinctUntilChanged()
                .drop(1)
                .filter { it }
                .collect {
                    requestFullSync(SubjectCollectionProgressSyncReason.SETTING_ENABLED)
                }
        }
    }

    override fun requestFullSync(reason: SubjectCollectionProgressSyncReason) {
        launchSync("full sync ($reason)") {
            syncAll(reason)
        }
    }

    override fun requestSubjectSync(subjectId: Int) {
        launchSync("subject $subjectId") {
            syncSubject(subjectId)
        }
    }

    private fun launchSync(description: String, block: suspend () -> Unit) {
        backgroundScope.launch(CoroutineName("SubjectCollectionProgressSyncer")) {
            syncMutex.withLock {
                runCatchingSync(description, block)
            }
        }
    }

    private suspend fun runCatchingSync(description: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Automatic subject collection progress $description failed" }
        }
    }

    private suspend fun syncAll(reason: SubjectCollectionProgressSyncReason) {
        if (!autoAdvanceEnabled.first()) {
            logger.info { "Skipping automatic subject collection progress sync because it is disabled" }
            return
        }

        logger.info { "Starting automatic subject collection progress sync: reason=$reason" }
        val collections = subjectCollectionRepository.fetchSubjectCollectionsSnapshot(
            listOf(UnifiedCollectionType.WISH, UnifiedCollectionType.DOING),
        )
        if (!autoAdvanceEnabled.first()) {
            logger.info { "Stopping automatic subject collection progress sync because it was disabled" }
            return
        }
        val updates = collections.mapNotNull { collection ->
            collection.syncedCollectionTypeByEpisodeProgress()?.let { collection to it }
        }

        logger.info {
            "Fetched ${collections.size} collections; ${updates.size} require a collection type update"
        }

        var wishToDoing = 0
        var wishToDone = 0
        var doingToDone = 0
        var failed = 0
        updates.forEach { (collection, targetType) ->
            try {
                setSubjectCollectionTypeOrDeleteUseCase(collection.subjectId, targetType)
                when (collection.collectionType to targetType) {
                    UnifiedCollectionType.WISH to UnifiedCollectionType.DOING -> wishToDoing++
                    UnifiedCollectionType.WISH to UnifiedCollectionType.DONE -> wishToDone++
                    UnifiedCollectionType.DOING to UnifiedCollectionType.DONE -> doingToDone++
                    else -> error(
                        "Unexpected collection sync transition: ${collection.collectionType} to $targetType",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                logger.warn(e) {
                    "Failed to sync subject ${collection.subjectId} from ${collection.collectionType} to $targetType"
                }
            }
        }

        logger.info {
            "Automatic subject collection progress sync completed: reason=$reason, fetched=${collections.size}, " +
                "skipped=${collections.size - updates.size}, wishToDoing=$wishToDoing, " +
                "wishToDone=$wishToDone, doingToDone=$doingToDone, failed=$failed"
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
        if (!autoAdvanceEnabled.first()) {
            logger.info {
                "Stopping automatic subject collection progress sync for subject $subjectId because it was disabled"
            }
            return
        }
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
