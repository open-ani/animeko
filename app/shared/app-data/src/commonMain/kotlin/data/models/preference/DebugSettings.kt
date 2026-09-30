package me.him188.ani.app.data.models.preference

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
data class DebugSettings(
    val enabled: Boolean = false,
    val showAllEpisodes: Boolean = false,
    val showControllerAlwaysOnRequesters: Boolean = false,
    /**
     * 桌面端开发者功能: 将当前平台支持自动安装的安装包拖入主窗口, 确认后安装并重启.
     * 仅在 [enabled] 为 `true` 时生效.
     */
    val installPackageOnDrop: Boolean = false,
    /**
     * 开发者功能「安装 main 分支的指定 commit」下载 GitHub Actions 产物所用的 GitHub token.
     * 空字符串表示未设置. 仅在 [enabled] 为 `true` 时可见.
     */
    val devBuildGitHubToken: String = "",
    @Suppress("PropertyName") @Transient val _placeHolder: Int = 0,
) {
    companion object {
        @Stable
        val Default = DebugSettings()
    }
}