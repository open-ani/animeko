/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Modifier that ensures a default focusable element exists on TV platforms.
 * 
 * This should be applied to the root composable of any screen/page to ensure
 * that when the page is displayed, there is always a focusable element available.
 * 
 * On TV platforms:
 * - Creates a FocusRequester and applies it to the element
 * - Automatically requests focus when the element is composed
 * - Makes the element focusable
 * 
 * On non-TV platforms:
 * - Does nothing (returns the modifier unchanged)
 * 
 * Usage:
 * ```
 * Box(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .tvDefaultFocus()
 * ) {
 *     // Your content
 * }
 * ```
 */
fun Modifier.tvDefaultFocus(): Modifier = composed {
    val isTv = LocalPlatform.current.isTv()
    
    if (isTv) {
        val focusRequester = remember { FocusRequester() }
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        
        this
            .focusRequester(focusRequester)
            .focusable()
    } else {
        this
    }
}

/**
 * Composable wrapper that ensures default focus on TV platforms.
 * 
 * This is a convenience wrapper around tvDefaultFocus() modifier.
 * It wraps the content in a focusable container that automatically
 * receives focus on TV platforms.
 * 
 * Usage:
 * ```
 * TvDefaultFocusContainer {
 *     // Your screen content
 * }
 * ```
 */
@Composable
fun TvDefaultFocusContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.tvDefaultFocus()
    ) {
        content()
    }
}
