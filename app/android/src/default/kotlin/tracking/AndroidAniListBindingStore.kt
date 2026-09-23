package me.him188.ani.android.tracking

import android.content.Context
import me.him188.ani.app.data.tracking.TrackingBindingStore

class AndroidAniListBindingStore(context: Context) : TrackingBindingStore {
    private val preferences = context.applicationContext.getSharedPreferences("anilist-bindings", Context.MODE_PRIVATE)

    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) { "Could not save AniList title binding" }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) { "Could not remove AniList title binding" }
    }

    override fun entries(): Map<String, String> = preferences.all.mapNotNull { (key, value) ->
        (value as? String)?.let { key to it }
    }.toMap()

    override fun putAll(entries: Map<String, String>) {
        val editor = preferences.edit()
        entries.forEach { (key, value) -> editor.putString(key, value) }
        check(editor.commit()) { "Could not restore AniList title bindings" }
    }
}
