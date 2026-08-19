/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerType
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.input.ActiveInputSourceState
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource

/**
 * [PullToRefreshBox] is a container that expects a scrollable layout as content and adds gesture
 * support for manually refreshing when the user swipes downward at the beginning of the content. By
 * default, it uses [PullToRefreshDefaults.Indicator] as the refresh indicator.
 *
 * @sample androidx.compose.material3.samples.PullToRefreshSample
 *
 * View models can be used as source as truth as shown in
 *
 * @sample androidx.compose.material3.samples.PullToRefreshViewModelSample
 *
 * A custom state implementation can be initialized like this
 *
 * @sample androidx.compose.material3.samples.PullToRefreshSampleCustomState
 *
 * Scaling behavior can be implemented like this
 *
 * @sample androidx.compose.material3.samples.PullToRefreshScalingSample
 *
 * @param isRefreshing whether a refresh is occurring
 * @param onRefresh callback invoked when the user gesture crosses the threshold, thereby requesting
 *   a refresh.
 * @param modifier the [Modifier] to be applied to this container
 * @param state the state that keeps track of distance pulled
 * @param contentAlignment The default alignment inside the Box.
 * @param indicator the indicator that will be drawn on top of the content when the user begins a
 *   pull or a refresh is occurring
 * @param content the content of the pull refresh container, typically a scrollable layout such as
 *   [LazyColumn] or a layout using [Modifier.verticalScroll]
 */
@Composable
@ExperimentalMaterial3Api
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    enabled: Boolean = true, // ADDED
    touchOnly: Boolean = false,
    indicator: @Composable BoxScope.() -> Unit = {
        Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            isRefreshing = isRefreshing,
            state = state,
        )
    },
    content: @Composable BoxScope.() -> Unit
) {
    val activeInputSource = LocalActiveInputSource.current
    val touchOnlyEnabled = rememberUpdatedState(enabled && touchOnly)
    val touchOnlyConnection = remember(activeInputSource) {
        TouchOnlyPullToRefreshNestedScrollConnection(
            activeInputSource = activeInputSource,
            enabled = { touchOnlyEnabled.value },
        )
    }

    Box(
        modifier
            .pullToRefresh(state = state, isRefreshing = isRefreshing, enabled = enabled, onRefresh = onRefresh)
            .ifThen(touchOnly) { nestedScroll(touchOnlyConnection) },
        contentAlignment = contentAlignment,
    ) {
        content()
        indicator()
    }
}

private class TouchOnlyPullToRefreshNestedScrollConnection(
    private val activeInputSource: ActiveInputSourceState,
    private val enabled: () -> Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (
            enabled() &&
            source == NestedScrollSource.UserInput &&
            activeInputSource.latest != PointerType.Touch &&
            available.y > 0f
        ) {
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }
}
