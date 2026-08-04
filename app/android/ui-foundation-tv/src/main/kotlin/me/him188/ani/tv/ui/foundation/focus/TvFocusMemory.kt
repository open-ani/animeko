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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/*
 * 内容区焦点记忆 —— 完整协议都在本文件内:
 *
 * 参与方与生命周期:
 * 1. 【创建】壳的调用方在 NavHost **之上** `remember { TvFocusMemory() }` (跨 route 存活),
 *    传给壳; 壳只对内容子树 `LocalTvFocusMemory provides` (侧边栏等外部区域不参与).
 * 2. 【上报】内容区可聚焦组件挂 [tvFocusMemorable]: 聚焦时上报自身 requester + 可选的
 *    稳定身份键 memoryId (页内唯一, 如 "rec-$subjectId"; null = 不参与跨 route 恢复).
 * 3. 【同页恢复】焦点去了侧边栏后返回: 壳调 [TvFocusMemory.restore], 直接 requestFocus
 *    上次的 requester (节点仍存活).
 * 4. 【跨 route 恢复】进详情页返回, 页面组合已重建、旧 requester 失效:
 *    壳在 route 重组处调 [TvFocusMemory.ArmOnRouteReturn] (组合期把 lastId 转成待认领
 *    目标) -> 新组合中身份匹配的 [tvFocusMemorable] 组件自动认领登记新 requester ->
 *    页面 [TvFocusScope.InitialFocus] 统一消费 (轮询恢复; 目标没了退回默认初始锚点).
 * 5. 【清理】壳在内容页切换时清 last/lastId (换页语义上不该恢复, 交给新页 InitialFocus).
 *
 * 为什么不用 Compose 的 saveFocusedChild/focusRestorer: 它们只保存/恢复"第一层子 target",
 * 跨多层容器 (壳 -> AnimatedContent -> 页面 -> Lazy 列表 -> 卡) 时保存到的是不可聚焦的
 * 中间容器, 恢复必然失败 (同帧 save=true/restore=false, TV 模拟器实测).
 */

/**
 * 内容区"最后聚焦点"记忆. 协议全貌见本文件头注释.
 */
@Stable
class TvFocusMemory {
    /** 最后聚焦组件的 requester; 由 [tvFocusMemorable] 聚焦时上报. */
    var last: FocusRequester? = null

    /** 最后聚焦组件的身份键; null = 该组件不参与跨 route 恢复. */
    var lastId: Any? = null

    /**
     * 待认领的跨 route 恢复目标身份键 (snapshot 状态: 组件组合中读取, 匹配即认领).
     * 由 [ArmOnRouteReturn] 从 [lastId] 转入.
     */
    var pendingRestoreId: Any? by mutableStateOf(null)
        private set

    /** 身份匹配组件认领后登记的 requester (新组合里的新实例). */
    private var pendingRequester: FocusRequester? = null

    /** 组件聚焦时上报 (由 [tvFocusMemorable] 自动挂接). */
    fun reportFocused(requester: FocusRequester, id: Any?) {
        last = requester
        lastId = id
    }

    /** 身份为 [id] 的组件在新组合中登记自身 (由 [tvFocusMemorable] 自动挂接, 不清 pending). */
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

    /** 内容页切换时清空记忆 (换页语义上不该恢复; 由壳挂接). */
    fun clear() {
        last = null
        lastId = null
    }

    /** 恢复到最后聚焦点 (同页场景). 返回是否发起了恢复 (节点已销毁时由调用方兜底). */
    fun restore(): Boolean {
        val requester = last ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }

    /** [ArmOnRouteReturn] 的非组合内核: 把离开前的身份键转成待认领的 pending. */
    fun armFromLast() {
        pendingRestoreId = lastId
    }

    /**
     * route 重组时装填跨 route 恢复目标 ([armFromLast]).
     * 须在内容子树组合**之前**调用 (本函数用组合期执行的 remember 块保证; 组件组合时
     * 已能读到 pending). 首次启动 lastId=null, 无害.
     */
    @Composable
    fun ArmOnRouteReturn() {
        remember(this) {
            armFromLast()
            true
        }
    }
}

/**
 * 当前内容区的焦点记忆; null = 所在子树不参与记忆 (如侧边栏).
 * 壳在内容区根部 provide; 组件挂 [tvFocusMemorable] 即自动接入, 页面无须感知.
 */
val LocalTvFocusMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

/**
 * 把本节点接入内容区焦点记忆: 聚焦时上报 (同页恢复), [memoryId] 非 null 时参与跨
 * route 恢复 (身份匹配的新组合节点自动认领). 挂在可聚焦组件的 modifier 链上即可,
 * 所在子树没有 provide 记忆时为空操作.
 */
fun Modifier.tvFocusMemorable(memoryId: Any? = null): Modifier = composed {
    val memory = LocalTvFocusMemory.current ?: return@composed Modifier
    val selfRequester = remember { FocusRequester() }
    if (memoryId != null && memory.pendingRestoreId == memoryId) {
        SideEffect { memory.claimPendingRestore(memoryId, selfRequester) }
    }
    Modifier
        .focusRequester(selfRequester)
        .onFocusChanged { if (it.isFocused) memory.reportFocused(selfRequester, memoryId) }
}
