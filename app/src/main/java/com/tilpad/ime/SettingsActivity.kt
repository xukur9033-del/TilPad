package com.tilpad.ime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SettingsActivity v6 — 修复语言切换不生效问题。
 *
 * 修复：
 * - 使用 attachBaseContext 在 Activity 创建前应用 Locale，确保资源正确加载
 * - 使用 finish + startActivity 替代 recreate，确保完整重启
 * - 修复布局中硬编码英文文字，改用字符串资源
 * - 音效试听使用 ToneGenerator 保证有声音输出
 * - 开关使用 iOS 风格绿色圆角样式
 * - 设置页每个菜单项只出现一次，无重复
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var tabHome: LinearLayout
    private lateinit var tabSkin: LinearLayout
    private lateinit var tabFont: LinearLayout
    private lateinit var tabSettings: LinearLayout

    private lateinit var pageHome: View
    private lateinit var pageSkin: View
    private lateinit var pageFont: View
    private lateinit var pageSettings: View

    private var audioManager: AudioManager? = null
    private var toneGenerator: ToneGenerator? = null

    private val soundRadioIds = listOf(
        R.id.radio_sound_0,
        R.id.radio_sound_1,
        R.id.radio_sound_2,
        R.id.radio_sound_3
    )

    companion object {
        private const val REQUEST_PICK_IMAGE = 1001
    }

    /**
     * 在 Activity 创建前应用保存的语言设置 — 确保所有字符串资源正确加载。
     * 这是语言切换生效的关键：必须在 attachBaseContext 中设置 Locale，
     * 而不是在 onCreate 中，因为此时资源已经被加载。
     */
    override fun attachBaseContext(newBase: Context) {
        val langIndex = newBase.getSharedPreferences("tilpad_settings", Context.MODE_PRIVATE)
            .getInt("app_language", 0)
        val langCode = when (langIndex) { 0 -> "ug"; 1 -> "zh"; else -> "en" }
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        // 创建新的 Context，确保资源使用正确的 Locale
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("tilpad_settings", Context.MODE_PRIVATE)
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: Exception) {
            // ToneGenerator 创建失败不影响功能
        }

        initViews()
        setupTabs()
        setupSettingsPage()
        setupSkinPage()
        setupFontPage()
        loadSettings()

        showPage(0)

        // 首次启动引导页 — 如果用户未完成引导流程，自动弹出
        if (!prefs.getBoolean(GuideActivity.KEY_GUIDE_COMPLETED, false)) {
            startActivity(Intent(this, GuideActivity::class.java))
        }
    }

    override fun onDestroy() {
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // 忽略
        }
        super.onDestroy()
    }

    private fun initViews() {
        tabHome = findViewById(R.id.tab_home)
        tabSkin = findViewById(R.id.tab_skin)
        tabFont = findViewById(R.id.tab_font)
        tabSettings = findViewById(R.id.tab_settings)

        pageHome = findViewById(R.id.page_home)
        pageSkin = findViewById(R.id.page_skin)
        pageFont = findViewById(R.id.page_font)
        pageSettings = findViewById(R.id.page_settings)
    }

    private fun setupTabs() {
        tabHome.setOnClickListener { showPage(0) }
        tabSkin.setOnClickListener { showPage(1) }
        tabFont.setOnClickListener { showPage(2) }
        tabSettings.setOnClickListener { showPage(3) }
    }

    private fun showPage(index: Int) {
        pageHome.visibility = View.GONE
        pageSkin.visibility = View.GONE
        pageFont.visibility = View.GONE
        pageSettings.visibility = View.GONE

        val active = 0xFF2563EB.toInt()
        val inactive = 0xFF999999.toInt()
        for (tab in listOf(tabHome, tabSkin, tabFont, tabSettings)) {
            val tv = tab.getChildAt(0) as? TextView
            tv?.setTextColor(inactive)
        }

        when (index) {
            0 -> {
                pageHome.visibility = View.VISIBLE
                (tabHome.getChildAt(0) as TextView).setTextColor(active)
            }
            1 -> {
                pageSkin.visibility = View.VISIBLE
                (tabSkin.getChildAt(0) as TextView).setTextColor(active)
            }
            2 -> {
                pageFont.visibility = View.VISIBLE
                (tabFont.getChildAt(0) as TextView).setTextColor(active)
            }
            3 -> {
                pageSettings.visibility = View.VISIBLE
                (tabSettings.getChildAt(0) as TextView).setTextColor(active)
            }
        }
    }

    // ============================================================
    // App 内多语言切换
    // ============================================================

    private fun showLanguageSwitcher() {
        val languages = arrayOf("维语 (ئۇيغۇرچە)", "中文", "English")
        val langCodes = arrayOf("ug", "zh", "en")
        val current = prefs.getInt("app_language", 0)

        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.select_language))
        builder.setSingleChoiceItems(languages, current) { dialog, which ->
            prefs.edit().putInt("app_language", which).apply()
            applyAppLanguage(langCodes[which])
            dialog.dismiss()
            // 完整重启 Activity 以应用语言变更 — recreate 在某些设备上不可靠
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            finish()
            startActivity(intent)
        }
        builder.setNegativeButton(getString(R.string.cancel), null)
        builder.show()
    }

    private fun applyAppLanguage(langCode: String) {
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        // 更新所有资源配置
        resources.updateConfiguration(config, resources.displayMetrics)
        applicationContext.resources.updateConfiguration(config, applicationContext.resources.displayMetrics)
    }

    // ============================================================
    // 设置页
    // ============================================================

    private fun setupSettingsPage() {
        // App 内语言切换
        findViewById<View>(R.id.item_app_language)?.setOnClickListener {
            showLanguageSwitcher()
        }

        // 启用输入法
        findViewById<View>(R.id.item_enable_ime)?.setOnClickListener {
            val intent = Intent("android.settings.INPUT_METHOD_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        // 切换输入法
        findViewById<View>(R.id.item_switch_ime)?.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }

        // 关于
        findViewById<View>(R.id.item_about)?.setOnClickListener {
            Toast.makeText(this, getString(R.string.settings_about), Toast.LENGTH_LONG).show()
        }

        // 反馈
        findViewById<View>(R.id.item_feedback)?.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, "TilPad 维语输入法 - 推荐!")
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_feedback)))
        }

        // 清除缓存
        findViewById<View>(R.id.item_clear_cache)?.setOnClickListener {
            try {
                val cacheDir = cacheDir
                if (cacheDir != null && cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.clear_failed), Toast.LENGTH_SHORT).show()
            }
        }

        // 震动开关
        val switchVibration = findViewById<Switch>(R.id.switch_vibration)
        switchVibration?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }

        // 按键声音开关
        val switchSound = findViewById<Switch>(R.id.switch_sound)
        switchSound?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        // 深色模式开关
        val switchDarkMode = findViewById<Switch>(R.id.switch_dark_mode)
        switchDarkMode?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                prefs.edit().putInt("skin_type", 1).apply()
            } else {
                prefs.edit().putInt("skin_type", 0).apply()
            }
        }

        // 音效选择
        setupSoundSelection()
    }

    private fun setupSoundSelection() {
        for (i in 0 until 4) {
            // 点击整行选中该音效
            val rowId = when (i) {
                0 -> R.id.sound_standard
                1 -> R.id.sound_spacebar
                2 -> R.id.sound_delete
                else -> R.id.sound_return
            }
            val row = findViewById<View>(rowId)
            row?.setOnClickListener {
                selectSound(i)
                Toast.makeText(this, getSoundName(i), Toast.LENGTH_SHORT).show()
            }

            // 试听按钮 — 使用 ToneGenerator 保证有声音
            val previewId = when (i) {
                0 -> R.id.preview_sound_0
                1 -> R.id.preview_sound_1
                2 -> R.id.preview_sound_2
                else -> R.id.preview_sound_3
            }
            val previewBtn = findViewById<TextView>(previewId)
            previewBtn?.setOnClickListener {
                previewSound(i)
                Toast.makeText(this, getSoundName(i), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 试听音效 — 使用 ToneGenerator 播放不同音调，保证有声音输出。
     */
    private fun previewSound(index: Int) {
        try {
            val toneType = when (index) {
                0 -> ToneGenerator.TONE_PROP_BEEP       // 标准音
                1 -> ToneGenerator.TONE_PROP_PROMPT     // 空格音
                2 -> ToneGenerator.TONE_PROP_NACK        // 删除音
                else -> ToneGenerator.TONE_PROP_ACK      // 回车音
            }
            toneGenerator?.startTone(toneType, 80)
        } catch (e: Exception) {
            // 回退到 AudioManager
            try {
                val effectId = when (index) {
                    0 -> AudioManager.FX_KEYPRESS_STANDARD
                    1 -> AudioManager.FX_KEYPRESS_SPACEBAR
                    2 -> AudioManager.FX_KEYPRESS_DELETE
                    else -> AudioManager.FX_KEYPRESS_RETURN
                }
                audioManager?.playSoundEffect(effectId, 1.0f)
            } catch (e2: Exception) {
                // 忽略
            }
        }
    }

    private fun selectSound(index: Int) {
        for (i in 0 until 4) {
            val radio = findViewById<RadioButton>(soundRadioIds[i])
            radio?.isChecked = (i == index)
        }
        prefs.edit().putInt("sound_type", index).apply()
    }

    private fun getSoundName(index: Int): String = when (index) {
        0 -> "Standard"
        1 -> "Spacebar"
        2 -> "Delete"
        else -> "Return"
    }

    // ============================================================
    // 皮肤页
    // ============================================================

    private fun setupSkinPage() {
        val skins = listOf(
            R.id.skin_default to 0,
            R.id.skin_dark to 1,
            R.id.skin_blue to 2,
            R.id.skin_green to 3,
            R.id.skin_pink to 4,
            R.id.skin_orange to 5,
            R.id.skin_purple to 6
        )

        for ((viewId, skinType) in skins) {
            findViewById<View>(viewId)?.setOnClickListener {
                prefs.edit()
                    .putInt("skin_type", skinType)
                    .putBoolean("dark_mode", skinType == 1)
                    .apply()
                Toast.makeText(this, getString(R.string.skin_applied), Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.skin_custom)?.setOnClickListener {
            pickImageFromGallery()
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        try {
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cannot_open_gallery), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val outFile = java.io.File(filesDir, "keyboard_bg.jpg")
                    val outputStream = java.io.FileOutputStream(outFile)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()

                    prefs.edit()
                        .putInt("skin_type", 100)
                        .putString("skin_image_path", outFile.absolutePath)
                        .apply()

                    Toast.makeText(this, getString(R.string.background_set), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.clear_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ============================================================
    // 字体页
    // ============================================================

    private fun setupFontPage() {
        val fontOptions = listOf(
            R.id.font_default to R.id.radio_font_default,
            R.id.font_round to R.id.radio_font_round,
            R.id.font_thin to R.id.radio_font_thin
        )

        for ((layoutId, radioId) in fontOptions) {
            findViewById<View>(layoutId)?.setOnClickListener {
                for ((_, rid) in fontOptions) {
                    findViewById<RadioButton>(rid)?.isChecked = false
                }
                findViewById<RadioButton>(radioId)?.isChecked = true
                val fontIndex = fontOptions.indexOfFirst { it.first == layoutId }
                prefs.edit().putInt("font_type", fontIndex).apply()
                Toast.makeText(this, getString(R.string.font_applied), Toast.LENGTH_SHORT).show()
            }
        }

        val slider = findViewById<SeekBar>(R.id.font_size_slider)
        slider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fontSize = 16f + progress * 1.4f
                prefs.edit().putFloat("font_size", fontSize).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Toast.makeText(this@SettingsActivity, getString(R.string.font_size_saved), Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ============================================================
    // 加载保存的设置
    // ============================================================

    private fun loadSettings() {
        // 更新语言显示值 — 根据当前选择的语言显示对应名称
        val langIndex = prefs.getInt("app_language", 0)
        val langNameResId = when (langIndex) {
            0 -> R.string.language_ug
            1 -> R.string.language_zh
            else -> R.string.language_en
        }
        findViewById<TextView>(R.id.tv_app_language_value)?.text = getString(langNameResId)

        val switchVibration = findViewById<Switch>(R.id.switch_vibration)
        switchVibration?.isChecked = prefs.getBoolean("vibration_enabled", true)

        val switchSound = findViewById<Switch>(R.id.switch_sound)
        switchSound?.isChecked = prefs.getBoolean("sound_enabled", false)

        val switchDarkMode = findViewById<Switch>(R.id.switch_dark_mode)
        switchDarkMode?.isChecked = prefs.getBoolean("dark_mode", false)

        val soundType = prefs.getInt("sound_type", 0)
        selectSound(soundType)

        val fontType = prefs.getInt("font_type", 0)
        for (i in listOf(0, 1, 2)) {
            val radioId = when (i) {
                0 -> R.id.radio_font_default
                1 -> R.id.radio_font_round
                else -> R.id.radio_font_thin
            }
            findViewById<RadioButton>(radioId)?.isChecked = (i == fontType)
        }

        val slider = findViewById<SeekBar>(R.id.font_size_slider)
        val fontSize = prefs.getFloat("font_size", 22f)
        val progress = ((fontSize - 16f) / 1.4f).toInt()
        slider?.progress = progress.coerceIn(0, 10)
    }
}
