/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.app.videoplayer.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import me.him188.ani.app.videoplayer.subtitle.SubtitleAppearance
import me.him188.ani.app.videoplayer.subtitle.SubtitleSafeArea
import me.him188.ani.app.videoplayer.subtitle.subtitleRegion
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.setFrame
import kotlin.math.floor

/** 单个多行文本层统一排版所有当前对白，尺寸受可见视频和控制栏边界约束。 */
internal class IosSubtitleOverlay : UIView(CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    internal val label = UILabel().apply {
        textAlignment = NSTextAlignmentCenter
        textColor = UIColor.whiteColor
        lineBreakMode = NSLineBreakByTruncatingTail
        layer.shadowColor = UIColor.blackColor.CGColor
        layer.shadowOpacity = 1f
        layer.shadowRadius = 2.0
        userInteractionEnabled = false
    }
    private var appearance = SubtitleAppearance()
    private var safeArea = SubtitleSafeArea()

    init {
        translatesAutoresizingMaskIntoConstraints = false
        userInteractionEnabled = false
        clipsToBounds = true
        addSubview(label)
    }

    fun update(text: String, appearance: SubtitleAppearance, safeArea: SubtitleSafeArea) {
        if (label.text == text && this.appearance == appearance && this.safeArea == safeArea) return
        label.text = text
        this.appearance = appearance
        this.safeArea = safeArea
        setNeedsLayout()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val region = bounds.useContents { subtitleRegion(size.width, size.height, safeArea) }
        label.hidden = label.text.isNullOrEmpty() || region.width <= 0 || region.height <= 0
        if (label.hidden) return
        label.numberOfLines = 0
        val requestedSize = 22.0 * appearance.effectiveFontScale
        var fontSize = requestedSize
        label.font = UIFont.systemFontOfSize(fontSize)
        while (fontSize > minOf(12.0, requestedSize) && measuredHeight(region.width) > region.height) {
            fontSize = maxOf(12.0, fontSize - 1.0)
            label.font = UIFont.systemFontOfSize(fontSize)
        }
        label.numberOfLines = floor(region.height / label.font.lineHeight).toLong().coerceIn(1, 6)
        val textHeight = minOf(region.height, measuredHeight(region.width))
        label.setFrame(CGRectMake(region.left, region.bottom - textHeight, region.width, textHeight))
    }

    private fun measuredHeight(width: Double): Double = label.sizeThatFits(CGSizeMake(width, 100_000.0)).useContents { height }
}
