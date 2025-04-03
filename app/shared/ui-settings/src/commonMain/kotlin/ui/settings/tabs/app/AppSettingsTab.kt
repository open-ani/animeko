/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.data.models.preference.FullscreenSwitchMode
import me.him188.ani.app.data.models.preference.NsfwMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.getIcon
import me.him188.ani.app.navigation.getText
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterGroup
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.SingleTester
import me.him188.ani.app.ui.settings.framework.Tester
import me.him188.ani.app.ui.settings.framework.components.DropdownItem
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.tabs.theme.ThemeGroup
import me.him188.ani.app.ui.update.AutoUpdateViewModel
import me.him188.ani.app.ui.update.ChangelogDialog
import me.him188.ani.app.ui.update.NewVersion
import me.him188.ani.app.ui.update.TextButtonUpdateLogo
import me.him188.ani.app.ui.update.UpdateChecker
import me.him188.ani.utils.platform.isDesktop
import me.him188.ani.utils.platform.isIos
import me.him188.ani.utils.platform.isMobile
import kotlin.coroutines.cancellation.CancellationException

sealed class CheckVersionResult {
    data class HasNewVersion(
        val newVersion: NewVersion,
    ) : CheckVersionResult()

    data object UpToDate : CheckVersionResult()
    data class Failed(
        val throwable: Throwable,
    ) : CheckVersionResult()
}

@Composable
fun AppSettingsTab(
    softwareUpdateGroupState: SoftwareUpdateGroupState,
    uiSettings: SettingsState<UISettings>,
    themeSettings: SettingsState<ThemeSettings>,
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    danmakuFilterConfig: SettingsState<DanmakuFilterConfig>,
    danmakuRegexFilterState: DanmakuRegexFilterState,
    showDebug: Boolean,
    modifier: Modifier = Modifier
) {
    SettingsTab(modifier) {
        SoftwareUpdateGroup(softwareUpdateGroupState)
        AppearanceGroup(uiSettings)
        ThemeGroup(themeSettings)
        PlayerGroup(
            videoScaffoldConfig,
            danmakuFilterConfig,
            danmakuRegexFilterState,
            showDebug,
        )
        AppSettingsTabPlatform()
    }
}

@Composable
fun SettingsScope.AppearanceGroup(
    state: SettingsState<UISettings>,
) {
    val uiSettings by state

    DropdownItem(
        selected = { uiSettings.mainSceneInitialPage },
        values = { MainScreenPage.visibleEntries },
        itemText = { Text(it.getText()) },
        onSelect = {
            state.update(uiSettings.copy(mainSceneInitialPage = it))
        },
        itemIcon = { Icon(it.getIcon(), null) },
        title = { Text("初始页面") },
        description = { Text("启动 APP 时进入的页面") },
    )

    Group(title = { Text("搜索") }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.searchSettings.enableNewSearchSubjectApi,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(
                            enableNewSearchSubjectApi = !uiSettings.searchSettings.enableNewSearchSubjectApi,
                        ),
                    ),
                )
            },
            title = { Text("使用新版条目查询接口") },
            description = { Text("实验性接口，可能会缺失部分条目，谨慎启用") },
        )
        DropdownItem(
            selected = { uiSettings.searchSettings.nsfwMode },
            values = { NsfwMode.entries },
            itemText = {
                when (it) {
                    NsfwMode.HIDE -> Text("隐藏")
                    NsfwMode.BLUR -> Text("模糊")
                    NsfwMode.DISPLAY -> Text("显示")
                }
            },
            onSelect = {
                state.update(
                    uiSettings.copy(
                        searchSettings = uiSettings.searchSettings.copy(nsfwMode = it),
                    ),
                )
            },
            title = { Text("NSFW 内容") },
        )
    }

    Group(title = { Text("我的追番") }, useThinHeader = true) {
        SwitchItem(
            checked = uiSettings.myCollections.enableListAnimation1,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        myCollections = uiSettings.myCollections.copy(
                            enableListAnimation1 = !uiSettings.myCollections.enableListAnimation1,
                        ),
                    ),
                )
            },
            title = { Text("列表滚动动画") },
            description = { Text("如遇到显示重叠问题，可尝试关闭") },
        )
    }

    Group(title = { Text("选集播放") }, useThinHeader = true) {
        val episode by remember { derivedStateOf { uiSettings.episodeProgress } }
        SwitchItem(
            checked = episode.theme == EpisodeListProgressTheme.LIGHT_UP,
            onCheckedChange = {
                state.update(
                    uiSettings.copy(
                        episodeProgress = episode.copy(
                            theme = if (it) EpisodeListProgressTheme.LIGHT_UP else EpisodeListProgressTheme.ACTION,
                        ),
                    ),
                )
            },
            title = { Text("点亮模式") },
            description = { Text("高亮已经看过的剧集，而不是将要看的剧集") },
        )
    }
}

@Stable
class SoftwareUpdateGroupState(
    val updateSettings: SettingsState<UpdateSettings>,
    backgroundScope: CoroutineScope,
    val currentVersion: String = currentAniBuildConfig.versionName,
    val releaseClass: ReleaseClass = guessReleaseClass(currentVersion),
    private val onTest: suspend () -> CheckVersionResult = {
        UpdateChecker().let { checker ->
            try {
                val v = checker.checkLatestVersion(
                    updateSettings.value.releaseClass,
                    currentVersion,
                )
                if (v == null) {
                    CheckVersionResult.UpToDate
                } else {
                    CheckVersionResult.HasNewVersion(v)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CheckVersionResult.Failed(e)
            }
        }
    },
) {
    val updateCheckerTester = SingleTester(
        Tester(
            "new",
            onTest = { onTest() },
            onError = { CheckVersionResult.Failed(it) },
        ),
        backgroundScope,
    )
}

@Composable
fun SettingsScope.SoftwareUpdateGroup(
    state: SoftwareUpdateGroupState,
    modifier: Modifier = Modifier,
) {
    Group(title = { Text("软件更新") }, modifier = modifier) {
        TextItem(
            description = { Text("当前版本") },
            icon = { ReleaseClassIcon(state.releaseClass) },
            title = { Text(state.currentVersion) },
        )
        HorizontalDividerItem()
        val uriHandler = LocalUriHandler.current
        RowButtonItem(
            onClick = {
                uriHandler.openUri(
                    "https://github.com/open-ani/ani/releases/tag/v${currentAniBuildConfig.versionName}",
                )
            },
            icon = { Icon(Icons.Rounded.ArrowOutward, null) },
        ) { Text("查看更新日志") }
        HorizontalDividerItem()
        val updateSettings by state.updateSettings
        SwitchItem(
            updateSettings.autoCheckUpdate,
            onCheckedChange = {
                state.updateSettings.update(updateSettings.copy(autoCheckUpdate = !updateSettings.autoCheckUpdate))
            },
            title = { Text("自动检查更新") },
            description = { Text("只会显示一个更新图标，不会自动下载") },
        )
        HorizontalDividerItem()
        DropdownItem(
            selected = { updateSettings.releaseClass },
            values = { ReleaseClass.enabledEntries },
            itemText = {
                when (it) {
                    ReleaseClass.ALPHA -> Text("每日构建 (最早体验新功能)")
                    ReleaseClass.BETA -> Text("测试版 (兼顾新功能和一定稳定性)")
                    ReleaseClass.RC, // RC 实际上不会有
                    ReleaseClass.STABLE -> Text("正式版 (最稳定)")
                }
            },
            exposedItemText = {
                when (it) {
                    ReleaseClass.ALPHA -> Text("每日构建")
                    ReleaseClass.BETA -> Text("测试版")
                    ReleaseClass.RC, // RC 实际上不会有
                    ReleaseClass.STABLE -> Text("正式版")
                }
            },
            onSelect = {
                state.updateSettings.update(updateSettings.copy(releaseClass = it))
            },
            itemIcon = {
                ReleaseClassIcon(it)
            },
            title = { Text("更新类型") },
        )
        if (!LocalPlatform.current.isIos()) {
            HorizontalDividerItem()
            SwitchItem(
                updateSettings.inAppDownload,
                { state.updateSettings.update(updateSettings.copy(inAppDownload = it)) },
                title = { Text("应用内下载") },
                description = {
                    if (updateSettings.inAppDownload) {
                        Text("省去跳转浏览器步骤")
                    } else {
                        Text("已关闭，将会跳转到外部浏览器完成下载")
                    }
                },
                enabled = updateSettings.autoCheckUpdate,
            )
            AniAnimatedVisibility(updateSettings.inAppDownload) {
                Column {
                    HorizontalDividerItem()
                    SwitchItem(
                        updateSettings.autoDownloadUpdate,
                        { state.updateSettings.update(updateSettings.copy(autoDownloadUpdate = it)) },
                        title = { Text("自动下载更新") },
                        description = { Text("下载完成后会提示，确认后才会安装") },
                        enabled = updateSettings.autoCheckUpdate,
                    )
                }
            }
        }
        HorizontalDividerItem()
        var showUpdatePopup by remember { mutableStateOf(false) }
        val autoUpdate: AutoUpdateViewModel = viewModel { AutoUpdateViewModel() }
        if (showUpdatePopup) {
            (state.updateCheckerTester.tester.result as? CheckVersionResult.HasNewVersion)?.let {
                ChangelogDialog(
                    latestVersion = it.newVersion,
                    onDismissRequest = { showUpdatePopup = false },
                    onStartDownload = { autoUpdate.startDownload(it.newVersion, uriHandler) },
                )
            }
        }
        TextButtonItem(
            onClick = {
                if (state.updateCheckerTester.tester.isTesting) {
                    state.updateCheckerTester.cancel()
                    return@TextButtonItem
                }
                when (state.updateCheckerTester.tester.result) {
                    is CheckVersionResult.HasNewVersion -> showUpdatePopup = true
                    is CheckVersionResult.Failed, is CheckVersionResult.UpToDate, null -> {
                        state.updateCheckerTester.testAll()
                        autoUpdate.startCheckLatestVersion(uriHandler)
                    }
                }
            },
            title = {
                if (state.updateCheckerTester.tester.isTesting) {
                    Text("检查中...")
                    return@TextButtonItem
                }
                when (val result = state.updateCheckerTester.tester.result) {
                    is CheckVersionResult.Failed -> Text("检查失败")
                    is CheckVersionResult.UpToDate -> Text("已是最新")
                    is CheckVersionResult.HasNewVersion -> Text(remember(result.newVersion.name) { "有新版本: ${result.newVersion.name}" })
                    null -> Text("检查更新")
                }
            },
        )
        AniAnimatedVisibility(
            state.updateCheckerTester.tester.result is CheckVersionResult.HasNewVersion // 在设置里检查的
                    || autoUpdate.hasUpdate, // 在主页自动检查的
        ) {
            HorizontalDividerItem()
            Item(
                headlineContent = {},
                trailingContent = {
                    TextButtonUpdateLogo(autoUpdate)
                },
            )
        }
    }
}

@Composable
fun SettingsScope.PlayerGroup(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
    danmakuFilterConfig: SettingsState<DanmakuFilterConfig>,
    danmakuRegexFilterState: DanmakuRegexFilterState,
    showDebug: Boolean
) {
    Group(title = { Text("播放器") }) {
        val config by videoScaffoldConfig
        DropdownItem(
            selected = { config.fullscreenSwitchMode },
            values = { FullscreenSwitchMode.entries },
            itemText = {
                Text(
                    when (it) {
                        FullscreenSwitchMode.ALWAYS_SHOW_FLOATING -> "总是显示"
                        FullscreenSwitchMode.AUTO_HIDE_FLOATING -> "显示五秒后隐藏"
                        FullscreenSwitchMode.ONLY_IN_CONTROLLER -> "不显示"
                    },
                )
            },
            onSelect = {
                videoScaffoldConfig.update(config.copy(fullscreenSwitchMode = it))
            },
            title = { Text("竖屏模式下显示全屏按钮") },
            description = { Text("总是显示播放器右下角的切换全屏按钮，方便切换") },
        )
        HorizontalDividerItem()
        SwitchItem(
            danmakuFilterConfig.value.enableRegexFilter,
            onCheckedChange = {
                danmakuFilterConfig.update(danmakuFilterConfig.value.copy(enableRegexFilter = it))
            },
            title = { Text("启用正则弹幕过滤器") },
        )
        HorizontalDividerItem()
        DanmakuRegexFilterGroup(
            state = danmakuRegexFilterState,
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.pauseVideoOnEditDanmaku,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(pauseVideoOnEditDanmaku = it))
            },
            title = { Text("发送弹幕时自动暂停播放") },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoMarkDone,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoMarkDone = it))
            },
            title = { Text("观看 90% 后自动标记为看过") },
        )
        HorizontalDividerItem()
        SwitchItem(
            checked = config.hideSelectorOnSelect,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(hideSelectorOnSelect = it))
            },
            title = { Text("选择数据源后自动关闭弹窗") },
        )
        if (LocalPlatform.current.isMobile()) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.autoFullscreenOnLandscapeMode,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(autoFullscreenOnLandscapeMode = it))
                },
                title = { Text("旋转屏幕时自动全屏") },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoPlayNext,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoPlayNext = it))
            },
            title = { Text("自动连播") },
        )
        if (LocalPlatform.current.isDesktop()) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.autoSkipOpEd,
                onCheckedChange = {
                    videoScaffoldConfig.update(config.copy(autoSkipOpEd = it))
                },
                title = { Text("自动跳过 OP 和 ED") },
                description = { Text("只对 BT 数据源的部分资源有效") },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            checked = config.autoSwitchMediaOnPlayerError,
            onCheckedChange = {
                videoScaffoldConfig.update(config.copy(autoSwitchMediaOnPlayerError = it))
            },
            title = { Text("播放失败时自动切换资源") },
        )
        PlayerGroupPlatform(videoScaffoldConfig)
    }
}

@Composable
private fun ReleaseClassIcon(releaseClass: ReleaseClass) {
    when (releaseClass) {
        ReleaseClass.ALPHA -> Icon(Icons.Rounded.RocketLaunch, null)
        ReleaseClass.BETA -> Icon(Icons.Rounded.Science, null)
        ReleaseClass.RC, ReleaseClass.STABLE -> Icon(Icons.Rounded.Verified, null)
    }
}

@Stable
private fun guessReleaseClass(version: String): ReleaseClass {
    val metadata = version.substringAfter("-", "").lowercase()
    return when {
        metadata.isEmpty() -> ReleaseClass.STABLE
        "alpha" in metadata || "dev" in metadata -> ReleaseClass.ALPHA
        "beta" in metadata -> ReleaseClass.BETA
        "rc" in metadata -> ReleaseClass.RC
        else -> ReleaseClass.STABLE
    }
}

@Composable
internal expect fun SettingsScope.AppSettingsTabPlatform()

@Composable
internal expect fun SettingsScope.PlayerGroupPlatform(
    videoScaffoldConfig: SettingsState<VideoScaffoldConfig>,
)
