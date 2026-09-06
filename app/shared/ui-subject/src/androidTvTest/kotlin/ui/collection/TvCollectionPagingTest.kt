/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.collection

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TvCollectionPagingTest {
    private val idle = LoadState.NotLoading(false)

    private fun states(source: LoadState = idle, mediator: LoadState? = idle) = CombinedLoadStates(
        refresh = idle,
        prepend = idle,
        append = idle,
        source = LoadStates(source, idle, idle),
        mediator = mediator?.let { LoadStates(it, idle, idle) },
    )

    @Test
    fun `cached category is not empty while database refresh is still loading`() {
        // Actual first-visit sequence: the mediator skips its refresh while Room is loading.
        assertFalse(states(source = LoadState.Loading).isCollectionRefreshComplete())
        assertTrue(states().isCollectionRefreshComplete())
    }

    @Test
    fun `remote refresh or failure does not confirm an empty collection`() {
        assertFalse(states(mediator = LoadState.Loading).isCollectionRefreshComplete())
        assertFalse(states(mediator = LoadState.Error(Exception("offline"))).isCollectionRefreshComplete())
        assertFalse(states(source = LoadState.Error(Exception("database"))).isCollectionRefreshComplete())
        assertTrue(states(mediator = null).isCollectionRefreshComplete())
    }
}
