/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.update.UpdateInstallationRunner
import me.him188.ani.app.tools.update.UpdateInstallationState
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.WindowDropCardContent
import me.him188.ani.app.ui.foundation.WindowDropHandler
import me.him188.ani.app.ui.foundation.WindowDropPreview
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_debug_install_package_cancel
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm_message
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm_new_version
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm_title
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_badge
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_meta
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_supported_hint
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_title
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_unknown_name
import me.him188.ani.app.ui.lang.settings_debug_install_package_unsupported
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.name
import org.jetbrains.compose.resources.stringResource

/**
 * 开发者功能「拖拽安装包以安装」的状态.
 *
 * 松手后 [offer] 筛选出当前平台支持自动安装的安装包, 记为 [pendingPackage] 等待用户确认;
 * 确认后 [installPending] 调用安装器安装, 安装成功会退出当前进程并由外部更新程序重启.
 * 安装失败的原因通过 [installationState] 暴露, 失败的安装包记录在 [lastInstalledPackage] 供手动安装.
 * 窗口拖放的接入见 [InstallPackageDropHandler], 对话框见 [InstallPackageDropDialogs].
 */
@Stable
class DropInstallPackageState(
    private val installer: UpdateInstaller,
) {
    private val installationRunner = UpdateInstallationRunner(installer)

    val installationState: StateFlow<UpdateInstallationState> get() = installationRunner.state

    /**
     * 当前平台支持自动安装的安装包扩展名, 用于向用户展示.
     */
    val supportedExtensions: Set<String> get() = installer.installablePackageExtensions

    /**
     * 等待用户确认安装的安装包.
     */
    var pendingPackage: SystemPath? by mutableStateOf(null)
        private set

    /**
     * 最近一次调用安装器安装的安装包.
     */
    var lastInstalledPackage: SystemPath? by mutableStateOf(null)
        private set

    val isInstalling: Boolean get() = installationState.value is UpdateInstallationState.Installing

    /**
     * [files] 中首个当前平台支持自动安装的安装包.
     */
    fun findInstallablePackage(files: List<Path>): SystemPath? =
        files.firstOrNull { installer.isInstallablePackage(it.inSystem) }?.inSystem

    /**
     * 处理松手后的内容. 取 [DragAndDropContent.FileList] 中首个受支持的安装包作为 [pendingPackage].
     */
    fun offer(content: DragAndDropContent): DropInstallPackageOutcome {
        if (isInstalling) {
            return DropInstallPackageOutcome.IGNORED
        }
        if (content !is DragAndDropContent.FileList || content.files.isEmpty()) {
            return DropInstallPackageOutcome.IGNORED
        }
        val file = findInstallablePackage(content.files)
            ?: return DropInstallPackageOutcome.UNSUPPORTED
        pendingPackage = file
        return DropInstallPackageOutcome.PENDING_CONFIRMATION
    }

    fun dismissPending() {
        pendingPackage = null
    }

    /**
     * 安装 [pendingPackage]. 调用后 [pendingPackage] 立即清空, 失败原因见 [installationState].
     */
    suspend fun installPending(context: ContextMP) {
        val file = pendingPackage ?: return
        pendingPackage = null
        lastInstalledPackage = file
        installationRunner.install(file, packageUrls = emptyList(), context = context)
    }

    fun dismissFailure() {
        installationRunner.dismissFailure()
    }
}

enum class DropInstallPackageOutcome {
    /**
     * 内容与本功能无关 (不是文件列表, 或正在安装中), 交由其他拖放目标处理.
     */
    IGNORED,

    /**
     * 拖入了文件, 但没有当前平台支持自动安装的安装包.
     */
    UNSUPPORTED,

    /**
     * 已记录待确认的安装包.
     */
    PENDING_CONFIRMATION,
}

/**
 * 从安装包文件名中提取版本号, 例如 `Ani-4.12.0-macos-aarch64.dmg` 得到 `4.12.0`,
 * `ani-4.12.0-beta02-windows-x86_64.zip` 得到 `4.12.0-beta02`. 提取不到时返回 `null`.
 */
internal fun parsePackageVersion(fileName: String): String? = PACKAGE_VERSION_REGEX.find(fileName)?.value

private val PACKAGE_VERSION_REGEX =
    Regex("""\d+\.\d+\.\d+(?:-(?:alpha|beta|rc|dev)[0-9A-Za-z]*)?""", RegexOption.IGNORE_CASE)

object DropInstallPackageTestTags {
    const val CONFIRM_BUTTON = "drop_install_package_confirm"
    const val CANCEL_BUTTON = "drop_install_package_cancel"
}

@Composable
fun rememberDropInstallPackageState(): DropInstallPackageState {
    return remember { DropInstallPackageState(GlobalKoin.get<UpdateInstaller>()) }
}

/**
 * 「拖拽安装包以安装」的窗口拖放处理者.
 *
 * 只接管含有受支持安装包的文件列表, 以及拖动阶段读不到内容的拖放 (松手后再判断);
 * 其他内容交给后续处理者. 松手时没有受支持的安装包则调用 [onUnsupported].
 */
class InstallPackageDropHandler(
    private val state: DropInstallPackageState,
    private val onUnsupported: () -> Unit,
) : WindowDropHandler {
    override fun onDragStarted(content: DragAndDropContent?): WindowDropPreview? {
        if (state.isInstalling) return null
        val file = when (content) {
            null -> null
            is DragAndDropContent.FileList -> state.findInstallablePackage(content.files) ?: return null
            is DragAndDropContent.PlainText, DragAndDropContent.Unsupported -> return null
        }
        return WindowDropPreview { InstallPackageDropCard(file) }
    }

    override fun onDrop(content: DragAndDropContent): Boolean {
        return when (state.offer(content)) {
            DropInstallPackageOutcome.PENDING_CONFIRMATION -> true
            DropInstallPackageOutcome.UNSUPPORTED -> {
                onUnsupported()
                false
            }

            DropInstallPackageOutcome.IGNORED -> false
        }
    }

    @Composable
    override fun supportedHint(): String = stringResource(
        Lang.settings_debug_install_package_drop_supported_hint,
        state.supportedExtensions.joinToString("、"),
    )
}

/**
 * 创建 [InstallPackageDropHandler], 松手时拖入的不是安装包则 toast 提示支持的格式.
 */
@Composable
fun rememberInstallPackageDropHandler(state: DropInstallPackageState): InstallPackageDropHandler {
    val toaster = LocalToaster.current
    val unsupportedMessage = stringResource(
        Lang.settings_debug_install_package_unsupported,
        state.supportedExtensions.joinToString(", "),
    )
    val onUnsupported by rememberUpdatedState { toaster.toast(unsupportedMessage) }
    return remember(state) { InstallPackageDropHandler(state) { onUnsupported() } }
}

/**
 * 拖入安装包时的卡片内容. [file] 为 `null` 表示拖动阶段读不到文件名.
 */
@Composable
private fun InstallPackageDropCard(file: SystemPath?) {
    WindowDropCardContent(
        icon = Icons.Rounded.Inventory2,
        title = stringResource(Lang.settings_debug_install_package_drop_title),
        subtitle = file?.name ?: stringResource(Lang.settings_debug_install_package_drop_unknown_name),
        description = stringResource(Lang.settings_debug_install_package_drop_meta, currentAniBuildConfig.versionName),
        badge = stringResource(Lang.settings_debug_install_package_drop_badge),
    )
}

/**
 * 「拖拽安装包以安装」的对话框: 松手后的确认框, 以及安装失败的提示 (可打开安装包手动安装).
 * 放在主窗口内容的同级, 与 [InstallPackageDropHandler] 配合使用.
 */
@Composable
fun InstallPackageDropDialogs(state: DropInstallPackageState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    state.pendingPackage?.let { file ->
        ConfirmInstallPackageDialog(
            file = file,
            currentVersion = currentAniBuildConfig.versionName,
            onConfirm = {
                scope.launch { state.installPending(context) }
            },
            onDismissRequest = state::dismissPending,
        )
    }

    val installationState by state.installationState.collectAsStateWithLifecycle()
    (installationState as? UpdateInstallationState.Failed)?.let { failed ->
        FailedToInstallDialog(
            message = failed.result.message ?: failed.result.reason.toString(),
            onDismissRequest = state::dismissFailure,
            file = state.lastInstalledPackage,
        )
    }
}

@Composable
private fun ConfirmInstallPackageDialog(
    file: SystemPath,
    currentVersion: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fileName = file.name
    val newVersion = remember(fileName) { parsePackageVersion(fileName) }
    val details = remember(file) {
        listOfNotNull(
            file.path.parent?.toString(),
            runCatching { file.length() }.getOrNull()?.takeIf { it > 0 }?.bytes?.toString(),
        ).joinToString(" · ")
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(DropInstallPackageTestTags.CONFIRM_BUTTON),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Lang.settings_debug_install_package_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag(DropInstallPackageTestTags.CANCEL_BUTTON),
            ) {
                Text(stringResource(Lang.settings_debug_install_package_cancel))
            }
        },
        title = { Text(stringResource(Lang.settings_debug_install_package_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(48.dp).background(colors.primaryContainer, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Inventory2,
                                contentDescription = null,
                                Modifier.size(26.dp),
                                tint = colors.onPrimaryContainer,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                            if (details.isNotEmpty()) {
                                Text(
                                    details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.MiddleEllipsis,
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VersionChip(currentVersion, colors.secondaryContainer, colors.onSecondaryContainer)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        Modifier.size(20.dp),
                        tint = colors.outline,
                    )
                    VersionChip(
                        newVersion ?: stringResource(Lang.settings_debug_install_package_confirm_new_version),
                        colors.primaryContainer,
                        colors.onPrimaryContainer,
                    )
                }
                Text(stringResource(Lang.settings_debug_install_package_confirm_message))
            }
        },
    )
}

@Composable
private fun VersionChip(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Text(
            text,
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
