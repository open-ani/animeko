/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.platform.getAniUserAgent
import me.him188.ani.utils.ktor.getPlatformKtorEngine
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform

internal const val FORK_OWNER = "GrahamZen"
internal const val FORK_REPO = "animeko"

/** 全架构包的文件名标记 (见 ReleaseArtifactNames); 任何设备都装得上, 作为 ABI 匹配不到时的兜底. */
private const val UNIVERSAL_APK_MARKER = "universal"

/**
 * 该资产是否是 [abi] 的包. release 资产名形如 `ani-<版本>-<架构>.apk`
 * (见 `ReleaseArtifactNames.androidApp`), 所以按 `-<架构>.` 这一整段匹配.
 *
 * 不能用 `abi in name` 纯子串: 32 位 x86 设备的 ABI 就叫 `x86`, 而它是 `x86_64` 的子串,
 * 会把 64 位包当成装得上的.
 */
private fun GitHubAsset.isForAbi(abi: String) = "-$abi." in name

/** 见 [UpdateChecker.pickInstallableApks]; 抽成顶层纯函数以便测试 (设备 ABI 列表由调用方给出). */
internal fun List<GitHubAsset>.pickInstallableApks(abis: List<String>): List<GitHubAsset> {
    if (abis.isEmpty()) return this
    // 按设备自己的偏好顺序取第一个"有对应包"的 ABI, 而不是只看首选 ABI —— 首选的那个不一定出包:
    // x86 电视模拟器首选 x86 (本项目不出), 但它支持 armeabi-v7a, 该装 v7 包
    val exact = abis.firstNotNullOfOrNull { abi -> firstOrNull { it.isForAbi(abi) } }
    val universal = firstOrNull { it.isForAbi(UNIVERSAL_APK_MARKER) }
    // 两个都没有 = release 命名规则变了: 宁可退回旧行为也不要空列表
    return listOfNotNull(exact, universal).ifEmpty { this }
}

class UpdateChecker {
    /**
     * 检查是否有更新的版本. 返回最新版本的信息, 或者 `null` 表示没有新版本.
     */
    suspend fun checkLatestVersion(
        releaseClass: ReleaseClass,
        currentVersion: String = currentAniBuildConfig.versionName,
    ): NewVersion? {
        HttpClient(getPlatformKtorEngine()) {
            expectSuccess = true
        }.use { client ->
            return try {
                client.getVersionFromGitHub(currentVersion, releaseClass).also { version ->
                    // 连选中的安装包一起打出来: 装不上的报障 (架构不符) 只凭版本号看不出问题在哪,
                    // 而选包发生在这一步, 到下载时才有日志就晚了 (用户不点下载就没有任何线索)
                    logger.info {
                        "Got latest version from GitHub: ${version?.name}, packages=" +
                            "${version?.downloadUrlAlternatives?.map { it.substringAfterLast('/') }}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Failed to get latest version from GitHub" }
                throw e
            }
        }
    }

    private suspend fun HttpClient.getVersionFromGitHub(
        currentVersion: String,
        releaseClass: ReleaseClass,
    ): NewVersion? {
        val releases = get("https://api.github.com/repos/$FORK_OWNER/$FORK_REPO/releases") {
            parameter("per_page", 20)
            header(HttpHeaders.UserAgent, getAniUserAgent())
        }.bodyAsText().let {
            json.decodeFromString<List<GitHubRelease>>(it)
        }

        val latest = releases
            .filter { !it.draft }
            .filter { release ->
                when (releaseClass) {
                    ReleaseClass.STABLE -> !release.prerelease
                    else -> true // BETA / ALPHA / RC: include prerelease
                }
            }
            .firstOrNull() ?: return null

        val versionName = latest.tagName.removePrefix("v")
        if (!isNewerThan(versionName, currentVersion)) return null

        return NewVersion(
            name = versionName,
            changelogs = listOf(
                Changelog(
                    version = versionName,
                    publishedAt = latest.publishedAt,
                    changes = latest.body,
                ),
            ),
            downloadUrlAlternatives = latest.assets
                .filter { it.name.endsWith(".apk") }
                .pickInstallableApks()
                .map { it.browserDownloadUrl },
            publishedAt = latest.publishedAt,
        )
    }

    /**
     * 从 release 的全部 APK 里挑出本机装得上的: 本机架构的专包在前, universal 兜底在后, 其余一律不留.
     *
     * 不筛的后果是"自动更新后安装提示不兼容" (`INSTALL_FAILED_NO_MATCHING_ABIS`):
     * [downloadUrlAlternatives] 会被 [me.him188.ani.app.tools.update.FileDownloader] 当成
     * **同一个文件的备选下载源** (逐个尝试, 第一个成功即停), 于是永远下载 release 里的第一个 APK ——
     * 按文件名排序就是 `arm64-v8a`. 32 位设备与 x86 设备装上去必然失败.
     *
     * 混入其它架构还有个更隐蔽的后果: 首选包下载中途失败时, 循环会接着下另一个架构的包并
     * "成功" —— 那不是镜像而是另一个文件, 下完照样装不上. 所以这里是 filter 而非单纯排序.
     *
     * 用设备的**完整** ABI 列表而不是只用首选 ABI ([me.him188.ani.utils.platform.Arch]):
     * 见 [Platform.Android.supportedAbis] —— 只看首选 ABI 时 x86 的电视模拟器会被当成 arm64 设备.
     * 非 Android 平台拿不到列表 (空), 此时不筛, 保持原有行为.
     */
    private fun List<GitHubAsset>.pickInstallableApks(): List<GitHubAsset> =
        pickInstallableApks((currentPlatform() as? Platform.Android)?.supportedAbis ?: emptyList())

    /**
     * Returns true if [candidate] is a newer version than [current].
     *
     * Handles semver with optional pre-release suffix, e.g. "4.0.0-beta04".
     * Stable (no suffix) is considered newer than any pre-release with the same numbers.
     */
    private fun isNewerThan(candidate: String, current: String): Boolean {
        if (candidate == current) return false

        fun parse(v: String): Pair<List<Int>, String> {
            val dashIdx = v.indexOf('-')
            return if (dashIdx >= 0) {
                v.substring(0, dashIdx).split('.').map { it.toIntOrNull() ?: 0 } to
                        v.substring(dashIdx + 1)
            } else {
                v.split('.').map { it.toIntOrNull() ?: 0 } to ""
            }
        }

        val (cNums, cPre) = parse(candidate)
        val (vNums, vPre) = parse(current)

        for (i in 0 until maxOf(cNums.size, vNums.size)) {
            val c = cNums.getOrElse(i) { 0 }
            val v = vNums.getOrElse(i) { 0 }
            if (c != v) return c > v
        }

        // Same numeric version — stable beats pre-release
        if (cPre.isEmpty() && vPre.isNotEmpty()) return true  // stable > beta
        if (cPre.isNotEmpty() && vPre.isEmpty()) return false  // beta < stable
        return cPre > vPre // both pre-release: compare lexicographically
    }

    private companion object {
        private val logger = logger<UpdateChecker>()
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("body") val body: String = "",
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("assets") val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
