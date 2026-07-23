/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import me.him188.ani.app.data.Res

/**
 * 随应用交付的图片验证码 ONNX 模型在 Compose 资源中的路径。
 *
 * 模型只保存一份, 位于 `app-data/src/commonMain/composeResources/files/`, 由 Compose Multiplatform
 * 统一打包到 Android assets、Desktop classpath 与 iOS app bundle。三端识别器都通过
 * [readImageCaptchaModelBytes] 读取这同一份资源。
 */
internal const val IMAGE_CAPTCHA_MODEL_RESOURCE = "files/captcha-v1.0.onnx"

/**
 * 读取随应用交付的 `captcha-v1.0` ONNX 模型字节。Android 与 Desktop 使用此入口:
 * 它们的资源分别在 apk / jar 内, 不是文件系统路径, 只能以字节形式交给 ONNX Runtime。
 */
suspend fun readImageCaptchaModelBytes(): ByteArray = Res.readBytes(IMAGE_CAPTCHA_MODEL_RESOURCE)

/**
 * 返回随应用交付的 `captcha-v1.0` ONNX 模型资源的 URI。仅 iOS 使用:
 * iOS 上 Compose 资源是 app bundle 内的真实文件, 而 ONNX Runtime 的 Objective-C 接口只接受文件路径,
 * 因此可将该 `file://` URI 解析为路径后直接交给 `ORTSession`, 无需读入字节。
 */
fun imageCaptchaModelUri(): String = Res.getUri(IMAGE_CAPTCHA_MODEL_RESOURCE)
