/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull

/*
 * TV 统一焦点管理框架 (使用侧 API 见 TvFocusModifiers.kt).
 *
 * 概念:
 * - [TvFocusKey]: 页面内一个具名焦点位置 (锚点). 页面用 enum 实现或 [TvFocusKey] 工厂创建.
 * - [TvFocusScope]: 页面级调度器. 所有"把焦点送到某处"的入口 (进页初始焦点 / 返回键分层 /
 *   弹层关闭归还 / 全局快捷键) 都走 [TvFocusScope.request], 由 [Resolver] 消化.
 *
 * **全事件驱动, 禁止轮询/延时** (§14.4-8): Compose 对未附着节点的 requestFocus 静默失败
 * 且返回值不可靠, 早期版本用"轮询 + 到位确认 + 延时"兜时序, 在慢设备上暴露出整族竞态
 * (烧满轮询抢用户焦点 / 时序窗口内按键误伤). 现在把缺失的"附着"事件补上:
 * 锚点 modifier 在节点附着/脱离时上报 ([onAnchorAttached]/[onAnchorDetached]),
 * [Resolver] 以快照流响应"pending 请求 && 目标已附着" —— 目标已附着则立即送焦,
 * 未附着 (Lazy 回收/转场中) 则请求悬挂, 目标一附着即送. 没有帧等待, 没有超时.
 *
 * 失败语义: 单发不抢 —— 送焦后若被后到的分配抢走, 框架不抢回 (与"不与用户抢焦点"
 * 一致); 用户按下方向/确认键即取消在途请求 ([notifyUserNavigation]).
 */

/** 页面内一个具名焦点位置. 页面私有 enum 直接实现本接口, 或用 [TvFocusKey] 工厂. */
interface TvFocusKey

/** 字符串命名的 [TvFocusKey] (不想定义 enum 的轻量场景). */
fun TvFocusKey(name: String): TvFocusKey = NamedFocusKey(name)

private data class NamedFocusKey(val name: String) : TvFocusKey {
    override fun toString(): String = name
}

/**
 * 页面级焦点调度器. 经 [rememberTvFocusScope] 创建; 页面根部须装 [Resolver] 消化请求.
 *
 * 线程模型: 全部在主线程 (组合/效应/按键分发) 使用.
 */
@Stable
class TvFocusScope {
    private val requesters = mutableMapOf<TvFocusKey, FocusRequester>()

    /** 当前待解析的请求 (锚点 + 序号; 序号使同锚点连续请求也能重新触发); null = 空闲. */
    internal var pending: Pair<TvFocusKey, Int>? by mutableStateOf(null)
        private set

    /**
     * 已附着锚点 -> 附着代数 (快照状态: [Resolver] 靠它感知"目标出现了").
     * 由锚点 modifier 的节点附着/脱离事件维护 (见 tvFocusAnchor).
     */
    private val attachedAnchors = mutableStateMapOf<TvFocusKey, Int>()

    // 当前聚焦的锚点集合 (各锚点 onFocusChanged 得失双向上报, 见 tvFocusAnchor)
    private val focusedKeys = mutableSetOf<TvFocusKey>()

    /**
     * 用户交互代数 (快照状态; [tvFocusNavSignal] 上报): 方向/确认键按下即自增并取消
     * 在途请求 —— 框架不与用户抢焦点. 需要交互取消语义的效应 (如网格切换冻结) 观察它.
     */
    var userNavGeneration: Int by mutableIntStateOf(0)
        private set

    /** [key] 的 FocusRequester (惰性创建). 框架内部互操作用; 页面侧一律走锚点 + [request]. */
    fun requesterOf(key: TvFocusKey): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * 请求把焦点送到 [key] (fire-and-forget): 锚点已附着则 [Resolver] 立即送焦;
     * 未附着 (Lazy 回收/转场中) 则悬挂, 锚点一附着即送. 同 key 连续请求也会重新触发.
     * 后发请求覆盖先发; 用户交互取消在途请求.
     */
    fun request(key: TvFocusKey) {
        pending = key to ((pending?.second ?: 0) + 1)
    }

    /** 用户按下方向/确认键的上报 (由 [tvFocusNavSignal]/[tvFocusHotkey] 自动挂接): 取消在途请求. */
    fun notifyUserNavigation() {
        userNavGeneration++
        pending = null
    }

    /** 锚点节点附着上报 (由 tvFocusAnchor 自动挂接). */
    fun onAnchorAttached(key: TvFocusKey) {
        attachedAnchors[key] = (attachedAnchors[key] ?: 0) + 1
    }

    /** 锚点节点脱离上报 (由 tvFocusAnchor 自动挂接). */
    fun onAnchorDetached(key: TvFocusKey) {
        attachedAnchors.remove(key)
    }

    /** [key] 的锚点当前是否附着 (目标存在性判断). */
    fun isAnchorAttached(key: TvFocusKey): Boolean = attachedAnchors.containsKey(key)

    /** 锚点焦点得失上报 (由 tvFocusAnchor 自动挂接, 手写节点亦可直接调用). */
    fun onAnchorFocusChanged(key: TvFocusKey, focused: Boolean) {
        if (focused) focusedKeys.add(key) else focusedKeys.remove(key)
    }

    /** [key] (或其子树) 当前是否持有焦点. */
    fun isFocused(key: TvFocusKey): Boolean = key in focusedKeys

    /**
     * 解析安装点: 页面根部组合一次. 快照流响应"有 pending 且目标锚点已附着" ->
     * 送焦一次, **成功才清 pending**: 送焦被拒 (目标暂不可聚焦 / 转场中) 时请求继续悬挂,
     * 目标下次附着或换代时重试; 用户按键仍可取消. 单实例消化所有 [request], 避免多处请求打架.
     */
    @Composable
    fun Resolver() {
        LaunchedEffect(this) {
            snapshotFlow {
                val p = pending ?: return@snapshotFlow null
                // 附着代数入元组: 目标脱离又重附着 (Lazy 回收再滚回) 也会重新触发
                attachedAnchors[p.first]?.let { generation -> Triple(p.first, p.second, generation) }
            }
                .filterNotNull()
                .collect { (key, _, _) ->
                    val granted = runCatching { requesterOf(key).requestFocus() }.getOrDefault(false)
                    if (granted && pending?.first == key) pending = null
                }
        }
    }

    /**
     * 进页初始焦点: 等 route 进入前台的 **Lifecycle RESUMED 事件** (转场完成) 后请求 [key]
     * (锚点未附着则悬挂到附着, 无延时). 转场中一律不送焦 —— 转场里的 requestFocus 会被
     * 转场收尾冲掉 (push/pop 皆然, 真人按键时序下稳定复现), 事件门控比"送了再补"可靠.
     * 壳内切页 (同一 route) 时 lifecycle 已是 RESUMED, 等价于立即请求.
     *
     * 跨 route 返回 (焦点记忆 Armed) 时先裁决记忆: 认领已登记 -> 记忆恢复原位, 不落默认锚点;
     * 无认领 (数据未到) -> 落默认锚点防悬空, 目标附着后由记忆即时接管.
     */
    @Composable
    fun InitialFocus(key: TvFocusKey) {
        val memory = LocalTvFocusMemory.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(this) {
            // LaunchedEffect 在组合应用后执行, 此刻首帧组合内的记忆认领 (SideEffect) 已完成
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            if (memory?.pendingRestoreId != null && memory.activate()) return@LaunchedEffect
            request(key)
        }
    }
}

/** 创建页面级焦点调度器. 页面根部另装 [TvFocusScope.Resolver]. */
@Composable
fun rememberTvFocusScope(): TvFocusScope = remember { TvFocusScope() }
