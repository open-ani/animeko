/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import me.him188.ani.datasources.api.MediaExtraFiles

internal fun MediaExtraFiles.toMediampMediaExtraFiles(): org.openani.mediamp.source.MediaExtraFiles {
    return org.openani.mediamp.source.MediaExtraFiles(
        subtitles = subtitles.map {
            org.openani.mediamp.source.Subtitle(
                uri = it.uri,
                mimeType = it.mimeType,
                language = it.language,
            )
        },
    )
}
