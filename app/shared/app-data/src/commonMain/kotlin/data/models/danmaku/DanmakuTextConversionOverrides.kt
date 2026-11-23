package me.him188.ani.app.data.models.danmaku

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import me.him188.ani.utils.platform.annotations.SerializationOnly

/**
 * 按弹幕来源覆盖全局的简繁转换目标.
 *
 * 键为 [me.him188.ani.danmaku.api.DanmakuServiceId.value],
 * 未出现在 [overrides] 中的来源跟随全局设置.
 *
 * @see me.him188.ani.app.domain.danmaku.DanmakuTextConversionSettings
 */
@Immutable
@Serializable
data class DanmakuTextConversionOverrides @SerializationOnly constructor(
    val overrides: Map<String, DanmakuTextConversion> = emptyMap(),
) {
    companion object {
        @OptIn(SerializationOnly::class)
        val Default = DanmakuTextConversionOverrides()
    }
}
