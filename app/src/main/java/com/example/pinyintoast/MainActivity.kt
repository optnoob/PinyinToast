package com.example.pinyintoast

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

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

    // Position settings
    private lateinit var spinnerPosition: Spinner
    private lateinit var seekbarYOffset: SeekBar
    private lateinit var tvYOffsetValue: TextView

    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "pinyin_toast_prefs"
        const val KEY_BASE_DURATION = "base_duration"
        const val KEY_PER_CHAR_ENABLED = "per_char_enabled"
        const val KEY_PER_CHAR_MS = "per_char_ms"
        const val KEY_MAX_DURATION = "max_duration"
        const val KEY_POSITION = "position"
        const val KEY_Y_OFFSET = "y_offset"

        const val DEFAULT_BASE_DURATION = 2000
        const val DEFAULT_PER_CHAR_MS = 500
        const val DEFAULT_MAX_DURATION = 2000
        const val DEFAULT_POSITION = 7  // index 7 = 下方置中 (bottom-center)
        const val DEFAULT_Y_OFFSET = 300

        // Position labels — order must match POSITION_GRAVITY_MAP in PinyinService
        val POSITION_LABELS = arrayOf(
            "上方靠左", "上方置中", "上方靠右",
            "中間靠左", "正中央",   "中間靠右",
            "下方靠左", "下方置中", "下方靠右"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

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

        // Position settings
        spinnerPosition = findViewById(R.id.spinner_position)
        seekbarYOffset = findViewById(R.id.seekbar_y_offset)
        tvYOffsetValue = findViewById(R.id.tv_y_offset_value)

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
        val savedPosition = prefs.getInt(KEY_POSITION, DEFAULT_POSITION)
        val savedYOffset = prefs.getInt(KEY_Y_OFFSET, DEFAULT_Y_OFFSET)

        seekbarBaseDuration.progress = savedBase
        tvBaseDurationValue.text = "$savedBase ms"

        cbPerChar.isChecked = savedPerCharEnabled
        layoutPerCharScale.visibility = if (savedPerCharEnabled) View.VISIBLE else View.GONE

        seekbarPerChar.progress = savedPerChar
        tvPerCharValue.text = "$savedPerChar ms"

        seekbarMaxDuration.progress = savedMax
        tvMaxDurationValue.text = "$savedMax ms"

        seekbarYOffset.progress = savedYOffset
        tvYOffsetValue.text = "$savedYOffset dp"

        // --- Position spinner ---
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, POSITION_LABELS)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPosition.adapter = adapter
        spinnerPosition.setSelection(savedPosition)
        spinnerPosition.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putInt(KEY_POSITION, position).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- Listeners ---
        seekbarBaseDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
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

        seekbarYOffset.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val snapped = (progress / 10) * 10
                tvYOffsetValue.text = "$snapped dp"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val snapped = ((seekBar?.progress ?: DEFAULT_Y_OFFSET) / 10) * 10
                seekBar?.progress = snapped
                prefs.edit().putInt(KEY_Y_OFFSET, snapped).apply()
            }
        })
    }

    // --- Toolbar overflow menu ---
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_contribute -> {
                showContributeDialog()
                true
            }
            R.id.menu_big_thank_you -> {
                showBigThankYouDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showContributeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Contribute")
            .setMessage(
                "Pinyin Toast 是一個開源專案。\n\n" +
                "歡迎提交 Bug 回報、功能建議或 Pull Request！\n\n" +
                "感謝你的支持 ❤️"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showBigThankYouDialog() {
        AlertDialog.Builder(this)
            .setTitle("Big Thank You! 🎉")
            .setMessage(
                "感謝使用 Pinyin Toast！\n\n" +
                "這個 App 的誕生離不開以下資源：\n" +
                "• pinyin4j 拼音轉換庫\n" +
                "• Android Accessibility Service API\n\n" +
                "最重要的是，感謝你 — 使用者！"
            )
            .setPositiveButton("😊", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        updateOverlayStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, PinyinService::class.java)) {
            tvStatus.text = "授權狀態：已啟用 ✔"
            tvStatus.setTextColor(resources.getColor(R.color.accent_green, null))
        } else {
            tvStatus.text = "授權狀態：未啟用 ✘"
            tvStatus.setTextColor(resources.getColor(R.color.accent_red, null))
        }
    }

    private fun updateOverlayStatus() {
        val canDraw = Settings.canDrawOverlays(this)
        if (canDraw) {
            tvOverlayStatus.text = "授權狀態：已授予 ✔"
            tvOverlayStatus.setTextColor(resources.getColor(R.color.accent_green, null))
            btnGrantOverlay.isEnabled = true
            btnGrantOverlay.alpha = 1.0f
        } else {
            tvOverlayStatus.text = "授權狀態：未授予 ✘ — 拼音將無法顯示於其他應用程式上"
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
