/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.danmaku

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.danmaku.ui.DanmakuStyle
import me.him188.ani.danmaku.ui.DanmakuTextConversion

object DanmakuConfigSerializer : KSerializer<DanmakuConfig> {

    @Serializable
    private class DanmakuConfigData(
        val style: DanmakuStyleData = DanmakuStyleData(),
        val speed: Float = DanmakuConfig.Default.speed,
        val safeSeparation: Float = DanmakuConfig.Default.safeSeparation.value,
        val displayArea: Float = DanmakuConfig.Default.displayArea,
        val enableColor: Boolean = DanmakuConfig.Default.enableColor,
        val enableTop: Boolean = DanmakuConfig.Default.enableTop,
        val enableFloating: Boolean = DanmakuConfig.Default.enableFloating,
        val enableBottom: Boolean = DanmakuConfig.Default.enableBottom,
        val textConversion: DanmakuTextConversion = DanmakuConfig.Default.textConversion,
        val isDebug: Boolean = DanmakuConfig.Default.isDebug,
    )

    /**
     * @see DanmakuStyle
     */
    @Serializable
    private class DanmakuStyleData(
        val fontSize: Float = DanmakuStyle.Default.fontSize.value,
        val fontWeight: Int = DanmakuStyle.Default.fontWeight.weight,
        val alpha: Float = DanmakuStyle.Default.alpha,
        val strokeColor: ULong = DanmakuStyle.Default.strokeColor.value,
        val strokeWidth: Float = DanmakuStyle.Default.strokeWidth,
    )

    override val descriptor: SerialDescriptor = DanmakuConfigData.serializer().descriptor

    override fun deserialize(decoder: Decoder): DanmakuConfig {
        val value = DanmakuConfigData.serializer().deserialize(decoder)

        return DanmakuConfig(
            style = DanmakuStyle(
                fontSize = value.style.fontSize.sp,
                fontWeight = FontWeight(value.style.fontWeight),
                alpha = value.style.alpha,
                strokeColor = Color(value.style.strokeColor),
                strokeWidth = value.style.strokeWidth,
            ),
            speed = value.speed,
            safeSeparation = value.safeSeparation.dp,
            displayArea = value.displayArea,
            enableColor = value.enableColor,
            enableTop = value.enableTop,
            enableFloating = value.enableFloating,
            enableBottom = value.enableBottom,
            textConversion = value.textConversion,
            isDebug = value.isDebug,
        )
    }

    override fun serialize(encoder: Encoder, value: DanmakuConfig) {
        val data = DanmakuConfigData(
            style = DanmakuStyleData(
                fontSize = value.style.fontSize.value,
                fontWeight = value.style.fontWeight.weight,
                alpha = value.style.alpha,
                strokeColor = value.style.strokeColor.value,
                strokeWidth = value.style.strokeWidth,
            ),
            speed = value.speed,
            safeSeparation = value.safeSeparation.value,
            displayArea = value.displayArea,
            enableColor = value.enableColor,
            enableTop = value.enableTop,
            enableFloating = value.enableFloating,
            enableBottom = value.enableBottom,
            textConversion = value.textConversion,
            isDebug = value.isDebug,
        )

        return DanmakuConfigData.serializer().serialize(encoder, data)
    }
}