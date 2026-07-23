/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package me.him188.ani.app.ios

import cocoapods.onnxruntime_objc.ORTEnv
import cocoapods.onnxruntime_objc.ORTLoggingLevel
import cocoapods.onnxruntime_objc.ORTSession
import cocoapods.onnxruntime_objc.ORTSessionOptions
import cocoapods.onnxruntime_objc.ORTTensorElementDataType
import cocoapods.onnxruntime_objc.ORTValue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaSample
import me.him188.ani.app.domain.mediasource.web.captcha.imageCaptchaModelUri
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.CoreGraphics.kCGInterpolationNone
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithLength
import platform.Foundation.numberWithLongLong
import platform.UIKit.UIImage
import platform.posix.memcpy

/** iOS 图片验证码识别器，使用随应用交付的 captcha-v1.0 ONNX 模型。 */
class IosOnnxImageCaptchaRecognizer : ImageCaptchaRecognizer {
    private val environment by lazy {
        callOrt { error ->
            ORTEnv(
                loggingLevel = ORTLoggingLevel.ORTLoggingLevelWarning,
                error = error,
            )
        }
    }
    private val session by lazy {
        val modelPath = resolveModelPath(imageCaptchaModelUri())
        val options = callOrt { error -> ORTSessionOptions(error = error) }
        callOrt { error ->
            ORTSession(
                env = environment,
                modelPath = modelPath,
                sessionOptions = options,
                error = error,
            )
        }.also {
            logger.info { "Loaded image captcha model from $modelPath" }
        }
    }

    override suspend fun recognize(sample: ImageCaptchaSample): String? = withContext(Dispatchers.Default) {
        runCatching { recognizeBlocking(session, sample.bytes) }
            .onSuccess { answer ->
                logger.debug { "Image captcha recognition result for ${sample.sourceUrl}: ${answer ?: "none"}" }
            }
            .onFailure { exception ->
                logger.warn(exception) { "Failed to recognize image captcha from ${sample.sourceUrl}" }
            }
            .getOrNull()
    }

    private fun resolveModelPath(uri: String): String =
        checkNotNull(NSURL.URLWithString(uri)?.path) {
            "Cannot resolve image captcha model path from $uri"
        }

    private fun recognizeBlocking(session: ORTSession, bytes: ByteArray): String? {
        val input = preprocess(bytes) ?: return null
        val tensorData = NSMutableData.dataWithLength((input.size * Float.SIZE_BYTES).convert())
            ?: return null
        input.usePinned { pinned ->
            memcpy(tensorData.mutableBytes, pinned.addressOf(0), (input.size * Float.SIZE_BYTES).convert())
        }
        val tensor = callOrt { error ->
            ORTValue(
                tensorData = tensorData,
                elementType = ORTTensorElementDataType.ORTTensorElementDataTypeFloat,
                shape = listOf(
                    NSNumber.numberWithLongLong(1),
                    NSNumber.numberWithLongLong(1),
                    NSNumber.numberWithLongLong(INPUT_HEIGHT.toLong()),
                    NSNumber.numberWithLongLong(INPUT_WIDTH.toLong()),
                ),
                error = error,
            )
        }
        @Suppress("UNCHECKED_CAST")
        val outputs = callOrt { error ->
            session.runWithInputs(
                inputs = mapOf(INPUT_NAME to tensor) as Map<Any?, Any?>,
                outputNames = setOf(OUTPUT_NAME) as Set<Any?>,
                runOptions = null,
                error = error,
            )
        }
        val output = outputs[OUTPUT_NAME] as? ORTValue ?: return null
        val outputData = callOrt { error -> output.tensorDataWithError(error) }
        val values = outputData.bytes?.reinterpret<FloatVar>() ?: return null
        return buildString(CAPTCHA_LENGTH) {
            repeat(CAPTCHA_LENGTH) { position ->
                val offset = position * DIGIT_COUNT
                val digit = (0 until DIGIT_COUNT).maxBy { values[offset + it] }
                append(digit)
            }
        }
    }

    private fun preprocess(bytes: ByteArray): FloatArray? {
        if (bytes.isEmpty()) return null
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.convert())
        }
        val image = UIImage(data = data)
        val cgImage = image.CGImage ?: return null
        val rgba = ByteArray(INPUT_WIDTH * INPUT_HEIGHT * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        try {
            rgba.usePinned { pinned ->
                val context = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = INPUT_WIDTH.convert(),
                    height = INPUT_HEIGHT.convert(),
                    bitsPerComponent = 8.convert(),
                    bytesPerRow = (INPUT_WIDTH * 4).convert(),
                    space = colorSpace,
                    bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or
                        kCGBitmapByteOrder32Big,
                ) ?: return null
                try {
                    CGContextSetInterpolationQuality(context, kCGInterpolationNone)
                    CGContextDrawImage(
                        context,
                        CGRectMake(0.0, 0.0, INPUT_WIDTH.toDouble(), INPUT_HEIGHT.toDouble()),
                        cgImage,
                    )
                } finally {
                    @Suppress("DEPRECATION")
                    CGContextRelease(context)
                }
            }
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
        return FloatArray(INPUT_WIDTH * INPUT_HEIGHT) { index ->
            val offset = index * 4
            val red = rgba[offset].toInt() and 0xff
            val green = rgba[offset + 1].toInt() and 0xff
            val blue = rgba[offset + 2].toInt() and 0xff
            val gray = (299 * red + 587 * green + 114 * blue + 500) / 1000
            gray / 255f
        }
    }

    private companion object {
        private val logger = logger<IosOnnxImageCaptchaRecognizer>()

        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "logits"
        private const val INPUT_WIDTH = 96
        private const val INPUT_HEIGHT = 32
        private const val CAPTCHA_LENGTH = 4
        private const val DIGIT_COUNT = 10
    }
}

private fun <T : Any> callOrt(block: (CPointer<ObjCObjectVar<NSError?>>) -> T?): T = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val value = block(error.ptr)
    error.value?.let { throw IllegalStateException(it.localizedDescription) }
    checkNotNull(value) { "ONNX Runtime returned no value" }
}
