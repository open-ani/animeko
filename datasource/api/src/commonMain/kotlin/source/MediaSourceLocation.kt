package me.him188.ani.datasources.api.source

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import me.him188.ani.datasources.api.Media

/**
 * 数据源 [MediaSource] 以及资源 [Media] 的存放位置.
 */
@Serializable(MediaSourceLocation.AsStringSerializer::class)
sealed class MediaSourceLocation {
    /**
     * 资源位于公共网络. 例如一个在线视频网站, 或者 BitTorrent 网络.
     */
    data object Online : MediaSourceLocation()

    /**
     * 资源位于当前局域网内 (下载很快延迟很低). 例如 NAS 或自建的视频服务器.
     * 如果需要通过公网访问自建视频服务器, 那该服务器属于 [Online] 而不是 [Lan].
     */
    data object Lan : MediaSourceLocation()

    /**
     * 资源位于本地文件系统. 必须是能通过 `File` 直接访问的.
     */
    data object Local : MediaSourceLocation()

    companion object {
        // A subclass can initialize the superclass before its own INSTANCE is assigned on JVM.
        val entries get() = listOf(Online, Lan, Local)
    }

    internal object AsStringSerializer : KSerializer<MediaSourceLocation> {
        override val descriptor = String.serializer().descriptor

        override fun serialize(encoder: Encoder, value: MediaSourceLocation) {
            encoder.encodeString(
                getText(value),
            )
        }

        private fun getText(value: MediaSourceLocation) = when (value) {
            Online -> "ONLINE"
            Lan -> "LAN"
            Local -> "LOCAL"
        }

        override fun deserialize(decoder: Decoder): MediaSourceLocation {
            val string = decoder.decodeString()
            for (entry in entries) { // type safe
                if (getText(entry) == string) {
                    return entry
                }
            }
            throw SerializationException("Unknown MediaSourceLocation: $string")
        }
    }
}

fun MediaSourceLocation.isLowEffort(): Boolean = this is MediaSourceLocation.Lan || this is MediaSourceLocation.Local
