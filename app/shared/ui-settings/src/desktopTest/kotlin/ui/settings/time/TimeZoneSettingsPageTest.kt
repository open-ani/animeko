/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.time

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.domain.settings.toTimeZoneOrNull
import me.him188.ani.app.platform.UnsupportedLocationTimeZoneProvider
import me.him188.ani.app.platform.currentSystemTimeZone
import me.him188.ani.app.tools.LocalTimeZone
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_time_zone_current
import me.him188.ani.app.ui.lang.settings_time_zone_id_with_offset
import me.him188.ani.app.ui.lang.settings_time_zone_unsupported_platform
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(TestOnly::class)
class TimeZoneSettingsPageTest {
    private val systemZone = currentSystemTimeZone()
    private val now = Clock.System.now()
    private val catalog = timeZoneCatalog(now, systemZone)
    private val targetZoneId = catalog.first().options.first().zoneId
    private val targetCity = targetZoneId.substringAfter('/')
    private val otherZoneId = catalog.last().options.first().zoneId

    @Test
    fun `follow system is selected by default`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}) } }

        onNodeWithTag(TimeZoneSettingsTestTags.FOLLOW_SYSTEM_ROW).assertIsSelected()
        onNodeWithTag(TimeZoneSettingsTestTags.SELECTED_ROW).assertDoesNotExist()
    }

    @Test
    fun `selecting a zone updates the page and the settings`() = runAniComposeUiTest {
        lateinit var state: SettingsState<UISettings>
        var expectedCurrentZone: String? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                expectedCurrentZone = stringResource(
                    Lang.settings_time_zone_current,
                    stringResource(
                        Lang.settings_time_zone_id_with_offset,
                        targetZoneId,
                        formatUtcOffset(TimeZone.of(targetZoneId).offsetAt(now).totalSeconds),
                    ),
                )
                Host({ state = it })
            }
        }

        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(targetZoneId)).performClick()
        waitUntil { state.value.timeZoneId == targetZoneId }

        assertEquals(targetZoneId, state.value.timeZoneId)
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(targetZoneId)).assertIsSelected()
        onNodeWithTag(TimeZoneSettingsTestTags.FOLLOW_SYSTEM_ROW).assertIsNotSelected()
        // 选中项被置顶后列表会把原来的首项固定在上方, 先滚到顶部再断言
        onNodeWithTag(TimeZoneSettingsTestTags.LIST).performScrollToIndex(0)
        onNodeWithTag(TimeZoneSettingsTestTags.SELECTED_ROW).assertIsSelected()
        onNodeWithTag(TimeZoneSettingsTestTags.CURRENT_ZONE).assertTextEquals(expectedCurrentZone!!)
    }

    @Test
    fun `clock follows the selected time zone`() = runAniComposeUiTest {
        val systemOffsetSeconds = systemZone.offsetAt(now).totalSeconds
        // 与系统时区相差 6 小时以上, 保证小时数必然不同
        val otherZone = catalog.flatMap { it.options }
            .first { abs(it.offsetSeconds - systemOffsetSeconds) >= 6 * 3600 }
        lateinit var state: SettingsState<UISettings>
        setContent { ProvideCompositionLocalsForPreview { Host({ state = it }) } }

        val hourBefore = clockText().substringBefore(':')
        onNodeWithTag(TimeZoneSettingsTestTags.SEARCH).performTextInput(otherZone.zoneId)
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(otherZone.zoneId)).performClick()
        waitUntil { state.value.timeZoneId == otherZone.zoneId }

        val hourAfter = clockText().substringBefore(':')
        assertTrue(hourBefore != hourAfter, "clock should follow the selected zone: $hourBefore -> $hourAfter")
    }

    @Test
    fun `search filters the list and still matches the follow system row`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}) } }

        onNodeWithTag(TimeZoneSettingsTestTags.SEARCH).performTextInput(targetCity)
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(targetZoneId)).assertExists()
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(otherZoneId)).assertDoesNotExist()
        onNodeWithTag(TimeZoneSettingsTestTags.FOLLOW_SYSTEM_ROW).assertDoesNotExist()

        onNodeWithTag(TimeZoneSettingsTestTags.SEARCH).performTextClearance()
        onNodeWithTag(TimeZoneSettingsTestTags.SEARCH).performTextInput(systemZone.id)
        onNodeWithTag(TimeZoneSettingsTestTags.FOLLOW_SYSTEM_ROW).assertExists()
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(targetZoneId)).assertDoesNotExist()
    }

    @Test
    fun `narrow layout stacks clock date and time vertically`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(420.dp, 1600.dp)) } }

        val currentZone = bounds(TimeZoneSettingsTestTags.CURRENT_ZONE)
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val date = bounds(TimeZoneSettingsTestTags.CLOCK_DATE)
        val time = bounds(TimeZoneSettingsTestTags.CLOCK_TIME)
        assertTrue(currentZone.bottom <= clock.top, "clock should be below the current zone line: $currentZone / $clock")
        assertTrue(date.top >= clock.bottom, "date should be below the clock: $date / $clock")
        assertTrue(time.top >= date.bottom, "time should be below the date: $time / $date")
        assertTrue(abs(centerX(clock) - centerX(date)) < 2f, "date should be centered under the clock")
        assertTrue(abs(centerX(clock) - centerX(time)) < 2f, "time should be centered under the clock")
    }

    @Test
    fun `narrow collapsed header puts the clock on the left and the rows on the right`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(420.dp, 1600.dp)) } }
        val expandedHeader = bounds(TimeZoneSettingsTestTags.CLOCK_AREA)

        onNodeWithTag(TimeZoneSettingsTestTags.LIST).performScrollToIndex(1)
        waitForIdle()

        assertNarrowCollapsed()
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val currentZone = bounds(TimeZoneSettingsTestTags.CURRENT_ZONE)
        assertTrue(
            abs(clock.left - currentZone.left) < 1f,
            "the collapsed clock should align with the current zone text: $clock / $currentZone",
        )
        assertTrue(clock.height < expandedHeader.height / 2f, "the collapsed clock should be small: $clock")
        val collapsedHeader = bounds(TimeZoneSettingsTestTags.CLOCK_AREA)
        assertTrue(
            collapsedHeader.height < expandedHeader.height,
            "the collapsed header should be shorter: $collapsedHeader / $expandedHeader",
        )
    }

    @Test
    fun `narrow search field is inset from the page edges`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(420.dp, 1600.dp)) } }

        val search = bounds(TimeZoneSettingsTestTags.SEARCH)
        val list = bounds(TimeZoneSettingsTestTags.LIST)
        val currentZone = bounds(TimeZoneSettingsTestTags.CURRENT_ZONE)
        assertTrue(
            abs(search.left - currentZone.left) < 1f,
            "narrow search should align with the current zone text: $search / $currentZone",
        )
        assertTrue(search.right < list.right - 1f, "narrow search should be narrower than the list: $search / $list")
    }

    @Test
    fun `narrow header collapses after the list is swiped up`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(420.dp, 1600.dp)) } }
        assertNarrowExpanded()

        onNodeWithTag(TimeZoneSettingsTestTags.LIST).performTouchInput { swipeUp() }
        waitForIdle()

        assertNarrowCollapsed()
    }

    @Test
    fun `narrow header expands only after the list is back at the top and is dragged down`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(420.dp, 1600.dp)) } }
        onNodeWithTag(TimeZoneSettingsTestTags.LIST).performTouchInput { swipeUp() }
        waitForIdle()
        assertNarrowCollapsed()

        // 回到顶部本身不恢复, 必须再向下拖拽
        onNodeWithTag(TimeZoneSettingsTestTags.LIST).performScrollToIndex(0)
        waitForIdle()
        assertNarrowCollapsed()

        onNodeWithTag(TimeZoneSettingsTestTags.LIST).performTouchInput { swipeDown() }
        waitForIdle()
        assertNarrowExpanded()
    }

    @Test
    fun `wide layout places the date and time beside the clock`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(900.dp, 1600.dp)) } }

        val date = bounds(TimeZoneSettingsTestTags.CLOCK_DATE)
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val time = bounds(TimeZoneSettingsTestTags.CLOCK_TIME)
        assertTrue(date.left >= clock.right, "date should be right of the clock: $date / $clock")
        assertTrue(time.left >= clock.right, "time should be right of the clock: $time / $clock")
        assertTrue(time.top >= date.bottom, "time should be below the date: $time / $date")
    }

    @Test
    fun `wide layout keeps the clock rows inside the clock height`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(900.dp, 1600.dp)) } }

        assertClockRowsInsideClockHeight()
    }

    @Test
    fun `wide layout keeps a gap between the clock rows when text is enlarged`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Host({}, size = DpSize(900.dp, 1600.dp), fontScale = 2.8f)
            }
        }

        assertClockRowsInsideClockHeight(minGap = 1f)
    }

    @Test
    fun `wide layout time fills the right column at 150 percent display scale`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Host({}, size = DpSize(900.dp, 1600.dp), density = 1.5f)
            }
        }

        assertClockTimeFillsRightColumn()
    }

    @Test
    fun `wide layout time fills the right column at enlarged text scale`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Host({}, size = DpSize(900.dp, 1600.dp), density = 1.5f, fontScale = 1.5f)
            }
        }

        assertClockTimeFillsRightColumn()
    }

    @Test
    fun `collapsing a region hides its entries until the header is clicked again`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}) } }
        val region = catalog.first().region
        val zoneId = catalog.first().options.first().zoneId

        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(zoneId)).assertExists()
        onNodeWithTag(TimeZoneSettingsTestTags.sectionHeader(region)).performClick()
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(zoneId)).assertDoesNotExist()
        // 置顶项不属于任何分组, 不受折叠影响
        onNodeWithTag(TimeZoneSettingsTestTags.FOLLOW_SYSTEM_ROW).assertExists()

        onNodeWithTag(TimeZoneSettingsTestTags.sectionHeader(region)).performClick()
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(zoneId)).assertExists()
    }

    @Test
    fun `querying shows the entries of a collapsed region`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}) } }
        val region = catalog.first().region
        val zoneId = catalog.first().options.first().zoneId

        onNodeWithTag(TimeZoneSettingsTestTags.sectionHeader(region)).performClick()
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(zoneId)).assertDoesNotExist()

        onNodeWithTag(TimeZoneSettingsTestTags.SEARCH).performTextInput(zoneId)
        onNodeWithTag(TimeZoneSettingsTestTags.zoneRow(zoneId)).assertExists()
    }

    @Test
    fun `search field spans the same width as the clock and the list`() = runAniComposeUiTest {
        setContent { ProvideCompositionLocalsForPreview { Host({}, size = DpSize(900.dp, 1600.dp)) } }

        val search = bounds(TimeZoneSettingsTestTags.SEARCH)
        val list = bounds(TimeZoneSettingsTestTags.LIST)
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        assertTrue(abs(search.left - list.left) < 1f, "search should start at the list edge: $search / $list")
        assertTrue(abs(search.right - list.right) < 1f, "search should end at the list edge: $search / $list")
        assertTrue(abs(search.left - clock.left) < 1f, "search should align with the clock: $search / $clock")
    }

    @Test
    fun `desktop reports that location is unavailable and cannot be clicked`() = runAniComposeUiTest {
        var expectedLabel: String? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                expectedLabel = stringResource(Lang.settings_time_zone_unsupported_platform)
                Host({})
            }
        }

        onNodeWithTag(TimeZoneSettingsTestTags.LOCATE_BOX).assertIsNotEnabled()
        onNodeWithTag(TimeZoneSettingsTestTags.LOCATE_LABEL, useUnmergedTree = true)
            .assertTextEquals(expectedLabel!!)
    }

    private fun ComposeUiTest.clockText(): String =
        onNodeWithTag(TimeZoneSettingsTestTags.CLOCK_TIME, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString { it.text }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /**
     * 窄屏展开态: 表盘居中, 日期与时间依次排在表盘下方且都水平居中.
     */
    private fun ComposeUiTest.assertNarrowExpanded() {
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val date = bounds(TimeZoneSettingsTestTags.CLOCK_DATE)
        val time = bounds(TimeZoneSettingsTestTags.CLOCK_TIME)
        assertTrue(date.top >= clock.bottom, "date should be below the clock: $date / $clock")
        assertTrue(time.top >= date.bottom, "time should be below the date: $time / $date")
        assertTrue(abs(centerX(clock) - centerX(date)) < 2f, "date should be centered under the clock: $clock / $date")
        assertTrue(abs(centerX(clock) - centerX(time)) < 2f, "time should be centered under the clock: $clock / $time")
    }

    /**
     * 窄屏折叠态: 小表盘在左上, 日期与时间在表盘右侧上下两行, 两行合计占满表盘高度.
     */
    private fun ComposeUiTest.assertNarrowCollapsed() {
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val date = bounds(TimeZoneSettingsTestTags.CLOCK_DATE)
        val time = bounds(TimeZoneSettingsTestTags.CLOCK_TIME)
        assertTrue(date.left >= clock.right, "date should be right of the clock: $date / $clock")
        assertTrue(time.left >= clock.right, "time should be right of the clock: $time / $clock")
        assertTrue(date.bottom <= time.top, "date should not overlap the time: $date / $time")
        assertTrue(date.top >= clock.top - 1f, "date should start at the clock top: $date / $clock")
        assertTrue(time.bottom <= clock.bottom + 1f, "time should end at the clock bottom: $time / $clock")
        assertTrue(
            abs(date.height + time.height - clock.height) <= 8f,
            "date and time together should fill the clock height: $date / $time / $clock",
        )
    }

    /**
     * 宽屏时钟区的两行共用表盘高度: 两行都在表盘高度之内, 且至少留出 [minGap] 的间隔.
     */
    private fun ComposeUiTest.assertClockRowsInsideClockHeight(minGap: Float = 0f) {
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val date = bounds(TimeZoneSettingsTestTags.CLOCK_DATE)
        val time = bounds(TimeZoneSettingsTestTags.CLOCK_TIME)
        assertTrue(date.top >= clock.top, "date should not rise above the clock: $date / $clock")
        assertTrue(time.bottom <= clock.bottom, "time should stay inside the clock height: $time / $clock")
        assertTrue(time.top - date.bottom >= minGap, "date and time should not crowd each other: $date / $time")
    }

    /**
     * 宽屏右列的时间占满日期下方到表盘底边之间的空间: 高度接近表盘高度, 不重叠, 不越界, 不横向溢出.
     */
    private fun ComposeUiTest.assertClockTimeFillsRightColumn() {
        val clock = bounds(TimeZoneSettingsTestTags.CLOCK)
        val date = bounds(TimeZoneSettingsTestTags.CLOCK_DATE)
        val time = bounds(TimeZoneSettingsTestTags.CLOCK_TIME)
        val content = bounds(TimeZoneSettingsTestTags.LIST)
        assertTrue(date.bottom <= time.top, "date should not overlap the time: $date / $time")
        assertTrue(time.bottom <= clock.bottom, "time should stay inside the clock height: $time / $clock")
        assertTrue(time.left >= clock.right, "time should stay right of the clock: $time / $clock")
        assertTrue(time.right <= content.right, "time should stay inside the right column: $time / $content")
        assertTrue(
            time.height >= clock.height * 0.7f,
            "time should fill most of the clock height: $time / $clock",
        )
    }

    private fun centerX(bounds: Rect): Float = (bounds.left + bounds.right) / 2f

    /**
     * 页面需要从 [LocalTimeZone] 读到当前生效时区, 因此这里像 App 根节点一样按设置提供它.
     */
    @Composable
    private fun Host(
        onState: (SettingsState<UISettings>) -> Unit,
        size: DpSize = DpSize(420.dp, 1600.dp),
        density: Float = 1f,
        fontScale: Float = 1f,
    ) {
        val state = rememberTestSettingsState(UISettings.Default)
        onState(state)
        val uiSettings by state
        val currentSystemZone = remember { currentSystemTimeZone() }
        val timeZone = remember(uiSettings.timeZoneId, currentSystemZone) {
            uiSettings.timeZoneId.toTimeZoneOrNull() ?: currentSystemZone
        }
        CompositionLocalProvider(
            LocalTimeZone provides timeZone,
            LocalDensity provides Density(density, fontScale),
        ) {
            Box(Modifier.requiredSize(size)) {
                TimeZoneSettingsPage(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    locationTimeZoneProvider = UnsupportedLocationTimeZoneProvider,
                )
            }
        }
    }
}
