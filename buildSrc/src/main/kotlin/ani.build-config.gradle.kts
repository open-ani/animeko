/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 实现在 aniBuildConfig.kt. 放在 .kt 里而不是脚本里, 这样 BuildConfigPlatform 等类型
// 是顶层类型, 消费方不用再写 `Build_config_gradle.BuildConfigPlatform` 这种脚本类前缀.
apply<AniBuildConfigPlugin>()
