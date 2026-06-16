package com.gwstreams.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first

private val Context.tvPrefs by preferencesDataStore("gws_tv_prefs")

/** Favorites (#3), recent channels (#5), and last-position resume (#8). */
class TvPrefsRepository(private val context: Context) {
    private val gson = Gson()
    private val kFav = stringPreferencesKey("favorites")
    private val kRecent = stringPreferencesKey("recent")
    private val kLastSection = stringPreferencesKey("last_section")
    private val kLastCategory = stringPreferencesKey("last_category")
    private val maxRecent = 12

    private suspend fun readIntList(key: androidx.datastore.preferences.core.Preferences.Key<String>): List<Int> {
        val raw = context.tvPrefs.data.first()[key] ?: return emptyList()
        return runCatching {
            gson.fromJson<List<Int>>(raw, object : TypeToken<List<Int>>() {}.type)
        }.getOrDefault(emptyList())
    }

    suspend fun favorites(): Set<Int> = readIntList(kFav).toSet()

    suspend fun toggleFavorite(id: Int): Set<Int> {
        val cur = favorites().toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        context.tvPrefs.edit { it[kFav] = gson.toJson(cur.toList()) }
        return cur
    }

    suspend fun recentChannels(): List<Int> = readIntList(kRecent)

    suspend fun pushRecent(id: Int): List<Int> {
        val cur = recentChannels().toMutableList()
        cur.remove(id)
        cur.add(0, id)
        val trimmed = cur.take(maxRecent)
        context.tvPrefs.edit { it[kRecent] = gson.toJson(trimmed) }
        return trimmed
    }

    suspend fun saveLastPosition(section: String, categoryId: String?) {
        context.tvPrefs.edit {
            it[kLastSection] = section
            it[kLastCategory] = categoryId ?: ""
        }
    }

    suspend fun lastSection(): String? = context.tvPrefs.data.first()[kLastSection]
    suspend fun lastCategory(): String? =
        context.tvPrefs.data.first()[kLastCategory]?.ifBlank { null }
}
