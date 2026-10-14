/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.subject.details.tracking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test

class TrackingSectionPreviewTest {
    @Test
    fun trackButtonRendersWithoutKoinInPreviews() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                TrackingSection(subjectId = 1)
            }
        }

        onNodeWithTag("trackingAction").assertIsDisplayed()
    }
}
