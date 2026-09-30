/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.platform.AniBuildConfig
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_about_version
import me.him188.ani.app.ui.lang.settings_build_info_branch
import me.him188.ani.app.ui.lang.settings_build_info_build_type
import me.him188.ani.app.ui.lang.settings_build_info_commit
import me.him188.ani.app.ui.lang.settings_build_info_commit_time
import me.him188.ani.app.ui.lang.settings_build_info_copied
import me.him188.ani.app.ui.lang.settings_build_info_copy_all
import me.him188.ani.app.ui.lang.settings_build_info_distro_channel
import me.him188.ani.app.ui.lang.settings_build_info_open_commit
import me.him188.ani.app.ui.lang.settings_build_info_platform
import me.him188.ani.app.ui.lang.settings_build_info_unknown
import me.him188.ani.app.ui.settings.tabs.AniHelperDestination
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.currentPlatform
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * "关于 > 构建信息" 页展示的内容. 全部在构建时确定, 见 `AniBuildConfig` 和 build-logic 的 `git.kt`.
 */
@Immutable
data class BuildInfo(
    val versionName: String,
    /** 空表示构建时无法获取. */
    val gitBranch: String,
    /** 完整 sha. 空表示构建时无法获取. */
    val gitCommitSha: String,
    /** ISO-8601. 空表示构建时无法获取. */
    val gitCommitTime: String,
    val distroChannel: String,
    val isDebug: Boolean,
    val platform: String,
) {
    val gitCommitShortSha: String get() = gitCommitSha.take(SHORT_SHA_LENGTH)

    /** GitHub 上这个 commit 的页面. 没有 sha 时为 `null`. */
    val gitCommitUrl: String?
        get() = gitCommitSha.takeIf { it.isNotBlank() }?.let { "${AniHelperDestination.GITHUB_HOME}/commit/$it" }

    companion object {
        /** 与 CI 写进版本号的 sha 长度一致 (`4.12.0-main-28ec14ac`). */
        const val SHORT_SHA_LENGTH = 8

        fun current(
            buildConfig: AniBuildConfig = currentAniBuildConfig,
            platform: Platform = currentPlatform(),
        ): BuildInfo = BuildInfo(
            versionName = buildConfig.versionName,
            gitBranch = buildConfig.gitBranch,
            gitCommitSha = buildConfig.gitCommitSha,
            gitCommitTime = buildConfig.gitCommitTime,
            distroChannel = buildConfig.distroChannel,
            isDebug = buildConfig.isDebug,
            platform = platform.nameAndArch,
        )
    }
}

/**
 * 详细构建信息. 每一行点击复制该项, 底部按钮复制全部, 方便贴到 issue 里.
 */
@Composable
fun BuildInfoTab(
    info: BuildInfo,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val unknown = stringResource(Lang.settings_build_info_unknown)
    val labelVersion = stringResource(Lang.settings_about_version)
    val labelBranch = stringResource(Lang.settings_build_info_branch)
    val labelCommit = stringResource(Lang.settings_build_info_commit)
    val labelCommitTime = stringResource(Lang.settings_build_info_commit_time)
    val labelBuildType = stringResource(Lang.settings_build_info_build_type)
    val labelDistroChannel = stringResource(Lang.settings_build_info_distro_channel)
    val labelPlatform = stringResource(Lang.settings_build_info_platform)

    // 顺序就是复制全部时的顺序
    val rows = listOf(
        labelVersion to info.versionName,
        labelBranch to info.gitBranch.ifBlank { unknown },
        labelCommit to info.gitCommitSha.ifBlank { unknown },
        labelCommitTime to info.gitCommitTime.ifBlank { unknown },
        labelBuildType to if (info.isDebug) "Debug" else "Release",
        labelDistroChannel to info.distroChannel,
        labelPlatform to info.platform,
    )

    fun copy(text: String) {
        scope.launch {
            clipboard.setClipEntryText(text)
            toaster.toast(getString(Lang.settings_build_info_copied))
        }
    }

    Column(modifier.fillMaxWidth()) {
        val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

        for ((label, value) in rows) {
            val isCommit = label == labelCommit
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = {
                    Text(
                        value,
                        // sha 和时间用等宽字体, 一眼能对上
                        fontFamily = if (isCommit || label == labelCommitTime) FontFamily.Monospace else null,
                    )
                },
                trailingContent = if (isCommit && info.gitCommitUrl != null) {
                    {
                        IconButton({ uriHandler.openUri(info.gitCommitUrl!!) }) {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = stringResource(Lang.settings_build_info_open_commit),
                            )
                        }
                    }
                } else null,
                modifier = Modifier.clickable(onClick = { copy(value) }, role = Role.Button),
                colors = listItemColors,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { copy(rows.joinToString("\n") { (label, value) -> "$label: $value" }) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Lang.settings_build_info_copy_all))
        }
    }
}

@TestOnly
val TestBuildInfo
    get() = BuildInfo(
        versionName = "4.8.0-alpha02",
        gitBranch = "main",
        gitCommitSha = "28ec14ac9f6f1b2c3d4e5f60718293a4b5c6d7e8",
        gitCommitTime = "2026-09-20T12:00:00+08:00",
        distroChannel = "default",
        isDebug = true,
        platform = "macOS AArch64",
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewBuildInfoTab() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            BuildInfoTab(TestBuildInfo)
        }
    }
}
