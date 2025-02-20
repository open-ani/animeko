/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.icons

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AnimekoIconColor = Color(0, 88, 160)

public val Icons.Filled.Animeko: ImageVector
    get() {
        if (_animekoIcon != null) {
            return _animekoIcon!!
        }
        _animekoIcon = Builder(
            name = "AnimekoIcon",
            defaultWidth = 108.0.dp,
            defaultHeight = 108.0.dp,
            viewportWidth = 108.0f,
            viewportHeight = 108.0f,
        ).apply {
            // Single fill path ("fill='white'") containing three subpaths.
            path(
                fill = SolidColor(Color.White),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4.0f,
                pathFillType = PathFillType.NonZero,
            ) {
                // ------------------------------------------------------------------
                // SUBPATH #1
                // "M53.616 11.72 C53.36 12.616 ... L53.616 11.72 Z"
                // ------------------------------------------------------------------
                moveTo(53.616f, 11.72f)
                curveTo(53.36f, 12.616f, 53.104f, 13.512f, 52.848f, 14.408f)
                curveTo(52.656f, 15.24f, 52.464f, 16.04f, 52.272f, 16.808f)
                curveTo(51.696f, 19.816f, 51.152f, 23.272f, 50.64f, 27.176f)
                curveTo(50.128f, 31.016f, 49.712f, 35.048f, 49.392f, 39.272f)
                curveTo(49.072f, 43.432f, 48.912f, 47.528f, 48.912f, 51.56f)
                curveTo(48.912f, 56.552f, 49.168f, 61.16f, 49.68f, 65.384f)
                curveTo(50.192f, 69.544f, 50.896f, 73.416f, 51.792f, 77.0f)
                curveTo(52.752f, 80.52f, 53.776f, 83.88f, 54.864f, 87.08f)
                lineTo(43.824f, 90.44f)
                curveTo(42.864f, 87.56f, 41.936f, 84.136f, 41.04f, 80.168f)
                curveTo(40.208f, 76.2f, 39.504f, 71.944f, 38.928f, 67.4f)
                curveTo(38.352f, 62.792f, 38.064f, 58.152f, 38.064f, 53.48f)
                curveTo(38.064f, 50.28f, 38.16f, 47.08f, 38.352f, 43.88f)
                curveTo(38.608f, 40.616f, 38.864f, 37.416f, 39.12f, 34.28f)
                curveTo(39.376f, 31.08f, 39.664f, 28.04f, 39.984f, 25.16f)
                curveTo(40.368f, 22.216f, 40.688f, 19.56f, 40.944f, 17.192f)
                curveTo(41.008f, 16.296f, 41.072f, 15.336f, 41.136f, 14.312f)
                curveTo(41.2f, 13.224f, 41.2f, 12.264f, 41.136f, 11.432f)
                lineTo(53.616f, 11.72f)
                close()

                // ------------------------------------------------------------------
                // SUBPATH #2
                // "M36.528 22.376 C42.608 22.376 ... 36.528 22.376 Z"
                // ------------------------------------------------------------------
                moveTo(36.528f, 22.376f)
                curveTo(42.608f, 22.376f, 48.304f, 22.248f, 53.616f, 21.992f)
                curveTo(58.992f, 21.736f, 64.144f, 21.288f, 69.072f, 20.648f)
                curveTo(74.064f, 20.008f, 79.024f, 19.08f, 83.952f, 17.864f)
                lineTo(84.048f, 29.0f)
                curveTo(80.72f, 29.576f, 77.04f, 30.12f, 73.008f, 30.632f)
                curveTo(69.04f, 31.08f, 64.88f, 31.464f, 60.528f, 31.784f)
                curveTo(56.24f, 32.104f, 52.016f, 32.36f, 47.856f, 32.552f)
                curveTo(43.696f, 32.68f, 39.792f, 32.744f, 36.144f, 32.744f)
                curveTo(34.48f, 32.744f, 32.592f, 32.712f, 30.48f, 32.648f)
                curveTo(28.432f, 32.584f, 26.384f, 32.52f, 24.336f, 32.456f)
                curveTo(22.352f, 32.328f, 20.624f, 32.232f, 19.152f, 32.168f)
                lineTo(18.864f, 21.032f)
                curveTo(19.952f, 21.16f, 21.52f, 21.352f, 23.568f, 21.608f)
                curveTo(25.616f, 21.8f, 27.792f, 21.992f, 30.096f, 22.184f)
                curveTo(32.464f, 22.312f, 34.608f, 22.376f, 36.528f, 22.376f)
                close()

                // ------------------------------------------------------------------
                // SUBPATH #3
                // "M77.904 36.392 C77.712 36.968 ... 77.904 36.392 Z"
                // ------------------------------------------------------------------
                moveTo(77.904f, 36.392f)
                curveTo(77.712f, 36.968f, 77.424f, 37.768f, 77.04f, 38.792f)
                curveTo(76.656f, 39.816f, 76.272f, 40.872f, 75.888f, 41.96f)
                curveTo(75.568f, 43.048f, 75.312f, 43.912f, 75.12f, 44.552f)
                curveTo(73.264f, 50.312f, 70.992f, 55.592f, 68.304f, 60.392f)
                curveTo(65.68f, 65.128f, 62.832f, 69.288f, 59.76f, 72.872f)
                curveTo(56.688f, 76.392f, 53.648f, 79.272f, 50.64f, 81.512f)
                curveTo(47.504f, 83.88f, 43.92f, 85.96f, 39.888f, 87.752f)
                curveTo(35.856f, 89.48f, 31.792f, 90.344f, 27.696f, 90.344f)
                curveTo(25.392f, 90.344f, 23.248f, 89.864f, 21.264f, 88.904f)
                curveTo(19.28f, 87.944f, 17.68f, 86.44f, 16.464f, 84.392f)
                curveTo(15.312f, 82.28f, 14.736f, 79.624f, 14.736f, 76.424f)
                curveTo(14.736f, 72.968f, 15.44f, 69.576f, 16.848f, 66.248f)
                curveTo(18.256f, 62.92f, 20.208f, 59.752f, 22.704f, 56.744f)
                curveTo(25.2f, 53.736f, 28.112f, 51.08f, 31.44f, 48.776f)
                curveTo(34.768f, 46.408f, 38.352f, 44.552f, 42.192f, 43.208f)
                curveTo(45.328f, 42.056f, 48.752f, 41.128f, 52.464f, 40.424f)
                curveTo(56.24f, 39.72f, 59.952f, 39.368f, 63.6f, 39.368f)
                curveTo(69.552f, 39.368f, 74.864f, 40.456f, 79.536f, 42.632f)
                curveTo(84.272f, 44.808f, 87.984f, 47.784f, 90.672f, 51.56f)
                curveTo(93.36f, 55.336f, 94.704f, 59.752f, 94.704f, 64.808f)
                curveTo(94.704f, 68.2f, 94.16f, 71.528f, 93.072f, 74.792f)
                curveTo(91.984f, 77.992f, 90.192f, 80.968f, 87.696f, 83.72f)
                curveTo(85.264f, 86.472f, 82.032f, 88.84f, 78.0f, 90.824f)
                curveTo(73.968f, 92.808f, 69.008f, 94.248f, 63.12f, 95.144f)
                lineTo(56.784f, 85.064f)
                curveTo(62.928f, 84.36f, 67.92f, 82.984f, 71.76f, 80.936f)
                curveTo(75.6f, 78.824f, 78.384f, 76.296f, 80.112f, 73.352f)
                curveTo(81.904f, 70.408f, 82.8f, 67.368f, 82.8f, 64.232f)
                curveTo(82.8f, 61.416f, 82.032f, 58.888f, 80.496f, 56.648f)
                curveTo(79.024f, 54.408f, 76.784f, 52.616f, 73.776f, 51.272f)
                curveTo(70.832f, 49.864f, 67.184f, 49.16f, 62.832f, 49.16f)
                curveTo(58.48f, 49.16f, 54.576f, 49.64f, 51.12f, 50.6f)
                curveTo(47.728f, 51.56f, 44.88f, 52.584f, 42.576f, 53.672f)
                curveTo(39.376f, 55.208f, 36.528f, 57.16f, 34.032f, 59.528f)
                curveTo(31.536f, 61.832f, 29.584f, 64.264f, 28.176f, 66.824f)
                curveTo(26.768f, 69.384f, 26.064f, 71.752f, 26.064f, 73.928f)
                curveTo(26.064f, 75.464f, 26.416f, 76.648f, 27.12f, 77.48f)
                curveTo(27.824f, 78.248f, 28.944f, 78.632f, 30.48f, 78.632f)
                curveTo(32.784f, 78.632f, 35.44f, 77.896f, 38.448f, 76.424f)
                curveTo(41.456f, 74.888f, 44.432f, 72.744f, 47.376f, 69.992f)
                curveTo(50.832f, 66.792f, 54.096f, 62.952f, 57.168f, 58.472f)
                curveTo(60.24f, 53.992f, 62.768f, 48.424f, 64.752f, 41.768f)
                curveTo(64.944f, 41.128f, 65.136f, 40.296f, 65.328f, 39.272f)
                curveTo(65.52f, 38.248f, 65.68f, 37.224f, 65.808f, 36.2f)
                curveTo(66.0f, 35.112f, 66.128f, 34.248f, 66.192f, 33.608f)
                lineTo(77.904f, 36.392f)
                close()
            }
        }
            .build()
        return _animekoIcon!!
    }

private var _animekoIcon: ImageVector? = null
