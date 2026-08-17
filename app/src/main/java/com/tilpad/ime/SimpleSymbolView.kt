package com.tilpad.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * SimpleSymbolView — 简易符号页面视图（@?! 第一页）。
 *
 * 紧贴键盘上方显示一行简单符号，用户点击符号后触发 [onSymbolSelectListener]。
 * 右上角放置 🪪 身份证小图标，点击后触发 [onExpandListener] 展开完整符号大页面。
 *
 * 不同输入模式显示不同符号集：
 * - 维语模式 (0)：@ ! ؟ - …
 * - 英文模式 (1)：… - ? ! @ / : ( )
 * - 中文模式 (2)：؟ ! ·
 *
 * 暗色模式可通过 [setDarkMode] 切换。
 */
class SimpleSymbolView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 符号选择回调 — 参数为选中的符号文本 */
    private var onSymbolSelectListener: ((String) -> Unit)? = null

    /** 展开回调 — 点击 🪪 图标时触发，展开完整符号大页面 */
    private var onExpandListener: (() -> Unit)? = null

    /** 当前输入模式：0=维语, 1=英文, 2=中文 */
    private var currentMode = MODE_CHINESE

    /** 暗色模式标记 */
    private var isDarkMode = false

    /** 符号按钮容器 */
    private lateinit var symbolContainer: LinearLayout

    /** 分隔线 */
    private lateinit var dividerView: View

    /** 右上角展开图标（🪪 身份证） */
    private lateinit var expandIcon: TextView

    companion object {
        const val MODE_UYGHUR = 0
        const val MODE_ENGLISH = 1
        const val MODE_CHINESE = 2

        /** 浅色模式颜色 */
        private const val BG_COLOR_LIGHT = "#FFFFFF"
        private const val SYMBOL_TEXT_COLOR_LIGHT = "#333333"
        private const val SYMBOL_BG_LIGHT = "#F0F2F5"
        private const val EXPAND_ICON_COLOR_LIGHT = "#888888"
        private const val DIVIDER_COLOR_LIGHT = "#E0E0E0"

        /** 暗色模式颜色 */
        private const val BG_COLOR_DARK = "#1A1A2E"
        private const val SYMBOL_TEXT_COLOR_DARK = "#FFFFFF"
        private const val SYMBOL_BG_DARK = "#2D2D44"
        private const val EXPAND_ICON_COLOR_DARK = "#AAAAAA"
        private const val DIVIDER_COLOR_DARK = "#3A3A4A"
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setupViews()
        updateSymbols()
    }

    // ============================================================
    // 视图初始化
    // ============================================================

    private fun setupViews() {
        val density = resources.displayMetrics.density

        // 设置高度 40dp
        val heightPx = (40 * density).toInt()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, heightPx)

        // 内边距
        setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)

        // ---- 符号容器（左侧，占主要空间） ----
        symbolContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        addView(symbolContainer)

        // ---- 弹性间距 ----
        val spacer = View(context)
        spacer.layoutParams = LayoutParams(0, 1, 1f)
        addView(spacer)

        // ---- 分隔线 ----
        dividerView = View(context)
        dividerView.layoutParams = LayoutParams((1 * density).toInt(), (20 * density).toInt()).apply {
            marginEnd = (6 * density).toInt()
        }
        addView(dividerView)

        // ---- 右上角 🪪 身份证小图标 ----
        expandIcon = TextView(context).apply {
            text = "🪪"
            textSize = 16f
            gravity = Gravity.CENTER
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

            setOnClickListener {
                onExpandListener?.invoke()
            }
        }
        addView(expandIcon)
    }

    // ============================================================
    // 符号管理
    // ============================================================

    /**
     * 获取当前模式对应的符号列表。
     * 中文模式包含常用标点符号（逗号、句号、顿号等），
     * 用户可点击直接输入，或再次点击对应符号按钮展开完整面板。
     */
    private fun getSymbolsForMode(mode: Int): List<String> {
        return when (mode) {
            MODE_UYGHUR -> listOf("@", "!", "؟", "-", "…")
            MODE_ENGLISH -> listOf("…", "-", "?", "!", "@", "/", ":", "(", ")")
            MODE_CHINESE -> listOf("，", "。", "、", "；", "：", "？", "！", "…", "—", "～", "·", "@")
            else -> listOf("，", "。", "、", "；", "：", "？", "！", "…", "—", "～", "·", "@")
        }
    }

    /**
     * 更新符号显示。
     */
    private fun updateSymbols() {
        symbolContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val symbols = getSymbolsForMode(currentMode)

        for (symbol in symbols) {
            val symbolBtn = TextView(context).apply {
                text = symbol
                textSize = 18f
                gravity = Gravity.CENTER
                val padH = (16 * density).toInt()
                val padV = (6 * density).toInt()
                setPadding(padH, padV, padH, padV)

                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (4 * density).toInt()
                }
                layoutParams = lp

                setOnClickListener {
                    onSymbolSelectListener?.invoke(symbol)
                }
            }
            symbolContainer.addView(symbolBtn)
        }

        applyTheme()
    }

    // ============================================================
    // 主题
    // ============================================================

    /**
     * 设置暗色模式。
     * @param dark true 为暗色模式，false 为浅色模式
     */
    fun setDarkMode(dark: Boolean) {
        isDarkMode = dark
        applyTheme()
    }

    private fun applyTheme() {
        val density = resources.displayMetrics.density
        val bgColor = if (isDarkMode) BG_COLOR_DARK else BG_COLOR_LIGHT
        val symbolTextColor = if (isDarkMode) SYMBOL_TEXT_COLOR_DARK else SYMBOL_TEXT_COLOR_LIGHT
        val symbolBgColor = if (isDarkMode) SYMBOL_BG_DARK else SYMBOL_BG_LIGHT
        val expandIconColor = if (isDarkMode) EXPAND_ICON_COLOR_DARK else EXPAND_ICON_COLOR_LIGHT
        val dividerColor = if (isDarkMode) DIVIDER_COLOR_DARK else DIVIDER_COLOR_LIGHT

        setBackgroundColor(Color.parseColor(bgColor))

        // 更新符号按钮样式 — 每个按钮独立 Drawable 避免状态回调冲突
        for (i in 0 until symbolContainer.childCount) {
            val child = symbolContainer.getChildAt(i)
            if (child is TextView) {
                child.setTextColor(Color.parseColor(symbolTextColor))
                val bgDrawable = GradientDrawable()
                bgDrawable.setColor(Color.parseColor(symbolBgColor))
                bgDrawable.cornerRadius = 6f * density
                child.background = bgDrawable
            }
        }

        // 更新展开图标
        expandIcon.setTextColor(Color.parseColor(expandIconColor))

        // 更新分隔线颜色
        dividerView.setBackgroundColor(Color.parseColor(dividerColor))
    }

    // ============================================================
    // 公开方法
    // ============================================================

    /**
     * 设置当前输入模式。
     * @param mode 0=维语, 1=英文, 2=中文
     */
    fun setCurrentMode(mode: Int) {
        if (mode == currentMode) return
        currentMode = when (mode) {
            MODE_UYGHUR, MODE_ENGLISH, MODE_CHINESE -> mode
            else -> MODE_CHINESE
        }
        updateSymbols()
    }

    /**
     * 设置符号选择监听器。
     * 当用户点击某个符号时触发。
     *
     * @param listener 符号回调，参数为选中的符号文本
     */
    fun setOnSymbolSelectListener(listener: (String) -> Unit) {
        onSymbolSelectListener = listener
    }

    /**
     * 设置展开监听器。
     * 当用户点击右上角 🪪 身份证图标时触发，用于展开完整符号大页面。
     *
     * @param listener 展开回调
     */
    fun setOnExpandListener(listener: () -> Unit) {
        onExpandListener = listener
    }
}
