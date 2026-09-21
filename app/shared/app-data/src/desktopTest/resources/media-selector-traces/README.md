# 实采数据驱动的自动选择回归测试

本目录存放清洗后的测试输入。MediaSelectionTraceReplayTest 使用虚拟时钟，把源状态和候选结果交给真正的
DefaultMediaSelector、过滤排序和 MediaSelectorAutoSelectUseCaseImpl，不访问网络。
现场的 selectedMediaId 仅用于核对采集证据，绝不写回选择器。

样本来自实际运行桌面端并打开播放页，版本为 4.9.0-dev。
每段约 45 秒，涵盖 18 个 WEB 源、4 个 BT 源、2 个本地缓存实例。
《孤独摇滚！》保留了缓存命中和清缓存重查两组；其他条目都在进入播放前清除该条目的 WEB 搜索缓存，重新请求真实网站。
没有改动源配置、tier 或现场的自动选择设置。

新番依据采集当日 [Bangumi 放送表](https://api.bgm.tv/calendar)，包括 2026 年 7 月开播的：

- [碧蓝之海 第三季](https://bgm.tv/subject/569116)：前作混入，较早返回的源不能抢走精确 T0。
- [无职转生 第三季](https://bgm.tv/subject/501963)：多季名称混在搜索结果中，第 09 集。
- [幼女战记 第二季](https://bgm.tv/subject/339515)：当前季度与前作区分，第 09 集。
- [相反的你和我 第二季](https://bgm.tv/subject/638497)：分别验证 sort=13/ep=1、sort=21/ep=9。
- [在超市后门吸烟的二人](https://bgm.tv/subject/571784)：源返回真实别名“躲在超市后门抽烟的两人”。
- [转学后班上的清纯可爱美少女，竟是小时候玩在一起的哥们儿](https://bgm.tv/subject/456080)：长标题与第 09 集。

另有《葬送的芙莉莲》和《孤独摇滚！》作已完结条目的对照。
新条目、剧集、别名和前后季关系从 Bangumi API 获取后写入本地测试后端；源结果由客户端自己的 HTTP/JCEF 查询取得。
批采集先等待条目和指定剧集的资料就绪，效果与先打开条目页再进入播放一致。
未取得 macOS 窗口截图，这些记录证明查询与选择行为，不证明视频画面正常播放。

## 清洗规则

clean.py 把已脱敏的原始采集变成可审阅的普通 JSON：

1. 将完全相同的资源值放入共享 media 池；同一 ID 若属性改变，保留不同版本。
2. 每个事件只记录发生变化的源、上下文和偏好；源列表使用位置索引，保留共用 local-file-system ID 的两个缓存实例。
3. 删除只有时间推进、其余内容全部相同的重复观察，保留实际输入变化的毫秒时间。
4. 保存原始 SHA-256、字节数、帧数和采集时间。写出前自动还原并逐项核对所有有效帧，任何输入改变都会报错。

保留标题、别名、集数、线路、字幕属性、tier、偏好、失败/验证码/限流状态及重试代数。
链接、下载位置和字幕地址在采集时替换为 fixture.invalid，资源 ID 做稳定摘要。
不保存原始运行日志、媒体文件、源配置、Cookie、请求头或账号令牌。

时间是从采集器附着到会话开始的单调时钟**观察时间**，不是 HTTP 请求耗时。
相邻事件可能被 Flow 合并；初始快照里已经完成的源，只能确认在该观察时间前完成。

## 断言与边界

每份记录有独立的 index.json 期望，包括资源 ID、来源、匹配质量、有效 tier 和选择时间窗口；
同时验证应选 ep 对应的集数。现场调度与单线程回放的差异允许小幅时间窗口。

另有四个使用实采候选的**受控策略用例**。它们保留候选属性和源完成时间，只在测试代码中显式裁剪候选/源，或调整被测偏好：

| 用例 | 必须满足的断言 |
| --- | --- |
| 记忆稀饭源 | 等稀饭完成才选它；更早完成的嘀嗒精确 T0 不能抢选 |
| 精确 T6 与模糊 T1 竞争 | 第 5000 ms 选海星的精确正片 T6，不能选模糊剧场版 T1 |
| 只有模糊候选 | 第 15000 ms 才允许海星剧场版 T1 兜底，即使该源早已完成 |
| 关闭快速选择 | 等较慢的稀饭完成，再选已经就绪的嘀嗒 T0 |

这些 5 秒/15 秒用例是明确构造的回归场景，不声称现场发生过同样的故障。
本组针对初次自动选择。采集保留播放错误后的选择变化，但回放不模拟播放器错误，也不把这些后续选择注入算法。

**已知错误：SP01「ABEMA特別番組『BOCCHI STATION』」会误选正片第 01 集。**
两份 SP 样本显式标注 knownEpisodeMismatch，用于保留复现证据，不代表剧集匹配正确。
修复时更新期望并删除该标记，保留采集时的 recordedMediaId，不要修改原始事实来让测试通过。

## 采集、清洗与运行

正常配置好桌面端和后端后，可手动打开任意播放页采集：

~~~bash
ANIMEKO_MEDIA_TRACE_DIR=/tmp/animeko-selection-traces \
ANIMEKO_MEDIA_TRACE_SECONDS=45 \
./gradlew :app:desktop:run
~~~

批采集可以按条目清除搜索缓存，不修改设置或 tier：

~~~bash
ANIMEKO_MEDIA_TRACE_DIR=/tmp/animeko-selection-traces \
ANIMEKO_MEDIA_TRACE_SECONDS=45 \
ANIMEKO_MEDIA_TRACE_CLEAR_SEARCH_CACHE=true \
ANIMEKO_MEDIA_TRACE_EPISODES=569116:1704888,638497:1704756 \
./gradlew :app:desktop:run
~~~

不设置 ANIMEKO_MEDIA_TRACE_DIR 时不安装采集器。清洗命令不会自动改动期望文件：

~~~bash
python3 app/shared/app-data/src/desktopTest/resources/media-selector-traces/clean.py \
  /tmp/animeko-selection-traces/capture.json \
  app/shared/app-data/src/desktopTest/resources/media-selector-traces/new-case.json

./gradlew :app:shared:app-data:desktopTest --tests '*MediaSelectionTraceReplayTest'
~~~

加入新样本时，检查标题/集数、精确与模糊匹配、源/线路 tier 及完成先后关系，再写独立期望和具名测试。
不要把现场误选直接当成正确答案，也不要用新输出自动覆盖失败断言。

## 样本清单

时间单位为毫秒，取首次观察到该源完成的时刻；全量源变化可从 JSON 的 events 还原。

| 样本 | sort / ep | 资源值数 | 有效事件 | 稀饭完成 | 海星完成 | 首次选择 |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| bocchi-01 | 1.0 / 1.0 | 169 | 32 | 196 | 132 | 250 · 嘀嗒影视 |
| bocchi-06 | 6.0 / 6.0 | 149 | 53 | 37 | 54 | 67 · 嘀嗒影视 |
| bocchi-12 | 12.0 / 12.0 | 194 | 71 | 30 | 37 | 116 · 嘀嗒影视 |
| bocchi-sp01 | 1.0 SP / — | 169 | 36 | 247 | 115 | 262 · 嘀嗒影视 |
| fresh-bocchi-01 | 1.0 / 1.0 | 169 | 65 | 3211 | 1679 | 1016 · 嘀嗒影视 |
| fresh-bocchi-sp01 | 1.0 SP / — | 169 | 63 | 3328 | 1529 | 1593 · 嘀嗒影视 |
| summer-grand-blue-s3-01 | 1.0 / 1.0 | 168 | 58 | 2851 | 1438 | 2853 · 稀饭动漫 |
| summer-mushoku-s3-09 | 9.0 / 9.0 | 199 | 62 | 4083 | 1928 | 3988 · 稀饭动漫 |
| summer-opposites-s2-09 | 21.0 / 9.0 | 87 | 55 | 2070 | 1022 | 2072 · 稀饭动漫 |
| summer-smoking-09 | 9.0 / 9.0 | 55 | 42 | 1559 | 861 | 821 · 嘀嗒影视 |
| summer-childhood-friend-09 | 9.0 / 9.0 | 29 | 38 | 1399 | 1059 | 1400 · 稀饭动漫 |
| frieren-01 | 1.0 / 1.0 | 129 | 67 | 2288 | 1335 | 1170 · 嘀嗒影视 |
| summer-youjo-s2-09 | 9.0 / 9.0 | 56 | 46 | 3245 | 1300 | 3254 · 稀饭动漫 |
| summer-opposites-s2-01 | 13.0 / 1.0 | 114 | 44 | 2120 | 951 | 2120 · 稀饭动漫 |

合计 8 个条目、14 段会话，原始 980 帧清洗为 732 个有效事件。
输入从 145,540,747 字节缩减为 2,687,491 字节（普通 JSON，减少 98.2%）。
