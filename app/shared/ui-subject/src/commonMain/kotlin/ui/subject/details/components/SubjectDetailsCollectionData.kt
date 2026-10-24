/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UnusedReceiverParameter")

package me.him188.ani.app.ui.subject.details.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.subject.SubjectCollectionStats
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.theme.slightlyWeaken
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_details_collection_dropped
import me.him188.ani.app.ui.lang.subject_details_collection_summary
import org.jetbrains.compose.resources.stringResource

/** 收藏统计一行的 test tag, 供 UI 测试定位. */
const val SUBJECT_COLLECTION_DATA_TEST_TAG = "SubjectCollectionData"

/**
 * 收藏统计里的一个数字.
 *
 * [abbreviate] 为 `false` 时原样输出; 为 `true` 时按千位缩写:
 * - `count <= 1000`: 原样输出 (`1000` 仍显示 `1000`)
 * - `1000 < count < 10000`: 千位加一位小数, 末尾的 `.0` 省略 (`1555` -> `1.5k`, `9000` -> `9k`)
 * - `count >= 10000`: 千位只取整数部分 (`10000` -> `10k`, `35555` -> `35k`)
 *
 * 缩写只用于空间不足时, 因此不引入四舍五入: 结果不会比实际值大.
 */
internal fun formatCollectionCount(count: Int, abbreviate: Boolean): String {
    if (!abbreviate || count <= 1000) return count.toString()
    if (count < 10_000) {
        val tenths = count / 100
        val whole = tenths / 10
        val fraction = tenths % 10
        return if (fraction == 0) "${whole}k" else "$whole.${fraction}k"
    }
    return "${count / 1000}k"
}

// 详情页内容 (不包含背景)
@Composable
fun SubjectDetailsDefaults.CollectionData(
    collectionStats: SubjectCollectionStats,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()

    val fullSummary = stringResource(
        Lang.subject_details_collection_summary,
        formatCollectionCount(collectionStats.collect, abbreviate = false),
        formatCollectionCount(collectionStats.doing, abbreviate = false),
    )
    val fullDropped = stringResource(
        Lang.subject_details_collection_dropped,
        formatCollectionCount(collectionStats.dropped, abbreviate = false),
    )
    val abbreviatedSummary = stringResource(
        Lang.subject_details_collection_summary,
        formatCollectionCount(collectionStats.collect, abbreviate = true),
        formatCollectionCount(collectionStats.doing, abbreviate = true),
    )
    val abbreviatedDropped = stringResource(
        Lang.subject_details_collection_dropped,
        formatCollectionCount(collectionStats.dropped, abbreviate = true),
    )

    // 完整文本的宽度只取决于文案与字体, 与约束无关, 因此缓存起来.
    val fullWidth = remember(fullSummary, fullDropped, style, textMeasurer) {
        measureWidth(textMeasurer, fullSummary, style) + measureWidth(textMeasurer, fullDropped, style)
    }

    BoxWithConstraints(modifier.testTag(SUBJECT_COLLECTION_DATA_TEST_TAG)) {
        // 可用宽度由父 Row 决定: 收藏按钮是非加权兄弟, 先测量, 这里拿到的是剩余宽度.
        // 只有完整文本真的放不下时才缩写, 避免在宽屏上无谓地丢掉精度.
        val isCramped = constraints.maxWidth != Constraints.Infinity && fullWidth > constraints.maxWidth

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (isCramped) abbreviatedSummary else fullSummary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = style,
            )
            Text(
                if (isCramped) abbreviatedDropped else fullDropped,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = style,
                color = LocalContentColor.current.slightlyWeaken(),
            )
        }
    }
}

/** 按实际排版测量单行文本宽度 (px). */
private fun measureWidth(textMeasurer: TextMeasurer, text: String, style: TextStyle): Int =
    textMeasurer.measure(
        text = text,
        style = style,
        maxLines = 1,
        softWrap = false,
    ).size.width

private val PreviewCollectionStats = SubjectCollectionStats(
    wish = 680,
    doing = 5120,
    done = 69753,
    onHold = 0,
    dropped = 1200,
)

@Preview
@Composable
private fun PreviewCollectionDataWide() = ProvideCompositionLocalsForPreview {
    Box(Modifier.width(420.dp)) {
        SubjectDetailsDefaults.CollectionData(PreviewCollectionStats)
    }
}

/** 窄屏: 完整文本放不下, 应缩写为 k. */
@Preview
@Composable
private fun PreviewCollectionDataCramped() = ProvideCompositionLocalsForPreview {
    Box(Modifier.width(160.dp)) {
        SubjectDetailsDefaults.CollectionData(PreviewCollectionStats)
    }
}
