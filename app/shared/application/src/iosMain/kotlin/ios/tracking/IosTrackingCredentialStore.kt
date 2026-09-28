/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ios.tracking

import me.him188.ani.tracking.api.TrackingCredentialStore
import me.him188.ani.tracking.api.TrackingLoginCredentials
import me.him188.ani.tracking.api.TrackingProviderId
import platform.Foundation.NSUserDefaults

/** Provider-scoped iOS credentials, kept outside settings and backup snapshots. */
class IosTrackingCredentialStore(
    providerId: TrackingProviderId,
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : TrackingCredentialStore {
    private val credentialKey = "tracking-credential:${providerId.value}"

    override suspend fun load(): TrackingLoginCredentials? {
        val secret = userDefaults.stringForKey(credentialKey) ?: return null
        return TrackingLoginCredentials(secret = secret)
    }

    override suspend fun save(credentials: TrackingLoginCredentials) {
        userDefaults.setObject(credentials.secret, forKey = credentialKey)
    }

    override suspend fun clear() {
        userDefaults.removeObjectForKey(credentialKey)
    }
}
