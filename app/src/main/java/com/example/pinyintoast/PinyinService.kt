package com.example.pinyintoast

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

class PinyinService : AccessibilityService() {

    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_NUMBER
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: TextView? = null
    private lateinit var windowManager: WindowManager
    private var hideRunnable: Runnable? = null
    private lateinit var prefs: SharedPreferences

    companion object {
        /**
         * Gravity values mapped to position index (matches POSITION_LABELS order in MainActivity):
         *  0=上方靠左  1=上方置中  2=上方靠右
         *  3=中間靠左  4=正中央    5=中間靠右
         *  6=下方靠左  7=下方置中  8=下方靠右
         */
        val POSITION_GRAVITY_MAP = intArrayOf(
            Gravity.TOP or Gravity.START,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            Gravity.TOP or Gravity.END,
            Gravity.CENTER_VERTICAL or Gravity.START,
            Gravity.CENTER,
            Gravity.CENTER_VERTICAL or Gravity.END,
            Gravity.BOTTOM or Gravity.START,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            Gravity.BOTTOM or Gravity.END
        )
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
//        Log.d("PinyinToast", "Event type: ${event.eventType}")

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val addedCount = event.addedCount
//            Log.d("PinyinToast", "TYPE_VIEW_TEXT_CHANGED text: ${event.text}")

            if (addedCount > 0 && event.text != null && event.text.isNotEmpty()) {
                val fullText = event.text.joinToString("")
                val fromIndex = event.fromIndex

                if (fromIndex >= 0 && fromIndex + addedCount <= fullText.length) {
                    val addedStr = fullText.substring(fromIndex, fromIndex + addedCount)
                    handleNewText(addedStr)
                }
            }
        }
    }

    private fun handleNewText(newText: String) {
        val result = StringBuilder()
        var hasChinese = false
        var chineseCharCount = 0

        for (char in newText) {
            if (char.toString().matches(Regex("[\\u4e00-\\u9fa5]"))) {
                hasChinese = true
                chineseCharCount++
                try {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(char, pinyinFormat)
                    if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                        result.append(char).append(pinyinArray[0]).append("  ")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (hasChinese && result.isNotEmpty()) {
            val toastMessage = result.toString().trim()
//            Log.d("PinyinToast", "overlay sent: $toastMessage")
            val duration = calculateDuration(chineseCharCount)
            mainHandler.post { showOverlay(toastMessage, duration) }
        }
    }

    /**
     * Calculate overlay display duration based on user preferences:
     *  - Base duration (default 2000 ms)
     *  - Per-character extension: base + (charCount × perCharMs)
     *  - Capped at max duration
     */
    private fun calculateDuration(charCount: Int): Long {
        val baseDuration = prefs.getInt(MainActivity.KEY_BASE_DURATION, MainActivity.DEFAULT_BASE_DURATION)
        val perCharEnabled = prefs.getBoolean(MainActivity.KEY_PER_CHAR_ENABLED, false)
        val perCharMs = prefs.getInt(MainActivity.KEY_PER_CHAR_MS, MainActivity.DEFAULT_PER_CHAR_MS)
        val maxDuration = prefs.getInt(MainActivity.KEY_MAX_DURATION, MainActivity.DEFAULT_MAX_DURATION)

        var total = baseDuration.toLong()
        if (perCharEnabled) {
            total += charCount.toLong() * perCharMs.toLong()
        }

        // Cap at max duration
        if (total > maxDuration) {
            total = maxDuration.toLong()
        }

        return total
    }

    private fun showOverlay(message: String, durationMs: Long) {
        // Cancel any pending hide
        hideRunnable?.let { mainHandler.removeCallbacks(it) }

        // Remove existing overlay if present
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }

        val tv = TextView(this).apply {
            text = message
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.argb(210, 30, 30, 30))
        }

        // Read position preferences
        val positionIndex = prefs.getInt(MainActivity.KEY_POSITION, MainActivity.DEFAULT_POSITION)
        val yOffsetDp = prefs.getInt(MainActivity.KEY_Y_OFFSET, MainActivity.DEFAULT_Y_OFFSET)
        val gravityValue = POSITION_GRAVITY_MAP.getOrElse(positionIndex) {
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val yOffsetPx = (yOffsetDp * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityValue
            y = yOffsetPx
        }

        try {
            windowManager.addView(tv, params)
            overlayView = tv
        } catch (e: Exception) {
            Log.e("PinyinToast", "Failed to add overlay: ${e.message}")
            return
        }

        // Auto-hide after calculated duration
        hideRunnable = Runnable {
            overlayView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                overlayView = null
            }
        }.also { mainHandler.postDelayed(it, durationMs) }
    }

    override fun onInterrupt() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }
}
