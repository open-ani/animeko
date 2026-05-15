/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.danmaku

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
class DanmakuRegexFilterState(
    list: State<List<DanmakuRegexFilter>>,
    val add: (filter: DanmakuRegexFilter) -> Unit,
    val edit: (id: String, new: DanmakuRegexFilter) -> Unit,
    val remove: (filter: DanmakuRegexFilter) -> Unit,
    val switch: (filter: DanmakuRegexFilter) -> Unit,
    val onExport: suspend () -> String,
    val onImport: suspend (String) -> Boolean,
) {
    val list by list
}

@TestOnly
fun createTestDanmakuRegexFilterState(): DanmakuRegexFilterState {
    val localListState = mutableStateOf(
        listOf(
            DanmakuRegexFilter(
                id = Uuid.randomString(),
                name = "测试",
                regex = "测试",
            ),
            DanmakuRegexFilter(
                id = Uuid.randomString(),
                name = "测试2",
                regex = "测试2",
            ),
        ),
    )
    val defaultAdd: (DanmakuRegexFilter) -> Unit = { filter ->
        localListState.value += filter
    }
    val defaultEdit: (String, DanmakuRegexFilter) -> Unit = { id, newFilter ->
        localListState.value = localListState.value.map {
            if (it.id == id) newFilter else it
        }
    }
    val defaultRemove: (DanmakuRegexFilter) -> Unit = { filter ->
        localListState.value -= filter
    }
    val defaultSwitch: (DanmakuRegexFilter) -> Unit = { filter ->
        localListState.value = localListState.value.map {
            if (it.id == filter.id) it.copy(enabled = !it.enabled) else it
        }
    }

    return DanmakuRegexFilterState(
        list = localListState,
        add = defaultAdd,
        edit = defaultEdit,
        remove = defaultRemove,
        switch = defaultSwitch,
        onExport = { "[]" },
        onImport = { true },
    )
}

