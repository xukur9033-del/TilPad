package com.tilpad.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 综合符号面板视图 — 上下滑动式布局。
 *
 * 布局：
 * - 顶栏：返回按钮 + 删除按钮 + 🔒锁定按钮。
 * - 中间主区域：当前分类的符号网格（GridView 上下滑动）。
 * - 底部：横向分类标签栏（可滚动切换）。
 *
 * 锁定逻辑：
 * - 未锁定时，选中符号后会自动回调返回键盘。
 * - 锁定后，可连续多选符号而不返回键盘。
 *
 * 暗色模式：通过 [setDarkMode] 切换。
 */
class SymbolPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ============================================================
    // 颜色配置
    // ============================================================
    private val colorBgLight = Color.parseColor("#FFFFFF")
    private val colorBgDark = Color.parseColor("#1A1A2E")
    private val colorTopBarLight = Color.parseColor("#F2F2F4")
    private val colorTopBarDark = Color.parseColor("#2D2D44")
    private val colorTabStripLight = Color.parseColor("#F2F2F4")
    private val colorTabStripDark = Color.parseColor("#2D2D44")
    private val colorAccentLight = Color.parseColor("#2563EB")
    private val colorAccentDark = Color.parseColor("#3AADEE")
    private val colorTextLight = Color.parseColor("#333333")
    private val colorTextDark = Color.parseColor("#E8E8E8")
    private val colorCellLight = Color.parseColor("#FAFAFA")
    private val colorCellDark = Color.parseColor("#262638")
    private val colorFuncLight = Color.parseColor("#E4E6EB")
    private val colorFuncDark = Color.parseColor("#3A3A4A")
    private val colorLockActiveLight = Color.parseColor("#FF9800")
    private val colorLockActiveDark = Color.parseColor("#FFB300")

    // ============================================================
    // 状态
    // ============================================================
    private var isDarkMode = false
    private var isLocked = false
    private var currentCategoryIndex = 0
    private var currentSymbols: List<String> = emptyList()

    // ============================================================
    // 视图引用
    // ============================================================
    private lateinit var topBar: LinearLayout
    private lateinit var backBtn: TextView
    private lateinit var deleteBtn: TextView
    private lateinit var lockButton: TextView
    private lateinit var gridView: GridView
    private lateinit var tabScrollView: HorizontalScrollView
    private lateinit var tabContainer: LinearLayout
    private val tabViews = mutableListOf<TextView>()
    private val symbolAdapter = SymbolAdapter()

    // ============================================================
    // 回调
    // ============================================================
    private var onSymbolSelectListener: ((String) -> Unit)? = null
    private var onBackListener: (() -> Unit)? = null
    private var onDeleteListener: (() -> Unit)? = null

    // ============================================================
    // 符号分类数据：分类名 -> 符号列表
    // ============================================================
    private val symbolData: List<Pair<String, List<String>>> = listOf(
        "常用标点" to listOf(
            "，", "。", "、", "；", "：", "？", "！", """, """, "'", "'",
            "…", "—", "～", "·", "【", "】", "《", "》", "「", "」", "『", "』",
            "(", ")", "[", "]", "{", "}", "〈", "〉", "␣", "・", "→", "←", "↑", "↓"
        ),
        "中文" to listOf(
            "的", "了", "一", "是", "在", "不", "有", "和", "人", "这",
            "中", "大", "为", "上", "国", "我", "以", "要", "他", "时",
            "来", "用", "们", "生", "到", "作", "地", "于", "出", "就",
            "分", "对", "成", "会", "可", "主", "发", "年", "动", "同",
            "工", "也", "能", "前", "这", "些", "么", "那", "之", "于"
        ),
        "英文" to listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
        ),
        "数字" to listOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩",
            "⑪", "⑫", "⒈", "⒉", "⒊", "⒋", "⒌", "⒍", "⒎", "⒏",
            "⒐", "⒑", "½", "⅓", "⅔", "¼", "¾", "⅕", "⅖", "⅗",
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "十", "百", "千", "万", "亿", "壹", "贰", "叁", "肆", "伍"
        ),
        "数学" to listOf(
            "+", "−", "×", "÷", "=", "≠", "≈", "≤", "≥", "√",
            "∫", "∞", "π", "∑", "∏", "∈", "∉", "∪", "∩", "⊂",
            "⊃", "∅", "∇", "∂", "∝", "∠", "⊥", "≡", "≅", "±",
            "∓", "∔", "∴", "∵", "⊕", "⊗", "⊙", "⊙", "⟂", "∥",
            "℃", "℉", "‰", "′", "″", "‴", "○", "△", "▽", "□",
            "◇", "☆", "★", "✦", "✧", "✩", "✪", "✫", "✬", "✭"
        ),
        "特殊" to listOf(
            "★", "☆", "☉", "☎", "☢", "☣", "☮", "☯", "☸", "✡",
            "✝", "☦", "☪", "🕎", "♌", "♍", "♎", "♏", "♐", "♑",
            "♒", "♓", "♈", "♉", "♊", "♋", "♪", "♫", "♬", "♩",
            "♭", "♮", "♯", "✓", "✗", "✔", "✘", "☜", "☞", "☝",
            "✍", "✁", "✂", "⚡", "☀", "☁", "☂", "☃", "☄", "★"
        ),
        "拼音声调" to listOf(
            "ā", "á", "ǎ", "à", "ē", "é", "ě", "è",
            "ī", "í", "ǐ", "ì", "ō", "ó", "ǒ", "ò",
            "ū", "ú", "ǔ", "ù", "ǖ", "ǘ", "ǚ", "ǜ", "ü",
            "Ā", "Á", "Ǎ", "À", "Ē", "É", "Ě", "È",
            "Ī", "Í", "Ǐ", "Ì", "Ō", "Ó", "Ǒ", "Ò",
            "Ū", "Ú", "Ǔ", "Ù", "Ǖ", "Ǘ", "Ǚ", "Ǜ", "Ü"
        ),
        "网络" to listOf(
            ".com", ".cn", ".net", ".org", ".edu", ".gov", ".mil",
            ".info", ".xyz", ".top", ".vip", ".http", "https://", "www.",
            "@qq.com", "@163.com", "@126.com", "@sina.com",
            "@gmail.com", "@outlook.com", "@hotmail.com", "@yahoo.com",
            "www.baidu.com", "www.google.com", "www.bing.com"
        ),
        "希腊" to listOf(
            "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ",
            "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω",
            "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ", "Μ",
            "Ν", "Ξ", "Ο", "Π", "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω"
        ),
        "拉丁" to listOf(
            "À", "Á", "Â", "Ã", "Ä", "Å", "Æ", "Ç",
            "È", "É", "Ê", "Ë", "Ì", "Í", "Î", "Ï",
            "Ð", "Ñ", "Ò", "Ó", "Ô", "Õ", "Ö", "Ø",
            "Ù", "Ú", "Û", "Ü", "Ý", "Þ", "ß",
            "à", "á", "â", "ã", "ä", "å", "æ", "ç",
            "è", "é", "ê", "ë", "ì", "í", "î", "ï"
        ),
        "日文" to listOf(
            "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ",
            "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と",
            "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ",
            "ま", "み", "む", "め", "も", "や", "ゆ", "よ",
            "ら", "り", "る", "れ", "ろ", "わ", "を", "ん",
            "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
            "サ", "シ", "ス", "セ", "ソ", "タ", "チ", "ツ", "テ", "ト"
        ),
        "装饰" to listOf(
            "✦", "✧", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "✰",
            "❂", "❄", "❅", "❆", "✿", "❀", "❁", "❃", "❋", "✼",
            "𓀀", "𓀁", "𓀂", "𓀃", "𓀄", "𓀅", "𓀆", "𓀇", "𓀈", "𓀉",
            "𓀊", "꧁", "꧂", "༺", "༻", "༼", "༽", "ᕕ", "ᕗ", "꒰",
            "꒱", "✁", "✂", "✃", "✄", "✆", "✇", "✈", "✉", "⚝"
        )
    )

    // ============================================================
    // 初始化
    // ============================================================
    init {
        buildLayout()
        buildTabs()
        gridView.adapter = symbolAdapter
        showCategory(0)
        applyColors()
    }

    // ============================================================
    // 构建布局：顶栏 + GridView + 底部分类标签
    // ============================================================
    private fun buildLayout() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---- 顶栏：返回 + 删除 + 锁定 ----
        topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)
            )
        }
        backBtn = TextView(context).apply {
            text = "←"
            gravity = Gravity.CENTER
            textSize = 20f
            isClickable = true
            setOnClickListener { onBackListener?.invoke() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
        }
        deleteBtn = TextView(context).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            textSize = 18f
            isClickable = true
            setOnClickListener { onDeleteListener?.invoke() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
        }
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
        }
        lockButton = TextView(context).apply {
            text = "🔒"
            gravity = Gravity.CENTER
            textSize = 16f
            isClickable = true
            setOnClickListener { toggleLock() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
        }
        topBar.addView(backBtn)
        topBar.addView(deleteBtn)
        topBar.addView(spacer)
        topBar.addView(lockButton)

        // ---- 中间符号网格（上下滑动） ----
        gridView = GridView(context).apply {
            numColumns = 6
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(2)
            verticalSpacing = dp(2)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        gridView.setOnItemClickListener { _, _, position, _ ->
            if (position in currentSymbols.indices) {
                val symbol = currentSymbols[position]
                onSymbolSelectListener?.invoke(symbol)
                if (!isLocked) {
                    onBackListener?.invoke()
                }
            }
        }

        // ---- 底部分类标签栏（横向可滚动） ----
        tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)
            )
        }
        tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
            )
        }
        tabScrollView.addView(tabContainer)

        root.addView(topBar)
        root.addView(gridView)
        root.addView(tabScrollView)
        addView(root)
    }

    // ============================================================
    // 构建底部分类标签
    // ============================================================
    private fun buildTabs() {
        tabContainer.removeAllViews()
        tabViews.clear()
        symbolData.forEachIndexed { index, (name, _) ->
            val tab = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 13f
                setPadding(dp(14), 0, dp(14), 0)
                isClickable = true
                setOnClickListener { showCategory(index) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = dp(4)
                }
            }
            tabContainer.addView(tab)
            tabViews.add(tab)
        }
    }

    // ============================================================
    // 切换分类
    // ============================================================
    private fun showCategory(index: Int) {
        if (index !in symbolData.indices) return
        currentCategoryIndex = index
        currentSymbols = symbolData[index].second
        symbolAdapter.notifyDataSetChanged()
        gridView.setSelection(0)
        updateTabColors()
        scrollTabIntoView(index)
    }

    private fun updateTabColors() {
        tabViews.forEachIndexed { i, tab ->
            val selected = i == currentCategoryIndex
            tab.setBackgroundColor(if (selected) accentColor else Color.TRANSPARENT)
            tab.setTextColor(if (selected) Color.WHITE else textColor)
        }
    }

    private fun scrollTabIntoView(index: Int) {
        tabScrollView.post {
            if (index !in tabViews.indices) return@post
            val tab = tabViews[index]
            val scrollX = tab.left - tabScrollView.width / 2 + tab.width / 2
            tabScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    // ============================================================
    // 锁定逻辑
    // ============================================================
    private fun toggleLock() {
        isLocked = !isLocked
        lockButton.text = if (isLocked) "🔓" else "🔒"
        applyLockColor()
    }

    private fun applyLockColor() {
        lockButton.setBackgroundColor(if (isLocked) lockActiveColor else funcBtnColor)
        lockButton.setTextColor(if (isLocked) Color.WHITE else textColor)
    }

    fun setLocked(locked: Boolean) {
        if (isLocked != locked) toggleLock()
    }

    val isSymbolLocked: Boolean
        get() = isLocked

    // ============================================================
    // 暗色模式 / 颜色
    // ============================================================
    private val bgColor: Int get() = if (isDarkMode) colorBgDark else colorBgLight
    private val topBarColor: Int get() = if (isDarkMode) colorTopBarDark else colorTopBarLight
    private val tabStripColor: Int get() = if (isDarkMode) colorTabStripDark else colorTabStripLight
    private val accentColor: Int get() = if (isDarkMode) colorAccentDark else colorAccentLight
    private val textColor: Int get() = if (isDarkMode) colorTextDark else colorTextLight
    private val cellColor: Int get() = if (isDarkMode) colorCellDark else colorCellLight
    private val funcBtnColor: Int get() = if (isDarkMode) colorFuncDark else colorFuncLight
    private val lockActiveColor: Int get() = if (isDarkMode) colorLockActiveDark else colorLockActiveLight

    private fun applyColors() {
        setBackgroundColor(bgColor)
        topBar.setBackgroundColor(topBarColor)
        backBtn.setBackgroundColor(funcBtnColor)
        backBtn.setTextColor(textColor)
        deleteBtn.setBackgroundColor(funcBtnColor)
        deleteBtn.setTextColor(textColor)
        tabContainer.setBackgroundColor(tabStripColor)
        gridView.setBackgroundColor(bgColor)
        updateTabColors()
        applyLockColor()
        symbolAdapter.notifyDataSetChanged()
    }

    // ============================================================
    // 公开 API
    // ============================================================

    fun setOnSymbolSelectListener(listener: (String) -> Unit) {
        onSymbolSelectListener = listener
    }

    fun setOnBackListener(listener: () -> Unit) {
        onBackListener = listener
    }

    fun setOnDeleteListener(listener: () -> Unit) {
        onDeleteListener = listener
    }

    fun setDarkMode(dark: Boolean) {
        isDarkMode = dark
        applyColors()
    }

    fun setCategory(index: Int) {
        if (index in symbolData.indices) showCategory(index)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ============================================================
    // 符号网格适配器
    // ============================================================
    private inner class SymbolAdapter : BaseAdapter() {
        override fun getCount(): Int = currentSymbols.size
        override fun getItem(position: Int): Any = currentSymbols[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(2), dp(4), dp(2), dp(4))
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
                )
            }
            val symbol = currentSymbols[position]
            tv.text = symbol
            tv.textSize = when {
                symbol.length <= 2 -> 18f
                symbol.length <= 5 -> 14f
                else -> 12f
            }
            tv.setBackgroundColor(cellColor)
            tv.setTextColor(textColor)
            return tv
        }
    }
}
