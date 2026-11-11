package com.charles.livecaptionn.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.ui.l10n.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cinema-grade floating subtitle dock:
 * - Renders stabilized translated subtitles with optional bilingual original line above.
 * - Surfaces live partial speech preview before the first cue finishes translating so
 *   users never stare at a blank `"…"` box during the opening sentence.
 * - Applies full [OverlayTheme] background AND text colors (`textRgb`) with a subtle
 *   glass hairline border for legibility over both bright and dark video content.
 * - Clamps position and width to live screen bounds across orientation changes and
 *   snaps to horizontal screen center when dragged near the center axis.
 */
class OverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val onPauseResume: () -> Unit,
    private val onClose: () -> Unit,
    private val onToggleMinimize: () -> Unit,
    private val uiStrings: () -> UiStrings = { UiStrings.EMPTY }
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var container: LinearLayout? = null
    private var controlsRow: LinearLayout? = null
    private var liveDot: View? = null
    private var langChip: TextView? = null
    private var statusText: TextView? = null
    private var originalText: TextView? = null
    private var translatedText: TextView? = null
    private var pauseButton: ImageButton? = null
    private var minButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var resizeHandle: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var controlsVisible = false
    private var lastRenderKey: String? = null
    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

    fun show(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        if (root != null) return
        showInternal(initialX, initialY, widthDp, heightDp)
    }

    private fun showInternal(initialX: Int, initialY: Int, widthDp: Int, @Suppress("UNUSED_PARAMETER") heightDp: Int) {
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        lastScreenWidth = screenW
        lastScreenHeight = screenH

        val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
        val maxW = (screenW - dp(16)).coerceAtLeast(minW)
        val clampedW = (widthDp * density).roundToInt().coerceIn(minW, maxW)

        // Default position centers horizontally near the lower third (cinema subtitle zone)
        val isDefaultPos = initialX == 60 && initialY == 220
        val rawX = if (isDefaultPos) (screenW - clampedW) / 2 else initialX
        val rawY = if (initialY == 220) (screenH * 0.70f).roundToInt() else initialY
        val clampedX = clampX(rawX, clampedW, screenW)
        val clampedY = clampY(rawY, screenH)

        val p = WindowManager.LayoutParams(
            clampedW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = clampedX
            y = clampedY
        }
        params = p

        val frame = FrameLayout(context)

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(185, 12, 14, 20))
                setStroke(dp(1), Color.argb(55, 255, 255, 255))
            }
            setPadding(dp(18), dp(11), dp(18), dp(13))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setOnTouchListener(DragTouchListener())
            setOnClickListener { setControlsVisible(!controlsVisible) }
        }
        container = box

        // Controls header bar: revealed on tap, auto-fades after 3.8s
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(70, 255, 255, 255))
            }
            setPadding(dp(10), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
        liveDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#69F0AE"))
                setSize(dp(8), dp(8))
            }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(7), 0) }
        }
        langChip = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.06f
            text = ""
        }
        statusText = TextView(context).apply {
            setTextColor(Color.argb(210, 240, 244, 255))
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pauseButton = makeButton(android.R.drawable.ic_media_pause, uiStrings()["Pause captioning"]) {
            scheduleControlsAutoHide()
            onPauseResume()
        }
        minButton = makeButton(android.R.drawable.arrow_down_float, uiStrings()["Minimize overlay"]) {
            scheduleControlsAutoHide()
            onToggleMinimize()
        }
        closeButton = makeButton(android.R.drawable.ic_menu_close_clear_cancel, uiStrings()["Close overlay"]) {
            onClose()
        }
        controls.addView(liveDot)
        controls.addView(langChip)
        controls.addView(statusText)
        controls.addView(pauseButton)
        controls.addView(minButton)
        controls.addView(closeButton)
        controlsRow = controls
        box.addView(controls)

        originalText = TextView(context).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1.06f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            visibility = View.GONE
            setShadowLayer(3.5f, 0f, 1.2f, Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(4)) }
        }
        box.addView(originalText)

        translatedText = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1.12f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4.5f, 0f, 1.8f, Color.BLACK)
            text = "…"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        box.addView(translatedText)

        frame.addView(box)

        resizeHandle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(180, 255, 255, 255))
                setStroke(dp(1), Color.argb(120, 0, 0, 0))
            }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(6), dp(6))
            }
            setOnTouchListener(ResizeTouchListener())
        }
        frame.addView(resizeHandle)

        root = frame
        try {
            wm.addView(frame, p)
        } catch (t: Throwable) {
            root = null
            params = null
            throw t
        }
    }

    fun update(ui: OverlayUiState) {
        val view = root ?: return
        val lp = params ?: return

        // Re-clamp if device orientation or screen dimensions changed
        val metrics = context.resources.displayMetrics
        if (metrics.widthPixels != lastScreenWidth || metrics.heightPixels != lastScreenHeight) {
            lastScreenWidth = metrics.widthPixels
            lastScreenHeight = metrics.heightPixels
            val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
            val maxW = (metrics.widthPixels - dp(16)).coerceAtLeast(minW)
            lp.width = lp.width.coerceIn(minW, maxW)
            lp.x = clampX(lp.x, lp.width, metrics.widthPixels)
            lp.y = clampY(lp.y, metrics.heightPixels)
            try { wm.updateViewLayout(view, lp) } catch (_: Throwable) {}
        }

        val theme = OverlayThemeCatalog.find(ui.themeId)
        val font = OverlayFontCatalog.find(ui.fontId)
        val s = uiStrings()
        val display = computeOverlayTextDisplay(ui)

        val renderKey = listOf(
            display.translatedText, display.showOriginal, display.originalText,
            display.livePartialText, ui.status.name, ui.statusDetail.orEmpty(),
            ui.minimized, ui.textSizeSp, ui.opacity, ui.themeId, ui.fontId,
            ui.sourceCode, ui.targetCode
        ).joinToString("|")
        if (renderKey == lastRenderKey) return
        lastRenderKey = renderKey

        val effectiveOpacity = ui.opacity.coerceIn(0.45f, 1.0f)
        val bgAlpha = (effectiveOpacity * 255).roundToInt()
        val textR = Color.red(theme.textRgb)
        val textG = Color.green(theme.textRgb)
        val textB = Color.blue(theme.textRgb)

        (container?.background as? GradientDrawable)?.apply {
            setColor(
                Color.argb(
                    bgAlpha,
                    Color.red(theme.backgroundRgb),
                    Color.green(theme.backgroundRgb),
                    Color.blue(theme.backgroundRgb)
                )
            )
            setStroke(dp(1), Color.argb(65, textR, textG, textB))
        }

        val listening = ui.status == RecognitionStatus.LISTENING || ui.status == RecognitionStatus.PROCESSING
        liveDot?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                when {
                    ui.status == RecognitionStatus.PAUSED -> Color.parseColor("#FFC107")
                    ui.status == RecognitionStatus.ERROR -> Color.parseColor("#FF5252")
                    listening -> Color.parseColor("#69F0AE")
                    else -> Color.argb(160, 255, 255, 255)
                }
            )
            setSize(dp(8), dp(8))
        }
        langChip?.text = "${ui.sourceCode.uppercase()} → ${ui.targetCode.uppercase()}  "
        val detail = ui.statusDetail?.trim().orEmpty()
        statusText?.text = buildString {
            append(s[ui.status.displayName])
            if (detail.isNotEmpty()) {
                append(" · ")
                append(detail)
            }
        }
        pauseButton?.contentDescription = if (ui.status == RecognitionStatus.PAUSED) s["Resume captioning"] else s["Pause captioning"]
        pauseButton?.setImageResource(
            if (ui.status == RecognitionStatus.PAUSED) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        minButton?.setImageResource(
            if (ui.minimized) android.R.drawable.arrow_up_float
            else android.R.drawable.arrow_down_float
        )

        // Determine whether to show the secondary upper line:
        // 1) Normal bilingual mode (`display.showOriginal`), OR
        // 2) Before the first translated cue arrives (`display.translatedText == "…"`) when live speech is active
        val isWaitingForFirstTranslation = display.translatedText == "…" && display.livePartialText.isNotEmpty()
        val effectiveOriginalLine = when {
            display.showOriginal -> display.originalText
            isWaitingForFirstTranslation -> display.livePartialText
            else -> ""
        }

        if (effectiveOriginalLine.isNotEmpty() && !ui.minimized) {
            originalText?.setTextColor(Color.argb(205, textR, textG, textB))
            originalText?.typeface = font.typeface
            originalText?.textSize = display.originalTextSizeSp
            if (originalText?.text?.toString() != effectiveOriginalLine) {
                originalText?.text = effectiveOriginalLine
            }
            originalText?.visibility = View.VISIBLE
        } else {
            originalText?.visibility = View.GONE
        }

        // Primary hero line: shows translated cue, or actionable status detail if still idle/waiting
        val heroLine = when {
            display.translatedText != "…" -> display.translatedText
            detail.isNotEmpty() && effectiveOriginalLine.isEmpty() -> detail
            else -> display.translatedText
        }
        translatedText?.setTextColor(theme.textRgb)
        translatedText?.typeface = Typeface.create(font.typeface, Typeface.BOLD)
        translatedText?.textSize = if (heroLine == detail && detail.isNotEmpty()) {
            (display.translatedTextSizeSp * 0.78f).coerceAtLeast(13f)
        } else {
            display.translatedTextSizeSp
        }
        translatedText?.maxLines = if (ui.minimized) 2 else 4
        if (translatedText?.text?.toString() != heroLine) {
            translatedText?.text = heroLine
        }
        translatedText?.visibility = View.VISIBLE
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        controlsRow?.visibility = if (visible) View.VISIBLE else View.GONE
        resizeHandle?.visibility = if (visible) View.VISIBLE else View.GONE
        mainHandler.removeCallbacks(hideControlsRunnable)
        if (visible) scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 3800)
    }

    fun hide() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        root?.let { view ->
            try { wm.removeViewImmediate(view) } catch (_: Throwable) { }
        }
        root = null
        params = null
        container = null
        controlsRow = null
        liveDot = null
        langChip = null
        statusText = null
        originalText = null
        translatedText = null
        pauseButton = null
        minButton = null
        closeButton = null
        resizeHandle = null
        lastRenderKey = null
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun clampX(x: Int, width: Int, screenW: Int): Int {
        val margin = dp(6)
        val maxX = (screenW - width - margin).coerceAtLeast(margin)
        return x.coerceIn(margin, maxX)
    }

    private fun clampY(y: Int, screenH: Int): Int {
        val minY = dp(24)
        val maxY = (screenH - dp(72)).coerceAtLeast(minY)
        return y.coerceIn(minY, maxY)
    }

    private fun makeButton(resId: Int, contentDesc: String, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(resId)
        contentDescription = contentDesc
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(45, 0, 0, 0))
        }
        setColorFilter(Color.WHITE)
        val size = dp(32)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            setMargins(dp(3), 0, dp(2), 0)
        }
        setOnClickListener { onClick() }
    }

    // Drag moves the bar with screen-bounds clamping & horizontal center-snapping.
    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            val metrics = context.resources.displayMetrics
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = event.rawX; touchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (!moved) return true
                    val view = root ?: return false
                    var candidateX = clampX(startX + dx.roundToInt(), lp.width, metrics.widthPixels)
                    val centeredX = (metrics.widthPixels - lp.width) / 2
                    if (abs(candidateX - centeredX) <= dp(12)) {
                        candidateX = centeredX
                    }
                    lp.x = candidateX
                    lp.y = clampY(startY + dy.roundToInt(), metrics.heightPixels)
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        scope.launch {
                            settingsRepository.update { it.copy(overlayX = lp.x, overlayY = lp.y) }
                        }
                    } else {
                        v.performClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var startW = 0
        private var touchX = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            val metrics = context.resources.displayMetrics
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = lp.width
                    touchX = event.rawX
                    scheduleControlsAutoHide()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val view = root ?: return false
                    val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
                    val maxW = (metrics.widthPixels - dp(12)).coerceAtLeast(minW)
                    lp.width = (startW + (event.rawX - touchX).roundToInt()).coerceIn(minW, maxW)
                    lp.x = clampX(lp.x, lp.width, metrics.widthPixels)
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wDp = (lp.width / density).roundToInt()
                    scheduleControlsAutoHide()
                    scope.launch {
                        settingsRepository.update { it.copy(overlayWidthDp = wDp, overlayX = lp.x) }
                    }
                    return true
                }
            }
            return false
        }
    }
}
