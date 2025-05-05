/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import me.him188.ani.app.data.models.ApiFailure
import me.him188.ani.app.data.repository.RepositoryAuthorizationException
import me.him188.ani.app.data.repository.user.Session
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours

/**
 * 授权状态管理器.
 *
 * 设计上是通用的, 但目前把它当 [BangumiSessionManager] inject.
 */
interface SessionManager { // For unit tests, see BangumiSessionManagerTest
    /**
     * 当前的授权状态. 注意, 此状态会包含 [SessionStatus.Loading] 这种中间状态. 可使用:
     * ```
     * state.filterNot { it is SessionStatus.Loading }
     * ```
     * 来过滤掉中间状态.
     */
    val state: Flow<SessionStatus>

    /**
     * 当前正在进行中的授权请求.
     */
    val processingRequest: StateFlow<ExternalOAuthRequest?>

    /**
     * 登录/退出登录事件流. 只有当用户主动操作 (例如点击按钮) 时才会广播事件. 刚启动 app 时的自动登录不会触发事件.
     */
    val events: SharedFlow<SessionEvent>

    /**
     * 请求为线上状态.
     *
     * 1. 若用户已经登录且会话有效 ([isSessionVerified] 为 `true`), 则此函数会立即返回.
     * 2. 若用户登录过, 但是当前会话已经过期, 则此函数会尝试使用 refresh token 刷新会话.
     *    - 若刷新成功, 此函数会返回, 不会弹出任何 UI 提示.
     *    - 若刷新失败, 则进行下一步.
     * 3. 若用户不是第一次启动, 而且他曾经选择了以游客身份登录, 则抛出异常 [AuthorizationCancelledException].
     * 4. 取决于 [onLaunch], 通过 [AniNavigator] 跳转到欢迎页或者登录页面, 并等待用户的登录结果.
     *    - 若用户取消登录 (选择游客), 此函数会抛出 [AuthorizationCancelledException].
     *    - 若用户成功登录, 此函数正常返回
     *
     * ## Cancellation Support
     *
     * 此函数支持 coroutine cancellation. 当 coroutine 被取消时, 此函数会中断授权请求并抛出 [CancellationException].
     *
     * @param onLaunch 此函数内只能抛出 [CancellationException] 来正常地取消授权请求 (see [ExternalOAuthRequest.invoke]). 其他所有异常都将会被视为是内部 bug.
     * @param skipOnGuest 当用户使用游客模式时, 若此参数为 `true`, 本函数正常返回, 不会尝试登录. 若为 `false`, 本函数继续尝试登录 (OAuth).
     *
     * @throws AuthorizationCancelledException 当用户选择以游客身份登录时抛出
     * @throws AuthorizationFailedException 当登录因为已知的错误, 如网络错误 [ApiFailure.NetworkError], [ApiFailure.Unauthorized], 以及 oauth 异常时抛出
     */
    @Throws(AuthorizationException::class, CancellationException::class)
    suspend fun requireAuthorize(
        onLaunch: suspend () -> Unit, // will be in background scope
        skipOnGuest: Boolean,
    )

    fun requireAuthorizeAsync(
        onLaunch: suspend () -> Unit, // will be in background scope
        skipOnGuest: Boolean,
    )

    /**
     * 设置一个新的会话. 将会替换当前的会话 (如果有).
     *
     * 当此函数返回时, 会话一定已经持久化, 但 [state] 不一定更新.
     *
     * 本函数不会立即验证会话的有效性.
     * 在 [Flow.collect] [state] 时才会从服务器验证, 验证成功后 [state] 才会变为 [SessionStatus.Verified].
     *
     * 此函数会通过 [events] 广播 [SessionEvent.Login] 事件.
     */
    suspend fun setSession(session: Session)

    /**
     * 重新尝试使用已保存的会话登录. 若没有已保存的会话, 本函数不会做任何事.
     */
    suspend fun retry()

    /**
     * 清空当前会话.
     *
     * 当此函数返回时, 会话一定已经从持久化存储中移除.
     *
     * 仅当之前有 session 时, 此函数才会通过 [events] 广播 [SessionEvent.Logout] 事件.
     */
    suspend fun clearSession()

    /**
     * 将当前会话复写为过期会话, 以触发 refresh. 仅供 debug 用, 未经过测试.
     */
    @TestOnly
    suspend fun invalidateSession() {
    }
}

/**
 * 获取去除加载中 [SessionStatus.Loading] 的最终授权状态.
 */
val SessionManager.finalState: Flow<SessionStatus.Final>
    get() = state.filterIsInstance()

@RequiresOptIn(
    "该接口将所有失败状态都归为一类, 这通常会导致问题, 例如忽略了因为网路错误导致的临时性失败. " +
            "当网络错误时, UI 应当提示网络错误, 并提供按钮允许重试. " +
            "仅当你已经通过其他方式处理了网络错误时, 才 @OptIn",
    level = RequiresOptIn.Level.ERROR,
)
annotation class OpaqueSession

/**
 * `false` 并不一定代表未登录, 也可能是网络错误
 */
@OpaqueSession
val SessionManager.isSessionVerified
    get() = state
        .filterNot { it is SessionStatus.Verifying || it is SessionStatus.Refreshing }
        .map { it is SessionStatus.Verified }

@OpaqueSession
val SessionManager.unverifiedAccessToken get() = state.map { it.unverifiedAccessTokenOrNull }

@OpaqueSession
val SessionManager.userInfo get() = state.map { it.userInfoOrNull }

@OpaqueSession
val SessionManager.username get() = state.map { it.usernameOrNull }

@OpaqueSession
val SessionManager.verifiedAccessToken: Flow<AccessTokenPair?>
    get() = state.map {
        (it as? SessionStatus.Verified)?.accessToken
    }

/**
 * @throws RepositoryAuthorizationException
 */
@OptIn(OpaqueSession::class)
suspend fun SessionManager.checkTokenNow(
    clock: Clock = Clock.System,
) {
    val session = verifiedAccessToken.first()
    if (session == null || session.isExpired(clock)) {
        // 没 token 肯定会失败, 就别发请求了
        throw RepositoryAuthorizationException("Precondition failed: verifiedAccessToken is null, aborting request.")
    }
}

@OptIn(OpaqueSession::class)
suspend fun SessionManager.isLoggedInNow(
    clock: Clock = Clock.System,
): Boolean {
    val token = verifiedAccessToken.first() ?: return false
    return !token.isExpired(clock)
}

fun AccessTokenPair.isExpired(clock: Clock = Clock.System): Boolean {
    return expiresAtMillis <= (clock.now()
        .toEpochMilliseconds() + 13.hours.inWholeMilliseconds) // 提前 13 小时让 token 过期. 因为某些地方 shareIn cache 是 12h
}

/**
 * 当用户希望以游客身份登录时抛出的异常.
 */
class AuthorizationCancelledException(
    override val message: String?,
    override val cause: Throwable? = null
) : AuthorizationException()

/**
 * 本次操作失败, 但还有重试的机会. 一般是有保存的 token, 但本次网络请求失败了.
 */
class AuthorizationFailedException(
    val status: SessionStatus,
    override val message: String? = null,
    override val cause: Throwable? = null,
) : AuthorizationException()

sealed class AuthorizationException : Exception()
