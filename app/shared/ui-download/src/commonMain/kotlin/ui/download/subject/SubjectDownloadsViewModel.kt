/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download.subject

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import me.him188.ani.app.ui.foundation.AbstractViewModel

/**
 * 独立条目下载页的 ViewModel, 在存续期间持有该条目的 [SubjectDownloadsPresenter].
 * @param coroutineContext [backgroundScope] 的额外 context, 测试时传入测试调度器.
 */
class SubjectDownloadsViewModel(
    subjectId: Int,
    presenters: SubjectDownloadsPresenterFactory,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : AbstractViewModel(coroutineContext) {
    val presenter: SubjectDownloadsPresenter = presenters.create(subjectId, backgroundScope)
}
