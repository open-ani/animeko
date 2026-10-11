/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntryDecorator
import me.him188.ani.app.domain.settings.remote.RemoteMediaSourceEditor
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_invalid_qr
import me.him188.ani.app.ui.lang.remote_settings_scan
import me.him188.ani.app.ui.lang.remote_settings_scan_hint
import me.him188.ani.app.ui.qrlogin.QrCodeScanScreen
import me.him188.ani.app.ui.settings.remote.RemoteSettingsScreen
import me.him188.ani.app.ui.settings.remote.RemoteSettingsSessionHost
import me.him188.ani.app.ui.settings.remote.RemoteSettingsViewModel
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.remote.settings.RemoteSettingsLink
import org.jetbrains.compose.resources.stringResource

/**
 * [NavRoutes.RemoteSettings] 与其 [NavRoutes.RemoteEditMediaSource] 子页共用同一个 [RemoteSettingsViewModel] (即同一个电视连接).
 * 该 ViewModel 按远程设置 entry 保存, [decorator] 在远程设置 entry 出栈时释放它.
 */
@Stable
internal class RemoteSettingsNavigation(private val stores: ViewModelStoreProvider) {
    val decorator = NavEntryDecorator<NavRoutes>(
        onPop = { stores.clearKey(it) },
        decorate = { it.Content() },
    )

    @Composable
    fun viewModel(remoteEntryId: String): RemoteSettingsViewModel =
        viewModel(viewModelStoreOwner = rememberViewModelStoreOwner(remoteEntryId, stores)) {
            RemoteSettingsViewModel()
        }
}

@Composable
internal fun rememberRemoteSettingsNavigation(): RemoteSettingsNavigation {
    val stores = rememberViewModelStoreProvider()
    return remember(stores) { RemoteSettingsNavigation(stores) }
}

@Composable
internal fun RemoteSettingsRoute(
    route: NavRoutes.RemoteSettings,
    navigation: RemoteSettingsNavigation,
    navigator: AniNavigator,
) {
    RemoteSettingsScreen(
        navigation.viewModel(route.entryId),
        onNavigateBack = { navigator.popBackStack(route, inclusive = true) },
        onEditMediaSource = { factoryId, instanceId ->
            navigator.navigateRemoteEditMediaSource(route, factoryId, instanceId)
        },
        scanner = { scanned, cancel -> RemoteSettingsScanScreen(scanned, cancel) },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun RemoteSettingsScanScreen(onScanned: (String) -> Unit, onNavigateBack: () -> Unit) {
    QrCodeScanScreen(
        title = stringResource(Lang.remote_settings_scan),
        hint = stringResource(Lang.remote_settings_scan_hint),
        invalidHint = stringResource(Lang.remote_settings_invalid_qr),
        parse = { content -> content.takeIf { runCatching { RemoteSettingsLink.parse(it) }.isSuccess } },
        onScanned = onScanned,
        onNavigateBack = onNavigateBack,
    )
}

/** 让编辑器与编辑页 entry 同生命周期；编辑页的 ViewModel 负责关闭它。 */
private class RemoteSourceEditorHolder(val editor: RemoteMediaSourceEditor?) : ViewModel()

@Composable
internal fun RemoteEditMediaSourceRoute(
    route: NavRoutes.RemoteEditMediaSource,
    navigation: RemoteSettingsNavigation,
    navigator: AniNavigator,
    windowInsets: WindowInsets,
) {
    val remoteRoute = NavRoutes.RemoteSettings(route.remoteEntryId)
    val vm = navigation.viewModel(route.remoteEntryId)
    val instanceId = route.mediaSourceInstanceId
    // ViewModel 按电视连接区分, 以免与本机同 ID 数据源的编辑页共用.
    val viewModelKey = "$instanceId:${route.remoteEntryId}"
    // 编辑器的目标在整个 entry 生命周期（包括退出动画和重新进入组合）中保持不变。
    val editor = viewModel(key = "editor:$viewModelKey") {
        RemoteSourceEditorHolder(vm.sourceEditor(instanceId))
    }.editor
    if (editor == null) {
        // 会话已失效（例如进程重建后凭据丢失）或电视上没有该数据源，回到上一页。
        LaunchedEffect(route) { navigator.popBackStack(route, inclusive = true) }
        return
    }
    val isSaving by editor.isSaving.collectAsStateWithLifecycle()
    RemoteSettingsSessionHost(
        vm,
        onNavigateBack = { navigator.popBackStack(remoteRoute, inclusive = true) },
        // 退出会断开会话并丢弃尚未发送的自动保存。
        exitEnabled = !isSaving,
    ) { contentModifier, _ ->
        EditMediaSourceContent(
            FactoryId(route.factoryId),
            instanceId,
            onNavigateBack = { navigator.popBackStack(route, inclusive = true) },
            windowInsets,
            contentModifier,
            viewModelKey,
            editor,
        )
    }
}
