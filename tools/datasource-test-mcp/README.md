# datasource-test-mcp

An stdio MCP server for validating Animeko media sources end to end.

## What It Tests

The MCP exposes three tools:

- `test_subject_episode_source`
  Pulls subject and episode metadata, fetches media candidates from a media source, resolves a final video URL with the real webview-based resolver engine, then probes the resolved video URL.
- `test_resource_page_url`
  Resolves a page URL with the real webview-based resolver engine, then probes the resolved video URL.
- `probe_video_url`
  Probes a final video URL such as `m3u8` or `mp4`.

The runtime resolver path is webview-based only. It does not use a static HTML parser.

## Handshake Failure Hints

When datasource fetch fails with an SSL/TLS handshake-style error, the MCP now does one extra diagnostic step:

1. Extract the current search host from the datasource config
2. Query Bing RSS with the datasource name and host token
3. Return possible replacement hosts in `media_fetch.sources[].handshakeFailureDomainHint`

This is a hint path only. It does not rewrite the datasource config automatically.

## Metadata Lookup

`test_subject_episode_source` fetches subject and episode metadata from the Ani API, then builds the local
`MediaFetchRequest` from that response. Ani API is the metadata source of truth.

## Playback Header Path

For web media sources, the playback path in the client is:

1. `WebVideoMatcher` produces a `WebVideo` with `headers`
2. resolver returns `HttpStreamingMediaDataProvider`
3. `HttpStreamingMediaDataProvider.open()` creates `UriMediaData`
4. player and cache downloader consume `UriMediaData.headers`

This matches the app's current playback model:

- Android ExoPlayer uses the header map as default request properties.
- Desktop VLC uses `User-Agent` and `Referer`.
- iOS AVKit passes the header map through `AVURLAssetHTTPHeaderFieldsKey`.

## E-ACG Validation Notes

Validated against the `E-ACG` source from:

- `https://sub.creamycake.org/v1/css1.json`

For `间谍过家家` season 1 episode 1:

- metadata lookup works
- media fetch works
- real webview resolution works
- final probe currently fails with downstream `403 The region has been denied.`

This is not currently explained by missing `Referer` handling in the client playback path. The app already supports playback headers. The remaining gap is more likely downstream geo restriction or, for some sites, missing browser session cookies.

## Multi-Channel Behavior

`test_subject_episode_source` now tests candidates channel by channel by default.

The default mode is `all_channels`:

- it sorts all web candidates
- it resolves each candidate in order
- it probes every candidate that resolves to a final video URL
- it returns per-channel results in `channelResults`

An optional `candidateTestMode` input is available:

- `all_channels`: test every candidate channel
- `first_success`: stop after the first channel that passes both resolve and playback probe

Top-level `ok` means at least one tested channel passed playback probing.

## Run

```bash
./gradlew :tools:datasource-test-mcp:installDist
./tools/datasource-test-mcp/launcher
```

Use [launcher](/Users/him188/Projects/animeko/ani/tools/datasource-test-mcp/launcher) as the MCP command so Codex starts the built launcher directly instead of invoking Gradle on every MCP startup.

## Test

```bash
./gradlew :tools:datasource-test-mcp:test
```
