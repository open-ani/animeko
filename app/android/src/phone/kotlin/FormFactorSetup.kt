/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.foundation.AniUiBehavior

/*
 * 形态适配接缝 (phone 变体): 每个 formFactor flavor 各提供一份同名实现,
 * MainActivity 只调用它, 因此不必知道自己跑在什么设备上. 见 src/tv 下的对应文件.
 */

/** 指针设备 (触屏/鼠标) 的界面行为. */
internal val formFactorUiBehavior: AniUiBehavior get() = AniUiBehavior.Default

/** 无需安装任何界面变体, 原样组合. */
@Composable
internal fun InstallFormFactorUi(content: @Composable () -> Unit) = content()

/** 无需额外初始化. */
internal fun onFormFactorActivityCreated(activity: ComponentActivity) {
    // no-op
}
