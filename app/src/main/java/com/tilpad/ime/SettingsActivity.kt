package com.tilpad.ime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * TilPad 设置入口 Activity。
 *
 * 输入法 App 本身没有传统意义上的"主界面"，但为了让用户在桌面
 * 能看到图标并方便地跳转到系统输入法设置，提供此简单 Activity。
 *
 * 功能：
 * 1. 显示应用名称和简介
 * 2. 提供按钮跳转到系统"语言和输入法"设置页
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 64, 48, 64)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "TilPad 维语输入法"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val subtitle = TextView(this).apply {
            text = "拉丁字母输入 → 自动转换为阿拉伯字母维语\n支持维/中/英三语切换"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        val enableButton = Button(this).apply {
            text = "去系统设置启用输入法"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }

        val switchButton = Button(this).apply {
            text = "切换为默认输入法"
            setOnClickListener {
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE
                ) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(enableButton)
        layout.addView(switchButton)

        setContentView(layout)
    }
}
