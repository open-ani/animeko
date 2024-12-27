/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.torrent.anitorrent.session

import kotlinx.io.files.Path
import me.him188.ani.utils.io.toNioPath
import org.openani.anitorrent.binding.torrent_resume_data_t
import kotlin.io.path.absolutePathString

class SwigTorrentResumeData(
    private val native: torrent_resume_data_t,
) : TorrentResumeData {
    override fun saveToPath(path: Path) {
        native.save_to_file(path.toNioPath().absolutePathString())
    }
}
