/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

/**
 * Standard delay before requesting focus after a UI change (e.g. popup open, layout visibility change).
 * This ensures that the UI has been laid out and is ready to receive focus.
 * 
 * Commonly used on Android TV.
 */
const val FOCUS_REQ_DELAY_MILLIS = 300L

/**
 * Duration threshold to distinguish a short click from a long click on TV D-pad center key.
 */
const val TV_LONG_CLICK_DURATION_MILLIS = 500L
