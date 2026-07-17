/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.Context
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.captcha.ImageCaptchaSample
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.nio.FloatBuffer

/** Android 图片验证码识别器，使用随应用交付的 captcha-v1.0 ONNX 模型。 */
class AndroidOnnxImageCaptchaRecognizer(
    context: Context,
) : ImageCaptchaRecognizer {
    private val applicationContext = context.applicationContext
    private val environment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OrtEnvironment.getEnvironment()
    }
    private val session by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val model = applicationContext.assets.open(MODEL_ASSET_PATH).use { it.readBytes() }
        OrtSession.SessionOptions().use { options ->
            environment.createSession(model, options).also {
                logger.info { "Loaded image captcha model from $MODEL_ASSET_PATH" }
            }
        }
    }

    override suspend fun recognize(sample: ImageCaptchaSample): String? = withContext(Dispatchers.Default) {
        runCatching { recognizeBlocking(sample.bytes) }
            .onSuccess { answer ->
                logger.debug { "Image captcha recognition result for ${sample.sourceUrl}: ${answer ?: "none"}" }
            }
            .onFailure { exception ->
                logger.warn(exception) { "Failed to recognize image captcha from ${sample.sourceUrl}" }
            }
            .getOrNull()
    }

    private fun recognizeBlocking(bytes: ByteArray): String? {
        val input = preprocess(bytes) ?: return null
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(input),
            longArrayOf(1, 1, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong()),
        ).use { tensor ->
            session.run(mapOf(INPUT_NAME to tensor)).use { result ->
                val logits = readLogits(result)
                if (logits.size != CAPTCHA_LENGTH || logits.any { it.size != DIGIT_COUNT }) {
                    return null
                }
                return logits.joinToString(separator = "") { positionLogits ->
                    positionLogits.indices.maxBy { positionLogits[it] }.toString()
                }
            }
        }
    }

    private fun preprocess(bytes: ByteArray): FloatArray? {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val resized = Bitmap.createScaledBitmap(source, INPUT_WIDTH, INPUT_HEIGHT, false)
        return try {
            val colors = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
            resized.getPixels(colors, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
            FloatArray(colors.size) { index ->
                val color = colors[index]
                val red = color shr 16 and 0xff
                val green = color shr 8 and 0xff
                val blue = color and 0xff
                val gray = (299 * red + 587 * green + 114 * blue + 500) / 1000
                gray / 255f
            }
        } finally {
            if (resized !== source) resized.recycle()
            source.recycle()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readLogits(result: OrtSession.Result): Array<FloatArray> {
        val batch = result[OUTPUT_NAME].get().value as Array<Array<FloatArray>>
        return batch.single()
    }

    private companion object {
        private val logger = logger<AndroidOnnxImageCaptchaRecognizer>()

        private const val MODEL_ASSET_PATH = "captcha/captcha-v1.0.onnx"
        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "logits"
        private const val INPUT_WIDTH = 96
        private const val INPUT_HEIGHT = 32
        private const val CAPTCHA_LENGTH = 4
        private const val DIGIT_COUNT = 10
    }
}
