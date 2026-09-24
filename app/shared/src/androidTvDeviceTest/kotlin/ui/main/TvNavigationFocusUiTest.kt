/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.framework.AniComposeUiTest
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.tv.ui.foundation.focus.LocalTvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusMemory
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.TvModalOverlay
import me.him188.ani.tv.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.tv.ui.foundation.theme.TvApplicationTheme
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvNavigationFocusUiTest {
    @Test
    fun earlyFocusSurvivesTransitionCleanup() = navigationTest { trace ->
        mount(trace, enforceOwnership = false)
        focusBefore("a-0", mainClock.currentTime + 400)
        val pushStart = mainClock.currentTime
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", pushStart + 400)
        runOnIdle { assertEquals(Lifecycle.State.STARTED, trace.lifecycles.getValue("b").currentState) }
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("b-0").assertIsFocused()
        onNodeWithTag("a-page").assertDoesNotExist()
        assertEquals(1, trace.events.count { it.endsWith("request b-0") })

        val popStart = mainClock.currentTime
        runOnIdle { trace.stack.removeLast() }
        focusBefore("a-0", popStart + 400)
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-0").assertIsFocused()
        onNodeWithTag("b-page").assertDoesNotExist()
    }

    @Test
    fun earlyFocusSurvivesPushAndPopWithoutAnEndOfTransitionRequest() = navigationTest { trace ->
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        onNodeWithTag("a-1").assertIsFocused()

        val pushStart = mainClock.currentTime
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", pushStart + 400)
        runOnIdle { assertEquals(Lifecycle.State.STARTED, trace.lifecycles.getValue("b").currentState) }
        down("b-0")
        onNodeWithTag("b-1").assertIsFocused()
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("b-1").assertIsFocused()
        onNodeWithTag("a-page").assertDoesNotExist()
        assertTrue(trace.events.any { it.contains("detach a instance=") })
        assertEquals(1, trace.events.count { it.endsWith("request b-0") })

        val popStart = mainClock.currentTime
        runOnIdle { trace.stack.removeLast() }
        focusBefore("a-1", popStart + 400)
        runOnIdle { assertEquals(Lifecycle.State.STARTED, trace.lifecycles.getValue("a").currentState) }
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-1").assertIsFocused()
        onNodeWithTag("b-page").assertDoesNotExist()
        assertTrue(trace.events.any { it.contains("detach b instance=") })
        assertEquals(1, trace.events.count { it.endsWith("request a-1") })
    }

    @Test
    fun outgoingEntryCannotStealFocusWithALateRequest() = navigationTest { trace ->
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        val pushStart = mainClock.currentTime
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", pushStart + 400)
        runOnIdle {
            trace.record("late request a-1")
            trace.scopes.getValue("a").request(TvFocusKey("a-1"))
            // The boundary also guards direct Compose focus requests.
            trace.scopes.getValue("a").requesterOf(TvFocusKey("a-1")).requestFocus()
        }
        mainClock.advanceTimeBy(64)
        onNodeWithTag("b-0").assertIsFocused()
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("b-0").assertIsFocused()
    }

    @Test
    fun rapidPopReactivatesTheRetainedEntryWithoutRecreatingItsFocusRoot() = navigationTest { trace ->
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        val originalScope = trace.scopes.getValue("a")
        runOnIdle { trace.stack.removeLast() }
        focusBefore("a-1", mainClock.currentTime + 400)
        runOnIdle { assertTrue(originalScope === trace.scopes.getValue("a")) }
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-1").assertIsFocused()
        assertEquals(1, trace.events.count { it.contains("attach a instance=") })
        assertEquals(1, trace.events.count { it.endsWith("request a-1") })
    }

    @Test
    fun leavingAnEntryCancelsPreparation() = navigationTest { trace ->
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        val ready = CompletableDeferred<Unit>()
        lateinit var preparation: Job
        runOnIdle {
            val focus = trace.scopes.getValue("a")
            preparation = trace.coroutines.getValue("a").launch {
                focus.requestPrepared {
                    trace.record("prepare a")
                    ready.await()
                    TvFocusKey("a-1")
                }
            }
        }
        waitUntil { trace.events.any { it.endsWith("prepare a") } }
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        waitUntil { preparation.isCompleted }
        runOnIdle {
            trace.suspendInitialFocus.add("a")
            trace.stack.removeLast()
        }
        mainClock.advanceTimeBy(64)
        runOnIdle {
            trace.showDeferredAnchor = true
            ready.complete(Unit)
        }
        mainClock.advanceTimeBy(64)
        onNodeWithTag("a-deferred").assertIsNotFocused()
        onNodeWithTag("a-1").assertIsNotFocused()
        runOnIdle { trace.scopes.getValue("a").request(TvFocusKey("a-0")) }
        focusBefore("a-0", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-0").assertIsFocused()
    }

    @Test
    fun leavingAnEntryDiscardsPendingAnchorRequests() = navigationTest { trace ->
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        runOnIdle {
            trace.scopes.getValue("a").request(TvFocusKey("a-deferred"))
            trace.stack.add("b")
        }
        focusBefore("b-0", mainClock.currentTime + 400)
        runOnIdle {
            trace.suspendInitialFocus.add("a")
            trace.stack.removeLast()
        }
        mainClock.advanceTimeBy(64)
        runOnIdle { trace.showDeferredAnchor = true }
        mainClock.advanceTimeBy(64)
        onNodeWithTag("a-deferred").assertIsNotFocused()
        runOnIdle { trace.scopes.getValue("a").request(TvFocusKey("a-0")) }
        focusBefore("a-0", mainClock.currentTime + 400)
    }

    @Test
    fun rememberedEntryRestoresBeforeResumeAfterNodeRecreation() = navigationTest { trace ->
        trace.useMemory = true
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        onNodeWithTag("a-1").assertIsFocused()
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-page").assertDoesNotExist()

        val popStart = mainClock.currentTime
        runOnIdle { trace.stack.removeLast() }
        focusBefore("a-1", popStart + 400)
        runOnIdle { assertEquals(Lifecycle.State.STARTED, trace.lifecycles.getValue("a").currentState) }
        onNodeWithTag("a-1").performKeyInput { pressKey(Key.DirectionUp) }
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-0").assertIsFocused()
    }

    @Test
    fun rememberedEntryUsesFallbackUntilTheSavedNodeIsPlaced() = navigationTest { trace ->
        trace.useMemory = true
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        runOnIdle {
            trace.hiddenEntries.add("a-1")
            trace.stack.removeLast()
        }
        focusBefore("a-0", mainClock.currentTime + 400)
        runOnIdle { trace.hiddenEntries.clear() }
        focusBefore("a-1", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-1").assertIsFocused()
    }

    @Test
    fun userNavigationCancelsRestorationOfALateRememberedNode() = navigationTest { trace ->
        trace.useMemory = true
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        runOnIdle {
            trace.hiddenEntries.add("a-1")
            trace.showDeferredAnchor = true
            trace.stack.removeLast()
        }
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        onNodeWithTag("a-deferred").assertIsFocused()
        runOnIdle { trace.hiddenEntries.clear() }
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-deferred").assertIsFocused()
    }

    @Test
    fun inputInASiblingScopeCancelsThePagesLateRestoration() = navigationTest { trace ->
        trace.useMemory = true
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        down("a-0")
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        runOnIdle {
            trace.hiddenEntries.add("a-1")
            trace.showSiblingControl = true
            trace.stack.removeLast()
        }
        focusBefore("a-0", mainClock.currentTime + 400)
        runOnIdle { trace.scopes.getValue("a-sibling").request(TvFocusKey("sibling")) }
        focusBefore("a-sibling", mainClock.currentTime + 400)
        onNodeWithTag("a-sibling").performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { trace.hiddenEntries.clear() }
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-sibling").assertIsFocused()
    }

    @Test
    fun outgoingEntryCannotHandleKeysWhileTheTargetWaitsForReadiness() = navigationTest { trace ->
        trace.suspendInitialFocus.add("b")
        mount(trace)
        focusBefore("a-0", mainClock.currentTime + 400)
        runOnIdle { trace.stack.add("b") }
        mainClock.advanceTimeBy(80)
        onAllNodes(isRoot()).onLast().performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertTrue(trace.events.none { it.contains("click ") }) }
        runOnIdle { trace.suspendInitialFocus.clear() }
        focusBefore("b-0", mainClock.currentTime + 400)
        onNodeWithTag("b-0").performKeyInput { pressKey(Key.DirectionCenter) }
        runOnIdle { assertEquals(1, trace.events.count { it.endsWith("click b-0") }) }
    }

    @Test
    fun anOutgoingModalReleasesItsTrapAndInheritsItsEntryOwnership() = navigationTest { trace ->
        trace.modalPage = "a"
        mount(trace)
        focusBefore("a-modal-0", mainClock.currentTime + 400)
        runOnIdle { trace.stack.add("b") }
        focusBefore("b-0", mainClock.currentTime + 400)
        runOnIdle {
            val modal = trace.scopes.getValue("a-modal")
            assertTrue(!modal.isActive)
            modal.requesterOf(TvFocusKey("a-modal-1")).requestFocus()
        }
        onNodeWithTag("b-0").assertIsFocused()
        runOnIdle { trace.stack.removeLast() }
        focusBefore("a-modal-0", mainClock.currentTime + 400)
        mainClock.advanceTimeBy(1_200)
        onNodeWithTag("a-modal-0").assertIsFocused()
    }

    private fun navigationTest(block: AniComposeUiTest.(Trace) -> Unit) = runAniComposeUiTest {
        val trace = Trace { mainClock.currentTime }
        try {
            block(trace)
        } catch (failure: Throwable) {
            throw AssertionError(trace.events.joinToString("\n"), failure)
        } finally {
            println("Navigation focus trace:\n" + trace.events.joinToString("\n"))
        }
    }

    private class Trace(val time: () -> Long) {
        val stack = mutableStateListOf("a")
        val scopes = mutableMapOf<String, TvFocusScope>()
        val coroutines = mutableMapOf<String, CoroutineScope>()
        val lifecycles = mutableMapOf<String, Lifecycle>()
        val memories = mutableMapOf<String, TvFocusMemory>()
        val hiddenEntries = mutableStateListOf<String>()
        var useMemory = false
        val suspendInitialFocus = mutableStateListOf<String>()
        var showDeferredAnchor by mutableStateOf(false)
        var showSiblingControl by mutableStateOf(false)
        var modalPage: String? by mutableStateOf(null)
        val events = mutableListOf<String>()
        private var nextInstance = 0

        fun instance(): Int = ++nextInstance
        fun record(event: String) { events += "${time()} $event" }
    }

    private fun AniComposeUiTest.mount(trace: Trace, enforceOwnership: Boolean = true) {
        mainClock.autoAdvance = false
        setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(inputMode) { inputMode.requestInputMode(InputMode.Keyboard) }
            TvApplicationTheme(ThemeSettings.Default.seedColor, languageTag = "en") {
                val pages = { page: String ->
                    NavEntry(page, contentKey = "entry-$page") {
                        val memory = if (trace.useMemory) trace.memories.getOrPut(page) { TvFocusMemory() } else null
                        CompositionLocalProvider(LocalTvFocusMemory provides memory) { Page(page, trace) }
                    }
                }
                NavDisplay(
                    backStack = trace.stack,
                    onBack = { trace.stack.removeLastOrNull() },
                    entryDecorators = buildList {
                        add(rememberSaveableStateHolderNavEntryDecorator())
                        if (enforceOwnership) add(rememberTvNavigationFocusDecorator(pages(trace.stack.last()).contentKey))
                    },
                    transitionSpec = { fadeIn(tween(1_000)) togetherWith fadeOut(tween(1_000)) },
                    popTransitionSpec = { fadeIn(tween(1_000)) togetherWith fadeOut(tween(1_000)) },
                    entryProvider = pages,
                )
            }
        }
    }

    @Composable
    private fun Page(page: String, trace: Trace) {
        val focus = rememberTvFocusScope()
        focus.Resolver()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val coroutineScope = rememberCoroutineScope()
        val instance = remember { trace.instance() }
        var placed by remember { mutableStateOf(false) }
        var lastFocused by rememberSaveable { mutableStateOf("$page-0") }
        DisposableEffect(instance, lifecycle) {
            trace.scopes[page] = focus
            trace.coroutines[page] = coroutineScope
            trace.lifecycles[page] = lifecycle
            trace.record("attach $page instance=$instance lifecycle=${lifecycle.currentState}")
            val observer = LifecycleEventObserver { _, event -> trace.record("$page $event") }
            lifecycle.addObserver(observer)
            onDispose {
                trace.record("detach $page instance=$instance")
                lifecycle.removeObserver(observer)
            }
        }
        if (page !in trace.suspendInitialFocus) {
            if (trace.useMemory) focus.InitialFocus(TvFocusKey("$page-0"))
            else focus.InitialFocus {
                trace.record("request $lastFocused")
                TvFocusKey(lastFocused)
            }
        }
        Column(Modifier.fillMaxSize().padding(48.dp).testTag("$page-page")
            .then(if (trace.modalPage == page) Modifier.tvModalUnderlay(true) else Modifier)
            .tvFocusNavSignal(focus).onGloballyPositioned {
                if (!placed) trace.record("place $page instance=$instance")
                placed = true
            }) {
            repeat(2) { index ->
                val key = "$page-$index"
                if (key in trace.hiddenEntries) return@repeat
                TvOptionRow(key, modifier = Modifier.tvFocusAnchor(focus, TvFocusKey(key))
                    .tvFocusMemorable(key)
                    .onFocusChanged {
                        trace.record("focus $key=${it.isFocused} instance=$instance")
                        if (it.isFocused) lastFocused = key
                    }.testTag(key)) { trace.record("click $key") }
            }
            if (trace.showDeferredAnchor) {
                TvOptionRow("Deferred", modifier = Modifier.tvFocusAnchor(focus, TvFocusKey("$page-deferred"))
                    .testTag("$page-deferred")) { }
            }
        }
        if (trace.showSiblingControl) {
            val sibling = rememberTvFocusScope()
            sibling.Resolver()
            DisposableEffect(sibling) {
                trace.scopes["$page-sibling"] = sibling
                onDispose { }
            }
            Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.BottomEnd) {
                TvOptionRow("Sibling", modifier = Modifier.tvFocusAnchor(sibling, TvFocusKey("sibling"))
                    .testTag("$page-sibling")) { }
            }
        }
        if (trace.modalPage == page) {
            TvModalOverlay(onClose = { trace.modalPage = null }, background = {}) {
                Page("$page-modal", trace)
            }
        }
    }

    private fun AniComposeUiTest.focusBefore(tag: String, deadline: Long) {
        waitUntil(timeoutMillis = 5_000) {
            mainClock.advanceTimeByFrame()
            assertTrue(mainClock.currentTime < deadline, "Focus missed the transition deadline for $tag")
            onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun AniComposeUiTest.down(tag: String) {
        onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionDown) }
        mainClock.advanceTimeByFrame()
    }
}
