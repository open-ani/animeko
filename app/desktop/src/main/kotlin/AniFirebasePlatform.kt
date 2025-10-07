/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.datastore.core.DataStore
import com.google.firebase.FirebasePlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.logging.logger

/**
 * @since 5.0.2
 */
class AniFirebasePlatform(
    private val datastore: DataStore<Map<String, String>>,
//    parentCoroutineContext: CoroutineContext,
) : FirebasePlatform() {
    private val logger = logger<AniFirebasePlatform>()
//    private val scope = parentCoroutineContext.childScope()

    override fun store(key: String, value: String) {
        runBlocking {
            datastore.updateData {
                it + (key to value)
            }
        }
    }

    override fun retrieve(key: String) = runBlocking {
        datastore.data.first()[key]
    }

    override fun clear(key: String) {
        runBlocking {
            datastore.updateData {
                it - key
            }
        }
    }

    override fun log(msg: String) = logger.info(msg)
}
