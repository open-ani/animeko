/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.data.repository.SavedWindowState
import me.him188.ani.app.data.repository.media.MediaSourceSaves
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionsSaveData
import me.him188.ani.app.data.repository.media.MikanIndexes
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionsSaveData
import me.him188.ani.utils.httpdownloader.DownloadState
import me.him188.ani.utils.io.SystemPath

// 一个对象, 可都写到 common 里, 不用每个 store 都 expect/actual
abstract class PlatformDataStoreManager {
    val mikanIndexStore: DataStore<MikanIndexes>
        get() = DataStoreFactory.create(
            serializer = MikanIndexes.serializer().asDataStoreSerializer({ MikanIndexes.Empty }),
            produceFile = { resolveDataStoreFile("mikanIndexes") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                MikanIndexes.Empty
            },
        )

    val mediaSourceSaveStore by lazy {
        DataStoreFactory.create(
            serializer = MediaSourceSaves.serializer()
                .asDataStoreSerializer({ MediaSourceSaves.Default }),
            produceFile = { resolveDataStoreFile("mediaSourceSaves") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                MediaSourceSaves.Default
            },
        )
    }

    val mediaSourceSubscriptionStore by lazy {
        DataStoreFactory.create(
            serializer = MediaSourceSubscriptionsSaveData.serializer()
                .asDataStoreSerializer({ MediaSourceSubscriptionsSaveData.Default }),
            produceFile = { resolveDataStoreFile("mediaSourceSubscription") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                MediaSourceSubscriptionsSaveData.Default
            },
        )
    }

    val episodeHistoryStore by lazy {
        DataStoreFactory.create(
            serializer = EpisodeHistories.serializer()
                .asDataStoreSerializer({ EpisodeHistories.Empty }),
            produceFile = { resolveDataStoreFile("episodeHistories") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                EpisodeHistories.Empty
            },
        )
    }

    // creata a datastore<List<DanmakuFilter>>
    val danmakuFilterStore by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(DanmakuRegexFilter.serializer())
                .asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("danmakuFilter") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                emptyList()
            },
        )
    }
    val savedWindowStateStore by lazy {
        DataStoreFactory.create(
            serializer = SavedWindowState.serializer().nullable
                .asDataStoreSerializer({ null }),
            produceFile = { resolveDataStoreFile("windowState") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                null
            },
        )
    }

    val peerFilterSubscriptionStore by lazy {
        DataStoreFactory.create(
            serializer = PeerFilterSubscriptionsSaveData.serializer()
                .asDataStoreSerializer({ PeerFilterSubscriptionsSaveData.Default }),
            produceFile = { resolveDataStoreFile("peerFilterSubscription") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                PeerFilterSubscriptionsSaveData.Default
            },
        )
    }

    val m3u8DownloaderStore by lazy {
        DataStoreFactory.create(
            serializer = ListSerializer(DownloadState.serializer()).asDataStoreSerializer({ emptyList() }),
            produceFile = { resolveDataStoreFile("m3u8Downloader") },
            corruptionHandler = ReplaceFileCorruptionHandler {
                emptyList()
            },
        )
    }

    abstract val tokenStore: DataStore<Preferences>
    abstract val preferencesStore: DataStore<Preferences>
    abstract val preferredAllianceStore: DataStore<Preferences>

    abstract fun resolveDataStoreFile(name: String): SystemPath

    protected val replaceFileCorruptionHandlerForPreferences: ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler { mutablePreferencesOf() }
}

