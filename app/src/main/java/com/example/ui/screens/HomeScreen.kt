package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppSettings
import com.example.domain.model.KeyRecord
import com.example.ui.StudioMode
import com.example.ui.VaultUiState
import com.example.ui.components.KeySilhouetteCanvas
import com.example.ui.theme.BlueprintNavy
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SecondaryTeal
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun HomeScreen(
    vaultState: VaultUiState,
    settings: AppSettings,
    onOpenStudio: (StudioMode) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenReports: () -> Unit,
    onSelectKey: (KeyRecord) -> Unit,
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalKeys = vaultState.allKeys.size
    val favoriteCount = vaultState.allKeys.count { it.isFavorite }
    val recentKeys = vaultState.allKeys.take(4)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.home_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onLockNow,
                    modifier = Modifier.testTag("home_lock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.pin_unlock_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Blueprint Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BlueprintNavy)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_decoder_hero),
                        contentDescription = stringResource(R.string.home_hero_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        BlueprintNavy.copy(alpha = 0.35f),
                                        BlueprintNavy.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SecondaryTeal.copy(alpha = 0.22f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF5EEAD4),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "100% OFFLINE • ZERO CLOUD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF5EEAD4)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.home_hero_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.home_hero_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onOpenStudio(StudioMode.BITTING_CAD) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("home_launch_studio_btn")
                            ) {
                                Text(stringResource(R.string.home_start_decoding))
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2x2 Stats Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        title = stringResource(R.string.home_stat_total_keys),
                        value = totalKeys.toString(),
                        icon = Icons.Default.Key,
                        accent = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = stringResource(R.string.home_stat_favorites),
                        value = favoriteCount.toString(),
                        icon = Icons.Default.Favorite,
                        accent = StatusWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        title = stringResource(R.string.home_stat_templates),
                        value = settings.templatesGeneratedCount.toString(),
                        icon = Icons.Default.ViewInAr,
                        accent = SecondaryTeal,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        title = stringResource(R.string.home_stat_security),
                        value = "AES-256",
                        icon = Icons.Default.Shield,
                        accent = StatusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Actions
        item {
            Text(
                text = stringResource(R.string.home_quick_actions),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    label = stringResource(R.string.home_action_photo_decode),
                    icon = Icons.Default.CameraAlt,
                    tint = PrimaryBlue,
                    onClick = { onOpenStudio(StudioMode.PHOTO_CALIBRATE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_photo")
                )
                QuickActionTile(
                    label = stringResource(R.string.home_action_screen_ruler),
                    icon = Icons.Default.Straighten,
                    tint = SecondaryTeal,
                    onClick = { onOpenStudio(StudioMode.SCREEN_RULER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_ruler")
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionTile(
                    label = stringResource(R.string.home_action_library),
                    icon = Icons.Default.FolderCopy,
                    tint = StatusWarning,
                    onClick = onOpenLibrary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_library")
                )
                QuickActionTile(
                    label = stringResource(R.string.home_action_templates),
                    icon = Icons.Default.FileDownload,
                    tint = StatusSuccess,
                    onClick = onOpenReports,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_action_templates")
                )
            }
        }

        // Recent Keys
        item {
            Text(
                text = stringResource(R.string.home_recent_keys),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (recentKeys.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.home_empty_recent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentKeys, key = { it.id }) { keyRecord ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectKey(keyRecord) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        KeySilhouetteCanvas(
                            record = keyRecord,
                            modifier = Modifier
                                .width(88.dp)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = keyRecord.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${keyRecord.keyStandardId} • ${stringResource(R.string.library_bitting_label)}: ${keyRecord.bittingCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
