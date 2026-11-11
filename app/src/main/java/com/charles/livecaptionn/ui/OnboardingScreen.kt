package com.charles.livecaptionn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.speech.SherpaModelCatalog
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import com.charles.livecaptionn.ui.l10n.UiLanguagePickerDialog

/**
 * First-run setup, three steps: permissions, languages, speech model.
 * The app cannot be used until a speech model is on the phone, so the
 * model download is a real step here, not a settings afterthought.
 */
@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onRequestAudioPermission: () -> Unit = {},
    onOpenOverlaySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val t = LocalUiStrings.current
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }
    var selectedUiCode by remember { mutableStateOf(ui.uiLanguageCode.takeIf { it.isNotBlank() } ?: "en") }
    var showUiPicker by remember { mutableStateOf(false) }

    // Permission state must refresh when the user returns from system Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    t["LiveSub Pro"],
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    t["Live captions & translation on your device"],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Step indicator
        Text(
            text = when (step) {
                0 -> t["Step 1 of 3 · Permissions"]
                1 -> t["Step 2 of 3 · Languages"]
                else -> t["Step 3 of 3 · Speech model"]
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..2) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> PermissionsStep(
                micGranted = ui.micPermissionGranted,
                overlayGranted = ui.overlayPermissionGranted,
                onRequestAudioPermission = onRequestAudioPermission,
                onOpenOverlaySettings = onOpenOverlaySettings
            )
            1 -> LanguagesStep(
                sourceCode = ui.settings.sourceLanguageCode,
                targetCode = ui.settings.targetLanguageCode,
                targetOptions = ui.availableTargetLanguages,
                uiLanguageCode = selectedUiCode,
                onPickSource = { viewModel.updateSource(it) },
                onPickTarget = { viewModel.updateTarget(it) },
                onSwap = { viewModel.swapLanguages() },
                onPickUiLanguage = { showUiPicker = true }
            )
            else -> ModelStep(
                modelName = ui.sherpaNeededModel?.displayName,
                modelSizeMb = ui.sherpaNeededModel?.totalSizeMb ?: 0,
                modelReady = ui.sherpaActiveModel != null,
                downloading = ui.sherpaNeededModel?.let { ui.sherpaModels[it.id]?.downloading } == true,
                progress = ui.sherpaNeededModel?.let { ui.sherpaModels[it.id]?.progress } ?: 0f,
                error = ui.sherpaNeededModel?.let { ui.sherpaModels[it.id]?.error },
                onDownload = { ui.sherpaNeededModel?.let { viewModel.downloadSherpaModel(it) } },
                onCancel = { ui.sherpaNeededModel?.let { viewModel.cancelSherpaModelDownload(it) } }
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                OutlinedButton(
                    onClick = { step -= 1 },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(t["Back"]) }
            }
            when (step) {
                0 -> Button(
                    onClick = { step = 1 },
                    enabled = ui.micPermissionGranted && ui.overlayPermissionGranted,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(t["Continue"]) }
                1 -> Button(
                    onClick = { step = 2 },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(t["Continue"]) }
                else -> Button(
                    onClick = { viewModel.completeOnboarding(selectedUiCode) },
                    enabled = ui.sherpaActiveModel != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(t["Get started"]) }
            }
        }
    }

    if (showUiPicker) {
        UiLanguagePickerDialog(
            currentCode = selectedUiCode,
            busy = ui.uiLocalizationBusy,
            error = ui.uiLocalizationError,
            stage = ui.uiLocalizationStage,
            translatedCount = ui.uiLocalizationTranslated,
            translatedTotal = ui.uiLocalizationTotal,
            onSelect = { code ->
                selectedUiCode = code
                showUiPicker = false
            },
            onDismiss = { showUiPicker = false }
        )
    }
}

@Composable
private fun PermissionsStep(
    micGranted: Boolean,
    overlayGranted: Boolean,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupPermissionRow(t["Microphone"], t["For microphone captions"], micGranted, t["Grant"]) {
                onRequestAudioPermission()
            }
            SetupPermissionRow(t["Overlay"], t["Lets captions float over videos and other apps"], overlayGranted, t["Open settings"]) {
                onOpenOverlaySettings()
            }
            if (!overlayGranted) {
                var showHelp by remember { mutableStateOf(false) }
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(t["Can't turn on Overlay?"])
                }
                if (showHelp) {
                    Text(
                        text = t["On Android 13 and newer, apps installed outside the Play Store are blocked from this switch at first. Fix: open Settings → Apps → LiveSub Pro, tap the ⋮ menu in the top corner, choose “Allow restricted settings”, then come back and turn on “Display over other apps”."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = t["For System Audio, Android also asks to share your screen when you start. That's normal."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupPermissionRow(
    label: String,
    hint: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!granted) {
            // Fixed size so every permission row's button looks identical,
            // whatever its label ("Grant" vs "Open settings").
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(140.dp).height(40.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun LanguagesStep(
    sourceCode: String,
    targetCode: String,
    targetOptions: List<Language>,
    uiLanguageCode: String,
    onPickSource: (String) -> Unit,
    onPickTarget: (String) -> Unit,
    onSwap: () -> Unit,
    onPickUiLanguage: () -> Unit
) {
    val t = LocalUiStrings.current
    val sourceOptions = remember {
        SherpaModelCatalog.LANGUAGES.map { (code, name) -> Language(code, name) }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t["Spoken language"], style = MaterialTheme.typography.labelMedium)
            SetupLanguageField(sourceCode, sourceOptions) { onPickSource(it.code) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t["Translate to"], style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onSwap) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t["Swap"])
                }
            }
            SetupLanguageField(targetCode, targetOptions) { onPickTarget(it.code) }

            Spacer(Modifier.height(4.dp))
            Text(t["App language"], style = MaterialTheme.typography.labelMedium)
            OutlinedButton(
                onClick = onPickUiLanguage,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(uiLanguageCode.uppercase(), modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun SetupLanguageField(
    selectedCode: String,
    options: List<Language>,
    onPick: (Language) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code.equals(selectedCode, ignoreCase = true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(enabled = options.isNotEmpty()) { open = true }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = selected?.let { "${it.name}  (${it.code})" } ?: selectedCode.uppercase(),
            style = MaterialTheme.typography.bodyMedium
        )
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    if (open) {
        LanguagePickerDialog(
            options = options,
            selectedCode = selectedCode,
            onDismiss = { open = false },
            onPick = {
                onPick(it)
                open = false
            }
        )
    }
}

@Composable
private fun ModelStep(
    modelName: String?,
    modelSizeMb: Int,
    modelReady: Boolean,
    downloading: Boolean,
    progress: Float,
    error: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit = {}
) {
    val t = LocalUiStrings.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t["Speech model"], style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = t["Captions are recognized on your phone, so the app needs a speech model for your language. One-time download, then it works offline."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                modelReady -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(t["Model installed and ready."], style = MaterialTheme.typography.bodyMedium)
                }
                downloading -> {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.format("Downloading %s… %d%%", modelName ?: "", (progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCancel) {
                            Text(t["Cancel"])
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (error != null) t["Retry download"]
                            else t.format("Download %s (~%d MB)", modelName ?: "", modelSizeMb)
                        )
                    }
                    error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Text(
                text = t["The translation model (~30 MB) downloads by itself the first time you caption."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
