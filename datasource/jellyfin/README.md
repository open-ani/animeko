# Jellyfin / Emby 数据源搜索逻辑问题

## 现有 Issues

### #1728 — 优化 Emby / Jellyfin 支持（Meta Issue）
- **创建者**: Him188（项目 owner）
- **时间**: 2025-02-26
- **状态**: Open
- **进度**: 仅 2/21 项完成
- **内容**: 从 2025 年 2 月用户调研整理的需求列表，涵盖 Emby/Jellyfin 支持的各种改进点
- **链接**: https://github.com/open-ani/animeko/issues/1728

### #1484 — 关于 emby 源的若干建议和 bug
- **状态**: Open
- **关键讨论**: 
  - "目前的数据源只匹配了 Season，并没有处理 Series"
  - 有用户提供了 Emby API 的层级搜索示例代码（Series → Seasons → Episodes）
- **链接**: https://github.com/open-ani/animeko/issues/1484

---

## 实际复现的问题（作为补充示例）

### 场景
Jellyfin 媒体库中有番剧：
- `碧蓝之海 第三季`（Series 命名 = Bangumi subject 名）→ 能搜到 ✅
- `幼女战记`（Series 命名 = 基础番名，不含"第二季"）→ 搜不到 ❌

### 根因
`BaseJellyfinMediaSource.kt` 的 `fetch()` 直接用 Bangumi 的完整 subject 名（如 `幼女战记 第二季`）作为 `searchTerm` 搜索 Jellyfin。当 Jellyfin 中 Series 名称不含季度后缀时，搜索返回空。

### 代码位置
`datasource/jellyfin/src/commonMain/kotlin/BaseJellyfinMediaSource.kt`

关键代码段（第 66-82 行）：
```kotlin
override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> {
    return SinglePagePagedSource {
        query.subjectNames
            .asFlow()
            .flatMapConcat { subjectName ->
                val resp = doSearch(subjectName)  // 直接用完整 subject 名搜索
                resp.Items.asFlow()
            }
            .flatMapMerge {
                when (it.Type) {
                    "Season", "Series" -> doSearch(parentId = it.Id).Items.asFlow()  // 递归 parentId
                    "Episode" -> flowOf(it)
                    "Movie" -> flowOf(it)
                    else -> emptyFlow()
                }
            }
            // ...
    }
}
```

### 建议的修复方向

1. **解析 Bangumi subjectName**，提取基础番名 + 季度号
   - `"碧蓝之海 第三季"` → `baseName="碧蓝之海"`, `targetSeason=3`
   - `"幼女战记 第二季"` → `baseName="幼女战记"`, `targetSeason=2`
   - 无季度后缀 → `targetSeason=null`（取第一个匹配的 Series）

2. **改为 Series → Seasons → Episodes 层级搜索**
   - 用基础番名搜索 Series
   - 用 `/Shows/{seriesId}/Seasons?userId={userId}` 获取季度列表
   - 按 `IndexNumber` 匹配合适的季节
   - 用 `/Shows/{seriesId}/Episodes?Season={seasonNum}&userId={userId}` 获取剧集

3. **保证 `matches()` 过滤通过**
   - 在 MediaProperties.subjectName 中保留 query 中的完整 subject 名（含季度信息），确保后续的 `it.matches(query)` 能通过

### 参考 API
- Jellyfin API: `GET /Shows/{Id}/Seasons`
- Jellyfin API: `GET /Shows/{Id}/Episodes`
- Emby API 兼容同样接口
