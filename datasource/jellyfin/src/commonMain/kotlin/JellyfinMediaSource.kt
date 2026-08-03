/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.jellyfin

import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.MediaPreviewThumbnails
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.get
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.MediaSourceParametersBuilder
import me.him188.ani.datasources.api.source.parameter.hasValue
import me.him188.ani.utils.ktor.ScopedHttpClient

class JellyfinMediaSource(
    config: MediaSourceConfig,
    client: ScopedHttpClient,
    override val mediaSourceId: String = ID,
) : BaseJellyfinMediaSource(client) {
    companion object {
        const val ID = "jellyfin"
        const val AUTH_MODE_API_KEY = "apiKey"
        const val AUTH_MODE_USERNAME_PASSWORD = "usernamePassword"

        val INFO = MediaSourceInfo(
            displayName = "Jellyfin",
            description = "Jellyfin Media Server",
            websiteUrl = "https://jellyfin.org",
            iconUrl = "https://jellyfin.org/images/favicon.ico",
        )
    }

    object Parameters : MediaSourceParametersBuilder() {
        val baseUrl = string(
            "baseUrl",
            defaultProvider = { "http://localhost:8096" },
            description = "服务器地址\n示例: http://localhost:8096",
        )
        val authMode = simpleEnum(
            "authMode",
            AUTH_MODE_API_KEY,
            AUTH_MODE_USERNAME_PASSWORD,
            default = AUTH_MODE_API_KEY,
            description = "认证方式: apiKey 使用 User ID 与 API Key; usernamePassword 使用用户名与密码登录",
        )
        val userId = string(
            "userId",
            description = "仅 API Key 模式使用。可在 Jellyfin \"控制台 - 用户\" 中选择一个用户, 在浏览器地址栏找到 \"userId=\" 后面的内容",
            visibleWhen = authMode.hasValue(AUTH_MODE_API_KEY),
        )
        val apikey = string(
            "apikey",
            description = "仅 API Key 模式使用。可在 Jellyfin \"控制台 - API 秘钥\" 中添加",
            visibleWhen = authMode.hasValue(AUTH_MODE_API_KEY),
        )
        // SECURITY: Legacy media-source parameters are serialized as plain text in
        // MediaSourceConfig.arguments. Password authentication intentionally uses the same local
        // storage path as API-key authentication; neither secret is protected by an OS credential
        // store. An HTTP base URL also sends the login password without transport encryption.
        // Code handling logs, exports, or diagnostics must redact both secrets.
        val username = string(
            "username",
            description = "仅用户名密码模式使用",
            visibleWhen = authMode.hasValue(AUTH_MODE_USERNAME_PASSWORD),
        )
        val password = string(
            "password",
            description = "仅用户名密码模式使用",
            visibleWhen = authMode.hasValue(AUTH_MODE_USERNAME_PASSWORD),
        )
    }

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId(ID)

        override val parameters: MediaSourceParameters = Parameters.build()
        override val info: MediaSourceInfo get() = INFO
        override val allowMultipleInstances: Boolean get() = true
        override fun create(
            mediaSourceId: String,
            config: MediaSourceConfig,
            client: ScopedHttpClient
        ): MediaSource = JellyfinMediaSource(config, client, mediaSourceId = mediaSourceId)
    }

    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val info: MediaSourceInfo = INFO
    override val baseUrl = config[Parameters.baseUrl].removeSuffix("/")
    override val itemFields: String = "MediaStreams,Chapters,Trickplay"
    private val authMode = config[Parameters.authMode]
    private val userId = config[Parameters.userId]
    private val apiKey = config[Parameters.apikey]
    private val passwordAuthenticator = if (authMode == AUTH_MODE_USERNAME_PASSWORD) {
        JellyfinPasswordAuthenticator(
            baseUrl = baseUrl,
            username = config[Parameters.username],
            password = config[Parameters.password],
            deviceId = "animeko-$mediaSourceId",
            client = client,
        )
    } else {
        null
    }

    internal override fun createPreviewThumbnails(
        itemId: String,
        trickplay: Map<String, Map<String, JellyfinTrickplayManifestDto>>?,
    ) = createJellyfinPreviewThumbnails(baseUrl, mediaSourceId, itemId, trickplay)

    override suspend fun getAuthorization(): Authorization {
        return when (authMode) {
            AUTH_MODE_API_KEY -> {
                require(userId.isNotBlank()) { "Jellyfin userId must not be blank in API Key mode" }
                require(apiKey.isNotBlank()) { "Jellyfin API Key must not be blank in API Key mode" }
                Authorization(
                    userId = userId,
                    accessToken = apiKey,
                    headerValue = """MediaBrowser Token="$apiKey"""",
                )
            }

            AUTH_MODE_USERNAME_PASSWORD -> {
                val session = checkNotNull(passwordAuthenticator).getSession()
                Authorization(
                    userId = session.userId,
                    accessToken = session.accessToken,
                    headerValue = session.authorizationHeader,
                )
            }

            else -> error("Unknown Jellyfin authentication mode: $authMode")
        }
    }

    override suspend fun invalidateAuthorization(authorization: Authorization): Boolean {
        val authenticator = passwordAuthenticator ?: return false
        authenticator.invalidate(authorization.accessToken)
        return true
    }

    override fun getDownloadUri(itemId: String, accessToken: String): String {
        return "$baseUrl/Items/$itemId/Download?ApiKey=$accessToken"
    }
}

@Serializable
@Suppress("PropertyName")
internal data class JellyfinTrickplayManifestDto(
    val Width: Int = 0,
    val Height: Int = 0,
    val TileWidth: Int = 0,  // columns
    val TileHeight: Int = 0, // rows
    val ThumbnailCount: Int = 0,
    val Interval: Long = 0,  // in milliseconds
)

internal fun createJellyfinPreviewThumbnails(
    baseUrl: String,
    requesterMediaSourceId: String,
    itemId: String,
    trickplay: Map<String, Map<String, JellyfinTrickplayManifestDto>>?,
): MediaPreviewThumbnails? {
    val validMediaSources = trickplay.orEmpty().mapNotNull { (mediaSourceId, manifests) ->
        val validManifests = manifests.values.filter {
            it.Width > 0 && it.Height > 0 && it.TileWidth > 0 && it.TileHeight > 0 &&
                it.ThumbnailCount > 0 && it.Interval > 0
        }
        val manifest = validManifests.firstOrNull { it.Width == 320 }
            ?: validManifests.minByOrNull { it.Width }
            ?: return@mapNotNull null
        mediaSourceId to manifest
    }
    val (mediaSourceId, manifest) = validMediaSources.firstOrNull { (mediaSourceId) -> mediaSourceId == itemId }
        ?: validMediaSources.singleOrNull()
        ?: return null
    return MediaPreviewThumbnails(
        width = manifest.Width,
        height = manifest.Height,
        intervalMillis = manifest.Interval,
        totalCount = manifest.ThumbnailCount,
        layout = MediaPreviewThumbnails.Layout.SpriteTile(
            columns = manifest.TileWidth,
            rows = manifest.TileHeight,
            urlPattern =
                "$baseUrl/Videos/$itemId/Trickplay/${manifest.Width}/{tileIndex}.jpg?MediaSourceId=$mediaSourceId",
        ),
        requesterMediaSourceId = requesterMediaSourceId,
    )
}
