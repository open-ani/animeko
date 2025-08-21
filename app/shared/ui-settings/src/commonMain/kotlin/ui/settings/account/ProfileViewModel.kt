/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.runtime.Immutable
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.UploadAvatarResult
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.user.SelfInfoStateProducer
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.utils.coroutines.SingleTaskExecutor
import me.him188.ani.utils.coroutines.flows.FlowRestarter
import me.him188.ani.utils.coroutines.flows.restartable
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * It is used on both [AccountSettingsPopupMedium] and [AccountS].
 */
class ProfileViewModel : AbstractViewModel(), KoinComponent {
    private val subjectCollectionRepo: SubjectCollectionRepository by inject()
    private val userRepo: UserRepository by inject()

    private val selfInfoStateProvider: SelfInfoStateProducer = SelfInfoStateProducer(koin = getKoin())

    private val avatarUploadTasker = SingleTaskExecutor(backgroundScope.coroutineContext)
    private val fullSyncTasker = MonoTasker(backgroundScope)

    private val stateRefresher = FlowRestarter()

    private val avatarUploadState =
        MutableStateFlow<EditProfileState.UploadAvatarState>(EditProfileState.UploadAvatarState.Default)

    val stateFlow = combine(
        selfInfoStateProvider.flow,
        avatarUploadState,
    ) { selfInfoState, avatarState ->
        AccountSettingsState(
            selfInfo = selfInfoState,
            boundBangumi = selfInfoState.isSessionValid == true && selfInfoState.bangumiConnected == true,
            avatarUploadState = avatarState,
        )
    }
        .restartable(stateRefresher)
        .stateInBackground(
            initialValue = AccountSettingsState.Empty,
            started = SharingStarted.WhileSubscribed(5_000),
        )

    suspend fun logout() {
        withContext(Dispatchers.Default) {
            userRepo.clearSelfInfo()
        }
    }

    fun resetAvatarUploadState() {
        avatarUploadState.value = EditProfileState.UploadAvatarState.Default
    }

    suspend fun uploadAvatar(file: PlatformFile): Boolean {
        return avatarUploadTasker.invoke {
            avatarUploadState.value = EditProfileState.UploadAvatarState.Uploading

            val uploadResultState = kotlin.run {
                try {
                    if (file.size() > 1.megaBytes.inBytes) {
                        return@run EditProfileState.UploadAvatarState.SizeExceeded
                    }

                    val imageBytes = withContext(Dispatchers.IO) {
                        file.readBytes()
                    }

                    if (!AvatarImageProcessor.checkImageFormat(imageBytes)) {
                        return@run EditProfileState.UploadAvatarState.InvalidFormat
                    }

                    when (userRepo.uploadAvatar(imageBytes)) {
                        UploadAvatarResult.SUCCESS -> EditProfileState.UploadAvatarState.Success("")
                        UploadAvatarResult.TOO_LARGE -> EditProfileState.UploadAvatarState.SizeExceeded
                        UploadAvatarResult.INVALID_FORMAT -> EditProfileState.UploadAvatarState.InvalidFormat
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    EditProfileState.UploadAvatarState.UnknownError(
                        file = file,
                        loadError = LoadError.fromException(ex),
                    )
                }
            }

            avatarUploadState.value = uploadResultState
            stateRefresher.restart()
            uploadResultState is EditProfileState.UploadAvatarState.Success
        }
    }

    fun validateNickname(nickname: String): Boolean {
        if (nickname.isEmpty()) {
            return true
        }

        if (nickname.isBlank() || !NICKNAME_MATCHER.matches(nickname)) {
            return false
        }

        val length = nickname.foldRight(0) { char, acc ->
            acc + if (char.code < 256) 1 else 2 // ASCII characters count as 1, others count as 2
        }

        return length in 6..20
    }

    suspend fun saveProfile(profile: EditProfileState) {
        val selfInfo = stateFlow.value.selfInfo.selfInfo
        userRepo.updateProfile(
            nickname = profile.nickname.takeIf { it != selfInfo?.nickname },
        )
        stateRefresher.restart()
    }

    suspend fun bangumiFullSync() {
        subjectCollectionRepo.performBangumiFullSync()
    }

    companion object {
        private val NICKNAME_MATCHER = Regex("^[\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FFa-zA-Z\\d_]+$")
    }
}

@Immutable
class AccountSettingsState(
    val selfInfo: SelfInfoUiState,
    val boundBangumi: Boolean,
    val avatarUploadState: EditProfileState.UploadAvatarState,
) {
    companion object {
        val Empty = AccountSettingsState(
            selfInfo = SelfInfoUiState(null, true, null, null),
            boundBangumi = false,
            avatarUploadState = EditProfileState.UploadAvatarState.Default,
        )
    }
}

@Immutable
class EditProfileState(
    val nickname: String,
) {
    companion object {
        val Empty = EditProfileState(
            nickname = "",
        )
    }

    @Immutable
    sealed interface UploadAvatarState {
        data object Default : UploadAvatarState

        data object Uploading : UploadAvatarState

        data class Success(val url: String) : UploadAvatarState

        sealed interface Failed : UploadAvatarState

        data object InvalidFormat : Failed

        data object SizeExceeded : Failed

        data class UnknownError(val file: PlatformFile, val loadError: LoadError) : Failed
    }
}

@OptIn(TestOnly::class)
val TestAccountSettingsState
    get() = AccountSettingsState(
        TestSelfInfoUiState,
        false,
        EditProfileState.UploadAvatarState.Default,
    )

private object AvatarImageProcessor {
    fun checkImageFormat(headBytes: ByteArray): Boolean {
        return isJpeg(headBytes) || isPng(headBytes) || isWebp(headBytes)
    }

    private fun isPng(data: ByteArray): Boolean {
        // PNG signature: 137 80 78 71 13 10 26 10
        return data.size >= 8 && data[0] == 137.toByte() &&
                data[1] == 'P'.code.toByte() &&
                data[2] == 'N'.code.toByte() &&
                data[3] == 'G'.code.toByte() &&
                data[4] == 13.toByte() &&
                data[5] == 10.toByte() &&
                data[6] == 26.toByte() &&
                data[7] == 10.toByte()
    }

    private fun isJpeg(data: ByteArray): Boolean {
        // JPEG signature: 0xFF 0xD8 0xFF
        return data.size >= 3 && data[0] == 0xFF.toByte() &&
                data[1] == 0xD8.toByte() &&
                data[2] == 0xFF.toByte()
    }

    private fun isWebp(data: ByteArray): Boolean {
        // WebP signature: "RIFF" + 4 bytes + "WEBP"
        return data.size >= 12 && data[0] == 'R'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == 'F'.code.toByte() &&
                data[8] == 'W'.code.toByte() &&
                data[9] == 'E'.code.toByte() &&
                data[10] == 'B'.code.toByte() &&
                data[11] == 'P'.code.toByte()
    }
}