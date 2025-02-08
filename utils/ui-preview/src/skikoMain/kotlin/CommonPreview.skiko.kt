/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package org.jetbrains.compose.ui.tooling.preview

import androidx.annotation.FloatRange
import androidx.annotation.IntRange

/**
 * [Preview] can be applied to either of the following:
 * - @[Composable] methods with no parameters to show them in the Android Studio preview.
 * - Annotation classes, that could then be used to annotate @[Composable] methods or other
 *   annotation classes, which will then be considered as indirectly annotated with that [Preview].
 *
 * The annotation contains a number of parameters that allow to define the way the @[Composable]
 * will be rendered within the preview.
 *
 * The passed parameters are only read by Studio when rendering the preview.
 *
 * @param name Display name of this preview allowing to identify it in the panel.
 * @param group Group name for this @[Preview]. This allows grouping them in the UI and display only
 *   one or more of them.
 * @param apiLevel API level to be used when rendering the annotated @[Composable]
 * @param widthDp Max width in DP the annotated @[Composable] will be rendered in. Use this to
 *   restrict the size of the rendering viewport.
 * @param heightDp Max height in DP the annotated @[Composable] will be rendered in. Use this to
 *   restrict the size of the rendering viewport.
 * @param locale Current user preference for the locale, corresponding to
 *   [locale](https://d.android.com/guide/topics/resources/providing-resources.html#LocaleQualifier)
 *   resource qualifier. By default, the `default` folder will be used. To preview an RTL layout use
 *   a locale that uses right to left script, such as `ar` (or the `ar-rXB` pseudo locale).
 * @param fontScale User preference for the scaling factor for fonts, relative to the base density
 *   scaling.
 * @param showSystemUi If true, the status bar and action bar of the device will be displayed.
 *   The @[Composable] will be render in the context of a full activity.
 * @param showBackground If true, the @[Composable] will use a default background color.
 * @param backgroundColor The 32-bit ARGB color int for the background or 0 if not set
 * @param uiMode Bit mask of the ui mode as per [android.content.res.Configuration.uiMode]
 * @param device Device string indicating the device to use in the preview. See the available
 *   devices in [Devices].
 * @param wallpaper Integer defining which wallpaper from those available in Android Studio to use
 *   for dynamic theming.
 */
@Repeatable
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
@Suppress("NO_ACTUAL_FOR_EXPECT", "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS")
actual annotation class Preview(
    actual val name: String = "",
    actual val group: String = "",
    @IntRange(from = 1) actual val apiLevel: Int = -1,
    actual val widthDp: Int = -1,
    actual val heightDp: Int = -1,
    actual val locale: String = "",
    @FloatRange(from = 0.01) actual val fontScale: Float = 1f,
    actual val showSystemUi: Boolean = false,
    actual val showBackground: Boolean = false,
    actual val backgroundColor: Long = 0,
    actual val uiMode: Int = 0,
    actual val device: String = "",
    actual val wallpaper: Int = -1,
)
