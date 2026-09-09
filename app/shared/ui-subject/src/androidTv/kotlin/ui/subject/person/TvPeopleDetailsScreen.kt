/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.PersonType
import me.him188.ani.app.data.models.subject.nameCn
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.comment.CommentOverlayCleanupEffect
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.comment_preview_image
import me.him188.ani.app.ui.lang.comment_preview_quote
import me.him188.ani.app.ui.lang.comment_review_hidden
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.people_discussion
import me.him188.ani.app.ui.lang.people_discussion_source
import me.him188.ani.app.ui.lang.people_view_image
import me.him188.ani.app.ui.lang.person_details_basic_info
import me.him188.ani.app.ui.lang.person_details_casts
import me.him188.ani.app.ui.lang.person_details_character_subjects
import me.him188.ani.app.ui.lang.person_details_no_comments
import me.him188.ani.app.ui.lang.person_details_voice_actors
import me.him188.ani.app.ui.lang.person_details_works
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_no_summary
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.search.renderLoadErrorMessage
import me.him188.ani.leanback.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.leanback.ui.foundation.focus.TvAnchoredBringIntoViewSpec
import me.him188.ani.leanback.ui.foundation.focus.requestPrepared
import me.him188.ani.leanback.ui.foundation.focus.tvBackKey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.leanback.ui.foundation.focus.tvFocusHotkey
import me.him188.ani.leanback.ui.foundation.focus.tvFocusLink
import me.him188.ani.leanback.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.leanback.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionsRow
import me.him188.ani.leanback.ui.subject.components.LocalTvDetailsBackdropImage
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdrop
import me.him188.ani.leanback.ui.subject.components.TvDetailsBackdropImage
import me.him188.ani.leanback.ui.subject.components.TvDetailsDescriptionCard
import me.him188.ani.leanback.ui.subject.components.TvDetailsBringIntoViewSpec
import me.him188.ani.leanback.ui.subject.components.TvDetailsFullscreenOverlay
import me.him188.ani.leanback.ui.subject.components.TvDetailsScrollAnchors
import me.him188.ani.leanback.ui.subject.components.TvSubjectDetailsDefaults
import me.him188.ani.leanback.ui.subject.components.tvDetailsScrollSection
import me.him188.ani.leanback.ui.subject.details.TvDetailsAction
import me.him188.ani.leanback.ui.subject.details.TvDetailsPersonCard
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleBrowseSection
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleDetailsLayout
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleIdentity
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleIntroduction
import me.him188.ani.leanback.ui.subject.person.components.TvPeoplePortrait
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleDiscussionPreviewCard
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleSection
import me.him188.ani.leanback.ui.subject.person.components.TvPeopleWorkCard
import me.him188.ani.leanback.ui.subject.person.components.peopleSection
import me.him188.ani.leanback.ui.subject.person.discussion.TvPeopleDiscussion
import me.him188.ani.leanback.ui.subject.person.discussion.TvPeopleDiscussionAction
import me.him188.ani.leanback.ui.subject.person.discussion.TvPeopleDiscussionPage
import me.him188.ani.leanback.ui.subject.person.discussion.TvPeopleDiscussionState
import me.him188.ani.leanback.ui.subject.person.discussion.peopleDiscussionCount
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeopleOverlay
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeoplePresentationState
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeopleScrollMemory
import me.him188.ani.leanback.ui.subject.person.presentation.TvPeopleFocusScrollSpec
import me.him188.ani.leanback.ui.subject.presentation.TvDetailsKey
import me.him188.ani.leanback.ui.subject.presentation.detailsFocusFallback
import me.him188.ani.leanback.ui.subject.reviews.reviewPreview
import org.jetbrains.compose.resources.stringResource

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun TvPeopleDetailsScreen(
    state: TvPeopleDetailsUiState,
    onIntent: (TvPeopleIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit = {},
) {
    val presentation = rememberSaveable(state.target, saver = TvPeoplePresentationState.Saver) { TvPeoplePresentationState() }
    val profile = state.profile
    val comments = state.comments.collectAsLazyPagingItems()
    val subjects = state.subjects?.collectAsLazyPagingItems()
    val casts = state.casts?.collectAsLazyPagingItems()
    val works = state.works?.collectAsLazyPagingItems()
    val actorItems = profile?.actors.orEmpty()
    val scroll = rememberScrollState()
    val rowStates = mapOf("actors" to rememberLazyListState(), "casts" to rememberLazyListState(), "works" to rememberLazyListState())
    val scrollMemory = rememberSaveable(state.target, saver = TvPeopleScrollMemory.Saver) { TvPeopleScrollMemory() }
    val restoringPosition = remember { scrollMemory.page != null }
    var restorePositionPending by remember { mutableStateOf(restoringPosition) }
    fun navigate(intent: TvPeopleIntent) {
        scrollMemory.capture(scroll, rowStates)
        onIntent(intent)
    }
    val sections = buildList {
        if (state.target.kind == TvPeopleKind.Character) {
            add(TvPeopleSection("actors", stringResource(Lang.person_details_voice_actors), actorItems.map { "actors:${it.id}" },
                profile == null && state.loading, state.error.takeIf { profile == null }, { onIntent(TvPeopleIntent.Retry) }) { index, itemModifier ->
                val actor = actorItems[index]
                TvDetailsPersonCard(actor.imageMedium, actor.displayName, "", {
                    navigate(TvPeopleIntent.OpenPerson(TvPeopleTarget(actor.id, TvPeopleKind.VoiceActor)))
                }, itemModifier, portrait = actor.type == PersonType.Individual)
            })
            subjects?.let { add(peopleSection("works", stringResource(Lang.person_details_character_subjects), it,
                key = { record -> record.subject.subjectId.toString() }) { record, itemModifier ->
                TvPeopleWorkCard(record.subject, record.role.nameCn.orEmpty(), itemModifier) { navigate(TvPeopleIntent.OpenSubject(record.subject)) }
            }) }
        }
        if (state.target.kind == TvPeopleKind.VoiceActor) casts?.let {
            add(peopleSection("casts", stringResource(Lang.person_details_casts), it,
                key = { record -> "${record.character.id}:${record.subject.subjectId}" }) { record, itemModifier ->
                TvDetailsPersonCard(record.character.imageMedium, record.character.displayName, record.subject.displayName,
                    { navigate(TvPeopleIntent.OpenPerson(TvPeopleTarget(record.character.id, TvPeopleKind.Character))) }, itemModifier)
            })
        }
        if (state.target.kind != TvPeopleKind.Character) works?.let {
            add(peopleSection("works", stringResource(Lang.person_details_works), it,
                key = { record -> record.subject.subjectId.toString() }) { record, itemModifier ->
                TvPeopleWorkCard(record.subject, record.positions.mapNotNull { position -> position.nameCn }.distinct().joinToString(" · "), itemModifier) {
                    navigate(TvPeopleIntent.OpenSubject(record.subject))
                }
            })
        }
    }.filter { it.visible }
    val currentSections by rememberUpdatedState(sections)
    val currentState by rememberUpdatedState(state)
    val focus = rememberTvFocusScope()
    focus.Resolver()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val window = LocalWindowInfo.current
    val inputMode = LocalInputModeManager.current
    val anchors = remember { TvDetailsScrollAnchors() }
    val defaultSpec = LocalBringIntoViewSpec.current
    val entryNavGeneration = remember { focus.userNavGeneration }
    val focusScrollEnabled = { !restoringPosition || focus.userNavGeneration != entryNavGeneration }
    val scrollSpec = remember(defaultSpec) {
        TvPeopleFocusScrollSpec(TvDetailsBringIntoViewSpec(anchors, { scroll.value }, defaultSpec) {
            currentSections.lastOrNull()?.keys?.contains(presentation.focused) == true
        }, focusScrollEnabled)
    }
    val rowPaddingPx = with(LocalDensity.current) { TvSubjectDetailsDefaults.HorizontalPadding.toPx() }
    val rowScrollSpec = remember(rowPaddingPx) {
        TvPeopleFocusScrollSpec(TvAnchoredBringIntoViewSpec { rowPaddingPx }, focusScrollEnabled)
    }
    var laidOut by remember { mutableStateOf(false) }
    var laidOutSections by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var backdrop by remember(profile?.image) { mutableStateOf<TvDetailsBackdropImage?>(null) }
    var entryPending by remember { mutableStateOf(true) }
    var expectedFocus by remember { mutableStateOf(presentation.focused) }
    var entryNavigation by remember { mutableStateOf(focus.userNavGeneration) }
    var previousKeys by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    suspend fun restore(target: String, previous: List<String> = emptyList()) {
        focus.requestPrepared(isRelevant = { presentation.overlay == TvPeopleOverlay.None }) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
            snapshotFlow { laidOut && window.isWindowFocused }.first { it }
            if (restorePositionPending && focus.userNavGeneration == entryNavGeneration) {
                snapshotFlow {
                    (currentState.profile != null || !currentState.loading) &&
                        currentSections.none { it.loading && it.itemKeys.isEmpty() } &&
                        laidOutSections == currentSections.associate { it.id to it.keys }
                }.first { it }
                scrollMemory.rows.forEach { (id, position) ->
                    val lastIndex = currentSections.find { it.id == id }?.keys?.lastIndex ?: -1
                    if (lastIndex >= 0) rowStates.getValue(id).scrollToItem(position.first.coerceAtMost(lastIndex), position.second)
                }
                scroll.scrollTo(scrollMemory.page ?: 0)
            }
            restorePositionPending = false
            val row = target.substringBefore(':')
            val selected = if (':' in target) {
                snapshotFlow {
                    val section = currentSections.find { it.id == row }
                    section == null || target in section.keys || !section.loading
                }.first { it }
                val keys = currentSections.find { it.id == row }?.keys.orEmpty()
                val selected = detailsFocusFallback(target, previous, keys, "intro")
                val index = keys.indexOf(selected)
                if (index >= 0) rowStates.getValue(row).let { list ->
                    if (list.layoutInfo.visibleItemsInfo.none { it.key == selected }) list.scrollToItem(index)
                }
                selected
            } else if (target == "image" && currentState.profile?.image.isNullOrBlank()) "intro" else target
            if (selected == "intro" && selected != target) scroll.scrollTo(0)
            expectedFocus = selected
            TvDetailsKey(selected)
        }
    }
    LaunchedEffect(presentation.overlay) {
        entryPending = true
        expectedFocus = presentation.focused
        entryNavigation = focus.userNavGeneration
        if (presentation.overlay == TvPeopleOverlay.None) {
            inputMode.requestInputMode(InputMode.Keyboard)
            restore(presentation.focused, previousKeys[presentation.focused.substringBefore(':')].orEmpty())
        }
    }
    val keys = sections.associate { it.id to it.keys }
    val focusBeforeUpdate = presentation.focused
    LaunchedEffect(keys) {
        val row = focusBeforeUpdate.substringBefore(':')
        val before = previousKeys[row].orEmpty()
        if (presentation.overlay == TvPeopleOverlay.None && focusBeforeUpdate in before && focusBeforeUpdate !in keys[row].orEmpty()) {
            restore(focusBeforeUpdate, before)
        }
        previousKeys = if (presentation.overlay != TvPeopleOverlay.None && focusBeforeUpdate in before && focusBeforeUpdate !in keys[row].orEmpty()) {
            keys + (row to before)
        } else keys
    }
    fun anchor(key: String) = Modifier.tvFocusAnchor(focus, TvDetailsKey(key)).onFocusChanged {
        if (it.isFocused && presentation.overlay == TvPeopleOverlay.None) {
            if (entryPending && focus.userNavGeneration == entryNavigation && key != expectedFocus) return@onFocusChanged
            entryPending = false
            presentation.rememberFocus(key)
        }
    }.testTag("tv-people-$key")
    fun sectionEntry(section: TvPeopleSection) = presentation.rowFocus[section.id]?.takeIf { it in section.keys }
        ?: section.keys.firstOrNull() ?: "intro"
    fun moveTo(key: String) { scope.launch { restore(key) } }
    fun backToHero() { focus.notifyUserNavigation(); moveTo("intro") }
    val canBackToHero = presentation.overlay == TvPeopleOverlay.None && presentation.focused != "intro"
    BackHandler(canBackToHero, ::backToHero)
    val heroAction = when {
        !profile?.image.isNullOrBlank() -> "image"
        state.error != null -> "retry"
        else -> null
    }
    val actionEntry = heroAction ?: sections.firstOrNull()?.let(::sectionEntry)
    val preview = comments.itemCount.takeIf { it > 0 }?.let { comments[0] }
    CommentOverlayCleanupEffect(comments) { onIntent(TvPeopleIntent.CommentsRefreshed) }
    LaunchedEffect(state.reportCompleted) {
        if (presentation.overlay == TvPeopleOverlay.Discussion && presentation.discussionPage == TvPeopleDiscussionPage.Report) {
            state.reportCompleted?.let { presentation.back(it) }
        }
    }

    Box(modifier.fillMaxSize().testTag("tv-people-details")) {
        TvPeopleDetailsLayout(
            focus, scroll, scrollSpec, anchors,
            scrollContentModifier = Modifier.onGloballyPositioned {
                laidOutSections = sections.associate { section -> section.id to section.keys }
                laidOut = true
            },
            heroModifier = Modifier.tvDetailsScrollSection(anchors, "hero", 0f) { scroll.value },
            backdrop = { TvDetailsBackdrop(profile?.image.orEmpty(), { 1f },
                onImageLoaded = { backdrop = TvDetailsBackdropImage(profile?.image.orEmpty(), it) }) },
            identity = { TvPeopleIdentity(state.target.kind, profile) },
            portrait = { TvPeoplePortrait(profile, it) },
            introduction = { cardModifier ->
                TvDetailsDescriptionCard(
                    summary = profile?.summary?.takeIf { it.isNotBlank() }
                        ?: state.error?.takeIf { profile == null }?.let { renderLoadErrorMessage(it) }
                        ?: stringResource(when {
                            state.loading && profile == null -> Lang.foundation_loading
                            !profile?.infobox.isNullOrEmpty() -> Lang.person_details_basic_info
                            else -> Lang.subject_details_no_summary
                        }),
                    onClick = { presentation.open(TvPeopleOverlay.Introduction) },
                    modifier = cardModifier.then(anchor("intro"))
                        .tvFocusLink(focus, right = TvDetailsKey("discussion"))
                        .tvFocusHotkey(focus, Key.DirectionDown) { actionEntry?.let(::moveTo) },
                    title = peopleIntroductionTitle(state.target.kind),
                )
            },
            discussion = { cardModifier ->
                val title = stringResource(Lang.people_discussion) + " · " + peopleDiscussionCount(comments, state.bangumiUnavailable)
                TvPeopleDiscussionPreviewCard(title, stringResource(Lang.people_discussion_source), { presentation.open(TvPeopleOverlay.Discussion) },
                    cardModifier.then(anchor("discussion")).tvFocusLink(focus, left = TvDetailsKey("intro"))
                        .tvFocusHotkey(focus, Key.DirectionDown) { actionEntry?.let(::moveTo) }) {
                    if (preview != null) {
                        val safePreview = reviewPreview(preview.content.elements, stringResource(Lang.comment_preview_quote),
                            stringResource(Lang.comment_preview_image), stringResource(Lang.comment_review_hidden))
                        RichText(listOf(safePreview), interactionEnabled = false, color = TvSubjectDetailsDefaults.Content,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp))
                    } else Text(stringResource(if (comments.loadState.refresh is LoadState.Loading) Lang.foundation_loading
                        else if (comments.loadState.refresh is LoadState.Error) Lang.settings_mediasource_retry else Lang.person_details_no_comments),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp))
                }
            },
            actions = {
                TvOptionsRow {
                    if (!profile?.image.isNullOrBlank()) {
                        TvDetailsAction(stringResource(Lang.people_view_image), Icons.Rounded.Fullscreen,
                            { presentation.open(TvPeopleOverlay.Image) }, anchor("image").tvFocusLink(focus, up = TvDetailsKey("intro"))
                                .tvFocusHotkey(focus, Key.DirectionDown) { sections.firstOrNull()?.let { moveTo(sectionEntry(it)) } })
                    }
                    if (state.error != null) TvDetailsAction(stringResource(Lang.settings_mediasource_retry), Icons.Rounded.Refresh,
                        { onIntent(TvPeopleIntent.Retry) }, anchor("retry").tvFocusLink(focus, up = TvDetailsKey("intro"))
                            .tvFocusHotkey(focus, Key.DirectionDown) { sections.firstOrNull()?.let { moveTo(sectionEntry(it)) } })
                }
                state.error?.let { Text(renderLoadErrorMessage(it), color = TvSubjectDetailsDefaults.SecondaryContent) }
            },
            modifier = Modifier.tvFocusNavSignal(focus).tvModalUnderlay(presentation.overlay != TvPeopleOverlay.None)
                .tvBackKey(canBackToHero, ::backToHero),
        ) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                sections.forEachIndexed { index, section ->
                    TvPeopleBrowseSection(section, rowStates.getValue(section.id), presentation.focused.startsWith("${section.id}:"),
                        rowModifier = Modifier.tvFocusHotkey(focus, Key.DirectionUp) {
                            moveTo(sections.getOrNull(index - 1)?.let(::sectionEntry) ?: heroAction ?: "intro")
                        }.tvFocusHotkey(focus, Key.DirectionDown) { sections.getOrNull(index + 1)?.let { moveTo(sectionEntry(it)) } },
                        anchor = ::anchor)
                }
            }
        }
        val generation = presentation.generation
        val close = { presentation.back(generation) }
        CompositionLocalProvider(LocalTvDetailsBackdropImage provides backdrop) {
            when (presentation.overlay) {
                TvPeopleOverlay.None -> Unit
                TvPeopleOverlay.Introduction -> TvPeopleIntroduction(state.target.kind, profile, state.loading, state.error, close)
                TvPeopleOverlay.Discussion -> TvPeopleDiscussion(
                    state = TvPeopleDiscussionState(
                        name = profile?.name.orEmpty(), page = presentation.discussionPage,
                        commentId = presentation.commentId, commentFocus = presentation.commentFocus,
                        image = presentation.image, generation = generation,
                        bangumiUnavailable = state.bangumiUnavailable, reportBusy = state.reportBusy,
                    ),
                    comments = comments,
                    commentPresentation = state.commentPresentation,
                    onAction = { action ->
                        when (action) {
                            is TvPeopleDiscussionAction.FocusComment -> presentation.commentFocus = action.key
                            is TvPeopleDiscussionAction.OpenComment -> presentation.openComment(action.id)
                            TvPeopleDiscussionAction.ShowReport -> presentation.report()
                            is TvPeopleDiscussionAction.ShowImage -> presentation.showImage(action.url)
                            TvPeopleDiscussionAction.Close -> close()
                        }
                    },
                    onVote = { comment, vote -> onIntent(TvPeopleIntent.Vote(comment, vote)) },
                    onReport = { comment, reason -> onIntent(TvPeopleIntent.Report(comment, reason, generation)) },
                    onOpenOriginal = {
                        val type = if (state.target.kind == TvPeopleKind.Character) "character" else "person"
                        onOpenUrl("https://bgm.tv/$type/${state.target.id}")
                    },
                    onOpenUrl = onOpenUrl,
                )
                TvPeopleOverlay.Image -> TvDetailsFullscreenOverlay(profile?.image.orEmpty(), close, "people-image") { imageFocus ->
                    Box(Modifier.fillMaxSize().padding(24.dp).tvFocusAnchor(imageFocus, TvDetailsKey("people-image"))
                        .focusable().testTag("tv-people-full-image")) {
                        AsyncImage(profile?.image.orEmpty(), profile?.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
    }
}
