/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package org.jetbrains.compose.ui.tooling.preview


/**
 * A MultiPreview annotation for displaying a @[androidx.compose.runtime.Composable] method using the screen sizes of five
 * different reference devices.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone", device = Devices.PHONE, showSystemUi = true)
@Preview(
    name = "Phone - Landscape",
    device = "spec:width=411dp,height=891dp,orientation=landscape,dpi=420",
    showSystemUi = true,
)
@Preview(name = "Unfolded Foldable", device = Devices.FOLDABLE, showSystemUi = true)
@Preview(name = "Tablet", device = Devices.TABLET, showSystemUi = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showSystemUi = true)
annotation class PreviewScreenSizes

/**
 * A MultiPreview annotation for desplaying a @[androidx.compose.runtime.Composable] method using seven standard font sizes.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "85%", fontScale = 0.85f)
@Preview(name = "100%", fontScale = 1.0f)
@Preview(name = "115%", fontScale = 1.15f)
@Preview(name = "130%", fontScale = 1.3f)
@Preview(name = "150%", fontScale = 1.5f)
@Preview(name = "180%", fontScale = 1.8f)
@Preview(name = "200%", fontScale = 2f)
annotation class PreviewFontScale

/**
 * A MultiPreview annotation for desplaying a @[Composable] method using light and dark themes.
 *
 * Note that the app theme should support dark and light modes for these previews to be different.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class PreviewLightDark

/**
 * A MultiPreview annotation for desplaying a @[Composable] method using four different wallpaper
 * colors.
 *
 * Note that the app should use a dynamic theme for these previews to be different.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Red", wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE)
@Preview(name = "Blue", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Preview(name = "Green", wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE)
@Preview(name = "Yellow", wallpaper = Wallpapers.YELLOW_DOMINATED_EXAMPLE)
annotation class PreviewDynamicColors
