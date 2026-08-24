/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

/**
 * 当前平台是否有系统级的 predictive back 手势, 决定页面导航是否使用 [PredictiveBackMotion] 的动效参数.
 *
 * - Android 13 (API 33) 及以上: `true`
 * - iOS: `true`, 系统自带边缘返回手势
 * - Desktop 与 Android 13 以下: `false`, 继续使用 [NavigationMotionScheme] 里旧的滑动 + 淡入淡出动画
 */
expect fun isPlatformSupportPredictiveBack(): Boolean
