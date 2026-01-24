/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.isTv

@Composable
fun BackNavigationIconButton(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalPlatform.current.isTv()
    val focusRequester = remember { FocusRequester() }
    
    // Request focus on TV platforms when this button is composed
    if (isTv) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
    
    TopAppBarActionButton(
        onNavigateBack,
        modifier.then(
            if (isTv) Modifier.focusRequester(focusRequester)
            else Modifier
        ),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.ArrowBack,
            null,
        )
    }
}

@Composable
fun TopAppBarActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick,
        modifier,
//        Modifier.offset(x = (-8).dp, y = (-8).dp).width(36.dp + 16.dp).height(36.dp + 16.dp)
    ) { // 让可点击区域大一点, 更方便
        Box(Modifier.size(24.dp)) {
            content()
        }
    }
}
