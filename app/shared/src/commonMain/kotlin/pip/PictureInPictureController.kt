/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.pip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.data.models.preference.BackgroundBehavior
import org.openani.mediamp.MediampPlayer

/**
 * 平台画中画 (Picture-in-Picture) 能力抽象.
 *
 * PiP 是窗口/页面级关切而非播放器内核关切: Android 的小窗展示整个 Activity, iOS 的小窗由 AVKit 托管
 * 视频 layer. 页面层通过 [updatePolicy] 声明"当前允许自动进入小窗", 由平台 actual 翻译为系统 API.
 *
 * 使用 [LocalPictureInPictureController] 获取当前控制器; 默认值为 [NoOpPictureInPictureController]
 * (不支持的平台或播放器).
 * 
 */
@Stable
interface PictureInPictureController {
    /**
     * 当前平台与播放器是否支持画中画.
     */
    val isSupported: Boolean

    /**
     * 应用当前是否处于小窗模式. 驱动 UI 最小化与弹幕冻结.
     */
    val isInPictureInPicture: StateFlow<Boolean>

    /**
     * 现在是否可以进入小窗 (例如 iOS 需要视频 layer 已就绪). 驱动控制栏按钮的 enabled 状态.
     */
    val isPictureInPicturePossible: StateFlow<Boolean>

    /**
     * 更新小窗策略. 由播放页面在以下时机调用:
     * - 页面可见性/设置/播放状态/视频宽高比变化时
     * - 页面退出时 (传 `autoEnterEnabled = false`, 宽高比传 null)
     *
     * @param autoEnterEnabled 用户上滑回桌面时是否自动进入小窗 (暂停状态等场景应为 false)
     * @param aspectWidth 视频画面宽度 (像素宽高比已折算), null 表示未知, 使用系统默认比例
     * @param aspectHeight 视频画面高度, null 表示未知
     */
    fun updatePolicy(autoEnterEnabled: Boolean, aspectWidth: Int?, aspectHeight: Int?)

    /**
     * 立即进入小窗 (控制栏手动入口). 仅在 [isPictureInPicturePossible] 为 true 时调用.
     */
    fun enterPictureInPicture()

    /**
     * 退出小窗, 让小窗内容还原到应用内原播放位置 (同一播放器, 不中断播放).
     *
     * 默认不做事: Android 没有以编程方式退出小窗的公开 API (用户点击小窗即可还原);
     * iOS 在用户回到应用时系统不会自动收起小窗, 需要页面在此时调用本方法.
     */
    fun exitPictureInPicture() {
    }
}

/**
 * 不支持 PiP 的 no-op 实现, 也是 [LocalPictureInPictureController] 的默认值.
 */
object NoOpPictureInPictureController : PictureInPictureController {
    override val isSupported: Boolean get() = false
    override val isInPictureInPicture: StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)
    override val isPictureInPicturePossible: StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun updatePolicy(autoEnterEnabled: Boolean, aspectWidth: Int?, aspectHeight: Int?) {
    }

    override fun enterPictureInPicture() {
    }
}

/**
 * 当前作用域的画中画控制器. 由播放页面 (EpisodePage) 提供.
 */
val LocalPictureInPictureController =
    staticCompositionLocalOf<PictureInPictureController> { NoOpPictureInPictureController }

@Composable
expect fun rememberPictureInPictureController(player: MediampPlayer): PictureInPictureController

/**
 * PiP 窗口宽高比的合法上下界 (Android 系统要求 2.39:1 .. 1:2.39).
 */
private const val MAX_ASPECT_NUMERATOR = 239
private const val MAX_ASPECT_DENOMINATOR = 100

/**
 * 计算进入小窗时应声明的视频宽高比.
 *
 * - 输入非法 (null, <= 0) 时返回 null, 表示不设置宽高比, 交给系统默认 (16:9)
 * - 超出系统合法范围时按长边缩放为 239:100 或 100:239, 保持方向
 * - 合法范围内约分为最简整数比, 使相同比例的不同分辨率得到相同结果 (去抖签名稳定)
 *
 * 结果可直接用于 Android [android.util.Rational] 构造.
 */
fun computePipAspectRatio(width: Int?, height: Int?): Pair<Int, Int>? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    return when {
        width * MAX_ASPECT_DENOMINATOR > height * MAX_ASPECT_NUMERATOR ->
            MAX_ASPECT_NUMERATOR to MAX_ASPECT_DENOMINATOR

        height * MAX_ASPECT_DENOMINATOR > width * MAX_ASPECT_NUMERATOR ->
            MAX_ASPECT_DENOMINATOR to MAX_ASPECT_NUMERATOR

        else -> {
            // 合法范围内仍需约分为最简整数比 (与 android.util.Rational 的归约行为一致)
            val gcd = gcd(width, height)
            (width / gcd) to (height / gcd)
        }
    }
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/**
 * 用户上滑回桌面时是否应自动进入小窗.
 *
 * 仅当设置允许且正在播放时才自动进入; 暂停状态上滑应正常退后台 (由 [BackgroundBehavior] 的其他分支处理).
 */
fun shouldAutoEnterPictureInPicture(
    behavior: BackgroundBehavior,
    playWhenReady: Boolean,
): Boolean = behavior == BackgroundBehavior.AUTO_PICTURE_IN_PICTURE && playWhenReady
