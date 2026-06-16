package com.gwstreams.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.settingsStore by preferencesDataStore("gws_settings")

enum class StreamFormat { TS, HLS }                 // #1
enum class VideoFit { FIT, ZOOM, STRETCH }          // #2
enum class DecoderMode { HARDWARE, SOFTWARE }       // #3
enum class CategorySort { DEFAULT, ALPHABETICAL, MANUAL }  // #7
enum class BufferPreset { LOW, BALANCED, HIGH }     // #9

/** All user-configurable settings, shared by phone and TV. */
data class AppSettings(
    // Guide / EPG
    val autoFetchEpg: Boolean = true,
    val epgRefreshMinutes: Int = 10,
    val use24hTime: Boolean = true,            // #4
    val epgTimezoneOffsetMin: Int = 0,         // #4 manual offset in minutes
    // Playback
    val bufferSeconds: Int = 30,
    val bufferPreset: BufferPreset = BufferPreset.BALANCED,  // #9
    val streamFormat: StreamFormat = StreamFormat.TS,        // #1
    val videoFit: VideoFit = VideoFit.FIT,                   // #2
    val decoderMode: DecoderMode = DecoderMode.HARDWARE,     // #3
    // Navigation / layout
    val defaultSection: String = "LIVE",       // #5  (LIVE/MOVIES/SERIES/LAST)
    val guideRowsDensity: Int = 2,             // #6  1=spacious,2=normal,3=dense
    // Content management
    val hiddenCategories: Set<String> = emptySet(),
    val categoryOrder: List<String> = emptyList(),
    val categorySort: CategorySort = CategorySort.DEFAULT,   // #7
    val lockedCategories: Set<String> = emptySet(),          // #8 PIN-locked
    val pin: String = ""                                     // #8 empty = no PIN
)

class SettingsRepository(private val context: Context) {
    private val gson = Gson()

    private val kAutoEpg = booleanPreferencesKey("auto_epg")
    private val kEpgMin = intPreferencesKey("epg_min")
    private val k24h = booleanPreferencesKey("use_24h")
    private val kTzOffset = intPreferencesKey("tz_offset")
    private val kBuffer = intPreferencesKey("buffer_sec")
    private val kBufPreset = stringPreferencesKey("buffer_preset")
    private val kFormat = stringPreferencesKey("stream_format")
    private val kFit = stringPreferencesKey("video_fit")
    private val kDecoder = stringPreferencesKey("decoder")
    private val kDefaultSection = stringPreferencesKey("default_section")
    private val kDensity = intPreferencesKey("guide_density")
    private val kHidden = stringPreferencesKey("hidden_cats")
    private val kOrder = stringPreferencesKey("cat_order")
    private val kSort = stringPreferencesKey("cat_sort")
    private val kLocked = stringPreferencesKey("locked_cats")
    private val kPin = stringPreferencesKey("pin")

    private fun strList(raw: String?): List<String> =
        raw?.let {
            runCatching { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
                .getOrDefault(emptyList())
        } ?: emptyList()

    suspend fun load(): AppSettings {
        val p = context.settingsStore.data.first()
        return AppSettings(
            autoFetchEpg = p[kAutoEpg] ?: true,
            epgRefreshMinutes = p[kEpgMin] ?: 10,
            use24hTime = p[k24h] ?: true,
            epgTimezoneOffsetMin = p[kTzOffset] ?: 0,
            bufferSeconds = p[kBuffer] ?: 30,
            bufferPreset = runCatching { BufferPreset.valueOf(p[kBufPreset] ?: "BALANCED") }.getOrDefault(BufferPreset.BALANCED),
            streamFormat = runCatching { StreamFormat.valueOf(p[kFormat] ?: "TS") }.getOrDefault(StreamFormat.TS),
            videoFit = runCatching { VideoFit.valueOf(p[kFit] ?: "FIT") }.getOrDefault(VideoFit.FIT),
            decoderMode = runCatching { DecoderMode.valueOf(p[kDecoder] ?: "HARDWARE") }.getOrDefault(DecoderMode.HARDWARE),
            defaultSection = p[kDefaultSection] ?: "LIVE",
            guideRowsDensity = p[kDensity] ?: 2,
            hiddenCategories = strList(p[kHidden]).toSet(),
            categoryOrder = strList(p[kOrder]),
            categorySort = runCatching { CategorySort.valueOf(p[kSort] ?: "DEFAULT") }.getOrDefault(CategorySort.DEFAULT),
            lockedCategories = strList(p[kLocked]).toSet(),
            pin = p[kPin] ?: ""
        )
    }

    suspend fun setAutoFetchEpg(v: Boolean) = context.settingsStore.edit { it[kAutoEpg] = v }
    suspend fun setEpgRefreshMinutes(v: Int) = context.settingsStore.edit { it[kEpgMin] = v }
    suspend fun setUse24h(v: Boolean) = context.settingsStore.edit { it[k24h] = v }
    suspend fun setTzOffset(min: Int) = context.settingsStore.edit { it[kTzOffset] = min }
    suspend fun setBufferSeconds(v: Int) = context.settingsStore.edit { it[kBuffer] = v }
    suspend fun setBufferPreset(p: BufferPreset) = context.settingsStore.edit {
        it[kBufPreset] = p.name
        it[kBuffer] = when (p) { BufferPreset.LOW -> 10; BufferPreset.BALANCED -> 30; BufferPreset.HIGH -> 60 }
    }
    suspend fun setStreamFormat(f: StreamFormat) = context.settingsStore.edit { it[kFormat] = f.name }
    suspend fun setVideoFit(f: VideoFit) = context.settingsStore.edit { it[kFit] = f.name }
    suspend fun setDecoderMode(d: DecoderMode) = context.settingsStore.edit { it[kDecoder] = d.name }
    suspend fun setDefaultSection(s: String) = context.settingsStore.edit { it[kDefaultSection] = s }
    suspend fun setGuideDensity(v: Int) = context.settingsStore.edit { it[kDensity] = v }
    suspend fun setHiddenCategories(ids: Set<String>) = context.settingsStore.edit { it[kHidden] = gson.toJson(ids.toList()) }
    suspend fun setCategoryOrder(ids: List<String>) = context.settingsStore.edit { it[kOrder] = gson.toJson(ids) }
    suspend fun setCategorySort(s: CategorySort) = context.settingsStore.edit { it[kSort] = s.name }
    suspend fun setLockedCategories(ids: Set<String>) = context.settingsStore.edit { it[kLocked] = gson.toJson(ids.toList()) }
    suspend fun setPin(pin: String) = context.settingsStore.edit { it[kPin] = pin }

    suspend fun resetAll() = context.settingsStore.edit { it.clear() }
}
