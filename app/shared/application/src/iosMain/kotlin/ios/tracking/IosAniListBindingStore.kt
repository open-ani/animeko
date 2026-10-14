/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ios.tracking

import me.him188.ani.app.data.tracking.TrackingBindingStore
import platform.Foundation.NSUserDefaults

/** Only non-secret account and title IDs are stored here. */
class IosAniListBindingStore(
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : TrackingBindingStore {
    private val prefix = "anilist-binding:"

    override fun get(key: String): String? = userDefaults.stringForKey(prefix + key)

    override fun put(key: String, value: String) {
        userDefaults.setObject(value, forKey = prefix + key)
    }

    override fun remove(key: String) {
        userDefaults.removeObjectForKey(prefix + key)
    }

    override fun entries(): Map<String, String> {
        val dict = userDefaults.dictionaryRepresentation()
        val result = mutableMapOf<String, String>()
        for ((k, v) in dict) {
            val keyStr = k as? String ?: continue
            if (keyStr.startsWith(prefix)) {
                val valueStr = v as? String ?: continue
                result[keyStr.removePrefix(prefix)] = valueStr
            }
        }
        return result
    }

    override fun putAll(entries: Map<String, String>) {
        entries.forEach { (key, value) -> put(key, value) }
    }
}
