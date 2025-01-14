/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

val BangumiLightSubjectGraphQLExecutor = BangumiBatchGraphQLExecutorEngine(
    actionName = "BangumiLightSubjectGraphQLExecutor",
    targetSchema = "subject",
    fragment = """
fragment Ep on Episode {
  id
  type
  name
  name_cn
  airdate
  sort
}

fragment MyFragment on Subject {
  id
  type
  name
  name_cn
  images{large}
  airtime{date}
  episodes : episodes(limit: 100) { ...Ep }
}
""".trimIndent(),
)
