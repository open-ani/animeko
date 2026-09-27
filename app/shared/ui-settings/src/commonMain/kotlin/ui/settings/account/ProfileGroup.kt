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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCodeScanner
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
import androidx.compose.ui.platform.testTag
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
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.CropRect
import me.him188.ani.app.ui.foundation.DragAndDropContent
import me.him188.ani.app.ui.foundation.DragAndDropHoverState
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.cropImageToSquare
import me.him188.ani.app.ui.foundation.decodeImageBitmap
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.OAuthPlatformIcon
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.rememberDragAndDropState
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.qr_login_settings_description
import me.him188.ani.app.ui.lang.qr_login_title
import me.him188.ani.app.ui.lang.settings_account_profile_avatar_invalid_format
import me.him188.ani.app.ui.lang.settings_account_profile_avatar_size_exceeded
import me.him188.ani.app.ui.lang.settings_account_profile_bind
import me.him188.ani.app.ui.lang.settings_account_profile_crop_and_upload
import me.him188.ani.app.ui.lang.settings_account_profile_crop_avatar
import me.him188.ani.app.ui.lang.settings_account_profile_crop_hint
import me.him188.ani.app.ui.lang.settings_account_profile_done
import me.him188.ani.app.ui.lang.settings_account_profile_email
import me.him188.ani.app.ui.lang.settings_account_profile_nickname
import me.him188.ani.app.ui.lang.settings_account_profile_nickname_hint
import me.him188.ani.app.ui.lang.settings_account_profile_not_bound
import me.him188.ani.app.ui.lang.settings_account_profile_not_set
import me.him188.ani.app.ui.lang.settings_account_profile_select_file
import me.him188.ani.app.ui.lang.settings_account_profile_select_file_description
import me.him188.ani.app.ui.lang.settings_account_profile_select_file_description_desktop
import me.him188.ani.app.ui.lang.settings_account_profile_third_party_accounts
import me.him188.ani.app.ui.lang.settings_account_profile_unbind
import me.him188.ani.app.ui.lang.settings_account_profile_unbind_bangumi_confirmation
import me.him188.ani.app.ui.lang.settings_account_profile_unbind_confirmation
import me.him188.ani.app.ui.lang.settings_account_profile_unbind_email_confirmation
import me.him188.ani.app.ui.lang.settings_account_profile_unbind_email_last_login_method
import me.him188.ani.app.ui.lang.settings_account_profile_ok
import me.him188.ani.app.ui.lang.login_change_email
import me.him188.ani.app.ui.lang.settings_account_profile_upload_avatar
import me.him188.ani.app.ui.lang.settings_account_profile_uploading_avatar
import me.him188.ani.app.ui.lang.settings_account_profile_user_id
import me.him188.ani.app.ui.lang.subject_collection_cancel
import me.him188.ani.app.ui.search.LoadErrorCard
import me.him188.ani.app.ui.search.LoadErrorCardLayout
import me.him188.ani.app.ui.search.LoadErrorCardRole
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import org.jetbrains.compose.resources.stringResource

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
    onNavigateToOAuth: (OAuthPlatform) -> Unit,
    onNavigateToGithubAccount: () -> Unit,
    vm: ProfileViewModel = viewModel<ProfileViewModel> { ProfileViewModel() },
    modifier: Modifier = Modifier,
    /**
     * 前往扫码登录其他设备. 为 `null` (当前平台不能扫码) 时不显示入口
     */
    onNavigateToQrLogin: (() -> Unit)? = null,
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
                onNavigateToOAuth(OAuthPlatform.BANGUMI)
            } else {
                onNavigateToBangumiSync()
            }
        },
        onExternalAccountClick = onNavigateToOAuth,
        onGithubAccountClick = onNavigateToGithubAccount,
        onQrLoginClick = onNavigateToQrLogin,
        onAvatarUpload = {
            vm.uploadAvatar(it)
        },
        onAvatarUploadBytes = {
            vm.uploadAvatar(it)
        },
        onResetAvatarUploadState = {
            vm.resetAvatarUploadState()
        },
        onUnbindBangumi = {
            asyncHandler.launch {
                vm.unbindBangumi()
            }
        },
        onUnbindExternalAccount = { provider ->
            asyncHandler.launch {
                vm.unbindExternalAccount(provider)
            }
        },
        onUnbindEmail = {
            asyncHandler.launch {
                vm.unbindEmail()
            }
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
    onUnbindBangumi: () -> Unit,
    /**
     * 点击未绑定的第三方平台, 前往绑定
     */
    onExternalAccountClick: (OAuthPlatform) -> Unit,
    /**
     * 点击已绑定的 GitHub 账号, 前往该账号的详情页 (开发者认证). 其他平台的已绑定账号不可点击
     */
    onGithubAccountClick: () -> Unit,
    /**
     * 参数为平台 ID
     */
    onUnbindExternalAccount: (provider: String) -> Unit,
    onUnbindEmail: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 点击 "扫码登录", 为其他设备 (例如电视) 登录当前账号. 为 `null` 时不显示. 未登录时也不显示
     */
    onQrLoginClick: (() -> Unit)? = null,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo1().windowSizeClass,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUnbindBangumiDialog by remember { mutableStateOf(false) }
    // 待确认解绑的第三方平台 ID
    var unbindingExternalProvider by remember { mutableStateOf<String?>(null) }
    var showUnbindEmailDialog by remember { mutableStateOf(false) }

    val currentInfo = state.selfInfo.selfInfo
    val currentState by rememberUpdatedState(state.selfInfo)
    var showUploadAvatarDialog by rememberSaveable { mutableStateOf(false) }
    val notSetText = stringResource(Lang.settings_account_profile_not_set)
    val nicknameText = stringResource(Lang.settings_account_profile_nickname)
    val nicknameHintText = stringResource(Lang.settings_account_profile_nickname_hint)
    val emailText = stringResource(Lang.settings_account_profile_email)
    val bindText = stringResource(Lang.settings_account_profile_bind)
    val changeEmailText = stringResource(Lang.login_change_email)
    val userIdText = stringResource(Lang.settings_account_profile_user_id)
    val thirdPartyAccountsText = stringResource(Lang.settings_account_profile_third_party_accounts)
    val notBoundText = stringResource(Lang.settings_account_profile_not_bound)
    val unbindText = stringResource(Lang.settings_account_profile_unbind)

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
                    title = { Text(nicknameText) },
                    description = { Text(currentInfo?.nickname?.let { "@$it" } ?: notSetText) },
                    textFieldDescription = { Text(nicknameHintText) },
                    onValueChangeCompleted = { onSaveNickname(it) },
                    inverseTitleDescription = true,
                    isErrorProvider = { isNicknameErrorProvider(it) },
                    sanitizeValue = { it.trim() },
                )

                // 未绑定时点击去绑定, 已绑定时点击去换绑
                val canEditEmail = currentInfo != null
                val hasEmail = currentInfo?.email != null

                TextItem(
                    title = {
                        SelectionContainer {
                            Text(
                                currentInfo?.email ?: notSetText,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    },
                    description = { Text(emailText) },
                    modifier = Modifier.placeholder(isPlaceholder).testTag("email"),
                    onClick = if (canEditEmail) onNavigateToEmail else null,
                    action = if (canEditEmail) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hasEmail) {
                                    TextButton(
                                        onClick = { showUnbindEmailDialog = true },
                                        modifier = Modifier.testTag("email-unbind"),
                                    ) { Text(unbindText) }
                                }
                                IconButton(onNavigateToEmail, Modifier.testTag("email-edit")) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        if (hasEmail) changeEmailText else bindText,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
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
                    description = { Text(userIdText) },
                    modifier = Modifier.placeholder(isPlaceholder),
                )

                if (onQrLoginClick != null && currentState.isSessionValid == true) {
                    TextItem(
                        title = { Text(stringResource(Lang.qr_login_title)) },
                        description = { Text(stringResource(Lang.qr_login_settings_description)) },
                        icon = { Icon(Icons.Rounded.QrCodeScanner, null) },
                        onClick = onQrLoginClick,
                        modifier = Modifier.testTag("qrLogin"),
                    )
                }

                Group(title = { Text(thirdPartyAccountsText) }) {
                    TextItem(
                        title = { Text("Bangumi") },
                        description = { Text(currentInfo?.bangumiUsername ?: notBoundText) },
                        icon = {
                            Image(Icons.Default.BangumiNext, contentDescription = "Bangumi Icon")
                        },
                        onClick = onBangumiClick,
                        action = if (!currentInfo?.bangumiUsername.isNullOrEmpty()) {
                            {
                                TextButton(onClick = { showUnbindBangumiDialog = true }) { Text(unbindText) }
                            }
                        } else null,
                        modifier = Modifier.placeholder(isPlaceholder),
                    )
                    // 服务端已启用的平台, 以及已绑定但服务端已关闭的平台 (仍可解绑)
                    val boundAccounts = currentInfo?.externalAccounts.orEmpty()
                    val knownPlatforms = (state.externalPlatforms + boundAccounts.mapNotNull { OAuthPlatform.fromId(it.provider) })
                        .distinct().filter { it != OAuthPlatform.BANGUMI }
                    for (platform in knownPlatforms) {
                        val account = boundAccounts.firstOrNull { it.provider == platform.id }
                        TextItem(
                            title = { Text(platform.displayName) },
                            description = { Text(account?.username ?: notBoundText) },
                            icon = { OAuthPlatformIcon(platform, Modifier.size(24.dp)) },
                            onClick = when {
                                account == null -> {
                                    { onExternalAccountClick(platform) }
                                }

                                platform == OAuthPlatform.GITHUB -> onGithubAccountClick

                                else -> null
                            },
                            action = if (account != null) {
                                {
                                    TextButton(
                                        onClick = { unbindingExternalProvider = platform.id },
                                        modifier = Modifier.testTag("externalAccount-${platform.id}-unbind"),
                                    ) { Text(unbindText) }
                                }
                            } else null,
                            modifier = Modifier.placeholder(isPlaceholder).testTag("externalAccount-${platform.id}"),
                        )
                    }
                    // 客户端不认识的平台 (新版本服务端增加的): 只能解绑
                    for (account in boundAccounts.filter { OAuthPlatform.fromId(it.provider) == null }) {
                        TextItem(
                            title = { Text(account.provider) },
                            description = { Text(account.username ?: notBoundText) },
                            action = {
                                TextButton(
                                    onClick = { unbindingExternalProvider = account.provider },
                                    modifier = Modifier.testTag("externalAccount-${account.provider}-unbind"),
                                ) { Text(unbindText) }
                            },
                            modifier = Modifier.placeholder(isPlaceholder).testTag("externalAccount-${account.provider}"),
                        )
                    }
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

    if (showUnbindBangumiDialog) {
        UnbindBangumiDialog(
            onConfirm = {
                onUnbindBangumi()
                showUnbindBangumiDialog = false
            },
            onCancel = { showUnbindBangumiDialog = false },
        )
    }

    if (showUnbindEmailDialog) {
        // 邮箱是唯一登录方式时不能解绑, 否则用户将无法再登录. 服务端也会拒绝 (409), 这里提前告知用户
        val hasOtherLoginMethod = !currentInfo?.bangumiUsername.isNullOrEmpty()
                || currentInfo?.externalAccounts.orEmpty().isNotEmpty()
        UnbindEmailDialog(
            email = currentInfo?.email.orEmpty(),
            canUnbind = hasOtherLoginMethod,
            onConfirm = {
                onUnbindEmail()
                showUnbindEmailDialog = false
            },
            onCancel = { showUnbindEmailDialog = false },
        )
    }

    unbindingExternalProvider?.let { provider ->
        UnbindExternalAccountDialog(
            platformName = OAuthPlatform.fromId(provider)?.displayName ?: provider,
            onConfirm = {
                onUnbindExternalAccount(provider)
                unbindingExternalProvider = null
            },
            onCancel = { unbindingExternalProvider = null },
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
private fun UnbindExternalAccountDialog(
    platformName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onCancel,
        text = { Text(stringResource(Lang.settings_account_profile_unbind_confirmation, platformName)) },
        confirmButton = {
            TextButton(onConfirm, Modifier.testTag("unbindExternalAccountConfirm")) {
                Text(stringResource(Lang.settings_account_profile_unbind), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text(stringResource(Lang.subject_collection_cancel))
            }
        },
    )
}

/**
 * @param canUnbind 为 `false` 时邮箱是唯一登录方式, 只提示不能解绑
 */
@Composable
private fun UnbindEmailDialog(
    email: String,
    canUnbind: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!canUnbind) {
        AlertDialog(
            onCancel,
            text = {
                Text(
                    stringResource(Lang.settings_account_profile_unbind_email_last_login_method),
                    Modifier.testTag("unbindEmailLastLoginMethod"),
                )
            },
            confirmButton = {
                TextButton(onCancel) {
                    Text(stringResource(Lang.settings_account_profile_ok))
                }
            },
        )
        return
    }
    AlertDialog(
        onCancel,
        text = { Text(stringResource(Lang.settings_account_profile_unbind_email_confirmation, email)) },
        confirmButton = {
            TextButton(onConfirm, Modifier.testTag("unbindEmailConfirm")) {
                Text(stringResource(Lang.settings_account_profile_unbind), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text(stringResource(Lang.subject_collection_cancel))
            }
        },
    )
}

@Composable
private fun UnbindBangumiDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    AlertDialog(
        onCancel,
        // icon omitted to reduce dependency on specific icon packs
        text = { Text(stringResource(Lang.settings_account_profile_unbind_bangumi_confirmation)) },
        confirmButton = {
            TextButton(onConfirm, enabled = confirmEnabled) {
                Text(stringResource(Lang.settings_account_profile_unbind), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onCancel) {
                Text(stringResource(Lang.subject_collection_cancel))
            }
        },
    )
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
    val doneText = stringResource(Lang.settings_account_profile_done)
    val uploadAvatarText = stringResource(Lang.settings_account_profile_upload_avatar)
    val selectFileText = stringResource(Lang.settings_account_profile_select_file)
    val selectFileDescriptionText = stringResource(Lang.settings_account_profile_select_file_description)
    val selectFileDescriptionDesktopText =
        stringResource(Lang.settings_account_profile_select_file_description_desktop)
    val filePicker = rememberFilePickerLauncher(
        type = FileKitType.Image,
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
                Text(doneText)
            }
        },
        title = {
            Text(uploadAvatarText)
        },
        text = {
            Column(modifier) {
                val selectFileDescription =
                    if (currentPlatform() is Platform.Desktop) selectFileDescriptionDesktopText else selectFileDescriptionText
                TextItem(
                    title = { Text(selectFileText) },
                    description = { Text(selectFileDescription) },
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

@Composable
private fun renderAvatarUploadMessage(
    state: EditProfileState.UploadAvatarState,
): String {
    return when (state) {
        is EditProfileState.UploadAvatarState.Uploading -> stringResource(Lang.settings_account_profile_uploading_avatar)
        is EditProfileState.UploadAvatarState.SizeExceeded -> stringResource(Lang.settings_account_profile_avatar_size_exceeded)
        is EditProfileState.UploadAvatarState.InvalidFormat -> stringResource(Lang.settings_account_profile_avatar_invalid_format)
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
                Text(stringResource(Lang.settings_account_profile_crop_and_upload))
            }
        },
        dismissButton = {
            TextButton(onDismissRequest) { Text(stringResource(Lang.subject_collection_cancel)) }
        },
        title = { Text(stringResource(Lang.settings_account_profile_crop_avatar)) },
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

                Text(stringResource(Lang.settings_account_profile_crop_hint), Modifier.padding(top = 8.dp))
            }
        },
    )
}
