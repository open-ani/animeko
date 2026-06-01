/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.activity

import android.content.Context
import android.content.res.Configuration
import me.him188.ani.app.data.models.preference.ThemeSettings
import kotlin.math.roundToInt

/**
 * 界面缩放在 Android 侧的落地: 用 [Context.createConfigurationContext] 改 Activity 的
 * `densityDpi`, 使主窗口与所有弹窗 (各自独立 window) 共用同一个 density.
 *
 * 见 [me.him188.ani.app.ui.foundation.UiScaleApplier] 的说明.
 */

/**
 * 缩放系数的同步镜像.
 *
 * 真值在 DataStore ([ThemeSettings.uiScale]) 里, 但 `attachBaseContext` 需要**同步**拿到它 ——
 * 那时连 Activity 都还没建好, 不能等协程. 所以每次设置变更都往 SharedPreferences 抄一份,
 * 启动时只读这份镜像 (内存缓存, 无感知开销).
 */
internal object UiScaleMirror {
    private const val PREFERENCES_NAME = "ani_ui_scale"
    private const val KEY_UI_SCALE = "uiScale"

    fun read(context: Context): Float {
        val raw = runCatching {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_UI_SCALE, 1f)
        }.getOrDefault(1f)
        return if (raw.isFinite()) {
            raw.coerceIn(ThemeSettings.UI_SCALE_MIN, ThemeSettings.UI_SCALE_MAX)
        } else {
            1f
        }
    }

    /**
     * 同步落盘: 调用方紧接着可能就 `recreate()`, 而重建后的 `attachBaseContext` 必须读到新值.
     * 同进程内 SharedPreferences 的内存缓存是立即可见的, 用 `commit` 只是连跨进程/被杀的情况一起保住.
     */
    fun write(context: Context, scale: Float) {
        runCatching {
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_UI_SCALE, scale)
                .commit()
        }
    }
}

/**
 * 按 [scale] 放大 `densityDpi`, 返回 density 已缩放的 context —— 交给 `super.attachBaseContext`
 * (Activity) 或直接用来建 `ComposeView` (屏保) 都可以.
 *
 * 同时按反比改掉三个 dp 尺寸字段: [Configuration] 里 `densityDpi` 与 `screenWidthDp` 之类是**各自独立**
 * 的字段, [Context.createConfigurationContext] 不会替你重算. 只改 densityDpi 会得到一份自相矛盾的
 * Configuration —— 资源限定符 (`values-w600dp`) 和读 `screenWidthDp` 的代码仍按原尺寸走, 与实际布局对不上.
 *
 * `scale == 1f` 时原样返回 —— 绝大多数设备走这条路, 完全不引入额外的 Resources 实例.
 */
internal fun Context.withUiScale(scale: Float): Context {
    if (scale == 1f) return this
    val scaled = Configuration(resources.configuration).apply {
        densityDpi = (densityDpi * scale).roundToInt()
        // 物理尺寸没变, density 变大 => 可用 dp 变少
        screenWidthDp = (screenWidthDp / scale).roundToInt()
        screenHeightDp = (screenHeightDp / scale).roundToInt()
        smallestScreenWidthDp = (smallestScreenWidthDp / scale).roundToInt()
    }
    return createConfigurationContext(scaled)
}
