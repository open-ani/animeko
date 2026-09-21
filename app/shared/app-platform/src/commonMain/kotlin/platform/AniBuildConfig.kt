/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.Stable
import me.him188.ani.utils.platform.currentPlatform


@Stable
interface AniBuildConfig {
    /**
     * `3.0.0-rc04`
     */
    val versionName: String
    val isDebug: Boolean
    val dandanplayAppId: String
    val dandanplayAppSecret: String

    /** TMDB API Read Access Token (v4 Bearer), 用于获取横版背景图/剧集缩略图. 未配置时为空串. */
    val tmdbApiToken: String
        get() = ""
    val sentryDsn: String
    val overrideAniApiServer: String
        get() = ""

    val distroChannel: String

    /**
     * 构建时所在的 git 分支名, 如 `main`. CI 里 PR 构建为 PR 的源分支, tag 构建为 tag 名.
     * 无法获取 (源码包构建, 或没有 git) 时为空.
     */
    val gitBranch: String
        get() = ""

    /**
     * 构建时 HEAD 的完整 commit sha (40 位十六进制). 无法获取时为空.
     */
    val gitCommitSha: String
        get() = ""

    /**
     * HEAD commit 的提交时间, ISO-8601, 如 `2026-09-20T12:00:00+08:00`. 无法获取时为空.
     */
    val gitCommitTime: String
        get() = ""

    val sentryEnabled: Boolean
        get() = true
    val analyticsEnabled: Boolean
        get() = true

    val isDefaultDistro: Boolean
        get() = distroChannel == DISTRO_PLATFORM_DEFAULT

    companion object {
        @Stable
        fun current(): AniBuildConfig = currentAniBuildConfig

        private const val DISTRO_PLATFORM_DEFAULT = "default"
    }
}

/**
 * E.g. `3000` for `3.0.0`, `3012` for `3.1.2`
 */
val AniBuildConfig.fourDigitVersionCode: String
    get() = buildString {
        val split = versionName.substringBefore("-").split(".")
        if (split.size == 3) {
            split[0].toIntOrNull()?.let {
                append(it.toString())
            }
            split[1].toIntOrNull()?.let {
                append(it.toString().padStart(2, '0'))
            }
            split[2].toIntOrNull()?.let {
                append(it.toString())
            }
        } else {
            for (section in split) {
                section.toIntOrNull()?.let {
                    append(it.toString())
                }
            }
        }
    }

@Stable
@PublishedApi
internal expect val currentAniBuildConfigImpl: AniBuildConfig

@Stable
inline val currentAniBuildConfig: AniBuildConfig get() = currentAniBuildConfigImpl

/**
 * 满足各个数据源建议格式的 User-Agent, 所有 HTTP 请求都应该带此 UA.
 */
fun getAniUserAgent(
    version: String = currentAniBuildConfig.versionName,
    platform: String = currentPlatform().nameAndArch,
): String = "open-ani/ani/$version ($platform) (https://github.com/open-ani/ani)"
