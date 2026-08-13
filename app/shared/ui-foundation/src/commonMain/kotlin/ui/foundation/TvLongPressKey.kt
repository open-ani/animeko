/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 长按判定阈值: 第几次 KeyDown 算长按.
 *
 * 系统在按住期间连发 KeyDown —— 第 1 次是按下本身, 第 2 次是首个连发 (约在按住 400~500ms 后),
 * 之后约 50ms 一发. 取 2 即"首个连发就算长按", 与手机端 500ms 的长按门槛大致同档.
 *
 * 判据固定为 `downCount >= 此值`. 曾经有两处写成 `> 阈值` (于是晚一发 ~50ms 才触发), 而那几个
 * 常量名 (`..._REPEATS`) 看不出"达到"与"超过"的区别, 换个常量就静默改了手感 —— 统一到本常量后
 * 不再有这种隐形分叉.
 */
const val LONG_PRESS_KEY_DOWN_COUNT = 2

/**
 * 长按判定的第二个条件: 距**按下**至少这么久.
 *
 * 平时是废条件 —— 首个连发本身就在 400~500ms 之后, 到那时早就够了 (Android 真机上触发时机与
 * 只看 [LONG_PRESS_KEY_DOWN_COUNT] 时一致). 它保证"按住多久算长按"不被连发节奏的平台差异左右,
 * 也给带 `readyToFire` 闸门的调用方一个确定的下限 (闸门只推迟触发, 不重新计时).
 *
 * 略小于系统首个连发的间隔: 免得在连发首发偏早的设备上把正常长按推迟到第二发.
 */
private val LONG_PRESS_MIN_HOLD = 350.milliseconds

/** 确认键: 遥控器 OK / 键盘回车. */
val TV_CONFIRM_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** 播放键. 不含 [Key.MediaPause] —— 它语义单一 (只暂停), 不该被长按改写. */
val TV_PLAY_KEYS = setOf(Key.MediaPlayPause, Key.MediaPlay)

/**
 * 本次 KeyDown 是否为系统按住连发: true = 连发, false = 新的一次按下, null = 平台无从判别.
 * (Android 看 `repeatCount > 0`; 桌面 AWT 与 iOS 拿不到该信息, 恒 null.)
 *
 * 这是区分"用户在本节点上按下了键"与"按住途中焦点/窗口切到了本节点"的唯一凭据: 手势中途
 * 换目标时, 新目标收到的第一发就已经是连发. 原生控件靠它免疫残余手势 (见 [tvLongPressKey]).
 *
 * null 的平台退化为"第一发当新按下": 没有残余手势免疫, 但长按计数照常工作
 * (焦点驱动的 TV UI 只在 Android 上启用, 残余手势也只在多窗口切换时才存在).
 */
internal expect val KeyEvent.isAutoRepeat: Boolean?

/**
 * [Modifier.tvLongPressKey] 的按住进度. 只在需要画按压反馈时才建, 否则由 modifier 内部自持.
 */
@Stable
class TvLongPressKeyState {
    /** 本次按住已收到的 KeyDown 次数; 抬起或失焦即归零. */
    var downCount: Int by mutableIntStateOf(0)
        internal set

    /** 本次按住已经触发过长按 (于是抬起时不再派发短按). */
    var longPressFired: Boolean by mutableStateOf(false)
        internal set

    /** 手势由本节点起手 (见过非连发的 KeyDown). 别处起手的残余手势不计数也不派发. */
    internal var tracking: Boolean = false

    /** 本次按住的起算时刻 (按下那一刻); 见 LONG_PRESS_MIN_HOLD. */
    internal var startMark: TimeMark? = null

    /**
     * 按下那一刻 `readyToFire` 闸门就关着 (目标还在赶路).
     * 这类按住不报 [pressing] —— 按压反馈只留给"停下来按住"那种: 目标还在赶路时按下, 缩放动画
     * 无论怎么排都跟不上位置 (真机: 边移动边长按, 框与卡对不上 / 动画像从一半开始), 不如不做.
     * 触发时机不受影响 (照常从按下计时, 到位即触发).
     */
    internal var deferredStart: Boolean = false

    /**
     * 按住途中但还没到阈值 —— "按下即缩小、到阈值弹回"那类反馈读它: 弹回来的那一刻长按正好触发.
     */
    val pressing: Boolean get() = !deferredStart && downCount in 1 until LONG_PRESS_KEY_DOWN_COUNT

    /** 起算: 计数从 1 开始, 时长从此刻 (按下) 开始. */
    internal fun start() {
        downCount = 1
        startMark = TimeSource.Monotonic.markNow()
    }

    internal fun reset() {
        downCount = 0
        longPressFired = false
        tracking = false
        startMark = null
        deferredStart = false
    }
}

@Composable
fun rememberTvLongPressKeyState(): TvLongPressKeyState = remember { TvLongPressKeyState() }

/**
 * 遥控器长按某个键: 数 KeyDown 连发次数, 到 [LONG_PRESS_KEY_DOWN_COUNT] **当场**触发
 * [onLongPress] (不等松手); 抬起时若长按没触发过就算短按, 派发 [onShortPress].
 *
 * 四条不变量 —— 原先六处各写一遍, 差异全出在这里:
 * - **认领键的 KeyDown 与 KeyUp 一律消费.** KeyDown 不能漏: 短按要等 KeyUp 才能确定, 漏下去
 *   底层会当场把它当成"按了一下". KeyUp 更不能漏: `clickable` / `Surface(onClick)` 自己就在
 *   KeyUp 上触发 onClick, 短按会双触发. 所以短按只能由本 modifier 手动派发 ([onShortPress]).
 * - **只认从本节点起手的手势.** 判据与原生控件同一条: `View.onKeyDown` 只在 `repeatCount == 0`
 *   时置按下态, `onKeyUp` 只在按下态才派发点击 —— 所以系统桌面长按图标弹出菜单后, 残余连发落
 *   在菜单项上只会聚焦, 不会点中. 这里同理: 没起手 (没见过非连发 KeyDown) 就收到的连发与
 *   KeyUp 一律吞掉不计数不派发 —— 那是别处的长按把焦点送过来时还没松开的那次按住. 靠定时
 *   兜底是错的: 用户按多久是他的自由, 超时一到保护就漏.
 * - **按住到阈值立即触发**, 而不是松手才判. 遥控器上的心理模型是"按住到东西出现就可以松手";
 *   松手才触发的话, 按压反馈弹回来就成了"松手才会生效"的假承诺 —— 而它并没有放弃路径,
 *   松手照样触发.
 * - **失焦即复位.** 按住期间焦点被别处抢走 (长按动作自己换了焦点、弹窗、列表重排) 时, 复位加
 *   上"只认起手手势"保证残局安全: 同一次按住的后续连发与 KeyUp 因为不再处于起手状态而被吞掉,
 *   绝不会把那记 KeyUp 误判成短按 (曾经的实际事故: 长按播放键触发刷新 → 列表重建把焦点抖掉
 *   → 复位 → 松手那记 KeyUp 被当成短按, 后台开进了播放器).
 *
 * 挂在**已经拥有该键的那个节点**上并接管它: 与 `clickable` / `Surface(onClick)` 同一条链,
 * 放在它们**之前**. 没有长按动作时整个别挂 —— 确认键交回 `clickable` 原生处理即可.
 *
 * 长按弹出的**弹层内部**要自己吞掉这一次按住的余波 —— 弹层里是普通 clickable, 没有上面第二条
 * 的手势判据, 见 [Modifier.consumeHeldConfirmKey].
 *
 * 播放器的长按倍速不走本实现: 它要的是"持续状态 + 松手还原"而不是一次性触发, 且要监视界面层级
 * 来兜"KeyUp 压根不送达"的情况 (见 `TvEpisodeScreen`).
 *
 * @param state 按住进度; 只在需要画按压反馈时传, 否则内部自建
 * @param keys 认领哪些键; 默认确认键, 播放键传 [TV_PLAY_KEYS]
 * @param readyToFire 触发闸门 (见下)
 */
fun Modifier.tvLongPressKey(
    onLongPress: () -> Unit,
    onShortPress: () -> Unit,
    state: TvLongPressKeyState? = null,
    keys: Set<Key> = TV_CONFIRM_KEYS,
    /**
     * 触发闸门: 返回 false 时**不触发**长按, 每一发连发重新问一次, 转真且按住够久即触发.
     * **计时不受它影响** —— 长按时长一律从按下那一刻算. 闸门只是"还没到位, 先不触发", 不是
     * "重新开始计时": 后者会让"边移动边长按"明显要按更久, 与平时手感不一致 (真机反馈过).
     * 于是闸门开启早于阈值时触发时机与平时完全相同, 晚时就在到位那一发触发. 短按不受影响.
     *
     * 用于"目标还在赶路, 先别触发"的场景: 选集轮播边按方向键边按住确认键时, 卡片还在滑向固定
     * 聚焦框, 此刻触发的话按压反馈画在半路上的卡片上、与钉住的框错开一段 (真机: "会对不上").
     * 传 `{ !listState.isScrollInProgress }` 即"卡片停进框里才算到位".
     *
     * 按下那一刻闸门就关着的那次按住**不出按压反馈** (见 [TvLongPressKeyState.deferredStart]):
     * 目标还在赶路时按下, 缩放动画无论怎么排都跟不上位置, 不如不做; 停下来按住的照常有.
     */
    readyToFire: (() -> Boolean)? = null,
): Modifier = composed {
    val resolved = state ?: remember { TvLongPressKeyState() }
    // hasFocus 而不是 isFocused: 本 modifier 可能挂在容器上 (真正持焦的是子树里的可点击节点),
    // 也可能挂在页面根上 (播放键那种整页共用一份的情形) —— hasFocus 两种都覆盖
    onFocusChanged { if (!it.hasFocus) resolved.reset() }
        .onPreviewKeyEvent { event ->
            if (event.key !in keys) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    val repeat = event.isAutoRepeat
                    val ready = readyToFire == null || readyToFire()
                    if (repeat == false || (repeat == null && !resolved.tracking)) {
                        // 确定的新按下 (或无从判别的平台上还没起手): 从本节点起手.
                        // 无条件重来而不是接着计数 —— 上一次按住的 KeyUp 可能被弹层窗口吃掉,
                        // 本节点还留着 tracking/fired 的脏状态, 不清掉的话这一按会哑掉
                        resolved.reset()
                        resolved.tracking = true
                        // 计数与计时一律从按下算 (闸门只管触发, 见 readyToFire)
                        resolved.start()
                        // 按下时闸门就关着 = 目标还在赶路: 这次按住不出按压反馈 (见 deferredStart)
                        resolved.deferredStart = !ready
                    } else if (resolved.tracking) {
                        // 起手之后的连发: 计数. (无从判别的平台走的也是这条 —— 同一个键
                        // 抬起之前不会再"按下", 起手后的 KeyDown 只能是连发)
                        resolved.downCount++
                        val held = resolved.startMark?.elapsedNow() ?: Duration.ZERO
                        if (!resolved.longPressFired &&
                            resolved.downCount >= LONG_PRESS_KEY_DOWN_COUNT &&
                            held >= LONG_PRESS_MIN_HOLD &&
                            ready // 还没到位: 先不触发, 下一发 (~50ms) 再问
                        ) {
                            resolved.longPressFired = true
                            onLongPress()
                        }
                    }
                    // else: 没起手就收到连发 = 别处的长按把焦点送来, 那次按住还没松 —— 吞掉不计数
                }

                KeyEventType.KeyUp -> {
                    val tracked = resolved.tracking
                    val fired = resolved.longPressFired
                    resolved.reset()
                    if (tracked && !fired) onShortPress()
                }
            }
            true
        }
}
