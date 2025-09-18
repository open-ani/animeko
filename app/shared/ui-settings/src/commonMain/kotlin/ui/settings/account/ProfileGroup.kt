/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.CropRect
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.DragAndDropHoverState
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.cropImageToSquare
import me.him188.ani.app.ui.foundation.decodeImageBitmap
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.rememberDragAndDropState
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.LoadErrorCardLayout
import me.him188.ani.app.ui.search.LoadErrorCardRole
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform

// Crop helpers for free-drag 1:1 selector
private enum class CropCorner { TL, TR, BL, BR }
private sealed interface CropDragMode {
    data object None : CropDragMode
    data object Move : CropDragMode
    data class Resize(val corner: CropCorner) : CropDragMode
}

@Composable
fun SettingsScope.ProfileGroup(
    onNavigateToEmail: () -> Unit,
    onNavigateToBangumiSync: () -> Unit,
    onNavigateToBangumiOAuth: () -> Unit,
    vm: ProfileViewModel = viewModel<ProfileViewModel> { ProfileViewModel() },
    modifier: Modifier = Modifier
) {
    val state by vm.stateFlow.collectAsStateWithLifecycle(initialValue = AccountSettingsState.Empty)
    val asyncHandler = rememberAsyncHandler()
    ProfileGroupImpl(
        state,
        isNicknameErrorProvider = { !vm.validateNickname(it) },
        onSaveNickname = { nickname ->
            asyncHandler.launch {
                vm.saveProfile(EditProfileState(nickname))
            }
        },
        onLogout = {
            asyncHandler.launch {
                vm.logout()
            }
        },
        onNavigateToEmail = onNavigateToEmail,
        onBangumiClick = {
            if (state.selfInfo.selfInfo?.bangumiUsername.isNullOrEmpty()) {
                onNavigateToBangumiOAuth()
            } else {
                onNavigateToBangumiSync()
            }
        },
        onAvatarUpload = {
            vm.uploadAvatar(it)
        },
        onAvatarUploadBytes = {
            vm.uploadAvatar(it)
        },
        onResetAvatarUploadState = {
            vm.resetAvatarUploadState()
        },
        modifier = modifier,
    )
}

/**
 * 个人账户信息
 */
@Composable
internal fun SettingsScope.ProfileGroupImpl(
    state: AccountSettingsState,
    isNicknameErrorProvider: (String) -> Boolean,
    onSaveNickname: (String) -> Unit,
    onAvatarUpload: suspend (PlatformFile) -> Boolean,
    onAvatarUploadBytes: suspend (ByteArray) -> Boolean,
    onResetAvatarUploadState: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToEmail: () -> Unit,
    onBangumiClick: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    val currentInfo = state.selfInfo.selfInfo
    val currentState by rememberUpdatedState(state.selfInfo)
    var showUploadAvatarDialog by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (windowSizeClass.isWidthCompact)
                Alignment.CenterHorizontally else Alignment.Start,
        ) {
            HeroIcon(
                Modifier.padding(vertical = if (windowSizeClass.isHeightAtLeastExpanded) 36.dp else 24.dp),
            ) {
                AvatarImage(
                    url = state.selfInfo.selfInfo?.avatarUrl,
                    modifier
                        .clip(CircleShape)
                        .clickable {
                            if (currentState.isSessionValid == true) {
                                // 仅当已登录时才允许编辑头像
                                showUploadAvatarDialog = true
                            }
                        }
                        .fillMaxSize()
                        .placeholder(state.selfInfo.isLoading),
                )
            }

            Column {
                // TODO: 2025/6/28 handle user info error
                val isPlaceholder = currentState.isSessionValid == null

                TextFieldItem(
                    value = currentInfo?.nickname.orEmpty(),
                    title = { Text("昵称") },
                    description = { Text(currentInfo?.nickname?.let { "@$it" } ?: "未设置") },
                    textFieldDescription = { Text("最多 20 字，只能包含中文、日文、英文、数字和下划线") },
                    onValueChangeCompleted = { onSaveNickname(it) },
                    inverseTitleDescription = true,
                    isErrorProvider = { isNicknameErrorProvider(it) },
                    sanitizeValue = { it.trim() },
                )

                val canBindEmail = remember(currentInfo) {
                    currentInfo != null && currentInfo.email == null
                }

                TextItem(
                    title = {
                        SelectionContainer {
                            Text(
                                currentInfo?.email ?: "未设置",
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    },
                    description = { Text("邮箱") },
                    modifier = Modifier.placeholder(isPlaceholder),
                    onClick = if (canBindEmail) onNavigateToEmail else null,
                    action = if (canBindEmail) {
                        {
                            IconButton(onNavigateToEmail) {
                                Icon(Icons.Rounded.Edit, "绑定", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else null,
                )
                TextItem(
                    title = {
                        SelectionContainer {
                            Text(currentInfo?.id.toString())
                        }
                    },
                    description = { Text("用户 ID") },
                    modifier = Modifier.placeholder(isPlaceholder),
                )

                Group(title = { Text("第三方账号") }) {
                    TextItem(
                        title = { Text("Bangumi") },
                        description = { Text(currentInfo?.bangumiUsername ?: "未绑定") },
                        icon = {
                            Image(Icons.Default.BangumiNext, contentDescription = "Bangumi Icon")
                        },
                        onClick = onBangumiClick,
                        modifier = Modifier.placeholder(isPlaceholder),
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        AccountLogoutDialog(
            {
                onLogout()
                showLogoutDialog = false
            },
            onCancel = { showLogoutDialog = false },
        )
    }

    if (showUploadAvatarDialog) {
        val asyncHandler = rememberAsyncHandler()
        UploadAvatarDialog(
            onDismissRequest = {
                showUploadAvatarDialog = false
            },
            state.avatarUploadState,
            onAvatarUpload = { file ->
                asyncHandler.launch {
                    showUploadAvatarDialog = !onAvatarUpload(file)
                }
            },
            onAvatarUploadBytes = { bytes ->
                asyncHandler.launch {
                    showUploadAvatarDialog = !onAvatarUploadBytes(bytes)
                }
            },
            onResetAvatarUploadState = onResetAvatarUploadState,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun SettingsScope.UploadAvatarDialog(
    onDismissRequest: () -> Unit,
    avatarUploadState: EditProfileState.UploadAvatarState,
    onAvatarUpload: (PlatformFile) -> Unit,
    onAvatarUploadBytes: (ByteArray) -> Unit,
    onResetAvatarUploadState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filePickerLaunched by rememberSaveable { mutableStateOf(false) }
    var cropTarget by remember { mutableStateOf<ByteArray?>(null) }
    val asyncHandler = rememberAsyncHandler()
    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.Image,
        title = "选择头像",
    ) {
        filePickerLaunched = false
        it?.let { file ->
            onResetAvatarUploadState()
            asyncHandler.launch {
                cropTarget = file.readBytes()
            }
        }
    }

    val dndState = rememberDragAndDropState dnd@{
        if (it !is DragAndDropContent.FileList || it.files.isEmpty()) return@dnd false

        onResetAvatarUploadState()
        asyncHandler.launch {
            cropTarget = PlatformFile(it.files.first()).readBytes()
        }
        return@dnd true
    }

    val dndBorderColor by animateColorAsState(
        when (dndState.hoverState) {
            DragAndDropHoverState.ENTERED -> MaterialTheme.colorScheme.primary
            DragAndDropHoverState.STARTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            DragAndDropHoverState.NONE -> Color.Transparent
        },
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !filePickerLaunched,
            ) {
                Text("完成")
            }
        },
        title = {
            Text("上传头像")
        },
        text = {
            Column(modifier) {
                TextItem(
                    title = { Text("选择文件") },
                    description = {
                        Text(
                            buildString {
                                if (currentPlatform() is Platform.Desktop) {
                                    append("或拖动文件到此处。")
                                }
                                append("支持 JPEG/PNG/WebP，最大 1MB。多次上传需间隔一分钟。")
                            },
                        )
                    },
                    onClickEnabled = !filePickerLaunched,
                    modifier = Modifier
                        .border(
                            BorderStroke(2.dp, dndBorderColor),
                            shape = MaterialTheme.shapes.small,
                        )
                        .dragAndDropTarget({ !filePickerLaunched }, dndState),
                    onClick = {
                        onResetAvatarUploadState()
                        filePicker.launch()
                        filePickerLaunched = true
                    },
                )

                AniAnimatedVisibility(
                    avatarUploadState is EditProfileState.UploadAvatarState.Uploading ||
                            avatarUploadState is EditProfileState.UploadAvatarState.Failed,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    when (avatarUploadState) {
                        is EditProfileState.UploadAvatarState.Uploading -> {
                            LoadErrorCardLayout(LoadErrorCardRole.Neural) {
                                ListItem(
                                    leadingContent = { CircularProgressIndicator(Modifier.size(24.dp)) },
                                    headlineContent = { Text(renderAvatarUploadMessage(avatarUploadState)) },
                                    colors = listItemColors,
                                )
                            }
                        }

                        is EditProfileState.UploadAvatarState.Failed -> {
                            when (avatarUploadState) {
                                is EditProfileState.UploadAvatarState.UnknownError -> {
                                    LoadErrorCard(
                                        avatarUploadState.loadError,
                                        onRetry = { onAvatarUpload(avatarUploadState.file) },
                                    )
                                }

                                is EditProfileState.UploadAvatarState.UnknownErrorWithRetry -> {
                                    LoadErrorCard(
                                        avatarUploadState.loadError,
                                        onRetry = avatarUploadState.onRetry,
                                    )
                                }

                                else -> {
                                    LoadErrorCardLayout(LoadErrorCardRole.Important) {
                                        ListItem(
                                            leadingContent = { Icon(Icons.Rounded.ErrorOutline, null) },
                                            headlineContent = { Text(renderAvatarUploadMessage(avatarUploadState)) },
                                            colors = listItemColors,
                                        )
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        },
    )

    val bytes = cropTarget
    if (bytes != null) {
        CropAvatarDialog(
            imageBytes = bytes,
            onDismissRequest = { cropTarget = null },
            onConfirmCropped = { cropped ->
                onAvatarUploadBytes(cropped)
                cropTarget = null // keep upload dialog open to show progress
            },
        )
    }
}

private fun renderAvatarUploadMessage(
    state: EditProfileState.UploadAvatarState,
): String {
    return when (state) {
        is EditProfileState.UploadAvatarState.Uploading -> "正在上传..."
        is EditProfileState.UploadAvatarState.SizeExceeded -> "图片大小超过 1MB"
        is EditProfileState.UploadAvatarState.InvalidFormat -> "图片格式不支持"
        is EditProfileState.UploadAvatarState.UnknownError -> renderLoadErrorMessage(state.loadError)
        is EditProfileState.UploadAvatarState.UnknownErrorWithRetry -> renderLoadErrorMessage(state.loadError)
        is EditProfileState.UploadAvatarState.Success, EditProfileState.UploadAvatarState.Default -> ""
    }
}

@Composable
private fun CropAvatarDialog(
    imageBytes: ByteArray,
    onDismissRequest: () -> Unit,
    onConfirmCropped: (ByteArray) -> Unit,
) {
    // Free-drag 1:1 selection box over scaled image inside a square viewport
    val bitmap = remember(imageBytes) { decodeImageBitmap(imageBytes) }
    val imgW = bitmap.width
    val imgH = bitmap.height
    val minCropPx = 64 // minimum crop size in original image pixels

    // Selection stored in viewport (Canvas) coordinates
    var selVx by rememberSaveable { mutableFloatStateOf(0f) }
    var selVy by rememberSaveable { mutableFloatStateOf(0f) }
    var selVs by rememberSaveable { mutableFloatStateOf(0f) }

    // Cache latest mapping from viewport->image for confirm click
    var lastScale by rememberSaveable { mutableFloatStateOf(1f) }
    var lastOffsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var lastOffsetY by rememberSaveable { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                {
                    // Map viewport selection back to image coordinates using cached mapping
                    val cropX = ((selVx - lastOffsetX) / lastScale).toInt().coerceIn(0, imgW)
                    val cropY = ((selVy - lastOffsetY) / lastScale).toInt().coerceIn(0, imgH)
                    val cropSize = (selVs / lastScale).toInt().coerceAtLeast(1)
                    val safeSize = kotlin.math.min(cropSize, kotlin.math.min(imgW - cropX, imgH - cropY))

                    val bytes = cropImageToSquare(
                        imageBytes,
                        CropRect(
                            x = cropX.coerceAtMost(imgW - 1),
                            y = cropY.coerceAtMost(imgH - 1),
                            size = safeSize,
                        ),
                        outputSize = 512,
                    )
                    onConfirmCropped(bytes)
                },
            ) {
                Text("裁剪并上传")
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) { Text("取消") }
        },
        title = { Text("裁剪头像") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val isAndroid = currentPlatform() is Platform.Mobile
                // Slightly larger viewport on Android for easier touch interactions
                val viewportDp = if (isAndroid) 360.dp else 320.dp
                val density = LocalDensity.current
                val viewportPx = with(density) { viewportDp.toPx() }
                // Actual canvas size may be smaller due to parent constraints
                var canvasSize by remember { mutableStateOf(IntSize.Zero) }

                val canvasW = canvasSize.width.takeIf { it > 0 }?.toFloat() ?: viewportPx
                val canvasH = canvasSize.height.takeIf { it > 0 }?.toFloat() ?: viewportPx

                // Fit whole image into current canvas (contain)
                val scale = remember(imgW, imgH, canvasW, canvasH) {
                    kotlin.math.min(canvasW / imgW, canvasH / imgH)
                }
                val dispW = imgW * scale
                val dispH = imgH * scale
                val offsetX = (canvasW - dispW) / 2f
                val offsetY = (canvasH - dispH) / 2f
                val minAllowedV = (minCropPx * scale).coerceAtLeast(1f)

                // cache mapping for Confirm action (used in onClick)
                lastScale = scale
                lastOffsetX = offsetX
                lastOffsetY = offsetY

                // Initialize selection in viewport coordinates (only first time)
                if (selVs == 0f && scale > 0f) {
                    selVs = kotlin.math.min(dispW, dispH) * 0.8f
                    selVx = offsetX + (dispW - selVs) / 2f
                    selVy = offsetY + (dispH - selVs) / 2f
                }

                val handleDp = if (isAndroid) 18.dp else 14.dp
                val handlePx = with(density) { handleDp.toPx() }
                // Enlarge clickable area on touch devices for easier dragging
                val handleHitExtra = with(density) { (if (isAndroid) 16.dp else 8.dp).toPx() }

                fun clampSelectionV() {
                    val maxSize = kotlin.math.min(dispW, dispH)
                    selVs = selVs.coerceIn(minAllowedV, maxSize)
                    selVx = selVx.coerceIn(offsetX, (offsetX + dispW - selVs).coerceAtLeast(offsetX))
                    selVy = selVy.coerceIn(offsetY, (offsetY + dispH - selVs).coerceAtLeast(offsetY))
                }
                clampSelectionV()

                var mode by remember { mutableStateOf<CropDragMode>(CropDragMode.None) }

                // Get theming values in composable context (not in draw block)
                val borderColor = MaterialTheme.colorScheme.primary

                Canvas(
                    modifier = Modifier
                        .size(viewportDp)
                        .onSizeChanged { canvasSize = it }
                        // Ensure the image never paints outside the visible frame
                        .clip(MaterialTheme.shapes.small)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = MaterialTheme.shapes.small,
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    val sx = selVx
                                    val sy = selVy
                                    val ss = selVs
                                    val cx = p.x
                                    val cy = p.y

                                    fun inRect(x: Float, y: Float, w: Float, h: Float) =
                                        cx >= x && cx <= x + w && cy >= y && cy <= y + h

                                    val tl = Pair(sx - handleHitExtra, sy - handleHitExtra)
                                    val tr = Pair(sx + ss - handlePx - handleHitExtra, sy - handleHitExtra)
                                    val bl = Pair(sx - handleHitExtra, sy + ss - handlePx - handleHitExtra)
                                    val br =
                                        Pair(sx + ss - handlePx - handleHitExtra, sy + ss - handlePx - handleHitExtra)

                                    mode = when {
                                        inRect(
                                            tl.first,
                                            tl.second,
                                            handlePx + 2 * handleHitExtra,
                                            handlePx + 2 * handleHitExtra,
                                        ) -> CropDragMode.Resize(CropCorner.TL)

                                        inRect(
                                            tr.first,
                                            tr.second,
                                            handlePx + 2 * handleHitExtra,
                                            handlePx + 2 * handleHitExtra,
                                        ) -> CropDragMode.Resize(CropCorner.TR)

                                        inRect(
                                            bl.first,
                                            bl.second,
                                            handlePx + 2 * handleHitExtra,
                                            handlePx + 2 * handleHitExtra,
                                        ) -> CropDragMode.Resize(CropCorner.BL)

                                        inRect(
                                            br.first,
                                            br.second,
                                            handlePx + 2 * handleHitExtra,
                                            handlePx + 2 * handleHitExtra,
                                        ) -> CropDragMode.Resize(CropCorner.BR)

                                        inRect(sx, sy, ss, ss) -> CropDragMode.Move
                                        else -> CropDragMode.None
                                    }
                                },
                                onDragEnd = { mode = CropDragMode.None },
                            ) { _, drag ->
                                when (val m = mode) {
                                    CropDragMode.None -> return@detectDragGestures
                                    CropDragMode.Move -> {
                                        selVx += drag.x
                                        selVy += drag.y
                                        clampSelectionV()
                                    }

                                    is CropDragMode.Resize -> {
                                        val dx = drag.x
                                        val dy = drag.y
                                        when (m.corner) {
                                            CropCorner.TL -> {
                                                val anchorX = selVx + selVs
                                                val anchorY = selVy + selVs
                                                val newX = (selVx + dx).coerceAtMost(anchorX - minAllowedV)
                                                    .coerceAtLeast(offsetX)
                                                val newY = (selVy + dy).coerceAtMost(anchorY - minAllowedV)
                                                    .coerceAtLeast(offsetY)
                                                val newSize = kotlin.math.min(anchorX - newX, anchorY - newY)
                                                selVx = anchorX - newSize
                                                selVy = anchorY - newSize
                                                selVs = newSize
                                            }

                                            CropCorner.TR -> {
                                                val anchorX = selVx
                                                val anchorY = selVy + selVs
                                                val newRight = (selVx + selVs + dx).coerceAtLeast(anchorX + minAllowedV)
                                                    .coerceAtMost(offsetX + dispW)
                                                val newTop = (selVy + dy).coerceAtMost(anchorY - minAllowedV)
                                                    .coerceAtLeast(offsetY)
                                                val newSize = kotlin.math.min(newRight - anchorX, anchorY - newTop)
                                                selVx = anchorX
                                                selVy = anchorY - newSize
                                                selVs = newSize
                                            }

                                            CropCorner.BL -> {
                                                val anchorX = selVx + selVs
                                                val anchorY = selVy
                                                val newLeft = (selVx + dx).coerceAtMost(anchorX - minAllowedV)
                                                    .coerceAtLeast(offsetX)
                                                val newBottom =
                                                    (selVy + selVs + dy).coerceAtLeast(anchorY + minAllowedV)
                                                        .coerceAtMost(offsetY + dispH)
                                                val newSize = kotlin.math.min(anchorX - newLeft, newBottom - anchorY)
                                                selVx = anchorX - newSize
                                                selVy = anchorY
                                                selVs = newSize
                                            }

                                            CropCorner.BR -> {
                                                val anchorX = selVx
                                                val anchorY = selVy
                                                val newRight = (selVx + selVs + dx).coerceAtLeast(anchorX + minAllowedV)
                                                    .coerceAtMost(offsetX + dispW)
                                                val newBottom =
                                                    (selVy + selVs + dy).coerceAtLeast(anchorY + minAllowedV)
                                                        .coerceAtMost(offsetY + dispH)
                                                val newSize = kotlin.math.min(newRight - anchorX, newBottom - anchorY)
                                                selVx = anchorX
                                                selVy = anchorY
                                                selVs = newSize
                                            }
                                        }
                                        clampSelectionV()
                                    }
                                }
                            }
                        },
                ) {
                    // Draw base image (fit center)
                    drawImage(
                        image = bitmap,
                        dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                        dstSize = IntSize(dispW.toInt(), dispH.toInt()),
                    )

                    // Overlay outside selection
                    val sx = selVx
                    val sy = selVy
                    val ss = selVs

                    val overlayColor = Color.Black.copy(alpha = 0.45f)
                    drawRect(overlayColor, topLeft = Offset(0f, 0f), size = Size(size.width, sy))
                    drawRect(overlayColor, topLeft = Offset(0f, sy), size = Size(sx, ss))
                    drawRect(overlayColor, topLeft = Offset(sx + ss, sy), size = Size(size.width - (sx + ss), ss))
                    drawRect(
                        overlayColor,
                        topLeft = Offset(0f, sy + ss),
                        size = Size(size.width, size.height - (sy + ss)),
                    )

                    // Border
                    drawRect(
                        color = borderColor,
                        topLeft = Offset(sx, sy),
                        size = Size(ss, ss),
                        style = Stroke(width = 2f),
                    )

                    // Corner handles
                    fun drawHandle(x: Float, y: Float) {
                        drawRect(
                            color = borderColor,
                            topLeft = Offset(x, y),
                            size = Size(handlePx, handlePx),
                        )
                    }
                    drawHandle(sx - handlePx / 2, sy - handlePx / 2)
                    drawHandle(sx + ss - handlePx / 2, sy - handlePx / 2)
                    drawHandle(sx - handlePx / 2, sy + ss - handlePx / 2)
                    drawHandle(sx + ss - handlePx / 2, sy + ss - handlePx / 2)
                }

                Text("拖动选框移动，拖动角点调整大小", Modifier.padding(top = 8.dp))
            }
        },
    )
}
