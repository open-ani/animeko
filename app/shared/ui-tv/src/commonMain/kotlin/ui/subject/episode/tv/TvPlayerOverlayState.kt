/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * TV 播放器覆盖层的层级. 整个播放器界面只有这一个状态机, 所有层级切换都经由
 * [TvPlayerOverlayState] 的方法, 所有按键语义都收敛在 TvEpisodeScreen 根部的唯一路由里,
 * 不在各组件内散落 BackHandler/onKeyEvent 补丁.
 */
enum class TvPlayerLayer {
    /** 纯视频态: 无任何组件, 焦点在根节点上收按键. */
    HIDDEN,

    /** 控制层: 顶部标题/时钟 + 胶囊按钮行 (聚焦时上方浮出面板) + 进度条 + 图标行. */
    CONTROLS,

    /** 详情页覆盖层: 隐藏全部播放器组件, 正在播放的视频画面作为详情页背景. */
    DETAILS,
}

/** 胶囊按钮对应的浮出面板 (第二层). */
enum class TvPlayerPanel {
    /** 弹幕列表 (底部含弹幕源开关/延迟调整). */
    DANMAKU_LIST,

    /** 相关推荐 (卡片形态). */
    RECOMMENDATIONS,

    /** 本集评论. */
    COMMENTS,

    /** 角色 (卡片形态: 头像 + 名字 + 角色/CV, 点击弹人物预览). */
    CHARACTERS,

    /** 制作人员 (卡片形态: 头像 + 名字 + 职位, 点击弹人物预览). */
    STAFF,
}

/**
 * 选集条的可用性. **三态**而非布尔: "还在加载"与"确认没有分集"必须分开 —— 合并成布尔后
 * 加载中会被当成没有分集, 图标行下键直接跳去详情页 (用户按下键的意思明明是看选集),
 * 数据到了才恢复正常, 表现为"按下键跳到内嵌详情页, 过一会儿又好了".
 */
enum class TvEpisodeStripState {
    /** 分集列表还没到 (loader 首次发射前). 下键记下意图等就绪, 不跳详情页. */
    LOADING,

    /** 确认没有可聚焦的分集 (未开播条目 / 加载失败). 下键直通详情页. */
    EMPTY,

    /** 有分集可聚焦. */
    AVAILABLE,
}

/**
 * 焦点当前所在的区域. 由各行容器的 onFocusChanged 上报 —— 只在获得焦点时更新,
 * 失焦不清除 (焦点交接的瞬间两边都是无焦点, 清除会产生瞬时 NONE 抖动).
 * 根按键路由据此决定边界行为 (如图标行按下进详情页, 面板内按返回回进度条).
 */
enum class TvPlayerFocusRegion {
    NONE,

    /** 浮出面板内 (弹幕列表/推荐/评论的条目上). */
    PANEL,

    /** 进度条上方的胶囊按钮行. */
    PILLS,

    /** 进度条行. */
    PROGRESS,

    /** 进度条下方的图标行. */
    BOTTOM_ROW,

    /** 图标行下方的选集条 (Prime 形态: 图标行按下键展开, 焦点在轮播卡片上). */
    EPISODES,
}

/**
 * 焦点落点解析的目标 (见 [TvPlayerOverlayState.pendingFocus]).
 * ROOT/PROGRESS/EPISODE_STRIP/BOTTOM_ROW 由 TvEpisodeScreen 的统一解析器消化,
 * PANEL 由面板宿主 (TvPlayerPanelHost) 消化 —— 入口请求器在它那棵子树里.
 */
enum class TvPlayerFocusTarget {
    /** 根节点 (回纯视频态收回焦点). */
    ROOT,

    /** 进度条行. */
    PROGRESS,

    /** 选集条轮播卡片. */
    EPISODE_STRIP,

    /** 进度条下方图标行. */
    BOTTOM_ROW,

    /** 浮出面板入口. */
    PANEL,
}

/**
 * TV 播放器覆盖层状态机.
 *
 * 性能约定: 这里的每个字段都是独立的 State, 消费方须在尽可能小的作用域读取
 * (布局层用 AnimatedVisibility 的 lambda / 子组件内读取), 避免按键一次整层重组.
 */
@Stable
class TvPlayerOverlayState {
    var layer: TvPlayerLayer by mutableStateOf(TvPlayerLayer.HIDDEN)
        private set

    /** 当前浮出的面板; null = 无面板. 由胶囊按钮聚焦时设置, 焦点移到进度条/图标行时清除. */
    var activePanel: TvPlayerPanel? by mutableStateOf(null)

    var focusRegion: TvPlayerFocusRegion by mutableStateOf(TvPlayerFocusRegion.NONE)

    /** 弹幕输入框展开中 (IME 态): 除 Back 收起外, 按键全部交给输入框. */
    var danmakuInputExpanded: Boolean by mutableStateOf(false)

    /** 打开中的下拉弹层数量 (倍速/画面比例等, 经 onExpandedChanged 上报): >0 时不自动隐藏. */
    var openPopupCount: Int by mutableIntStateOf(0)

    /**
     * 正在回复的评论 (TV 专用回复弹窗, 见 TvCommentReplyDialog); null = 未打开.
     *
     * 不用手机端那个底部 sheet: 它带右上角关闭按钮、评论正文挤在输入框下面, 且返回键收起后
     * 没有任何东西把焦点还回列表 (Compose 移除聚焦节点时会清掉整棵树的焦点, 不会交给祖先).
     */
    var replyingComment: TvCommentReplyTarget? by mutableStateOf(null)
        private set

    /**
     * 面板条目焦点找回锚: 每次自增一下, 面板宿主就把焦点送回**当前聚焦的那一条**
     * (由条目获焦时登记请求器, 见 TvPlayerPanelHost).
     *
     * 用于一切"从面板条目点开了别的东西, 那东西关掉之后"的场合: 回复弹窗、人物预览、
     * 弹幕延迟对话框. 不这么做的话焦点会停在被移除的节点上 (= 没有焦点), 方向键全失效.
     */
    var panelItemFocusTick: Int by mutableIntStateOf(0)
        private set

    /** 播放器统计悬浮层开关 (三个点菜单切换). */
    var showPlayerStats: Boolean by mutableStateOf(false)

    /**
     * 选集条展开中 (Prime 形态): 胶囊/进度条/图标行隐藏, 选集条完整展开在底部.
     * 收起态只在图标行下露出 "剧集" 标题 + 卡片顶部一条 (peek).
     */
    var episodeStripExpanded: Boolean by mutableStateOf(false)
        private set

    /** 选集条可用性 (见 [TvEpisodeStripState]), 由选集条组件经 [onEpisodeStripStateChanged] 上报. */
    var episodeStrip: TvEpisodeStripState by mutableStateOf(TvEpisodeStripState.LOADING)
        private set

    /**
     * 图标行下键时选集条还在加载: 记下意图, 就绪后自动展开.
     *
     * 加载中不能跳详情页 (那是"确认无分集"才该做的), 也不能干脆吞掉 —— 吞掉的话用户会以为
     * 遥控器没反应而重复按. 记意图 = 按下去就一定会展开, 只是可能晚一两帧.
     */
    var expandStripWhenReady: Boolean by mutableStateOf(false)

    /** 自动隐藏计时锚: 每次按键交互自增, 计时协程以它为 key 重启. */
    var interactionTick: Int by mutableIntStateOf(0)
        private set

    /**
     * 待解析的焦点落点 (目标 + 序号; 序号使同目标连续请求也能重新触发解析).
     * 单一 pending: 新请求**替换**旧请求 —— 过去每个目标各挂一个独立轮询循环,
     * 快速交替 (如选集条 展开→收起→展开) 时新旧循环并发运行, 一方到位后另一方
     * 仍会继续 requestFocus 一秒多, 把焦点抢回去. 现在解析器用 collectLatest
     * 收本字段, 新请求一到旧解析立即取消, 不存在互抢窗口.
     *
     * 初始即请求 ROOT (进入页面根节点收焦, 纯视频态直接收按键).
     */
    var pendingFocus: Pair<TvPlayerFocusTarget, Int> by mutableStateOf(TvPlayerFocusTarget.ROOT to 0)
        private set

    private fun requestFocus(target: TvPlayerFocusTarget) {
        pendingFocus = target to (pendingFocus.second + 1)
        // 不要在这里顺手把 focusRegion 设成目标区域. [focusRegion] 同时是落点解析器的**到位判据**
        // (见 TvEpisodeScreen 的解析循环), 而 resolveFocusRepeatedly 在第一次 attempt 之前就查
        // 一遍判据 —— 提前写成"已到位"会让解析器一次 requestFocus 都不发, 整个控制层没有任何
        // 东西拿到焦点 (表现为焦点不见了 + 方向键失效). 区域只能由 onFocusChanged 回报.
    }

    fun markInteraction() {
        interactionTick++
    }

    /** 下拉弹层开合上报 (倍速/比例/字幕/三个点): 引用计数 + 重置自动隐藏计时. */
    fun onPopupExpandedChanged(expanded: Boolean) {
        openPopupCount = (openPopupCount + if (expanded) 1 else -1).coerceAtLeast(0)
        markInteraction()
    }

    /** 把焦点送进当前浮出面板 (点击胶囊按钮时). */
    fun requestPanelFocus() {
        requestFocus(TvPlayerFocusTarget.PANEL)
    }

    /** 把焦点送回根节点 (纯视频态收按键). */
    fun requestRootFocus() {
        requestFocus(TvPlayerFocusTarget.ROOT)
    }

    /** 把焦点送回面板里当前聚焦的那一条 (见 [panelItemFocusTick]). */
    fun requestPanelItemFocus() {
        panelItemFocusTick++
        markInteraction()
    }

    /**
     * 评论弹窗内左右键"翻到相邻评论"的请求 (delta, 序号). 由评论面板消费 —— 只有它手上有
     * 展平后的行列表, 而弹窗是屏幕级的兄弟节点 (见 TvEpisodeScreen).
     *
     * 序号使同方向的连续请求也能重新触发; delta 为 +1/-1, 到端点由面板自行忽略.
     */
    var replyNavRequest: Pair<Int, Int> by mutableStateOf(0 to 0)
        private set

    /** 打开评论回复弹窗 (面板里点某条评论). */
    fun startReply(target: TvCommentReplyTarget) {
        replyingComment = target
        markInteraction()
    }

    /** 评论弹窗内左右键: 请求翻到相邻的一条评论 (见 [replyNavRequest]). */
    fun navigateReply(delta: Int) {
        replyNavRequest = delta to (replyNavRequest.second + 1)
        markInteraction()
    }

    /** 关闭评论弹窗, 焦点还给刚点开的那条评论. */
    fun dismissReply() {
        val target = replyingComment ?: return
        replyingComment = null
        // 面板里点开的那条: 焦点还给它. 胶囊点开的"发表评论"没有对应的面板条目 (面板里那些
        // 条目此刻一个都没被点过), 焦点由那颗胶囊自己的 restoreFocusAfter 收回 —— 这里再抢
        // 一次就是把焦点硬塞进面板, 与用户离开时的位置对不上
        if (target.quoted != null) requestPanelItemFocus()
    }

    /** 唤出控制层; [focusProgress] = 进入后把焦点放到进度条行 (默认). */
    fun showControls(focusProgress: Boolean = true) {
        layer = TvPlayerLayer.CONTROLS
        expandStripWhenReady = false
        // 上次隐藏时遗留的视觉状态在这里复位, 而不是在 hideAll/openDetails 里:
        // 覆盖层淡出途中复位会让被隐藏的控制行/图标行反向播放"回来"的入场动画
        // (半程可见后整层才消失), 观感是返回卡了一下
        episodeStripExpanded = false
        activePanel = null
        focusRegion = TvPlayerFocusRegion.NONE
        markInteraction()
        if (focusProgress) requestFocus(TvPlayerFocusTarget.PROGRESS)
    }

    /** 从详情层回到选集条 (详情页顶部按上键): 控制层出现时选集条直接是展开态并聚焦. */
    fun returnToEpisodeStrip() {
        // 无分集 (未开播条目) 时选集条不渲染: 展开态会把控制行也藏起来, 焦点请求器
        // 永远解析不到, 整层没有任何可聚焦目标 —— 退回普通控制层 (焦点落进度条)
        if (episodeStrip != TvEpisodeStripState.AVAILABLE) {
            showControls()
            return
        }
        layer = TvPlayerLayer.CONTROLS
        episodeStripExpanded = true
        activePanel = null
        focusRegion = TvPlayerFocusRegion.NONE
        markInteraction()
        requestFocus(TvPlayerFocusTarget.EPISODE_STRIP)
    }

    /** 展开选集条 (图标行按下键): 控制行隐藏, 焦点送到轮播卡片 (当前集). */
    fun expandEpisodeStrip() {
        expandStripWhenReady = false
        episodeStripExpanded = true
        markInteraction()
        requestFocus(TvPlayerFocusTarget.EPISODE_STRIP)
    }

    /** 选集条还在加载时按了下键: 记下意图 (见 [expandStripWhenReady]). */
    fun expandEpisodeStripWhenReady() {
        expandStripWhenReady = true
        markInteraction()
    }

    /**
     * 选集条上报可用性. 若有待兑现的展开意图且已就绪, 就地展开 ——
     * 但仅在控制层仍然在场时: 期间用户可能已经回了纯视频态或进了详情层 (等待期最长可达
     * 首次加载的整个时间), 那时展开会把焦点请求发到一棵没组合的子树上, 永远解析不到.
     */
    fun onEpisodeStripStateChanged(state: TvEpisodeStripState) {
        episodeStrip = state
        if (!expandStripWhenReady) return
        when {
            state == TvEpisodeStripState.LOADING -> {} // 继续等
            layer != TvPlayerLayer.CONTROLS -> expandStripWhenReady = false // 用户已经走开
            state == TvEpisodeStripState.AVAILABLE -> expandEpisodeStrip()
            else -> {
                // 等到的结果是"确认没有分集": 兑现成下键原本的兜底语义 (进详情页)
                expandStripWhenReady = false
                openDetails()
            }
        }
    }

    /** 收起选集条 (卡片上按上键): 控制行回来, 焦点还给图标行. */
    fun collapseEpisodeStrip() {
        episodeStripExpanded = false
        markInteraction()
        requestFocus(TvPlayerFocusTarget.BOTTOM_ROW)
    }

    /** 隐藏一切组件回纯视频态, 焦点收回根节点. 视觉状态不复位 (见 [showControls]). */
    fun hideAll() {
        layer = TvPlayerLayer.HIDDEN
        danmakuInputExpanded = false
        expandStripWhenReady = false
        replyingComment = null
        requestFocus(TvPlayerFocusTarget.ROOT)
    }

    /** 打开详情页覆盖层 (隐藏全部播放器组件). 视觉状态不复位 (见 [showControls]). */
    fun openDetails() {
        layer = TvPlayerLayer.DETAILS
        danmakuInputExpanded = false
        expandStripWhenReady = false
        replyingComment = null
    }

    /** 把焦点送回进度条行 (面板内按返回等). */
    fun focusProgress() {
        requestFocus(TvPlayerFocusTarget.PROGRESS)
    }
}
