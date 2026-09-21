/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.pip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.videoplayer.ui.IosVideoLayerRegistry
import me.him188.ani.app.videoplayer.ui.findIosVideoLayer
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.Foundation.NSKeyValueObservingOptionInitial
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.Foundation.NSKeyValueObservingOptions
import platform.Foundation.NSKeyValueObservingProtocol
import platform.Foundation.addObserver
import platform.Foundation.removeObserver
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberPictureInPictureController(player: MediampPlayer): PictureInPictureController {
    // layer 由 VideoPlayer 的 UIKitView factory 异步创建, 通过版本号感知
    val version by IosVideoLayerRegistry.version.collectAsState()
    val layer = remember(player, version) { player.findIosVideoLayer() }
    val avPlayer = player as? AVKitMediampPlayer
    val controller = remember(player, layer) {
        if (layer != null && avPlayer != null) {
            IosPictureInPictureController(layer, avPlayer)
        } else {
            NoOpPictureInPictureController
        }
    }
    DisposableEffect(controller) {
        onDispose { (controller as? IosPictureInPictureController)?.dispose() }
    }
    return controller
}

/**
 * iOS 画中画控制器, 包装 AVKit 的 `AVPictureInPictureController`.
 *
 * - 自动进入: `canStartPictureInPictureAutomaticallyFromInline` (用户上滑回桌面时系统自动以小窗继续,
 *   动画由系统提供), 由 [updatePolicy] 开关
 * - 小窗播控由系统提供, 无需自定义 action
 * - `restoreUserInterface` 直接 completion(true): 没有需要恢复的全屏 UI 状态
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class IosPictureInPictureController(
    layer: AVPlayerLayer,
    private val player: AVKitMediampPlayer,
) : PictureInPictureController {
    override val isSupported: Boolean get() = true

    private val _isInPictureInPicture = MutableStateFlow(false)
    override val isInPictureInPicture: StateFlow<Boolean> = _isInPictureInPicture.asStateFlow()

    private val _isPictureInPicturePossible = MutableStateFlow(false)
    override val isPictureInPicturePossible: StateFlow<Boolean> = _isPictureInPicturePossible.asStateFlow()

    // AVPictureInPictureController.delegate 是 weak 引用, 必须由本类强持有
    private val delegate = Delegate(
        onIsInChanged = { value -> _isInPictureInPicture.value = value },
    )

    private val pipController = AVPictureInPictureController(playerLayer = layer).apply {
        this.delegate = this@IosPictureInPictureController.delegate
        // 部署目标 iOS 16.0, 无需检查 14.2 可用性
        canStartPictureInPictureAutomaticallyFromInline = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // isPictureInPicturePossible 的 KVO 变更通知在部分 iOS 版本上不可靠: 初值投递后
        // 可能不再投递 (系统层 possible 已翻 true, 自动小窗都好使, 但 KVO 不再回调),
        // 而控制栏按钮的 enabled 依赖这个 flow — 随播放器状态重读真实值兜底.
        // StateFlow 收集会立即发射当前状态, 等价于再补一次初值.
        scope.launch {
            player.state.collect { refreshPossible() }
        }
    }

    // KVO 快速路径, 仅在变更通知真正投递时触发
    private val possibleObserver = pipController.observeKeyPathOnMain(
        KEY_IS_PICTURE_IN_PICTURE_POSSIBLE,
        options = NSKeyValueObservingOptionInitial or NSKeyValueObservingOptionNew,
    ) {
        refreshPossible()
    }

    private fun refreshPossible() {
        _isPictureInPicturePossible.value = pipController.isPictureInPicturePossible()
    }

    override fun updatePolicy(autoEnterEnabled: Boolean, aspectWidth: Int?, aspectHeight: Int?) {
        // 宽高比由 AVKit 按 layer 内容自动处理, 无需声明 (与 Android 的 PictureInPictureParams 不同)
        pipController.canStartPictureInPictureAutomaticallyFromInline = autoEnterEnabled
        refreshPossible()
    }

    override fun enterPictureInPicture() {
        refreshPossible()
        if (pipController.isPictureInPicturePossible() && !pipController.isPictureInPictureActive()) {
            pipController.startPictureInPicture()
        }
    }

    fun dispose() {
        scope.cancel()
        pipController.removeObserver(possibleObserver, KEY_IS_PICTURE_IN_PICTURE_POSSIBLE)
        pipController.delegate = null
    }

    private companion object {
        const val KEY_IS_PICTURE_IN_PICTURE_POSSIBLE = "isPictureInPicturePossible"
    }
}

/**
 * `AVPictureInPictureControllerDelegate` 的最小实现: 只跟踪小窗启停;
 * 用户关闭小窗时系统会暂停播放, 播放器状态经 mediamp 状态机自动同步 UI, 无需额外处理.
 */
private class Delegate(
    val onIsInChanged: (Boolean) -> Unit,
) : NSObject(), AVPictureInPictureControllerDelegateProtocol {
    override fun pictureInPictureControllerDidStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController,
    ) {
        onIsInChanged(true)
    }

    override fun pictureInPictureControllerDidStopPictureInPicture(
        pictureInPictureController: AVPictureInPictureController,
    ) {
        onIsInChanged(false)
    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit,
    ) {
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
    }
}

/**
 * 注册 KVO 并把回调蹦床到主队列 (KVO 可能在工作线程触发).
 * 写法参照 mediamp-avkit 内部的 `observeKeyPathOnMain`.
 */
@OptIn(ExperimentalForeignApi::class)
private class MainQueueKvoObserver(
    private val onChange: () -> Unit,
) : NSObject(), NSKeyValueObservingProtocol {
    override fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: COpaquePointer?,
    ) {
        dispatch_async(dispatch_get_main_queue()) { onChange() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSObject.observeKeyPathOnMain(
    keyPath: String,
    options: NSKeyValueObservingOptions = NSKeyValueObservingOptionNew,
    onChange: () -> Unit,
): NSObject {
    val observer = MainQueueKvoObserver(onChange)
    addObserver(observer, forKeyPath = keyPath, options = options, context = null)
    return observer
}
