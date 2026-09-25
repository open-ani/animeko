/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.foundation_loading
import me.him188.ani.app.ui.lang.settings_load_failed
import me.him188.ani.app.ui.lang.settings_media_advanced_settings
import me.him188.ani.app.ui.lang.settings_media_source_subscription
import me.him188.ani.app.ui.lang.settings_mediasource_retry
import me.him188.ani.app.ui.lang.subject_details_empty
import me.him188.ani.app.ui.lang.tv_settings_subscriptions_description
import me.him188.ani.app.ui.settings.tabs.media.MediaSelectorWorkflowPreview
import me.him188.ani.app.ui.settings.tabs.media.rememberMediaSelectorWorkflowDemoState
import me.him188.ani.app.ui.settings.tabs.theme.ThemePalette
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvBackKey
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.focus.tvFocusMemorable
import me.him188.ani.tv.ui.foundation.focus.tvFocusNavSignal
import me.him188.ani.tv.ui.foundation.layout.tvModalUnderlay
import me.him188.ani.tv.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun sectionKey(section: TvSettingsSection) = TvFocusKey("settings-section-$section")
internal fun settingsItemKey(id: String) = TvFocusKey("settings-item-$id")

@Composable
fun TvSettingsScreen(
    state: TvSettingsUiState,
    onIntent: (TvSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    displayModes: List<TvSettingsDisplayMode> = emptyList(),
    onOpenUrl: (String) -> Unit = {},
    onImport: () -> Unit = {},
) {
    val focus = rememberTvFocusScope()
    var section by rememberSaveable { mutableStateOf(TvSettingsSection.Appearance) }
    var detailFocused by rememberSaveable { mutableStateOf(false) }
    var aboutPage by rememberSaveable { mutableStateOf(TvSettingsAboutPage.Overview) }
    var extra by rememberSaveable { mutableStateOf<TvSettingsExtra?>(null) }
    var lastDetailItems by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var dialog by remember { mutableStateOf<TvSettingsDialog?>(null) }
    var navigationGeneration by remember { mutableIntStateOf(0) }
    var pendingFocus by remember { mutableStateOf<Pair<TvFocusKey, Int>?>(null) }
    var focusRevision by remember { mutableIntStateOf(0) }
    var paletteBounds by remember { mutableStateOf(mapOf<Int, Rect>()) }
    val savedContent = rememberSaveableStateHolder()
    val pageId = "$section-$aboutPage"
    focus.Resolver()

    fun sendFocus(target: TvFocusKey) {
        pendingFocus = target to focus.userNavGeneration
        focusRevision++
    }
    LaunchedEffect(focusRevision) {
        pendingFocus?.let { (target, generation) ->
            if (focus.userNavGeneration == generation) focus.request(target)
        }
        pendingFocus = null
    }
    fun openPage(page: TvSettingsAboutPage) {
        aboutPage = page
        navigationGeneration++
        if (page == TvSettingsAboutPage.Licenses) onIntent(TvSettingsIntent.LoadLibraries)
        sendFocus(settingsItemKey("detail-entry"))
    }
    fun openExtra(page: TvSettingsExtra) {
        extra = page
        navigationGeneration++
        sendFocus(settingsItemKey("extra-entry"))
    }
    val backGeneration = navigationGeneration
    val back: () -> Unit = {
        if (backGeneration == navigationGeneration && dialog == null) {
            navigationGeneration++
            val currentExtra = extra
            if (currentExtra != null) {
                extra = null
                sendFocus(settingsItemKey(currentExtra.origin))
            } else if (aboutPage != TvSettingsAboutPage.Overview) {
                val origin = when (aboutPage) {
                    TvSettingsAboutPage.Licenses -> "licenses"
                    TvSettingsAboutPage.Developers -> "developers"
                    else -> "acknowledgements"
                }
                aboutPage = if (aboutPage == TvSettingsAboutPage.Licenses) {
                    TvSettingsAboutPage.Acknowledgements
                } else TvSettingsAboutPage.Overview
                sendFocus(settingsItemKey(origin))
            } else {
                detailFocused = false
                sendFocus(sectionKey(section))
            }
        }
    }
    BackHandler(detailFocused && dialog == null, onBack = back)
    var directionalAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var directionalKey by remember { mutableStateOf<Key?>(null) }
    val content = TvSettingsItems(state, onIntent) { dialog = it }
    when {
        state.loadFailed -> content.action(
            "retry", stringResource(Lang.settings_mediasource_retry),
            description = stringResource(Lang.settings_load_failed), opensDetail = false,
        ) { onIntent(TvSettingsIntent.Retry) }
        !state.loaded -> content.action("loading", stringResource(Lang.foundation_loading), opensDetail = false) {}
        else -> when (section) {
            TvSettingsSection.Appearance -> content.appearance()
            TvSettingsSection.Theme -> content.palette()
            TvSettingsSection.Player -> content.player(TvSettingsPlayerPage.Overview, displayModes) { page ->
                openExtra(TvSettingsExtra.entries.first { it.playerPage == page })
            }
            TvSettingsSection.Sources -> content.sources { openExtra(TvSettingsExtra.Subscriptions) }
            TvSettingsSection.Watching -> content.watching { openExtra(TvSettingsExtra.WatchingAdvanced) }
            TvSettingsSection.BitTorrent -> content.bitTorrent()
            TvSettingsSection.About -> content.about(aboutPage, ::openPage)
        }
    }
    val items = content.items.withEmptyPlaceholder()
    val initialDetailId = if (section == TvSettingsSection.Theme) {
        items.firstOrNull { it.selected }?.id
    } else lastDetailItems[pageId]?.takeIf { id -> items.any { it.id == id && it.enabled } }
    val detailEntry = settingsItemKey(initialDetailId ?: "detail-entry")
    focus.InitialFocus(if (extra != null) settingsItemKey("extra-entry") else if (detailFocused) detailEntry else sectionKey(section))
    fun enterDetail() {
        if (detailFocused) return
        detailFocused = true
        navigationGeneration++
        sendFocus(detailEntry)
    }
    fun atPaletteRowStart(): Boolean {
        val current = paletteBounds.entries.firstOrNull { focus.isFocused(settingsItemKey("palette-${it.key}")) }
            ?.value ?: return true
        return paletteBounds.values.none { it.top == current.top && it.left < current.left }
    }

    Box(modifier.fillMaxSize().clipToBounds().testTag("tv-settings").tvFocusNavSignal(focus)) {
        TvSettingsLayout(
            modifier = Modifier.tvModalUnderlay(dialog != null)
                .tvBackKey(detailFocused && dialog == null, back)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == directionalKey) {
                        val action = directionalAction
                        directionalKey = null
                        directionalAction = null
                        action?.invoke()
                        true
                    } else if (event.type == KeyEventType.KeyDown && dialog == null) {
                        val action = when {
                            event.key == Key.DirectionRight && !detailFocused -> ::enterDetail
                            event.key == Key.DirectionRight && extra == null -> items.firstOrNull {
                                it.opensPane && focus.isFocused(settingsItemKey(it.id))
                            }?.onClick
                            event.key == Key.DirectionLeft && detailFocused &&
                                (section != TvSettingsSection.Theme || atPaletteRowStart()) -> back
                            else -> null
                        }
                        if (action != null) {
                            if (directionalKey == null) {
                                directionalKey = event.key
                                directionalAction = action
                            }
                            true
                        } else false
                    } else false
                },
            sections = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).tvModalUnderlay(detailFocused),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TvSettingsSection.entries.forEach { entry ->
                        TvOptionRow(
                            title = stringResource(entry.title),
                            icon = when (entry) {
                                TvSettingsSection.Appearance -> Icons.Outlined.Settings
                                TvSettingsSection.Theme -> Icons.Outlined.Palette
                                TvSettingsSection.Player -> Icons.Outlined.SmartDisplay
                                TvSettingsSection.Sources -> Icons.Outlined.Storage
                                TvSettingsSection.Watching -> Icons.Outlined.Subscriptions
                                TvSettingsSection.BitTorrent -> Icons.Outlined.Storage
                                TvSettingsSection.About -> Icons.Outlined.Info
                            },
                            selected = section == entry,
                            showSelectionIndicator = false,
                            modifier = Modifier.testTag("tv-settings-section-$entry")
                                .tvFocusAnchor(focus, sectionKey(entry))
                                .tvFocusMemorable("settings-section-$entry")
                                .focusProperties { canFocus = !detailFocused && dialog == null }
                                .onFocusChanged {
                                    if (it.isFocused && !detailFocused && section != entry) {
                                        section = entry
                                        aboutPage = TvSettingsAboutPage.Overview
                                    }
                                },
                        ) {
                            section = entry
                            aboutPage = TvSettingsAboutPage.Overview
                            enterDetail()
                        }
                    }
                }
            },
            detail = {
                Text(
                    if (section == TvSettingsSection.About) aboutPage.title() else stringResource(section.title),
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                )
                if (extra == null) Text(
                    if (aboutPage != TvSettingsAboutPage.Overview) stringResource(section.title) else section.description(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                savedContent.SaveableStateProvider(pageId) {
                    TvSettingsItemsPane(
                        items, focus, detailFocused && extra == null && dialog == null,
                        lazy = section == TvSettingsSection.About && aboutPage == TvSettingsAboutPage.Licenses,
                        selectedOrigin = extra?.origin,
                        onFocused = { lastDetailItems = lastDetailItems + (pageId to it) },
                        palette = if (section == TvSettingsSection.Theme && state.loaded) {{
                            ThemePalette(
                                selectedColor = state.theme.seedColor,
                                onSelect = { color ->
                                    onIntent(TvSettingsIntent.Theme { copy(seedColorValue = color.value, useDynamicTheme = false) })
                                },
                                colorModifier = { index ->
                                    Modifier.testTag("tv-settings-item-palette-$index")
                                        .tvFocusAnchor(focus, settingsItemKey("palette-$index"))
                                        .tvFocusMemorable("settings-item-palette-$index")
                                        .focusProperties { canFocus = detailFocused && dialog == null }
                                        .onGloballyPositioned { paletteBounds = paletteBounds + (index to it.boundsInRoot()) }
                                },
                            )
                        }} else null,
                    )
                }
            },
            extra = extra?.let { page -> {
                val workflow = if (page == TvSettingsExtra.WatchingAdvanced) {
                    rememberMediaSelectorWorkflowDemoState(state.selector.fastSelectWebKind)
                } else null
                Text(
                    page.playerPage?.title() ?: stringResource(if (page == TvSettingsExtra.Subscriptions) {
                        Lang.settings_media_source_subscription
                    } else Lang.settings_media_advanced_settings),
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                )
                if (page == TvSettingsExtra.Subscriptions) Text(
                    stringResource(Lang.tv_settings_subscriptions_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (workflow != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MediaSelectorWorkflowPreview(workflow,
                        Modifier.fillMaxWidth(.5f).testTag("tv-settings-selector-workflow"))
                }
                val extraContent = TvSettingsItems(state, onIntent) { dialog = it }
                if (page.playerPage != null) extraContent.player(page.playerPage, displayModes) {}
                else if (workflow != null) extraContent.watchingAdvanced(workflow)
                else extraContent.subscriptions()
                savedContent.SaveableStateProvider("extra-$page") {
                    TvSettingsItemsPane(extraContent.items.withEmptyPlaceholder(), focus, dialog == null, paneId = "extra")
                }
            } },
        )
        dialog?.let { current ->
            TvSettingsEditor(
                current, focus, onIntent, onOpenUrl, onImport,
                onClose = { deleted ->
                    if (dialog === current) {
                        dialog = null
                        sendFocus(settingsItemKey(if (deleted) "regex-add" else current.origin))
                    }
                },
            )
        }
    }
}

@Composable
private fun List<TvSettingItem>.withEmptyPlaceholder(): List<TvSettingItem> = ifEmpty {
    listOf(TvSettingItem("empty", stringResource(Lang.subject_details_empty), opensDetail = false, onClick = {}))
}

@Composable
private fun ColumnScope.TvSettingsItemsPane(
    items: List<TvSettingItem>,
    focus: TvFocusScope,
    active: Boolean,
    paneId: String = "detail",
    lazy: Boolean = false,
    selectedOrigin: String? = null,
    onFocused: (String) -> Unit = {},
    palette: (@Composable () -> Unit)? = null,
) {
    val modifier = Modifier.weight(1f).fillMaxWidth()
        .tvFocusAnchor(focus, settingsItemKey("$paneId-entry"))
        .focusGroup().testTag("tv-settings-$paneId-items").tvModalUnderlay(!active)
    val ids = items.map { it.id }
    var previousIds by remember { mutableStateOf(ids) }
    // Capture focus ownership before a loading/retry item leaves composition.
    val recoveryGeneration = remember(ids) {
        focus.userNavGeneration.takeIf {
            previousIds.any { it !in ids && focus.isFocused(settingsItemKey(it)) }
        }
    }
    SideEffect { previousIds = ids }
    LaunchedEffect(ids) {
        if (active && recoveryGeneration == focus.userNavGeneration) {
            focus.request(settingsItemKey(items.firstOrNull { it.selected }?.id ?: items.first().id))
        }
    }
    if (lazy) {
        LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items, key = { it.id }) { item -> TvSettingsRow(item, focus, active) { onFocused(item.id) } }
        }
    } else Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (palette != null) palette()
        else items.forEach { item ->
            key(item.id) {
                TvSettingsRow(
                    item.copy(
                        selected = item.selected || item.id == selectedOrigin,
                        description = item.description.takeIf { selectedOrigin == null },
                    ),
                    focus, active,
                ) { onFocused(item.id) }
            }
        }
    }
}

@Composable
private fun TvSettingsRow(item: TvSettingItem, focus: TvFocusScope, active: Boolean, onFocused: () -> Unit = {}) {
    TvOptionRow(
        title = item.title,
        value = item.value,
        supportingText = item.description,
        checked = item.checked,
        selected = item.selected,
        showSelectionIndicator = false,
        enabled = item.enabled,
        icon = item.icon,
        trailingIcon = if (item.checked == null && item.opensDetail) Icons.AutoMirrored.Rounded.KeyboardArrowRight else null,
        leadingContent = item.artwork?.let { artwork ->
            { Image(painterResource(artwork), null,
                Modifier.size(32.dp).clip(item.artworkShape).testTag("tv-settings-artwork-${item.id}")) }
        },
        modifier = Modifier.testTag("tv-settings-item-${item.id}")
            .tvFocusAnchor(focus, settingsItemKey(item.id))
            .tvFocusMemorable("settings-item-${item.id}")
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusProperties { canFocus = active },
        onClick = item.onClick,
    )
}
