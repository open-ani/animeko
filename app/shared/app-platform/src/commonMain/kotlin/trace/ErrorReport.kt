/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.trace

import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.SentryId

object ErrorReport {
    inline fun captureMessage(
        message: String,
        level: SentryLevel = SentryLevel.INFO,
        crossinline config: Scope.() -> Unit = {}
    ): SentryId {
        return if (Sentry.isEnabled()) {
            Sentry.captureMessage(message) {
                it.level = level
                config(it)
            }
        } else {
            SentryId.EMPTY_ID
        }
    }

    inline fun breadcrumb(
        breadcrumb: Breadcrumb,
        config: Breadcrumb.() -> Unit = {}
    ) = Sentry.addBreadcrumb(breadcrumb.apply(config))

    inline fun captureException(
        throwable: Throwable,
        level: SentryLevel = SentryLevel.ERROR,
        crossinline config: Scope.() -> Unit = {}
    ): SentryId {
        return if (Sentry.isEnabled()) {
            Sentry.captureException(throwable) {
                it.level = level
                config(it)
            }
        } else {
            SentryId.EMPTY_ID
        }
    }
}
