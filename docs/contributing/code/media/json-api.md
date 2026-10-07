# JSON API media sources

`json-api` version 1 is a declarative media source for HTTP APIs returning JSON.
It supports browsing, automatic subject matching, multiple instances and fresh
playback URL resolution. The settings editor provides grouped forms for basic
information, search, channels/episodes, playback and automatic matching. An
advanced JSON editor accepts either an arguments object or a single-source
`{"mediaSources":[...]}` import. Both use the same configuration and validate it
before saving. Incomplete JSON request bodies and numeric fields remain editable
without losing the draft. Subscription configurations are read-only.

Wide windows show configuration and testing side by side; compact windows switch
between them. The tester browses subjects and channel/episode lists and resolves
fresh playback URLs without saving the draft. It reports only the video host,
keeping signed query parameters out of the result display.

The add-source dialog includes a **稀饭动漫 Next** preset. Selecting it persists
a complete, editable `json-api` configuration and opens the grouped editor;
no clipboard import or account login is required. The generic **JSON API** entry
remains available for other sites. Presets are optional and are not automatically
inserted into existing source lists.

The preset and [Xifan Next example](examples/xifan-next.json) use the site's public
publishable frontend key. This key is not a user credential. Sites may change
their API contract or key; the configuration can be edited without recompiling.
Exported configurations include headers: omit private credentials before sharing.

Public availability was verified on 2026-10-07: the unauthenticated homepage
references a JavaScript bundle containing the same `sb_publishable_` key and API
host. Search (`search_animes`), detail (`get_anime_detail`) and playback
(`issue-web-playback`) requests succeed with that key and no user cookies or
user session token. These are publicly exposed website APIs, not a documented
third-party API with a stability guarantee.

## Requests and variables

Each `request` has `url`, `method` (`GET` or `POST`), optional `headers` and an
optional structured JSON `body`. Global `headers` apply to all three requests;
per-request headers override them. Video requests use `playback.videoHeaders`
only, so API credentials are not forwarded to video hosts.

| Stage | Available variables |
| --- | --- |
| Search | `{keyword}`, `{page}`, `{pageSize}` |
| Detail | `{subjectId}`, `{subjectName}` |
| Playback | Detail variables, `{channelId}`, `{channelName}`, `{episodeId}`, `{episodeName}` |

URL substitutions are percent-encoded. In a JSON body, an entire string value
such as `"{subjectId}"` is replaced with the original JSON primitive, preserving
numeric identifiers. Embedded substitutions such as `"Bearer {channelId}"` stay
strings and are escaped by the JSON serializer. Unknown variables fail explicitly.
Templates are declarative; JavaScript execution is not supported.

## Extraction

Paths use the existing `utils/jsonpath` implementation. `search.itemsPath` selects
subjects from the search response. ID, name and aliases paths are relative to
each subject. Alias selections can contain strings or arrays of strings.

`detail.channelsPath` selects channels from the detail response. Channel ID/name
and `episodesPath` are relative to each channel; episode fields are relative to
each episode. For an API without channels, use `channelsPath: "$"` and empty
channel ID/name paths. Each object can then contain its own episode list.

Episode numbers are parsed as `EpisodeSort`. An optional kind path and prefix
map distinguish main episodes, specials and other sorts. Unmapped kinds and
missing numbers remain available for manual selection. `fetch` returns every
episode of matching subjects; the media selector performs episode filtering.
`autoMatch: false` keeps only manual browsing. `filterBySubjectName` defaults to
true and includes extracted aliases. `searchNamesCount` defaults to 3.

Pagination is optional. Page numbers start at `firstPage` (default 1), and
`pageSize` defaults to 50. Searching stops on a short page, a page with no new
IDs, or `maxPages` (default 20, at most 100).

## Playback and persistence

`playback.urlPath` selects a video URL. An optional `successPath` must resolve to
true. If the response contains per-channel candidates, configure `candidatesPath`,
`candidateIdPath` and `candidateUrlPath`; a matching channel candidate takes
precedence over the top-level URL. Resolved URLs must use HTTP(S).

Website URL templates (`search.subjectUrl`, `detail.episodeUrl`) cannot contain
fragments. Browse links and download locations carry typed identifiers in an
`ani-api` fragment. `Media.originalUrl` is the clean website URL. Media IDs use
subject, channel and episode IDs and remain stable across manual/automatic
selection. Playback reads those identifiers after an app restart and requests
fresh signed URLs through `WebVideoResolverProvider` on desktop, Android and iOS.
No local bridge process or transient in-memory lookup is required.
