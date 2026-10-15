package io.github.biliaudioplayer.store

import android.content.Context

/** Pins are local display preferences, isolated by the signed-in account. */
class PinStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_pins", Context.MODE_PRIVATE)
    fun folders(mid: Long): Set<String> = preferences.getStringSet("$mid:folders", emptySet()).orEmpty().toSet()
    fun parts(mid: Long): Set<String> = preferences.getStringSet("$mid:parts", emptySet()).orEmpty().toSet()
    fun videos(mid: Long): Set<String> = preferences.getStringSet("$mid:videos", emptySet()).orEmpty().toSet()
    fun toggleFolder(mid: Long, id: String) = toggle("$mid:folders", id)
    fun togglePart(mid: Long, id: String) = toggle("$mid:parts", id)
    fun toggleVideo(mid: Long, id: String) = toggle("$mid:videos", id)
    private fun toggle(key: String, id: String) {
        val values = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        if (!values.add(id)) values.remove(id)
        preferences.edit().putStringSet(key, values).apply()
    }
}
