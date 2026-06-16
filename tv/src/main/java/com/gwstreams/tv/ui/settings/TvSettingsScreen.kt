@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gwstreams.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.gwstreams.app.data.model.Category
import com.gwstreams.app.data.repo.*
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

@Composable
fun TvSettingsScreen(vm: TvViewModel, onLogout: () -> Unit) {
    val state by vm.state.collectAsState()
    val s = state.settings

    LazyColumn(
        contentPadding = PaddingValues(40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().background(Midnight)
    ) {
        item {
            Text("Settings", style = TvType.displayMedium, color = TextHi)
            Spacer(Modifier.height(16.dp))
        }

        // ---- Guide / EPG ----
        item { SectionHeader("Programme guide (EPG)") }
        item {
            ToggleRow(
                label = "Auto-fetch EPG when opening Live",
                checked = s.autoFetchEpg,
                onToggle = { vm.setAutoFetchEpg(!s.autoFetchEpg) }
            )
        }
        item {
            StepperRow(
                label = "EPG refresh interval",
                value = "${s.epgRefreshMinutes} min",
                onLess = { vm.setEpgRefreshMinutes((s.epgRefreshMinutes - 5).coerceAtLeast(5)) },
                onMore = { vm.setEpgRefreshMinutes((s.epgRefreshMinutes + 5).coerceAtMost(120)) }
            )
        }
        item {
            ToggleRow(
                label = "Use 24-hour time",
                checked = s.use24hTime,
                onToggle = { vm.setUse24h(!s.use24hTime) }
            )
        }
        item {
            StepperRow(
                label = "EPG timezone offset",
                value = "${if (s.epgTimezoneOffsetMin >= 0) "+" else ""}${s.epgTimezoneOffsetMin / 60}h ${kotlin.math.abs(s.epgTimezoneOffsetMin % 60)}m",
                onLess = { vm.setTzOffset(s.epgTimezoneOffsetMin - 30) },
                onMore = { vm.setTzOffset(s.epgTimezoneOffsetMin + 30) }
            )
        }

        // ---- Playback ----
        item { 
            Spacer(Modifier.height(8.dp))
            SectionHeader("Playback") 
        }
        item {
            ChooserRow(
                label = "Buffer",
                options = listOf("Low", "Balanced", "High"),
                selectedIndex = when (s.bufferPreset) {
                    BufferPreset.LOW -> 0
                    BufferPreset.BALANCED -> 1
                    BufferPreset.HIGH -> 2
                },
                onSelect = { idx ->
                    vm.setBufferPreset(
                        when (idx) {
                            0 -> BufferPreset.LOW
                            2 -> BufferPreset.HIGH
                            else -> BufferPreset.BALANCED
                        }
                    )
                }
            )
        }
        item {
            ChooserRow(
                label = "Live stream format",
                options = listOf("TS", "HLS"),
                selectedIndex = if (s.streamFormat == StreamFormat.HLS) 1 else 0,
                onSelect = { idx ->
                    vm.setStreamFormat(if (idx == 1) StreamFormat.HLS else StreamFormat.TS)
                }
            )
        }
        item {
            ChooserRow(
                label = "Video fit",
                options = listOf("Fit", "Zoom", "Stretch"),
                selectedIndex = when (s.videoFit) {
                    VideoFit.FIT -> 0
                    VideoFit.ZOOM -> 1
                    VideoFit.STRETCH -> 2
                },
                onSelect = { idx ->
                    vm.setVideoFit(
                        when (idx) {
                            1 -> VideoFit.ZOOM
                            2 -> VideoFit.STRETCH
                            else -> VideoFit.FIT
                        }
                    )
                }
            )
        }
        item {
            ChooserRow(
                label = "Decoder",
                options = listOf("Hardware", "Software"),
                selectedIndex = if (s.decoderMode == DecoderMode.SOFTWARE) 1 else 0,
                onSelect = { idx ->
                    vm.setDecoderMode(if (idx == 1) DecoderMode.SOFTWARE else DecoderMode.HARDWARE)
                }
            )
        }

        // ---- Layout / navigation ----
        item { 
            Spacer(Modifier.height(8.dp))
            SectionHeader("Layout & navigation") 
        }
        item {
            ChooserRow(
                label = "Open app on",
                options = listOf("Live", "Movies", "Series", "Last used"),
                selectedIndex = when (s.defaultSection) {
                    "MOVIES" -> 1
                    "SERIES" -> 2
                    "LAST" -> 3
                    else -> 0
                },
                onSelect = { idx ->
                    vm.setDefaultSection(
                        when (idx) {
                            1 -> "MOVIES"
                            2 -> "SERIES"
                            3 -> "LAST"
                            else -> "LIVE"
                        }
                    )
                }
            )
        }
        item {
            ChooserRow(
                label = "Guide density",
                options = listOf("Spacious", "Normal", "Dense"),
                selectedIndex = (s.guideRowsDensity - 1).coerceIn(0, 2),
                onSelect = { idx -> vm.setGuideDensity(idx + 1) }
            )
        }
        item {
            ChooserRow(
                label = "Category sort",
                options = listOf("Default", "A–Z"),
                selectedIndex = if (s.categorySort == CategorySort.ALPHABETICAL) 1 else 0,
                onSelect = { idx ->
                    vm.setCategorySort(if (idx == 1) CategorySort.ALPHABETICAL else CategorySort.DEFAULT)
                }
            )
        }

        // ---- Categories: hide / lock ----
        item { 
            Spacer(Modifier.height(8.dp))
            SectionHeader("Live categories — show / hide / lock") 
        }
        val liveCats = vm.allCategoriesForSection(TvSection.LIVE)
        items(liveCats, key = { it.categoryId }) { cat ->
            CategoryManageRow(
                cat = cat,
                hidden = cat.categoryId in s.hiddenCategories,
                locked = cat.categoryId in s.lockedCategories,
                onHide = { vm.toggleCategoryHidden(cat.categoryId) },
                onLock = { vm.toggleCategoryLocked(cat.categoryId) }
            )
        }

        // ---- Maintenance ----
        item { 
            Spacer(Modifier.height(8.dp))
            SectionHeader("Maintenance") 
        }
        item { FocusButton("Clear cache & refresh", onClick = { vm.clearCacheAndRefresh() }) }
        item { FocusButton("Reset all settings to default", onClick = { vm.resetAllSettings() }) }
        item {
            Spacer(Modifier.height(16.dp))
            FocusButton("Log out", onClick = onLogout)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = TvType.labelLarge, color = Aqua, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onToggle,
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TvType.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface1)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TvType.titleMedium, color = TextHi, modifier = Modifier.weight(1f))
        FocusButton("-", onLess, compact = true)
        Text(value, style = TvType.titleMedium, color = Aqua,
            modifier = Modifier.padding(horizontal = 14.dp).widthIn(min = 84.dp))
        FocusButton("+", onMore, compact = true)
    }
}

@Composable
private fun ChooserRow(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Surface1)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, style = TvType.titleMedium, color = TextHi)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, opt ->
                ChoiceChip(label = opt, active = (i == selectedIndex), onClick = { onSelect(i) })
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = if (active) Aqua else Surface2,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Text(label, style = TvType.labelLarge,
            color = if (active) Midnight else TextMid,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun CategoryManageRow(cat: Category, hidden: Boolean, locked: Boolean, onHide: () -> Unit, onLock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface1)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cat.categoryName, style = TvType.titleMedium,
            color = if (hidden) TextLow else TextHi, modifier = Modifier.weight(1f))
        SmallIconButton(
            icon = { Icon(if (hidden) Icons.Filled.CheckBoxOutlineBlank else Icons.Filled.CheckBox, "Show/hide", tint = if (hidden) TextLow else Aqua) },
            onClick = onHide
        )
        Spacer(Modifier.width(8.dp))
        SmallIconButton(
            icon = { Icon(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, "Lock", tint = if (locked) Coral else TextLow) },
            onClick = onLock
        )
    }
}

@Composable
private fun SmallIconButton(icon: @Composable () -> Unit, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface2,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Box(Modifier.padding(8.dp)) { icon() }
    }
}

@Composable
private fun FocusButton(label: String, onClick: () -> Unit, compact: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = if (focused) Aqua else Surface2,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Text(label, style = TvType.labelLarge,
            color = if (focused) Midnight else TextHi,
            modifier = Modifier.padding(
                horizontal = if (compact) 18.dp else 28.dp,
                vertical = if (compact) 8.dp else 14.dp))
    }
}
