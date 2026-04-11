package com.example.pinyintoast

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var btnEnableService: Button
    private lateinit var btnGrantOverlay: Button

    // Advanced settings
    private lateinit var seekbarBaseDuration: SeekBar
    private lateinit var tvBaseDurationValue: TextView
    private lateinit var cbPerChar: CheckBox
    private lateinit var layoutPerCharScale: LinearLayout
    private lateinit var seekbarPerChar: SeekBar
    private lateinit var tvPerCharValue: TextView
    private lateinit var seekbarMaxDuration: SeekBar
    private lateinit var tvMaxDurationValue: TextView

    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "pinyin_toast_prefs"
        const val KEY_BASE_DURATION = "base_duration"
        const val KEY_PER_CHAR_ENABLED = "per_char_enabled"
        const val KEY_PER_CHAR_MS = "per_char_ms"
        const val KEY_MAX_DURATION = "max_duration"

        const val DEFAULT_BASE_DURATION = 2000
        const val DEFAULT_PER_CHAR_MS = 500
        const val DEFAULT_MAX_DURATION = 2000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Status section
        tvStatus = findViewById(R.id.tv_status)
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        btnEnableService = findViewById(R.id.btn_enable_service)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)

        // Advanced settings
        seekbarBaseDuration = findViewById(R.id.seekbar_base_duration)
        tvBaseDurationValue = findViewById(R.id.tv_base_duration_value)
        cbPerChar = findViewById(R.id.cb_per_char)
        layoutPerCharScale = findViewById(R.id.layout_per_char_scale)
        seekbarPerChar = findViewById(R.id.seekbar_per_char)
        tvPerCharValue = findViewById(R.id.tv_per_char_value)
        seekbarMaxDuration = findViewById(R.id.seekbar_max_duration)
        tvMaxDurationValue = findViewById(R.id.tv_max_duration_value)

        // --- Status buttons ---
        btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // --- Restore saved prefs ---
        val savedBase = prefs.getInt(KEY_BASE_DURATION, DEFAULT_BASE_DURATION)
        val savedPerCharEnabled = prefs.getBoolean(KEY_PER_CHAR_ENABLED, false)
        val savedPerChar = prefs.getInt(KEY_PER_CHAR_MS, DEFAULT_PER_CHAR_MS)
        val savedMax = prefs.getInt(KEY_MAX_DURATION, DEFAULT_MAX_DURATION)

        seekbarBaseDuration.progress = savedBase
        tvBaseDurationValue.text = "$savedBase ms"

        cbPerChar.isChecked = savedPerCharEnabled
        layoutPerCharScale.visibility = if (savedPerCharEnabled) View.VISIBLE else View.GONE

        seekbarPerChar.progress = savedPerChar
        tvPerCharValue.text = "$savedPerChar ms"

        seekbarMaxDuration.progress = savedMax
        tvMaxDurationValue.text = "$savedMax ms"

        // --- Listeners ---
        seekbarBaseDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Snap to 100ms steps
                val snapped = (progress / 100) * 100
                tvBaseDurationValue.text = "$snapped ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val snapped = ((seekBar?.progress ?: DEFAULT_BASE_DURATION) / 100) * 100
                seekBar?.progress = snapped
                prefs.edit().putInt(KEY_BASE_DURATION, snapped).apply()
            }
        })

        cbPerChar.setOnCheckedChangeListener { _, isChecked ->
            layoutPerCharScale.visibility = if (isChecked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean(KEY_PER_CHAR_ENABLED, isChecked).apply()
        }

        seekbarPerChar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val snapped = (progress / 100) * 100
                tvPerCharValue.text = "$snapped ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val snapped = ((seekBar?.progress ?: DEFAULT_PER_CHAR_MS) / 100) * 100
                seekBar?.progress = snapped
                prefs.edit().putInt(KEY_PER_CHAR_MS, snapped).apply()
            }
        })

        seekbarMaxDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val snapped = (progress / 100) * 100
                tvMaxDurationValue.text = "$snapped ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val snapped = ((seekBar?.progress ?: DEFAULT_MAX_DURATION) / 100) * 100
                seekBar?.progress = snapped
                prefs.edit().putInt(KEY_MAX_DURATION, snapped).apply()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateOverlayStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, PinyinService::class.java)) {
            tvStatus.text = "已啟用 ✔"
            tvStatus.setTextColor(resources.getColor(R.color.accent_green, null))
        } else {
            tvStatus.text = "未啟用 ✘"
            tvStatus.setTextColor(resources.getColor(R.color.accent_red, null))
        }
    }

    private fun updateOverlayStatus() {
        val canDraw = Settings.canDrawOverlays(this)
        if (canDraw) {
            tvOverlayStatus.text = "已授予 ✔"
            tvOverlayStatus.setTextColor(resources.getColor(R.color.accent_green, null))
            btnGrantOverlay.isEnabled = false
            btnGrantOverlay.alpha = 0.4f
        } else {
            tvOverlayStatus.text = "未授予 ✘"
            tvOverlayStatus.setTextColor(resources.getColor(R.color.accent_red, null))
            btnGrantOverlay.isEnabled = true
            btnGrantOverlay.alpha = 1.0f
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = context.packageName + "/" + accessibilityService.canonicalName
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
        }

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(settingValue)
                while (splitter.hasNext()) {
                    if (splitter.next().equals(expectedComponentName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
