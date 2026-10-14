/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.him188.ani.app.ui.foundation.navigation.BackHandler

/**
 * 编辑器右侧 (详情) 窗格里的两层页面: 测试页在栈底, 查看单项详情时在其上叠一层.
 */
@Serializable
sealed class MediaSourceDetailRoutes : NavKey {
    @Serializable
    @SerialName("TEST")
    data object TEST : MediaSourceDetailRoutes()

    @Serializable
    @SerialName("DETAIL")
    data object DETAIL : MediaSourceDetailRoutes()
}

/**
 * 测试页与详情页之间的导航状态: 当前查看的项目 [viewingItem] 与导航栈.
 *
 * @param T 详情页展示的项目, 例如一个剧集的测试结果.
 */
@Stable
class MediaSourceDetailNavState<T : Any> {
    private val viewingItemState = mutableStateOf<T?>(null)

    var viewingItem: T? by viewingItemState
        private set

    val viewingItemAsState: State<T?> get() = viewingItemState

    /**
     * 栈底总是 [MediaSourceDetailRoutes.TEST]. [MediaSourceTestAndDetailPane] 进入组合时换成可随配置变化保存的实例.
     */
    var backStack: SnapshotStateList<MediaSourceDetailRoutes> = mutableStateListOf(MediaSourceDetailRoutes.TEST)
        internal set

    fun view(item: T) {
        viewingItem = item
        if (backStack.lastOrNull() != MediaSourceDetailRoutes.DETAIL) {
            backStack.add(MediaSourceDetailRoutes.DETAIL)
        }
    }

    fun stopViewing() {
        viewingItem = null
        // 回到栈底的 TEST. 不能用 add(TEST), 否则栈里会出现两个相同的 key
        while (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

/**
 * 详情页在不同窗口尺寸下的布局.
 */
@Immutable
data class MediaSourceDetailPaneLayout(
    /**
     * 详情页自带顶栏 (列表窗格同时展开, 详情只是右侧的一张卡片).
     */
    val showTopBarInPane: Boolean,
    /**
     * 详情页的顶栏显示在整个页面的 Scaffold 上 (紧凑窗口).
     */
    val showTopBarInScaffold: Boolean,
    /**
     * 紧凑窗口下配置面板不可见, 详情页底部用 bottom sheet 提供相关配置.
     */
    val showBottomSheet: Boolean,
) {
    companion object {
        val Expanded = MediaSourceDetailPaneLayout(
            showTopBarInPane = true,
            showTopBarInScaffold = false,
            showBottomSheet = false,
        )

        val Compact = MediaSourceDetailPaneLayout(
            showTopBarInPane = false,
            showTopBarInScaffold = true,
            showBottomSheet = true,
        )

        fun calculate(scaffoldValue: ThreePaneScaffoldValue): MediaSourceDetailPaneLayout {
            return when {
                // list 和 detail 同时展开, 也就是大屏环境. list 内包含了配置, 无需用 bottom sheet 显示配置
                scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded -> Expanded
                else -> Compact
            }
        }
    }
}

/**
 * 详情窗格: 栈底是 [testPane], 查看项目时在其上显示 [detailPane]. 返回手势回到测试页.
 */
@Composable
fun <T : Any> MediaSourceTestAndDetailPane(
    navState: MediaSourceDetailNavState<T>,
    modifier: Modifier = Modifier,
    initialRoute: MediaSourceDetailRoutes = MediaSourceDetailRoutes.TEST,
    testPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
) {
    // 栈底总是 TEST, DETAIL 叠在它上面, 所以最多两层
    val backStack = rememberSaveable(saver = DetailBackStackSaver) {
        if (initialRoute == MediaSourceDetailRoutes.TEST) {
            mutableStateListOf<MediaSourceDetailRoutes>(MediaSourceDetailRoutes.TEST)
        } else {
            mutableStateListOf(MediaSourceDetailRoutes.TEST, initialRoute)
        }
    }
    navState.backStack = backStack

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { navState.stopViewing() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<MediaSourceDetailRoutes.TEST> {
                testPane()
            }
            entry<MediaSourceDetailRoutes.DETAIL> {
                BackHandler(onBack = { navState.stopViewing() })
                detailPane()
            }
        },
    )
}

private val DetailBackStackSaver: Saver<SnapshotStateList<MediaSourceDetailRoutes>, Any> = listSaver(
    save = { stack ->
        stack.map { route ->
            when (route) {
                MediaSourceDetailRoutes.TEST -> "TEST"
                MediaSourceDetailRoutes.DETAIL -> "DETAIL"
            }
        }
    },
    restore = { saved ->
        // 空栈会让 NavDisplay 抛异常, 此时放弃恢复
        if (saved.isEmpty()) {
            null
        } else {
            saved.map { entry ->
                when (entry as String) {
                    "DETAIL" -> MediaSourceDetailRoutes.DETAIL
                    else -> MediaSourceDetailRoutes.TEST
                }
            }.toMutableStateList()
        }
    },
)
