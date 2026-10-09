package com.harsh.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "harsh_settings")

/**
 * Settings persistence backed by DataStore Preferences. All writes are
 * asynchronous and coalesced by DataStore, so we never write on every frame.
 */
class Prefs(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")                       // "dark" | "black"
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")      // 0.5 .. 2.0
        val SUBTITLE_LANG = stringPreferencesKey("subtitle_lang")       // "none" | "auto" | ISO code
        val SUBTITLE_SIZE = intPreferencesKey("subtitle_size")          // sp
        val SUBTITLE_DELAY = intPreferencesKey("subtitle_delay_ms")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")         // "fit" | "fill" | "zoom" | "16:9" | "4:3"
        val RESUME = booleanPreferencesKey("resume_playback")
        val AUTO_HIDE = booleanPreferencesKey("auto_hide_controls")
        val AUTO_HIDE_DELAY = intPreferencesKey("auto_hide_delay_ms")
        val LOW_RAM = booleanPreferencesKey("low_ram_mode")
        val THUMB_CACHE_MB = intPreferencesKey("thumb_cache_mb")
    }

    val theme: Flow<String> = context.dataStore.data.map { it[Keys.THEME] ?: "black" }
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[Keys.PLAYBACK_SPEED] ?: 1.0f }
    val subtitleLang: Flow<String> = context.dataStore.data.map { it[Keys.SUBTITLE_LANG] ?: "auto" }
    val subtitleSize: Flow<Int> = context.dataStore.data.map { it[Keys.SUBTITLE_SIZE] ?: 22 }
    val subtitleDelayMs: Flow<Int> = context.dataStore.data.map { it[Keys.SUBTITLE_DELAY] ?: 0 }
    val aspectRatio: Flow<String> = context.dataStore.data.map { it[Keys.ASPECT_RATIO] ?: "fit" }
    val resumePlayback: Flow<Boolean> = context.dataStore.data.map { it[Keys.RESUME] ?: true }
    val autoHideControls: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_HIDE] ?: true }
    val autoHideDelayMs: Flow<Int> = context.dataStore.data.map { it[Keys.AUTO_HIDE_DELAY] ?: 4000 }
    val lowRamMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOW_RAM] ?: false }
    val thumbnailCacheMb: Flow<Int> = context.dataStore.data.map { it[Keys.THUMB_CACHE_MB] ?: 24 }

    suspend fun setTheme(v: String) = context.dataStore.edit { it[Keys.THEME] = v }
    suspend fun setPlaybackSpeed(v: Float) = context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = v.coerceIn(0.5f, 2.0f) }
    suspend fun setSubtitleLang(v: String) = context.dataStore.edit { it[Keys.SUBTITLE_LANG] = v }
    suspend fun setSubtitleSize(v: Int) = context.dataStore.edit { it[Keys.SUBTITLE_SIZE] = v.coerceIn(12, 48) }
    suspend fun setSubtitleDelayMs(v: Int) = context.dataStore.edit { it[Keys.SUBTITLE_DELAY] = v.coerceIn(-10_000, 10_000) }
    suspend fun setAspectRatio(v: String) = context.dataStore.edit { it[Keys.ASPECT_RATIO] = v }
    suspend fun setResumePlayback(v: Boolean) = context.dataStore.edit { it[Keys.RESUME] = v }
    suspend fun setAutoHideControls(v: Boolean) = context.dataStore.edit { it[Keys.AUTO_HIDE] = v }
    suspend fun setAutoHideDelayMs(v: Int) = context.dataStore.edit { it[Keys.AUTO_HIDE_DELAY] = v.coerceIn(1000, 30_000) }
    suspend fun setLowRamMode(v: Boolean) = context.dataStore.edit { it[Keys.LOW_RAM] = v }
    suspend fun setThumbnailCacheMb(v: Int) = context.dataStore.edit { it[Keys.THUMB_CACHE_MB] = v.coerceIn(4, 128) }
}
