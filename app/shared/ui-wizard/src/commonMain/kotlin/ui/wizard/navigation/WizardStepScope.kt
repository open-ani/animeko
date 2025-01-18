/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.runtime.Stable

/**
 * 在使用 [WizardBuilder.step] 构建的时候使用, 为 UI 提供数据获取和更新的能力
 *
 * 让向导步骤界面可以读取配置和设置新的配置, 显示跳过状态
 *
 * @param data 这个步骤的 UI 数据
 * @param requestedSkip 是否请求跳过此步骤.
 * 若为 true, 可能需要显示一个对话框来让用户选择是否跳过
 * @param onUpdate 步骤的 UI 数据更新时调用. 
 */
@Stable
class WizardStepScope<T>(
    val data: T,
    val requestedSkip: Boolean,
    private val onUpdate: (T) -> Unit,
    private val onConfirmSkip: (Boolean) -> Unit,
) {

    /**
     * 更新 UI 数据, 会导致 [data] 变更为新的数据
     */
    fun update(block: T.() -> T) {
        onUpdate(data.block())
    }

    /**
     * 确认是否跳过此步骤
     */
    fun confirmSkip(skip: Boolean) {
        onConfirmSkip(skip)
    }
}