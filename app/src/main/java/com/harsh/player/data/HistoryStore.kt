package com.harsh.player.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight local store for playback history, resume positions, favorites and
 * simple playlists. Uses SharedPreferences because the data is small, key-based
 * and updated infrequently — far cheaper than a database for this use case.
 *
 * Nothing here ever leaves the device.
 */
class HistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("harsh_history", Context.MODE_PRIVATE)

    // In-memory mirror of resume positions to avoid disk reads during playback.
    private val resumeCache = ConcurrentHashMap<String, Long>()

    init {
        // Seed the cache once.
        prefs.all.forEach { (k, v) ->
            if (k.startsWith(PREFIX_RESUME) && v is Long) {
                resumeCache[k.removePrefix(PREFIX_RESUME)] = v
            }
        }
    }

    // ---- Resume positions -------------------------------------------------

    /** Returns the saved position in ms, or 0 if none / effectively at the end. */
    fun resumePosition(key: String): Long = resumeCache[key] ?: 0L

    fun saveResumePosition(key: String, positionMs: Long, durationMs: Long) {
        // Don't keep a resume point in the last 10 seconds or the first 5 seconds.
        if (durationMs > 0 && positionMs > durationMs - 10_000) {
            clearResume(key); return
        }
        if (positionMs < 5_000) return
        resumeCache[key] = positionMs
        prefs.edit().putLong(PREFIX_RESUME + key, positionMs).apply()
    }

    fun clearResume(key: String) {
        resumeCache.remove(key)
        prefs.edit().remove(PREFIX_RESUME + key).apply()
    }

    // ---- Favorites --------------------------------------------------------

    fun favorites(): MutableSet<String> =
        HashSet(prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet())

    fun isFavorite(key: String): Boolean = favorites().contains(key)

    fun toggleFavorite(key: String): Boolean {
        val set = favorites()
        val nowFav = if (set.contains(key)) { set.remove(key); false } else { set.add(key); true }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
        return nowFav
    }

    // ---- Recently played --------------------------------------------------

    fun recentlyPlayed(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun recordPlayed(key: String) {
        val list = recentlyPlayed().toMutableList()
        list.remove(key)
        list.add(0, key)
        while (list.size > MAX_RECENT) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENT, JSONArray(list).toString()).apply()
    }

    fun removeFromHistory(key: String) {
        val list = recentlyPlayed().toMutableList()
        list.remove(key)
        prefs.edit().putString(KEY_RECENT, JSONArray(list).toString()).apply()
        clearResume(key)
    }

    fun clearHistory() {
        resumeCache.clear()
        prefs.edit().remove(KEY_RECENT).apply()
        // Remove all resume keys.
        val ed = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX_RESUME) }.forEach { ed.remove(it) }
        ed.apply()
    }

    // ---- Playlists --------------------------------------------------------

    data class Playlist(val name: String, val keys: List<String>)

    fun playlists(): List<Playlist> {
        val raw = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val keysArr = obj.getJSONArray("keys")
                Playlist(
                    obj.getString("name"),
                    (0 until keysArr.length()).map { keysArr.getString(it) }
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePlaylist(name: String, keys: List<String>) {
        val current = playlists().toMutableList()
        current.removeAll { it.name == name }
        current.add(Playlist(name, keys))
        writePlaylists(current)
    }

    fun deletePlaylist(name: String) {
        val current = playlists().toMutableList()
        current.removeAll { it.name == name }
        writePlaylists(current)
    }

    private fun writePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("keys", JSONArray(p.keys))
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    companion object {
        private const val PREFIX_RESUME = "resume_"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recent_played"
        private const val KEY_PLAYLISTS = "playlists"
        private const val MAX_RECENT = 60
    }
}
