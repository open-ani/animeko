/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.framework

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.runComposeUiTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 相对于 [runComposeUiTest], 有一些修改:
 * - [ComposeUiTest.waitUntil] 的超时时间更长
 */
expect fun runAniComposeUiTest(
    effectContext: CoroutineContext = EmptyCoroutineContext,
    testBody: AniComposeUiTest.() -> Unit
)

/**
 * @see ComposeUiTest
 */
// compatibility
typealias AniComposeUiTest = ComposeUiTest

// compatibility
inline val AniComposeUiTest.composeUiTest get() = this

/**
 * 截图当前的 UI 并与 resources 目录下的图片 [expectedResource] 进行比较.
 *
 * 在不支持的平台上, 这个操作会被忽略.
 *
 * 传入空字符串可以用来生成截图 (适用于新编写测试用例).
 *
 * @param expectedResource 期望的 `commonTest/resources/` 中的图片文件路径, 例如 "screenshot.png".
 */
@Deprecated(
    "This function may be affected by different window sizes. Use assertScreenshot on node instead.",
    ReplaceWith("onNodeWithTag(\"YOUR_TAG\").assertScreenshot(expectedResource)"),
    level = DeprecationLevel.ERROR,
)
expect fun AniComposeUiTest.assertScreenshot(expectedResource: String)

/**
 * 与 resources 目录下的图片 [expectedResource] 进行比较.
 *
 * 在不支持的平台上, 这个操作会被忽略.
 *
 * 传入空字符串可以用来生成截图 (适用于新编写测试用例).
 *
 * @param expectedResource 期望的 `commonTest/resources/` 中的图片文件路径, 例如 "screenshot.png".
 */
expect fun ImageBitmap.assertScreenshot(expectedResource: String)

/**
 * 截图当前的 UI 并与 resources 目录下的图片 [expectedResource] 进行比较.
 *
 * 在不支持的平台上, 这个操作会被忽略.
 *
 * 传入空字符串可以用来生成截图 (适用于新编写测试用例).
 *
 * @param expectedResource 期望的 `commonTest/resources/` 中的图片文件路径, 例如 "screenshot.png".
 */
expect fun SemanticsNodeInteraction.assertScreenshot(expectedResource: String)
