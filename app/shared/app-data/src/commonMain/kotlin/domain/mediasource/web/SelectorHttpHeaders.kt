/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import me.him188.ani.app.domain.mediasource.web.format.SelectorFormatId
import me.him188.ani.app.domain.mediasource.web.format.SelectorSubjectFormatJsonPathIndexed

internal fun HttpRequestBuilder.acceptSelectorSearch(subjectFormatId: SelectorFormatId) {
    if (subjectFormatId == SelectorSubjectFormatJsonPathIndexed.id) {
        accept(ContentType.Application.Json)
        // JSON 搜索接口也可能返回 HTML 验证页, 交给现有验证码流程处理.
        accept(ContentType.Text.Html.withParameter("q", "0.9"))
    } else {
        accept(ContentType.Text.Html)
    }
}
