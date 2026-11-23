/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import me.him188.ani.app.domain.media.selector.SelectTrigger
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.datasources.api.Media

/**
 * 宿主观察用户在选择器里的操作.
 */
interface MediaSelectorStateListener {
    /**
     * 用户在选择器里点选了一项 ([MediaSelectorState.select]) 之后. 播放页用它结束本条目的浏览记忆:
     * 用户在自动匹配或 BT 页手动换了源, 说明不想再按记忆走.
     */
    suspend fun onUserSelect(media: Media, trigger: SelectTrigger) {}

    /**
     * 用户处理某个数据源的验证码结束.
     */
    fun onCaptchaResolved(instanceId: String, mediaSourceId: String, outcome: SolveOutcome) {}

    companion object Noop : MediaSelectorStateListener
}
