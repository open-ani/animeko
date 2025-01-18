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
 * 在使用 [WizardBuilder.step] 构建的时候使用
 *
 * 让向导步骤界面可以读取配置和设置新的配置, 显示跳过状态
 */
@Stable
class WizardConfigState<T>(
    val data: T,
    val requestedSkip: Boolean,
    private val onUpdate: (T) -> Unit,
    private val onConfirmSkip: (Boolean) -> Unit,
) {

    fun update(block: T.() -> T) {
        onUpdate(data.block())
    }

    fun confirmSkip(skip: Boolean) {
        onConfirmSkip(skip)
    }
}