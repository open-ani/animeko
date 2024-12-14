/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test

import me.him188.ani.app.data.repository.RepositoryException

/**
 * 标记接口, 表示一个通用的刷新结果.
 */
interface RefreshResult {
    /**
     * 刷新成功, 不会显示错误按钮.
     */
    interface Success : RefreshResult

    interface InProgress : RefreshResult

    /**
     * 刷新失败, 会显示错误按钮.
     */
    sealed interface Failed : RefreshResult

    /**
     * 一个已知类型的 API 错误
     */
    interface ApiError : Failed {
        val exception: RepositoryException
    }

    /**
     * 配置有误, 例如测试 RSS 数据源. UI 只会显示 "配置不完整". 你需要让你的编辑框自己显示 error.
     */
    interface InvalidConfig : Failed

    /**
     * 一个任意类型异常. 这属于不期望遇到的错误 (bug).
     */
    interface UnknownError : Failed {
        val exception: Throwable
    }
}
