/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.paging

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

fun CombinedLoadStates.exceptions(): Sequence<Throwable> {
    return sequence {
        (mediator?.append as? LoadState.Error)?.error?.let { yield(it) }
        (mediator?.prepend as? LoadState.Error)?.error?.let { yield(it) }
        (mediator?.refresh as? LoadState.Error)?.error?.let { yield(it) }
        (source.append as? LoadState.Error)?.error?.let { yield(it) }
        (source.prepend as? LoadState.Error)?.error?.let { yield(it) }
        (source.refresh as? LoadState.Error)?.error?.let { yield(it) }
    }
}
