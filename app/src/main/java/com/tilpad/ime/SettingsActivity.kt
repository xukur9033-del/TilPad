package com.tilpad.ime

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * TilPad 设置入口 Activity — Telegram 风格界面。
 *
 * 功能：
 * 1. 蓝色渐变 Header 展示 App 图标和名称
 * 2. 卡片式设置项：启用输入法、切换输入法
 * 3. 开关式偏好设置：震动、声音、深色模式
 * 4. 关于和反馈入口
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 设置项：启用输入法 → 跳转系统输入法设置
        findViewById<LinearLayout>(R.id.item_enable_ime).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        // 设置项：切换默认输入法 → 弹出系统输入法选择器
        findViewById<LinearLayout>(R.id.item_switch_ime).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        }

        // 设置项：关于 TilPad → 弹出关于对话框
        findViewById<LinearLayout>(R.id.item_about).setOnClickListener {
            showAboutDialog()
        }

        // 设置项：反馈建议 → 显示反馈信息
        findViewById<LinearLayout>(R.id.item_feedback).setOnClickListener {
            showFeedbackToast()
        }

        // 开关：键盘震动
        val switchVibrate = findViewById<SwitchMaterial>(R.id.switch_vibrate)
        switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "已开启键盘震动" else "已关闭键盘震动"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 开关：按键声音
        val switchSound = findViewById<SwitchMaterial>(R.id.switch_sound)
        switchSound.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "已开启按键声音" else "已关闭按键声音"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 开关：深色模式（提示功能开发中）
        val switchDarkMode = findViewById<SwitchMaterial>(R.id.switch_dark_mode)
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            val msg = if (isChecked) "深色模式开发中，敬请期待" else "已关闭深色模式"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于 TilPad")
            .setMessage(
                "TilPad 维语输入法\n\n" +
                "版本：1.1\n" +
                "功能：拉丁维语 (ULY) → 阿拉伯维语 (UAY) 自动转换\n\n" +
                "支持维/中/英三语键盘切换\n" +
                "持续开发中，更多功能即将上线"
            )
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 显示反馈提示
     */
    private fun showFeedbackToast() {
        Toast.makeText(
            this,
            "反馈功能开发中，感谢支持！",
            Toast.LENGTH_SHORT
        ).show()
    }
}
