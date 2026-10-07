/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.subject.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.him188.ani.app.data.models.preference.CollectionSortOrder
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.collection_sort
import me.him188.ani.app.ui.lang.collection_sort_air_date
import me.him188.ani.app.ui.lang.collection_sort_name
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionSortButtonTest {
    @Test
    fun choosingOrderDismissesMenuAndAllowsAnotherChoice() = runAniComposeUiTest {
        var order by mutableStateOf(CollectionSortOrder.LAST_UPDATED)
        var button = ""
        var name = ""
        var airDate = ""
        setContent {
            button = stringResource(Lang.collection_sort)
            name = stringResource(Lang.collection_sort_name)
            airDate = stringResource(Lang.collection_sort_air_date)
            CollectionSortButton(order) { order = it }
        }
        waitForIdle()
        onNodeWithContentDescription(button).performClick()
        onNodeWithText(name).performClick()
        runOnIdle { assertEquals(CollectionSortOrder.NAME, order) }
        onNodeWithText(name).assertDoesNotExist()
        onNodeWithContentDescription(button).performClick()
        onNodeWithText(name).assertIsSelected()
        onNodeWithText(airDate).performClick()
        runOnIdle { assertEquals(CollectionSortOrder.AIR_DATE, order) }
        onNodeWithText(airDate).assertDoesNotExist()
    }
}
