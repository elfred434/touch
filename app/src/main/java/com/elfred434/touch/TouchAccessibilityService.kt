package com.elfred434.touch

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioManager.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.abs

class TouchAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var zoneView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingTapCount = 0
    private var tapRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var longTriggered = false
    private val touchSlop by lazy { (resources.displayMetrics.density * 24).toInt() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        rebuildOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlay()
        if (current === this) current = null
        super.onDestroy()
    }

    fun rebuildOverlay() {
        removeOverlay()
        val p = TouchPrefs.prefs(this)
        if (!p.getBoolean(TouchPrefs.ENABLED, true)) return
        val density = resources.displayMetrics.density
        val width = (p.getInt(TouchPrefs.ZONE_WIDTH_DP, 82) * density).toInt().coerceAtLeast(36)
        val height = (p.getInt(TouchPrefs.ZONE_HEIGHT_DP, 50) * density).toInt().coerceAtLeast(32)
        val screenWidth = resources.displayMetrics.widthPixels
        val xCenter = (screenWidth * p.getFloat(TouchPrefs.ZONE_X_PERCENT, 0.5f)).toInt()
        val x = (xCenter - width / 2).coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val y = (p.getInt(TouchPrefs.ZONE_Y_DP, 0) * density).toInt().coerceAtLeast(0)
        val view = object : View(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean = handleTouch(event)
        }
        view.alpha = 0.01f
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        try {
            windowManager?.addView(view, params)
            zoneView = view
        } catch (_: Exception) {
            Toast.makeText(this, "Impossible d’afficher la zone tactile sur cet appareil.", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeOverlay() {
        zoneView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        zoneView = null
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; downTime = event.eventTime
                moved = false; longTriggered = false
                longPressRunnable?.let(mainHandler::removeCallbacks)
                longPressRunnable = Runnable {
                    if (!moved) {
                        longTriggered = true
                        tapRunnable?.let(mainHandler::removeCallbacks); tapRunnable = null
                        pendingTapCount = 0
                        trigger(TouchPrefs.LONG_PRESS)
                    }
                }.also { mainHandler.postDelayed(it, 600) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                    moved = true
                    longPressRunnable?.let(mainHandler::removeCallbacks)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let(mainHandler::removeCallbacks)
                if (event.actionMasked == MotionEvent.ACTION_CANCEL) return true
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val elapsed = event.eventTime - downTime
                if (moved && abs(dx) > abs(dy) && abs(dx) > touchSlop) {
                    pendingTapCount = 0
                    tapRunnable?.let(mainHandler::removeCallbacks); tapRunnable = null
                    trigger(if (dx < 0) TouchPrefs.SWIPE_LEFT else TouchPrefs.SWIPE_RIGHT)
                } else if (!longTriggered && elapsed < 600) {
                    pendingTapCount = (pendingTapCount + 1).coerceAtMost(3)
                    tapRunnable?.let(mainHandler::removeCallbacks)
                    tapRunnable = Runnable {
                        val key = when (pendingTapCount) {
                            1 -> TouchPrefs.TAP
                            2 -> TouchPrefs.DOUBLE_TAP
                            else -> TouchPrefs.TRIPLE_TAP
                        }
                        pendingTapCount = 0
                        trigger(key)
                    }.also { mainHandler.postDelayed(it, 330) }
                }
                return true
            }
        }
        return true
    }

    private fun trigger(gesture: String) {
        val p = TouchPrefs.prefs(this)
        if (p.getBoolean(TouchPrefs.HAPTICS, true)) {
            @Suppress("DEPRECATION")
            zoneView?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
        performConfiguredAction(TouchPrefs.actionFor(this, gesture))
    }

    private fun performConfiguredAction(action: String) {
        try {
            when (action) {
                TouchPrefs.TORCH -> toggleTorch()
                TouchPrefs.SCREENSHOT -> {
                    if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    else toast("Capture d’écran non prise en charge par cette version d’Android.")
                }
                TouchPrefs.CAMERA -> startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                TouchPrefs.MEDIA_PLAY_PAUSE -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                TouchPrefs.MEDIA_NEXT -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                TouchPrefs.MEDIA_PREVIOUS -> sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                TouchPrefs.RINGER -> toggleRinger()
                TouchPrefs.DND -> openDndSettingsOrToggle()
                TouchPrefs.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                TouchPrefs.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                TouchPrefs.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                TouchPrefs.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                TouchPrefs.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                TouchPrefs.VOLUME_UP -> (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE, FLAG_SHOW_UI)
                TouchPrefs.VOLUME_DOWN -> (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(STREAM_MUSIC, ADJUST_LOWER, FLAG_SHOW_UI)
                TouchPrefs.POWER -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                TouchPrefs.NOTES, TouchPrefs.TIMER, TouchPrefs.OPEN_APP -> {
                    startActivity(Intent(this, MainActivity::class.java).putExtra("open_tool", action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                else -> Unit
            }
        } catch (e: SecurityException) {
            toast("Autorisation Android requise pour cette action.")
        } catch (e: Exception) {
            toast("Action indisponible sur cet appareil.")
        }
    }

    private fun toggleTorch() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (cameraId == null) { toast("Aucun flash détecté."); return }
        val prefs = TouchPrefs.prefs(this)
        val on = prefs.getBoolean("torch_on", false)
        manager.setTorchMode(cameraId, !on)
        prefs.edit().putBoolean("torch_on", !on).apply()
    }

    private fun sendMediaKey(keyCode: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val now = android.os.SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun toggleRinger() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val next = when (audio.ringerMode) {
            RINGER_MODE_NORMAL -> RINGER_MODE_VIBRATE
            RINGER_MODE_VIBRATE -> RINGER_MODE_SILENT
            else -> RINGER_MODE_NORMAL
        }
        audio.ringerMode = next
        toast(when (next) { RINGER_MODE_NORMAL -> "Sonnerie activée"; RINGER_MODE_VIBRATE -> "Vibreur activé"; else -> "Mode silencieux" })
    }

    private fun openDndSettingsOrToggle() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            toast("Autorise Touch pour gérer Ne pas déranger.")
            return
        }
        val next = if (nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
            android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(next)
    }

    private fun toast(message: String) = mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }

    companion object {
        @Volatile private var current: TouchAccessibilityService? = null
        fun refreshZone() { current?.rebuildOverlay() }
        fun currentAction(context: Context, action: String) {
            val service = current
            if (service == null) Toast.makeText(context, "Active d’abord le service Touch.", Toast.LENGTH_SHORT).show()
            else service.performConfiguredAction(action)
        }
    }
}
