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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.DragAndDropHoverState
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
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
                                append("支持 JPEG/PNG/WebP，最大 1MB。可裁剪为方形头像")
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
    // Very simple square crop UI: drag to move, slider to zoom
    val bitmap = remember(imageBytes) { me.him188.ani.app.ui.foundation.decodeImageBitmap(imageBytes) }
    val imgW = bitmap.width
    val imgH = bitmap.height
    val minDim = kotlin.math.min(imgW, imgH).toFloat()

    var zoom by remember { mutableStateOf(1f) } // 1f means using minDim as crop size
    val maxZoom = remember(imgW, imgH) { (minDim / 64f).coerceAtLeast(1f) } // ensure at least 64px crop
    var cropLeft by remember { mutableStateOf(((imgW - minDim) / 2f)) }
    var cropTop by remember { mutableStateOf(((imgH - minDim) / 2f)) }

    fun cropSize(): Float = (minDim / zoom)
    fun clamp() {
        val s = cropSize()
        cropLeft = cropLeft.coerceIn(0f, (imgW - s).coerceAtLeast(0f))
        cropTop = cropTop.coerceIn(0f, (imgH - s).coerceAtLeast(0f))
    }

    // keep centered when zoom changes
    val lastZoom = remember { mutableStateOf(zoom) }
    if (lastZoom.value != zoom) {
        val oldSize = minDim / lastZoom.value
        val newSize = cropSize()
        // keep center
        val centerX = cropLeft + oldSize / 2f
        val centerY = cropTop + oldSize / 2f
        cropLeft = centerX - newSize / 2f
        cropTop = centerY - newSize / 2f
        lastZoom.value = zoom
        clamp()
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                {
                    val size = cropSize().toInt().coerceAtLeast(1)
                    val bytes = me.him188.ani.app.ui.foundation.cropImageToSquare(
                        imageBytes,
                        me.him188.ani.app.ui.foundation.CropRect(cropLeft.toInt(), cropTop.toInt(), size),
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
                // square viewport
                val viewportDp = 280.dp
                val density = androidx.compose.ui.platform.LocalDensity.current
                val viewportPx = with(density) { viewportDp.toPx() }
                val scale = viewportPx / cropSize()

                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(viewportDp)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = MaterialTheme.shapes.small,
                        )
                        .pointerInput(imgW, imgH, zoom) {
                            detectDragGestures { _, dragAmount ->
                                val toOriginal = cropSize() / viewportPx
                                cropLeft -= dragAmount.x * toOriginal
                                cropTop -= dragAmount.y * toOriginal
                                clamp()
                            }
                        },
                ) {
                    // draw cropped area to fill canvas
                    val s = cropSize().toInt().coerceAtLeast(1)
                    drawImage(
                        image = bitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset(
                            cropLeft.toInt().coerceAtLeast(0),
                            cropTop.toInt().coerceAtLeast(0),
                        ),
                        srcSize = androidx.compose.ui.unit.IntSize(s.coerceAtMost(imgW), s.coerceAtMost(imgH)),
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    )
                }

                // zoom slider
                androidx.compose.material3.Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it.coerceIn(1f, maxZoom)
                        clamp()
                    },
                    valueRange = 1f..maxZoom,
                )
                Text("拖动图片调整位置，滑动调节缩放")
            }
        },
    )
}
