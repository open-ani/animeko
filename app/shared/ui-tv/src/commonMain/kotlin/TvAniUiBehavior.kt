/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.tv

import me.him188.ani.app.ui.foundation.AniUiBehavior

/**
 * 遥控器设备 (Android TV / Google TV) 的界面行为.
 *
 * 由 TV 应用入口传给 `AniApp`. 共享界面代码不认识它, 只读 `LocalAniUiBehavior` 里的开关.
 */
val TvAniUiBehavior = AniUiBehavior(
    focusDrivenNavigation = true,
    showBackNavigationButton = false,
    showDismissButtons = false,
    showNavigationTopAppBar = false,
    pinTopAppBar = true,
    sheetMaxWidthFraction = 0.9f,
    immersiveShell = true,
    supportsWindowedPlayback = false,
    crossfadeNavigation = true,
    blackRootBackground = true,
    panelsAsCenteredDialogs = true,
    autoInstallUpdates = true,
    forceDarkInPlayer = true,
)
