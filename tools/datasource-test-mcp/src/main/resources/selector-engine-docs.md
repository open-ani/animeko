# SelectorMediaSourceEngine 步骤说明

Selector (CSS Selector) 数据源引擎从一个网页站点解析出某一集的可播放链接。
引擎代码位于 `app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/SelectorMediaSourceEngine.kt`。

完整流程共 5 个引擎步骤,之后还有播放期的视频 URL 匹配:

```
searchSubjects → selectSubjects → searchEpisodes → selectEpisodes → selectMedia
                                                                        │
                                              播放/测试时: extractVideo (WebView) + matchWebVideo (正则)
```

MCP 工具对应关系:

- `selector_resolve_episode` 自动跑完整流程并返回每步 trace;
- `selector_run_step` 单独跑其中一个步骤;
- `validate_selector_config` 离线校验配置字段。

## 1. searchSubjects — 搜索条目列表

**做什么**: 把搜索关键词(番剧名)代入 `searchUrl` 的 `{keyword}` 占位符,HTTP GET 该 URL,返回搜索结果页。

**输入**: `searchUrl`、关键词、`searchUseOnlyFirstWord`(只用名字的第一个词搜索)、`searchRemoveSpecial`(移除特殊字符,如「剧场版」标记外的符号)。

**输出**: `SearchSubjectResult { url, document, captchaKind }`。
`document == null` 表示 404;`captchaKind != null` 表示被人机验证(Cloudflare 等)拦截。

**常见问题**:
- 返回 404 / 空页面 → searchUrl 模板错误,或站点换了搜索路径;
- captchaKind 非空 → 站点开了防爬,需要真实浏览器 cookie;
- 返回的是 JSON API 响应 → 配合 `json-path-indexed` 条目格式使用。

`selector_run_step(step=searchSubjects, config, keyword)` 会返回抓到的原始 HTML,便于人工检查页面结构。

## 2. selectSubjects — 解析条目列表

**做什么**: 用条目格式 (`subjectFormatId`) 配置的 CSS selector / JsonPath 从搜索结果页中提取条目(名称 + 详情页链接)。

**条目格式** (三选一):

| 格式 id | 提取方式 | 关键配置 |
|---|---|---|
| `a` | 选择 `<a>` 元素,`title`/text 为名称,`href` 为链接 | `selectLists` |
| `indexed` | 名称与链接分别用两个 selector 选出后按序配对 | `selectNames`, `selectLinks` |
| `json-path-indexed` | 页面是 JSON API 响应,用 JsonPath 提取 | `selectLinks`, `selectNames` |

**输出**: `WebSearchSubjectInfo { name, fullUrl, partialUrl }` 列表。返回 `null` 表示配置无效(selector 为空或语法错误)。

**常见问题**:
- 解析出 0 个条目 → selector 与页面结构不匹配,把 searchSubjects 返回的 HTML 拿来对照调 selector;
- 名称混入无关文字 → selector 选得太宽。

`selector_run_step(step=selectSubjects, config, url 或 html)` 支持直接给 HTML 离线调试。

## 3. searchEpisodes — 获取条目详情页

**做什么**: HTTP GET 某个条目的详情页(其中包含剧集/线路列表)。无解析逻辑,404 时返回 `null`。

`selector_run_step(step=searchEpisodes, url)` 返回详情页原始 HTML。

## 4. selectEpisodes — 解析剧集列表

**做什么**: 用剧集格式 (`channelFormatId`) 配置的 selector 从详情页提取剧集(名称、播放页链接、线路)。

**剧集格式** (二选一):

| 格式 id | 适用页面 | 关键配置 |
|---|---|---|
| `no-channel` | 只有一个剧集列表 | `selectEpisodes`, `selectEpisodeLinks`(可选), `matchEpisodeSortFromName` |
| `index-grouped` | tab 分线路,每个线路一个列表 | `selectChannelNames`, `matchChannelName`, `selectEpisodeLists`, `selectEpisodesFromList`, `selectEpisodeLinksFromList`(可选), `matchEpisodeSortFromName` |

`matchEpisodeSortFromName` 是从剧集名提取序号的正则,需要 `(?<ep>...)` 命名分组,
默认 `第\s*(?<ep>.+)\s*[话集]`。名为「正片」「高清版」或含分辨率字样的会被当作电影,序号视为 01。

**输出**: `SelectedChannelEpisodes { channels, episodes }`;`episodes` 每项为
`WebSearchEpisodeInfo { channel, name, episodeSortOrEp, playUrl }`。返回 `null` 表示配置无效。

**常见问题**:
- 0 个剧集 → selector 不匹配;
- `episodeSortOrEp` 为空 → `matchEpisodeSortFromName` 没匹配上剧集名,后续 selectMedia 会因此过滤掉该集;
- `playUrl` 错误 → 剧集元素不是 `<a>` 时需要配 `selectEpisodeLinks(FromList)`。

`selector_run_step(step=selectEpisodes, config, url 或 html+subjectUrl)`。

## 5. selectMedia — 转换并过滤

**做什么**: 把剧集信息转换为 `Media` 对象,并按查询上下文过滤:

- `episodeSortOrEp == null` 的剧集直接丢弃;
- `filterByEpisodeSort == true` 时只保留序号与目标集数(sort 或 ep)一致的剧集;
- 字幕语言从线路名/剧集名猜测(如「简中」「繁中」),否则用 `defaultSubtitleLanguage`。

**输出**: `SelectMediaResult { originalList, filteredList }`。trace 里的 `filteredOut` 列出被过滤掉的候选,便于排查「有剧集但匹配不上目标集数」的问题。

`selector_run_step(step=selectMedia, config, episodes, query)`。

## 播放期: extractVideo 与 matchWebVideo

拿到 `playUrl`(播放页)后还不是最终视频地址。播放时 App 用 WebView (桌面为 CEF) 加载播放页,
拦截页面发出的所有网络请求,用 `matchVideo` 配置逐个匹配:

1. `matchNestedUrl` 匹配到的 URL → 作为嵌套页面继续加载(`enableNestedUrl` 开启时);
2. `matchVideoUrl` 匹配到的 URL → 即最终视频地址,`(?<v>...)` 命名分组可只取其中一段,没有分组则取整个 URL;
3. 都不匹配 → 忽略该请求。

匹配成功后,视频请求会附带 `addHeadersToVideo` 配置的 `User-Agent` / `Referer`;
WebView 加载播放页时会注入 `cookies`(每行一个 `name=value`)。

MCP 工具:

- `selector_run_step(step=matchWebVideo, config, url)` — 离线测试某个 URL 会被判为「视频 / 嵌套页 / 忽略」,调 `matchVideoUrl` 正则用;
- `selector_run_step(step=extractVideo, url, config?)` — 真实启动 WebView 加载播放页并拦截视频 URL;
- `probe_video(videoUrl, headers)` — 对最终视频 URL 做 HTTP 探测,并用 Animeko 桌面端同款播放器 (VLC) 真实播放几秒,报告分辨率、时长、编码、帧率、码率。

## 配置 JSON 形态

`validate_selector_config` / `selector_resolve_episode` / `selector_run_step` 的 `config` 参数接受:

1. App 导出格式: `{"factoryId": "web-selector", "version": 2, "arguments": {...}}`;
2. 裸 arguments: `{"name": "...", "searchConfig": {...}}`;
3. 裸 searchConfig: `{"searchUrl": "...", ...}`;
4. 订阅列表: `{"mediaSources": [{...}, ...]}`(取第一个 web-selector 源)。

## 调试建议流程

1. `validate_selector_config` 先排除语法错误;
2. `search_subjects` / `get_subject_episodes` 拿到目标条目与 episodeId;
3. `selector_resolve_episode` 跑全流程,看哪一步的 trace 先出问题;
4. 用 `selector_run_step` 单独重跑该步骤(可传 HTML 离线迭代 selector);
5. 视频链接解析不出来时用 `matchWebVideo` 调正则、`extractVideo` 看 WebView 实际拦截到了什么;
6. 最后 `probe_video` 验证视频真实可播放。
