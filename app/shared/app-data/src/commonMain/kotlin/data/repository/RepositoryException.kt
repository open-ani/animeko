/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import androidx.paging.PagingSource

/**
 * 数据层抛出的异常.
 */
sealed class RepositoryException : Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
}

/**
 * 一个请求需要用户登录, 而用户未登录.
 */
class RepositoryAuthorizationException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

/**
 * 网络错误
 */
class RepositoryNetworkException(message: String? = null, cause: Throwable? = null) :
    RepositoryException(message, cause)

val PagingSource.LoadResult.Error<*, *>.repositoryException: RepositoryException?
    get() = throwable as? RepositoryException
