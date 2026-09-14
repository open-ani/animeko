/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import me.him188.ani.app.ui.framework.AniComposeUiTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun AniComposeUiTest.assertDetailsEndPaddingAligned(pageTag: String) {
    val padding = onNodeWithTag("tv-details-end-padding", useUnmergedTree = true)
        .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused)).fetchSemanticsNode()
    val viewport = onNodeWithTag(pageTag).fetchSemanticsNode().boundsInRoot
    assertTrue(padding.size.height > 0, "The page must have separate trailing space")
    // boundsInRoot is clipped: use the actual layout edge to detect an offscreen anchor.
    assertEquals(viewport.bottom, padding.positionInRoot.y + padding.size.height, 1f,
        "The end of the trailing space must align with the viewport bottom")
}
