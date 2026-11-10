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
import kotlin.math.roundToInt

// Cinema-style subtitle dock: no header, no paragraph, just the current cue.
// Tap the bar to reveal controls; they fade away again after 2.5s.
// Drag the bar to move it, drag the corner dot to resize it.
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

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

    fun show(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        if (root != null) return
        showInternal(initialX, initialY, widthDp, heightDp)
    }

    private fun showInternal(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        val screenH = context.resources.displayMetrics.heightPixels
        // First run (old default position) docks near the bottom, like real subs.
        val startY = if (initialY == 220) (screenH * 0.68f).roundToInt() else initialY

        val p = WindowManager.LayoutParams(
            (widthDp * density).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = startY
        }
        params = p

        val frame = FrameLayout(context)

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(168, 10, 10, 14))
            }
            setPadding(dp(16), dp(10), dp(16), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setOnTouchListener(DragTouchListener())
            setOnClickListener { setControlsVisible(!controlsVisible) }
        }
        container = box

        // Controls row: hidden until the bar is tapped.
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(6)) }
        }
        liveDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF5252"))
                setSize(dp(8), dp(8))
            }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { setMargins(0, 0, dp(6), 0) }
        }
        langChip = TextView(context).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.08f
            text = ""
        }
        statusText = TextView(context).apply {
            setTextColor(Color.argb(170, 255, 255, 255))
            textSize = 10.5f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pauseButton = makeButton(android.R.drawable.ic_media_pause, uiStrings()["Pause captioning"]) { onPauseResume() }
        minButton = makeButton(android.R.drawable.arrow_down_float, uiStrings()["Minimize overlay"]) { onToggleMinimize() }
        closeButton = makeButton(android.R.drawable.ic_menu_close_clear_cancel, uiStrings()["Close overlay"]) { onClose() }
        controls.addView(liveDot)
        controls.addView(langChip)
        controls.addView(statusText)
        controls.addView(pauseButton)
        controls.addView(minButton)
        controls.addView(closeButton)
        controlsRow = controls
        box.addView(controls)

        originalText = TextView(context).apply {
            setTextColor(Color.argb(185, 255, 255, 255))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1.05f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(3)) }
        }
        box.addView(originalText)

        translatedText = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1.12f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 1.5f, Color.BLACK)
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
                setColor(Color.argb(140, 255, 255, 255))
            }
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(5), dp(5))
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
        if (root == null) return
        val theme = OverlayThemeCatalog.find(ui.themeId)
        val font = OverlayFontCatalog.find(ui.fontId)
        val s = uiStrings()
        val display = computeOverlayTextDisplay(ui)

        // Skip the re-layout when nothing visible actually changed.
        val renderKey = listOf(
            display.translatedText, display.showOriginal, display.originalText,
            ui.status.name, ui.minimized, ui.textSizeSp, ui.opacity, ui.themeId, ui.fontId
        ).joinToString("|")
        if (renderKey == lastRenderKey) return
        lastRenderKey = renderKey

        val effectiveOpacity = ui.opacity.coerceIn(0.45f, 1.0f)
        (container?.background as? GradientDrawable)?.setColor(
            Color.argb(
                (effectiveOpacity * 255).roundToInt(),
                Color.red(theme.backgroundRgb), Color.green(theme.backgroundRgb), Color.blue(theme.backgroundRgb)
            )
        )

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
        statusText?.text = buildString {
            append(s[ui.status.displayName])
            val detail = ui.statusDetail?.trim().orEmpty()
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

        if (display.showOriginal && !ui.minimized) {
            originalText?.typeface = font.typeface
            originalText?.textSize = display.originalTextSizeSp
            if (originalText?.text?.toString() != display.originalText) originalText?.text = display.originalText
            originalText?.visibility = View.VISIBLE
        } else {
            originalText?.visibility = View.GONE
        }

        translatedText?.typeface = Typeface.create(font.typeface, Typeface.BOLD)
        translatedText?.textSize = display.translatedTextSizeSp
        translatedText?.maxLines = if (ui.minimized) 1 else 2
        if (translatedText?.text?.toString() != display.translatedText) translatedText?.text = display.translatedText
        translatedText?.visibility = View.VISIBLE
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        controlsRow?.visibility = if (visible) View.VISIBLE else View.GONE
        resizeHandle?.visibility = if (visible) View.VISIBLE else View.GONE
        mainHandler.removeCallbacks(hideControlsRunnable)
        if (visible) mainHandler.postDelayed(hideControlsRunnable, 2500)
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

    private fun makeButton(resId: Int, contentDesc: String, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(resId)
        contentDescription = contentDesc
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        val size = dp(30)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
        setOnClickListener { onClick() }
    }

    // Drag moves the bar; a clean tap toggles controls (click listener above).
    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
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
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    if (!moved) return true
                    val view = root ?: return false
                    lp.x = startX + dx.roundToInt()
                    lp.y = startY + dy.roundToInt()
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
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = lp.width
                    touchX = event.rawX
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val view = root ?: return false
                    val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
                    val maxW = context.resources.displayMetrics.widthPixels
                    lp.width = (startW + (event.rawX - touchX).roundToInt()).coerceIn(minW, maxW)
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wDp = (lp.width / density).roundToInt()
                    scope.launch {
                        settingsRepository.update { it.copy(overlayWidthDp = wDp) }
                    }
                    return true
                }
            }
            return false
        }
    }
}
