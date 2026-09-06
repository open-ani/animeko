/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.imageviewer.ImageViewerContent
import me.him188.ani.app.ui.foundation.navigation.BackHandler

interface ImageViewerHandler {
    val imageModel: StateFlow<String?>
    val viewing: State<Boolean>

    fun viewImage(model: String?)
    fun clear()
}

val LocalImageViewerHandler: ProvidableCompositionLocal<ImageViewerHandler> = compositionLocalOf {
    error("no ImageViewerHandler provided")
}

/** [ImageViewer] 缩放层的 test tag, 供 UI 测试断言查看器已打开. */
const val IMAGE_VIEWER_TEST_TAG = "ImageViewer"

@Composable
fun rememberImageViewerHandler(): ImageViewerHandler {
    return remember {
        object : ImageViewerHandler {
            override val imageModel: MutableStateFlow<String?> = MutableStateFlow(null)
            override val viewing: MutableState<Boolean> = mutableStateOf(false)

            override fun viewImage(model: String?) {
                imageModel.value = model
                viewing.value = model != null
            }

            override fun clear() {
                imageModel.value = null
                viewing.value = false
            }
        }
    }
}

/**
 * 页面级图片查看器. 支持手势/滚轮/键盘缩放, 保存图片; 桌面端还支持把图片文件拖拽到其他应用.
 *
 * - 桌面端: 在真实窗口内时用独立窗口显示, 不遮挡页面; 预览/测试环境没有宿主窗口时回退到页面内覆盖层.
 * - Android/iOS: 覆盖整个页面的全屏层, 单击图片关闭.
 *
 * 在页面布局的最外层调用, 与 [ImageViewerBackHandler] 配合使用.
 */
@Composable
expect fun ImageViewer(handler: ImageViewerHandler, onClose: () -> Unit)

/**
 * 页面返回键关闭查看器. 桌面独立窗口模式下不拦截页面的返回键 (窗口自己响应 Esc 和关闭按钮).
 */
@Composable
expect fun ImageViewerBackHandler(handler: ImageViewerHandler)

/**
 * 覆盖整个页面的全屏查看器, 单击图片关闭.
 */
@Composable
internal fun ImageViewerOverlay(
    handler: ImageViewerHandler,
    onClose: () -> Unit,
) {
    val model by handler.imageModel.collectAsStateWithLifecycle()
    AniAnimatedVisibility(
        visible = handler.viewing.value,
        enter = LocalAniMotionScheme.current.animatedVisibility.standardEnter,
        exit = LocalAniMotionScheme.current.animatedVisibility.standardExit,
        modifier = Modifier.fillMaxSize(),
    ) {
        ImageViewerContent(
            model = model,
            onClose = onClose,
            modifier = Modifier.fillMaxSize(),
            closeOnTap = true,
            showCloseButton = true,
        )
    }
}

@Composable
internal fun ImageViewerOverlayBackHandler(handler: ImageViewerHandler) {
    BackHandler(enabled = handler.viewing.value) { handler.clear() }
}
