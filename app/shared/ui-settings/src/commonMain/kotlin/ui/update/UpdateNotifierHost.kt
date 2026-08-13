/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import kotlinx.coroutines.delay

/** 入口更新提示卡无操作自动消失时长 (毫秒). */
private const val UPDATE_CARD_AUTO_DISMISS_MILLIS = 20_000L

/**
 * 检测新版本并在右下角显示更新卡片 (上游同款带按钮样式): 详情 / 自动更新 / 关闭.
 * 点自动更新走与设置页完全相同的下载流程 (下载卡片可取消/重试), TV 上下载完自动安装.
 * TV: 卡片出现时初始焦点直接落在 "自动更新" 按钮上.
 */
@Composable
fun BoxScope.UpdateNotifier(
    viewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel() },
) {
    SideEffect {
        viewModel.startAutomaticCheckLatestVersion()
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val presentation by viewModel.presentationFlow.collectAsStateWithLifecycle()
    val newVersion = presentation.newVersion
    val state = presentation.state

    // Per-version dismiss state
    var dismissed by rememberSaveable(newVersion?.name) { mutableStateOf(false) }

    // TV: 下载完成后自动安装 (与设置页一致), 遥控器用户不必再按一次安装
    val autoInstall = LocalAniUiBehavior.current.autoInstallUpdates
    val downloaded = state is AppUpdateState.Downloaded
    LaunchedEffect(autoInstall, downloaded) {
        if (autoInstall && downloaded) {
            viewModel.install(context)
        }
    }

    // 安装失败对话框: 失败由 ViewModel 状态承载 (install 本身立即返回)
    presentation.installationFailure?.let { failure ->
        FailedToInstallDialog(
            message = failure.reason.toString(),
            onDismissRequest = { viewModel.dismissInstallationFailure() },
            state = state,
        )
    }

    val showCard = !dismissed && (state is AppUpdateState.HasUpdate || presentation.isDownloading)
    val hasUpdateCard = showCard && state is AppUpdateState.HasUpdate

    // TV: 气泡出现时把初始焦点送到"自动更新"按钮 (到位确认 + 重试, 见 resolveFocusRepeatedly)
    val autoUpdateFocus = remember { FocusRequester() }
    var autoUpdateFocused by remember { mutableStateOf(false) }
    LaunchedEffect(autoInstall, hasUpdateCard, newVersion?.name) {
        if (autoInstall && hasUpdateCard) {
            // 到位标志复位: 同一组合生命周期内出现第二个版本的卡片时,
            // 上一轮遗留的 true 会让解析立即"假到位", 新卡片拿不到初始焦点
            autoUpdateFocused = false
            resolveFocusRepeatedly(arrived = { autoUpdateFocused }) {
                runCatching { autoUpdateFocus.requestFocus() }
            }
        }
    }

    // 无操作自动消失: 提示卡出现一段时间后自行关闭, 不永久挡住右下角内容.
    // 开始下载后 hasUpdateCard 变 false, 本效应取消 —— 下载进度卡不受影响
    LaunchedEffect(hasUpdateCard, newVersion?.name) {
        if (hasUpdateCard) {
            delay(UPDATE_CARD_AUTO_DISMISS_MILLIS)
            dismissed = true
        }
    }

    // 焦点在气泡里时返回键等同点关闭按钮: 不接的话返回会穿到下层页面 (退出当前页) 而气泡还挡
    // 在右下角 —— 遥控器上"返回 = 关掉眼前这个东西"是最强的预期.
    // 只对"有更新"这张卡生效: 下载中那张卡的按钮是"取消下载", 会真的中止下载,
    // 返回键不该承担破坏性动作 (让它照常穿到页面).
    var cardFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = hasUpdateCard && cardFocused) { dismissed = true }

    // 焦点导航设备: 卡片在场期间焦点锁在卡片内, 方向键走到边界即取消这次焦点搜索.
    // 不锁的话卡片抢到初始焦点后用户随手一按方向键焦点就滑进下层页面, 而卡片还挡在右下角 ——
    // 既看不出焦点在哪, 也不知道怎么把它关掉. 锁上后出口只剩三个按钮和返回键, 全是一按之遥.
    // 只锁"有更新"这张卡 (与上面返回键同理): 下载中那张要挂几分钟, 锁住等于扣着整个应用不放.
    // 20 秒无操作自动消失仍然有效, 是这个模态的兜底时限; 届时焦点由 NavHost 的兜底监视
    // (见 AniAppContent 的 navHostModifier) 送回页面, 不会丢在根上.
    val trapFocus = LocalAniUiBehavior.current.focusDrivenNavigation && hasUpdateCard

    AniAnimatedVisibility(
        visible = showCard,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp)
            // 观察整棵子树的焦点 (卡片里的按钮), 而不是本节点自己
            .onFocusChanged { cardFocused = it.hasFocus }
            // focusProperties 只作用于链上其后的 focusTarget 与子布局节点, 且子节点向上取属性时
            // 会停在最近的一个 focusTarget 上 —— 也就是这里的 focusGroup, 因此卡片内部按钮之间
            // 的左右移动不会被 onExit 拦截, 只有跨出整张卡的那一步才会.
            .ifThen(trapFocus) {
                focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup()
            },
    ) {
        when {
            state is AppUpdateState.HasUpdate -> {
                NewVersionPopupCard(
                    version = newVersion?.name ?: "",
                    changes = newVersion?.majorChanges ?: emptyList(),
                    onDetailsClick = {
                        newVersion?.let {
                            uriHandler.openUri(
                                "https://github.com/$FORK_OWNER/$FORK_REPO/releases/tag/v${it.name}",
                            )
                        }
                    },
                    onAutoUpdateClick = {
                        newVersion?.let { viewModel.startDownload(it, uriHandler) }
                    },
                    onDismissRequest = { dismissed = true },
                    autoUpdateButtonModifier = Modifier
                        .focusRequester(autoUpdateFocus)
                        // 双向上报: 只报得不报失时, 按钮已聚焦下解析效应重跑 (复位标志后
                        // requestFocus 无事件) 会烧满轮询并抢回用户移开的焦点
                        .onFocusChanged { autoUpdateFocused = it.isFocused },
                )
            }

            presentation.isDownloading -> {
                DownloadingUpdatePopupCard(
                    version = newVersion ?: return@AniAnimatedVisibility,
                    fileDownloaderStats = presentation.fileDownloaderStats,
                    error = presentation.downloadError,
                    isInstalling = state is AppUpdateState.Installing,
                    onInstallClick = { viewModel.install(context) },
                    onCancelClick = {
                        viewModel.cancelDownload()
                        dismissed = true
                    },
                    onRetryClick = { viewModel.restartDownload(uriHandler) },
                )
            }
        }
    }
}

/**
 * 设置页中的更新提示卡片，带下载和安装按钮，永久显示直到手动关闭.
 */
@Composable
fun BoxScope.UpdateSettingsNotifier(
    viewModel: AppUpdateViewModel = viewModel { AppUpdateViewModel() },
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val presentation by viewModel.presentationFlow.collectAsStateWithLifecycle()
    val newVersion = presentation.newVersion
    val state = presentation.state

    // Per-version dismiss state
    var dismissed by rememberSaveable(newVersion?.name) { mutableStateOf(false) }

    // TV: 下载完成后自动安装, 避免遥控器用户在下载结束后还要再操作一次按钮.
    // 用状态变为 Downloaded (而非定时器) 触发, 以适配不同设备的下载耗时.
    val autoInstall = LocalAniUiBehavior.current.autoInstallUpdates
    val downloaded = state is AppUpdateState.Downloaded
    LaunchedEffect(autoInstall, downloaded) {
        if (autoInstall && downloaded) {
            viewModel.install(context)
        }
    }

    // 安装失败对话框: 失败由 ViewModel 状态承载 (install 本身立即返回)
    presentation.installationFailure?.let { failure ->
        FailedToInstallDialog(
            message = failure.reason.toString(),
            onDismissRequest = { viewModel.dismissInstallationFailure() },
            state = state,
        )
    }

    val showCard = !dismissed && (state is AppUpdateState.HasUpdate || presentation.isDownloading)

    AniAnimatedVisibility(
        visible = showCard,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp),
    ) {
        when {
            state is AppUpdateState.HasUpdate -> {
                NewVersionPopupCard(
                    version = newVersion?.name ?: "",
                    changes = newVersion?.majorChanges ?: emptyList(),
                    onDetailsClick = {
                        newVersion?.let {
                            uriHandler.openUri(
                                "https://github.com/$FORK_OWNER/$FORK_REPO/releases/tag/v${it.name}",
                            )
                        }
                    },
                    onAutoUpdateClick = {
                        newVersion?.let { viewModel.startDownload(it, uriHandler) }
                    },
                    onDismissRequest = { dismissed = true },
                )
            }

            presentation.isDownloading -> {
                DownloadingUpdatePopupCard(
                    version = newVersion ?: return@AniAnimatedVisibility,
                    fileDownloaderStats = presentation.fileDownloaderStats,
                    error = presentation.downloadError,
                    isInstalling = state is AppUpdateState.Installing,
                    onInstallClick = { viewModel.install(context) },
                    onCancelClick = {
                        viewModel.cancelDownload()
                        dismissed = true
                    },
                    onRetryClick = { viewModel.restartDownload(uriHandler) },
                )
            }
        }
    }
}
