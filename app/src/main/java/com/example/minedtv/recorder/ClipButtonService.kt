package com.example.minedtv.recorder

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import com.example.minedtv.R
import com.example.minedtv.util.Logger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Accessibility service used only to (1) draw a floating Clip button over games while
 * recording and (2) note which app is in front so clips get the game's name.
 * It reads no screen content and performs no actions.
 */
class ClipButtonService : AccessibilityService() {
    private var scope = MainScope()
    private var button: ImageView? = null
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    override fun onServiceConnected() {
        Logger.i("Clip button service connected")
        scope = MainScope()
        Recorder.clipButtonActive.value = true
        scope.launch {
            Recorder.status.collect { if (it is RecorderStatus.Recording) show() else hide() }
        }
        scope.launch {
            Recorder.events.collect { feedback(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui") || pkg.contains("launcher") || pkg == "android") return
        if (Recorder.lastApp.value != pkg) {
            Recorder.lastApp.value = pkg
            Logger.d("Foreground app: $pkg")
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        hide()
        Recorder.clipButtonActive.value = false
        scope.cancel()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun background(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), 0x55000000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show() {
        if (button != null) return
        val size = dp(56)
        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - size - dp(12)
            y = metrics.heightPixels / 3
        }
        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = background(GOLD)
            alpha = 0.92f
            elevation = dp(6).toFloat()
            contentDescription = "Clip the last ${Recorder.settings.value.clipSeconds} seconds"
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        Logger.i("Floating Clip button tapped")
                        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        }.start()
                        Recorder.clip()
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(view, params)
        button = view
    }

    private fun hide() {
        button?.let { runCatching { windowManager.removeView(it) } }
        button = null
    }

    /** Dim while saving, flash green when saved or red on failure. */
    private fun feedback(event: RecorderEvent) {
        val b = button ?: return
        when (event) {
            RecorderEvent.ClipStarted -> b.alpha = 0.55f
            is RecorderEvent.ClipSaved -> flash(b, GREEN)
            is RecorderEvent.ClipFailed -> flash(b, RED)
        }
    }

    private fun flash(b: ImageView, color: Int) {
        b.alpha = 0.92f
        b.background = background(color)
        b.postDelayed({ b.background = background(GOLD) }, 1_200)
    }

    private companion object {
        const val GOLD = 0xFFFFC53D.toInt()
        const val GREEN = 0xFF3DDC84.toInt()
        const val RED = 0xFFFF4D5E.toInt()
    }
}
