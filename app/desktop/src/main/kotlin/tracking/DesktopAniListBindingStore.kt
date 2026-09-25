package me.him188.ani.app.desktop.tracking

import java.util.prefs.Preferences
import me.him188.ani.app.data.tracking.TrackingBindingStore

/** Only non-secret account and title IDs are stored here. */
class DesktopAniListBindingStore : TrackingBindingStore {
    private val preferences = Preferences.userRoot().node("me/him188/ani/tracking/anilist-bindings")

    override fun get(key: String): String? = preferences.get(key, null)

    override fun put(key: String, value: String) {
        preferences.put(key, value)
        preferences.flush()
    }

    override fun remove(key: String) {
        preferences.remove(key)
        preferences.flush()
    }

    override fun entries(): Map<String, String> = preferences.keys().associateWith { preferences.get(it, "") }

    override fun putAll(entries: Map<String, String>) {
        entries.forEach(preferences::put)
        preferences.flush()
    }
}
