package com.tilpad.ime

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 首次启动引导页 — 指引用户启用并切换 TilPad 输入法。
 *
 * 逻辑：
 * 1. App 第一次打开自动弹出此页面
 * 2. 如果用户没有完成输入法启用，关闭后下次打开依旧弹出
 * 3. 两步流程做完后做本地标记，以后不再弹出
 * 4. 第一步按钮：跳转系统输入法设置页面
 * 5. 从系统返回后，实时检测 TilPad 是否已被系统启用
 * 6. 只有检测到已启用，第二步按钮才变蓝可点
 * 7. 第二步按钮：唤起系统切换输入法弹窗
 * 8. 右上角关闭按钮只关闭弹窗，不修改首次标记
 */
class GuideActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var btnStep1: Button
    private lateinit var btnStep2: Button
    private lateinit var btnClose: View

    companion object {
        const val PREF_NAME = "tilpad_settings"
        const val KEY_GUIDE_COMPLETED = "guide_completed"
    }

    /**
     * 在 Activity 创建前应用保存的语言设置 — 确保引导页也使用正确语言。
     */
    override fun attachBaseContext(newBase: Context) {
        val langIndex = newBase.getSharedPreferences("tilpad_settings", Context.MODE_PRIVATE)
            .getInt("app_language", 0)
        val langCode = when (langIndex) { 0 -> "ug"; 1 -> "zh"; else -> "en" }
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        btnStep1 = findViewById(R.id.btn_step1)
        btnStep2 = findViewById(R.id.btn_step2)
        btnClose = findViewById(R.id.btn_close)

        // 第一步按钮：跳转系统输入法设置页
        btnStep1.setOnClickListener {
            try {
                val intent = Intent("android.settings.INPUT_METHOD_SETTINGS")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.guide_error_settings), Toast.LENGTH_SHORT).show()
            }
        }

        // 第二步按钮：唤起系统切换输入法弹窗
        btnStep2.setOnClickListener {
            try {
                val imm = getSystemService(INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()

                // 标记引导完成
                prefs.edit().putBoolean(KEY_GUIDE_COMPLETED, true).apply()

                // 延迟关闭，让用户看到弹窗
                btnStep2.postDelayed({
                    finish()
                }, 500)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.guide_error_chooser), Toast.LENGTH_SHORT).show()
            }
        }

        // 关闭按钮：只关闭当前页面，不修改首次标记
        btnClose.setOnClickListener {
            finish()
        }
    }

    /**
     * 从系统设置页面返回时，实时检测 TilPad 输入法是否已被系统启用。
     * 只有检测到已启用，第二步按钮才切换成蓝色渐变、允许点击。
     */
    override fun onResume() {
        super.onResume()
        checkImeEnabled()
    }

    /**
     * 检测 TilPad 输入法是否已被系统启用。
     * 读取系统已启用输入法列表做真实判断，防止用户进入设置但没有开启输入法的情况。
     */
    private fun checkImeEnabled() {
        val imm = getSystemService(INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        val enabledList = imm.enabledInputMethodList

        var isTilPadEnabled = false
        for (ime in enabledList) {
            if (ime.packageName == packageName) {
                isTilPadEnabled = true
                break
            }
        }

        if (isTilPadEnabled) {
            // 输入法已启用 — 第二步按钮变蓝可点
            btnStep2.isEnabled = true
            btnStep2.setTextColor(0xFFFFFFFF.toInt())
        } else {
            // 输入法未启用 — 第二步按钮保持灰色不可点
            btnStep2.isEnabled = false
            btnStep2.setTextColor(0xFF999999.toInt())
        }
    }
}
