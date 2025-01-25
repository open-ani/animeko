/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.ikaros

import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.get
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.MediaSourceParametersBuilder
import me.him188.ani.utils.ktor.WrapperHttpClient
import java.nio.charset.StandardCharsets
import java.util.Base64

class IkarosMediaSource(
    override val mediaSourceId: String,
    config: MediaSourceConfig,
    httpClient: WrapperHttpClient,
) : HttpMediaSource() {
    companion object {
        const val ID = "ikaros"
        val INFO = MediaSourceInfo(
            displayName = "Ikaros",
            description = "专注于 ACGMN 的内容管理系统 (CMS)",
            websiteUrl = "https://docs.ikaros.run",
            iconUrl = "https://docs.ikaros.run/img/favicon.ico",
        )
    }

    private val client = IkarosClient(
        config[Parameters.baseUrl],
        httpClient,
        addAuthorizationHeaders = {
            val username = config[Parameters.username]
            val password = config[Parameters.password]
            header(
                HttpHeaders.Authorization,
                "Basic " + Base64.getEncoder()
                    .encodeToString(
                        "$username:$password".toByteArray(
                            StandardCharsets.UTF_8,
                        ),
                    ),
            )
        },
    )

    object Parameters : MediaSourceParametersBuilder() {
        val baseUrl = string("baseUrl", description = "API base URL")
        val username = string("username", description = "用户名")
        val password = string("password", description = "密码")
    }

    class Factory : MediaSourceFactory {
        override val factoryId: FactoryId get() = FactoryId(ID)

        override val parameters: MediaSourceParameters = Parameters.build()
        override val allowMultipleInstances: Boolean get() = true
        override fun create(
            mediaSourceId: String,
            config: MediaSourceConfig,
            client: WrapperHttpClient
        ): MediaSource =
            IkarosMediaSource(mediaSourceId, config, client)

        override val info: MediaSourceInfo get() = INFO
    }

    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val info: MediaSourceInfo get() = INFO

    override suspend fun checkConnection(): ConnectionStatus {
        return if ((HttpStatusCode.OK == client.checkConnection())
        ) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
    }

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
        val emptySizeSource = IkarosSizeSource(
            totalSize = flowOf(0), finished = flowOf(false), results = flowOf(),
        )
        try {
            val bgmTvSubjectId = checkNotNull(query.subjectId)
            val episodeSort = checkNotNull(query.episodeSort)
            val subjectSyncs = client.getSubjectSyncsWithBgmTvSubjectId(bgmTvSubjectId)
            if (subjectSyncs.isEmpty()) {
                return emptySizeSource
            }
            val subjectId = subjectSyncs[0].subjectId
            val episodeRecords = client.getEpisodeRecordsWithId(subjectId.toString())
            if (episodeRecords.isEmpty()) {
                return emptySizeSource
            }
            return client.episodeRecords2SizeSource(subjectId.toString(), episodeRecords, episodeSort)
        } catch (exception: RuntimeException) {
            logger.error("Request fail: ", exception);
        }
        return emptySizeSource
    }
}
