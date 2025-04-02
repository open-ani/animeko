/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent

import kotlinx.atomicfu.locks.SynchronizedObject
import me.him188.ani.app.platform.Context
import kotlin.concurrent.Volatile


/**
 * Must not be stored
 */
expect fun Context.createPlatformDataStoreManager(): PlatformDataStoreManager

// workaround for compiler bug
val Context.dataStores: PlatformDataStoreManager get() = PlatformDataStoreManagerHolder.get(this)

private object PlatformDataStoreManagerHolder : SynchronizedObject() {
    private class Initialized(
        val contextHashCode: Int,
        val instance: PlatformDataStoreManager,
    )

    @Volatile
    private var initialized: Initialized? = null

    fun get(context: Context): PlatformDataStoreManager {
        getInitializedAndCheck(context)?.let { 
            return it
        }
        kotlinx.atomicfu.locks.synchronized(this) {
            getInitializedAndCheck(context)?.let {
                return it
            }
            return context.createPlatformDataStoreManager().also {
                initialized = Initialized(
                    context.hashCode(),
                    it,
                )
            }
        }
    }

    private fun getInitializedAndCheck(context: Context): PlatformDataStoreManager? {
        initialized?.let {
            check(it.contextHashCode == context.hashCode()) {
                "PlatformDataStoreManager already initialized with another Context instance"
            }
            return it.instance
        }
        return null
    }
}
