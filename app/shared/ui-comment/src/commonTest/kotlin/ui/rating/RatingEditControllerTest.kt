/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.rating

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RatingEditControllerTest {
    private val selfRating = MutableStateFlow(SelfRatingInfo.Empty)
    private val enableEdit = MutableStateFlow(true)

    private fun uiStateOf(controller: RatingEditController) =
        controller.uiStateFlow(RatingInfo.Empty, selfRating, enableEdit)

    @Test
    fun `request edit when not collected shows requires-collection dialog`() = runTest(UnconfinedTestDispatcher()) {
        val controller = RatingEditController(
            isCollected = { false },
            currentSelfRating = { selfRating.value },
            onRate = {},
            backgroundScope,
        )
        val uiState = uiStateOf(controller)

        controller.requestEditRating()
        uiState.first().let {
            assertTrue(it.showRatingRequiresCollectionDialog)
            assertFalse(it.showRatingDialog)
        }

        controller.dismissRatingRequiresCollectionDialog()
        assertFalse(uiState.first().showRatingRequiresCollectionDialog)
    }

    @Test
    fun `request edit when collected opens dialog and submit closes it`() = runTest(UnconfinedTestDispatcher()) {
        var rated: RateRequest? = null
        val rateGate = CompletableDeferred<Unit>()
        val controller = RatingEditController(
            isCollected = { true },
            currentSelfRating = { selfRating.value },
            onRate = { rated = it; rateGate.await() },
            backgroundScope,
        )
        val uiState = uiStateOf(controller)

        controller.requestEditRating()
        assertTrue(uiState.first().showRatingDialog)

        val request = RateRequest(score = 8, comment = "great", isPrivate = false)
        controller.submitRating(request)
        uiState.first().let {
            assertTrue(it.isUpdating, "submitting keeps the dialog open with a loading state")
            assertTrue(it.showRatingDialog)
        }
        assertEquals(request, rated)

        rateGate.complete(Unit)
        uiState.first().let {
            assertFalse(it.isUpdating)
            assertFalse(it.showRatingDialog)
        }
    }

    @Test
    fun `cancel edit closes both dialogs`() = runTest(UnconfinedTestDispatcher()) {
        val controller = RatingEditController(
            isCollected = { true },
            currentSelfRating = { selfRating.value },
            onRate = {},
            backgroundScope,
        )
        val uiState = uiStateOf(controller)

        controller.requestEditRating()
        assertTrue(uiState.first().showRatingDialog)
        controller.cancelEditRating()
        uiState.first().let {
            assertFalse(it.showRatingDialog)
            assertFalse(it.showRatingRequiresCollectionDialog)
        }
    }

    @Test
    fun `ui state reflects upstream self rating and enableEdit`() = runTest(UnconfinedTestDispatcher()) {
        val controller = RatingEditController(
            isCollected = { true },
            currentSelfRating = { selfRating.value },
            onRate = {},
            backgroundScope,
        )
        val uiState = uiStateOf(controller)
        assertNull(uiState.first().selfRatingInfo.comment)

        selfRating.value = SelfRatingInfo.Empty.copy(score = 7, comment = "ok", isPrivate = true)
        enableEdit.value = false
        uiState.first().let {
            assertEquals(7, it.selfRatingInfo.score)
            assertFalse(it.enableEdit)
        }
    }
}
