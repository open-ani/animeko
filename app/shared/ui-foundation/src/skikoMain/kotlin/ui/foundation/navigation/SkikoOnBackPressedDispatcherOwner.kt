/*
 * Copyright 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.navigation

import androidx.lifecycle.LifecycleOwner
import me.him188.ani.app.navigation.AniNavigator

class SkikoOnBackPressedDispatcherOwner(
    override val onBackPressedDispatcher: OnBackPressedDispatcher,
    lifecycleOwner: LifecycleOwner,
) : OnBackPressedDispatcherOwner, LifecycleOwner by lifecycleOwner {
    constructor(aniNavigator: AniNavigator, lifecycleOwner: LifecycleOwner) : this(
        popBackStackDispatcher(aniNavigator),
        lifecycleOwner,
    )
}

/**
 * 没有任何启用的 [BackHandler] 时, 返回等价于退出当前页面.
 */
private fun popBackStackDispatcher(aniNavigator: AniNavigator): OnBackPressedDispatcher =
    OnBackPressedDispatcher(fallback = { aniNavigator.popBackStack() })
