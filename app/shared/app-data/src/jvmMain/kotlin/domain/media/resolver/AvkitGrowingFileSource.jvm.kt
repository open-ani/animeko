/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.io.RawSource
import kotlinx.io.files.SystemFileSystem
import me.him188.ani.utils.io.SystemPath

internal actual fun avkitGrowingFileSource(path: SystemPath): RawSource = SystemFileSystem.source(path.path)
