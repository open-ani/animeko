# 拆分季弹幕匹配的实采回归数据

`DandanplaySplitSeasonReplayTest` 用本目录的数据回放拆分季各集的弹弹 play 弹幕自动匹配，不访问网络。
回放走 app 的真实路径：Ani 服务端的条目数据经 app 的转换得到条目、剧集和 `splitSeason`，
再由 `SearchDanmakuRequest` 生成弹幕请求，交给真正的 `DandanplayDanmakuProvider`，弹弹 play 的响应来自录制。
规则本身见 [MediaSelector 的拆分季规则](../../../../../../../docs/contributing/code/media/media-selector.md#拆分季的季内序号)。

| 文件 | 内容 |
| --- | --- |
| `index.json` | 录制日期、条目，以及每一集在弹弹里对应的 episodeId（`expected`），需要说明核对依据的集带 `note` |
| `ani-subjects.json` | Ani 服务端 `/v2/subjects/{id}` 的响应，即 app 看到的条目名、别名、剧集和 `relations.splitSeason` |
| `dandanplay.json` | 弹弹 play 的响应，键是方法、路径和排序后的查询参数 |

## 条目

数据录制于 2026-10-13，只收已经播出的集，共 19 个条目、221 集。
弹弹对拆分季后半的三种收录方式都包括在内，另有三段式（石之海）和两个第一段作对照：

| 弹弹的收录方式 | 条目 |
| --- | --- |
| 后半没有关联，整季合成一部番剧，关联在第一段上 | Re:0 第三季反击篇、第四季夺还篇（#3414） |
| 后半自己一部番剧，接着前半编号，季首 sort 不是 1 | Re:0 第二季后半（#14–#25，sort 39–50） |
| 后半自己一部番剧，接着前半编号，季首 sort 是 1 | 无职转生第 2 部分、间谍过家家第 2 部分、86 第 2 部分、忧国的莫里亚蒂第二季、排球少年 TO THE TOP 第二期、石之海第 2 和第 3 部分、魔王学院第二季第 2 部分 |
| 后半自己一部番剧，从 1 编号 | 无职转生第二季第 2 部分（前半有 sort 为 0 的序章）、进击的巨人第三季 Part.2 和最终季 Part.2、魔法使的新娘第二季第 2 部分、炎炎消防队叁之章第 2 部分、坂本日常第 2 部分 |
| 第一段，拆分季规则不生效 | Re:0 第四季丧失篇（关联到合并番剧）、石之海 |

## 正确答案

每一集的答案按 Bangumi 与弹弹的剧集标题和播出日期核对，不取任何一种匹配规则的输出：
215 集的标题（去掉“第x话”、统一全半角与标点后）在录制的全部弹弹剧集里只与一集相同；
其余 6 集标题对不上或有两个候选，按播出日期（相差不超过一天）和集号核对，依据写在 `note` 里。
这 6 集包括弹弹自己的数据错误：炎炎消防队后半第 13 话的标题被写成第 1 话的“无自觉”。
没有出现弹弹把同一集收在几部番剧里的情况；`expected` 允许写多个 episodeId，为空表示弹弹没有这一集、应当匹配不到。

## 断言

每个条目回放两遍，答案相同：

1. 录制的响应；
2. 剧集标题只留“第x话”的响应，相当于弹弹刚收录新一集、还没有标题的时候。录制时弹弹的标题已经基本补齐，
   标题匹配能掩盖集号规则的问题，这一遍让每一集都只能靠集号对上。

用 2026-10-13 的数据，带拆分季信息时两遍都没有错集和漏匹配。作为对照，同样的请求不带拆分季信息时，
第一遍错 4 集（Re:0 第二季后半 2 集、反击篇 1 集、炎炎消防队 1 集），第二遍错 28 集。

请求不带文件名，不覆盖弹弹的文件匹配；也不请求弹幕列表，回放对任何弹幕列表请求都返回空列表，并记下请求的 episodeId 作为匹配结果。

## 录制

录制器 `DandanplaySplitSeasonFixtureRecorder` 只在设置了 `ANIMEKO_DANDANPLAY_FIXTURE_DIR` 时运行。
它按 `index.json` 的条目从 Ani 服务端取条目数据，再用真正的 provider 对每一集分别带和不带拆分季信息各匹配一次，
记下其间的弹弹请求，所以两种匹配回放时都不会缺少响应。同一请求只发一次，请求之间间隔 300 毫秒。
弹弹的响应只保留匹配用到的字段（番剧的 id、名字和类型，剧集的 id、标题、集号和播出日期），
条目数据去掉简介、标签、信息框和剧集简介。

~~~bash
ANIMEKO_DANDANPLAY_FIXTURE_DIR="$PWD/app/shared/app-data/src/desktopTest/resources/dandanplay-split-season" \
ANIMEKO_DANDANPLAY_APP_ID=... \
ANIMEKO_DANDANPLAY_APP_SECRET=... \
./gradlew :app:shared:app-data:desktopTest --tests '*DandanplaySplitSeasonFixtureRecorder' --rerun

./gradlew :app:shared:app-data:desktopTest --tests '*DandanplaySplitSeasonReplayTest'
~~~

录制器不改 `index.json` 里的答案。新增条目时先在 `index.json` 里加上条目 id 再录制，
然后按上面的方法逐集核对答案、写入 `expected`。回放测试按 `index.json` 给每一集生成测试，不用另写。
弹弹和 Ani 服务端的数据会变，重新录制后答案可能随之变化，要重新核对，不要用匹配结果直接覆盖答案。
