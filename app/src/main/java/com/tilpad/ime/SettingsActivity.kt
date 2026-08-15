package com.tilpad.ime

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SettingsActivity v3 — 底部 Tab 多页面导航。
 *
 * 四个页面：
 * 1. 首页测试页 — 直接打字测试维语/中文
 * 2. 主题皮肤页 — 内置多款皮肤 + 相册自定义
 * 3. 字体设置页 — 字体切换 + 字体大小调节
 * 4. 设置页 — 启用输入法/切换/震动/声音/深色模式/关于
 */
class SettingsActivity : AppCompatActivity() {

    // 底部 Tab 按钮
    private lateinit var tabHome: LinearLayout
    private lateinit var tabSkin: LinearLayout
    private lateinit var tabFont: LinearLayout
    private lateinit var tabSettings: LinearLayout

    // 四个页面容器
    private lateinit var pageHome: View
    private lateinit var pageSkin: View
    private lateinit var pageFont: View
    private lateinit var pageSettings: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 找到底部 Tab
        tabHome = findViewById(R.id.tab_home)
        tabSkin = findViewById(R.id.tab_skin)
        tabFont = findViewById(R.id.tab_font)
        tabSettings = findViewById(R.id.tab_settings)

        // 找到页面容器
        pageHome = findViewById(R.id.page_home)
        pageSkin = findViewById(R.id.page_skin)
        pageFont = findViewById(R.id.page_font)
        pageSettings = findViewById(R.id.page_settings)

        // Tab 点击事件
        tabHome.setOnClickListener { showPage(0) }
        tabSkin.setOnClickListener { showPage(1) }
        tabFont.setOnClickListener { showPage(2) }
        tabSettings.setOnClickListener { showPage(3) }

        // 设置页功能
        setupSettingsPage()

        // 默认显示首页
        showPage(0)
    }

    private fun showPage(index: Int) {
        // 隐藏所有页面
        pageHome.visibility = View.GONE
        pageSkin.visibility = View.GONE
        pageFont.visibility = View.GONE
        pageSettings.visibility = View.GONE

        // 重置 Tab 颜色
        val active = 0xFF2563EB.toInt()
        val inactive = 0xFF999999.toInt()
        for (tab in listOf(tabHome, tabSkin, tabFont, tabSettings)) {
            val tv = tab.getChildAt(0) as? TextView
            tv?.setTextColor(inactive)
        }

        // 显示选中页面
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

    private fun setupSettingsPage() {
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
            Toast.makeText(this, "TilPad 维语输入法 v3.0\n基于 composing 模式的维语连写引擎\n内置拼音引擎支持中文输入", Toast.LENGTH_LONG).show()
        }

        // 反馈
        findViewById<View>(R.id.item_feedback)?.setOnClickListener {
            Toast.makeText(this, "反馈功能开发中，敬请期待", Toast.LENGTH_SHORT).show()
        }
    }
}
