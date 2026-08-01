/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import coil3.EventListener
import coil3.Image
import coil3.asImage
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult

actual fun ImageBitmap.asCoilImage(): Image {
    return this.asSkiaBitmap().asImage()
}

internal actual fun imageLoadIssueEventListenerFactory(): EventListener.Factory =
    EventListener.Factory { ImageLoadIssueEventListener() }

private class ImageLoadIssueEventListener : EventListener() {
    private val tracker = ImageLoadIssueTracker()

    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        tracker.fetchStart()
    }

    override fun fetchEnd(
        request: ImageRequest,
        fetcher: Fetcher,
        options: Options,
        result: FetchResult?,
    ) {
        tracker.fetchEnd()
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        tracker.success(request, result)
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        tracker.error(request, result)
    }
}
