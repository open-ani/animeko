/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.update.UpdateInstallationRunner
import me.him188.ani.app.tools.update.UpdateInstallationState
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.processDragAndDropEventImpl
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_debug_install_package_cancel
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm_message
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm_new_version
import me.him188.ani.app.ui.lang.settings_debug_install_package_confirm_title
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_badge
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_meta
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_title
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_unknown_name
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_unsupported_meta
import me.him188.ani.app.ui.lang.settings_debug_install_package_drop_unsupported_title
import me.him188.ani.app.ui.lang.settings_debug_install_package_unsupported
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.name
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * 正在拖入窗口的内容, 用于在松手前展示提示.
 */
@Immutable
sealed interface DraggingPackage {
    /**
     * 拖入了当前平台支持自动安装的安装包.
     */
    data class Installable(val file: SystemPath) : DraggingPackage

    /**
     * 拖入的是文件, 但没有受支持的安装包. [fileName] 是首个文件的文件名.
     */
    data class Unsupported(val fileName: String) : DraggingPackage

    /**
     * 拖动阶段读不到拖入的内容 (部分系统只在松手后才提供文件列表), 松手后再判断.
     */
    data object Unknown : DraggingPackage
}

/**
 * 开发者功能「拖拽安装包以安装」的状态.
 *
 * 拖放会话开始时 [onDragStarted] 记录 [dragging] 供覆盖层展示, 结束时 [onDragEnded] 清空;
 * 松手后 [offer] 筛选出当前平台支持自动安装的安装包, 记为 [pendingPackage] 等待用户确认;
 * 确认后 [installPending] 调用安装器安装, 安装成功会退出当前进程并由外部更新程序重启.
 * 安装失败的原因通过 [installationState] 暴露, 失败的安装包记录在 [lastInstalledPackage] 供手动安装.
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
     * 正在拖入窗口的内容; `null` 表示没有进行中的拖放.
     *
     * 只随拖放会话的开始与结束变化, 不随指针进出窗口内其他拖放目标变化, 覆盖层因此不会闪烁.
     */
    var dragging: DraggingPackage? by mutableStateOf(null)
        private set

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

    /**
     * 拖放会话开始. [content] 为 `null` 表示拖动阶段读不到内容.
     * 拖入的不是文件 (例如文本) 时不展示覆盖层.
     */
    fun onDragStarted(content: DragAndDropContent?) {
        if (installationState.value is UpdateInstallationState.Installing) return
        dragging = when (content) {
            null -> DraggingPackage.Unknown
            is DragAndDropContent.FileList -> {
                val first = content.files.firstOrNull() ?: return
                content.files.firstOrNull { installer.isInstallablePackage(it.inSystem) }
                    ?.let { DraggingPackage.Installable(it.inSystem) }
                    ?: DraggingPackage.Unsupported(first.name)
            }

            is DragAndDropContent.PlainText, DragAndDropContent.Unsupported -> null
        }
    }

    fun onDragEnded() {
        dragging = null
    }

    /**
     * 处理松手后的内容. 取 [DragAndDropContent.FileList] 中首个受支持的安装包作为 [pendingPackage].
     */
    fun offer(content: DragAndDropContent): DropInstallPackageOutcome {
        if (installationState.value is UpdateInstallationState.Installing) {
            return DropInstallPackageOutcome.IGNORED
        }
        if (content !is DragAndDropContent.FileList || content.files.isEmpty()) {
            return DropInstallPackageOutcome.IGNORED
        }
        val file = content.files.firstOrNull { installer.isInstallablePackage(it.inSystem) }
            ?: return DropInstallPackageOutcome.UNSUPPORTED
        pendingPackage = file.inSystem
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
    const val OVERLAY = "drop_install_package_overlay"
    const val CONFIRM_BUTTON = "drop_install_package_confirm"
    const val CANCEL_BUTTON = "drop_install_package_cancel"
}

@Composable
fun rememberDropInstallPackageState(): DropInstallPackageState {
    return remember { DropInstallPackageState(GlobalKoin.get<UpdateInstaller>()) }
}

/**
 * 让 [content] 所在区域接受拖入的安装包 (开发者功能「拖拽安装包以安装」).
 *
 * [enabled] 为 `false` 时不参与拖放. 开启时 [content] 内部的其他拖放目标仍优先接收落在其上的拖放.
 * 拖入期间在 [content] 之上淡入一层提示 (松手以安装 / 不支持的文件), 拖放结束后淡出;
 * 松手后弹窗确认, 确认后安装并重启; 松手时拖入的不是安装包则提示支持的格式;
 * 安装失败时展示失败原因并允许打开安装包手动安装.
 */
@Composable
fun DropInstallPackageHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    state: DropInstallPackageState = rememberDropInstallPackageState(),
    content: @Composable () -> Unit,
) {
    val toaster = LocalToaster.current
    val unsupportedMessage = stringResource(
        Lang.settings_debug_install_package_unsupported,
        state.supportedExtensions.joinToString(", "),
    )
    val currentEnabled by rememberUpdatedState(enabled)
    val onDropContent by rememberUpdatedState { dropped: DragAndDropContent ->
        when (state.offer(dropped)) {
            DropInstallPackageOutcome.PENDING_CONFIRMATION -> true
            DropInstallPackageOutcome.UNSUPPORTED -> {
                toaster.toast(unsupportedMessage)
                false
            }

            DropInstallPackageOutcome.IGNORED -> false
        }
    }
    val target = remember(state) {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                // 部分系统在拖动阶段读取内容会抛异常, 此时按「读不到」处理, 松手后再判断.
                state.onDragStarted(runCatching { processDragAndDropEventImpl(event) }.getOrNull())
            }

            override fun onEnded(event: DragAndDropEvent) {
                state.onDragEnded()
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val dropped = runCatching { processDragAndDropEventImpl(event) }
                    .getOrDefault(DragAndDropContent.Unsupported)
                return onDropContent(dropped)
            }
        }
    }

    Box(modifier.dragAndDropTarget({ currentEnabled }, target)) {
        content()

        val dragging = state.dragging
        // 淡出期间仍需展示最后一次的内容
        var lastDragging by remember { mutableStateOf(dragging) }
        if (dragging != null) lastDragging = dragging
        AnimatedVisibility(
            visible = dragging != null,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 150)),
        ) {
            lastDragging?.let {
                DropInstallPackageOverlay(
                    dragging = it,
                    supportedExtensions = state.supportedExtensions,
                    currentVersion = currentAniBuildConfig.versionName,
                )
            }
        }
    }

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

private val DropCardCornerRadius = 28.dp

/**
 * 拖入期间覆盖在窗口内容之上的提示: 一层蒙版 (深色主题压暗, 浅色主题提亮, 让卡片边缘不与蒙版形成双重边),
 * 居中一张虚线边框的卡片, 没有持续动效.
 */
@Composable
internal fun DropInstallPackageOverlay(
    dragging: DraggingPackage,
    supportedExtensions: Set<String>,
    currentVersion: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val unsupported = dragging is DraggingPackage.Unsupported
    val title = stringResource(
        if (unsupported) Lang.settings_debug_install_package_drop_unsupported_title
        else Lang.settings_debug_install_package_drop_title,
    )
    val fileName = when (dragging) {
        is DraggingPackage.Installable -> dragging.file.name
        is DraggingPackage.Unsupported -> dragging.fileName
        DraggingPackage.Unknown -> stringResource(Lang.settings_debug_install_package_drop_unknown_name)
    }
    val meta = if (unsupported) {
        stringResource(Lang.settings_debug_install_package_drop_unsupported_meta, supportedExtensions.joinToString(", "))
    } else {
        stringResource(Lang.settings_debug_install_package_drop_meta, currentVersion)
    }

    val isDark = colors.surface.luminance() < 0.5f
    val scrimColor = if (isDark) colors.scrim.copy(alpha = 0.72f) else colors.surface.copy(alpha = 0.8f)
    Box(
        modifier
            .fillMaxSize()
            .background(scrimColor)
            .testTag(DropInstallPackageTestTags.OVERLAY),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .width(560.dp)
                .dashedBorder(if (unsupported) colors.error else colors.primary, DropCardCornerRadius),
            shape = RoundedCornerShape(DropCardCornerRadius),
            color = colors.surfaceContainer,
            shadowElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(start = 40.dp, top = 40.dp, end = 40.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(
                            if (unsupported) colors.errorContainer else colors.primaryContainer,
                            RoundedCornerShape(24.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (unsupported) Icons.Rounded.Block else Icons.Rounded.Inventory2,
                        contentDescription = null,
                        Modifier.size(36.dp),
                        tint = if (unsupported) colors.onErrorContainer else colors.onPrimaryContainer,
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Surface(
                    shape = CircleShape,
                    color = if (unsupported) colors.errorContainer else colors.secondaryContainer,
                    contentColor = if (unsupported) colors.onErrorContainer else colors.onSecondaryContainer,
                ) {
                    Text(
                        fileName,
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Surface(
                    Modifier.padding(top = 4.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = colors.outline,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Text(
                        stringResource(Lang.settings_debug_install_package_drop_badge),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * 在内容之上绘制一圈圆头虚线的圆角边框, 边框完全落在自身范围内.
 * 虚线长度按周长取整, 首尾相接处不会出现半截虚线.
 */
private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    width: Dp = 2.dp,
    dash: Dp = 14.dp,
): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2
    val rectSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
    val perimeter = 2 * (rectSize.width + rectSize.height) - 8 * radius + 2 * PI.toFloat() * radius
    val segments = (perimeter / (2 * dash.toPx())).roundToInt().coerceAtLeast(1)
    val segment = perimeter / (2 * segments)
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = rectSize,
        cornerRadius = CornerRadius(radius),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            // 圆头会让每段各向外延伸半个线宽, 相应缩短实线并加长间隔, 保持视觉上等分
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment - strokeWidth, segment + strokeWidth)),
        ),
    )
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
