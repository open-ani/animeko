/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.videoplayer.ui

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import me.him188.ani.app.videoplayer.subtitle.SubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.SubtitleSafeArea
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVTextStyleRule
import platform.AVFoundation.currentItem
import platform.AVFoundation.textStyleRules
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreMedia.kCMTextMarkupAttribute_OrthogonalLinePositionPercentageRelativeToWritingDirection
import platform.CoreMedia.kCMTextMarkupAttribute_RelativeFontSize

/** 原生 AVKit 文本轨道与外挂字幕共享字号和控制栏避让设置。 */
internal class IosNativeSubtitleStyle(private val player: AVPlayer) {
    private var item: AVPlayerItem? = null
    private var fontScale = -1f
    private var position = -1.0

    fun update(appearance: SubtitleAppearance, safeArea: SubtitleSafeArea, videoHeight: Double) {
        val current = player.currentItem ?: return
        val scale = appearance.effectiveFontScale
        val linePosition = if (videoHeight > 0) {
            ((videoHeight - maxOf(24.0, safeArea.bottom + 12.0) - 22 * scale) / videoHeight * 100).coerceIn(10.0, 90.0)
        } else 90.0
        if (current == item && scale == fontScale && linePosition == position) return
        current.textStyleRules = listOf(AVTextStyleRule(textMarkupAttributes = mapOf(
            checkNotNull(kCMTextMarkupAttribute_RelativeFontSize).stringValue() to scale.toDouble() * 100,
            checkNotNull(kCMTextMarkupAttribute_OrthogonalLinePositionPercentageRelativeToWritingDirection).stringValue() to linePosition,
        )))
        item = current
        fontScale = scale
        position = linePosition
    }
}

private fun CFStringRef.stringValue(): String = memScoped {
    val capacity = CFStringGetMaximumSizeForEncoding(CFStringGetLength(this@stringValue), kCFStringEncodingUTF8) + 1
    val bytes = allocArray<ByteVar>(capacity)
    check(CFStringGetCString(this@stringValue, bytes, capacity, kCFStringEncodingUTF8))
    bytes.toKString()
}
