/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import me.him188.ani.app.data.models.preference.MpvOption
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer

private val logger = logger("MpvOptionsApplier")

internal actual fun applyMpvOptions(player: MediampPlayer, options: List<MpvOption>) {
    if (options.isEmpty()) return // 不要仅仅为了应用空配置而触发 mpv 实例的懒创建
    if (player !is MpvMediampPlayer) return

    // 访问 impl 会触发 mpv 实例的创建 (mediamp 内部是 lazy 的), 此时 mediamp 自己的默认选项已经设置完毕,
    // 用户的选项在其之后应用, 因此可以覆盖它们.
    val handle = try {
        player.impl as? MPVHandle ?: return
    } catch (e: Throwable) {
        logger.error(e) { "Failed to obtain MPVHandle, custom mpv options are not applied" }
        return
    }

    for ((key, value) in options) {
        val applied = try {
            handle.option(key, value)
        } catch (e: Throwable) {
            logger.error(e) { "Failed to apply mpv option '$key'" }
            continue
        }
        if (applied) {
            logger.info { "Applied custom mpv option '$key=$value'" }
        } else {
            logger.warn {
                "mpv rejected custom option '$key=$value'. " +
                        "The option may not exist, its value may be invalid, or it can only be set at startup."
            }
        }
    }
}
