/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.utils.io.SystemPath
import kotlin.coroutines.cancellation.CancellationException

/**
 * 安装包安装器
 *
 * - 安卓：弹出系统 APK 安装界面
 * - Windows：使用脚本自动覆盖安装 （一键）
 * - macOS：打开 dmg 让系统去安装，需要用户手动拖拽一下
 */
interface UpdateInstaller {
    /**
     * 返回安装前需要下载的文件地址.
     *
     * 默认直接使用版本 API 返回的安装包地址. Linux AppImage 将其转换为较小的 zsync 元数据地址,
     * 实际差分数据由外部更新器在安装阶段下载.
     */
    fun getUpdatePreparationUrls(packageUrls: List<String>): List<String> = packageUrls

    /**
     * 如果 [install] 可能返回 [InstallationResult.Failed], 则需实现
     */
    suspend fun openForManualInstallation(file: SystemPath, context: ContextMP): Boolean = false

    fun install(file: SystemPath, context: ContextMP): InstallationResult

    /**
     * 使用版本 API 返回的原始安装包地址执行安装.
     *
     * 默认平台仍安装已经下载到 [file] 的完整安装包.
     */
    suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
    ): InstallationResult = install(file, context)
}

sealed class UpdateInstallationState {
    data object Idle : UpdateInstallationState()
    data object Installing : UpdateInstallationState()
    data object Succeed : UpdateInstallationState()
    data class Failed(val result: InstallationResult.Failed) : UpdateInstallationState()
    data class Cancelled(val cause: CancellationException) : UpdateInstallationState()
}

class UpdateInstallationRunner(
    private val installer: UpdateInstaller,
) {
    private val _state = MutableStateFlow<UpdateInstallationState>(UpdateInstallationState.Idle)
    val state: StateFlow<UpdateInstallationState> = _state.asStateFlow()

    suspend fun install(
        file: SystemPath,
        packageUrls: List<String>,
        context: ContextMP,
    ) {
        _state.value = UpdateInstallationState.Installing
        try {
            _state.value = when (val result = installer.install(file, packageUrls, context)) {
                InstallationResult.Succeed -> UpdateInstallationState.Succeed
                is InstallationResult.Failed -> UpdateInstallationState.Failed(result)
            }
        } catch (e: CancellationException) {
            _state.value = UpdateInstallationState.Cancelled(e)
            throw e
        } catch (e: Throwable) {
            _state.value = UpdateInstallationState.Failed(
                InstallationResult.Failed(
                    InstallationFailureReason.FAILED_TO_COPY,
                    e.message,
                ),
            )
        }
    }

    fun dismissFailure() {
        _state.update { state ->
            if (state is UpdateInstallationState.Failed) UpdateInstallationState.Idle else state
        }
    }
}

sealed class InstallationResult {
    data object Succeed : InstallationResult() // 实际上可能不会返回, 因为安装成功会重启

    /**
     * 安装失败, 附带失败原因. UI 会展示这个失败原因
     */
    data class Failed(
        val reason: InstallationFailureReason,
        val message: String? = null,
    ) : InstallationResult()
}

enum class InstallationFailureReason {
    /**
     * 未支持的安装目录结构. 例如 Windows 上未找到 `Ani.exe`
     */
    UNSUPPORTED_FILE_STRUCTURE,

    FAILED_TO_MOUNT_DMG,
    FAILED_TO_COPY,
}
