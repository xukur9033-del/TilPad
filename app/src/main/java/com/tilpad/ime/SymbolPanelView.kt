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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 综合符号面板视图，继承自 [LinearLayout]。
 *
 * 布局：
 * - 左侧固定列：常用数学运算符（+ − × ÷ = ≠ ≈ ≤ ≥ √ ∫ ∞ π）。
 * - 中间主区域：当前分类的符号网格（GridView + BaseAdapter）。
 * - 右侧竖排分类目录（15 个分类，可滚动）。
 * - 底部 3 个按钮：返回键盘、删除、🔒锁定。
 *
 * 锁定逻辑：
 * - 未锁定时，选中符号后会自动回调返回键盘（[setOnBackListener]）。
 * - 锁定后，可连续多选符号而不返回键盘。
 *
 * 暗色模式：通过 [setDarkMode] 切换。
 *
 * 使用示例：
 * ```
 * val panel = SymbolPanelView(context)
 * panel.setOnSymbolSelectListener { sym -> inputConnection.commitText(sym, 1) }
 * panel.setOnBackListener { switchToKeyboard() }
 * panel.setOnDeleteListener { inputConnection.deleteSurroundingText(1, 0) }
 * panel.setDarkMode(true)
 * ```
 */
class SymbolPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // ============================================================
    // 颜色配置（浅色模式使用更轻盈的色调）
    // ============================================================
    private val colorBgLight = Color.parseColor("#FAFAFA")
    private val colorBgDark = Color.parseColor("#1A1A2E")
    private val colorSidebarLight = Color.parseColor("#F0F0F0")
    private val colorSidebarDark = Color.parseColor("#222238")
    private val colorCatStripLight = Color.parseColor("#F0F0F0")
    private val colorCatStripDark = Color.parseColor("#222238")
    private val colorAccentLight = Color.parseColor("#2563EB")
    private val colorAccentDark = Color.parseColor("#3AADEE")
    private val colorTextLight = Color.parseColor("#555555")
    private val colorTextDark = Color.parseColor("#CCCCCC")
    private val colorCellLight = Color.parseColor("#FFFFFF")
    private val colorCellDark = Color.parseColor("#262638")
    private val colorFuncLight = Color.parseColor("#E0E0E0")
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
    private lateinit var gridView: GridView
    private lateinit var sidebarContainer: LinearLayout
    private lateinit var categoryContainer: LinearLayout
    private lateinit var lockButton: TextView
    private val categoryViews = mutableListOf<TextView>()
    private val sidebarViews = mutableListOf<TextView>()
    private val bottomButtons = mutableListOf<TextView>()
    private lateinit var symbolAdapter: SymbolAdapter

    // ============================================================
    // 回调
    // ============================================================
    private var onSymbolSelectListener: ((String) -> Unit)? = null
    private var onBackListener: (() -> Unit)? = null
    private var onDeleteListener: (() -> Unit)? = null

    // ============================================================
    // 左侧固定列：数学运算符
    // ============================================================
    private val mathOperators = listOf(
        "+", "−", "×", "÷", "=", "≠", "≈", "≤", "≥", "√", "∫", "∞", "π"
    )

    // ============================================================
    // 符号分类数据：分类名 -> 符号列表（共 15 个分类）
    // ============================================================
    private val symbolData: List<Pair<String, List<String>>> = listOf(
        "常用符号" to listOf(
            "，", "。", "、", "；", "：", "？", "！", "“", "”", "‘", "’",
            "…", "—", "～", "·", "【", "】", "《", "》", "「", "」", "『", "』",
            "(", ")", "[", "]", "{", "}", "〈", "〉", "␣"
        ),
        "中文标点" to listOf(
            "的", "了", "一", "是", "在", "不", "了", "有", "和", "人",
            "这", "中", "大", "为", "上", "国", "我", "以", "要", "他",
            "时", "来", "用", "们", "生", "到", "作", "地", "于", "出",
            "就", "分", "对", "成", "会", "可", "主", "发", "年", "动",
            "同", "工", "也"
        ),
        "英文符号" to listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
        ),
        "拼音声调" to listOf(
            "ā", "á", "ǎ", "à", "ē", "é", "ě", "è",
            "ī", "í", "ǐ", "ì", "ō", "ó", "ǒ", "ò",
            "ū", "ú", "ǔ", "ù", "ǖ", "ǘ", "ǚ", "ǜ", "ü"
        ),
        "网络域名" to listOf(
            ".com", ".cn", ".net", ".org", ".edu", ".gov", ".mil",
            ".info", ".xyz", ".top", ".vip", ".http", "https://", "www."
        ),
        "邮箱后缀" to listOf(
            "@qq.com", "@163.com", "@126.com", "@sina.com",
            "@gmail.com", "@outlook.com", "@hotmail.com", "@yahoo.com"
        ),
        "特殊符号" to listOf(
            "★", "☆", "☉", "☎", "☢", "☣", "☮", "☯", "☸", "✡", "✝", "☦", "☪", "🕎",
            "♌", "♍", "♎", "♏", "♐", "♑", "♒", "♓", "♈", "♉", "♊", "♋"
        ),
        "颜文字" to listOf(
            "(◕ᴗ◕✿)", "(❁´◡`❁)", "(●'◡'●)", "ヾ(≧▽≦*)o", "o(*￣▽￣*)o",
            "(｡◕‿◕｡)", "ヾ(≧▽≦*)o", "╮(╯▽╰)╭", "(✿◡‿◡)", "(*^▽^*)",
            "(｡♥‿♥｡)", "ᓚᘏᗢ"
        ),
        "数学符号" to listOf(
            "+", "−", "×", "÷", "=", "≠", "≈", "≤", "≥", "√", "∫", "∞", "π",
            "∑", "∏", "∈", "∉", "∪", "∩", "⊂", "⊃", "∅", "∞", "∇", "∂",
            "∝", "∠", "⊥", "≡", "≅", "≈", "≠", "±", "∓", "∔"
        ),
        "数字序号" to listOf(
            "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩", "⑪", "⑫",
            "⒈", "⒉", "⒊", "⒋", "⒌", "⒍", "⒎", "⒏", "⒐", "⒑",
            "⑴", "⑵", "⑶", "⑷", "⑸", "⑹", "⑺", "⑻", "⑼", "⑽",
            "⒜", "⒝", "⒞", "⒟", "⒠", "⒡"
        ),
        "希腊字母" to listOf(
            "α", "β", "γ", "δ", "ε", "ζ", "η", "θ", "ι", "κ", "λ", "μ",
            "ν", "ξ", "ο", "π", "ρ", "σ", "τ", "υ", "φ", "χ", "ψ", "ω",
            "Α", "Β", "Γ", "Δ", "Ε", "Ζ", "Η", "Θ", "Ι", "Κ", "Λ", "Μ",
            "Ν", "Ξ", "Ο", "Π", "Ρ", "Σ", "Τ", "Υ", "Φ", "Χ", "Ψ", "Ω"
        ),
        "中文数字" to listOf(
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九",
            "十", "百", "千", "万", "亿",
            "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖", "拾",
            "佰", "仟", "万", "亿", "两"
        ),
        "日文假名" to listOf(
            "あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ",
            "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と",
            "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ",
            "ま", "み", "む", "め", "も", "や", "ゆ", "よ",
            "ら", "り", "る", "れ", "ろ", "わ", "を", "ん",
            "ア", "イ", "ウ", "エ", "オ", "カ", "キ", "ク", "ケ", "コ",
            "サ", "シ", "ス", "セ", "ソ"
        ),
        "拉丁特殊" to listOf(
            "À", "Á", "Â", "Ã", "Ä", "Å", "Æ", "Ç",
            "È", "É", "Ê", "Ë", "Ì", "Í", "Î", "Ï",
            "Ð", "Ñ", "Ò", "Ó", "Ô", "Õ", "Ö", "Ø",
            "Ù", "Ú", "Û", "Ü", "Ý", "Þ", "ß",
            "à", "á", "â", "ã", "ä", "å", "æ", "ç",
            "è", "é", "ê", "ë", "ì", "í", "î", "ï"
        ),
        "装饰花纹" to listOf(
            "𓀀", "𓀁", "𓀂", "𓀃", "𓀄", "𓀅", "𓀆", "𓀇", "𓀈", "𓀉", "𓀊",
            "✦", "✧", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "✰",
            "❂", "❄", "❅", "❆", "✿", "❀", "❁", "❂", "❃", "❄", "❅"
        )
    )

    // ============================================================
    // 初始化
    // ============================================================
    init {
        orientation = VERTICAL
        buildLayout()
        buildSidebar()
        buildCategoryList()
        symbolAdapter = SymbolAdapter()
        gridView.adapter = symbolAdapter
        showCategory(0)
        applyColors()
    }

    // ============================================================
    // 构建布局：左侧栏 + 中间网格 + 右侧分类 + 底部按钮
    // ============================================================
    private fun buildLayout() {
        // ---- 主区域：左侧数学栏 + 中间符号网格 + 右侧分类目录 ----
        val mainArea = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        // 左侧固定列：数学运算符（可垂直滚动）
        val sidebarScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        sidebarContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        sidebarScroll.addView(sidebarContainer)

        // 中间符号网格
        gridView = GridView(context).apply {
            numColumns = 6
            horizontalSpacing = dp(2)
            verticalSpacing = dp(2)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        // 选中符号回调 + 锁定逻辑
        gridView.setOnItemClickListener { _, _, position, _ ->
            if (position in currentSymbols.indices) {
                val symbol = currentSymbols[position]
                onSymbolSelectListener?.invoke(symbol)
                if (!isLocked) {
                    onBackListener?.invoke()
                }
            }
        }

        // 右侧竖排分类目录（可垂直滚动）
        val catScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        categoryContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(4))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        catScroll.addView(categoryContainer)

        mainArea.addView(sidebarScroll)
        mainArea.addView(gridView)
        mainArea.addView(catScroll)

        // ---- 底部按钮栏：返回键盘、删除、🔒锁定 ----
        val bottomBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val backBtn = makeBottomButton("返回键盘") { onBackListener?.invoke() }
        val deleteBtn = makeBottomButton("删除") { onDeleteListener?.invoke() }
        lockButton = makeBottomButton("🔒锁定") { toggleLock() }
        bottomBar.addView(backBtn)
        bottomBar.addView(deleteBtn)
        bottomBar.addView(lockButton)

        addView(mainArea)
        addView(bottomBar)
    }

    /** 创建底部按钮 TextView，自动登记到 [bottomButtons] 以便暗色模式重新着色 */
    private fun makeBottomButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 13f
            maxLines = 1
            setPadding(dp(4), dp(10), dp(4), dp(10))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }.also { bottomButtons.add(it) }
    }

    // ============================================================
    // 构建左侧数学运算符栏
    // ============================================================
    private fun buildSidebar() {
        sidebarContainer.removeAllViews()
        sidebarViews.clear()
        mathOperators.forEach { op ->
            val tv = TextView(context).apply {
                text = op
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                textSize = 18f
                maxLines = 1
                setPadding(dp(2), dp(8), dp(2), dp(8))
                isClickable = true
                setOnClickListener {
                    onSymbolSelectListener?.invoke(op)
                    if (!isLocked) {
                        onBackListener?.invoke()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            sidebarContainer.addView(tv)
            sidebarViews.add(tv)
        }
    }

    // ============================================================
    // 构建右侧分类目录
    // ============================================================
    private fun buildCategoryList() {
        categoryContainer.removeAllViews()
        categoryViews.clear()
        symbolData.forEachIndexed { index, (name, _) ->
            val cat = TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 11f
                maxLines = 2
                setPadding(dp(4), dp(10), dp(4), dp(10))
                isClickable = true
                setOnClickListener { showCategory(index) }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            categoryContainer.addView(cat)
            categoryViews.add(cat)
        }
    }

    // ============================================================
    // 切换分类
    // ============================================================
    private fun showCategory(index: Int) {
        currentCategoryIndex = index
        currentSymbols = symbolData[index].second
        symbolAdapter.notifyDataSetChanged()
        updateCategoryColors()
        gridView.setSelection(0)
    }

    private fun updateCategoryColors() {
        categoryViews.forEachIndexed { i, tv ->
            val selected = i == currentCategoryIndex
            tv.setBackgroundColor(if (selected) accentColor else Color.TRANSPARENT)
            tv.setTextColor(if (selected) Color.WHITE else textColor)
        }
    }

    // ============================================================
    // 锁定逻辑
    // ============================================================
    private fun toggleLock() {
        isLocked = !isLocked
        lockButton.text = if (isLocked) "🔒已锁定" else "🔒锁定"
        applyLockColor()
    }

    private fun applyLockColor() {
        lockButton.setBackgroundColor(if (isLocked) lockActiveColor else funcBtnColor)
        lockButton.setTextColor(if (isLocked) Color.WHITE else textColor)
    }

    /** 外部设置锁定状态 */
    fun setLocked(locked: Boolean) {
        if (isLocked != locked) toggleLock()
    }

    /** 当前是否锁定（可连续多选） */
    val isSymbolLocked: Boolean
        get() = isLocked

    // ============================================================
    // 暗色模式 / 颜色（按 isDarkMode 取值）
    // ============================================================
    private val bgColor: Int
        get() = if (isDarkMode) colorBgDark else colorBgLight
    private val sidebarColor: Int
        get() = if (isDarkMode) colorSidebarDark else colorSidebarLight
    private val catStripColor: Int
        get() = if (isDarkMode) colorCatStripDark else colorCatStripLight
    private val accentColor: Int
        get() = if (isDarkMode) colorAccentDark else colorAccentLight
    private val textColor: Int
        get() = if (isDarkMode) colorTextDark else colorTextLight
    private val cellColor: Int
        get() = if (isDarkMode) colorCellDark else colorCellLight
    private val funcBtnColor: Int
        get() = if (isDarkMode) colorFuncDark else colorFuncLight
    private val lockActiveColor: Int
        get() = if (isDarkMode) colorLockActiveDark else colorLockActiveLight

    private fun applyColors() {
        setBackgroundColor(bgColor)
        // 左侧数学栏
        sidebarContainer.setBackgroundColor(sidebarColor)
        sidebarViews.forEach { tv ->
            tv.setBackgroundColor(cellColor)
            tv.setTextColor(textColor)
        }
        // 中间网格
        gridView.setBackgroundColor(bgColor)
        // 右侧分类目录
        categoryContainer.setBackgroundColor(catStripColor)
        updateCategoryColors()
        // 底部按钮（除锁外）重新着色
        bottomButtons.forEach { btn ->
            if (btn !== lockButton) {
                btn.setBackgroundColor(funcBtnColor)
                btn.setTextColor(textColor)
            }
        }
        applyLockColor()
        symbolAdapter.notifyDataSetChanged()
    }

    // ============================================================
    // 公开 API
    // ============================================================

    /** 设置符号选中回调，参数为被选中的符号字符串 */
    fun setOnSymbolSelectListener(listener: (String) -> Unit) {
        onSymbolSelectListener = listener
    }

    /** 设置返回键盘回调（未锁定时选完符号或点击"返回键盘"触发） */
    fun setOnBackListener(listener: () -> Unit) {
        onBackListener = listener
    }

    /** 设置删除回调（点击"删除"按钮触发） */
    fun setOnDeleteListener(listener: () -> Unit) {
        onDeleteListener = listener
    }

    /** 切换暗色模式 */
    fun setDarkMode(dark: Boolean) {
        isDarkMode = dark
        applyColors()
    }

    /** 切换到指定分类（0 ~ 14） */
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
            // 根据符号长度自适应字号
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
