package com.tilpad.ime

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * QuickToolbarView — 快捷工具栏视图。
 *
 * 紧贴键盘上方显示，横向滚动，提供：
 * 1. 粘贴按钮：读取系统剪贴板内容并触发 [onPasteListener]，由服务层提交到 InputConnection
 * 2. 快捷短语（完整 CRUD）：
 *    - 点击短语直接输入（触发 [onPhraseListener]）
 *    - 长按任意短语或点击右侧 ✎ 图标进入编辑模式，每个短语显示 × 删除按钮
 *    - 编辑模式下点击 × 删除对应短语
 *    - 点击「添加」按钮弹出 AlertDialog 输入自定义短语
 *    - 短语列表通过 SharedPreferences（key = "tilpad_phrases"）以逗号分隔持久化
 *
 * 暗色模式可通过 [setDarkMode] 切换。
 *
 * 布局结构（均代码构建）：
 * [粘贴] | [横向滚动: 短语1 短语2 ... 添加] | [✎]
 */
class QuickToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** 粘贴回调 — 由 InputMethodService 实现，负责将剪贴板内容提交到 InputConnection */
    private var onPasteListener: (() -> Unit)? = null

    /** 快捷短语回调 — 参数为选中的短语文本 */
    private var onPhraseListener: ((String) -> Unit)? = null

    /** 暗色模式标记 */
    private var isDarkMode = false

    /** 编辑模式标记（true 时每个短语显示 × 删除按钮） */
    private var isEditMode = false

    /** SharedPreferences，用于持久化短语列表 */
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前短语列表（默认短语 + 自定义短语），增删后即时持久化 */
    private val phrases = loadPhrases().toMutableList()

    /** 粘贴按钮 */
    private lateinit var pasteButton: TextView

    /** 分隔线 */
    private lateinit var dividerView: View

    /** 编辑模式切换按钮 ✎ */
    private lateinit var editToggleButton: TextView

    /** 短语横向滚动容器 */
    private lateinit var scrollView: HorizontalScrollView

    /** 滚动容器内部的水平 LinearLayout，承载短语条目与「添加」按钮 */
    private lateinit var phrasesContainer: LinearLayout

    companion object {
        /** SharedPreferences 文件名 */
        private const val PREFS_NAME = "tilpad_quick_toolbar"

        /** 短语持久化 key */
        private const val KEY_PHRASES = "tilpad_phrases"

        /** 短语分隔符（逗号分隔） */
        private const val DELIMITER = ","

        /** 默认短语 */
        private val DEFAULT_PHRASES = listOf(
            "你好", "谢谢", "再见", "请问", "抱歉",
            "好的", "没问题", "稍等", "辛苦了", "不用谢"
        )

        // ---- 浅色模式颜色 ----
        private const val BG_COLOR_LIGHT = "#FFFFFF"
        private const val TEXT_COLOR_LIGHT = "#333333"
        private const val DIVIDER_COLOR_LIGHT = "#E0E0E0"
        private const val BTN_BG_LIGHT = "#F0F2F5"

        // ---- 暗色模式颜色 ----
        private const val BG_COLOR_DARK = "#1A1A2E"
        private const val TEXT_COLOR_DARK = "#FFFFFF"
        private const val DIVIDER_COLOR_DARK = "#3A3A4A"
        private const val BTN_BG_DARK = "#2D2D44"

        // ---- 强调色（添加按钮） ----
        private const val ADD_BG_LIGHT = "#007AFF"
        private const val ADD_BG_DARK = "#0A84FF"

        // ---- 编辑激活态颜色（✎ 激活） ----
        private const val EDIT_ACTIVE_LIGHT = "#FF9500"
        private const val EDIT_ACTIVE_DARK = "#FF9F0A"

        // ---- 删除按钮颜色 ----
        private const val DELETE_BG = "#FF3B30"
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setupViews()
        applyTheme()
    }

    // ============================================================
    // 视图初始化
    // ============================================================

    private fun setupViews() {
        val density = resources.displayMetrics.density

        // 整体高度 40dp
        val heightPx = (40 * density).toInt()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, heightPx)
        setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0)

        // ---- 粘贴按钮 ----
        pasteButton = createToolbarButton("粘贴")
        pasteButton.setOnClickListener { handlePasteClick() }
        addView(pasteButton)

        // ---- 分隔线 ----
        dividerView = createDivider()
        addView(dividerView)

        // ---- 横向滚动区域（短语 + 添加按钮） ----
        scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // scrollView 是根 LinearLayout 的子视图，需使用 LinearLayout.LayoutParams
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

            phrasesContainer = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // phrasesContainer 是 HorizontalScrollView(FrameLayout) 的子视图
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                val padV = (4 * density).toInt()
                setPadding((2 * density).toInt(), padV, (2 * density).toInt(), padV)
            }
            addView(phrasesContainer)
        }
        addView(scrollView)

        // ---- 编辑模式切换按钮 ✎ ----
        editToggleButton = createToolbarButton("✎")
        editToggleButton.setOnClickListener { toggleEditMode() }
        addView(editToggleButton)
    }

    /**
     * 创建工具栏按钮（粘贴 / ✎）。
     */
    private fun createToolbarButton(text: String): TextView {
        val density = resources.displayMetrics.density
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            val padH = (14 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (4 * density).toInt()
            }
        }
    }

    /**
     * 创建竖直分隔线。
     */
    private fun createDivider(): View {
        val density = resources.displayMetrics.density
        return View(context).apply {
            layoutParams = LayoutParams((1 * density).toInt(), (20 * density).toInt()).apply {
                marginEnd = (4 * density).toInt()
            }
        }
    }

    /**
     * 重建短语条目与「添加」按钮。
     * 在初始化、增删短语、切换编辑模式或主题时调用。
     */
    private fun rebuildPhraseChips() {
        phrasesContainer.removeAllViews()
        for (phrase in phrases) {
            phrasesContainer.addView(createPhraseChip(phrase))
        }
        phrasesContainer.addView(createAddButton())
    }

    /**
     * 创建单个短语条目（编辑模式下附带 × 删除按钮）。
     */
    private fun createPhraseChip(phrase: String): View {
        val density = resources.displayMetrics.density
        val textColor = if (isDarkMode) TEXT_COLOR_DARK else TEXT_COLOR_LIGHT
        val chipBg = if (isDarkMode) BTN_BG_DARK else BTN_BG_LIGHT

        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createButtonBackground(chipBg)
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * density).toInt()
            }
        }

        val textView = TextView(context).apply {
            text = phrase
            textSize = 13f
            setTextColor(Color.parseColor(textColor))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(textView)

        val deleteButton = TextView(context).apply {
            text = "×"
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            val size = (20 * density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                marginStart = (6 * density).toInt()
            }
            background = createCircleBackground(DELETE_BG)
            visibility = if (isEditMode) View.VISIBLE else View.GONE
            setOnClickListener { removePhrase(phrase) }
        }
        container.addView(deleteButton)

        // 点击短语：仅在非编辑模式下输入
        container.setOnClickListener {
            if (!isEditMode) {
                onPhraseListener?.invoke(phrase)
            }
        }

        // 长按进入编辑模式
        container.setOnLongClickListener {
            if (!isEditMode) {
                setEditMode(true)
            }
            true
        }

        return container
    }

    /**
     * 创建「添加」按钮（强调色背景）。
     */
    private fun createAddButton(): TextView {
        val density = resources.displayMetrics.density
        val addBg = if (isDarkMode) ADD_BG_DARK else ADD_BG_LIGHT
        return TextView(context).apply {
            text = "添加"
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            val padH = (12 * density).toInt()
            val padV = (6 * density).toInt()
            setPadding(padH, padV, padH, padV)
            background = createButtonBackground(addBg)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * density).toInt()
            }
            setOnClickListener { showAddPhraseDialog() }
        }
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
        val bgColor = if (isDarkMode) BG_COLOR_DARK else BG_COLOR_LIGHT
        val textColor = if (isDarkMode) TEXT_COLOR_DARK else TEXT_COLOR_LIGHT
        val dividerColor = if (isDarkMode) DIVIDER_COLOR_DARK else DIVIDER_COLOR_LIGHT
        val btnBgColor = if (isDarkMode) BTN_BG_DARK else BTN_BG_LIGHT

        setBackgroundColor(Color.parseColor(bgColor))

        pasteButton.apply {
            setTextColor(Color.parseColor(textColor))
            background = createButtonBackground(btnBgColor)
        }

        dividerView.setBackgroundColor(Color.parseColor(dividerColor))

        editToggleButton.apply {
            val active = isEditMode
            val bg = if (active) {
                if (isDarkMode) EDIT_ACTIVE_DARK else EDIT_ACTIVE_LIGHT
            } else {
                btnBgColor
            }
            setTextColor(Color.parseColor(if (active) "#FFFFFF" else textColor))
            background = createButtonBackground(bg)
        }

        // 重建短语条目以应用新主题色与编辑态
        rebuildPhraseChips()
    }

    /**
     * 创建按钮圆角背景。
     */
    private fun createButtonBackground(bgColor: String): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = 6f * density
        }
    }

    /**
     * 创建圆形背景（删除按钮）。
     */
    private fun createCircleBackground(bgColor: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(bgColor))
        }
    }

    // ============================================================
    // 编辑模式
    // ============================================================

    /** 切换编辑模式开关（由 ✎ 按钮调用） */
    private fun toggleEditMode() = setEditMode(!isEditMode)

    private fun setEditMode(value: Boolean) {
        if (isEditMode == value) return
        isEditMode = value
        applyTheme()
    }

    // ============================================================
    // 短语 CRUD + 持久化
    // ============================================================

    /**
     * 从 SharedPreferences 读取短语列表。
     * 首次使用（无记录）时返回默认短语。
     */
    private fun loadPhrases(): List<String> {
        val saved = prefs.getString(KEY_PHRASES, null) ?: return DEFAULT_PHRASES.toList()
        return saved.split(DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 将当前短语列表持久化到 SharedPreferences（逗号分隔）。
     */
    private fun savePhrases() {
        prefs.edit().putString(KEY_PHRASES, phrases.joinToString(DELIMITER)).apply()
    }

    /**
     * 删除指定短语并持久化。
     */
    private fun removePhrase(phrase: String) {
        if (phrases.remove(phrase)) {
            savePhrases()
            rebuildPhraseChips()
            Toast.makeText(context, "已删除「$phrase」", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 弹出 AlertDialog 输入自定义短语并添加。
     */
    private fun showAddPhraseDialog() {
        val density = resources.displayMetrics.density
        val editText = EditText(context).apply {
            hint = "输入自定义短语"
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
            val padH = (16 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
        }

        AlertDialog.Builder(context)
            .setTitle("添加短语")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                val text = editText.text.toString().trim()
                when {
                    text.isEmpty() -> Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show()
                    phrases.contains(text) -> Toast.makeText(context, "该短语已存在", Toast.LENGTH_SHORT).show()
                    else -> addPhrase(text)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 添加自定义短语、持久化并滚动到末尾。
     */
    private fun addPhrase(text: String) {
        phrases.add(text)
        savePhrases()
        rebuildPhraseChips()
        scrollView.post { scrollView.fullScroll(View.FOCUS_RIGHT) }
        Toast.makeText(context, "已添加「$text」", Toast.LENGTH_SHORT).show()
    }

    // ============================================================
    // 粘贴功能
    // ============================================================

    /**
     * 处理粘贴按钮点击 — 读取系统剪贴板内容。
     * 若剪贴板有文本，触发 [onPasteListener] 由服务层提交到输入框。
     */
    private fun handlePasteClick() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null) {
            Toast.makeText(context, "无法访问剪贴板", Toast.LENGTH_SHORT).show()
            return
        }

        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
            return
        }

        val text = clip.getItemAt(0).coerceToText(context).toString()
        if (text.isBlank()) {
            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 触发粘贴回调 — 服务层负责将内容提交到 InputConnection
        onPasteListener?.invoke()
    }

    // ============================================================
    // 公开回调方法
    // ============================================================

    /**
     * 设置粘贴监听器。
     * 当用户点击粘贴按钮且剪贴板有内容时触发。
     * 服务层应在此回调中通过 ClipboardHelper / InputConnection 将内容提交到输入框。
     *
     * @param listener 粘贴回调
     */
    fun setOnPasteListener(listener: () -> Unit) {
        onPasteListener = listener
    }

    /**
     * 设置快捷短语监听器。
     * 当用户点击某个短语时触发（仅在非编辑模式下）。
     *
     * @param listener 短语回调，参数为选中的短语文本
     */
    fun setOnPhraseListener(listener: (String) -> Unit) {
        onPhraseListener = listener
    }
}
