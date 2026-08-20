/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.imageviewer.zoomable.ZoomableGestureScope
import me.him188.ani.app.imageviewer.zoomable.rememberZoomableState
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme

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

@Composable
fun ImageViewer(
    handler: ImageViewerHandler,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val model by handler.imageModel.collectAsStateWithLifecycle()

    var contentSizeX by rememberSaveable(model.toString()) { mutableStateOf(0f) }
    var contentSizeY by rememberSaveable(model.toString()) { mutableStateOf(0f) }
    val contentSize by derivedStateOf { Size(contentSizeX, contentSizeY) }

    val zoomableState = rememberZoomableState(key = handler.viewing.value)

    LaunchedEffect(contentSize, handler.viewing.value) {
        zoomableState.contentSize = contentSize
    }

    AniAnimatedVisibility(
        visible = handler.viewing.value,
        enter = LocalAniMotionScheme.current.animatedVisibility.standardEnter,
        exit = LocalAniMotionScheme.current.animatedVisibility.standardExit,
        modifier = Modifier.fillMaxSize(),
    ) {
        me.him188.ani.app.imageviewer.ImageViewer(
            model = me.him188.ani.app.imageviewer.ImageViewer.AnyComposable(
                composable = {
                    AsyncImage(
                        model = model,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = null,
                        onSuccess = {
                            contentSizeX = it.width.toFloat()
                            contentSizeY = it.height.toFloat()
                        },
                    )
                },
            ),
            state = zoomableState,
            modifier = Modifier.testTag(IMAGE_VIEWER_TEST_TAG).background(Color.Black),
            detectGesture = ZoomableGestureScope(
                onDoubleTap = { offset -> scope.launch { zoomableState.toggleScale(offset) } },
                onTap = { _ -> onClose() },
            ),
        )
    }
}
