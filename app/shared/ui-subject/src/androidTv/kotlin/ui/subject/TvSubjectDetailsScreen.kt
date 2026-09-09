/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.leanback.ui.subject.details.TvDetailsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.ui.comment.UIComment
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_characters
import me.him188.ani.app.ui.lang.subject_details_empty
import me.him188.ani.app.ui.lang.subject_details_episodes
import me.him188.ani.app.ui.lang.subject_details_no_episodes
import me.him188.ani.app.ui.lang.subject_details_related_subjects
import me.him188.ani.app.ui.lang.subject_details_staff
import me.him188.ani.app.ui.lang.subject_details_view_all
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusExit
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.widgets.TvHeroButton
import me.him188.ani.leanback.ui.foundation.widgets.tvShellBackgroundColor
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdrop
import me.him188.ani.leanback.ui.subject.components.LocalTvDetailsBackdropImage
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdropImage
import me.him188.ani.leanback.ui.subject.components.TvDetailsBringIntoViewSpec
import me.him188.ani.leanback.ui.subject.components.TvDetailsBrowseRowLayout
import me.him188.ani.leanback.ui.subject.components.TvDetailsScrollAnchors
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsPageLayout
import me.him188.ani.leanback.ui.subject.components.tvDetailsScrollSection
import me.him188.ani.leanback.ui.foundation.focus.tvBackKey
import me.him188.ani.leanback.ui.foundation.layout.rememberTvOptionAnchors
import me.him188.ani.leanback.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.leanback.ui.foundation.layout.tvOptionAnchor
import me.him188.ani.leanback.ui.subject.details.TvCharacterCard
import me.him188.ani.leanback.ui.subject.details.TvDetailsCollectionAction
import me.him188.ani.leanback.ui.subject.details.TvDetailsRatingAction
import me.him188.ani.leanback.ui.subject.details.TvDetailsHeroSection
import me.him188.ani.leanback.ui.subject.details.TvDetailsLists
import me.him188.ani.leanback.ui.subject.details.TvEpisodeCard
import me.him188.ani.leanback.ui.subject.details.TvRelatedSubjectCard
import me.him188.ani.leanback.ui.subject.details.TvStaffCard
import me.him188.ani.leanback.ui.subject.details.TvSubjectDetailsPanels
import me.him188.ani.leanback.ui.subject.details.TvSubjectInformationSection
import me.him188.ani.leanback.ui.subject.details.TvSubjectComments
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsPanelKind
import me.him188.ani.leanback.ui.subject.presentation.TvSubjectPresentationState
import me.him188.ani.leanback.ui.subject.presentation.detailsFocusFallback
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvSubjectDetailsScreen(
    uiState: TvSubjectDetailsUiState,
    onIntent: (TvSubjectDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
    presentation: TvSubjectPresentationState = rememberSaveable(saver = TvSubjectPresentationState.Saver) { TvSubjectPresentationState() },
    onOpenUrl: (String) -> Unit = {},
) {
    val backGeneration = presentation.generation
    val closePanel = { presentation.closeIfCurrent(backGeneration) }
    BackHandler(presentation.panel != null, onBack = closePanel)
    Box(modifier.fillMaxSize().background(tvShellBackgroundColor())
        .tvBackKey(enabled = presentation.panel != null, onBack = closePanel)
        .testTag("tv-subject-details")) {
        val content = uiState.content
        if (content == null) {
            TvDetailsLoadingOrError(uiState.error, { onIntent(TvSubjectDetailsIntent.Retry) })
        } else {
            TvSubjectDetailsContent(uiState, presentation, onIntent, onOpenUrl)
        }
    }
}

@Composable
private fun TvDetailsLoadingOrError(error: LoadError?, onRetry: () -> Unit) {
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    var laidOut by remember(error) { mutableStateOf(false) }
    LaunchedEffect(error) {
        focus.requestPrepared {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { laidOut && window.isWindowFocused }.first { it }
            TvDetailsKey("entry")
        }
    }
    Column(
        Modifier.fillMaxSize().tvFocusNavSignal(focus).padding(48.dp)
            .onGloballyPositioned { laidOut = true },
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (error == null) {
            Box(Modifier.fillMaxWidth(.55f).height(52.dp).placeholder(true))
            Box(Modifier.weight(1f).fillMaxWidth().placeholder(true))
            Row(Modifier.tvFocusAnchor(focus, TvDetailsKey("entry")).focusable().testTag("tv-details-loading"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(Lang.foundation_loading), color = TvSubjectDetailsDefaults.SecondaryContent)
            }
        } else {
            Text(renderLoadErrorMessage(error), color = MaterialTheme.colorScheme.error)
            TvHeroButton(stringResource(Lang.settings_mediasource_retry), Icons.Rounded.Refresh, true,
                onRetry, {}, Modifier.tvFocusAnchor(focus, TvDetailsKey("entry")).testTag("tv-details-retry"))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvSubjectDetailsContent(
    state: TvSubjectDetailsUiState,
    presentation: TvSubjectPresentationState,
    onIntent: (TvSubjectDetailsIntent) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val details = checkNotNull(state.content)
    val lists = TvDetailsLists(
        details.charactersPager.collectAsLazyPagingItems(),
        details.staffPager.collectAsLazyPagingItems(),
        details.relatedSubjectsPager.collectAsLazyPagingItems(),
        details.commentsPager.collectAsLazyPagingItems(),
    )
    val scrollState = rememberScrollState()
    val optionAnchors = rememberTvOptionAnchors()
    val episodesState = rememberLazyListState()
    val charactersState = rememberLazyListState()
    val staffState = rememberLazyListState()
    val relatedState = rememberLazyListState()
    val rowStates = mapOf("episode" to episodesState, "character" to charactersState, "staff" to staffState, "related" to relatedState)
    val rowKeys = mapOf(
        "episode" to details.episodes.map { "episode:${it.episodeId}" },
        "character" to lists.characters.itemSnapshotList.items.map { "character:${it.character.id}" },
        "staff" to lists.staff.itemSnapshotList.items.map { "staff:${it.personInfo.id}:${it.position}" },
        "related" to lists.related.itemSnapshotList.items.map { "related:${it.subjectId}" },
    )
    val latestKeys by rememberUpdatedState(rowKeys)
    val latestLists by rememberUpdatedState(lists)
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    var pageLaidOut by remember { mutableStateOf(false) }
    var belowFoldReady by remember { mutableStateOf(false) }
    var informationReturnTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val anchors = remember { TvDetailsScrollAnchors() }
    val defaultSpec = LocalBringIntoViewSpec.current
    val scrollSpec = remember(scrollState, defaultSpec) { TvDetailsBringIntoViewSpec(anchors, { scrollState.value }, defaultSpec) }
    val backdrop = state.images.backdrop?.url ?: details.info.imageLarge
    var backdropImage by remember(backdrop) { mutableStateOf<TvDetailsBackdropImage?>(null) }
    val backdropFadeDistance = with(LocalDensity.current) { TvSubjectDetailsDefaults.BackdropFadeDistance.toPx() }

    suspend fun restore(target: String, previous: List<String> = emptyList()) {
        focus.requestPrepared(isRelevant = { presentation.panel == null }) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { pageLaidOut && window.isWindowFocused }.first { it }
            if (target != "play") belowFoldReady = true
            val section = target.substringBefore(':')
            val rowState = rowStates[section]
            if (rowState != null) {
                snapshotFlow {
                    target in latestKeys[section].orEmpty() || when (section) {
                        "character" -> latestLists.characters.loadState.refresh !is LoadState.Loading
                        "staff" -> latestLists.staff.loadState.refresh !is LoadState.Loading
                        "related" -> latestLists.related.loadState.refresh !is LoadState.Loading
                        else -> true
                    }
                }.first { it }
                val keys = latestKeys[section].orEmpty()
                val selected = if (keys.isEmpty()) null else detailsFocusFallback(target, previous, keys, keys.first())
                if (selected != null) {
                    rowState.scrollToItem(keys.indexOf(selected))
                    TvDetailsKey(selected)
                } else TvDetailsKey(if (section == "episode") "all-episodes" else "${section}s-all")
            } else TvDetailsKey(target)
        }
    }
    LaunchedEffect(focus) {
        if (presentation.panel == null) restore(presentation.lastFocused)
        else belowFoldReady = true
    }
    LaunchedEffect(presentation.restoreTarget, presentation.panel) {
        val target = presentation.restoreTarget ?: return@LaunchedEffect
        if (presentation.panel == null) {
            restore(target)
            if (presentation.restoreTarget == target) presentation.restoreTarget = null
        }
    }
    LaunchedEffect(state.operation) {
        val operation = state.operation
        if (operation.completed && operation.error == null) presentation.complete(operation.requestId, operation.offerMarkAllWatched)
    }
    var previousKeys by remember { mutableStateOf(rowKeys) }
    // Capture the identity before the lazy layout removes its focused node.
    val focusedBeforeUpdate = presentation.lastFocused
    LaunchedEffect(rowKeys) {
        val current = focusedBeforeUpdate
        val section = current.substringBefore(':')
        val before = previousKeys[section].orEmpty()
        val after = rowKeys[section].orEmpty()
        if (presentation.panel == null && current in before && current !in after) {
            // A replacement Paging flow initially has no items. Wait for its data before
            // choosing a neighbour; this preparation survives subsequent list emissions.
            scope.launch { restore(current, before) }
        }
        previousKeys = rowKeys
    }
    fun Modifier.anchor(id: String, level: Int): Modifier = this
        .tvFocusAnchor(focus, TvDetailsKey(id))
        .onFocusChanged {
            if (it.hasFocus) {
                if (id == "info" && presentation.lastFocused != id) informationReturnTarget = presentation.lastFocused
                presentation.lastFocused = id
                if (id.startsWith("episode:")) presentation.lastEpisode = id
                presentation.backLevel = level
                if (id == "play") belowFoldReady = true
            }
        }.testTag("tv-details-$id")
    fun open(kind: TvDetailsPanelKind, argument: String = "") {
        presentation.open(kind, argument)
    }
    val episodeKeys = rowKeys.getValue("episode")
    val episodesEntry = presentation.lastEpisode?.takeIf { it in episodeKeys }
        ?: details.playTargetId?.let { "episode:$it" }?.takeIf { it in episodeKeys }
        ?: episodeKeys.firstOrNull()
        ?: "all-episodes"
    val charactersEntry = if (lists.characters.itemCount > 0) "characters-row" else "characters-all"
    val staffEntry = if (lists.staff.itemCount > 0) "staff-row" else "staffs-all"
    val relatedEntry = when {
        lists.related.itemCount > 0 -> "related-row"
        lists.related.loadState.refresh is LoadState.Loading || lists.related.loadState.append is LoadState.Loading -> "info"
        else -> "relateds-all"
    }
    val informationPreviousKeys = rowKeys[if (relatedEntry == "info") "staff" else "related"].orEmpty()
    val informationUpTarget = informationReturnTarget?.takeIf { it in informationPreviousKeys }
        ?: informationPreviousKeys.firstOrNull()
        ?: if (relatedEntry == "info") staffEntry else relatedEntry
    val informationFocusProgress by animateFloatAsState(
        targetValue = if (presentation.lastFocused == "info") 1f else 0f,
        animationSpec = tween(180),
        label = "details-info-focus",
    )
    val surroundingContentModifier = Modifier.graphicsLayer {
        alpha = 1f - (1f - TvSubjectDetailsDefaults.InformationSurroundingAlpha) * informationFocusProgress
    }
    fun rowFocus(id: String, up: String, down: String? = null) = Modifier
        .tvFocusAnchor(focus, TvDetailsKey(id))
        .tvFocusExit(focus, *listOfNotNull(
            FocusDirection.Up to TvDetailsKey(up),
            down?.let { FocusDirection.Down to TvDetailsKey(it) },
        ).toTypedArray())
    fun back() {
        focus.notifyUserNavigation()
        scope.launch {
            restore("play")
        }
    }
    BackHandler(presentation.panel == null && presentation.backLevel > 0) { back() }

    TvSubjectDetailsPageLayout(
        focus = focus, scrollState = scrollState, bringIntoViewSpec = scrollSpec,
        scrollContentModifier = Modifier.onGloballyPositioned {
            anchors.contentTopInRoot = it.positionInRoot().y
            pageLaidOut = true
        },
        backdrop = {
            TvDetailsBackdrop(backdrop, { scrollState.value / backdropFadeDistance },
                onImageLoaded = { backdropImage = TvDetailsBackdropImage(backdrop, it) })
        },
        modifier = Modifier.tvModalUnderlay(presentation.panel != null)
            .tvBackKey(enabled = presentation.panel == null && presentation.backLevel > 0, onBack = ::back),
    ) { heroHeight ->
        TvDetailsHeroSection(
            details = details, height = heroHeight, interactive = belowFoldReady,
            onPlay = { onIntent(TvSubjectDetailsIntent.Resume) },
            onSummary = { open(TvDetailsPanelKind.Summary) },
            onComments = { open(TvDetailsPanelKind.Comments) },
            onCollection = { open(TvDetailsPanelKind.Collection) },
            onRating = { open(TvDetailsPanelKind.Rating) },
            actionBoundsModifier = { Modifier.tvOptionAnchor(optionAnchors, it) },
            actionModifier = { id ->
                Modifier.anchor(id, if (id == "summary" || id == "bgm-rating") 1 else 0)
                    .tvFocusLink(
                        focus,
                        up = when (id) {
                            "bgm-rating" -> null
                            "summary" -> TvDetailsKey("bgm-rating")
                            else -> TvDetailsKey("summary")
                        },
                        down = TvDetailsKey(when (id) {
                            "bgm-rating" -> "summary"
                            "summary" -> "play"
                            else -> episodesEntry
                        }),
                        right = when (id) {
                            "play" -> TvDetailsKey("collection")
                            "collection" -> TvDetailsKey("rating")
                            else -> null
                        },
                        left = when (id) {
                            "rating" -> TvDetailsKey("collection")
                            "collection" -> TvDetailsKey("play")
                            else -> null
                        },
                    )
                    .then(if (id in setOf("play", "collection", "rating")) {
                        Modifier.tvFocusHotkey(focus, Key.DirectionDown) { scope.launch { restore(episodesEntry) } }
                    } else Modifier)
            },
            modifier = surroundingContentModifier.tvDetailsScrollSection(anchors, "hero", 0f) { scrollState.value },
        )
        if (!belowFoldReady) return@TvSubjectDetailsPageLayout
        TvDetailsBrowseRowLayout(
            title = stringResource(Lang.subject_details_episodes), listState = episodesState,
            sectionId = "episodes",
            focused = presentation.lastFocused.startsWith("episode:") || presentation.lastFocused == "all-episodes",
            modifier = surroundingContentModifier,
            rowModifier = rowFocus("episodes-row", "all-episodes", charactersEntry),
            headingAction = {
                TvDetailsAction(
                    stringResource(Lang.subject_details_view_all), Icons.Rounded.ChevronRight,
                    onClick = { open(TvDetailsPanelKind.Episodes, presentation.lastEpisode?.substringAfter(':').orEmpty()) },
                    modifier = Modifier.anchor("all-episodes", 1).tvFocusLink(focus, up = TvDetailsKey("play"))
                        .tvFocusHotkey(focus, Key.DirectionDown) {
                            scope.launch { restore(if (episodeKeys.isEmpty()) charactersEntry else episodesEntry) }
                        },
                    iconOnly = true,
                )
            },
        ) {
            items(details.episodes, key = { it.episodeId }) { episode ->
                TvEpisodeCard(
                    episode = episode,
                    imageUrl = state.images.episodeStills[episode.episodeId] ?: backdrop,
                    onClick = { onIntent(TvSubjectDetailsIntent.PlayEpisode(episode.episodeId)) },
                    onLongClick = { onIntent(TvSubjectDetailsIntent.ToggleEpisode(episode.episodeId, presentation.generation)) },
                    modifier = Modifier.anchor("episode:${episode.episodeId}", 1),
                )
            }
            if (details.episodes.isEmpty()) {
                if (details.episodesLoading) {
                    item("loading") {
                        Row(Modifier.testTag("tv-details-episodes-loading"), horizontalArrangement = Arrangement.spacedBy(TvSubjectDetailsDefaults.RowSpacing)) {
                            repeat(3) { Box(Modifier.width(TvSubjectDetailsDefaults.EpisodeCardWidth).aspectRatio(16f / 9f).placeholder(true)) }
                        }
                    }
                } else {
                    item("empty") { Text(stringResource(Lang.subject_details_no_episodes), color = TvSubjectDetailsDefaults.SecondaryContent) }
                }
            }
        }
        TvDetailsBrowseRow(stringResource(Lang.subject_details_characters), lists.characters, charactersState,
            modifier = surroundingContentModifier,
            sectionId = "characters",
            focused = presentation.lastFocused.startsWith("character:") || presentation.lastFocused == "characters-all",
            entryModifier = Modifier.anchor("characters-all", 1).tvFocusLink(focus, down = TvDetailsKey(staffEntry))
                .tvFocusHotkey(focus, Key.DirectionUp) { scope.launch { restore(episodesEntry) } },
            onAll = null,
            rowModifier = rowFocus("characters-row", episodesEntry, staffEntry)
                .tvFocusHotkey(focus, Key.DirectionUp) { scope.launch { restore(episodesEntry) } }) { item ->
            TvCharacterCard(item, Modifier.anchor("character:${item.character.id}", 1)) {
                open(TvDetailsPanelKind.Person, "character:${item.character.id}")
            }
        }
        TvDetailsBrowseRow(stringResource(Lang.subject_details_staff), lists.staff, staffState,
            modifier = surroundingContentModifier,
            sectionId = "staff",
            focused = presentation.lastFocused.startsWith("staff:") || presentation.lastFocused == "staffs-all",
            entryModifier = Modifier.anchor("staffs-all", 1).tvFocusLink(focus, up = TvDetailsKey(charactersEntry),
                down = TvDetailsKey(relatedEntry)),
            onAll = null,
            rowModifier = rowFocus("staff-row", charactersEntry, relatedEntry)) { item ->
            TvStaffCard(item, Modifier.anchor("staff:${item.personInfo.id}:${item.position}", 1)) {
                open(TvDetailsPanelKind.Person, "person:${item.personInfo.id}")
            }
        }
        TvDetailsBrowseRow(stringResource(Lang.subject_details_related_subjects), lists.related, relatedState,
            modifier = surroundingContentModifier,
            sectionId = "related",
            focused = presentation.lastFocused.startsWith("related:") || presentation.lastFocused == "relateds-all",
            entryModifier = Modifier.anchor("relateds-all", 1).tvFocusLink(focus, up = TvDetailsKey(staffEntry),
                down = TvDetailsKey("info")),
            onAll = null, rowModifier = rowFocus("related-row", staffEntry, "info")) { item ->
            TvRelatedSubjectCard(item, { onIntent(TvSubjectDetailsIntent.OpenRelatedSubject(it)) },
                Modifier.anchor("related:${item.subjectId}", 1))
        }
        TvSubjectInformationSection(
            details.info, totalEpisodes = if (details.episodesLoading) null else details.mainEpisodeIds.size,
            focusProgress = informationFocusProgress,
            modifier = Modifier.anchor("info", 1)
                .tvFocusHotkey(focus, Key.DirectionUp) { scope.launch { restore(informationUpTarget) } },
        )
        state.error?.let { error ->
            Text(renderLoadErrorMessage(error), color = MaterialTheme.colorScheme.error,
                modifier = surroundingContentModifier.padding(48.dp))
        }
    }
    val panelStateHolder = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalTvDetailsBackdropImage provides backdropImage) {
        // Keep the review page laid out beneath its children: rating anchors and list position
        // remain valid even when the viewport changes while an editor is open.
        presentation.panels.firstOrNull { it.kind == TvDetailsPanelKind.Comments }?.let { reviews ->
            panelStateHolder.SaveableStateProvider(reviews.key) {
                TvSubjectComments(
                    details, lists.comments, backdrop, reviews,
                    active = presentation.panel?.key == reviews.key,
                    onFocused = presentation::rememberPanelFocus,
                    onClose = presentation::close,
                    onRating = { presentation.open(TvDetailsPanelKind.Rating, origin = "review-rating") },
                    onComment = { presentation.open(TvDetailsPanelKind.Comment, it.stableId, "review:${it.stableId}") },
                    ratingBoundsModifier = Modifier.tvOptionAnchor(optionAnchors, "review-rating"),
                )
            }
        }
        presentation.panel?.takeUnless { it.kind == TvDetailsPanelKind.Comments }?.let { panel ->
            DisposableEffect(panel.key) {
                onDispose {
                    if (panel.kind == TvDetailsPanelKind.Rating) panelStateHolder.removeState(panel.key)
                }
            }
            panelStateHolder.SaveableStateProvider(panel.key) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val trigger = presentation.panels.lastOrNull {
                        it.origin == "collection" || it.origin == "rating" || it.origin == "review-rating"
                    }?.origin
                    val bounds = trigger?.let(optionAnchors::boundsOf)
                    if (trigger != null && bounds == null) return@BoxWithConstraints
                    val compact = maxWidth - TvSubjectDetailsDefaults.HorizontalPadding * 2 < 600.dp
                    TvSubjectDetailsPanels(state, presentation, lists, onIntent, onOpenUrl,
                        anchorBounds = bounds,
                        anchorButton = { buttonModifier ->
                            when (trigger) {
                                "collection" -> TvDetailsCollectionAction(details.collectionType, presentation::close,
                                    buttonModifier, compact, active = true)
                                "rating" -> TvDetailsRatingAction(details.selfRating.score, presentation::close,
                                    buttonModifier, compact, active = true)
                                "review-rating" -> TvDetailsRatingAction(details.selfRating.score, presentation::close,
                                    buttonModifier.fillMaxWidth(), compact = false, active = true,
                                    boundsModifier = Modifier.fillMaxWidth())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun <T : Any> TvDetailsBrowseRow(
    title: String,
    items: LazyPagingItems<T>,
    listState: LazyListState,
    sectionId: String,
    focused: Boolean,
    limit: Int = Int.MAX_VALUE,
    entryModifier: Modifier,
    onAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val error = (items.loadState.refresh as? LoadState.Error) ?: (items.loadState.append as? LoadState.Error)
    TvDetailsBrowseRowLayout(
        title, listState, sectionId, focused, modifier, rowModifier,
        headingAction = {
            if (onAll != null) TvDetailsAction(stringResource(Lang.subject_details_view_all), Icons.Rounded.ChevronRight, onAll, entryModifier, iconOnly = true)
        },
    ) {
        items(count = minOf(items.itemCount, limit), key = { index ->
            val item = items.peek(index)
            when (item) {
                is RelatedCharacterInfo -> "character:${item.character.id}"
                is RelatedPersonInfo -> "staff:${item.personInfo.id}:${item.position}"
                is RelatedSubjectInfo -> "subject:${item.subjectId}"
                is UIComment -> item.stableId
                else -> "placeholder:$index"
            }
        }) { index -> items[index]?.let { content(it) } }
        if (items.loadState.refresh is LoadState.Loading || items.loadState.append is LoadState.Loading) {
            item("loading") {
                Row(Modifier.testTag("tv-details-$sectionId-loading"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { Box(Modifier.size(96.dp).placeholder(true)) }
                }
            }
        } else if (error != null) {
            item("error") {
                Column {
                    Text(renderLoadErrorMessage(LoadError.fromException(error.error)), color = MaterialTheme.colorScheme.error)
                    TvHeroButton(stringResource(Lang.settings_mediasource_retry), Icons.Rounded.Refresh, true, items::retry, {}, if (onAll == null) entryModifier else Modifier)
                }
            }
        } else if (items.itemCount == 0) {
            item("empty") { Text(stringResource(Lang.subject_details_empty), if (onAll == null) entryModifier.focusable() else Modifier,
                color = TvSubjectDetailsDefaults.SecondaryContent) }
        }
    }
}
