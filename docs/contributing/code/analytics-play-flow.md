# 播放加载流程埋点 (`play_flow`)

本文档描述播放页从进入到起播的 Analytics 事件 `play_flow`: 它回答"用户卡在哪一步"、"自动选源靠什么选到的"、"哪些数据源慢或失败"、
"用户做了什么来推进播放"这类产品问题, 并且按会话重放出一次播放加载的完整过程.

> [!IMPORTANT]
> 请先阅读 [Media 框架](media-framework.md) 与 [代码地图](media/media-code-map.md) 中的「单个剧集的播放时序」.

## 标识与生命周期

| 属性 | 类型 | 含义 |
|---|---|---|
| `play_session_id` | String (UUID) | 一次播放会话. 生命周期 = 一个播放页 = 一个 `EpisodeViewModel`. 由 `PlayFlowRecorder` 生成. |
| `episode_attempt` | Long | 会话内第几次剧集尝试. 进入播放页是 1, 之后每次页内切集 (手动、自动连播、TV 的同集重试) 加 1. |
| `seq` | Long | 会话内严格递增的序号. **重放时只按它排序**, 不按时间戳. |
| `t_ms` | Long | 距进入播放页的毫秒数. |
| `subject_id`, `episode_id` | Long | 当前剧集. |
| `step` | String | 事件类型, 见下表. |

这 7 个属性出现在每条 `play_flow` 事件里. `sel_no` (Long, 每次尝试内从 1 起) 是一次资源选择的编号, 把 `auto_select_done` / `user_select` →
`video_load_done` → `playback_started` / `auto_switch` / `play_error` 串成一条加载链; 同一资源再次被选中沿用同一编号.

属性值的类型只有 String 与 Long (GA4 / Firebase 的限制): 布尔为 `1` / `0`, 时长为毫秒, 字符串截断到 100 字符. 一条事件最多 25 个属性.
`reason` 只放枚举值或异常的简单类名, 不放异常消息; 不发送搜索关键字、条目标题、URL、文件路径.

记录器是 `PlayFlowRecorder` (`app/shared/app-data/.../domain/player/analytics/`). `EpisodeViewModel` 创建它并传给 `EpisodeFetchSelectPlayState`;
扩展通过 `PlayerExtensionContext.playFlow` 使用, 事件带 `EpisodeSession.playFlowEpisode` 作为归属, 切集后旧任务补发的事件仍归到旧的尝试.
一个会话超过 400 条事件后只再发一条 `step=truncated`.

## 开关

`play_flow` 是否发送由功能开关 `play_flow_report` 的 `threshold` 控制 (见 [功能开关](feature-flags.md)), 默认 0 即不发送. `EpisodeViewModel` 创建 `PlayFlowRecorder` 时读一次配置并抽一个 `[0, 1)` 的随机数 (`PlayFlowRecorder.shouldReport`), 小于 `threshold` 才发送, 一次播放会话内保持一致;
不发送时记录器照常写入调试悬浮窗, 既有事件 (`episode_enter` 等) 不受影响. 后台配 `{"threshold": 0.3}` 即 30% 的播放会话上报 (每个播放会话抽一次签, 与用户无关), 改 `threshold` 从下次播放起生效; 用总开关可以在配额紧张时立即停发.

## 事件

### 进入与剧集信息

| step | 时机 | 属性 |
|---|---|---|
| `enter` | 进入播放页 / 页内切集 / 自动连播 / TV 重试, 每次剧集尝试开始 | `trigger`: `open` / `switch_episode` / `auto_next` / `retry`; `is_fullscreen` |
| `info_loaded` | 剧集信息加载完成或失败 | `outcome`: `ok` / `failed`; `trigger` (ok): `initial` / `retry`; `reason` (failed): `no_results` / `requires_login` / `network` / `service_unavailable` / `rate_limited` / `request_error` / `unknown`; `duration_ms` 自 `enter` |

### 查询资源

| step | 时机 | 属性 |
|---|---|---|
| `search_started` | 本次尝试开始查找资源 | `kind`: `new` (新建查询) / `reused` (切集复用上一集的查询); `count` 参与的数据源数 (不含禁用与本地缓存源); `restarts` 复用时自动重试的失败源数 |
| `source_failed` | 一轮查询结束时 (紧接在 `search_done` 之前), 这一轮里有失败、限流、需要验证码, 或成功但耗时达到 10 秒的源 | `round`; `count` 本轮有问题的源数; `part` 从 1 起; `sources_1` … `sources_15`: 逗号分隔的 `域名=结果` 记号, 每段不超过 100 字符; 一条装不下时按 `part` 分成多条, 所有源都会记录 |
| `search_done` | 一轮查询全部结束, 或用户在结束前离开播放页 | `outcome`: `complete` / `incomplete`; `round`; `count` 本轮到达终态的源数; `media_count` 全部源返回的资源总数; `cached_count` 本轮完全由搜索缓存满足的源数; `duration_ms` 本轮时长 |

`source_failed` 的记号里, 域名是数据源网址的主机名 (去掉 `www.`), 没有网址的源用 `media_source_id`; 结果是失败的异常简名或阻断原因,
`rate_limited`, `captcha_required`, 或 `slow` (成功但慢). 失败类记号排在前, 慢源在后. 查询时按 `(play_session_id, round, part)` 收齐各条, 把各段拼起来再按逗号拆:
`SPLIT(CONCAT(sources_1, ',', sources_2, …), ',')`.

正常完成的源不单独记录 (一次查询通常有几十个源, 逐源记录会占 play_flow 总量的七成以上), 只计入 `search_done` 的 `count`, `media_count` 与 `cached_count`;
被取消的源不是源本身的问题, 也不记录. 慢阈值是 `PlayFlowFetchObserver.DEFAULT_SLOW_THRESHOLD`.

查询会话是条目级的, 同一条目的各集共用. `round` 从会话创建起为 1; 一轮结束后再有源重启 (用户刷新、限流自动重试、切集重试失败的源) 开新一轮,
新一轮只统计重启过的源. 切集复用已结束的会话时不重发逐源结果, 只有 `search_started kind=reused`.

### 自动选源

| step | 时机 | 属性 |
|---|---|---|
| `auto_select_done` | 每次自动选源有了结果 (每个查询 bundle 一条) | `outcome`: `selected` / `no_media` / `timeout` / `preempted_by_user` / `skipped`; `method` (selected): `local_cache` / `browse_memory` / `preferred_source` / `instant` / `exact` / `fuzzy` / `bt`; `reason`: 未选中时是具体原因 (`fuzzy_deadline`, `no_web_candidates`, `info_missing`, `cancelled` 等), 选中时是浏览记忆未命中、退回自动选择的原因 (`no_memory`, `browse_timeout`, `channel_not_found` 等); `media_source_id`; `kind`; `match`: `exact` / `fuzzy`; `tier`; `sel_no`; `duration_ms` 自开始 |

`method` 对应 `MediaAutoSelector` 的决策依据: 本地缓存、浏览记忆回放、记忆的 Web 源、Web 三阶段 (精确且高阶级 → 精确 → 模糊)、BT.
`outcome=timeout` 表示到了模糊匹配阶段仍没有候选; `preempted_by_user` 表示等待期间用户自己选了; `skipped` 带 `reason=info_missing` (剧集信息未加载) 或 `cancelled`.

### 用户操作

| step | 时机 | 属性 |
|---|---|---|
| `user_select` | 用户手动选了一个资源 | `sel_no`; `entry`: `auto_page` / `bt_page` / `manual_search` / `dropped_file` / `tv` / `other`; `media_source_id`; `kind`: `web` / `bt` / `local_cache` / `dropped_file`; `match`; `tier`; `count`: 1 = 换掉了已选中的资源 |
| `manual_search` | 手动查找的一步完成 | `stage`: `search` / `open_subject` / `open_channel` / `play`; `outcome`: `ok` / `empty` / `failed` / `captcha_solved` / `captcha_solved_failed` / `captcha_failed` / `captcha_unsupported` / `invalid_state` / `create_media_failed`; `media_source_id`; `count` 结果数 (条目 / 线路 / 剧集); `remember` (play) 是否记住选择; `duration_ms` |
| `user_action` | 用户为推进播放做的其他操作 | `action`: `refresh_source` / `refresh_all` / `edit_query` / `retry_info` / `retry_playback` / `solve_captcha`; `media_source_id`; `outcome` (solve_captcha): `solved` / `failed` / `cancelled` / `unsupported` |

### 加载与播放

| step | 时机 | 属性 |
|---|---|---|
| `video_load_done` | 选中的资源加载完毕: 可以播放, 失败, 或被打断 | `sel_no`; `outcome`: `ok` / `failed` / `cancelled`; `stage` (failed / cancelled): `resolve` / `open` / `player`; `reason` (failed): `no_playable_link` / `resolve_timeout` / `network` / `web_engine_unavailable` / `unsupported_media` / `player_io` / `player_decoding` / `player_unsupported_format` / 异常简名; `reason` (cancelled): `user_switched` / `episode_switched` / `left_page`; `media_source_id`; `kind`; `engine`: `web` / `local_bt` / `cloud_bt` / `direct` / `local_file` / `none` / `other`; `duration_ms` 自开始加载 |
| `auto_switch` | 加载或播放失败后系统自动换源的结果 | `sel_no` 失败的资源; `trigger`: `load_failed` / `player_error`; `outcome`: `switched` / `no_more_sources` / `skipped`; `reason` (skipped): `dropped_file` / `user_switched` / `prefer_bt` / `cancelled`; `media_source_id` 换到的资源; `count` 已排除的资源数 |
| `playback_started` | 画面开始播放 (播放器报告播放中) | `sel_no`; `media_source_id`; `kind`; `is_first` 本次尝试首次; `duration_ms` 自选中; `since_enter_ms` 自 `enter` |
| `play_error` | 已经在播放中出错 | `sel_no`; `reason`: `player_io` 等播放器错误码; `position_ms` |

一次选择对应一次加载; 新的选择会取消进行中的加载, 产生 `video_load_done outcome=cancelled reason=user_switched`.
`playback_started` 每次加载最多一条, `is_first=1` 的那条是这次尝试的首次起播.

## 与既有事件的关系

`episode_enter`, `episode_switch`, `media_fetch_result`, `episode_playing`, `episode_exit` 继续保留, 并带 `play_session_id` 与 `episode_attempt`,
可以与 `play_flow` 关联. `episode_enter` 在 `EpisodeViewModel` 创建时记录. `episode_playing` 仍是每个播放页一次; 按尝试的首次起播用
`play_flow playback_started is_first=1`. `media_fetch_result` 与 `episode_exit` 是每次尝试的汇总, `play_flow` 不重复它们的指标.

## 重放一次播放会话

GA4 BigQuery 导出中:

```sql
SELECT
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'seq') AS seq,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'episode_attempt') AS attempt,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 't_ms') AS t_ms,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'step') AS step,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'outcome') AS outcome,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'reason') AS reason,
  (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'media_source_id') AS source,
  (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'sel_no') AS sel_no
FROM `analytics.events_*`
WHERE event_name = 'play_flow'
  AND (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'play_session_id') = @sid
ORDER BY seq;
```

规则:

1. 只按 `(play_session_id, seq)` 排序. `seq` 断号说明丢了事件.
2. 按 `episode_attempt` 分段. 旧尝试的后台任务可能在新尝试开始后才补发事件, 按事件自身的 `episode_attempt` 归属.
3. 加载链按 `(play_session_id, episode_attempt, sel_no)` 聚合.
4. 一次尝试的起播耗时 = `playback_started is_first=1` 的 `since_enter_ms`; 没有这条时, 看最后一条 `info_loaded failed` /
   `auto_select_done no_media|timeout` / `video_load_done failed` 判断卡在哪一步.

桌面端打开 `AnalyticsConfig.debugLogging` 时, 每条事件会以 `Posted event: play_flow` 打到日志, 可按 `seq` 人工核对.

设置 → 调试 → 「打开播放流程悬浮窗」会在所有页面之上显示一个可拖动的悬浮窗 (`PlayFlowDebugFloatingWindow`):
「实时」页以时间线显示当前播放会话的事件 (旧在上, 新在下, 停在底部时自动跟随), 「历史」页列出本次运行内最近的播放会话.
数据来自 `PlayFlowDebugLog` (进程内, 最多保留 20 个会话, 不持久化), `PlayFlowRecorder` 发出的每条事件都会写入它.

## 代码位置

- `domain/player/analytics/PlayFlowRecorder.kt`: 记录器、`PlayFlowEpisode`、`PlayFlowStep`、属性类型转换.
- `domain/player/analytics/PlayFlowDebugLog.kt` + `app/shared/.../ui/subject/episode/analytics/PlayFlowDebugWindow.kt`: 调试悬浮窗及其数据.
- `domain/player/analytics/EpisodeFlowExtension.kt`: `info_loaded`, `search_started`, `user_select`.
- `domain/player/analytics/PlaybackFlowExtension.kt`: `playback_started`, `play_error`.
- `domain/player/analytics/PlayFlowMediaLoadListener.kt` + `domain/episode/MediaLoadListener.kt`: `video_load_done`, 由 `PlayerSession.loadMedia` 回调.
- `domain/player/analytics/PlayFlowFetchObserver.kt`: `source_failed`, `search_done`; 搜索缓存命中来自 `MediaSourceFetchResult.searchStats`
  (`domain/media/fetch/MediaSourceFetchTelemetry.kt`, 由 `keywordSearchChain` 报告).
- `domain/player/extension/AutoSelectExtension.kt`: `auto_select_done`, 结果来自 `MediaAutoSelectOutcome` / `ReplayResult`.
- `domain/player/extension/SwitchMediaOnPlayerErrorExtension.kt`: `auto_switch`, 结果来自 `PlayerLoadErrorHandling`.
- `domain/episode/EpisodeFetchSelectPlayState.kt`: `enter` (每次 `newEpisodeSession`), `EpisodeSwitchTrigger`.
- `app/shared/.../ui/subject/episode/analytics/PlayFlowUiListeners.kt`: `user_action`, `manual_search` 的 UI 适配器;
  `EpisodeViewModel` / `TvEpisodeViewModel` 在用户操作处调用.
- 选择入口由 `SelectTrigger` (`domain/media/selector/MediaSelectorEvents.kt`) 随 `SelectEvent` 广播.

## 新增一个 step

1. 在 `PlayFlowStep` 加枚举值, 在上表加一行 (属性名尽量复用已有的: `outcome`, `reason`, `trigger`, `kind`, `stage`, `count`, `duration_ms` 等,
   GA4 每个属性最多 50 个事件级自定义维度).
2. 在合适的扩展或 UI 适配器里 `playFlow.record(step, episode) { put(...) }`, 事件归属用任务开始时捕获的 `PlayFlowEpisode`.
3. 在 `PlayFlowExtensionsTest` 或对应扩展的测试里用 `RecordingAnalytics.stepsOf(step)` 断言.
