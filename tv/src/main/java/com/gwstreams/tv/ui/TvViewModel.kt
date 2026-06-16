package com.gwstreams.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.model.Category
import com.gwstreams.app.data.repo.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class TvSection { LIVE, MOVIES, SERIES, SETTINGS }

data class TvContentItem(
    val id: Int,
    val title: String,
    val image: String?,
    val rating: String? = null,
    val containerExt: String? = null,
    val num: Int? = null,
    val section: TvSection
)

data class TvUiState(
    val section: TvSection = TvSection.LIVE,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val items: List<TvContentItem> = emptyList(),
    val nowNext: Map<Int, NowNext> = emptyMap(),
    val timelineEpg: Map<Int, List<com.gwstreams.app.data.repo.Programme>> = emptyMap(),
    val favorites: Set<Int> = emptySet(),
    val recentChannelIds: List<Int> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val settings: AppSettings = AppSettings(),
    val loggedIn: Boolean = false,
    val autoLoggingIn: Boolean = false,
    val savedHost: String = "",
    val savedUser: String = "",
    val savedPass: String = ""
)

class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = XtreamRepository()
    private val settingsRepo = SettingsRepository(app)
    private val creds = com.gwstreams.tv.data.TvCredentialStore(app)
    private val prefs = com.gwstreams.tv.data.TvPrefsRepository(app)

    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state

    private val catCache = mutableMapOf<TvSection, List<Category>>()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(settings = settingsRepo.load())
            // Surface any saved credentials so the login screen can prefill,
            // and attempt a silent auto-login so the app stays signed in.
            creds.load()?.let { c ->
                _state.value = _state.value.copy(
                    savedHost = c.host, savedUser = c.user, savedPass = c.pass
                )
                attemptAutoLogin(c)
            }
        }
    }

    private fun attemptAutoLogin(c: com.gwstreams.tv.data.TvCredentialStore.Creds) {
        _state.value = _state.value.copy(autoLoggingIn = true)
        viewModelScope.launch {
            val result = repo.login(c.host, c.user, c.pass)
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(autoLoggingIn = false, loggedIn = true)
                    restoreAfterLogin()
                },
                onFailure = {
                    // Saved creds failed (expired/changed) — fall back to the login screen.
                    _state.value = _state.value.copy(autoLoggingIn = false)
                }
            )
        }
    }

    /** Load favorites/recents and resume the last section + category (#8). */
    private fun restoreAfterLogin() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                favorites = prefs.favorites(),
                recentChannelIds = prefs.recentChannels()
            )
            val pref = _state.value.settings.defaultSection
            val lastSection = if (pref == "LAST") {
                prefs.lastSection()?.let { name -> runCatching { TvSection.valueOf(name) }.getOrNull() } ?: TvSection.LIVE
            } else {
                runCatching { TvSection.valueOf(pref) }.getOrNull() ?: TvSection.LIVE
            }
            val lastCat = if (pref == "LAST") prefs.lastCategory() else null
            selectSectionRestoring(lastSection, lastCat)
        }
    }

    /** Like selectSection but lets us jump straight to a remembered category. */
    private fun selectSectionRestoring(section: TvSection, categoryId: String?) {
        if (section == TvSection.SETTINGS) { selectSection(TvSection.LIVE); return }
        _state.value = _state.value.copy(section = section, loading = true, error = null, query = "")
        viewModelScope.launch {
            try {
                val cats = catCache.getOrPut(section) {
                    when (section) {
                        TvSection.LIVE -> repo.liveCategories()
                        TvSection.MOVIES -> repo.vodCategories()
                        TvSection.SERIES -> repo.seriesCategories()
                        TvSection.SETTINGS -> emptyList()
                    }
                }
                val visible = applyCategoryPrefs(cats)
                _state.value = _state.value.copy(categories = visible)
                val target = if (categoryId != null && visible.any { it.categoryId == categoryId })
                    categoryId else visible.firstOrNull()?.categoryId
                selectCategory(target)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Couldn't load. Check connection.")
            }
        }
    }

    val expiry: String? get() = Session.userInfo?.expDate

    fun login(host: String, user: String, pass: String, remember: Boolean, onResult: (Boolean, String?) -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = repo.login(host, user, pass)
            result.fold(
                onSuccess = {
                    if (remember) creds.save(repo.normalizeHost(host), user, pass)
                    _state.value = _state.value.copy(loading = false, loggedIn = true)
                    restoreAfterLogin()
                    onResult(true, null)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                    onResult(false, e.message)
                }
            )
        }
    }

    fun selectSection(section: TvSection) {
        if (section == TvSection.SETTINGS) {
            _state.value = _state.value.copy(section = section)
            return
        }
        _state.value = _state.value.copy(section = section, loading = true, error = null, query = "")
        viewModelScope.launch {
            try {
                val cats = catCache.getOrPut(section) {
                    when (section) {
                        TvSection.LIVE -> repo.liveCategories()
                        TvSection.MOVIES -> repo.vodCategories()
                        TvSection.SERIES -> repo.seriesCategories()
                        TvSection.SETTINGS -> emptyList()
                    }
                }
                val visible = applyCategoryPrefs(cats)
                _state.value = _state.value.copy(categories = visible)
                selectCategory(visible.firstOrNull()?.categoryId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Couldn't load. Check connection.")
            }
        }
    }

    /** Apply hidden + ordering settings to a category list. */
    private fun applyCategoryPrefs(cats: List<Category>): List<Category> {
        val s = _state.value.settings
        val visible = cats.filterNot { it.categoryId in s.hiddenCategories }
        return when (s.categorySort) {
            com.gwstreams.app.data.repo.CategorySort.ALPHABETICAL ->
                visible.sortedBy { it.categoryName.lowercase() }
            com.gwstreams.app.data.repo.CategorySort.MANUAL -> {
                if (s.categoryOrder.isEmpty()) visible
                else {
                    val orderIndex = s.categoryOrder.withIndex().associate { (i, id) -> id to i }
                    visible.sortedBy { orderIndex[it.categoryId] ?: Int.MAX_VALUE }
                }
            }
            else -> visible
        }
    }

    fun selectCategory(categoryId: String?) {
        _state.value = _state.value.copy(
            selectedCategory = categoryId, loading = true,
            nowNext = emptyMap(), timelineEpg = emptyMap()
        )
        viewModelScope.launch {
            // Remember where we are for resume-on-launch (#8).
            prefs.saveLastPosition(_state.value.section.name, categoryId)
            try {
                val section = _state.value.section
                val items = when (section) {
                    TvSection.LIVE -> repo.liveStreams(categoryId).map {
                        TvContentItem(it.streamId, it.name, it.streamIcon, num = it.num, section = TvSection.LIVE)
                    }
                    TvSection.MOVIES -> repo.vodStreams(categoryId).map {
                        TvContentItem(it.streamId, it.name, it.streamIcon, it.rating,
                            containerExt = it.containerExtension, section = TvSection.MOVIES)
                    }
                    TvSection.SERIES -> repo.series(categoryId).map {
                        TvContentItem(it.seriesId, it.name, it.cover, it.rating, section = TvSection.SERIES)
                    }
                    TvSection.SETTINGS -> emptyList()
                }
                _state.value = _state.value.copy(items = items, loading = false)
                if (section == TvSection.LIVE && _state.value.settings.autoFetchEpg) {
                    fetchEpg(items.map { it.id })
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Couldn't load content.")
            }
        }
    }

    private fun fetchEpg(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            // Now/next first (fast paint), then full timeline for the whole category (#4, #6).
            val nn = repo.batchNowNext(ids)
            _state.value = _state.value.copy(nowNext = nn)
            val timeline = repo.batchFullEpg(ids)
            val parsed = timeline.mapValues { (_, listings) ->
                com.gwstreams.app.data.repo.EpgParser.toProgrammes(listings)
            }
            _state.value = _state.value.copy(timelineEpg = parsed)
        }
    }

    fun refreshEpg() {
        if (_state.value.section == TvSection.LIVE) fetchEpg(_state.value.items.map { it.id })
    }

    fun onQuery(q: String) { _state.value = _state.value.copy(query = q) }

    fun visibleItems(): List<TvContentItem> {
        val s = _state.value
        var all = s.items
        if (s.section == TvSection.LIVE && s.showFavoritesOnly) {
            all = all.filter { it.id in s.favorites }
        }
        val q = s.query.trim()
        if (q.isNotEmpty()) {
            all = all.filter { it.title.contains(q, ignoreCase = true) || it.num?.toString() == q }
        }
        return all
    }

    fun nowNextFor(id: Int): NowNext? = _state.value.nowNext[id]
    fun timelineFor(id: Int): List<com.gwstreams.app.data.repo.Programme> =
        _state.value.timelineEpg[id] ?: emptyList()

    suspend fun epgFor(id: Int) = repo.epgCached(id)

    // ---- Favorites (#3), recents (#5), last channel (#2) ----
    fun loadPrefs() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                favorites = prefs.favorites(),
                recentChannelIds = prefs.recentChannels()
            )
        }
    }

    fun toggleFavorite(id: Int) {
        viewModelScope.launch {
            val favs = prefs.toggleFavorite(id)
            _state.value = _state.value.copy(favorites = favs)
        }
    }

    fun toggleFavoritesOnly() {
        _state.value = _state.value.copy(showFavoritesOnly = !_state.value.showFavoritesOnly)
    }

    fun recordWatched(id: Int) {
        viewModelScope.launch {
            val recents = prefs.pushRecent(id)
            _state.value = _state.value.copy(recentChannelIds = recents)
        }
    }

    /** Previous channel for last-channel flip (#2); null if none. */
    fun lastChannelId(): Int? = _state.value.recentChannelIds.getOrNull(1)

    fun itemById(id: Int): TvContentItem? = _state.value.items.firstOrNull { it.id == id }

    // ---- Settings mutations ----
    fun setAutoFetchEpg(v: Boolean) = updateSettings { settingsRepo.setAutoFetchEpg(v); it.copy(autoFetchEpg = v) }
    fun setEpgRefreshMinutes(v: Int) = updateSettings { settingsRepo.setEpgRefreshMinutes(v); it.copy(epgRefreshMinutes = v) }
    fun setBufferSeconds(v: Int) = updateSettings { settingsRepo.setBufferSeconds(v); it.copy(bufferSeconds = v) }
    fun setUse24h(v: Boolean) = updateSettings { settingsRepo.setUse24h(v); it.copy(use24hTime = v) }
    fun setTzOffset(min: Int) = updateSettings { settingsRepo.setTzOffset(min); it.copy(epgTimezoneOffsetMin = min) }
    fun setBufferPreset(p: com.gwstreams.app.data.repo.BufferPreset) = updateSettings {
        settingsRepo.setBufferPreset(p)
        val secs = when (p) {
            com.gwstreams.app.data.repo.BufferPreset.LOW -> 10
            com.gwstreams.app.data.repo.BufferPreset.BALANCED -> 30
            com.gwstreams.app.data.repo.BufferPreset.HIGH -> 60
        }
        it.copy(bufferPreset = p, bufferSeconds = secs)
    }
    fun setStreamFormat(f: com.gwstreams.app.data.repo.StreamFormat) = updateSettings { settingsRepo.setStreamFormat(f); it.copy(streamFormat = f) }
    fun setVideoFit(f: com.gwstreams.app.data.repo.VideoFit) = updateSettings { settingsRepo.setVideoFit(f); it.copy(videoFit = f) }
    fun setDecoderMode(d: com.gwstreams.app.data.repo.DecoderMode) = updateSettings { settingsRepo.setDecoderMode(d); it.copy(decoderMode = d) }
    fun setDefaultSection(s: String) = updateSettings { settingsRepo.setDefaultSection(s); it.copy(defaultSection = s) }
    fun setGuideDensity(v: Int) = updateSettings { settingsRepo.setGuideDensity(v); it.copy(guideRowsDensity = v) }
    fun setCategorySort(s: com.gwstreams.app.data.repo.CategorySort) = updateSettings { settingsRepo.setCategorySort(s); it.copy(categorySort = s) }
    fun setPin(pin: String) = updateSettings { settingsRepo.setPin(pin); it.copy(pin = pin) }

    fun toggleCategoryHidden(categoryId: String) {
        updateSettings { s ->
            val hidden = s.hiddenCategories.toMutableSet()
            if (!hidden.add(categoryId)) hidden.remove(categoryId)
            settingsRepo.setHiddenCategories(hidden)
            s.copy(hiddenCategories = hidden)
        }
    }

    fun toggleCategoryLocked(categoryId: String) {
        updateSettings { s ->
            val locked = s.lockedCategories.toMutableSet()
            if (!locked.add(categoryId)) locked.remove(categoryId)
            settingsRepo.setLockedCategories(locked)
            s.copy(lockedCategories = locked)
        }
    }

    /** Clear cached lists + EPG and re-pull the current category (#10). */
    fun clearCacheAndRefresh() {
        repo.clearCache()
        catCache.clear()
        selectSection(_state.value.section)
    }

    /** Reset all settings to defaults (#10). */
    fun resetAllSettings() {
        viewModelScope.launch {
            settingsRepo.resetAll()
            _state.value = _state.value.copy(settings = settingsRepo.load())
        }
    }

    /** Categories for the settings manager (unfiltered, from cache). */
    fun allCategoriesForSection(section: TvSection): List<Category> =
        catCache[section] ?: emptyList()

    private fun updateSettings(block: suspend (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = block(_state.value.settings)
            _state.value = _state.value.copy(settings = updated)
        }
    }

    fun logout() {
        Session.host = ""; Session.username = ""; Session.password = ""; Session.userInfo = null
        repo.clearCache()
        catCache.clear()
        viewModelScope.launch { creds.clear() }
        _state.value = TvUiState(settings = _state.value.settings)
    }
}
