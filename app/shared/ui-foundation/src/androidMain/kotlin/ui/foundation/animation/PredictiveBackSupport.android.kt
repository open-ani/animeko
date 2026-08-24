/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.animation

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Predictive back 手势是 Android 13 (API 33) 引入的.
 *
 * Android 15 以下的系统不会因为 `targetSdk` 高就默认打开提前返回派发, 所以 manifest 里显式声明了
 * `android:enableOnBackInvokedCallback="true"`, 否则 13 / 14 上收不到手势进度.
 *
 * 注意 Android 13 还额外受"预测性返回动画"开发者选项控制. 关掉时系统直接回调返回, 不给进度,
 * 此时 `NavDisplay` 走非手势的 pop 动画, 视觉参数仍然是 predictive back 那一套.
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
actual fun isPlatformSupportPredictiveBack(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
