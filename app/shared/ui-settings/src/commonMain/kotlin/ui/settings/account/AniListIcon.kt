/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 * AniList mark geometry from Mihon's Apache-2.0 brand_anilist.xml.
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

@Composable
fun AniListIcon(modifier: Modifier = Modifier) {
    val a = remember {
        PathParser().parsePathString(
            "m63.6,43.6 l-36.8,105h28.6l6.2,-18.2h31.2l6.1,18.1h28.5L90.7,43.6zM68.2,107.1 L77.1,78.1 86.9,107.1z",
        ).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    val l = remember {
        PathParser().parsePathString(
            "M127,148.5h32.5q6.3,0 6.4,-6.3v-14q-0.1,-6.4 -6.4,-6.5h-37.6V50q-0.1,-6.3 -6.4,-6.4h-14q-6.3,0.1 -6.4,6.4v7.8c-3.2,-9.8 31.8,90.7 32,90.7",
        ).toPath().apply { fillType = PathFillType.EvenOdd }
    }
    Canvas(modifier.size(40.dp)) {
        withTransform({ scale(size.width / 192f, size.height / 192f, Offset.Zero) }) {
            drawRect(Color(0xFF1F2631), size = Size(192f, 192f))
            drawPath(a, Color.White)
            drawPath(l, Color(0xFF02A9FF))
        }
    }
}
