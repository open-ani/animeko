/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import org.openani.mediamp.source.MediaData

internal actual suspend fun preparePlatformMediaPlayback(data: MediaData): PlatformMediaPlayback =
    PlatformMediaPlayback(data)
