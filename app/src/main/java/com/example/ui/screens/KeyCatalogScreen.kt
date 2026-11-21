package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.CatalogCategory
import com.example.domain.model.KeyBlankCatalog
import com.example.domain.model.KeyCatalogPreset
import com.example.ui.components.KeySilhouetteCanvas
import com.example.ui.theme.BlueprintNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal

@Composable
fun KeyCatalogScreen(
    settings: AppSettings,
    onSelectPreset: (preset: KeyCatalogPreset, openLongitudinalPad: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = settings.language == AppLanguage.ARABIC
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CatalogCategory.ALL) }
    var selectedPinFilter by remember { mutableStateOf<Int?>(null) }
    var uncutOnly by remember { mutableStateOf(false) }
    var doubleSidedOnly by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedCategory, selectedPinFilter, searchQuery, uncutOnly, doubleSidedOnly) {
        gridState.scrollToItem(0)
    }

    val filteredPresets = remember(selectedCategory, selectedPinFilter, searchQuery, uncutOnly, doubleSidedOnly) {
        KeyBlankCatalog.filter(
            category = selectedCategory,
            pinCountFilter = selectedPinFilter,
            query = searchQuery,
            uncutOnly = uncutOnly,
            doubleSidedOnly = doubleSidedOnly
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("catalog_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_cancel)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.catalog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.catalog_subtitle, filteredPresets.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PrimaryBlue.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "${filteredPresets.size}/1000",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.pin_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("catalog_search_input")
        )

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CatalogCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(if (isArabic) cat.labelAr else cat.labelEn) },
                    modifier = Modifier.testTag("catalog_cat_${cat.name.lowercase()}")
                )
            }
        }

        // Pin Count Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedPinFilter == null,
                onClick = { selectedPinFilter = null },
                label = { Text(stringResource(R.string.library_filter_all)) }
            )
            listOf(3, 4, 5, 6, 7, 8).forEach { pins ->
                FilterChip(
                    selected = selectedPinFilter == pins,
                    onClick = {
                        selectedPinFilter = if (selectedPinFilter == pins) null else pins
                    },
                    label = { Text(if (pins == 8) "8P+" else "${pins}P") },
                    modifier = Modifier.testTag("catalog_pins_$pins")
                )
            }
            FilterChip(
                selected = uncutOnly,
                onClick = { uncutOnly = !uncutOnly },
                label = { Text(stringResource(R.string.catalog_filter_blanks)) },
                modifier = Modifier.testTag("catalog_filter_blanks_chip")
            )
            FilterChip(
                selected = doubleSidedOnly,
                onClick = { doubleSidedOnly = !doubleSidedOnly },
                label = { Text(stringResource(R.string.studio_double_sided)) },
                modifier = Modifier.testTag("catalog_filter_double_chip")
            )
        }

        // 2-Column Interactive Blueprint Grid
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("catalog_grid")
        ) {
            items(filteredPresets, key = { it.id }) { preset ->
                val previewRecord = remember(preset.id, isArabic) {
                    preset.toPreviewRecord(isArabic)
                }
                CatalogPresetCard(
                    preset = preset,
                    previewRecord = previewRecord,
                    isArabic = isArabic,
                    onOpenScreenMatch = { onSelectPreset(preset, true) },
                    onOpenCad = { onSelectPreset(preset, false) }
                )
            }
        }
    }
}

@Composable
private fun CatalogPresetCard(
    preset: KeyCatalogPreset,
    previewRecord: com.example.domain.model.KeyRecord,
    isArabic: Boolean,
    onOpenScreenMatch: () -> Unit,
    onOpenCad: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenScreenMatch)
            .testTag("catalog_item_${preset.number}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BlueprintNavy)
                    .padding(6.dp)
            ) {
                KeySilhouetteCanvas(
                    record = previewRecord,
                    strokeColor = Color(0xFF38BDF8),
                    fillColor = PrimaryBlue.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xCC0F172A),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "#${preset.number.toString().padStart(3, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (preset.isDoubleSided) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SecondaryTeal.copy(alpha = 0.24f),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = stringResource(R.string.studio_double_sided),
                            tint = Color(0xFF5EEAD4),
                            modifier = Modifier
                                .padding(3.dp)
                                .size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = if (isArabic) preset.nameAr else preset.nameEn,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${preset.codeTag} • ${preset.pinCount}P • ${preset.bittingCode.ifEmpty { "0".repeat(preset.pinCount) }}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SecondaryTeal.copy(alpha = 0.14f),
                    modifier = Modifier.clickable(onClick = onOpenCad)
                ) {
                    Text(
                        text = "CAD",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SecondaryTeal,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PrimaryBlue.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Straighten,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.catalog_match_on_screen),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
