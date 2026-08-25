/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.coroutines.CancellationException
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.isCompleted
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

interface SyncSubjectCollectionTypesByProgressUseCase {
    suspend operator fun invoke(): SubjectCollectionProgressSyncResult
}

data class SubjectCollectionProgressSyncResult(
    val fetched: Int,
    val skipped: Int,
    val wishToDoing: Int,
    val doingToDone: Int,
    val failed: Int,
)

class SyncSubjectCollectionTypesByProgressUseCaseImpl(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val setSubjectCollectionTypeOrDeleteUseCase: SetSubjectCollectionTypeOrDeleteUseCase,
) : SyncSubjectCollectionTypesByProgressUseCase {
    override suspend fun invoke(): SubjectCollectionProgressSyncResult {
        logger.info { "Starting manual subject collection progress sync" }
        val collections = subjectCollectionRepository.fetchSubjectCollectionsSnapshot(
            listOf(UnifiedCollectionType.WISH, UnifiedCollectionType.DOING),
        )
        val updates = collections.mapNotNull { collection ->
            collection.syncedCollectionTypeByEpisodeProgress()?.let { collection to it }
        }

        logger.info {
            "Fetched ${collections.size} collections; ${updates.size} require a collection type update"
        }

        var wishToDoing = 0
        var doingToDone = 0
        var failed = 0
        updates.forEach { (collection, targetType) ->
            try {
                setSubjectCollectionTypeOrDeleteUseCase(collection.subjectId, targetType)
                when (targetType) {
                    UnifiedCollectionType.DOING -> wishToDoing++
                    UnifiedCollectionType.DONE -> doingToDone++
                    else -> error("Unexpected collection sync target: $targetType")
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

        val result = SubjectCollectionProgressSyncResult(
            fetched = collections.size,
            skipped = collections.size - updates.size,
            wishToDoing = wishToDoing,
            doingToDone = doingToDone,
            failed = failed,
        )
        logger.info {
            "Manual subject collection progress sync completed: fetched=${result.fetched}, " +
                "skipped=${result.skipped}, wishToDoing=${result.wishToDoing}, " +
                "doingToDone=${result.doingToDone}, failed=${result.failed}"
        }
        return result
    }

    private companion object {
        val logger = logger<SyncSubjectCollectionTypesByProgressUseCase>()
    }
}

internal fun SubjectCollectionInfo.syncedCollectionTypeByEpisodeProgress(): UnifiedCollectionType? {
    val mainStoryCollectionTypes = episodes
        .asSequence()
        .filter { it.episodeInfo.type == EpisodeType.MainStory }
        .map { it.collectionType }
        .toList()

    if (mainStoryCollectionTypes.isEmpty()) return null

    return when (collectionType) {
        UnifiedCollectionType.WISH -> UnifiedCollectionType.DOING.takeIf {
            mainStoryCollectionTypes.any { it == UnifiedCollectionType.DONE }
        }

        UnifiedCollectionType.DOING -> UnifiedCollectionType.DONE.takeIf {
            airingInfo.isCompleted && mainStoryCollectionTypes.all { it == UnifiedCollectionType.DONE }
        }

        else -> null
    }
}
