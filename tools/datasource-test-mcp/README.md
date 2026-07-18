# datasource-test-mcp

An HTTP MCP server for developing and validating Animeko media sources.

The transport is a stateless subset of the MCP Streamable HTTP spec: JSON-RPC messages are POSTed to
`/mcp` and answered as `application/json` (no SSE streams, no sessions). The server binds to
`127.0.0.1` by default and rejects non-local `Origin` headers to prevent DNS rebinding.

> **Agent 操作手册**: [.agents/skills/datasource-test](../../.agents/skills/datasource-test/SKILL.md)
> 描述了「拿到一份数据源 JSON 配置 → 分层测试 → 定位修复 → 出报告」的完整流程,
> 包括 server 启动/直连 HTTP 调用方式、每步失败的判读表、行为红线,
> 以及站点超出现有引擎能力时的「能力进化」闭环 (缺口取证 → 文档 → subagent 产出 patch → 复测)。

## Capabilities

### 信息能力 (Ani API)

- `search_subjects` — 用名字搜索番剧, 返回 subjectId/名称/放送日期, 可选返回剧集列表.
- `get_subject_episodes` — 按 subjectId 获取完整剧集列表 (episodeId, sort, 名称).
- `get_trends` — 获取当前热门番剧排行, 适合挑选知名条目来测试数据源.

### 数据源能力 (Selector / CSS-selector 数据源)

- `validate_selector_config` — 离线校验 selector 配置 JSON: 必填字段, CSS selector/JsonPath/正则语法,
  命名分组等. 接受 App 导出格式 / 裸 arguments / 裸 searchConfig / 订阅列表四种形态.
- `selector_resolve_episode` — 提供 subjectId+episodeId+配置, 自动跑 `SelectorMediaSourceEngine`
  全流程 (searchSubjects → selectSubjects → searchEpisodes → selectEpisodes → selectMedia),
  返回每个步骤的 trace 便于定位问题. **默认**继续做 WebView 视频解析与 VLC 播放探测
  (会启动 CEF 与播放器); 只测解析层传 `extractVideo=false`.
- `selector_run_step` — 单步执行任意引擎步骤: 支持离线传 HTML 调试 selector, 离线测 matchVideo 正则,
  以及真实 WebView 拦截视频 URL.
- `get_selector_engine_docs` — 返回引擎步骤文档 (源文件: [selector-engine-docs.md](src/main/resources/selector-engine-docs.md)).

### 视频能力

- `probe_video` — 探测最终视频 URL: HTTP 可达性 + **用 Animeko 桌面端同款播放器 (mediamp-vlc) 真实播放**.
  默认弹出 Compose 测试窗口实时显示画面 (`showWindow=false` 可关), 实际播放几秒验证可播放性,
  并从 VLC 读取真实媒体信息 (分辨率/时长/编码/帧率/码率).
  需要系统安装 VLC 3.0.18 (macOS: `/Applications/VLC.app`); 未安装时降级为仅 HTTP 探测.

### 兼容保留

- `test_subject_episode_source` — 任意数据源工厂 (dmhy/mikan/selector/...) 的端到端测试.
- `test_resource_page_url` — 任意播放页的 WebView 视频解析 + 探测.

`probe_video_url` 已被 `probe_video` 取代 (输入兼容, 输出更丰富).

## Code layout

按能力分包 (`src/main/kotlin/.../datasourcetestmcp/`):

- `mcp/` — HTTP MCP server (Streamable HTTP/JSON-RPC) 与工具注册表
- `info/` — 信息能力: Ani API 番剧/剧集查询
- `selector/` — 数据源能力: 配置解析校验 + 引擎全流程/单步执行
- `resolver/` — WebView 视频解析管线 (播放页 → 视频 URL) 与逐线路测试
- `video/` — 视频能力: HTTP 探测 + ffprobe/ffmpeg 分析
- `source/` — 通用数据源端到端测试 (dmhy/mikan/... 工厂注册, 域名诊断)
- 根包 — 入口 `Main.kt` 与共享模型 (`StageResult` 等)

## Typical debugging flow

1. `validate_selector_config` 排除配置语法错误 (没有配置时, `get_selector_engine_docs`
   返回的文档里有一份最小可用示例);
2. `search_subjects` / `get_subject_episodes` 找到目标 episodeId
   (注意: 这是 Ani API 元数据查询, 与引擎步骤 `searchSubjects` 是两回事);
3. `selector_resolve_episode` 跑全流程, 看哪一步的 trace 先失败 (只测解析层传 `extractVideo=false`);
4. `selector_run_step` 单独重跑该步骤 (支持直接传 HTML 离线迭代 selector);
5. `probe_video` 验证解析出的视频 URL 真实可播放.

站点开了人机验证 (trace 里 captchaKind 非空) 时, 本工具无法过验证码, 请改用 App 内设置页的数据源测试器.

## Handshake Failure Hints

When datasource fetch fails with an SSL/TLS handshake-style error, `test_subject_episode_source` does one
extra diagnostic step:

1. Extract the current search host from the datasource config
2. Query Bing RSS with the datasource name and host token
3. Return possible replacement hosts in `media_fetch.sources[].handshakeFailureDomainHint`

This is a hint path only. It does not rewrite the datasource config automatically.

## Metadata Lookup

`search_subjects` / `get_subject_episodes` / `selector_resolve_episode` / `test_subject_episode_source`
fetch subject and episode metadata from the Ani API. Ani API is the metadata source of truth.

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

## Multi-Channel Behavior

`test_subject_episode_source` and `selector_resolve_episode` test candidates channel by channel:

- `all_channels`: test every candidate channel (default for `test_subject_episode_source`)
- `first_success`: stop after the first channel that passes both resolve and playback probe
  (default for `selector_resolve_episode`)

Top-level `ok` means at least one tested channel passed playback probing.

## Run

```bash
./gradlew :tools:datasource-test-mcp:installDist
./tools/datasource-test-mcp/build/install/datasource-test-mcp/bin/datasource-test-mcp
```

Defaults to `http://127.0.0.1:8264/mcp`; override with `--host <host>` / `--port <port>`.

Point your MCP client at the running server, e.g. in `.mcp.json`:

```json
{
  "mcpServers": {
    "ani-datasource-test": {
      "type": "http",
      "url": "http://127.0.0.1:8264/mcp"
    }
  }
}
```

The `.agents/skills/datasource-test` skill looks for this MCP under the name `ani-datasource-test`
(tools like `mcp__ani-datasource-test__selector_resolve_episode`); keep the name in sync if you change it.

## Test

```bash
./gradlew :tools:datasource-test-mcp:test
```
