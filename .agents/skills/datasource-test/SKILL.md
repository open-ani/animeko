---
name: datasource-test
description: Test and debug an Animeko media source from its JSON config using the datasource-test-mcp HTTP MCP server. Validates selector config syntax offline, runs the real SelectorMediaSourceEngine pipeline (searchSubjects → selectSubjects → searchEpisodes → selectEpisodes → selectMedia) with per-step traces, iterates CSS selectors offline against saved HTML, tests matchVideo regexes, extracts real video URLs via CEF WebView, and probes actual playback with VLC. Use when asked to 测试数据源 / 调试 selector 配置 / verify a datasource JSON works / diagnose why a source finds no candidates for an episode or fails to play. Covers web-selector (CSS selector) sources in depth; other factories (rss/dmhy/mikan/jellyfin/...) via end-to-end testing. Also defines the capability-evolution (进化) loop: when a site is provably beyond current engine/WebVideoExtractor capabilities, write a capability-gap document and hand it to an isolated subagent that implements the new capability and outputs a git patch, then re-verify. Works exclusively through the `ani-datasource-test` HTTP MCP; it never starts the server itself — if that MCP is not configured the skill stops and tells the user how to add it. When a `chrome-devtools` MCP is also available, it is used as the real-browser ground truth (rendered DOM, real network requests, click interactions) to distinguish config mistakes from genuine engine/extractor capability gaps.
---

# 用 JSON 配置测试 Animeko 数据源

给定一份数据源配置 JSON,按本 skill 的流程判定它:

- **L0 配置合法**: 字段/选择器/正则语法正确;
- **L1 解析层通**: 能为指定番剧的指定集数解析出候选播放页;
- **L2 视频层通**: 能从播放页解析出真实视频 URL;
- **L3 播放层通**: 视频 URL 真实可播放。

失败时要定位到**具体引擎步骤和配置字段**,给出修复,并复测。

失败还有一种**正当结局**: 网站形态太多,现有流程必然覆盖不了所有站点——站点可能超出
selector 引擎或 WebVideoExtractor 等组件的现有能力。此时不要硬调配置,走第 9 节的
「能力进化」流程: 取证 → 写缺口文档 → 交给隔离 subagent 实现新能力(输出 git patch) → 复测。

所有测试通过 `ani-datasource-test` MCP(由 `tools/datasource-test-mcp` 提供的 HTTP MCP server)完成。
它直接调用 App 的 `SelectorMediaSourceEngine` 真实代码,行为与 App 一致。两份参考文档:

- `tools/datasource-test-mcp/README.md` — server 能力总览、多线路语义、运行方式;
- MCP 工具 `get_selector_engine_docs` — 每个引擎步骤的输入/输出/常见问题/最小配置示例
  (源文件 `tools/datasource-test-mcp/src/main/resources/selector-engine-docs.md`)。
  **开始调试 selector 源前先调用一次它**,里面的步骤语义本 skill 不再重复。

## 0. 前置: 确认 ani-datasource-test MCP 可用(用不了就停止)

本 skill **只通过**一个名为 **`ani-datasource-test`** 的 HTTP MCP server 工作,其工具形如
`mcp__ani-datasource-test__validate_selector_config`、`mcp__ani-datasource-test__selector_resolve_episode` 等。

**开始任何测试前先确认该 MCP 已就绪**: 在当前可用工具里查找 `mcp__ani-datasource-test__*`
(如 `validate_selector_config`);无法确认时,试调用一次
`mcp__ani-datasource-test__validate_selector_config`,返回「未知工具 / 未连接 / 连接错误」即视为不可用。

- **可用** → 用这些 MCP 工具完成后续全部步骤;
- **不可用** → **立即停止本 skill**。**不要**自己 `installDist` 或后台启动 server,
  **不要**用 curl 直连 HTTP 端口绕过。向用户输出下面的「添加方法」,请他们配置好
  `ani-datasource-test` MCP 后**重新发起**测试。

### 添加方法(agent 只输出这段指引,不代为执行)

第一步——用户在 animeko 仓库根目录启动 HTTP server,并保持它运行:

```bash
./gradlew :tools:datasource-test-mcp:installDist
tools/datasource-test-mcp/launcher                                              # unix / macOS
tools\datasource-test-mcp\build\install\datasource-test-mcp\bin\datasource-test-mcp.bat   # Windows
```

默认监听 `http://127.0.0.1:8264/mcp`(可加 `--host` / `--port` 覆盖)。

第二步——在 agent 客户端里把它注册为名为 `ani-datasource-test` 的 HTTP MCP:

- **Claude Code**:
  ```bash
  claude mcp add --transport http ani-datasource-test http://127.0.0.1:8264/mcp
  ```
  或写入项目根的 `.mcp.json`:
  ```json
  { "mcpServers": { "ani-datasource-test": { "type": "http", "url": "http://127.0.0.1:8264/mcp" } } }
  ```
- **Codex**: 在 `~/.codex/config.toml` 增加(以所用 Codex 版本的文档为准):
  ```toml
  [mcp_servers.ani-datasource-test]
  url = "http://127.0.0.1:8264/mcp"
  ```

配置后重启 / 重新连接客户端使 `mcp__ani-datasource-test__*` 工具生效,再重新发起测试。

### 调用这些 MCP 工具时

- 工具返回的是一段 **JSON 文本**,解析它得到结构化数据;结果被标记为 error 时表示工具执行异常。
- server **串行**处理调用,不要并发多个工具请求。
- 整页 HTML 很大: 优先用 `url` 让工具自行抓取,只在离线迭代必须传 `html` 时才内联,并留意上下文占用。

### 可选但强烈推荐: chrome-devtools MCP(浏览器真相源)

如果当前**还**能用一个 **chrome-devtools MCP**(工具形如 `mcp__chrome-devtools__*`,如
`navigate_page` / `take_snapshot` / `list_network_requests` / `get_network_request` / `click` /
`evaluate_script` / `list_console_messages`;具体名称以所连接的 server 为准),把它与
`ani-datasource-test` **结合使用**。它不是必需的——没有也能跑本 skill,只是遇到"工具解析结果与
站点实际不符"时少了直接对照手段。**没有就别自己启动浏览器**,按现有证据推进或转第 9 节取证。

**为什么有用**: `ani-datasource-test` 引擎是**无 JS 的静态 HTTP 抓取 + 解析**,chrome-devtools
驱动的是**真实浏览器**(执行 JS、能交互)。当某一步在 MCP 工具里跑不通、站点在浏览器里却正常时,
浏览器看到的才是"真相",**两者的差异本身就是诊断信号**。

**怎么用**(按需, 不是每步都跑;详细触发点见第 4、6、9 节):

- 某解析步骤跑不通 → `navigate_page` 打开对应页面(搜索页代入真实关键词 / 条目详情页 / playUrl)→
  `take_snapshot` 看**渲染后** DOM,与 MCP 工具返回的静态 HTML 对比;`list_network_requests`
  看真实请求(搜索是否 POST/带 token、是否有返回数据的 JSON 接口、有没有 m3u8/mp4 媒体请求)。
- 判定「配置能修好」还是「引擎能力缺口」: 浏览器 DOM 与工具 HTML 结构一致 → 只是 selector 没写对;
  浏览器有数据而工具 HTML 没有 → 页面靠 JS 渲染(找到底层 JSON 接口可改用 `json-path-indexed`,
  否则是缺口);媒体请求要 `click` 播放器后才出现 → click-gate,WebVideoExtractor 缺口。

**关键纪律**: chrome-devtools 只用来**取真相、定位问题**。selector/正则的修正最终必须回到
`ani-datasource-test` 的离线步骤、用**工具实际抓到的静态 HTML**(而非浏览器渲染后的 DOM)验证通过——
因为 App 里跑的就是那个无 JS 引擎。**别把只在浏览器渲染后才成立的 selector 当作修好了**。

## 1. 识别配置类型

先解析用户给的 JSON,按字段判定:

| 特征 | 类型 | 测试路径 |
|---|---|---|
| `factoryId == "web-selector"`(或含 `searchConfig`/`searchUrl`) | Selector (CSS) 源 | 本 skill 第 2–6 步全套 |
| `factoryId == "rss"` / `"dmhy"` / `"mikan"` / `"jellyfin"` / `"emby"` / `"ikaros"` 等 | 其他工厂 | 只走第 7 步 `test_subject_episode_source` |
| `{"mediaSources": [...]}` | 订阅列表 | 逐个取出按上两行处理(selector 工具默认只取第一个 web-selector 源) |

selector 工具的 `config` 参数接受四种形态(App 导出 / 裸 arguments / 裸 searchConfig / 订阅列表),
原样传入即可,无需手动拆包。

## 2. 离线校验配置(selector)

```
validate_selector_config(config)
```

- `issues[]` 按 `severity` 分 `error` / `warning` / `info`,每条带精确的字段路径;
- **有 error 必须先修**(选择器/正则/JsonPath 语法错误在真实运行中都是静默空结果,不修后面全白跑);
- warning 逐条判断: 如「searchUrl 不含 {keyword}」几乎总是配置错误;「没有 `(?<ep>)` 分组」会导致集数解析失败;
- 修改配置后**重新 validate** 再继续。

## 3. 用 Ani API 选一个测试条目

> ⚠️ 命名区分: `search_subjects`(下面这个,Ani API 元数据)与引擎步骤 `searchSubjects`
> (`selector_run_step` 的 step 值,请求目标站)只差一个下划线,是两回事。

1. 用户指定了番剧 → `search_subjects(query=番名)` 拿 `subjectId`;
   用户没指定 → `get_trends()` 挑一部热门当季番(目标站大概率收录;老站可再补一部完结老番);
2. `get_subject_episodes(subjectId)` 拿剧集列表,通常选第 1 集的 `episodeId`(整数);
3. 记下条目的中文名——后面单步调试时作为 `keyword` / `query.subjectName` 用。

Ani API 冷启动可能要 40s+(server 已设 90s 超时),首次调用慢是正常的,不要过早判死。

## 4. 解析层全流程(先关视频解析)

```
selector_resolve_episode(subjectId, episodeId, config, extractVideo=false)
```

`extractVideo=false` 只测解析层: 快、不启动 CEF/VLC、不弹窗。返回:

- `ok` + `summary`;
- `steps[]`: 每步 `{name, status: success|failed, summary, details, errors, durationMillis}`;
- `medias[]`: 找到的候选播放页(含线路、集数、playUrl)。

**从第一个 `status=failed` 的步骤开始排查**,对照下表行动:

| 最先失败的步骤 | 常见症状 (看 summary/details) | 下一步动作 |
|---|---|---|
| `aniMetadata` | Ani API 拿不到条目/剧集 | 检查 subjectId/episodeId 是否为有效整数;网络问题则重试一次 |
| `searchSubjects` | 404 | `searchUrl` 模板错误或站点换了搜索路径;有 chrome-devtools 时 `navigate_page`+`list_network_requests` 看站点真实搜索请求的 URL/method,否则 `curl` 人工确认 |
| `searchSubjects` | `captchaKind` 非空 | **停**。本工具无法过验证码,报告用户改用 App 内设置页的数据源测试器 |
| `selectSubjects` | 解析出 0 个条目 | 转第 5 步: 拿搜索页 HTML 离线迭代 subjectFormat 的 selector;若 HTML 其实是 JSON,改用 `json-path-indexed`。有 chrome-devtools 时先 `take_snapshot` 对比:浏览器有条目而工具 HTML 没有 → JS 渲染,查 JSON 接口或转第 9 节 |
| `searchEpisodes` | 条目详情页 404 | 条目链接拼接错误,检查 `rawBaseUrl`(留空时从 searchUrl 推断,details 里能看到实际 URL) |
| `selectEpisodes` | 0 个剧集 / 0 条线路 | 转第 5 步: 拿详情页 HTML 离线迭代 channelFormat 的 selector;同上,可用 chrome-devtools 的渲染后 DOM 判断是 selector 问题还是 JS 渲染 |
| `selectEpisodes` | 剧集有了但 `episodeSort` 全空 | `matchEpisodeSortFromName` 正则没匹配上剧集名(需要 `(?<ep>)` 分组);对照 details 里的剧集名调正则 |
| `selectMedia` | `filteredCount=0` 但 original 非空 | 看 details 里的 `filteredOut`: 集数对不上(sort vs ep、电影/OVA 特判)或该条目根本不含目标集。也可临时把 config 的 `filterByEpisodeSort` 设 false 验证是过滤问题还是解析问题 |
| 全部成功但 `medias` 为空 | 目标条目排名靠后被截断 | 调大 `maxSubjectsPerName`(默认 3,App 内无此限制;条目按名称长度升序排) |

> `selectSubjects`/`selectEpisodes` 解析不出来时,「配置没写对」和「页面靠 JS 渲染(引擎能力缺口)」
> 表现相同(都是 0 条)。有 chrome-devtools MCP 就用它一步分辨: `navigate_page` 后 `take_snapshot`
> 的渲染 DOM 若与工具返回的静态 HTML 结构一致,就是 selector 问题(转第 5 步);若渲染后才有数据,
> 就是 JS 渲染。**但修好的 selector 必须在工具的静态 HTML 上验证通过**(见第 0 节纪律)。

## 5. 单步调试与离线迭代

`selector_run_step` 参数速查(完整 schema 见 `tools/list`):

| step | 必需参数 | 说明 |
|---|---|---|
| `searchSubjects` | `config`, `keyword` | 真实请求搜索页,返回 HTML(默认 100k 截断,`maxHtmlLength` 可调) |
| `selectSubjects` | `config`, `url` 或 `html` | 解析条目列表;传 `html` 即离线 |
| `searchEpisodes` | `url` | 抓条目详情页,返回 HTML |
| `selectEpisodes` | `config`, `url` 或 `html`+`subjectUrl` | 解析线路/剧集;**离线传 html 必须带真实 `subjectUrl`**,相对链接以条目页所在目录为基,否则 playUrl 拼错 |
| `selectMedia` | `config`, `episodes`, `query` | `episodes` 直接用 selectEpisodes 输出的数组(`{channel?, name, episodeSort?, playUrl}`);`query` 为 `{subjectName, episodeSort, allSubjectNames?, episodeEp?, episodeName?}` |
| `matchWebVideo` | `config`, `url` | 离线测一个 URL 判为 视频/嵌套页/忽略 |
| `extractVideo` | `url`(可选 `config`) | 真实 CEF WebView 加载播放页拦截视频 URL |

**离线迭代 selector 的标准循环**(改选择器时用,避免反复打站点):

1. `searchSubjects` / `searchEpisodes` 抓一次 HTML(页面超过 100k 时调大 `maxHtmlLength`,截断的 HTML 解析会失真);
2. 人工读 HTML 找到目标元素的结构,修改 config 里的 selector;
3. `selectSubjects(html=...)` / `selectEpisodes(html=..., subjectUrl=...)` 离线验证,重复 2–3 直到解析正确;
4. 也可以不搬 HTML,直接传 `url` 让它现抓——但每次都是真实请求,**修改配置期间优先离线**;
5. selector 调好后回到第 4 步全流程复测。

## 6. 播放层: 视频 URL 解析与播放探测

解析层通过后:

1. **先离线调正则**: 拿几个候选视频 URL —— 有 chrome-devtools 时 `navigate_page` 打开 playUrl、
   `list_network_requests` 过滤 m3u8/mp4/媒体请求拿到**真实** URL 与请求头(最可靠);否则从
   playUrl 结构猜。用 `matchWebVideo(config, url)` 确认判定(`matched` / `loadPage` / `continue`)。
   注意嵌套页判定优先于视频判定,`matchNestedUrl` 写太宽会把视频 URL 当页面加载导致永远超时;
2. **真实解析**: `selector_run_step(step=extractVideo, url=playUrl, config, probeResolvedVideo=true)`,
   或直接 `selector_resolve_episode(..., extractVideo=true, probeVideo=true)`(默认值)跑完整链路。
   会启动 CEF(首次初始化下载/解压较慢)与 VLC(可能弹播放窗口);`extractMode=all_channels` 可逐线路全测;
3. **单测最终 URL**: `probe_video(videoUrl, headers)` —— HTTP 探测 + VLC 真实播放几秒,报告分辨率/时长/编码。
   `headers` 用 extractVideo 返回的(含 Referer/User-Agent,防盗链站点必需);`showWindow=false` 可不弹窗。
   系统未装 VLC 时自动降级为仅 HTTP 探测,结论要相应弱化为「HTTP 可达」而非「可播放」。

WebView 拦不到视频 URL 时: 看 extractVideo 返回的 `diagnostics`(拦截到的请求样本),
从中找出真实视频 URL 的模式,回去改 `matchVideoUrl` / `matchNestedUrl`,先用 `matchWebVideo` 离线验证再重跑。
若 `diagnostics` 显示页面已正常加载却**完全没有媒体类请求**,用 chrome-devtools 复现对照:
`navigate_page` 打开 playUrl → `list_network_requests` 看有无媒体请求 → 若没有,`click` 播放器按钮后
再 `list_network_requests`;若点击后才出现视频请求(而 App 的 WebViewExtractor 不会点击),
这不是配置问题,是 WebVideoExtractor 的能力缺口,把这段 devtools 证据带上转第 9 节。
(没有 chrome-devtools 时,凭 `diagnostics` 无媒体请求这一点也可推断,但证据较弱。)

## 7. App 级复核与非 selector 源

```
test_subject_episode_source(subjectId, episodeId,
    mediaSource={factoryId: "...", serializedArguments: {...arguments 对象...}})
```

- 对 **selector 源**: 它构造真实的 `SelectorMediaSource` 跑原生 `fetch()`(含限流、冷却重试、无条目数截断),
  是与 App 行为严格一致的最终复核——`selector_resolve_episode` 全绿后建议跑一次;
- 对 **rss/dmhy/mikan/jellyfin 等其他工厂**: 这是唯一的测试入口(selector_* 工具不适用);
  `serializedArguments` 传配置里的 `arguments` 对象;
- 默认 `candidateTestMode=all_channels` 逐个候选测试;
- SSL 握手类错误时结果里会附 `handshakeFailureDomainHint`(可能的换域名提示),报告给用户但**不要**自动改配置。

## 8. 判定标准与最终报告

结论必须分层陈述,不要混为一个"能用/不能用":

| 层 | 通过标准 |
|---|---|
| L0 配置 | `validate_selector_config` 无 error |
| L1 解析 | `selector_resolve_episode(extractVideo=false)` 的 `medias` 非空(找到目标集数的候选) |
| L2 视频 | `extractResults` 中至少一个候选 `resolvedVideo` 非空 |
| L3 播放 | 顶层 `ok=true`(至少一条线路通过播放探测;`first_success` 模式下即首条通过的线路) |

报告内容: 每层结果与证据(候选数、可用线路名、视频 URL、探测到的分辨率/时长);失败层的定位
(哪一步、哪个字段、site 端还是配置端);对配置做过的每一处修改;以及**修正后的完整配置 JSON**。
测试用的条目/集数也要写明(结论仅对被测集数成立)。

若判定为**能力缺口**(第 9 节),报告还需附: 进化需求文档路径、subagent 产出的 patch 路径、
应用 patch 后的复测结果。

## 9. 能力进化: 站点超出现有能力时

现有 selector 流程与 WebView 拦截覆盖不了所有网站。测试的正当结局除了「通过」与「配置修好了」,
还有第三种: **发现组件缺少某种能力**。此时按本节把缺口整理成文档,交给独立 subagent 实现新能力
(输出 git patch),主会话应用后复测——这就是「进化」: 在检测数据源的过程中发现自身能力的不足并改进。
可进化的不只是 selector 引擎,WebVideoExtractor、matchVideo 等组件同样适用。

### 9.1 先确认是能力缺口,不是配置没调对

宣布「做不到」之前,必须**同时满足**:

1. `validate_selector_config` 无 error;
2. 已按第 4–6 步离线迭代过(证明是 selector/正则**表达不了**,而不是没写对);
3. 拿到指向**机制性缺失**的证据(下表)。**有 chrome-devtools MCP 时,缺口判定几乎都靠它坐实**——
   它执行 JS、能看真实网络、能交互,正是区分"配置问题"与"引擎机制不支持"的手段;下表「如何确证」
   多数就是它的用法。没有它时可凭工具输出推断,但证据较弱,报告里要标注为"推断"。

典型缺口与确证方法(非穷尽,遇到新形态照同样思路取证):

| 组件 | 缺口示例 | 如何确证(chrome-devtools MCP) |
|---|---|---|
| 引擎·搜索 | 搜索要用 POST / 需要动态 token / 需要登录态 | 工具 fetch 搜索 URL 拿到的不是结果页;`navigate_page` 搜索页 + `list_network_requests` 看真实搜索请求的 method 与参数 |
| 引擎·解析 | 条目/剧集列表由 JS 动态渲染,静态 HTML 无数据 | 工具 `searchSubjects`/`searchEpisodes` 的 HTML 里搜不到条目文本,但 `take_snapshot` 的渲染 DOM 里有(引擎无 JS) |
| 引擎·解析 | 搜索结果/剧集列表分页,目标在后面几页 | 首页 HTML 只含前 N 项加分页控件;`take_snapshot` 确认目标在翻页/加载更多之后 |
| 引擎·selectMedia | playUrl 需要由 JS/接口计算,`<a>` 里没有 | HTML 里的 href 是 `javascript:...` 或占位符;`list_network_requests` 看点击后计算 playUrl 的接口 |
| WebVideoExtractor | **需要用户手势(点一下播放器)才触发视频加载** | `extractVideo` 的 `diagnostics` 无媒体请求;`navigate_page` playUrl 后 `list_network_requests` 也无,`click` 播放器后再看才出现 m3u8/mp4 |
| WebVideoExtractor | 视频走 blob:/MSE/WebSocket,没有可拦截的 HTTP 媒体 URL | `list_network_requests` 里只有 blob/WS,无媒体 HTTP 请求 |
| matchVideo | 判定需要看响应体/响应头,URL 层面区分不了视频与广告 | 两类 URL 结构相同;`get_network_request` 看响应体/头才能区分 |

### 9.2 写进化需求文档

路径: `docs/evolution/<yyyyMMdd>-<组件>-<短slug>.md`(目录不存在就创建)。固定结构:

```markdown
# 需要的新能力: <一句话>

- **组件**: SelectorMediaSourceEngine / WebViewVideoExtractor(注明平台) / matchVideo / ...
- **触发站点**: <URL>(最小复现配置与测试条目/集数见「复现材料」)
- **现象与证据**: <ani-datasource-test 的工具调用序列与关键输出摘录;
  chrome-devtools 复现的观察——真实网络请求 / 渲染后 DOM / 点击后才出现的请求等,尽量贴具体条目>
- **为什么现有能力做不到**: <落到具体代码与机制,例如「WebView 拦截器
  (AndroidWebMediaResolver.shouldInterceptRequest / DesktopWebMediaResolver 的
  onBeforeResourceLoad)只被动监听页面发出的请求,从不与页面交互,而该站点的
  播放器要在用户点击后才创建 video 元素并发起请求」>
- **新能力的行为定义**: <精确描述期望行为;建议的配置字段名、类型、默认值与 JSON 示例;
  默认值必须等价于旧行为>
- **兼容性约束**: 不改变现有源的行为;涉及 WebView 的能力需说明
  commonMain 接口与 androidMain/desktopMain/appleMain 三端实现
- **验收标准**: 用触发站点跑通第 4–6 步;`:app:shared:app-data:desktopTest`
  与 `:tools:datasource-test-mcp:test` 全绿
- **相关代码入口**: <文件清单>

## 复现材料
<最小配置 JSON、subjectId/episodeId、失败的工具调用参数与输出>
```

「为什么做不到」必须先**读过相关源码**、落到代码事实上,不要停留在现象描述——这一节的质量
直接决定 subagent 能否正确实现。

### 9.3 移交 subagent 实现,输出 git patch

进化实现**不在验证会话里做**——验证会话不修改 App 源码。启动一个独立 subagent
(Claude Code 用 Agent 工具、`general-purpose` 类型,建议 `isolation: worktree` 隔离工作区;
其他环境开新会话),prompt 必须自包含,要点:

```
读 docs/evolution/<文档名>.md,按其中「新能力的行为定义」在 animeko 仓库实现该能力。
1. 只依据该文档与仓库源码工作;不运行 MCP server,不做数据源验证(那是主会话的事);
2. 新配置字段的默认值必须保持旧行为;涉及 WebView 的能力要覆盖 commonMain 接口
   与 androidMain/desktopMain/appleMain 各端实现;
3. 为新逻辑补单元测试;跑 :app:shared:app-data:desktopTest 与
   :tools:datasource-test-mcp:test,确认全绿;
4. 不 commit、不 push;最后用 git diff 把全部改动输出为 patch,
   保存到 docs/evolution/<同名>.patch,报告 patch 路径与改动文件清单。
```

subagent 返回后由**主会话**闭环:

1. 审阅 patch: 改动范围是否与文档一致、默认行为是否确实不变、测试是否补了;
2. 应用 patch 到工作区。**新能力改的是 App 源码,运行中的 `ani-datasource-test` server 仍是旧代码**——
   请用户重新 `installDist` 并重启该 server(同第 0 节),使新构建生效;agent 不自行启动 server;
3. server 重启后,用触发站点重跑第 4–6 步,确认新能力真的解决了问题;
4. 把复测结果回填进化文档的「验收标准」下,最终把**文档 + patch + 复测证据**一起交给用户,
   是否提交由用户决定。

## 10. 行为红线

- **验证码**(任何步骤返回 captchaKind)→ 立即停止该站点的重试,报告用户用 App 内测试器,不要换关键词硬试;
- **对目标站点保持克制**: 每次 searchSubjects/resolve_episode 都是真实请求。失败后先修配置或转离线,
  不要原样重跑;两次真实搜索之间自然间隔(配置里的 `requestInterval` 语义);
- **不要把整页 HTML 塞进对话上下文**: 只摘取与 selector 相关的片段;超大页面存临时文件处理;
- **不要自动改写用户的站点域名**(即使拿到 handshakeFailureDomainHint)——那只是提示,换域名由用户决定;
- 修改过配置就要从 validate 重新走,最终交付的配置必须是**完整重跑过第 4 步(建议含第 7 步)** 的版本;
- **能力缺口的判定必须满足 9.1 的三个前提**——「我没调出来」不是缺口,先穷尽配置手段并取证;
- **验证会话不直接修改 App 源码**: 进化改动一律由隔离的 subagent 产出 patch,
  主会话只负责审阅、应用、重建、复测。
