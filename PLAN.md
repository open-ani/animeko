# Current Project Plan: AniList (Mihon-Style Tracker) Integration

> This file is the active entrypoint for agents and developers working on the Tracker / AniList syncing feature.
> Full technical spec, TDD task list: [`docs/dev/trackers/anilist-sync-plan.md`](docs/dev/trackers/anilist-sync-plan.md).
> **GitHub Issue**: [open-ani/animeko#3427](https://github.com/open-ani/animeko/issues/3427)

## Scope

- Bangumi remains the primary catalog and metadata core. This feature makes zero changes to `ani-api-server` or
  to Bangumi's role as the canonical ID/metadata source.
- **AniList login uses the official Auth PIN flow**: the app opens `https://anilist.co/api/v2/oauth/authorize`
  in the system browser (reusing `browserNavigator.openBrowser`, the same call the existing Bangumi login uses),
  AniList's own redirect page shows the user a token, and the user pastes it into the app. This needs no
  Android intent-filter, no iOS `ASWebAuthenticationSession`, no desktop loopback HTTP listener, and no
  `ani-api-server` involvement.
- Tokens are treated as opaque with an assumed ~365-day lifetime (AniList's implicit-grant response carries no
  real `expires_in`, and AniList issues no refresh tokens); expiry is handled reactively — a failed authenticated
  call triggers relogin — never proactively.
- **ID mapping subscribes to `bangumi-data`'s JSON** (CC BY 4.0, ~93% of entries carry both a `bangumi` and an
  `aniList` site ID in one record) through animeko's existing subscription mechanism (the same shape as
  `MediaSourceSubscriptionUpdater` / `PeerFilterSubscription`, fetched via the existing
  `SubscriptionsAniApi.proxy(url)` endpoint). Mapping storage is `{bangumiId, site, externalId}`, not
  AniList-specific, so a future MAL/Kitsu tracker reuses the same subscription and table.
- The hand-rolled AniList GraphQL client has no Apollo Kotlin or other codegen dependency — only four fixed
  queries/mutations are needed.
- Bind UX is Mihon-style manual search-and-confirm, with the `bangumi-data` mapping table used as an
  auto-suggested match shown first.
- Built to actually run and be used (by the author and their circle), not a minimal demo PR — see the plan doc's
  "why sliced this way" note for how that shaped the PR boundaries below.

## PR Roadmap

### [x] PR 1 — Module scaffold, DB schema, AniList API client (no UI, fully unit-tested)
- [x] `tracker:api` + `tracker:anilist` Gradle modules (mirrors the existing `datasource:api` / `datasource:bangumi` split).
- [x] `TrackerAccountEntity`, `TrackerBindingEntity`, `TrackerMappingEntity` in `AniDatabase` v23 → v24 (`AutoMigration`, purely additive).
- [x] `AniListRateLimitPlugin` (Ktor `HttpClient` plugin, 25 req/min — Mihon's proven margin under AniList's 90/min cap).
- [x] Hand-rolled AniList GraphQL client: `ViewerProfile`, `SearchAnime`, `GetMediaEntry`, `SaveMediaListEntry`.
- Not yet built (belongs with PR 2's login flow, needs `app-data` → `tracker:anilist` wiring that doesn't exist until then): the concrete `TrackerService` implementation and `isAuthorized()`. `tracker:anilist` currently exposes the token-expiry check as a pure function (`isAniListTokenLikelyExpired`) for that future implementation to call.

### [x] PR 2 — Login and bind-and-sync (the actual user-visible feature)
- [x] AniList PIN/OAuth login UI + state machine, Settings "Trackers" card.
- [x] `TrackingRegistry` / `TrackingCoordinator`, hooked into episode watched events.
- [x] Subject-details tracking card + manual search-and-bind, status, progress, score.
- [x] **Android**: Real device smoke test passed.
- [x] **macOS Desktop**: Verified on device with dedicated DataStore token storage and local HTTP OAuth callback.
- [x] **iOS**: Verified on iOS Simulator (`iPhone 17`) with `NSUserDefaults` token/binding storage and Safari `ani://` redirect callback.
- [x] **Windows**: Explicitly excluded from this PR.
- [x] **Correctness/UX follow-ups found via real use (2026-09-19), TRK-23..30 -- see `anilist-sync-plan.md` §4.3
  for full detail, Mihon citations, and design.**
  1. [x] Status never auto-transitions (Reading on first progress, Completed at total) -- `syncProgress` always
     sent `status = null`; confirmed against a real bound entry, not just a code read. Fixed (2026-09-19):
     `computeAutoTrackerStatus` in `TrackerManager.kt`, `TrackerService.syncProgress` now takes an optional
     status and returns the tracker's raw status string so it can be persisted.
  2. [x] Binding after already watching locally doesn't backfill existing progress to AniList. Fixed (2026-09-19):
     `TrackerBindingState.confirm()`'s `localWatchedEpisodesProvider`.
  3. [x] Bind sheet defaulted to Bangumi's Chinese title (`displayName`) as the search query, which AniList's
     search doesn't index -- switched to `nameOrNameCn` (original title, falling back to Chinese only if blank)
     plus a "try English/Romaji" hint on empty results (2026-09-19). Unconditional -- independent of the new
     "show original title" display setting below.
  4. [x] Bound state was just an "Unbind" button with no visibility into current status/progress and no manual
     correction -- Mihon's real shipping UI (checked live on a second device) shows an editable status/progress/
     score card per bound tracker. Fixed (2026-09-19): `BoundTrackerCard` in `TrackerBindingBottomSheet.kt`
     (single-tracker still, no UI generalization needed). **This closes out the TRK-23..30 follow-up batch.**

- [x] **App-wide "show original title" display setting (2026-09-19)** -- a separate, general-purpose feature
  raised alongside the TRK-28 fix above, *not* itself a TRK item: a `UISettings.subjectAppearance.useOriginalTitle`
  toggle (Settings > App > "Anime title") that swaps which of `name`/`nameCn` is primary across subject
  headers, collection lists, related-subjects row, and the followed-subjects carousel. Does not affect TRK-28's
  bind-sheet search default, which always uses the original title regardless of this setting.
  - **Coverage extended (2026-09-19, same day)** after the user found gaps on the Explore page and episode
    names: added `EpisodeInfo`/`LightEpisodeInfo`/`LightSubjectInfo.nameOrNameCn`/`preferredDisplayName`
    (mirroring `SubjectInfo`'s), applied at `EpisodeGrid`, `PaginatedEpisodeList`, `EpisodeListSection`,
    `EpisodeDetails`, and via an original-title twin field on the non-Compose presentation builders
    `AiringScheduleItemPresentation` (schedule tab), `SubjectPreviewItemInfo` (search results), and
    `PlayingEpisodeSummary` (video player's now-playing summary). Deliberately still **not** applied to
    `EpisodeListUiState`, `DanmakuLoader`, `AddDownloadUseCase`, `RememberPlayProgressExtension`,
    `WatchTogetherPlayerExtension`, `DownloadManagementViewModel` (danmaku-matching/download-filename risk,
    same reasoning as the original scope decision), or the Trending carousel (`TrendingSubjectInfo`/
    `AniTrendingSubject` genuinely only carry `nameCn` server-side -- no client-side fix possible).
  - **Recommendation section fixed too (2026-09-19, later same day)** after the user reported it specifically:
    unlike Trending, the recommendation API (`AniSubjectRecommendation`) *does* return an original
    `subjectName` -- `RecommendedSubjectInfo` (`data/models/recommend/RecommendedItemInfo.kt`) and
    `RecommendationRepository.toRecommendedSubjectInfo()` were just discarding it, only ever storing
    `nameCn`. Added a `name` field + `preferredDisplayName()`, wired into
    `RecommendedSubjectsVerticalGrid.kt`'s render. While auditing this, found and fixed the same
    "original title discarded" bug in three subject-detail navigation placeholders that only ever set
    `nameCN`/`name` to the same Chinese value: `RecommendedSubjectInfo.toNavPlaceholder()`
    (`ExplorationScreen.kt`), the Schedule tab's `onClickItem` (`AniAppContent.kt`, now uses
    `AiringScheduleItemPresentation.subjectOriginalTitle`), and `SearchViewModel.viewSubjectDetails()`/
    `SearchPageEffect.NavigateToSubjectDetails` (search results tap-through, now threads
    `SubjectPreviewItemInfo.originalTitle` all the way to `SearchScreen.kt`'s placeholder). These placeholders
    are transient (replaced by the real `SubjectInfo` once the details page loads) but previously flashed
    the Chinese title even with the setting on. `RelatedSubjectsRow.kt`, `EpisodeDetails.kt`'s recommendation
    click, and `PeopleDetailsSections.kt` were already correct and needed no change.

### [ ] PR 3 — Mapping subscription (auto-suggest on bind)
- `TrackerMappingSubscription` (mirrors `PeerFilterSubscription`'s shape exactly: built-in default URL, `updatePeriod`, `lastUpdated`, user-addable URLs).
- Wired as the auto-suggest source in PR 2's bind sheet; additive and independently mergeable since manual bind already makes PR 2 fully usable without it.

## Not doing

- Server-side changes to `ani-api-server` of any kind.
- A generic multi-tracker `TrackerService` UI shell for MAL/Kitsu before AniList ships — the interface is designed to allow it later, but no second tracker is being built now.
