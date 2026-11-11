package com.charles.livecaptionn.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.compatibility.CompatibilityTier
import com.charles.livecaptionn.compatibility.DeviceSpecs
import com.charles.livecaptionn.overlay.OverlayFontCatalog
import com.charles.livecaptionn.overlay.OverlayThemeCatalog
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.settings.Language
import com.charles.livecaptionn.settings.SttBackend
import com.charles.livecaptionn.settings.TranslationBackend
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.speech.SherpaModel
import com.charles.livecaptionn.speech.SherpaModelCatalog
import com.charles.livecaptionn.speech.SherpaModelState
import com.charles.livecaptionn.translation.MlKitLanguages
import com.charles.livecaptionn.ui.feedback.FeedbackViewModel
import com.charles.livecaptionn.ui.feedback.IssueDetailsDialog
import com.charles.livecaptionn.ui.feedback.ReportProblemDialog
import com.charles.livecaptionn.ui.feedback.SubmitSuccessSnackbar
import com.charles.livecaptionn.ui.feedback.SupportAndFeedbackCard
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import com.charles.livecaptionn.ui.l10n.UiLanguagePickerDialog
import com.charles.livecaptionn.ui.l10n.UiLocalizationRepository.UiLocalizationStage
import com.charles.livecaptionn.update.UpdateInfo

private enum class StudioTab(
    val titleKey: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    STUDIO("Live", Icons.Filled.GraphicEq),
    STYLE("Overlay", Icons.Filled.Subtitles),
    ENGINES("Models", Icons.Filled.Memory),
    TOOLS("Tools", Icons.Filled.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val t = LocalUiStrings.current
    var selectedTabOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = StudioTab.entries.getOrElse(selectedTabOrdinal) { StudioTab.STUDIO }

    var translateUrlDraft by remember(ui.settings.serverBaseUrl) { mutableStateOf(ui.settings.serverBaseUrl) }
    var sttUrlDraft by remember(ui.settings.sttBaseUrl) { mutableStateOf(ui.settings.sttBaseUrl) }
    var showModelSheet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }

    val feedbackCtx = LocalContext.current
    val feedbackApp = feedbackCtx.applicationContext as com.charles.livecaptionn.LiveCaptionApp
    val feedbackVm: FeedbackViewModel = viewModel(
        factory = FeedbackViewModel.Factory(feedbackCtx, feedbackApp.container.bugReportRepo)
    )
    val feedbackState by feedbackVm.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (ui.showTutorial) {
        TutorialScreen(
            deviceSpecs = ui.deviceSpecs,
            onDismiss = { viewModel.closeTutorial() }
        )
        return
    }

    val modelReady = when (ui.settings.sttBackend) {
        SttBackend.LOCAL_SHERPA -> ui.sherpaActiveModel != null
        SttBackend.REMOTE_WHISPER -> true
    }

    // First-run compatibility gate: shown once, before anything else.
    val gateSpecs = ui.deviceSpecs
    if (gateSpecs != null && !ui.settings.compatibilityGateDone) {
        CompatibilityGateDialog(
            specs = gateSpecs,
            onConfirm = { proceedAnyway -> viewModel.acknowledgeCompatibility(proceedAnyway) }
        )
    }

    // If a permission that was granted gets removed later, warn with a popup.
    var prevAllGranted by remember { mutableStateOf<Boolean?>(null) }
    var showPermissionRevoked by remember { mutableStateOf(false) }
    LaunchedEffect(ui.micPermissionGranted, ui.overlayPermissionGranted) {
        val all = ui.micPermissionGranted && ui.overlayPermissionGranted
        if (prevAllGranted == true && !all) showPermissionRevoked = true
        prevAllGranted = all
    }
    if (showPermissionRevoked) {
        PermissionRevokedDialog(
            ui = ui,
            onRequestAudioPermission = onRequestAudioPermission,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onDismiss = { showPermissionRevoked = false }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = t["LiveSub Pro"],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (ui.runtime.running) {
                                    "${t["Live"]} · ${ui.settings.sourceLanguageCode.uppercase()} → ${ui.settings.targetLanguageCode.uppercase()}"
                                } else {
                                    "${ui.settings.sourceLanguageCode.uppercase()} → ${ui.settings.targetLanguageCode.uppercase()}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ui.runtime.running) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openTutorial() }) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = "Tutorial & Guide")
                    }
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Filled.History, contentDescription = t["History"])
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                StudioTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabOrdinal == index,
                        onClick = { selectedTabOrdinal = index },
                        icon = { Icon(tab.icon, contentDescription = t[tab.titleKey]) },
                        label = { Text(t[tab.titleKey], maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val updateCtx = LocalContext.current
            ui.availableUpdate?.let { info ->
                UpdateAvailableBanner(
                    info = info,
                    fromPlayStore = ui.installedFromPlayStore,
                    onDownload = { viewModel.openUpdateUrl(updateCtx, info) },
                    onDismiss = { viewModel.dismissUpdate() }
                )
            }

            val specs = ui.deviceSpecs
            val setupContext = LocalContext.current
            val openWebDoc: () -> Unit = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(REMOTE_SETUP_DOC_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                setupContext.startActivity(intent)
            }

            if (specs != null) {
                if (specs.tier == CompatibilityTier.UNSUPPORTED) {
                    UnsupportedDeviceBanner(
                        specs = specs,
                        onWatchTutorial = { viewModel.openTutorial() },
                        onOpenDoc = openWebDoc
                    )
                } else if (specs.tier == CompatibilityTier.BORDERLINE && !ui.settings.borderlineWarningDismissed) {
                    BorderlineDeviceBanner(
                        specs = specs,
                        onProceedAnyway = { viewModel.dismissBorderlineWarning() },
                        onWatchTutorial = { viewModel.openTutorial() }
                    )
                }
            }

            when (selectedTab) {
                StudioTab.STUDIO -> {
                    CaptionControlCard(
                        ui = ui,
                        onStart = onStart,
                        onStop = onStop,
                        onRequestAudioPermission = onRequestAudioPermission,
                        onOpenOverlaySettings = onOpenOverlaySettings,
                        onDownloadModel = { ui.sherpaNeededModel?.let { viewModel.downloadSherpaModel(it) } },
                        onCancelDownload = { ui.sherpaNeededModel?.let { viewModel.cancelSherpaModelDownload(it) } },
                        onManageModels = { showModelSheet = true }
                    )
                    LanguageCard(
                        ui = ui,
                        viewModel = viewModel,
                        onManageModels = { showModelSheet = true }
                    )
                    AudioSourceCard(
                        ui = ui,
                        viewModel = viewModel,
                        onManageModels = { showModelSheet = true }
                    )
                    DashboardCard(ui)
                }

                StudioTab.STYLE -> {
                    LiveOverlayPreviewCard(ui = ui)
                    OverlaySettingsCard(
                        ui = ui,
                        viewModel = viewModel
                    )
                }

                StudioTab.ENGINES -> {
                    AudioSourceCard(
                        ui = ui,
                        viewModel = viewModel,
                        onManageModels = { showModelSheet = true }
                    )
                    SherpaModelLibraryCard(
                        states = ui.sherpaModels,
                        activeModelId = ui.sherpaActiveModel?.id,
                        neededModelId = ui.sherpaNeededModel?.id,
                        onDownload = viewModel::downloadSherpaModel,
                        onCancel = viewModel::cancelSherpaModelDownload,
                        onDelete = viewModel::deleteSherpaModel
                    )
                    ServerCard(
                        ui = ui,
                        translateUrl = translateUrlDraft,
                        onTranslateUrlChange = { translateUrlDraft = it },
                        onSaveTranslateUrl = { viewModel.updateBaseUrl(translateUrlDraft) },
                        onRefreshLibre = { viewModel.refreshLibreCatalog() },
                        onSelectTranslationBackend = viewModel::updateTranslationBackend,
                        sttUrl = sttUrlDraft,
                        onSttUrlChange = { sttUrlDraft = it },
                        onSaveSttUrl = { viewModel.updateSttUrl(sttUrlDraft) },
                        showStt = ui.settings.audioSource == AudioSource.SYSTEM &&
                            ui.settings.sttBackend == SttBackend.REMOTE_WHISPER
                    )
                }

                StudioTab.TOOLS -> {
                    FeatureToolsCard(
                        settings = ui.settings,
                        profiles = feedbackApp.container.captionProfiles,
                        glossary = feedbackApp.container.glossary,
                        micPermissionGranted = ui.micPermissionGranted,
                        overlayPermissionGranted = ui.overlayPermissionGranted,
                        modelReady = modelReady,
                        onApplyProfile = viewModel::applyProfile,
                        onSaveHistoryChange = viewModel::updateSaveHistory
                    )
                    UiLanguageCard(
                        languageCode = ui.uiLanguageCode,
                        busy = ui.uiLocalizationBusy,
                        error = ui.uiLocalizationError,
                        stage = ui.uiLocalizationStage,
                        translatedCount = ui.uiLocalizationTranslated,
                        translatedTotal = ui.uiLocalizationTotal,
                        onSelect = viewModel::updateUiLanguage
                    )
                    DeviceCompatibilityCard(
                        specs = ui.deviceSpecs,
                        onWatchTutorial = { viewModel.openTutorial() },
                        onSimulateTier = { viewModel.setSimulatedTier(it) }
                    )
                    SupportAndFeedbackCard(
                        state = feedbackState,
                        onReportProblem = { feedbackVm.showReportDialog() },
                        onOpenReport = { feedbackVm.openIssueDetails(it) }
                    )
                }
            }

            if (feedbackState.submitSuccess) {
                SubmitSuccessSnackbar(
                    message = t["Report submitted successfully!"],
                    onDismiss = { feedbackVm.clearSubmitSuccess() }
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        if (showModelSheet) {
            SherpaModelSheet(
                states = ui.sherpaModels,
                activeModelId = ui.sherpaActiveModel?.id,
                onDismiss = { showModelSheet = false },
                onDownload = viewModel::downloadSherpaModel,
                onCancel = viewModel::cancelSherpaModelDownload,
                onDelete = viewModel::deleteSherpaModel
            )
        }

        if (ui.showUnsupportedModal) {
            UnsupportedDeviceDialog(
                specs = ui.deviceSpecs,
                onDismiss = { viewModel.dismissUnsupportedModal() },
                onWatchTutorial = {
                    viewModel.dismissUnsupportedModal()
                    viewModel.openTutorial()
                }
            )
        }

        if (feedbackState.showReportDialog) {
            ReportProblemDialog(
                state = feedbackState,
                onDismiss = { feedbackVm.hideReportDialog() },
                onTitleChange = { feedbackVm.updateReportTitle(it) },
                onDescriptionChange = { feedbackVm.updateReportDescription(it) },
                onNameChange = { feedbackVm.updateReporterName(it) },
                onEmailChange = { feedbackVm.updateReporterEmail(it) },
                onIncludeDiagnosticsChange = { feedbackVm.updateIncludeDiagnostics(it) },
                onAttachmentSelected = { feedbackVm.updateAttachmentUri(it) },
                onClearAttachment = { feedbackVm.clearAttachment() },
                onSubmit = { feedbackVm.submitReport() }
            )
        }

        if (feedbackState.showIssueDetails) {
            IssueDetailsDialog(
                state = feedbackState,
                onDismiss = { feedbackVm.closeIssueDetails() },
                onReplyTextChange = { feedbackVm.updateReplyText(it) },
                onReplyAttachmentSelected = { feedbackVm.updateReplyAttachmentUri(it) },
                onClearReplyAttachment = { feedbackVm.clearReplyAttachment() },
                onPostReply = { feedbackVm.postReply() }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CaptionControlCard(
    ui: MainUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onDownloadModel: () -> Unit,
    onCancelDownload: () -> Unit,
    onManageModels: () -> Unit
) {
    val isRunning = ui.runtime.running
    val t = LocalUiStrings.current

    val needsMic = ui.settings.audioSource == AudioSource.MIC && !ui.micPermissionGranted
    val needsOverlay = !ui.overlayPermissionGranted
    val needsModel = ui.settings.sttBackend == SttBackend.LOCAL_SHERPA && ui.sherpaActiveModel == null
    val canStart = !needsMic && !needsOverlay && !needsModel
    val neededModel = ui.sherpaNeededModel
    val neededState = neededModel?.let { ui.sherpaModels[it.id] }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isRunning) 1.5.dp else 1.dp,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            val statusColor = when {
                isRunning -> MaterialTheme.colorScheme.tertiary
                ui.runtime.status == RecognitionStatus.ERROR -> MaterialTheme.colorScheme.error
                canStart -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.secondary
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            isRunning -> t["Live"] + " · " + t[ui.runtime.status.displayName]
                            ui.runtime.status == RecognitionStatus.ERROR -> t["Couldn't start"]
                            canStart -> t["Ready"]
                            else -> t["Setup needed before captioning"]
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = if (ui.settings.audioSource == AudioSource.SYSTEM) t["System Audio"] else t["Microphone"],
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val err = ui.runtime.lastError?.trim().orEmpty()
            if (err.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (isRunning) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (ui.runtime.originalText.isNotBlank()) {
                            Text(
                                text = ui.runtime.originalText,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        val liveTranslated = ui.runtime.translatedText
                            .ifBlank { ui.runtime.previewTranslatedText }
                            .ifBlank { ui.runtime.originalText }
                            .ifBlank { "…" }
                        Text(
                            text = liveTranslated,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isRunning && canStart,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Start"], fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t["Stop"], fontWeight = FontWeight.Bold)
                }
            }

            if (!isRunning && !canStart) {
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (needsOverlay) {
                        BlockerRow(t["Overlay permission is off"], t["Open settings"], onOpenOverlaySettings)
                    }
                    if (needsMic) {
                        BlockerRow(t["Microphone permission is off"], t["Grant"], onRequestAudioPermission)
                    }
                    if (needsModel) {
                        when {
                            neededState?.downloading == true -> {
                                LinearProgressIndicator(
                                    progress = { neededState.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = t.format(
                                            "Downloading %s model… %d%%",
                                            neededModel?.displayName ?: "",
                                            (neededState.progress * 100).toInt()
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onCancelDownload) {
                                        Text(t["Cancel"])
                                    }
                                }
                            }
                            neededModel != null -> {
                                val langName = SherpaModelCatalog.LANGUAGES
                                    .firstOrNull { it.first == ui.settings.sourceLanguageCode }?.second
                                    ?: ui.settings.sourceLanguageCode.uppercase()
                                BlockerRow(
                                    t.format("Speech model for %s is not downloaded", langName),
                                    t.format("Download · ~%d MB", neededModel.totalSizeMb),
                                    onDownloadModel
                                )
                                neededState?.error?.let { modelError ->
                                    Text(
                                        text = modelError,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            else -> BlockerRow(
                                t["No on-device model for this language"],
                                t["All models"],
                                onManageModels
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockerRow(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Filled.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onAction,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.defaultMinSize(minWidth = 136.dp, minHeight = 38.dp)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun DashboardCard(ui: MainUiState) {
    val context = LocalContext.current
    var appRamMb by remember { mutableStateOf(0L) }
    var deviceFreeMb by remember { mutableStateOf(0L) }
    var deviceTotalMb by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        while (true) {
            val memInfo = android.os.Debug.MemoryInfo()
            android.os.Debug.getMemoryInfo(memInfo)
            appRamMb = memInfo.totalPss / 1024L
            if (am != null) {
                val info = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(info)
                deviceTotalMb = info.totalMem / (1024 * 1024)
                deviceFreeMb = info.availMem / (1024 * 1024)
            }
            delay(2000)
        }
    }
    val specs = ui.deviceSpecs
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                SectionLabel("Dashboard")
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (ui.runtime.running) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (ui.runtime.running) "LIVE" else "IDLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (ui.runtime.running) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardTile(
                    label = "App RAM",
                    value = if (appRamMb > 0) "$appRamMb MB" else "—",
                    modifier = Modifier.weight(1f)
                )
                DashboardTile(
                    label = "Device RAM free",
                    value = if (deviceTotalMb > 0) "${formatGb(deviceFreeMb)} / ${formatGb(deviceTotalMb)}" else "—",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardTile(
                    label = "Subtitle latency",
                    value = ui.runtime.lastTranslationMs?.let { "$it ms" } ?: "—",
                    modifier = Modifier.weight(1f)
                )
                DashboardTile(
                    label = "Device support",
                    value = specs?.tier?.label ?: "Checking…",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = buildString {
                    append(if (ui.settings.sttBackend == SttBackend.LOCAL_SHERPA) "Sherpa on-device" else "Remote Whisper")
                    append(" · ")
                    append(if (ui.settings.audioSource == AudioSource.SYSTEM) "System Audio" else "Microphone")
                    ui.sherpaActiveModel?.let { append(" · "); append(it.displayName) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatGb(mb: Long): String = String.format("%.1f GB", mb / 1024f)

@Composable
private fun PermissionRevokedDialog(
    ui: MainUiState,
    onRequestAudioPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val missing = buildList {
        if (!ui.micPermissionGranted) add("Microphone")
        if (!ui.overlayPermissionGranted) add("Display over other apps (Overlay)")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("Permission removed") },
        text = {
            Text(
                "A permission LiveSub Pro needs was turned off: ${missing.joinToString(", ")}. " +
                    "Subtitles will not start until it is granted again."
            )
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                if (!ui.overlayPermissionGranted) onOpenOverlaySettings() else onRequestAudioPermission()
            }) {
                Text("Grant again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

@Composable
private fun CompatibilityGateDialog(
    specs: DeviceSpecs,
    onConfirm: (proceedAnyway: Boolean) -> Unit
) {
    val supported = specs.tier == CompatibilityTier.PASSED
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                if (supported) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (supported) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(if (supported) "Your device is supported" else "Device compatibility check")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${specs.deviceModel} · ${specs.androidVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "RAM: ${formatGb(specs.totalRamMb)} · CPU: ${specs.cpuCores} cores · " +
                        "${if (specs.is64Bit) "64-bit" else "32-bit"} · Free storage: ${formatGb(specs.freeStorageMb)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Support level: ${specs.tier.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (specs.tier) {
                        CompatibilityTier.PASSED -> MaterialTheme.colorScheme.tertiary
                        CompatibilityTier.BORDERLINE -> Color(0xFFE65100)
                        CompatibilityTier.UNSUPPORTED -> MaterialTheme.colorScheme.error
                    }
                )
                specs.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!supported) {
                    Text(
                        text = "On-device subtitles may be slow or unavailable on this device. You can proceed anyway — remote mode stays available.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(!supported) }) {
                Text(if (supported) "Start using" else "Proceed anyway")
            }
        }
    )
}

@Composable
private fun AudioSourceCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    onManageModels: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Audio Source"])

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = t["Microphone"],
                    icon = Icons.Filled.Mic,
                    selected = ui.settings.audioSource == AudioSource.MIC,
                    onClick = { viewModel.updateAudioSource(AudioSource.MIC) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = t["System Audio"],
                    icon = Icons.Filled.SurroundSound,
                    selected = ui.settings.audioSource == AudioSource.SYSTEM,
                    onClick = { viewModel.updateAudioSource(AudioSource.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }

            val isSystem = ui.settings.audioSource == AudioSource.SYSTEM
            Text(
                text = if (isSystem)
                    t["Captures audio from videos and apps that allow playback capture. If Android asks, choose Share entire screen."]
                else
                    t["Uses the device microphone."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(t["Transcription engine"], style = MaterialTheme.typography.labelMedium)
            val isSttUnsupported = ui.deviceSpecs?.tier == CompatibilityTier.UNSUPPORTED && !ui.settings.compatibilityOverride
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = if (isSttUnsupported) t["Sherpa"] + " 🔒" else t["Sherpa"],
                    icon = if (isSttUnsupported) Icons.Filled.Lock else Icons.Filled.Bolt,
                    selected = ui.settings.sttBackend == SttBackend.LOCAL_SHERPA,
                    onClick = { viewModel.updateSttBackend(SttBackend.LOCAL_SHERPA) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = if (isSystem) t["Remote"] else t["Built-in"],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.sttBackend == SttBackend.REMOTE_WHISPER,
                    onClick = { viewModel.updateSttBackend(SttBackend.REMOTE_WHISPER) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (ui.settings.sttBackend == SttBackend.LOCAL_SHERPA) {
                val active = ui.sherpaActiveModel
                val needed = ui.sherpaNeededModel
                val neededState = needed?.let { ui.sherpaModels[it.id] }
                when {
                    active != null -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t.format("Model ready: %s", active.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onManageModels) {
                            Text(t["All speech models"])
                        }
                    }
                    neededState?.downloading == true -> {
                        LinearProgressIndicator(
                            progress = { neededState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = t.format("Downloading %s model… %d%%", needed?.displayName ?: "", (neededState.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { needed?.let { viewModel.cancelSherpaModelDownload(it) } }) {
                                Text(t["Cancel"])
                            }
                        }
                    }
                    needed != null -> {
                        Text(
                            text = t.format("This language uses the %s model.", needed.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.downloadSherpaModel(needed) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t.format("Download model (~%d MB)", needed.totalSizeMb))
                            }
                            TextButton(onClick = onManageModels) {
                                Text(t["All speech models"])
                            }
                        }
                        neededState?.error?.let { modelError ->
                            Text(
                                text = modelError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = t["No on-device model for this language yet."],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = onManageModels) {
                            Text(t["All speech models"])
                        }
                    }
                }
            } else {
                Text(
                    text = if (isSystem)
                        t["Sends captured audio to the configured Whisper ASR endpoint."]
                    else
                        t["Uses Android's built-in recognizer. Available locales depend on what's installed on this device."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, border, RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun LanguageCard(
    ui: MainUiState,
    viewModel: MainViewModel,
    onManageModels: () -> Unit
) {
    val sourceOptions = ui.availableSourceLanguages
    val targetOptions = ui.availableTargetLanguages
    val t = LocalUiStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Languages"])

            Text(t["Source (spoken)"], style = MaterialTheme.typography.labelMedium)
            LanguagePickerField(
                selectedCode = ui.settings.sourceLanguageCode,
                options = sourceOptions,
                placeholder = if (sourceOptions.isEmpty()) t["No languages available"] else t["Pick a language"],
                onPick = { viewModel.updateSource(it.code) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(t["Target (translation)"], style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = viewModel::swapLanguages) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t["Swap"])
                }
            }
            LanguagePickerField(
                selectedCode = ui.settings.targetLanguageCode,
                options = targetOptions,
                placeholder = if (targetOptions.isEmpty()) t["No languages available"] else t["Pick a language"],
                onPick = { viewModel.updateTarget(it.code) }
            )

            val vmStt = ui.settings.sttBackend
            val usingMlKit = ui.settings.translationBackend == TranslationBackend.ML_KIT
            when {
                vmStt == SttBackend.LOCAL_SHERPA -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = t["On-device: the source list only shows languages with a Sherpa speech model."],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onManageModels) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t["Speech models"])
                        }
                    }
                }
                usingMlKit -> {
                    Text(
                        text = t.format(
                            "On-device translation via Google ML Kit — %d languages, fully offline after first download.",
                            MlKitLanguages.LIST.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ui.libreLanguages.isEmpty() && ui.libreError != null -> {
                    Text(
                        text = t.format(
                            "Could not reach LibreTranslate at %s — showing a fallback list. Save a valid URL to load the full language set from your server.",
                            ui.settings.serverBaseUrl
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ui.libreLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = t["Loading languages from LibreTranslate…"],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ui.libreLanguages.isNotEmpty() -> {
                    Text(
                        text = t.format(
                            "%d languages reported by LibreTranslate. Add more to your server by installing extra Argos Translate packages.",
                            ui.libreLanguages.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (ui.settings.sttBackend != SttBackend.LOCAL_SHERPA) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(t["Auto-detect source"], style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = ui.settings.autoDetectSource,
                        onCheckedChange = viewModel::updateAutoDetect
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerField(
    selectedCode: String,
    options: List<Language>,
    placeholder: String,
    onPick: (Language) -> Unit,
    requiresPro: (Language) -> Boolean = { false },
    onRequiresPro: () -> Unit = {}
) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.code.equals(selectedCode, ignoreCase = true) }
    val label = selected?.let { "${it.name}  (${it.code})" }
        ?: selectedCode.takeIf { it.isNotBlank() }?.uppercase()
        ?: placeholder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(enabled = options.isNotEmpty()) { open = true }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (options.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
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
            },
            requiresPro = requiresPro,
            onRequiresPro = {
                open = false
                onRequiresPro()
            }
        )
    }
}

@Composable
internal fun LanguagePickerDialog(
    options: List<Language>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onPick: (Language) -> Unit,
    requiresPro: (Language) -> Boolean = { false },
    onRequiresPro: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val t = LocalUiStrings.current
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.name.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(t["Close"]) } },
        title = { Text(t["Choose language"]) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(t["Search"]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filtered.forEach { lang ->
                        val isSelected = lang.code.equals(selectedCode, ignoreCase = true)
                        val locked = requiresPro(lang)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (locked) onRequiresPro() else onPick(lang) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                locked -> Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = t["Requires Pro"],
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                isSelected -> Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                else -> Spacer(Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    lang.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (locked) "${lang.code} · ${t["Pro"]}" else lang.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            text = t.format("No languages match \"%s\".", query),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SherpaModelLibraryCard(
    states: Map<String, SherpaModelState>,
    activeModelId: String?,
    neededModelId: String?,
    onDownload: (SherpaModel) -> Unit,
    onCancel: (SherpaModel) -> Unit,
    onDelete: (SherpaModel) -> Unit
) {
    val t = LocalUiStrings.current
    val installedCount = SherpaModelCatalog.ALL.count { states[it.id]?.installed == true }
    val installedMb = SherpaModelCatalog.ALL
        .filter { states[it.id]?.installed == true }
        .sumOf { it.totalSizeMb }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(t["Speech models"])
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$installedCount installed · ~${formatModelSize(installedMb)}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = t["Sherpa models run fully offline on this phone. One download per language — delete any time to free storage."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SherpaModelCatalog.ALL.forEach { model ->
                SherpaModelRow(
                    model = model,
                    state = states[model.id] ?: SherpaModelState(),
                    isActive = model.id == activeModelId,
                    isRecommended = model.id == neededModelId,
                    onDownload = { onDownload(model) },
                    onCancel = { onCancel(model) },
                    onDelete = { onDelete(model) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SherpaModelSheet(
    states: Map<String, SherpaModelState>,
    activeModelId: String? = null,
    onDismiss: () -> Unit,
    onDownload: (SherpaModel) -> Unit,
    onCancel: (SherpaModel) -> Unit = {},
    onDelete: (SherpaModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val t = LocalUiStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(t["Speech models"], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = t["Sherpa models run fully offline on this phone. One download per language — delete any time to free storage."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SherpaModelCatalog.ALL.forEach { model ->
                SherpaModelRow(
                    model = model,
                    state = states[model.id] ?: SherpaModelState(),
                    isActive = model.id == activeModelId,
                    isRecommended = false,
                    onDownload = { onDownload(model) },
                    onCancel = { onCancel(model) },
                    onDelete = { onDelete(model) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SherpaModelRow(
    model: SherpaModel,
    state: SherpaModelState,
    isActive: Boolean = false,
    isRecommended: Boolean = false,
    onDownload: () -> Unit,
    onCancel: () -> Unit = {},
    onDelete: () -> Unit
) {
    val t = LocalUiStrings.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                state.installed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = if (isActive || isRecommended) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        } else null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "~${formatModelSize(model.totalSizeMb)} · ${model.languages.joinToString(", ") { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (model.description.isNotBlank()) {
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                when {
                    state.downloading -> TextButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t["Cancel"])
                    }
                    state.installed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = t["Installed"],
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = t["Delete"])
                        }
                    }
                    else -> FilledTonalButton(onClick = onDownload, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.error != null) t["Retry"] else t["Get"])
                    }
                }
            }
            if (state.downloading) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                val downloadedMb = (model.totalSizeMb * state.progress).toInt()
                Text(
                    text = "${(state.progress * 100).toInt()}% ($downloadedMb / ${model.totalSizeMb} MB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!state.downloading && state.error != null && !state.installed) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(t["Delete"]) },
            text = {
                Text("Remove ${model.displayName} (~${formatModelSize(model.totalSizeMb)}) from device storage?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text(t["Delete"], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(t["Cancel"])
                }
            }
        )
    }
}

private fun formatModelSize(sizeMb: Int): String {
    if (sizeMb >= 1024) {
        val gb = sizeMb / 1024f
        return if (gb >= 10f) "${gb.toInt()} GB" else String.format("%.1f GB", gb)
    }
    return "$sizeMb MB"
}

@Composable
private fun LiveOverlayPreviewCard(ui: MainUiState) {
    val t = LocalUiStrings.current
    val theme = OverlayThemeCatalog.find(ui.settings.overlayThemeId)
    val bgColor = Color(
        red = android.graphics.Color.red(theme.backgroundRgb) / 255f,
        green = android.graphics.Color.green(theme.backgroundRgb) / 255f,
        blue = android.graphics.Color.blue(theme.backgroundRgb) / 255f,
        alpha = ui.settings.overlayOpacity.coerceIn(0.45f, 1f)
    )
    val textColor = Color(
        red = android.graphics.Color.red(theme.textRgb) / 255f,
        green = android.graphics.Color.green(theme.textRgb) / 255f,
        blue = android.graphics.Color.blue(theme.textRgb) / 255f,
        alpha = 1f
    )
    val fontFamily = when (ui.settings.overlayFontId) {
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Live Subtitle Preview")
                Text(
                    text = "${theme.label} · ${ui.settings.textSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ui.settings.showOriginal) {
                        Text(
                            text = ui.runtime.originalText.ifBlank { "Original spoken phrase appears here" },
                            color = textColor.copy(alpha = 0.80f),
                            fontSize = (ui.settings.textSizeSp * 0.78f).coerceIn(12f, 20f).sp,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = ui.runtime.translatedText.ifBlank {
                            t["Live captions & translation on your device"]
                        },
                        color = textColor,
                        fontSize = ui.settings.textSizeSp.sp,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlaySettingsCard(
    ui: MainUiState,
    viewModel: MainViewModel
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Overlay"])

            Text(t.format("Text size: %dsp", ui.settings.textSizeSp.toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.textSizeSp,
                valueRange = 14f..40f,
                onValueChange = viewModel::updateTextSize
            )

            Text(t.format("Opacity: %d%%", (ui.settings.overlayOpacity * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = ui.settings.overlayOpacity,
                valueRange = 0.2f..1f,
                onValueChange = viewModel::updateOpacity
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t["Show original text"], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Display spoken source language above translated subtitles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ui.settings.showOriginal,
                    onCheckedChange = viewModel::updateShowOriginal
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(t["Overlay theme"], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayThemeCatalog.THEMES.forEach { theme ->
                    ChoiceChip(
                        label = theme.label,
                        icon = Icons.Filled.Palette,
                        selected = ui.settings.overlayThemeId == theme.id,
                        onClick = { viewModel.updateOverlayTheme(theme.id) }
                    )
                }
            }

            Text(t["Overlay font"], style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayFontCatalog.FONTS.forEach { font ->
                    ChoiceChip(
                        label = font.label,
                        icon = Icons.Filled.TextFields,
                        selected = ui.settings.overlayFontId == font.id,
                        onClick = { viewModel.updateOverlayFont(font.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    ui: MainUiState,
    translateUrl: String,
    onTranslateUrlChange: (String) -> Unit,
    onSaveTranslateUrl: () -> Unit,
    onRefreshLibre: () -> Unit,
    onSelectTranslationBackend: (TranslationBackend) -> Unit,
    sttUrl: String,
    onSttUrlChange: (String) -> Unit,
    onSaveSttUrl: () -> Unit,
    showStt: Boolean
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                SectionLabel(t["Translation engine"])
            }

            val isTranslateUnsupported = ui.deviceSpecs?.tier == CompatibilityTier.UNSUPPORTED && !ui.settings.compatibilityOverride
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChip(
                    label = if (isTranslateUnsupported) t["On-device (ML Kit)"] + " 🔒" else t["On-device (ML Kit)"],
                    icon = if (isTranslateUnsupported) Icons.Filled.Lock else Icons.Filled.Bolt,
                    selected = ui.settings.translationBackend == TranslationBackend.ML_KIT,
                    onClick = { onSelectTranslationBackend(TranslationBackend.ML_KIT) },
                    modifier = Modifier.weight(1f)
                )
                ChoiceChip(
                    label = t["LibreTranslate"],
                    icon = Icons.Filled.Cloud,
                    selected = ui.settings.translationBackend == TranslationBackend.LIBRE_TRANSLATE,
                    onClick = { onSelectTranslationBackend(TranslationBackend.LIBRE_TRANSLATE) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (ui.settings.translationBackend == TranslationBackend.ML_KIT) {
                Text(
                    text = t["Google ML Kit translates fully on this device — no server required. The first time you use a language pair, a ~30 MB model downloads and is then cached offline forever."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = translateUrl,
                    onValueChange = onTranslateUrlChange,
                    label = { Text(t["LibreTranslate URL"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = t.format("Example: %s", "http://<your-pc-ip>:5000") +
                        " The app fetches /languages to populate the dropdowns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onSaveTranslateUrl,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(t["Save"]) }
                    OutlinedButton(
                        onClick = onRefreshLibre,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t["Refresh"])
                    }
                }
                if (ui.libreError != null && !ui.libreLoading) {
                    Text(
                        text = t.format("Language fetch error: %s", ui.libreError),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (showStt) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = sttUrl,
                    onValueChange = onSttUrlChange,
                    label = { Text(t["Speech-to-Text URL (Whisper)"]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = t.format("Example: %s", "http://<your-pc-ip>:9000/asr?output=json"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onSaveSttUrl,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t["Save STT URL"]) }
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(
    info: UpdateInfo,
    fromPlayStore: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val t = LocalUiStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = t["Update available"],
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = info.releaseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = info.notes.take(240),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 4
                )
            }
            if (fromPlayStore) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = t["You installed this app from the Play Store. The GitHub build is a pre-release and may be unstable. Installing it will also disable Play Store auto-updates for this app."],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDownload,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (info.apkDownloadUrl != null) t["Download APK"] else t["View release"])
                }
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp)
                ) { Text(t["Later"]) }
            }
        }
    }
}

@Composable
private fun UiLanguageCard(
    languageCode: String,
    busy: Boolean,
    error: String?,
    stage: UiLocalizationStage = UiLocalizationStage.DOWNLOADING,
    translatedCount: Int = 0,
    translatedTotal: Int = 0,
    onSelect: (String) -> Unit
) {
    val t = LocalUiStrings.current
    var showPicker by remember { mutableStateOf(false) }
    val selected = MlKitLanguages.LIST.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }
    val label = if (languageCode.equals("en", true)) {
        "${t["English"]} (en)"
    } else {
        "${selected?.name.orEmpty()} ($languageCode)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(t["Interface language"])

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) { showPicker = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = when (stage) {
                        UiLocalizationStage.TRANSLATING ->
                            if (translatedTotal > 0) {
                                t.format("Translating the interface… (%d/%d)", translatedCount, translatedTotal)
                            } else {
                                t["Translating the interface…"]
                            }
                        UiLocalizationStage.DOWNLOADING -> t["Downloading translation model…"]
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let {
                Text(
                    text = t["Translation failed. Tap to retry."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { onSelect(languageCode) }) { Text(t["Retry"]) }
            }
            if (!busy && error == null && !languageCode.equals("en", true)) {
                Text(
                    text = t["On-device ML Kit translation may be inaccurate."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showPicker) {
        UiLanguagePickerDialog(
            currentCode = languageCode,
            busy = busy,
            error = error,
            stage = stage,
            translatedCount = translatedCount,
            translatedTotal = translatedTotal,
            onSelect = {
                showPicker = false
                onSelect(it)
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun UnsupportedDeviceBanner(
    specs: DeviceSpecs,
    onWatchTutorial: () -> Unit,
    onOpenDoc: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Unsupported for On-Device AI",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB71C1C),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = "This device does not meet the minimum hardware requirements (2.5 GB RAM, 4 CPU cores) for on-device Sherpa & ML Kit processing. To prevent crashes, the app has automatically enabled Remote Whisper and LibreTranslate.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF37474F)
            )
            Text(
                text = "Detected: ${specs.totalRamMb} MB RAM • ${specs.cpuCores} cores • ${if (specs.is64Bit) "64-bit" else "32-bit"} • ${specs.freeStorageMb} MB free",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF546E7A)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onWatchTutorial,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Watch Tutorial", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenDoc,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Setup Guide", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BorderlineDeviceBanner(
    specs: DeviceSpecs,
    onProceedAnyway: () -> Unit,
    onWatchTutorial: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Notice: Limited Hardware Resources",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = "Your device (${specs.deviceModel}) meets the minimum requirements, but has limited resources (${specs.totalRamMb} MB RAM, ${specs.cpuCores} cores, ${if (specs.is64Bit) "64-bit" else "32-bit"}). On-device models may experience latency or memory pressure. For best performance, remote Whisper is recommended.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3E2723)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onProceedAnyway,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Proceed Anyway", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onWatchTutorial,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Setup Guide", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun UnsupportedDeviceDialog(
    specs: DeviceSpecs?,
    onDismiss: () -> Unit,
    onWatchTutorial: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
        },
        title = {
            Text("Hardware Requirements Not Met", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "On-device neural speech recognition and translation require at least 2.5 GB RAM and 4 CPU cores to run reliably."
                )
                if (specs != null) {
                    Text(
                        "Device specs:\n• RAM: ${specs.totalRamMb} MB (min 2560 MB)\n• CPU: ${specs.cpuCores} cores (min 4)\n• Architecture: ${if (specs.is64Bit) "64-bit" else "32-bit"}\n• Free storage: ${specs.freeStorageMb} MB",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    "On-device models are locked. Remote Whisper STT and LibreTranslate have been selected automatically so you can use live captions seamlessly."
                )
            }
        },
        confirmButton = {
            Button(onClick = onWatchTutorial) {
                Text("Watch Tutorial")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood")
            }
        }
    )
}

@Composable
private fun DeviceCompatibilityCard(
    specs: DeviceSpecs?,
    onWatchTutorial: () -> Unit,
    onSimulateTier: (CompatibilityTier?) -> Unit
) {
    if (specs == null) return
    var showSimMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("System Compatibility Check")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (specs.tier) {
                                CompatibilityTier.PASSED -> Color(0xFF2E7D32)
                                CompatibilityTier.BORDERLINE -> Color(0xFFE65100)
                                CompatibilityTier.UNSUPPORTED -> Color(0xFFC62828)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = specs.tier.label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "${specs.deviceModel} • ${specs.androidVersion}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("• RAM: ${specs.totalRamMb} MB (min 2.5 GB, 4 GB+ recommended)", style = MaterialTheme.typography.bodySmall)
                Text("• CPU: ${specs.cpuCores} cores (${if (specs.is64Bit) "64-bit" else "32-bit"})", style = MaterialTheme.typography.bodySmall)
                Text("• Free Storage: ${specs.freeStorageMb} MB", style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onWatchTutorial,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tutorial & Guide", fontSize = 12.sp)
                }
                if (BuildConfig.DEBUG) {
                    OutlinedButton(
                        onClick = { showSimMenu = true },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Test Tier...", fontSize = 12.sp)
                    }
                }
            }

            if (showSimMenu) {
                AlertDialog(
                    onDismissRequest = { showSimMenu = false },
                    title = { Text("Simulate Compatibility Tier") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Select a tier to test app behavior and UI adaptation on this device:")
                            OutlinedButton(
                                onClick = { onSimulateTier(CompatibilityTier.PASSED); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Passed (>= 4 GB RAM, 64-bit)") }
                            OutlinedButton(
                                onClick = { onSimulateTier(CompatibilityTier.BORDERLINE); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Borderline (3 GB RAM / 32-bit / 4 cores)") }
                            OutlinedButton(
                                onClick = { onSimulateTier(CompatibilityTier.UNSUPPORTED); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Unsupported (< 2.5 GB RAM / < 4 cores)") }
                            TextButton(
                                onClick = { onSimulateTier(null); showSimMenu = false },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reset to Real Hardware") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showSimMenu = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}
