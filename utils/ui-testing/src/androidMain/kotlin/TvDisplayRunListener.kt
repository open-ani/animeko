/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.RunListener

/**
 * 在整个 instrumented test 运行期间把设备的显示规格设为 1080p 电视 (1920×1080 @ 320dpi, 即 960×540dp),
 * 运行结束后还原.
 *
 * TV 界面的 UI 测试按电视的显示规格断言布局与焦点, 而测试设备可能是任意尺寸的手机模拟器.
 * 系统最多把显示放大到物理尺寸的 2 倍, 因此设备的物理屏幕至少要有 960×540.
 *
 * 在模块的 `androidDeviceTest/AndroidManifest.xml` 中注册:
 * ```xml
 * <instrumentation ...>
 *     <meta-data android:name="listener" android:value="me.him188.ani.app.ui.framework.TvDisplayRunListener" />
 * </instrumentation>
 * ```
 */
class TvDisplayRunListener : RunListener() {
    override fun testRunStarted(description: Description?) {
        shell("wm size ${WIDTH_PX}x$HEIGHT_PX")
        shell("wm density $DENSITY_DPI")
        // 显示规格异步生效. 等到系统报告新的尺寸, 测试 Activity 才会以电视规格启动.
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
            if (maxOf(metrics.widthPixels, metrics.heightPixels) == WIDTH_PX && metrics.densityDpi == DENSITY_DPI) return
            Thread.sleep(100)
        }
        // 系统最多把显示放大到物理尺寸的 2 倍, 物理屏幕小于 960x540 的设备达不到电视规格.
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        Log.e(
            "TvDisplayRunListener",
            "Display is ${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi instead of " +
                    "${WIDTH_PX}x$HEIGHT_PX @ ${DENSITY_DPI}dpi. TV UI tests need a device whose physical screen " +
                    "is at least ${WIDTH_PX / 2}x${HEIGHT_PX / 2}.",
        )
    }

    override fun testRunFinished(result: Result?) {
        shell("wm size reset")
        shell("wm density reset")
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        // 读完输出即命令执行完毕.
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private companion object {
        const val WIDTH_PX = 1920
        const val HEIGHT_PX = 1080
        const val DENSITY_DPI = 320
    }
}
