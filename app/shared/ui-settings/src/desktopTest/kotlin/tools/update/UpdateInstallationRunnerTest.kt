/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import me.him188.ani.app.platform.Context
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdateInstallationRunnerTest {
    @Test
    fun `tracks installation failure and dismisses it`() = runTest {
        val installationStarted = CompletableDeferred<Unit>()
        val finishInstallation = CompletableDeferred<Unit>()
        val failure = InstallationResult.Failed(InstallationFailureReason.FAILED_TO_COPY, "failed")
        val runner = UpdateInstallationRunner(
            object : UpdateInstaller {
                override fun install(file: SystemPath, context: Context): InstallationResult = failure

                override suspend fun install(
                    file: SystemPath,
                    packageUrls: List<String>,
                    context: Context,
                ): InstallationResult {
                    installationStarted.complete(Unit)
                    finishInstallation.await()
                    return failure
                }
            },
        )

        val installation = async {
            runner.install(Path("update").inSystem, emptyList(), object : Context() {})
        }
        installationStarted.await()
        assertEquals(UpdateInstallationState.Installing, runner.state.value)

        finishInstallation.complete(Unit)
        installation.await()
        assertEquals(failure, assertIs<UpdateInstallationState.Failed>(runner.state.value).result)

        runner.dismissFailure()
        assertEquals(UpdateInstallationState.Idle, runner.state.value)
    }
}
