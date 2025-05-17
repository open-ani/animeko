/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Immutable
sealed class MediaSelectorSummary {
    /**
     * 用于 UI 比较, 如果 [MediaSelectorSummary] 变了但是 [typeId] 相同就不会有 animation.
     */
    abstract val typeId: Int

    @Immutable
    data class AutoSelecting(
        val queriedSources: List<QueriedSourcePresentation>,
        val estimate: Duration
    ) : MediaSelectorSummary() {
        override val typeId get() = 1
    }

    @Immutable
    data class RequiresManualSelection(
        val queriedSources: List<QueriedSourcePresentation>,
    ) : MediaSelectorSummary() {
        override val typeId get() = 2
    }

    @Immutable
    data class Selected(
        val source: QueriedSourcePresentation,
        val mediaTitle: String,
        val isPerfectMatch: Boolean,
    ) : MediaSelectorSummary() {
        override val typeId get() = 3
    }
}

/**
 * https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=935-6969&t=lhxsUsBY06BworUx-4
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaSelectorSummaryCard(
    summary: MediaSelectorSummary,
    onClickManualSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MediaSelectorColors.calculate(summary)
    val summaryUpdated by rememberUpdatedState(summary)

    val motionScheme = LocalAniMotionScheme.current
    val transitionSpec: AnimatedContentTransitionScope<MediaSelectorSummary>.() -> ContentTransform = {
        val default = motionScheme.animatedContent.standard(this)
        if (initialState.typeId == targetState.typeId) {
            // 如果 typeId 相同, 不会有动画
            ContentTransform(
                EnterTransition.None,
                ExitTransition.None,
                sizeTransform = default.sizeTransform,
            )
        } else {
            default
        }
    }

    val progressIndicatorState = rememberEstimatedProgressIndicatorState()
    val animationState = remember(progressIndicatorState) {
        MediaSelectorSummaryState(summary, progressIndicatorState)
    }
    LaunchedEffect(animationState) {
        animationState.collectSummaryChanges(snapshotFlow { summaryUpdated })
    }

    MediaSelectorSummaryLayout(
        header = {
            val commonModifiers = Modifier.heightIn(min = MediaSelectorSummaryDefaults.headerHeight)
                .fillMaxWidth()

            val listItemColors = ListItemDefaults.colors(
                containerColor = colors.headerContainerColor,
                headlineColor = colors.headerContentColor,
            )

            AnimatedContent(
                animationState.currentSummary,
                transitionSpec = transitionSpec,
            ) { state ->
                val button = @Composable {
                    val buttonLabel = when (state) {
                        is MediaSelectorSummary.AutoSelecting -> "手动选择"
                        is MediaSelectorSummary.RequiresManualSelection -> "手动选择"
                        is MediaSelectorSummary.Selected -> "更换"
                    }

                    OutlinedButton(
                        onClickManualSelect,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            Icons.Rounded.SyncAlt,
                            contentDescription = null,
                            Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(buttonLabel, softWrap = false)
                    }
                }

                val description = @Composable {
                    when (state) {
                        is MediaSelectorSummary.AutoSelecting -> {
                            ListItem(
                                headlineContent = { Text("正在自动选择数据源") },
                                commonModifiers,
                                leadingContent = {
                                    LoadingIndicator(
                                        Modifier.size(24.dp),
                                    )
                                },
                                colors = listItemColors,
                            )
                        }

                        is MediaSelectorSummary.RequiresManualSelection -> {
                            ListItem(
                                headlineContent = { Text("请选择数据源") },
                                commonModifiers,
                                colors = listItemColors,
                            )
                        }

                        is MediaSelectorSummary.Selected -> {
                            ListItem(
                                headlineContent = { Text(state.source.sourceName, softWrap = true, maxLines = 2) },
                                commonModifiers,
                                overlineContent = { Text("数据源") },
                                leadingContent = {
                                    SourceIcon(
                                        state.source,
                                        Modifier.size(24.dp),
                                    )
                                },
                                colors = listItemColors,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f)) {
                        description()
                    }
                    Row(Modifier.padding(end = 16.dp)) {
                        button()
                    }
                }
            }
        },
        headerContainerColor = colors.headerContainerColor,
        modifier = modifier,
        content = {
            AnimatedContent(
                animationState.currentSummary,
                transitionSpec = transitionSpec,
                contentAlignment = Alignment.TopCenter,
            ) { state ->
                when (state) {
                    is MediaSelectorSummary.AutoSelecting,
                    is MediaSelectorSummary.RequiresManualSelection -> {
                        val queriedSources = when (state) {
                            is MediaSelectorSummary.AutoSelecting -> state.queriedSources
                            is MediaSelectorSummary.RequiresManualSelection -> state.queriedSources
                            is MediaSelectorSummary.Selected -> error("not reachable")
                        }

                        Column(Modifier.fillMaxWidth()) {
                            EstimatedLinearProgressIndictorBox(
                                progressIndicatorState,
                                Modifier.requiredHeight(8.dp)
                                    .padding(horizontal = MediaSelectorSummaryDefaults.bodyContentPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                LinearProgressIndicator(
                                    progress = { progressIndicatorState.progress },
                                    Modifier.fillMaxWidth(),
                                    trackColor = MaterialTheme.colorScheme.inverseOnSurface,
                                )
                            }

                            QueriedSources(
                                queriedSources,
                                Modifier.padding(all = MediaSelectorSummaryDefaults.bodyContentPadding),
                            )
                        }
                    }

                    is MediaSelectorSummary.Selected -> {
                        if (!state.isPerfectMatch) { // 只在匹配到可能错误的时候才显示标题
                            Box(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .padding(all = MediaSelectorSummaryDefaults.bodyContentPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                ProvideTextStyleContentColor(
                                    MaterialTheme.typography.bodySmall,
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    Text(
                                        state.mediaTitle,
                                        softWrap = true,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SourceIcon(source: QueriedSourcePresentation, modifier: Modifier) {
    me.him188.ani.app.ui.mediaselect.common.SourceIcon(
        iconUrl = source.sourceIconUrl,
        sourceName = source.sourceName,
        modifier,
    )
}

@Stable
private class MediaSelectorSummaryState(
    initialState: MediaSelectorSummary,
    private val progressIndicatorState: EstimatedProgressIndicatorState,
) {
    var currentSummary: MediaSelectorSummary by mutableStateOf(initialState)
        private set

    suspend fun collectSummaryChanges(flow: Flow<MediaSelectorSummary>) {
        flow
            .distinctUntilChangedBy { summary ->
                // 仅当 typeId 或者 AutoSelecting 的 estimate 变更时, 才重启协程 (动画). 
                // 如果状态里的 queriedSources 属性变了, 不要重启协程 (动画), 否则可能会有轻微的不连贯.
                intArrayOf(
                    summary.typeId,
                    // AutoSelecting.estimate ?: 0
                    (summary as? MediaSelectorSummary.AutoSelecting)?.estimate?.inWholeMilliseconds?.toInt() ?: 0,
                    // No boxing here.
                )
            }
            .collectLatest { state ->
                when (state) {
                    is MediaSelectorSummary.AutoSelecting -> {
                        currentSummary = state
                        progressIndicatorState.animateWithoutFinish(
                            durationMillis = state.estimate.inWholeMilliseconds.toInt(),
                        )
                    }

                    is MediaSelectorSummary.RequiresManualSelection,
                    is MediaSelectorSummary.Selected -> {
                        progressIndicatorState.finish()
                        currentSummary = state
                    }
                }
            }
    }
}


@Immutable
data class QueriedSourcePresentation(
    val sourceName: String,
    val sourceIconUrl: String,
)

@Composable
private fun QueriedSources(
    sources: List<QueriedSourcePresentation>,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "已查找：",
            softWrap = false,
            style = MaterialTheme.typography.labelMedium,
            color = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            maxLines = 1,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            overflow = FlowRowOverflow.expandIndicator {
                val shownItemCount by remember(this) {
                    snapshotFlow { shownItemCount }
                }.collectAsStateWithLifecycle(0)

                Box(Modifier.widthIn(min = 24.dp).heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        remember(sources.size, shownItemCount) {
                            "+${sources.size - shownItemCount}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        softWrap = false,
                    )
                }
            },
        ) {
            for (source in sources) {
                SourceIcon(source, Modifier.size(24.dp))
            }
        }

    }
}

/**
 * @param header ListItem
 * @param content should have 16.dp all around padding
 */
@Composable
private fun MediaSelectorSummaryLayout(
    header: @Composable () -> Unit,
    headerContainerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(modifier) {
        Surface(
            color = headerContainerColor,
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                header()
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

private object MediaSelectorSummaryDefaults {
    val headerHeight = 72.dp
    val bodyContentPadding = 16.dp
}

private data class MediaSelectorColors(
    val headerContainerColor: Color,
    val headerContentColor: Color,
    val bodyContainerColor: Color,
    val bodyContentColor: Color,
) {
    companion object {
        @Composable
        fun calculate(state: MediaSelectorSummary): MediaSelectorColors {
            return when (state) {
                is MediaSelectorSummary.AutoSelecting -> MediaSelectorColors(
                    headerContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    headerContentColor = MaterialTheme.colorScheme.onSurface,
                    bodyContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    bodyContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is MediaSelectorSummary.RequiresManualSelection -> MediaSelectorColors(
                    headerContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    headerContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    bodyContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    bodyContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                is MediaSelectorSummary.Selected -> MediaSelectorColors(
                    headerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    headerContentColor = MaterialTheme.colorScheme.onSurface,
                    bodyContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    bodyContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewMediaSelectorSummarySelectedPerfect() {
    ProvideCompositionLocalsForPreview {
        MediaSelectorSummaryCard(
            summary = MediaSelectorSummary.Selected(
                source = TestQueriedSources[0],
                mediaTitle = TestMediaTitle,
                isPerfectMatch = true,
            ),
            onClickManualSelect = {},
        )
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewMediaSelectorSummarySelected() {
    ProvideCompositionLocalsForPreview {
        MediaSelectorSummaryCard(
            summary = MediaSelectorSummary.Selected(
                source = TestQueriedSources[0],
                mediaTitle = TestMediaTitle,
                isPerfectMatch = false,
            ),
            onClickManualSelect = {},
        )
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
fun PreviewMediaSelectorSummaryAutoSelecting() {
    ProvideCompositionLocalsForPreview {
        var state: MediaSelectorSummary by remember {
            mutableStateOf(
                createTestMediaSelectorSummaryAutoSelecting(),
            )
        }
        MediaSelectorSummaryCard(
            summary = state,
            onClickManualSelect = {},
            Modifier.width(360.dp),
        )
        LaunchedEffect(Unit) {
            delay(5.seconds)
            state = MediaSelectorSummary.Selected(
                TestQueriedSources[0],
                mediaTitle = TestMediaTitle,
                isPerfectMatch = false,
            )
        }

    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
fun PreviewMediaSelectorSummaryAutoSelectingQuickComplete() {
    ProvideCompositionLocalsForPreview {
        var state: MediaSelectorSummary by remember {
            mutableStateOf(
                createTestMediaSelectorSummaryAutoSelecting(),
            )
        }
        MediaSelectorSummaryCard(
            summary = state,
            onClickManualSelect = {},
            Modifier.width(360.dp),
        )
        LaunchedEffect(Unit) {
            delay(2.seconds)
            state = MediaSelectorSummary.Selected(
                TestQueriedSources[0],
                mediaTitle = TestMediaTitle,
                isPerfectMatch = Random.nextBoolean(),
            )
        }

    }
}

private val TestMediaTitle
    get() = "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s"

@TestOnly
fun createTestMediaSelectorSummaryRequiresManualSelection(sources: List<QueriedSourcePresentation>) =
    MediaSelectorSummary.RequiresManualSelection(
        queriedSources = sources,
    )

@TestOnly
fun createTestMediaSelectorSummaryAutoSelecting() = MediaSelectorSummary.AutoSelecting(
    queriedSources = TestQueriedSources,
    estimate = 5.seconds,
)

@TestOnly
internal val TestQueriedSources
    get() = (1..30).map {
        QueriedSourcePresentation("source$it", "https://picsum.photos/200/300")
    }
