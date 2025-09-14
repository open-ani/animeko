package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.RememberObserver
import androidx.compose.ui.geometry.Rect
import kotlinx.cinterop.ExperimentalForeignApi
import me.him188.ani.app.platform.Context
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayer
import org.openani.mediamp.avkit.PlayerUIView
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPictureInPictureController
import platform.Foundation.NSLog
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.objc.sel_registerName

@OptIn(ExperimentalForeignApi::class)
actual class PictureInPictureController actual constructor(
    private val context: Context,
    private val player: MediampPlayer
) : RememberObserver {


    private lateinit var pipController: AVPictureInPictureController
    private val pipDelegate = AVPictureInPictureControllerDelegate()
    
    private fun initializePipController() = runCatching {
        if (::pipController.isInitialized) {
            return@runCatching
        }

        if (!isPictureInPictureSupported) {
            NSLog("Picture-in-Picture is not supported on this device")
            return@runCatching
        }

        val window: UIView? = getKeyWindow()
        if (window == null) {
            NSLog("There is no key window.")
            return@runCatching
        }

        val playerUIView = findPlayerUIView(window)
        if (playerUIView == null) {
            NSLog("There is no playerUIView.")
            return@runCatching
        }

        val playerLayer = findAVPlayerLayer(playerUIView)
        if (playerLayer == null) {
            NSLog("There is no playerLayer.")
            return@runCatching
        }

        if (playerLayer.player == null) {
            playerLayer.player = player.impl as AVPlayer
        }

        // 创建画中画控制器
        pipController = AVPictureInPictureController(playerLayer).apply {
            delegate = pipDelegate
            // 启用自动画中画恢复
            canStartPictureInPictureAutomaticallyFromInline = true
        }
        NSLog("PictureInPictureController initialized successfully")

    }.onFailure { NSLog("Failed to setup PiP controller: ${it.message}") }

    private val isPictureInPictureSupported: Boolean
        get() = AVPictureInPictureController.isPictureInPictureSupported() &&
                player is AVKitMediampPlayer

    @OptIn(ExperimentalForeignApi::class)
    actual fun enterPictureInPictureMode(rect: Rect) {
        if (!::pipController.isInitialized) {
            NSLog("Picture-in-Picture is not initialized")
            return
        }

        if (!isPictureInPictureSupported) {
            NSLog("Picture-in-Picture is not supported")
            return
        }

        runCatching {
            // 预检查画中画可用性
            if (pipController.isPictureInPicturePossible()) {
                dispatch_async(dispatch_get_main_queue()) {
                    if (pipController.isPictureInPictureActive()) {
                        pipController.stopPictureInPicture()
                    } else {
                        pipController.startPictureInPicture()
                    }
                }
            } else {
                NSLog("Picture-in-Picture is not possible at this time")
            }
        }.onFailure {
            NSLog("Failed to enter Picture-in-Picture mode: ${it.message}")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getKeyWindow(): UIWindow? = UIApplication.sharedApplication().let { app ->
        if (app.respondsToSelector(sel_registerName("connectedScenes"))) {
            app.connectedScenes
                .asSequence()
                .filterIsInstance<UIWindowScene>()
                .flatMap { it.windows.asSequence() }
                .filterIsInstance<UIWindow>()
                .firstOrNull { it.isKeyWindow() }
        } else app.keyWindow
    }

    fun findPlayerUIView(view: UIView): PlayerUIView? {
        if (view is PlayerUIView) {
            return view
        }

        // 遍历子视图
        for (subview in view.subviews) {
            val result = findPlayerUIView(subview as UIView)
            if (result != null) {
                return result // 找到匹配的视图，返回
            }
        }

        return null // 未找到匹配的视图
    }

    fun findAVPlayerLayer(playerUIView: PlayerUIView): AVPlayerLayer? {
        return playerUIView.layer as? AVPlayerLayer
    }

    fun cleanup() {
        if (::pipController.isInitialized) {
            pipController.delegate = null
        }
    }

    override fun onRemembered() {
        val delay: Long = 1
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delay), dispatch_get_main_queue()) {
            initializePipController()
        }
    }

    override fun onForgotten() {
        cleanup()
    }

    override fun onAbandoned() {
        cleanup()
    }
}