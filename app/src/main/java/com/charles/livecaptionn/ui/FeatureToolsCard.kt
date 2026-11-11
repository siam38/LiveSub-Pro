package com.charles.livecaptionn.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.data.CaptionProfile
import com.charles.livecaptionn.data.CaptionProfileRepository
import com.charles.livecaptionn.data.GlossaryEntry
import com.charles.livecaptionn.data.GlossaryRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch

@Composable
fun FeatureToolsCard(
    settings: CaptionSettings,
    profiles: CaptionProfileRepository,
    glossary: GlossaryRepository,
    micPermissionGranted: Boolean,
    overlayPermissionGranted: Boolean,
    modelReady: Boolean,
    onApplyProfile: (CaptionProfile) -> Unit,
    onSaveHistoryChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalUiStrings.current
    var profileItems by remember { mutableStateOf<List<CaptionProfile>>(emptyList()) }
    var glossaryItems by remember { mutableStateOf<List<GlossaryEntry>>(emptyList()) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var showGlossaryDialog by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profileItems = profiles.list()
        glossaryItems = glossary.list()
    }

    val isReady = overlayPermissionGranted &&
        (settings.audioSource.name == "SYSTEM" || micPermissionGranted) &&
        modelReady

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t["Tools and privacy"],
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t[if (isReady) "Ready to start captioning" else "Setup needed before captioning"],
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Local transcript persistence toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t["Save transcripts to local history"],
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = t["Stored privately on this device for search and SRT/TXT export."],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = settings.saveHistory, onCheckedChange = onSaveHistoryChange)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Saved Presets section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t["Presets"],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t["Save language pairs and subtitle sizing for one-tap switching."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        presetName = "${settings.sourceLanguageCode.uppercase()} → ${settings.targetLanguageCode.uppercase()} (${settings.textSizeSp.toInt()}sp)"
                        showPresetDialog = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Save preset"])
                }
            }

            if (profileItems.isEmpty()) {
                Text(
                    text = t["No saved presets yet."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profileItems.forEach { profile ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${profile.sourceLanguage.uppercase()} → ${profile.targetLanguage.uppercase()} · ${profile.textSizeSp.toInt()}sp" +
                                            if (profile.showOriginal) " · ${t["Bilingual"]}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onApplyProfile(profile) }) {
                                    Text(t["Use"], fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch { profiles.delete(profile.id) }
                                        profileItems = profileItems.filterNot { it.id == profile.id }
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = t["Delete"])
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Glossary section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t["Glossary and phrase replacements"],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = t["Fix character names, technical terms, or recurring phrases automatically."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showGlossaryDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t["Add"])
                }
            }

            if (glossaryItems.isEmpty()) {
                Text(
                    text = t["No glossary rules added yet."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    glossaryItems.forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entry.phrase}  →  ${entry.replacement}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (entry.caseSensitive) {
                                        Text(
                                            text = t["Case-sensitive"],
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch { glossary.delete(entry.id) }
                                        glossaryItems = glossaryItems.filterNot { it.id == entry.id }
                                    }
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = t["Delete"])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text(t["Save preset"]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text(t["Preset name"]) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${settings.sourceLanguageCode.uppercase()} → ${settings.targetLanguageCode.uppercase()} · ${settings.textSizeSp.toInt()}sp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = presetName.isNotBlank(),
                    onClick = {
                        val trimmedName = presetName.trim()
                        val profile = CaptionProfile(
                            name = trimmedName,
                            sourceLanguage = settings.sourceLanguageCode,
                            targetLanguage = settings.targetLanguageCode,
                            textSizeSp = settings.textSizeSp,
                            showOriginal = settings.showOriginal,
                            proOnly = false
                        )
                        scope.launch { profiles.save(profile) }
                        profileItems = profileItems.filterNot { it.name == profile.name } + profile
                        showPresetDialog = false
                    }
                ) { Text(t["Save"]) }
            },
            dismissButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text(t["Cancel"]) }
            }
        )
    }

    if (showGlossaryDialog) {
        AlertDialog(
            onDismissRequest = { showGlossaryDialog = false },
            title = { Text(t["Add glossary entry"]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = phrase,
                        onValueChange = { phrase = it },
                        label = { Text(t["Phrase"]) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text(t["Replacement"]) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = caseSensitive,
                            onCheckedChange = { caseSensitive = it }
                        )
                        Text(
                            text = t["Case-sensitive match"],
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = phrase.isNotBlank() && replacement.isNotBlank(),
                    onClick = {
                        val entry = GlossaryEntry(
                            phrase = phrase.trim(),
                            replacement = replacement.trim(),
                            caseSensitive = caseSensitive
                        )
                        scope.launch { glossary.save(entry) }
                        glossaryItems = glossaryItems + entry
                        showGlossaryDialog = false
                        phrase = ""
                        replacement = ""
                        caseSensitive = false
                    }
                ) { Text(t["Add"]) }
            },
            dismissButton = {
                TextButton(onClick = { showGlossaryDialog = false }) { Text(t["Cancel"]) }
            }
        )
    }
}
