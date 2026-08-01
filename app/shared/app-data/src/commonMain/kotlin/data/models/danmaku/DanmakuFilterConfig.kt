package me.him188.ani.app.data.models.danmaku

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.utils.platform.annotations.SerializationOnly

/**
 * Configuration for danmaku filters.
 */
@Immutable
@Serializable
data class DanmakuFilterConfig @SerializationOnly constructor(
    val enableRegexFilter: Boolean = true,
    /**
     * 合并内容重复的弹幕. 见 `DanmakuMerger`.
     */
    val enableMerge: Boolean = false,
    @Suppress("PropertyName") @Transient val _placeholder: Int = 0
) {
    companion object {
        @OptIn(SerializationOnly::class)
        @Stable
        val Default = DanmakuFilterConfig()
    }
}
