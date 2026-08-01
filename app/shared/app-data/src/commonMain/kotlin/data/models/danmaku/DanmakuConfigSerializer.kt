/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.danmaku

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
        /**
         * `null` 表示不画阴影. 旧版本的配置里没有这个字段, 默认就是 `null`.
         */
        val shadow: DanmakuShadowData? = null,
    )

    /**
     * @see Shadow
     */
    @Serializable
    private class DanmakuShadowData(
        val color: ULong,
        val offsetX: Float,
        val offsetY: Float,
        val blurRadius: Float,
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
                shadow = value.style.shadow?.let {
                    Shadow(
                        color = Color(it.color),
                        offset = Offset(it.offsetX, it.offsetY),
                        blurRadius = it.blurRadius,
                    )
                },
            ),
            speed = value.speed,
            safeSeparation = value.safeSeparation.dp,
            displayArea = value.displayArea,
            enableColor = value.enableColor,
            enableTop = value.enableTop,
            enableFloating = value.enableFloating,
            enableBottom = value.enableBottom,
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
                shadow = value.style.shadow?.let {
                    DanmakuShadowData(
                        color = it.color.value,
                        offsetX = it.offset.x,
                        offsetY = it.offset.y,
                        blurRadius = it.blurRadius,
                    )
                },
            ),
            speed = value.speed,
            safeSeparation = value.safeSeparation.value,
            displayArea = value.displayArea,
            enableColor = value.enableColor,
            enableTop = value.enableTop,
            enableFloating = value.enableFloating,
            enableBottom = value.enableBottom,
            isDebug = value.isDebug,
        )

        return DanmakuConfigData.serializer().serialize(encoder, data)
    }
}