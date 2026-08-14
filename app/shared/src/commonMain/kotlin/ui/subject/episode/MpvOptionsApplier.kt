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
import org.openani.mediamp.mpv.MPVHandle
import org.openani.mediamp.mpv.MpvMediampPlayer

private val logger = logger<MpvMediampPlayer>()

/**
 * 将用户自定义的 mpv 选项 ([me.him188.ani.app.data.models.preference.PlayerKernelConfig.mpvOptions]) 应用到 [player].
 *
 * 只有使用 mpv 内核的平台 (桌面端) 会真正应用, 其他平台以及非 mpv 的播放器实现都是空操作.
 *
 * 选项在 mpv 实例初始化之后设置, 因此只能在启动时设置的选项 (例如 `vo`) 会被 mpv 拒绝, 此时仅记录日志.
 * 可能阻塞调用线程 (mpv 实例是懒创建的), 应在后台线程调用.
 */
internal fun MpvMediampPlayer.applyOptions(options: List<MpvOption>) {
    if (options.isEmpty()) return // 不要仅仅为了应用空配置而触发 mpv 实例的懒创建

    // 访问 impl 会触发 mpv 实例的创建 (mediamp 内部是 lazy 的), 此时 mediamp 自己的默认选项已经设置完毕,
    // 用户的选项在其之后应用, 因此可以覆盖它们.
    val handle = try {
        impl as? MPVHandle ?: return
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
