/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.io

import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.io.SeekableInput
import uk.co.caprica.vlcj.media.callback.DefaultCallbackMedia

class SeekableInputCallbackMedia(
    private val input: SeekableInput,
    private val onClose: () -> Unit,
) : DefaultCallbackMedia(true) {
    override fun onGetSize(): Long = input.size
    override fun onOpen(): Boolean {
        if (ENABLE_LOGS) logger.debug { "open" }
        onSeek(0L)
        return true
    }

    override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
        if (ENABLE_LOGS) logger.debug { "reading max $bufferSize" }
        return try {
            input.read(buffer, 0, bufferSize).also {
                if (ENABLE_LOGS) logger.debug { "read $it" }
            }
        } catch (e: Exception) {
            logger.error(e) { "SeekableInputCallbackMedia failed to read. See cause." }
            -1
        }
    }

    override fun onSeek(offset: Long): Boolean {
        if (ENABLE_LOGS) logger.debug { "seeking to $offset" }
        input.seek(offset)
        if (ENABLE_LOGS) logger.debug { "seeking to $offset: ok" }
        return true
    }

    public override fun onClose() {
        logger.debug { "Closing CallbackMedia $this" }
        this.onClose.invoke()
    }

    private companion object {
        private const val ENABLE_LOGS = false
        private val logger = logger<SeekableInputCallbackMedia>()
    }
}