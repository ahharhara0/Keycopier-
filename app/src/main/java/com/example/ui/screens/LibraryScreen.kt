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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.domain.model.AppSettings
import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.engine.BittingCalculator
import com.example.ui.VaultUiState
import com.example.ui.components.KeySilhouetteCanvas
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vaultState: VaultUiState,
    settings: AppSettings,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (KeyCategory?) -> Unit,
    onToggleGridView: () -> Unit,
    onToggleFavorite: (KeyRecord) -> Unit,
    onEditInStudio: (KeyRecord) -> Unit,
    onOpenInReports: (KeyRecord) -> Unit,
    onShareSpec: (KeyRecord) -> Unit,
    onDeleteKey: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedRecord by remember { mutableStateOf<KeyRecord?>(null) }
    var deleteCandidate by remember { mutableStateOf<KeyRecord?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Header + Grid/List Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(
                onClick = onToggleGridView,
                modifier = Modifier.testTag("library_grid_toggle")
            ) {
                Icon(
                    imageVector = if (vaultState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle layout"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search Field
        OutlinedTextField(
            value = vaultState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (vaultState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input")
        )

        Spacer(Modifier.height(10.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = vaultState.selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text(stringResource(R.string.library_filter_all)) }
            )
            KeyCategory.entries.forEach { cat ->
                val label = if (settings.language == AppLanguage.ARABIC) cat.labelAr else cat.labelEn
                FilterChip(
                    selected = vaultState.selectedCategory == cat,
                    onClick = { onSelectCategory(cat) },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (vaultState.filteredKeys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.library_empty_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.library_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (vaultState.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 165.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vaultState.filteredKeys, key = { it.id }) { record ->
                    LibraryKeyGridCard(
                        record = record,
                        language = settings.language,
                        onClick = { selectedRecord = record },
                        onToggleFavorite = { onToggleFavorite(record) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vaultState.filteredKeys, key = { it.id }) { record ->
                    LibraryKeyListCard(
                        record = record,
                        language = settings.language,
                        onClick = { selectedRecord = record },
                        onToggleFavorite = { onToggleFavorite(record) }
                    )
                }
            }
        }
    }

    // Detail Bottom Sheet
    val detail = selectedRecord
    if (detail != null) {
        ModalBottomSheet(onDismissRequest = { selectedRecord = null }) {
            KeyDetailSheetContent(
                record = detail,
                settings = settings,
                onEdit = {
                    selectedRecord = null
                    onEditInStudio(detail)
                },
                onExport = {
                    selectedRecord = null
                    onOpenInReports(detail)
                },
                onShare = { onShareSpec(detail) },
                onDelete = { deleteCandidate = detail }
            )
        }
    }

    // Delete Confirmation Dialog
    val pendingDelete = deleteCandidate
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.library_delete_confirm_title)) },
            text = { Text(String.format(stringResource(R.string.library_delete_confirm_msg), pendingDelete.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteKey(pendingDelete.id)
                        deleteCandidate = null
                        selectedRecord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun LibraryKeyListCard(
    record: KeyRecord,
    language: AppLanguage,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val categoryLabel = if (language == AppLanguage.ARABIC) record.category.labelAr else record.category.labelEn
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("library_key_card_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KeySilhouetteCanvas(
                record = record,
                modifier = Modifier
                    .width(96.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = record.bittingCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${record.keyStandardId} • $categoryLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (record.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.library_favorite_toggle),
                    tint = if (record.isFavorite) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LibraryKeyGridCard(
    record: KeyRecord,
    language: AppLanguage,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val categoryLabel = if (language == AppLanguage.ARABIC) record.category.labelAr else record.category.labelEn
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = record.bittingCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (record.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.library_favorite_toggle),
                        tint = if (record.isFavorite) StatusWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            KeySilhouetteCanvas(
                record = record,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = record.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${record.keyStandardId} • $categoryLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KeyDetailSheetContent(
    record: KeyRecord,
    settings: AppSettings,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val spec = KeyStandards.getById(record.keyStandardId)
    val digits = record.bittingCode.filter { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = record.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "${spec.displayName} • ${String.format(stringResource(R.string.library_pins_count), record.pinCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = record.bittingCode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        KeySilhouetteCanvas(
            record = record,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp)
        )

        // Pin Depths Table
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                record.spacingsMm.forEachIndexed { idx, sp ->
                    val depth = record.depthsMm.getOrElse(idx) { 0f }
                    val codeChar = digits.getOrNull(idx)?.toString() ?: "-"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pin #${idx + 1}", style = MaterialTheme.typography.labelMedium)
                        Text(
                            BittingCalculator.formatDimension(sp, settings.preferredUnit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            BittingCalculator.formatDimension(depth, settings.preferredUnit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "[$codeChar]",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (idx < record.spacingsMm.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }

        if (record.notes.isNotBlank()) {
            Text(
                text = record.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library_edit_in_studio), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library_export_templates), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_share))
            }
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.library_delete_key), color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
