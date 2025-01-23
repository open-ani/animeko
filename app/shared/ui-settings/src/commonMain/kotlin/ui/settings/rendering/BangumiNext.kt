/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.rendering

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.EvenOdd
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

public val Icons.Filled.BangumiNext: ImageVector
    get() {
        if (_bangumiNext != null) {
            return _bangumiNext!!
        }
        _bangumiNext = Builder(
            name = "MyCuteIcon",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 145.0f,
            viewportHeight = 145.0f,
        ).apply {
            //
            // Main pink path (fill="#F09199").
            // Using pathFillType = EvenOdd because the original used fill-rule="evenodd".
            // If you prefer non-zero winding, you can switch to NonZero.
            //
            path(
                fill = SolidColor(Color(0xFFF09199)),
                stroke = null,
                strokeLineWidth = 0.0f,
                strokeLineCap = Butt,
                strokeLineJoin = Miter,
                strokeLineMiter = 4.0f,
                pathFillType = EvenOdd,
            ) {
                // Below is a direct translation of the large "d" attribute:
                // M 84.803 38.7817 ...
                // into moveTo / curveTo / lineTo calls.
                // Note: The path is quite complex. Shown here is a version
                // converted via an automated SVG-to-Compose-path tool or
                // by parsing the d-attribute carefully.

                moveTo(84.803f, 38.7817f)
                curveTo(84.803f, 38.7817f, 72.5f, 34.332f, 60.197f, 38.7817f)
                curveTo(60.197f, 38.7817f, 61.0758f, 40.5616f, 61.9545f, 46.3463f)
                curveTo(60.197f, 40.5616f, 48.7727f, 20.9828f, 34.7121f, 12.0833f)
                curveTo(34.7121f, 42.5005f, 49.879f, 49.3166f, 52.38f, 50.2338f)
                lineTo(44.8182f, 47.6812f)
                curveTo(34.7121f, 50.351f, 21.9697f, 83.2791f, 14.5f, 152.25f)
                lineTo(54.9242f, 152.096f)
                curveTo(54.9242f, 152.096f, 54.9242f, 128.666f, 67.2272f, 128.666f)
                lineTo(67.2272f, 123.772f)
                curveTo(67.2272f, 123.772f, 50.0909f, 123.772f, 45.2575f, 111.312f)
                lineTo(45.7234f, 139.791f)
                curveTo(43.9394f, 128.666f, 43.9394f, 108.198f, 43.9394f, 108.198f)
                curveTo(43.9394f, 107.916f, 43.9369f, 107.563f, 43.934f, 107.146f)
                curveTo(43.8821f, 99.6739f, 43.6875f, 71.6845f, 54.9242f, 63.2553f)
                curveTo(49.4318f, 69.7074f, 48.3333f, 83.2791f, 48.3333f, 83.2791f)
                curveTo(49.4318f, 85.9489f, 53.3863f, 86.6164f, 53.3863f, 86.6164f)
                curveTo(53.3863f, 86.6164f, 54.2651f, 83.0566f, 56.6818f, 80.1643f)
                curveTo(56.6818f, 80.1643f, 56.0227f, 89.9537f, 70.7424f, 89.9537f)
                curveTo(70.7424f, 89.9537f, 69.4242f, 86.8389f, 70.303f, 83.7241f)
                curveTo(70.303f, 83.7241f, 72.9394f, 88.6188f, 80.4091f, 88.1738f)
                curveTo(80.4091f, 88.1738f, 81.9469f, 85.504f, 81.7272f, 83.2791f)
                curveTo(81.7272f, 83.2791f, 84.5833f, 86.6164f, 89.6363f, 86.8389f)
                curveTo(89.6363f, 86.8389f, 90.0757f, 84.3915f, 90.0757f, 82.3891f)
                curveTo(90.0757f, 82.3891f, 92.9318f, 86.6164f, 97.5454f, 86.6164f)
                curveTo(96.8863f, 77.7169f, 94.0303f, 70.8198f, 90.0757f, 63.2553f)
                curveTo(101.141f, 74.4611f, 101.096f, 93.1011f, 101.064f, 106.191f)
                curveTo(101.062f, 106.876f, 101.061f, 107.545f, 101.061f, 108.198f)
                curveTo(101.061f, 108.198f, 101.061f, 128.666f, 99.303f, 139.791f)
                lineTo(99.7424f, 111.312f)
                curveTo(94.909f, 123.772f, 77.7727f, 123.772f, 77.7727f, 123.772f)
                lineTo(77.7727f, 128.666f)
                curveTo(90.0757f, 128.666f, 90.0757f, 152.25f, 90.0757f, 152.25f)
                lineTo(130.5f, 152.25f)
                curveTo(123.03f, 83.2791f, 110.288f, 50.351f, 100.182f, 47.6812f)
                lineTo(92.7731f, 50.1821f)
                curveTo(95.6821f, 49.0938f, 110.288f, 42.1329f, 110.288f, 12.0833f)
                curveTo(96.2273f, 20.9828f, 84.803f, 40.5616f, 82.6061f, 46.3463f)
                curveTo(83.9242f, 40.5616f, 84.803f, 38.7817f, 84.803f, 38.7817f)
                close()

                // The small bits near the bottom:
                // M92.2727 50.351C92.2727 50.351 92.4514 50.3025 ...
                // In this particular SVG, those sub-motions are effectively 
                // the same overall path. Sometimes they’re separate subpaths.

                moveTo(92.2727f, 50.351f)
                curveTo(92.2727f, 50.351f, 92.4514f, 50.3025f, 92.7731f, 50.1821f)
                lineTo(92.2727f, 50.351f)
                close()

                moveTo(52.7273f, 50.351f)
                lineTo(52.38f, 50.2338f)
                curveTo(52.6048f, 50.3162f, 52.7273f, 50.351f, 52.7273f, 50.351f)
                close()

                // … Subpath for the details around 89.5087 / 98.4082, etc.
                moveTo(53.606f, 89.5087f)
                curveTo(62.8333f, 89.7312f, 65.6894f, 97.0733f, 65.9091f, 98.4082f)
                lineTo(64.5909f, 97.5182f)
                curveTo(64.5909f, 97.5182f, 64.1515f, 110.645f, 55.803f, 110.867f)
                curveTo(47.3924f, 111.092f, 47.4341f, 101.506f, 47.4529f, 97.174f)
                curveTo(47.4537f, 96.9813f, 47.4545f, 96.7989f, 47.4545f, 96.6283f)
                curveTo(46.5757f, 98.8532f, 45.6969f, 104.193f, 45.6969f, 104.193f)
                curveTo(45.6969f, 99.5206f, 46.7954f, 89.3445f, 53.606f, 89.5087f)
                close()

                moveTo(79.0909f, 98.4082f)
                curveTo(79.3106f, 97.0733f, 82.1666f, 89.7312f, 91.3939f, 89.5087f)
                curveTo(98.2045f, 89.3445f, 99.303f, 99.5206f, 99.303f, 104.193f)
                curveTo(99.303f, 104.193f, 98.4242f, 98.8532f, 97.5454f, 96.6283f)
                curveTo(97.5454f, 96.7989f, 97.5462f, 96.9813f, 97.5471f, 97.174f)
                curveTo(97.5659f, 101.506f, 97.6075f, 111.092f, 89.1969f, 110.867f)
                curveTo(80.8484f, 110.645f, 80.4091f, 97.5182f, 80.4091f, 97.5182f)
                lineTo(79.0909f, 98.4082f)
                close()

                moveTo(55.4126f, 105.973f)
                curveTo(55.3617f, 105.973f, 55.3111f, 105.964f, 55.2632f, 105.946f)
                curveTo(55.1708f, 105.912f, 55.0925f, 105.847f, 55.0407f, 105.762f)
                curveTo(54.9889f, 105.678f, 54.9667f, 105.578f, 54.9776f, 105.479f)
                lineTo(55.316f, 102.324f)
                lineTo(52.7279f, 102.324f)
                curveTo(52.6481f, 102.324f, 52.5698f, 102.302f, 52.5013f, 102.261f)
                curveTo(52.4328f, 102.219f, 52.3769f, 102.159f, 52.3394f, 102.088f)
                curveTo(52.3019f, 102.017f, 52.2843f, 101.936f, 52.2885f, 101.856f)
                curveTo(52.2927f, 101.775f, 52.3185f, 101.697f, 52.3632f, 101.63f)
                lineTo(55.8301f, 96.3792f)
                curveTo(55.8849f, 96.297f, 55.9652f, 96.2357f, 56.0584f, 96.2051f)
                curveTo(56.1515f, 96.1744f, 56.2521f, 96.1762f, 56.3441f, 96.2101f)
                curveTo(56.4326f, 96.2433f, 56.5082f, 96.3046f, 56.5596f, 96.3847f)
                curveTo(56.6111f, 96.4649f, 56.6357f, 96.5596f, 56.6297f, 96.655f)
                lineTo(56.2914f, 99.8321f)
                lineTo(58.8794f, 99.8321f)
                curveTo(58.9593f, 99.832f, 59.0376f, 99.8539f, 59.1061f, 99.8955f)
                curveTo(59.1745f, 99.9371f, 59.2305f, 99.9967f, 59.268f, 100.068f)
                curveTo(59.3055f, 100.139f, 59.3231f, 100.22f, 59.3189f, 100.301f)
                curveTo(59.3147f, 100.381f, 59.2888f, 100.459f, 59.2441f, 100.526f)
                lineTo(55.7773f, 105.777f)
                curveTo(55.737f, 105.837f, 55.6827f, 105.887f, 55.6191f, 105.921f)
                curveTo(55.5555f, 105.955f, 55.4846f, 105.973f, 55.4126f, 105.973f)
                close()

                moveTo(89.246f, 105.973f)
                curveTo(89.195f, 105.973f, 89.1445f, 105.964f, 89.0966f, 105.946f)
                curveTo(89.0042f, 105.912f, 88.9258f, 105.847f, 88.874f, 105.762f)
                curveTo(88.8223f, 105.678f, 88.8f, 105.578f, 88.811f, 105.479f)
                lineTo(89.1493f, 102.324f)
                lineTo(86.5613f, 102.324f)
                curveTo(86.4814f, 102.324f, 86.4031f, 102.302f, 86.3346f, 102.261f)
                curveTo(86.2662f, 102.219f, 86.2102f, 102.159f, 86.1727f, 102.088f)
                curveTo(86.1352f, 102.017f, 86.1176f, 101.936f, 86.1218f, 101.856f)
                curveTo(86.126f, 101.775f, 86.1519f, 101.697f, 86.1966f, 101.63f)
                lineTo(89.6634f, 96.3792f)
                curveTo(89.7182f, 96.297f, 89.7986f, 96.2357f, 89.8917f, 96.2051f)
                curveTo(89.9849f, 96.1744f, 90.0854f, 96.1762f, 90.1775f, 96.2101f)
                curveTo(90.266f, 96.2433f, 90.3415f, 96.3046f, 90.393f, 96.3847f)
                curveTo(90.4444f, 96.4649f, 90.469f, 96.5596f, 90.4631f, 96.655f)
                lineTo(90.1248f, 99.8321f)
                lineTo(92.7128f, 99.8321f)
                curveTo(92.7926f, 99.832f, 92.871f, 99.8539f, 92.9394f, 99.8955f)
                curveTo(93.0079f, 99.9371f, 93.0639f, 99.9967f, 93.1013f, 100.068f)
                curveTo(93.1388f, 100.139f, 93.1564f, 100.22f, 93.1522f, 100.301f)
                curveTo(93.148f, 100.381f, 93.1222f, 100.459f, 93.0775f, 100.526f)
                lineTo(89.6107f, 105.777f)
                curveTo(89.5704f, 105.837f, 89.516f, 105.887f, 89.4524f, 105.921f)
                curveTo(89.3888f, 105.955f, 89.3179f, 105.973f, 89.246f, 105.973f)
                close()
            }
        }
            .build()
        return _bangumiNext!!
    }

private var _bangumiNext: ImageVector? = null
