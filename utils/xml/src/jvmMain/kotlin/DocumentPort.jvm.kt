/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress(
    "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING",
    "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS", "ACTUAL_WITHOUT_EXPECT", "EXPECT_ACTUAL_INCOMPATIBILITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_MODALITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_CLASS_SCOPE",
)

package me.him188.ani.utils.xml


actual typealias Document = org.jsoup.nodes.Document
actual typealias Node = org.jsoup.nodes.Node
actual typealias Element = org.jsoup.nodes.Element
actual typealias Elements = org.jsoup.select.Elements
actual typealias Evaluator = org.jsoup.select.Evaluator
