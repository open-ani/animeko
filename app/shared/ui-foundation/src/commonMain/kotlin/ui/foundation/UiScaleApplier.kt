/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 把界面缩放落到**窗口层**的平台能力.
 *
 * 为什么需要它: 界面缩放只靠 Compose 的 `LocalDensity` 是盖不全的. Android 上每个弹窗
 * (Dialog / Popup / DropdownMenu / BottomSheet) 都是独立 window, 各有自己的 `AndroidComposeView`,
 * 而 Compose 在每个 window 的 composition 根部会执行 `LocalDensity provides owner.density` ——
 * `owner.density` 读的是 `view.context.resources`, 于是主窗口里提供的缩放跨不过 window 边界,
 * 弹窗一律被打回系统 density. 全应用有上百处弹窗调用点, 逐个包 provider 必然漏.
 *
 * 唯一不漏的做法是改所有 window 的共同来源 —— Activity 的 `Configuration.densityDpi`
 * (等价于只作用于本应用的 `adb shell wm density`). 代价是它只能在 Activity 创建时确定,
 * 改动需要重建 Activity, 所以拆成两层:
 *
 * - **基线**: [appliedScale], 即当前 Activity 创建时落到窗口层的值. 主窗口与所有弹窗都按它渲染.
 * - **预览**: 用户在设置里调整时, 主窗口由 `AniApp` 的 `LocalDensity` 即时跟随新值 (弹窗仍是基线),
 *   使用户能边调边看; 离开设置页时再调用 [apply] 把基线对齐, 此后弹窗也一致.
 */
@Stable
interface UiScaleApplier {
    /**
     * 窗口层当前已应用的缩放系数. 不支持的平台恒为 `1f`.
     *
     * `AniApp` 用它把「设置值」换算成需要在 Compose 层额外补的比例, 避免与窗口层的缩放叠乘.
     */
    val appliedScale: Float

    /**
     * 把 [scale] 落到窗口层, 使弹窗等新 window 也跟随.
     *
     * Android 上会重建 Activity, 因此只应在切换页面这类本就要重新布局的时机调用.
     * 与 [appliedScale] 相同时为空操作 —— 调用方不必自己判断.
     */
    fun apply(scale: Float)
}

/**
 * 不支持窗口层缩放的平台 (桌面 / iOS) 的实现: 基线恒为 `1f`, [apply][UiScaleApplier.apply] 无操作.
 * 这些平台上界面缩放退化为「只有 Compose 层生效」, 而它们并不暴露该设置项, 所以恒为空转.
 */
object NoopUiScaleApplier : UiScaleApplier {
    override val appliedScale: Float get() = 1f
    override fun apply(scale: Float) {}
}

val LocalUiScaleApplier = staticCompositionLocalOf<UiScaleApplier> { NoopUiScaleApplier }
