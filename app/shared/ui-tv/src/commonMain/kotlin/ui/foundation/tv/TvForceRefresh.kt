/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.foundation.TV_PLAY_KEYS
import me.him188.ani.app.ui.foundation.tvLongPressKey
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.tv_force_refresh_toast
import org.jetbrains.compose.resources.stringResource

/**
 * 遥控器**播放键长按 = 强制刷新**, 短按仍是 [onPlay] (直达播放).
 *
 * 只加在"刷新有意义"的页面上 (追番 / 新番时间表 / 探索页的继续观看): 这些页面的数据都是一小时
 * 一刷的定时拉取 (见各自的 repository), 用户想立刻看到更新时没有别的入口.
 *
 * 判定与全库其它长按共用一份实现 (见 [tvLongPressKey]): 按住到阈值**当场**触发 (不等松手,
 * 立即给出 toast 反馈), 松手时若还没触发就算短按. 因此本 modifier 必须挂在**已经拥有播放键的
 * 那个节点**上并接管它 —— 短按要等到 KeyUp 才能确定, 期间不能让 KeyDown 漏下去被别人当成
 * "按了播放" (所以网格的键路由把 onPlayKey 让给了本 modifier).
 *
 * @param onRefresh 长按触发: 强制重拉数据
 * @param onPlay 短按触发, 返回是否已处理 (焦点不在卡片上时返回 false)
 */
@Composable
fun tvPlayKeyForceRefresh(
    onRefresh: () -> Unit,
    onPlay: () -> Boolean = { false },
): Modifier {
    val toaster = LocalToaster.current
    val refreshingText = stringResource(Lang.tv_force_refresh_toast)
    // 不 remember 这个 Modifier: 它要读到最新的 onRefresh/onPlay 与文案 (remember 会把首次组合
    // 那一份闭包永久留下), 而 modifier 元素本身很轻, 每次重组重建无所谓
    return Modifier.tvLongPressKey(
        onLongPress = {
            // 刷新本身可能没有可见变化 (数据没变时界面一模一样), 必须给一句反馈
            toaster.toast(refreshingText)
            onRefresh()
        },
        onShortPress = { onPlay() },
        keys = TV_PLAY_KEYS,
    )
}
