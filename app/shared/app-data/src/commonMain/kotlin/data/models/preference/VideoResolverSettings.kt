/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.him188.ani.app.data.models.preference.WebViewDriver.entries

@Serializable
@Immutable
data class VideoResolverSettings(
    val driver: WebViewDriver = WebViewDriver.AUTO,
    val headless: Boolean = true,
    val resourceExtractionTimeoutSeconds: Int = DEFAULT_RESOURCE_EXTRACTION_TIMEOUT_SECONDS,

    @Suppress("PropertyName")
    @Transient val _placeholder: Int = 0,
) {
    val effectiveResourceExtractionTimeoutSeconds: Int
        get() = if (resourceExtractionTimeoutSeconds in ResourceExtractionTimeoutSecondsOptions) {
            resourceExtractionTimeoutSeconds
        } else {
            DEFAULT_RESOURCE_EXTRACTION_TIMEOUT_SECONDS
        }

    val effectiveResourceExtractionTimeoutMillis: Long
        get() = effectiveResourceExtractionTimeoutSeconds * 1_000L

    companion object {
        const val DEFAULT_RESOURCE_EXTRACTION_TIMEOUT_SECONDS = 8
        val ResourceExtractionTimeoutSecondsOptions = listOf(3, 5, 8, 10, 15, 20, 30)

        val Default = VideoResolverSettings()
    }
}

@Serializable
enum class WebViewDriver {
    CHROME,
    EDGE,
    AUTO;
    // Maybe a custom executable file
    // CUSTOM;

    override fun toString(): String {
        return this.name.lowercase()
    }

    companion object {
        val enabledEntries by lazy {
            entries.sortedDescending()
        }
    }
}
