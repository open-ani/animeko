/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides.Companion.Bottom
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides.Companion.End
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides.Companion.Left
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides.Companion.Right
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides.Companion.Start
import me.him188.ani.app.ui.foundation.layout.PaddingValuesSides.Companion.Top

/**
 * @see WindowInsets.only
 */
@Stable
fun PaddingValues.only(sides: PaddingValuesSides): PaddingValues = OnlyPaddingValues(this, sides)

private class OnlyPaddingValues(
    private val delegate: PaddingValues,
    private val sides: PaddingValuesSides
) : PaddingValues {
    override fun calculateBottomPadding(): Dp {
        if (sides.hasAny(PaddingValuesSides.Bottom)) {
            return delegate.calculateBottomPadding()
        }
        return 0.dp
    }

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
        if (layoutDirection == LayoutDirection.Ltr) {
            if (sides.hasAny(PaddingValuesSides.AllowLeftInLtr)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        } else {
            if (sides.hasAny(PaddingValuesSides.AllowLeftInRtl)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        }
        return 0.dp
    }

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
        if (layoutDirection == LayoutDirection.Ltr) {
            if (sides.hasAny(PaddingValuesSides.AllowRightInLtr)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        } else {
            if (sides.hasAny(PaddingValuesSides.AllowRightInRtl)) {
                return delegate.calculateLeftPadding(layoutDirection)
            }
        }
        return 0.dp
    }

    override fun calculateTopPadding(): Dp {
        if (sides.hasAny(PaddingValuesSides.Top)) {
            return delegate.calculateTopPadding()
        }
        return 0.dp
    }
}

/**
 * [PaddingValuesSides] is used in [PaddingValues.only] to define which sides of the
 * [PaddingValues] should apply.
 */
@kotlin.jvm.JvmInline
value class PaddingValuesSides private constructor(private val value: Int) {
    /**
     * Returns a [PaddingValuesSides] containing sides defied in [sides] and the
     * sides in `this`.
     */
    operator fun plus(sides: PaddingValuesSides): PaddingValuesSides =
        PaddingValuesSides(value or sides.value)

    internal fun hasAny(sides: PaddingValuesSides): Boolean =
        (value and sides.value) != 0

    override fun toString(): String = "PaddingValuesSides(${valueToString()})"

    private fun valueToString(): String = buildString {
        fun appendPlus(text: String) {
            if (isNotEmpty()) append('+')
            append(text)
        }

        if (value and Start.value == Start.value) appendPlus("Start")
        if (value and Left.value == Left.value) appendPlus("Left")
        if (value and Top.value == Top.value) appendPlus("Top")
        if (value and End.value == End.value) appendPlus("End")
        if (value and Right.value == Right.value) appendPlus("Right")
        if (value and Bottom.value == Bottom.value) appendPlus("Bottom")
    }

    companion object {
        //     _---- allowLeft  in ltr
        //    /
        //    | _--- allowRight in ltr
        //    |/
        //    || _-- allowLeft  in rtl
        //    ||/
        //    ||| _- allowRight in rtl
        //    |||/
        //    VVVV
        //    Mask   = ----
        //
        //    Left   = 1010
        //    Right  = 0101
        //    Start  = 1001
        //    End    = 0110

        internal val AllowLeftInLtr = PaddingValuesSides(1 shl 3)
        internal val AllowRightInLtr = PaddingValuesSides(1 shl 2)
        internal val AllowLeftInRtl = PaddingValuesSides(1 shl 1)
        internal val AllowRightInRtl = PaddingValuesSides(1 shl 0)

        /**
         * Indicates a [PaddingValues] start side, which is left or right
         * depending on [LayoutDirection]. If [LayoutDirection.Ltr], [Start]
         * is the left side. If [LayoutDirection.Rtl], [Start] is the right side.
         *
         * Use [Left] or [Right] if the physical direction is required.
         */
        val Start = AllowLeftInLtr + AllowRightInRtl

        /**
         * Indicates a [PaddingValues] end side, which is left or right
         * depending on [LayoutDirection]. If [LayoutDirection.Ltr], [End]
         * is the right side. If [LayoutDirection.Rtl], [End] is the left side.
         *
         * Use [Left] or [Right] if the physical direction is required.
         */
        val End = AllowRightInLtr + AllowLeftInRtl

        /**
         * Indicates a [PaddingValues] top side.
         */
        val Top = PaddingValuesSides(1 shl 4)

        /**
         * Indicates a [PaddingValues] bottom side.
         */
        val Bottom = PaddingValuesSides(1 shl 5)

        /**
         * Indicates a [PaddingValues] left side. Most layouts will prefer using
         * [Start] or [End] to account for [LayoutDirection].
         */
        val Left = AllowLeftInLtr + AllowLeftInRtl

        /**
         * Indicates a [PaddingValues] right side. Most layouts will prefer using
         * [Start] or [End] to account for [LayoutDirection].
         */
        val Right = AllowRightInLtr + AllowRightInRtl

        /**
         * Indicates a [PaddingValues] horizontal sides. This is a combination of
         * [Left] and [Right] sides, or [Start] and [End] sides.
         */
        val Horizontal = Left + Right

        /**
         * Indicates a [PaddingValues] [Top] and [Bottom] sides.
         */
        val Vertical = Top + Bottom
    }
}

operator fun PaddingValues.plus(
    other: PaddingValues,
): PaddingValues {
    val first = this
    return object : PaddingValues {
        override fun calculateBottomPadding(): Dp = first.calculateBottomPadding() + other.calculateBottomPadding()

        override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
            return first.calculateLeftPadding(layoutDirection) + other.calculateLeftPadding(layoutDirection)
        }

        override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
            return first.calculateRightPadding(layoutDirection) + other.calculateRightPadding(layoutDirection)
        }

        override fun calculateTopPadding(): Dp {
            return first.calculateTopPadding() + other.calculateTopPadding()
        }
    }
}
