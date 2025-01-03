/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A wrapper around [LazyPagingItems] that is specialized to a single item.
 */
class LazyItem<T : Any>(
    private val lazyPagingItems: LazyPagingItems<T>
) {
    /**
     * Whether there is an item being presented.
     * `false` means some error must have occurred.
     */
    val hasItem = lazyPagingItems.itemCount > 0

    /**
     * Gets the presented item.
     *
     * @return The presented item, or `null` if it is a placeholder
     * @throws IndexOutOfBoundsException if there is no item being presented.
     */
    val item = lazyPagingItems[0]

    /**
     * @see LazyPagingItems.loadState
     */
    val loadState get() = lazyPagingItems.loadState

    /**
     * @see LazyPagingItems.retry
     */
    fun retry() {
        lazyPagingItems.retry()
    }

    /**
     * @see LazyPagingItems.refresh
     */
    fun refresh() {
        lazyPagingItems.refresh()
    }
}

@Composable
fun <T : Any> Flow<T>.collectAsLazyItem(
    context: CoroutineContext = EmptyCoroutineContext
): LazyItem<T> {
    val flow = remember(this) {
        this.map {
            PagingData.from(listOf(it))
        }
    }

    val lazyPagingItems = flow.collectAsLazyPagingItems(context)
    return remember(lazyPagingItems) {
        LazyItem(lazyPagingItems)
    }
}

@Composable
fun <T : Any> Flow<T>.collectAsLazyItemWithLifecycle(
    context: CoroutineContext = EmptyCoroutineContext,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
): LazyItem<T> {
    val flow = remember(this) {
        this.map {
            PagingData.from(listOf(it))
        }
    }

    val lazyPagingItems = flow.collectAsLazyPagingItemsWithLifecycle(context, lifecycle, minActiveState)
    return remember(lazyPagingItems) {
        LazyItem(lazyPagingItems)
    }
}
