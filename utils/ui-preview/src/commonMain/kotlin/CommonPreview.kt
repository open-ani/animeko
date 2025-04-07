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
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.annotation.StringDef
import androidx.compose.runtime.Composable

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
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Repeatable
expect annotation class Preview(
    // copy of Android preview annotation to have parameters in the commonMain (it also works since IJ 251 EAP1)
    val name: String = "",
    val group: String = "",
    @IntRange(from = 1) val apiLevel: Int = -1,
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val locale: String = "",
    @FloatRange(from = 0.01) val fontScale: Float = 1f,
    val showSystemUi: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    @all:UiMode val uiMode: Int = 0,
    @all:Device val device: String = "",
    @all:Wallpaper
    val wallpaper: Int = -1,
)

@Suppress("ConstPropertyName")
object Devices {
    // Reference devices
    const val PHONE = "spec:width=411dp,height=891dp"
    const val FOLDABLE = "spec:width=673dp,height=841dp"
    const val TABLET = "spec:width=1280dp,height=800dp,dpi=240"
    const val DESKTOP = "spec:width=1920dp,height=1080dp,dpi=160"

    // TV devices (not adding 4K since it will be very heavy for preview)
    const val TV_720p = "spec:width=1280dp,height=720dp"
    const val TV_1080p = "spec:width=1920dp,height=1080dp"
}

@StringDef(
    Devices.PHONE,
    Devices.FOLDABLE,
    Devices.TABLET,
    Devices.DESKTOP,
    Devices.TV_720p,
    Devices.TV_1080p,
    open = true,
)
internal annotation class Device

///////////////////////////////////////////////////////////////////////////
// Below are copied from Android
///////////////////////////////////////////////////////////////////////////

/** Annotation for defining the wallpaper to use for dynamic theming in the [Preview]. */
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    Wallpapers.NONE,
    Wallpapers.RED_DOMINATED_EXAMPLE,
    Wallpapers.GREEN_DOMINATED_EXAMPLE,
    Wallpapers.BLUE_DOMINATED_EXAMPLE,
    Wallpapers.YELLOW_DOMINATED_EXAMPLE,
)
internal annotation class Wallpaper


/** Annotation of setting uiMode in [Preview]. */
@Suppress("UniqueConstants") // UI_MODE_NIGHT_UNDEFINED == UI_MODE_TYPE_UNDEFINED
@Retention(AnnotationRetention.SOURCE)
@IntDef(
    value =
        [
            Configuration.UI_MODE_TYPE_MASK,
            Configuration.UI_MODE_TYPE_UNDEFINED,
            Configuration.UI_MODE_TYPE_APPLIANCE,
            Configuration.UI_MODE_TYPE_CAR,
            Configuration.UI_MODE_TYPE_DESK,
            Configuration.UI_MODE_TYPE_NORMAL,
            Configuration.UI_MODE_TYPE_TELEVISION,
            Configuration.UI_MODE_TYPE_VR_HEADSET,
            Configuration.UI_MODE_TYPE_WATCH,
            Configuration.UI_MODE_NIGHT_MASK,
            Configuration.UI_MODE_NIGHT_UNDEFINED,
            Configuration.UI_MODE_NIGHT_NO,
            Configuration.UI_MODE_NIGHT_YES,
        ],
)
internal annotation class UiMode

object Configuration {
    /** Constant for [.uiMode]: bits that encode the mode type.  */
    const val UI_MODE_TYPE_MASK: Int = 0x0f

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value indicating that no mode type has been set.  */
    const val UI_MODE_TYPE_UNDEFINED: Int = 0x00

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to
     * [no
     * UI mode]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier) resource qualifier specified.  */
    const val UI_MODE_TYPE_NORMAL: Int = 0x01

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to the
     * [desk]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier)
     * resource qualifier.  */
    const val UI_MODE_TYPE_DESK: Int = 0x02

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to the
     * [car]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier)
     * resource qualifier.  */
    const val UI_MODE_TYPE_CAR: Int = 0x03

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to the
     * [television]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier)
     * resource qualifier.  */
    const val UI_MODE_TYPE_TELEVISION: Int = 0x04

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to the
     * [appliance]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier)
     * resource qualifier.  */
    const val UI_MODE_TYPE_APPLIANCE: Int = 0x05

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to the
     * [watch]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier)
     * resource qualifier.  */
    const val UI_MODE_TYPE_WATCH: Int = 0x06

    /** Constant for [.uiMode]: a [.UI_MODE_TYPE_MASK]
     * value that corresponds to the
     * [vrheadset]({@docRoot}guide/topics/resources/providing-resources.html#UiModeQualifier)
     * resource qualifier.  */
    const val UI_MODE_TYPE_VR_HEADSET: Int = 0x07

    /** Constant for [.uiMode]: bits that encode the night mode.  */
    const val UI_MODE_NIGHT_MASK: Int = 0x30

    /** Constant for [.uiMode]: a [.UI_MODE_NIGHT_MASK]
     * value indicating that no mode type has been set.  */
    const val UI_MODE_NIGHT_UNDEFINED: Int = 0x00

    /** Constant for [.uiMode]: a [.UI_MODE_NIGHT_MASK]
     * value that corresponds to the
     * [notnight]({@docRoot}guide/topics/resources/providing-resources.html#NightQualifier)
     * resource qualifier.  */
    const val UI_MODE_NIGHT_NO: Int = 0x10

    /** Constant for [.uiMode]: a [.UI_MODE_NIGHT_MASK]
     * value that corresponds to the
     * [night]({@docRoot}guide/topics/resources/providing-resources.html#NightQualifier)
     * resource qualifier.  */
    const val UI_MODE_NIGHT_YES: Int = 0x20
}

object Wallpapers {
    /** Default value, representing dynamic theming not enabled. */
    const val NONE = -1

    /** Example wallpaper whose dominant colour is red. */
    const val RED_DOMINATED_EXAMPLE = 0

    /** Example wallpaper whose dominant colour is green. */
    const val GREEN_DOMINATED_EXAMPLE = 1

    /** Example wallpaper whose dominant colour is blue. */
    const val BLUE_DOMINATED_EXAMPLE = 2

    /** Example wallpaper whose dominant colour is yellow. */
    const val YELLOW_DOMINATED_EXAMPLE = 3
}
