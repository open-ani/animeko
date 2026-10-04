/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.remote.settings

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.json.Json

object RemoteSettingsProtocol {
    const val VERSION = 1
    const val SCHEMA_VERSION = 1
    const val MAX_REQUEST_BYTES = 2 * 1024 * 1024
    const val MAX_LOG_BYTES = 2 * 1024 * 1024
    /**
     * Fields added by a newer app version are ignored by an older one, so adding a field to a wire
     * model keeps both versions compatible. Every known field is always written, which lets the
     * receiver tell a field the sender does not know from one it left at its default.
     */
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

/** Process-scoped connection credentials. Never persist this object or include its URI in logs. */
class RemoteSettingsLink(
    val ip: String,
    val port: Int,
    val accessKey: String,
    val appVersion: String,
    val userUuid: String,
    val protocolVersion: Int = RemoteSettingsProtocol.VERSION,
) {
    init {
        require(isPrivateIpv4(ip)) { "需要局域网 IPv4 地址" }
        require(port in 1..65535) { "端口无效" }
        require(uuid.matches(accessKey)) { "访问密钥无效" }
        require(userUuid.isEmpty() || uuid.matches(userUuid)) { "用户 ID 无效" }
        require(appVersion.length in 1..128) { "应用版本无效" }
    }

    val baseUrl: String
        get() = "http://$ip:$port"

    fun toUri(): String =
        URLBuilder()
            .apply {
                protocol = URLProtocol.createOrDefault("ani")
                host = "remote-settings"
                parameters.append("ip", ip)
                parameters.append("port", this@RemoteSettingsLink.port.toString())
                parameters.append("accessKey", accessKey)
                parameters.append("appVersion", appVersion)
                parameters.append("userUuid", userUuid)
                parameters.append("protocolVersion", protocolVersion.toString())
            }
            .buildString()

    override fun toString(): String = "RemoteSettingsLink($ip:$port, credentials=redacted)"

    companion object {
        private val uuid =
            Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        fun parse(text: String): RemoteSettingsLink {
            require(text.length <= 2048) { "二维码内容过长" }
            val url =
                try {
                    Url(text)
                } catch (_: Exception) {
                    throw IllegalArgumentException("二维码链接无效")
                }
            require(url.protocol.name == "ani" && url.host == "remote-settings") { "不是电视远程设置二维码" }
            require(
                url.encodedPath in listOf("", "/") &&
                    url.fragment.isEmpty() &&
                    url.user == null &&
                    url.password == null
            ) {
                "二维码链接无效"
            }
            fun parameter(name: String): String {
                val values = url.parameters.getAll(name)
                require(values?.size == 1) { "二维码参数无效: $name" }
                return values.single()
            }
            return RemoteSettingsLink(
                parameter("ip"),
                parameter("port").toIntOrNull() ?: throw IllegalArgumentException("端口无效"),
                parameter("accessKey"),
                parameter("appVersion"),
                parameter("userUuid"),
                parameter("protocolVersion").toIntOrNull()
                    ?: throw IllegalArgumentException("协议版本无效"),
            )
        }

        fun isPrivateIpv4(address: String): Boolean {
            val parts = address.split('.')
            if (
                parts.size != 4 ||
                    parts.any {
                        it.isEmpty() ||
                            it.any { c -> c !in '0'..'9' } ||
                            (it.length > 1 && it[0] == '0')
                    }
            )
                return false
            val bytes = parts.map { it.toIntOrNull() ?: return false }
            if (bytes.any { it !in 0..255 }) return false
            return bytes[0] == 10 ||
                bytes[0] == 172 && bytes[1] in 16..31 ||
                bytes[0] == 192 && bytes[1] == 168
        }
    }
}
