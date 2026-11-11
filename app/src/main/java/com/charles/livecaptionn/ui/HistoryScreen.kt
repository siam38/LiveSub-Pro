package com.charles.livecaptionn.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.data.TranscriptEntry
import com.charles.livecaptionn.data.TranscriptExport
import com.charles.livecaptionn.data.TranscriptHistoryStore
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyStore: TranscriptHistoryStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val t = LocalUiStrings.current
    var entries by remember { mutableStateOf<List<TranscriptEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPairFilter by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<TranscriptEntry?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entries = historyStore.getAll()
    }

    val languagePairs = remember(entries) {
        entries.map { "${it.sourceLanguage.uppercase()} → ${it.targetLanguage.uppercase()}" }
            .distinct()
    }

    val filteredEntries = remember(entries, searchQuery, selectedPairFilter) {
        entries.filter { entry ->
            val pairLabel = "${entry.sourceLanguage.uppercase()} → ${entry.targetLanguage.uppercase()}"
            val matchesPair = selectedPairFilter == null || pairLabel == selectedPairFilter
            val matchesSearch = searchQuery.isBlank() ||
                entry.originalText.contains(searchQuery, ignoreCase = true) ||
                entry.translatedText.contains(searchQuery, ignoreCase = true)
            matchesPair && matchesSearch
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t["Delete entry"]) },
            text = {
                Text(t.format("Delete this transcript entry?\n\n\"%s\"", deleteTarget?.originalText?.take(60)))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget ?: return@TextButton
                    scope.launch {
                        historyStore.deleteById(target.id)
                        entries = historyStore.getAll()
                    }
                    deleteTarget = null
                }) {
                    Text(t["Delete"], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(t["Cancel"])
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(t["Clear"]) },
            text = {
                Text("Delete all ${entries.size} saved transcript entries from this device?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            historyStore.clear()
                            entries = emptyList()
                            selectedPairFilter = null
                        }
                        showClearAllConfirm = false
                    }
                ) {
                    Text(t["Delete"], color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(t["Cancel"])
                }
            }
        )
    }

    fun shareExport(payload: String, mimeType: String = "text/plain") {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        context.startActivity(Intent.createChooser(share, t["Share transcripts"]))
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = t["Transcript History"],
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (entries.isNotEmpty()) {
                            Text(
                                text = "${filteredEntries.size} / ${entries.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t["Back"])
                    }
                },
                actions = {
                    if (filteredEntries.isNotEmpty()) {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Filled.IosShare, contentDescription = t["Share transcripts"])
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Plain Text (.txt)") },
                                onClick = {
                                    showExportMenu = false
                                    shareExport(TranscriptExport(filteredEntries).asText())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export SubRip Subtitles (.srt)") },
                                onClick = {
                                    showExportMenu = false
                                    shareExport(TranscriptExport(filteredEntries).asSrt())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export JSON (.json)") },
                                onClick = {
                                    showExportMenu = false
                                    shareExport(TranscriptExport(filteredEntries).asJson(), "application/json")
                                }
                            )
                        }
                    }
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t["Clear"])
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (entries.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(t["Search transcripts…"]) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = t["Clear search"], modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                if (languagePairs.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedPairFilter == null,
                            onClick = { selectedPairFilter = null },
                            label = { Text("All (${entries.size})") }
                        )
                        languagePairs.forEach { pair ->
                            FilterChip(
                                selected = selectedPairFilter == pair,
                                onClick = {
                                    selectedPairFilter = if (selectedPairFilter == pair) null else pair
                                },
                                label = { Text(pair) }
                            )
                        }
                    }
                }
            }

            if (filteredEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.SubtitlesOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank() || selectedPairFilter != null) t["No matching transcripts"]
                        else t["No transcripts yet"],
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (searchQuery.isNotBlank() || selectedPairFilter != null) t["Try a different search term."]
                        else t["Transcripts will appear here as you use captioning."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TranscriptCard(
                            entry = entry,
                            onDelete = { deleteTarget = entry }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    entry: TranscriptEntry,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val t = LocalUiStrings.current
    val timeStr = remember(entry.timestamp) {
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        fmt.format(Date(entry.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${entry.sourceLanguage.uppercase()} \u2192 ${entry.targetLanguage.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val data = ClipData.newPlainText(
                                "transcript",
                                "${entry.originalText}\n${entry.translatedText}"
                            )
                            clip.setPrimaryClip(data)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = t["Copy"],
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = t["Delete"],
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (entry.originalText.isNotBlank() &&
                !entry.originalText.trim().equals(entry.translatedText.trim(), ignoreCase = true)
            ) {
                Text(
                    text = entry.originalText,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = entry.translatedText.ifBlank { entry.originalText },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
