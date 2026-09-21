/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.ui.qrlogin

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

actual val isQrCodeScannerSupported: Boolean get() = true

@Composable
actual fun QrCodeScanner(
    onScanned: (String) -> Unit,
    torchEnabled: Boolean,
    permissionDeniedContent: @Composable (requestPermission: () -> Unit) -> Unit,
    modifier: Modifier,
) {
    var hasPermission by remember {
        mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized)
    }
    val requestPermission: () -> Unit = {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusNotDetermined) {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) { hasPermission = granted }
            }
        } else {
            // 系统只询问一次, 之后只能到设置里打开
            val settings = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
            if (settings != null) UIApplication.sharedApplication.openURL(settings, emptyMap<Any?, Any>(), null)
        }
    }
    LaunchedEffect(Unit) {
        if (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusNotDetermined) {
            requestPermission()
        }
    }

    Box(modifier) {
        if (hasPermission) {
            CameraPreview(onScanned, torchEnabled, Modifier.matchParentSize())
        } else {
            permissionDeniedContent(requestPermission)
        }
    }
}

@Composable
private fun CameraPreview(onScanned: (String) -> Unit, torchEnabled: Boolean, modifier: Modifier) {
    val onScannedUpdated by rememberUpdatedState(onScanned)
    val controller = remember { QrCaptureController { onScannedUpdated(it) } }

    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.stop() }
    }
    LaunchedEffect(controller, torchEnabled) {
        controller.setTorch(torchEnabled)
    }

    UIKitView(
        factory = { controller.view },
        modifier = modifier,
    )
}

/**
 * 相机会话 + 系统自带的二维码识别 ([AVCaptureMetadataOutput]). 回调都在主线程.
 */
private class QrCaptureController(onScanned: (String) -> Unit) {
    private val session = AVCaptureSession()
    private val device: AVCaptureDevice? = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    val view: UIView = object : UIView(frame = CGRectZero.readValue()) {
        override fun layoutSubviews() {
            super.layoutSubviews()
            // 跟随 Compose 给的大小, 不要隐式动画
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            previewLayer.setFrame(bounds)
            CATransaction.commit()
        }
    }.apply {
        backgroundColor = UIColor.blackColor
        layer.addSublayer(previewLayer)
    }

    // AVCaptureMetadataOutput 只弱引用 delegate
    private val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputMetadataObjects: List<*>,
            fromConnection: AVCaptureConnection,
        ) {
            for (obj in didOutputMetadataObjects) {
                val text = (obj as? AVMetadataMachineReadableCodeObject)?.stringValue ?: continue
                onScanned(text)
            }
        }
    }

    init {
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input != null && session.canAddInput(input)) {
            session.addInput(input)
            val output = AVCaptureMetadataOutput()
            if (session.canAddOutput(output)) {
                session.addOutput(output)
                output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
                // 必须在 addOutput 之后设置, 否则可用类型为空
                output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            }
        }
    }

    // startRunning / stopRunning 会阻塞, 不能在主线程调用. 串行队列保证 start / stop / 手电筒的先后顺序
    private val sessionQueue = dispatch_queue_create("me.him188.ani.qr-scanner", null)

    fun start() = onSessionQueue { if (!session.running) session.startRunning() }

    fun stop() = onSessionQueue {
        applyTorch(false)
        if (session.running) session.stopRunning()
    }

    fun setTorch(enabled: Boolean) = onSessionQueue { applyTorch(enabled) }

    private fun applyTorch(enabled: Boolean) {
        val device = device?.takeIf { it.hasTorch } ?: return
        if (!device.lockForConfiguration(null)) return
        device.torchMode = if (enabled) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        device.unlockForConfiguration()
    }

    private fun onSessionQueue(block: () -> Unit) {
        dispatch_async(sessionQueue, block)
    }
}
