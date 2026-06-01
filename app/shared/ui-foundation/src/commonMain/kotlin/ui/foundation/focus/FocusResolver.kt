/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.focus

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * 焦点落点轮询解析器: 每帧执行一次 [attempt] (通常是 requestFocus, 可附带滚动等
 * 前置动作), 以 [arrived] 确认真正到位, 到位或试满 [attempts] 次即结束.
 *
 * 为什么必须轮询 + 到位确认 (全库统一经验, 勿用单次 requestFocus 或其返回值):
 * - 目标节点未组合/未附着时 requestFocus 被焦点系统**静默拒绝**, 不抛异常,
 *   `runCatching { requestFocus() }.isSuccess` 恒真, 是假成功;
 * - 页面切换/弹窗开合期间其它异步焦点分配可能后到抢焦点, 单次请求会被覆盖,
 *   需要多帧断言直到 [arrived] (由目标的 onFocusChanged 置位) 确认.
 *
 * 关于抢焦点 (务必给出 [abandon]): 目标始终不到位时本循环会烧满 [attempts] 次, 期间**每次都发
 * 一遍 requestFocus**. 用户此刻按遥控器移动焦点, 下一次尝试就把焦点抢回去 —— 表现为偶发的
 * "焦点自己跳回来". [arrived] 的"先查后试"只能挡住已到位的情况, 挡不住"永远不到位". 因此凡是
 * 能观察到焦点落在别处的调用点, 都应传 [abandon] 让解析主动退出, 不与用户竞争.
 *
 * @param attempts 重试次数上限. 默认 40 次 (~1.2s), 覆盖绝大多数组合/动画时序;
 *   数据加载等更慢的场景酌情调大 —— 但窗口越长, 没有 [abandon] 时抢焦点的概率越高.
 * @param delayMillis 每次尝试间的补充等待 (帧间隔之外); 0 = 只按帧重试
 *   (适合目标已组合、只等焦点系统就绪的场景).
 * @param arrived 到位判据, 通常读一个由目标 onFocusChanged 置位的标志.
 * @param abandon 放弃判据: 观察到焦点已落在"既不是起点也不是目标"的地方 (即用户自己移开了)
 *   时返回 true. 起点要在调用前快照 —— 解析开始那一刻焦点通常正停在要离开的那个元素上,
 *   不能算介入. 默认不放弃 (仅适用于模态弹窗初始聚焦这类用户无处可去的场景).
 * @param attempt 每次尝试的动作; requestFocus 需调用方自行 runCatching
 *   (节点未附着时抛 IllegalStateException).
 * @return 是否在限次内到位.
 */
suspend fun resolveFocusRepeatedly(
    attempts: Int = 40,
    delayMillis: Long = 30,
    arrived: () -> Boolean,
    abandon: () -> Boolean = { false },
    attempt: suspend () -> Unit,
): Boolean {
    repeat(attempts) {
        withFrameNanos { }
        // 先查后试: 已到位 (或已放弃) 时不再多发一次 requestFocus ——
        // 用户可能已把焦点移走, 多发的这次会把焦点抢回来一格
        if (arrived()) return true
        if (abandon()) return false
        attempt()
        if (arrived()) return true
        if (delayMillis > 0) delay(delayMillis)
    }
    return false
}
