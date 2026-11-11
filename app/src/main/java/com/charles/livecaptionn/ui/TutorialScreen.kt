package com.charles.livecaptionn.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.compatibility.CompatibilityTier
import com.charles.livecaptionn.compatibility.DeviceSpecs

// Kept for the device-support banner's external guide link.
const val REMOTE_SETUP_DOC_URL = "https://github.com/siam38/LiveSub-Pro"

private data class GuideStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val points: List<String>,
    val preview: String? = null
)

private val GUIDE_STEPS = listOf(
    GuideStep(
        icon = Icons.Filled.Subtitles,
        title = "Live subtitles, on any app",
        body = "LiveSub Pro listens to speech and shows floating subtitles over videos, dramas and meetings — translated into your language, in real time.",
        points = listOf(
            "Everything runs on your phone. No account, no subscription.",
            "Speech is processed on-device, so your audio stays private."
        ),
        preview = "你好,欢迎来到我的频道 → Hello, welcome to my channel"
    ),
    GuideStep(
        icon = Icons.Filled.Download,
        title = "1 · Get a speech model",
        body = "Open the Models tab and download the model for the language you watch. It downloads once and then works fully offline.",
        points = listOf(
            "Watching Chinese shows? Get “Chinese (2025, Chinese only)”.",
            "Delete a model any time to free storage."
        )
    ),
    GuideStep(
        icon = Icons.Filled.Translate,
        title = "2 · Pick your languages",
        body = "On the Live tab, set the source language (what is spoken) and the target language (what you read). The default is Chinese → English.",
        points = listOf(
            "Want both lines? Turn on “Show original” in the Overlay tab for bilingual subtitles.",
            "Translation models download once, then work offline too."
        )
    ),
    GuideStep(
        icon = Icons.Filled.GraphicEq,
        title = "3 · Choose System Audio",
        body = "System Audio captures the sound of the video or app you are watching. When Android asks, choose “Share entire screen” so the app can hear it.",
        points = listOf(
            "Use Microphone instead for real-life conversations around you.",
            "Some apps block audio capture — then Microphone mode is the fallback."
        )
    ),
    GuideStep(
        icon = Icons.Filled.PlayArrow,
        title = "4 · Press Start",
        body = "Tap Start on the Live tab. The floating subtitle box appears on top of other apps and follows you anywhere.",
        points = listOf(
            "Drag the box to move it. Use its buttons to pause or stop.",
            "Subtitles appear as full sentences — no word-by-word flicker."
        ),
        preview = "The subtitles float right over your video."
    ),
    GuideStep(
        icon = Icons.Filled.Tune,
        title = "5 · Make it yours",
        body = "Open the Overlay tab to change subtitle size, opacity, theme and font. The Tools tab keeps your history and exports.",
        points = listOf(
            "History saves past subtitles — export them as text or SRT.",
            "The Dashboard on the Live tab shows RAM use and subtitle latency live."
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(
    deviceSpecs: DeviceSpecs?,
    onDismiss: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = GUIDE_STEPS[stepIndex]
    val isLast = stepIndex == GUIDE_STEPS.lastIndex

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "How LiveSub Pro works",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Step ${stepIndex + 1} of ${GUIDE_STEPS.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close guide")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / GUIDE_STEPS.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                step.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    step.points.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (step.preview != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = step.preview,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (isLast && deviceSpecs != null) {
                DeviceStatusCard(deviceSpecs)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (stepIndex > 0) stepIndex-- },
                    enabled = stepIndex > 0,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Back")
                }
                if (!isLast) {
                    Button(
                        onClick = { stepIndex++ },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Done")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusCard(specs: DeviceSpecs) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (specs.tier) {
                CompatibilityTier.PASSED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                CompatibilityTier.BORDERLINE -> Color(0xFFFFF8E1)
                CompatibilityTier.UNSUPPORTED -> Color(0xFFFFEBEE)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (specs.tier) {
                    CompatibilityTier.PASSED -> Icons.Filled.CheckCircle
                    CompatibilityTier.BORDERLINE -> Icons.Filled.Memory
                    CompatibilityTier.UNSUPPORTED -> Icons.Filled.Error
                },
                contentDescription = null,
                tint = when (specs.tier) {
                    CompatibilityTier.PASSED -> MaterialTheme.colorScheme.tertiary
                    CompatibilityTier.BORDERLINE -> Color(0xFFE65100)
                    CompatibilityTier.UNSUPPORTED -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Your device: ${specs.deviceModel}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${specs.tier.label} · ${String.format("%.1f", specs.totalRamMb / 1024f)} GB RAM · ${specs.cpuCores} cores",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
