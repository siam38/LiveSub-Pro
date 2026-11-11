package com.charles.livecaptionn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.charles.livecaptionn.service.CaptionForegroundService
import com.charles.livecaptionn.service.MediaProjectionHolder
import com.charles.livecaptionn.settings.AudioSource
import com.charles.livecaptionn.ui.HistoryScreen
import com.charles.livecaptionn.ui.MainScreen
import com.charles.livecaptionn.ui.MainViewModel
import com.charles.livecaptionn.ui.MainViewModelFactory
import com.charles.livecaptionn.ui.OnboardingScreen
import com.charles.livecaptionn.ui.l10n.UiStringsProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var showHistory by mutableStateOf(false)

    private var pendingProjectionSource: AudioSource? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCaptioning()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val source = pendingProjectionSource ?: AudioSource.SYSTEM
        pendingProjectionSource = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionHolder.set(result.resultCode, result.data!!.clone() as Intent)
            startCaptionService(source)
        } else {
            (application as LiveCaptionApp).container.runtimeStore.update {
                it.copy(
                    status = com.charles.livecaptionn.speech.RecognitionStatus.ERROR,
                    lastError = "System-audio capture permission was not granted."
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as LiveCaptionApp
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModelFactory(app.container, application))
            val settings by app.container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val onboardingComplete = settings?.onboardingComplete ?: true
            UiStringsProvider(app.container) {
                com.charles.livecaptionn.ui.theme.LiveSubProTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        if (!onboardingComplete) {
                            OnboardingScreen(
                                viewModel = vm,
                                onRequestAudioPermission = { requestAudioPermission() },
                                onOpenOverlaySettings = { vm.openOverlayPermissionSettings(this@MainActivity) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {

                                if (showHistory) {
                                    HistoryScreen(
                                        historyStore = app.container.transcriptHistory,
                                        onBack = { showHistory = false },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    MainScreen(
                                        viewModel = vm,
                                        onRequestAudioPermission = { requestAudioPermission() },
                                        onStart = { startCaptioning() },
                                        onStop = { stopCaptionService() },
                                        onOpenOverlaySettings = { vm.openOverlayPermissionSettings(this@MainActivity) },
                                        onHistory = { showHistory = true },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestAudioPermission() {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCaptioning() {
        val app = application as LiveCaptionApp
        lifecycleScope.launch {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                app.container.runtimeStore.update {
                    it.copy(
                        status = com.charles.livecaptionn.speech.RecognitionStatus.ERROR,
                        lastError = "Overlay permission is required. Enable it in system settings and try again."
                    )
                }
                return@launch
            }
            val settings = app.container.settingsRepository.settingsFlow.first()
            when (settings.audioSource) {
                AudioSource.SYSTEM -> {
                    val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mpManager.createScreenCaptureIntent(
                            MediaProjectionConfig.createConfigForDefaultDisplay()
                        )
                    } else {
                        mpManager.createScreenCaptureIntent()
                    }
                    pendingProjectionSource = settings.audioSource
                    mediaProjectionLauncher.launch(captureIntent)
                }
                AudioSource.MIC -> {
                    val hasMic = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic) {
                        startCaptionService(AudioSource.MIC)
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
        }
    }

    private fun startCaptionService(audioSource: AudioSource) {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_START

            putExtra(CaptionForegroundService.EXTRA_AUDIO_SOURCE, audioSource.name)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopCaptionService() {
        val intent = Intent(this, CaptionForegroundService::class.java).apply {
            action = CaptionForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}
