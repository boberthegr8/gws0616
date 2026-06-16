@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gwstreams.tv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gwstreams.app.data.repo.NowNext
import com.gwstreams.app.data.repo.Programme
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvUiState
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

/**
 * TiviMate-style live screen. Dense layout (smaller rows = ~2x content on screen).
 * Left panel: brand, sections, categories, then SEARCH pinned at the bottom.
 * Right panel: channel rows with a multi-programme timeline guide.
 */
@Composable
fun TvLiveScreen(
    vm: TvViewModel,
    nowSec: Long,
    onPlay: (TvContentItem) -> Unit
) {
    val state by vm.state.collectAsState()

    Row(Modifier.fillMaxSize().background(Midnight)) {
        LeftPanel(
            state = state,
            onSection = vm::selectSection,
            onCategory = vm::selectCategory,
            onQuery = vm::onQuery,
            onToggleFavs = vm::toggleFavoritesOnly
        )
        Box(Modifier.weight(1f).fillMaxHeight().padding(6.dp)) {
            when {
                state.loading -> CircularProgressIndicator(
                    color = Aqua, modifier = Modifier.align(Alignment.Center)
                )
                state.error != null -> Text(
                    state.error!!, color = TextMid,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.section != TvSection.LIVE ->
                    com.gwstreams.tv.ui.vod.TvVodGrid(vm.visibleItems(), onPlay)
                else -> LiveGuide(vm, state, nowSec, onPlay)
            }
        }
    }
}

@Composable
private fun LeftPanel(
    state: TvUiState,
    onSection: (TvSection) -> Unit,
    onCategory: (String) -> Unit,
    onQuery: (String) -> Unit,
    onToggleFavs: () -> Unit
) {
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(Surface1)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row {
            Text("Great White ", style = TvType.titleLarge, color = TextHi)
            Text("Streams", style = TvType.titleLarge, color = Aqua)
        }
        Spacer(Modifier.height(12.dp))

        // Sections (compact)
        TvSection.values().forEach { section ->
            NavRow(
                label = section.label(),
                selected = state.section == section,
                onClick = { onSection(section) },
                dense = true
            )
        }

        if (state.section == TvSection.LIVE) {
            Spacer(Modifier.height(8.dp))
            NavRow(
                label = if (state.showFavoritesOnly) "\u2605 Favorites (on)" else "\u2606 Favorites only",
                selected = state.showFavoritesOnly,
                onClick = onToggleFavs,
                dense = true
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("CATEGORIES", style = TvType.labelSmall, color = TextLow)
        Spacer(Modifier.height(4.dp))

        // Categories take the remaining space; search is pinned below.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            items(state.categories, key = { it.categoryId }) { cat ->
                NavRow(
                    label = cat.categoryName,
                    selected = cat.categoryId == state.selectedCategory,
                    onClick = { onCategory(cat.categoryId) },
                    dense = true
                )
            }
        }

        // SEARCH pinned at the bottom of the left panel (per request).
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextLow) },
            placeholder = { Text("Search", color = TextLow) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Aqua,
                unfocusedBorderColor = SurfaceHi,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                cursorColor = Aqua,
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, dense: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = when {
        focused -> SurfaceHi
        selected -> Surface2
        else -> Color.Transparent
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Text(
            label,
            style = TvType.titleMedium,
            color = if (selected || focused) Aqua else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (dense) 7.dp else 12.dp)
        )
    }
}

@Composable
private fun LiveGuide(
    vm: TvViewModel,
    state: TvUiState,
    nowSec: Long,
    onPlay: (TvContentItem) -> Unit
) {
    val items = vm.visibleItems()
    // Density (#6): 1 spacious, 2 normal, 3 dense -> row height.
    val rowHeight = when (state.settings.guideRowsDensity) {
        1 -> 66
        3 -> 44
        else -> 52
    }
    val use24h = state.settings.use24hTime
    val tzMin = state.settings.epgTimezoneOffsetMin
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { "g-${it.id}" }) { item ->
            GuideRow(
                item = item,
                nowNext = state.nowNext[item.id],
                timeline = vm.timelineFor(item.id),
                isFavorite = item.id in state.favorites,
                nowSec = nowSec,
                rowHeight = rowHeight,
                use24h = use24h,
                tzMin = tzMin,
                onPlay = onPlay,
                onFavorite = { vm.toggleFavorite(item.id) }
            )
        }
    }
}

@Composable
private fun GuideRow(
    item: TvContentItem,
    nowNext: NowNext?,
    timeline: List<Programme>,
    isFavorite: Boolean,
    nowSec: Long,
    rowHeight: Int,
    use24h: Boolean,
    tzMin: Int,
    onPlay: (TvContentItem) -> Unit,
    onFavorite: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = { onPlay(item) },
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight.dp)
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Channel cell (compact)
            Row(
                Modifier.width(200.dp).fillMaxHeight().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.num != null) {
                    Text("${item.num}", style = TvType.labelSmall, color = TextLow,
                        modifier = Modifier.width(30.dp))
                }
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(5.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(item.title, style = TvType.titleMedium, color = TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (isFavorite) {
                    Icon(Icons.Filled.Star, "Favorite", tint = Aqua, modifier = Modifier.size(16.dp))
                }
            }
            // Timeline: now + several upcoming programmes (#4)
            val upcoming = remember(timeline, nowNext, nowSec) {
                val list = timeline.filter { it.stop > nowSec }.take(4)
                if (list.isNotEmpty()) list else listOfNotNull(nowNext?.now, nowNext?.next)
            }
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp, horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (upcoming.isEmpty()) {
                    Text("No guide data", style = TvType.bodyMedium, color = TextLow,
                        modifier = Modifier.align(Alignment.CenterVertically))
                } else {
                    upcoming.forEachIndexed { i, p ->
                        ProgrammeBlock(p, isNow = p.isLiveAt(nowSec), nowSec = nowSec,
                            weight = if (i == 0) 1.5f else 1f, use24h = use24h, tzMin = tzMin)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ProgrammeBlock(p: Programme, isNow: Boolean, nowSec: Long, weight: Float, use24h: Boolean, tzMin: Int) {
    Column(
        Modifier.weight(weight).fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isNow) Surface2 else Surface1)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            "${timeLabel(p.start, use24h, tzMin)} ${p.title}",
            style = TvType.bodyMedium,
            color = if (isNow) TextHi else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isNow) {
            Spacer(Modifier.height(2.dp))
            Box(
                Modifier.fillMaxWidth().height(2.dp)
                    .clip(RoundedCornerShape(1.dp)).background(SurfaceHi)
            ) {
                Box(
                    Modifier.fillMaxWidth(p.progressAt(nowSec)).fillMaxHeight()
                        .clip(RoundedCornerShape(1.dp)).background(Aqua)
                )
            }
        }
    }
}

private fun timeLabel(sec: Long, use24h: Boolean, tzMin: Int): String {
    val pattern = if (use24h) "HH:mm" else "h:mm a"
    val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
    return fmt.format(java.util.Date((sec + tzMin * 60) * 1000))
}

fun TvSection.label(): String = when (this) {
    TvSection.LIVE -> "Live TV"
    TvSection.MOVIES -> "Movies"
    TvSection.SERIES -> "Series"
    TvSection.SETTINGS -> "Settings"
}
