/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.Flow
import kotlin.enums.enumEntries

internal typealias PageTypeUpperBound<P> = Enum<P>

/**
 * Common side sheet implementation.
 * @param P must be parcelable on Android.
 */
@Composable
inline fun <reified P : PageTypeUpperBound<P>> VideoSideSheets(
    controller: VideoSideSheetsController<P>,
    modifier: Modifier = Modifier,
    noinline pageContent: @Composable (VideoSideSheetScope.(page: P) -> Unit),
) {
    VideoSideSheets(controller, enumEntries(), modifier, pageContent)
}

/**
 * Common side sheet implementation.
 * @param P must be parcelable on Android.
 */
@Composable
fun <P : PageTypeUpperBound<P>> VideoSideSheets(
    controller: VideoSideSheetsController<P>,
    pages: List<P>,
    modifier: Modifier = Modifier,
    pageContent: @Composable (VideoSideSheetScope.(page: P) -> Unit),
) {
    val backStack = controller.backStack
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { controller.goBack() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        transitionSpec = { fadeIn(snap()) togetherWith fadeOut(snap()) },
        popTransitionSpec = { fadeIn(snap()) togetherWith fadeOut(snap()) },
        predictivePopTransitionSpec = { fadeIn(snap()) togetherWith fadeOut(snap()) },
        entryProvider = entryProvider {
            entry<VideoSideSheetRoute.None> {
                // Nothing here
            }
            entry<VideoSideSheetRoute.Page> { route ->
                pages.firstOrNull { it.name == route.name }?.let { page ->
                    val scope = remember(controller, route) {
                        VideoSideSheetScopeImpl(controller, route)
                    }
                    pageContent(scope, page)
                }
            }
        },
    )
}

/**
 * side sheet 的导航栈. 栈底总是 [None], 也就是"没有打开任何 sheet".
 */
internal sealed class VideoSideSheetRoute : NavKey {
    data object None : VideoSideSheetRoute()

    /**
     * @param name 对应 [PageTypeUpperBound] 的 [Enum.name].
     * @param index 入栈时的位置. 仅用于让同一个页面重复入栈时 key 仍然唯一,
     * [NavDisplay] 要求栈内的 key 不重复.
     */
    data class Page(val name: String, val index: Int) : VideoSideSheetRoute()
}

@Stable
sealed class VideoSideSheetsController<P : PageTypeUpperBound<P>> {
    internal abstract val backStack: SnapshotStateList<VideoSideSheetRoute>

    /**
     * Whether a sheet is displaying.
     */
    val hasPageFlow: Flow<Boolean>
        get() = snapshotFlow { backStack.lastOrNull() is VideoSideSheetRoute.Page }

    fun navigateTo(route: P) {
        backStack.add(VideoSideSheetRoute.Page(route.name, backStack.size))
    }

    /**
     * Pops the top page. 栈底的 [VideoSideSheetRoute.None] 不会被弹出.
     */
    internal fun goBack() {
        popTo(backStack.lastIndex)
    }

    /**
     * Clears all pages and effectively closes the side sheet.
     */
    internal fun closeSideSheet() {
        popTo(1)
    }

    /**
     * 把栈弹到只剩 [targetSize] 个元素. 至少保留栈底的 [VideoSideSheetRoute.None],
     * 因为空栈会让 [NavDisplay] 抛异常.
     */
    private fun popTo(targetSize: Int) {
        val size = targetSize.coerceAtLeast(1)
        while (backStack.size > size) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

/**
 * Whether a sheet is displaying.
 */
@Composable
fun <P : PageTypeUpperBound<P>> VideoSideSheetsController<P>.hasPageAsState(): State<Boolean> {
    return hasPageFlow.collectAsState(initial = false)
}

@Composable
fun <P : PageTypeUpperBound<P>> rememberVideoSideSheetsController(): VideoSideSheetsController<P> {
    val backStack = rememberSaveable(saver = VideoSideSheetBackStackSaver) {
        mutableStateListOf<VideoSideSheetRoute>(VideoSideSheetRoute.None)
    }
    return remember(backStack) {
        VideoSideSheetsControllerImpl(backStack)
    }
}

private val VideoSideSheetBackStackSaver: Saver<SnapshotStateList<VideoSideSheetRoute>, Any> = listSaver(
    save = { stack ->
        stack.map { route ->
            when (route) {
                VideoSideSheetRoute.None -> ""
                is VideoSideSheetRoute.Page -> "${route.index}:${route.name}"
            }
        }
    },
    restore = { saved ->
        // 空栈会让 NavDisplay 抛异常, 此时放弃恢复
        if (saved.isEmpty()) {
            null
        } else {
            saved.map { entry ->
                val value = entry as String
                val separator = value.indexOf(':')
                if (separator == -1) {
                    VideoSideSheetRoute.None
                } else {
                    VideoSideSheetRoute.Page(
                        name = value.substring(separator + 1),
                        index = value.substring(0, separator).toInt(),
                    )
                }
            }.toMutableStateList()
        }
    },
)

@Stable
sealed interface VideoSideSheetScope {
    /**
     * Pops up the current back stack entry.
     */
    fun goBack()

    /**
     * Clears all back stack entries and effectively closes the side sheet.
     */
    fun closeSideSheet()
}


private class VideoSideSheetsControllerImpl<P : PageTypeUpperBound<P>>(
    override val backStack: SnapshotStateList<VideoSideSheetRoute>,
) : VideoSideSheetsController<P>()

internal class VideoSideSheetScopeImpl(
    private val controller: VideoSideSheetsController<*>,
    private val route: VideoSideSheetRoute,
) : VideoSideSheetScope {
    override fun goBack() {
        // 弹出这个 scope 所属的页面, 而不是当前栈顶: 页面退场动画期间栈顶可能已经变了
        val index = controller.backStack.indexOfLast { it == route }
        if (index <= 0) return
        while (controller.backStack.size > index) {
            controller.backStack.removeAt(controller.backStack.lastIndex)
        }
    }

    override fun closeSideSheet() {
        controller.closeSideSheet()
    }
}
