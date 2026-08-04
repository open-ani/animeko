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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/*
 * TV 统一焦点管理框架 (设计泛化自上游 PR#3217 的 TvDetailsFocusAnchors / FocusResolver,
 * 那边是每页手写一套, 这里抽成可复用的 scope + Modifier 扩展; 使用侧 API 见 TvFocusModifiers.kt).
 *
 * 概念:
 * - [TvFocusKey]: 页面内一个具名焦点位置 (锚点). 页面用 enum 实现或 [TvFocusKey] 工厂创建.
 * - [TvFocusScope]: 页面级调度器. 所有"把焦点送到某处"的入口 (进页初始焦点 / 返回键分层 /
 *   弹层关闭归还 / 全局快捷键) 都走 [TvFocusScope.request], 由单个解析循环消化 ——
 *   轮询 requestFocus, 锚点节点 (或其子树) 获得焦点时确认到位即停.
 *
 * 为什么需要轮询 + 到位确认 (而不是一次 requestFocus):
 * - 节点未附着 (页面切换/弹层退场的头几帧) 时 requestFocus 静默失败, 返回值也不可靠;
 * - 页面切换期间其它异步焦点分配可能后到抢焦点, 需要多帧兜底;
 * - 到位判据必须查"当前聚焦状态"而非一次性事件: 系统可能在解析启动前就自行把焦点还给
 *   锚点, 此时对已聚焦节点的 requestFocus 不再产生焦点事件, 靠事件标志会烧满全部轮询
 *   次数, 期间用户移开的焦点每帧被抢回 (上游实测事故).
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
 * 线程模型: 全部在主线程 (组合/效应) 使用.
 */
@Stable
class TvFocusScope {
    private val requesters = mutableMapOf<TvFocusKey, FocusRequester>()

    /** 当前待解析的请求 (锚点 + 序号; 序号使同锚点连续请求也能重新触发); null = 空闲. */
    internal var pending: Pair<TvFocusKey, Int>? by mutableStateOf(null)
        private set

    // 当前聚焦的锚点集合 (各锚点 onFocusChanged 得失双向上报, 见 tvFocusAnchor)
    private val focusedKeys = mutableSetOf<TvFocusKey>()

    // 事件闩 (兜底): 焦点在两次轮询之间落到锚点又立即被移走时当前状态查不到, 靠它记住.
    // 在 request 重置 (而非解析起手): 请求到解析启动隔着一帧, 期间到位的事件不能丢
    private var arrivedLatch = false

    // 用户方向键计数 (tvFocusNavSignal 上报): 解析期间用户开始导航 = 立即放弃本次请求 ——
    // 否则轮询会把用户刚移走的焦点一次次抢回目标锚点 (实机症状: 按走后焦点又跳回,
    // 要连按多次才能离开). 判据同上游 PR 的 GridFocusController "按键放弃" 语义.
    private var userNavCount = 0

    /** [key] 的 FocusRequester (惰性创建). 供需要原始 requester 的组件互操作 (如 SideRail). */
    fun requesterOf(key: TvFocusKey): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /**
     * 请求把焦点送到 [key] (fire-and-forget): 由 [Resolver] 轮询解析, 到位即停.
     * 同 key 连续请求也会重新触发 (弹层反复开关归还焦点的场景).
     */
    fun request(key: TvFocusKey) {
        arrivedLatch = false
        pending = key to ((pending?.second ?: 0) + 1)
    }

    /** 用户按下方向键的上报 (由 [tvFocusNavSignal]/[tvFocusHotkey] 自动挂接): 放弃在途解析. */
    fun notifyUserNavigation() {
        userNavCount++
    }

    /** 锚点焦点得失上报 (由 [tvFocusAnchor] 自动挂接, 手写节点亦可直接调用). */
    fun onAnchorFocusChanged(key: TvFocusKey, focused: Boolean) {
        if (focused) {
            focusedKeys.add(key)
            if (pending?.first == key) arrivedLatch = true
        } else {
            focusedKeys.remove(key)
        }
    }

    /** [key] (或其子树) 当前是否持有焦点. */
    fun isFocused(key: TvFocusKey): Boolean = key in focusedKeys

    /**
     * 解析循环安装点: 页面根部组合一次. 单实例消化所有 [request], 避免多处请求打架.
     */
    @Composable
    fun Resolver() {
        LaunchedEffect(pending) {
            val (key, _) = pending ?: return@LaunchedEffect
            val requester = requesterOf(key)
            val navAtStart = userNavCount
            resolveFocusRepeatedly(
                arrived = { arrivedLatch || key in focusedKeys },
                // 用户开始导航即放弃: 不跟用户抢焦点
                abandon = { userNavCount != navAtStart },
            ) {
                runCatching { requester.requestFocus() }
            }
            pending = null
        }
    }

    /**
     * 进页初始焦点: 等待标准布局延迟后请求 [key]. 与 [request] 同一条解析路径
     * (轮询 + 到位确认), 页面切换转场期间节点未附着也能兜住.
     *
     * 若焦点记忆里有待认领的跨 route 恢复目标 (从详情页等返回, 见 [TvFocusMemory]),
     * 优先恢复到它; 目标已不在 (数据变化) 或恢复失败时退回 [key] 默认初始焦点.
     */
    @Composable
    fun InitialFocus(key: TvFocusKey) {
        val memory = LocalTvFocusMemory.current
        LaunchedEffect(Unit) {
            delay(FOCUS_REQ_DELAY_MILLIS)
            val restoreTarget = memory?.takePendingRestore()
            if (restoreTarget != null) {
                val navAtStart = userNavCount
                val restored = resolveFocusRepeatedly(
                    arrived = { memory.last == restoreTarget },
                    abandon = { userNavCount != navAtStart },
                ) {
                    runCatching { restoreTarget.requestFocus() }
                }
                if (restored) return@LaunchedEffect
            }
            request(key)
        }
    }
}

/** 创建页面级焦点调度器. 页面根部另装 [TvFocusScope.Resolver]. */
@Composable
fun rememberTvFocusScope(): TvFocusScope = remember { TvFocusScope() }

/**
 * 内容区"最后聚焦点"记忆: 壳对内容子树 provide 一个实例, 可聚焦组件 (TvPosterCard /
 * TvHeroButton 等) 聚焦时上报自身 requester; 焦点去了侧边栏等外部区域后可经 [restore]
 * 精确回到原位.
 *
 * 同页恢复 (侧边栏往返) 用 [last] requester 直接 requestFocus. **跨 route 恢复** (进详情
 * 页返回, 页面组合已重建, 旧 requester 失效) 用身份键: 组件聚焦时一并上报稳定的
 * [lastId] (如 subjectId); 壳在 route 重建时把它转成 [pendingRestoreId], 新组合中身份
 * 匹配的组件经 [claimPendingRestore] 认领登记, 页面 [TvFocusScope.InitialFocus] 统一
 * 消费 —— 认领成功恢复原位, 目标不在了退回默认初始焦点.
 *
 * 为什么不用 Compose 的 saveFocusedChild/focusRestorer: 它们只保存/恢复"第一层子 target",
 * 跨多层容器 (壳 -> AnimatedContent -> 页面 -> Lazy 列表 -> 卡) 时保存到的是不可聚焦的
 * 中间容器, 恢复必然失败 (同帧 save=true/restore=false, TV 模拟器实测).
 */
@Stable
class TvFocusMemory {
    /** 最后聚焦组件的 requester; 由组件聚焦时上报. */
    var last: FocusRequester? = null

    /** 最后聚焦组件的身份键 (组件侧 memoryId, 页内唯一); null = 该组件不参与跨 route 恢复. */
    var lastId: Any? = null

    /**
     * 待认领的跨 route 恢复目标身份键 (snapshot 状态: 组件组合中读取, 匹配即认领).
     * 由壳在 route 重建时从 [lastId] 转入.
     */
    var pendingRestoreId: Any? by mutableStateOf(null)

    /** 身份匹配组件认领后登记的 requester (新组合里的新实例). */
    private var pendingRequester: FocusRequester? = null

    /** 组件聚焦时上报 (foundation 可聚焦组件自动挂接). */
    fun reportFocused(requester: FocusRequester, id: Any?) {
        last = requester
        lastId = id
    }

    /** 身份为 [id] 的组件在新组合中登记自身 (组合/SideEffect 阶段调用, 不清 pending). */
    fun claimPendingRestore(id: Any, requester: FocusRequester) {
        if (pendingRestoreId == id) pendingRequester = requester
    }

    /** 取走认领结果并结束本轮跨 route 恢复 (由 [TvFocusScope.InitialFocus] 消费). */
    fun takePendingRestore(): FocusRequester? {
        val result = pendingRequester
        pendingRequester = null
        pendingRestoreId = null
        return result
    }

    /** 恢复到最后聚焦点 (同页场景). 返回是否发起了恢复 (节点已销毁时由调用方兜底). */
    fun restore(): Boolean {
        val requester = last ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }
}

/**
 * 当前内容区的焦点记忆; null = 所在子树不参与记忆 (如侧边栏).
 * 壳在内容区根部 provide, 组件侧由 foundation 可聚焦组件自动上报, 页面无须感知.
 */
val LocalTvFocusMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

/**
 * 轮询解析焦点请求 (设计同上游 PR 的 FocusResolver): 每帧先查 [arrived] 后试 [attempt] ——
 * 已到位时不再多发一次 requestFocus, 否则用户刚移开的焦点会被抢回来一格.
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
        if (arrived()) return true
        if (abandon()) return false
        attempt()
        if (arrived()) return true
        if (delayMillis > 0) delay(delayMillis)
    }
    return false
}
