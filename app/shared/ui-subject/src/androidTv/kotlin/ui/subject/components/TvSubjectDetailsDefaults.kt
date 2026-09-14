/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 详情页共享的尺寸与形状。 */
internal object TvSubjectDetailsDefaults {
    /** 内容水平留白 (含让开侧栏收起宽; 详情页是独立目的地, 自带留白). */
    val HorizontalPadding = 58.dp

    // Google TV reference measured in a 960 × 540 viewport.
    const val OverviewHeightFraction = .90f
    val TitleSize = 40.sp
    val TitleLineHeight = 54.sp
    val OverviewBottomPadding = 24.dp
    val EndPadding = 192.dp
    val DescriptionCardHeight = 116.dp
    val DescriptionCardShape = RoundedCornerShape(26.dp)
    val ActionHeight = 40.dp
    val ActionShape = RoundedCornerShape(50)
    val ActionBlurRadius = 24.dp
    val ActionTint = Color.White.copy(alpha = .16f)
    val PlayGlowRadius = 16.dp
    val PlayGlowSpread = 2.dp
    const val PlayGlowAlpha = .48f
    val TooltipMaxWidth = 280.dp
    val TooltipSpacing = 12.dp
    val TooltipSlide = 10.dp
    val RatingPanelWidth = 420.dp
    const val RatingPanelAnchorFraction = .25f
    val RatingPanelMaxHeight = 340.dp
    val PanelMaxWidth = 860.dp
    val EpisodesPanelMaxWidth = 600.dp
    val Content = Color(0xFFF1F1F1)
    val SecondaryContent = Color(0xFFBFBDBF)
    val Background = Color(0xFF282629)
    val ReaderBlurRadius = 48.dp
    val ReaderEdgeFade = 32.dp
    val RowSpacing = 20.dp
    val RelatedCardWidth = 198.dp
    const val InformationSurroundingAlpha = .35f

    /** backdrop 随滚动淡出的距离. */
    val BackdropFadeDistance = 400.dp

    /** 选集剧照卡宽度 (16:9). */
    val EpisodeCardWidth = 226.dp

    /** 角色/制作人员卡宽度. */
    val PersonCardWidth = 124.dp

    /** 评价卡宽度. */
    val CommentCardWidth = 320.dp
}
