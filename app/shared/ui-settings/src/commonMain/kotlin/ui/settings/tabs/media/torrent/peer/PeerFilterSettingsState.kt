/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage

@Stable
class PeerFilterSettingsState(
    storage: SaveableStorage<TorrentPeerConfig>
) {
    var searchingBlockedIp by mutableStateOf(false)
        private set
    val searchBlockedIpQuery = MutableStateFlow("")

    private var ipBlackList by storage.prop({ it.ipBlackList }, { copy(ipBlackList = it) }, emptyList())
    val searchedIpBlockList: Flow<List<String>> = searchBlockedIpQuery
        .combine(snapshotFlow { ipBlackList }) { query, list ->
            query to list
        }
        .transformLatest { (query, list) ->
            // 需要去重，避免 lazy column 出现重复的 key
            emit(list.filter { it.contains(query) }.distinct())
        }
    
    var ipFilterEnabled by storage.prop({ it.enableIpFilter }, { copy(enableIpFilter = it) }, false)
    var ipFiltersPattern by storage.prop(
        { it.localRule.blockedIpPattern },
        { copy(localRule = localRule.copy(blockedIpPattern = it)) },
        emptyList(),
    )
    
    var idFilterEnabled by storage.prop(
        get = { it.enableIdFilter },
        copy = { copy(enableIdFilter = it) },
        default = false,
    )
    var idFiltersRegex by storage.prop(
        { it.localRule.blockedIdRegex },
        { copy(localRule = localRule.copy(blockedIdRegex = it)) },
        emptyList(),
    )
    var blockInvalidId by storage.prop({ it.blockInvalidId }, { copy(blockInvalidId = it) }, false)
    
    var clientFilterEnabled by storage.prop({ it.enableClientFilter }, { copy(enableClientFilter = it) }, false)
    var clientFiltersRegex by storage.prop(
        { it.localRule.blockedClientRegex },
        { copy(localRule = localRule.copy(blockedClientRegex = it)) },
        emptyList(),
    )
    
    fun addBlockedIp(list: List<String>) {
        ipBlackList = ipBlackList.toMutableList().apply { addAll(list) }
    }
    
    fun removeBlockedIp(value: String) {
        val newList = mutableListOf<String>()
        for (ip in ipBlackList) {
            if (ip != value) newList.add(ip)
        }
        ipBlackList = newList
    }
    
    fun setSearchBlockIpQuery(value: String) {
        searchBlockedIpQuery.value = value
    }
    
    fun stopSearchBlockedIp() {
        searchBlockedIpQuery.value = ""
        searchingBlockedIp = false
    }
    
    fun startSearchBlockedIp() {
        searchBlockedIpQuery.value = ""
        searchingBlockedIp = true
    }
}