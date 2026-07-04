/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.video

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import me.him188.ani.utils.ktor.UrlHelpers

interface VideoUrlProbeEngine {
    suspend fun probe(url: String, headers: Map<String, String>): VideoProbeResult
}

class VideoProbe(
    private val httpClient: HttpClient,
) : VideoUrlProbeEngine {
    override suspend fun probe(
        url: String,
        headers: Map<String, String>,
    ): VideoProbeResult {
        return runCatching {
            val response = request(url, headers)
            val contentType = response.contentType()?.toString()
            val finalUrl = response.request.url.toString()

            if (url.lowercase().contains(".m3u8") || contentType?.contains("mpegurl", ignoreCase = true) == true) {
                val playlist = response.bodyAsText()
                val entries = playlist.lineSequence()
                    .map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
                if (entries.isEmpty()) {
                    return@runCatching VideoProbeResult(
                        ok = false,
                        url = url,
                        finalUrl = finalUrl,
                        kind = "m3u8",
                        statusCode = response.status.value,
                        contentType = contentType,
                        headers = response.headersSnapshot(),
                        summary = "Playlist loaded but contains no playable entries",
                        playlistEntries = 0,
                        errors = listOf("Empty playlist"),
                    )
                }

                val firstEntry = UrlHelpers.computeAbsoluteUrl(finalUrl, entries.first())
                if (firstEntry.lowercase().contains(".m3u8")) {
                    return@runCatching probe(firstEntry, headers).copy(
                        kind = "m3u8-master",
                        nestedPlaylistUrl = firstEntry,
                    )
                }

                val segmentResponse = request(firstEntry, headers, rangeProbe = true)
                return@runCatching VideoProbeResult(
                    ok = segmentResponse.status.isSuccess(),
                    url = url,
                    finalUrl = finalUrl,
                    kind = "m3u8",
                    statusCode = response.status.value,
                    contentType = contentType,
                    headers = response.headersSnapshot(),
                    summary = if (segmentResponse.status.isSuccess()) {
                        "Playlist and first segment are reachable"
                    } else {
                        "Playlist is reachable but first segment probe failed"
                    },
                    playlistEntries = entries.size,
                    sampledSegmentUrl = firstEntry,
                    sampledSegmentStatusCode = segmentResponse.status.value,
                    errors = if (segmentResponse.status.isSuccess()) emptyList() else listOf("First segment probe returned ${segmentResponse.status.value}"),
                )
            }

            VideoProbeResult(
                ok = response.status.isSuccess(),
                url = url,
                finalUrl = finalUrl,
                kind = "file",
                statusCode = response.status.value,
                contentType = contentType,
                headers = response.headersSnapshot(),
                summary = if (response.status.isSuccess()) "Media URL is reachable" else "Media URL probe failed",
                errors = if (response.status.isSuccess()) emptyList() else listOf("Probe returned ${response.status.value}"),
            )
        }.getOrElse { exception ->
            VideoProbeResult(
                ok = false,
                url = url,
                kind = "unknown",
                summary = "Probe failed with exception",
                errors = listOf("${exception::class.simpleName}: ${exception.message.orEmpty()}"),
            )
        }
    }

    private suspend fun request(
        url: String,
        headers: Map<String, String>,
        rangeProbe: Boolean = false,
    ): HttpResponse {
        return httpClient.get(url) {
            headers.forEach { (key, value) -> header(key, value) }
            if (rangeProbe) {
                header(HttpHeaders.Range, "bytes=0-1023")
            }
        }
    }

    private fun HttpResponse.headersSnapshot(): Map<String, String> {
        return headers.entries().associate { it.key to it.value.joinToString(", ") }
    }
}
