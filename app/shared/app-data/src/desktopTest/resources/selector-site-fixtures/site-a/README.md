# 真实站点页面夹具

一个真实在线动漫站点的搜索页与条目页，经 `clean.py` 脱敏后入库，供 `SelectorSiteFixtureTest`
用真实页面结构回归 Selector 数据源的浏览与自动匹配两条路径。抓取于 2026-09-24。

| 文件 | 内容 |
|---|---|
| `search-frieren.html` | 搜索“葬送的芙莉莲”，两条结果 |
| `search-aot.html` | 搜索“进击的巨人”，十条结果，含 OAD、剧场版与各季 |
| `subject-44.html` | 《葬送的芙莉莲》条目页，两条线路各 28 集 |
| `subject-3390.html` | 《葬送的芙莉莲 第二季》条目页，三条线路各 10 集 |
| `subject-1617.html` | 《进击的巨人 OAD》条目页，一条线路 8 集 |
| `config.json` | 该站点的 `searchConfig`，域名已替换；`requestInterval` 设为 0 以免测试真实等待 |

## 脱敏规则

见 `clean.py`：删除全部脚本块；绝对 URL 整体替换为 `https://static.fixture.invalid/`，
相对链接保留（条目页、播放页与静态资源路径是选择器要匹配的内容）；站点名称与品牌串替换为占位词；
输出前检查残留，有则拒绝写出。番剧名、线路名与集名是页面公开内容，原样保留。
