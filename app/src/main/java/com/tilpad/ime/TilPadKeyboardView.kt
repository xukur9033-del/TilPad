package com.tilpad.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import kotlin.math.abs

/**
 * TilPadKeyboardView v3 — 自绘 Canvas 键盘。
 *
 * Shift 逻辑：
 * - 仅 English 模式保留 shift：第1次临时大写，第2次锁定大写，第3次恢复小写
 * - 维语模式：移除 shift 按键
 * - 中文模式：原 shift 位置改为手写按钮
 *
 * 麦克风逻辑：
 * - 删除独立麦克风方块
 * - 空格键长按触发语音输入
 * - 空格键右下角小麦克风图标提示
 *
 * 回车键：蓝色背景白色箭头保留不变
 */
class TilPadKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Language { UYGHUR, PINYIN, ENGLISH }
    enum class ShiftState { NONE, TEMP_CAPS, LOCK_CAPS }
    enum class UyghurLayout { LAYOUT_32, LAYOUT_26 }
    enum class ChineseLayout { QWERTY_26, NINE_KEY, HANDWRITE }

    data class Key(
        val code: Int,
        val label: String,
        val outputText: String = "",
        val width: Float = 1f,
        val isFunctional: Boolean = false,
        val isSpecial: Boolean = false,
        val isSpace: Boolean = false,
        val isHandwrite: Boolean = false,
        val isShift: Boolean = false,
        val isSymbolPerson: Boolean = false,
        val secondaryLabel: String = ""
    )

    companion object {
        const val CODE_SHIFT = -1
        const val CODE_DELETE = -5
        const val CODE_ENTER = -4
        const val CODE_SYMBOL = -103
        const val CODE_ABC = -100
        const val CODE_LANG_SWITCH = -101
        const val CODE_SPACE = 32
        const val CODE_COMMA = -201
        const val CODE_PERIOD = -202
        const val CODE_AT = -203
        const val CODE_HANDWRITE = -204
        const val CODE_SYMBOL_AT = -205  // @?! 按键
        const val CODE_QUESTION = -206  // ؟ 按键
        const val CODE_MINUS = -207  // 减号按键
        const val CODE_SYMBOL_PERSON = -208  // 符号切换按键(人物卡片图标)
        const val CODE_DOT_QUESTION = -209  // .? 标点按键
    }

    var currentLanguage: Language = Language.UYGHUR
        private set

    var shiftState: ShiftState = ShiftState.NONE
        private set

    var uyghurLayoutType: UyghurLayout = UyghurLayout.LAYOUT_32
        private set

    var chineseLayoutType: ChineseLayout = ChineseLayout.QWERTY_26
        private set

    private var isSymbolMode: Boolean = false
    private var keys: List<Key> = emptyList()
    private var keyRects: List<RectF> = emptyList()
    private var pressedIndex: Int = -1
    private var longPressRunnable: Runnable? = null
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout()
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var spaceLongPressFired: Boolean = false
    private var touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private var deleteRepeatRunnable: Runnable? = null
    private var isDeleteRepeating: Boolean = false
    private var deleteRepeatCount: Int = 0
    private val deleteLongPressDelay = 200L  // 删除键长按触发延迟200ms（比标准500ms快）
    private val deleteRepeatIntervalFast = 30L  // 快速删除间隔30ms
    private val deleteRepeatIntervalSlow = 60L  // 初始删除间隔60ms（前几次稍慢，后续加速）

    // ============================================================
    // 维语动态字母切换
    // 初始态：元音使用 hamza 前缀形式（ئا ئە ئې ئى ئو ئۇ ئۆ ئۈ）—词首形态
    // 激活态：元音使用独立形式（ا ە ې ى و ۇ ۆ ۈ）—词中/词末形态
    // 按字母键→切换激活态；按空格/提交→恢复初始态
    // ============================================================
    private var isUyghurActiveForm: Boolean = false

    /**
     * 元音转换映射：独立形式 code → (hamza前缀label, hamza前缀output)
     * 用于初始态将独立元音转换为 hamza 前缀形式。
     */
    private val uyghurVowelInitialMap = mapOf(
        0x0627 to ("ئا" to "ئا"),  // ا → ئا
        0x06D5 to ("ئە" to "ئە"),  // ە → ئە
        0x06D0 to ("ئې" to "ئې"),  // ې → ئې
        0x0649 to ("ئى" to "ئى"),  // ى → ئى
        0x0648 to ("ئو" to "ئو"),  // و → ئو
        0x06C7 to ("ئۇ" to "ئۇ"),  // ۇ → ئۇ
        0x06C6 to ("ئۆ" to "ئۆ"),  // ۆ → ئۆ
        0x06C8 to ("ئۈ" to "ئۈ")   // ۈ → ئۈ
    )

    /**
     * 将布局中的独立元音转换为 hamza 前缀形式（初始态）。
     */
    private fun transformToInitialForm(keyList: List<Key>): List<Key> {
        return keyList.map { key ->
            val initial = uyghurVowelInitialMap[key.code]
            if (initial != null) {
                key.copy(label = initial.first, outputText = initial.second)
            } else {
                key
            }
        }
    }

    var onKeyListener: ((Int, String, String) -> Unit)? = null
    var onLangSwitchListener: (() -> Unit)? = null
    var onMicListener: (() -> Unit)? = null
    var onSymbolListener: (() -> Unit)? = null
    var onCloseListener: (() -> Unit)? = null
    var onHandwriteListener: (() -> Unit)? = null
    var onSymbolAtListener: (() -> Unit)? = null
    var onLayoutSwitchListener: (() -> Unit)? = null

    /**
     * 中文模式符号按钮切换回调。
     * 参数为按钮 code (CODE_SYMBOL_AT / CODE_COMMA / CODE_PERIOD)，
     * 由 Service 层实现三态切换：简易符号栏 → 完整符号面板 → 收起。
     */
    var onSymbolToggleListener: ((Int) -> Unit)? = null

    // ============================================================
    // 中文长按符号弹出
    // ============================================================
    var onLongPressSymbolListener: ((String) -> Unit)? = null

    /** 第一行字母键长按输出：数字+大小写字母 */
    private val row1LongPress = mapOf(
        113 to listOf("1", "Q", "q"),  // q
        119 to listOf("2", "W", "w"),  // w
        101 to listOf("3", "E", "e"),  // e
        114 to listOf("4", "R", "r"),  // r
        116 to listOf("5", "T", "t"),  // t
        121 to listOf("6", "Y", "y"),  // y
        117 to listOf("7", "U", "u"),  // u
        105 to listOf("8", "I", "i"),  // i
        111 to listOf("9", "O", "o"),  // o
        112 to listOf("0", "P", "p")   // p
    )

    /** 第二行字母键长按输出：符号 */
    private val row2LongPress = mapOf(
        97 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // a
        115 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // s
        100 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // d
        102 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // f
        103 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // g
        104 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // h
        106 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // j
        107 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // k
        108 to listOf("~", "!", "@", "#", "%", "\u201C", "\u201D", "*", "?"),  // l
        39 to listOf("\u2019", "\u2018", "\u201C", "\u201D", "\u00B0")  // ' → ' ' " " °
    )

    /** 第三行字母键长按输出：括号等 */
    private val row3LongPress = mapOf(
        122 to listOf("(", ")"),  // z
        120 to listOf("(", ")"),  // x
        99 to listOf("(", ")"),  // c
        118 to listOf("-", "_"),  // v
        98 to listOf(":", ";"),  // b
        110 to listOf("+", "=", "\u2014"),  // n
        109 to listOf("/", "\\", "|")   // m
    )

    /** 逗号键长按输出 */
    private val commaLongPress = listOf("?", ":", ";", "!", "\u3001", "(", ")", "\u201C", "\u201D", "\u2026")

    // 绘制参数 — 百度输入法同款样式
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 百度同款颜色
    private val keyBgNormal = Color.parseColor("#FFFFFF")       // 白色按键
    private val keyBgPressed = Color.parseColor("#E8E8E8")      // 按下灰色（百度同款）
    private val keyBgFunctional = Color.parseColor("#adb5bd")   // 功能键灰色（百度同款）
    private val keyBgFunctionalPressed = Color.parseColor("#9aa0a6")
    private val keyBgSpecial = Color.parseColor("#2a7aff")      // 回车键蓝色（百度同款）
    private val keyBgSpecialPressed = Color.parseColor("#1a6aef")
    private val keyBgSpace = Color.parseColor("#FFFFFF")        // 空格键白色底
    private val textColorNormal = Color.parseColor("#222222")   // 按键文字深色（百度同款）
    private val textColorFunctional = Color.parseColor("#222222")
    private val textColorSpecial = Color.WHITE
    private val textColorSpace = Color.parseColor("#888888")
    private val keyboardBg = Color.parseColor("#d1d5db")        // 键盘背景灰色（百度同款）
    private val cornerRadius = 8f                               // 圆角8dp（百度同款）
    private val keySpacing = 5f                                 // 按键间距5dp（百度同款）
    private val keyTextSize = 21f                              // 主文字21sp（百度同款）
    private val keyLabelSize = 13f                             // 功能键文字13sp
    private val supNumSize = 9f                                 // 上标数字9sp
    private val supSymSize = 11f                                // 上标符号11sp
    private val boldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private var isDarkMode = false

    fun setDarkMode(dark: Boolean) {
        isDarkMode = dark
        invalidate()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateKeyLayout()
    }

    // ============================================================
    // 键盘布局
    // ============================================================

    private fun updateKeyLayout() {
        keys = if (isSymbolMode) {
            getSymbolLayout()
        } else {
            when (currentLanguage) {
                Language.UYGHUR -> getUyghurLayout()
                Language.PINYIN -> {
                    when (chineseLayoutType) {
                        ChineseLayout.QWERTY_26 -> getQwertyLayout("中文", true)
                        ChineseLayout.NINE_KEY -> getNineKeyLayout()
                        ChineseLayout.HANDWRITE -> getQwertyLayout("中文", true) // 手写由面板处理
                    }
                }
                Language.ENGLISH -> getQwertyLayout("English", false)
            }
        }
        if (width > 0) computeKeyRects(width)
        invalidate()
    }

    /**
     * 维语布局 — 移除 shift 按键，ڭ U+06AD 单独按键。
     * 32键版含全部字母，26键版为紧凑布局。
     */
    private fun getUyghurLayout(): List<Key> {
        return if (uyghurLayoutType == UyghurLayout.LAYOUT_32) {
            getUyghur32Layout()
        } else {
            getUyghur26Layout()
        }
    }

    /**
     * 维语32键布局 — 按用户指定字母序列，支持动态切换。
     *
     * 布局以独立元音形式（激活态）定义，初始态时通过 transformToInitialForm 转换。
     *
     * 初始默认序列（RTL阅读）：
     *   ژ پ ئو ڭ ئۇ ي ت ر ئې ۋ چ
     *   گ ف ل ك ق ئى ئە ئا د س ھ
     *   ئۆ ج خ م ن ب ئۈ غ ش ز ئ
     *
     * 激活态序列（RTL阅读）：
     *   ژ پ و ڭ ۇ ي ت ر ې ۋ چ
     *   گ ف ل ك ق ى ە ا د س ھ
     *   ۆ ج خ م ن ب ۈ غ ش ز ئ
     */
    private fun getUyghur32Layout(): List<Key> {
        // 用户指定字母排布 — 每个主按键上方有数字/符号小字标识
        //
        // Row 1: چ ۋ ئې ر ت ي ئۇ ڭ ئو پ ژ (11键)
        //   副标签:  1  2  3  4  5  6  7  8  9  0  /
        // Row 2: ھ س د ئا ئە ئى ق ك ل ف گ (11键)
        //   副标签:  ) ( # $ ^ * - + = { }
        // Row 3: ز ش غ ئۈ ب ن م خ ج ئۆ (11键)
        //   副标签:  » « ¨ ¯ : ; … ~ < >
        // Row 4: [123] [؟] [space] [،] [ئ] [↵]

        val row1 = listOf(
            Key(0x0686, "چ", "چ", secondaryLabel = "1"),
            Key(0x06CB, "ۋ", "ۋ", secondaryLabel = "2"),
            Key(0x06D0, "ې", "ې", secondaryLabel = "3"),
            Key(0x0631, "ر", "ر", secondaryLabel = "4"),
            Key(0x062A, "ت", "ت", secondaryLabel = "5"),
            Key(0x064A, "ي", "ي", secondaryLabel = "6"),
            Key(0x06C7, "ۇ", "ۇ", secondaryLabel = "7"),
            Key(0x06AD, "ڭ", "ڭ", secondaryLabel = "8"),
            Key(0x0648, "و", "و", secondaryLabel = "9"),
            Key(0x067E, "پ", "پ", secondaryLabel = "0"),
            Key(0x0698, "ژ", "ژ", secondaryLabel = "/")
        )
        val row2 = listOf(
            Key(0x06BE, "ھ", "ھ", secondaryLabel = ")"),
            Key(0x0633, "س", "س", secondaryLabel = "("),
            Key(0x062F, "د", "د", secondaryLabel = "#"),
            Key(0x0627, "ا", "ا", secondaryLabel = "$"),
            Key(0x06D5, "ە", "ە", secondaryLabel = "^"),
            Key(0x0649, "ى", "ى", secondaryLabel = "*"),
            Key(0x0642, "ق", "ق", secondaryLabel = "-"),
            Key(0x0643, "ك", "ك", secondaryLabel = "+"),
            Key(0x0644, "ل", "ل", secondaryLabel = "="),
            Key(0x0641, "ف", "ف", secondaryLabel = "{"),
            Key(0x06AF, "گ", "گ", secondaryLabel = "}")
        )
        val row3 = listOf(
            Key(0x0632, "ز", "ز", secondaryLabel = "»"),
            Key(0x0634, "ش", "ش", secondaryLabel = "«"),
            Key(0x063A, "غ", "غ", secondaryLabel = "¨"),
            Key(0x06C8, "ۈ", "ۈ", secondaryLabel = "¯"),
            Key(0x0628, "ب", "ب", secondaryLabel = ":"),
            Key(0x0646, "ن", "ن", secondaryLabel = ";"),
            Key(0x0645, "م", "م", secondaryLabel = "…"),
            Key(0x062E, "خ", "خ", secondaryLabel = "~"),
            Key(0x062C, "ج", "ج", secondaryLabel = "<"),
            Key(0x06C6, "ۆ", "ۆ", secondaryLabel = ">"),
            Key(CODE_DELETE, "⌫", width = 1.3f, isFunctional = true)
        )
        val row4 = listOf(
            Key(CODE_SYMBOL, "123", width = 1.2f, isFunctional = true),
            Key(CODE_QUESTION, "؟", "؟", width = 0.8f, isFunctional = true, secondaryLabel = "!"),
            Key(CODE_SPACE, "ئۇيغۇرچە", " ", width = 4f, isFunctional = true, isSpace = true),
            Key(CODE_COMMA, "،", "،", width = 0.8f, isFunctional = true, secondaryLabel = "'"),
            Key(0x0626, "ئ", "ئ", width = 0.8f, isFunctional = true, secondaryLabel = "@"),
            Key(CODE_ENTER, "↵", width = 1.2f, isFunctional = true, isSpecial = true)
        )
        val layout = row1 + row2 + row3 + row4
        // 初始态：元音转换为 hamza 前缀形式
        return if (!isUyghurActiveForm) transformToInitialForm(layout) else layout
    }

    private fun getUyghur26Layout(): List<Key> {
        // 26键紧凑布局 — 以独立元音（激活态）定义，初始态自动转换
        val row1 = listOf(
            Key(0x0642, "ق", "ق"), Key(0x0648, "و", "و"),
            Key(0x06D5, "ە", "ە"), Key(0x0631, "ر", "ر"),
            Key(0x062A, "ت", "ت"), Key(0x064A, "ي", "ي"),
            Key(0x06C7, "ۇ", "ۇ"), Key(0x06C6, "ۆ", "ۆ"),
            Key(0x0649, "ى", "ى"), Key(0x067E, "پ", "پ"),
            Key(0x0686, "چ", "چ")
        )
        val row2 = listOf(
            Key(0x06BE, "ھ", "ھ"), Key(0x0633, "س", "س"),
            Key(0x062F, "د", "د"), Key(0x0627, "ا", "ا"),
            Key(0x06D5, "ە", "ە"), Key(0x0642, "ق", "ق"),
            Key(0x0643, "ك", "ك"), Key(0x0644, "ل", "ل"),
            Key(0x0641, "ف", "ف"), Key(0x06AF, "گ", "گ"),
            Key(0x0698, "ژ", "ژ")
        )
        val row3 = listOf(
            Key(0x0632, "ز", "ز"), Key(0x0634, "ش", "ش"),
            Key(0x063A, "غ", "غ"), Key(0x0628, "ب", "ب"),
            Key(0x0646, "ن", "ن"), Key(0x06AD, "ڭ", "ڭ"),
            Key(0x0645, "م", "م"), Key(0x062E, "خ", "خ"),
            Key(0x062C, "ج", "ج"), Key(0x06C8, "ۈ", "ۈ"),
            Key(0x06CB, "ۋ", "ۋ"),
            Key(CODE_DELETE, "⌫", width = 1.3f, isFunctional = true)
        )
        val row4 = listOf(
            Key(CODE_SYMBOL, "123", width = 1.2f, isFunctional = true),
            Key(CODE_COMMA, "،", "،", width = 0.8f, isFunctional = true),
            Key(CODE_SPACE, "ئۇيغۇرچە", " ", width = 4f, isFunctional = true, isSpace = true),
            Key(CODE_PERIOD, ".", ".", width = 0.8f, isFunctional = true),
            Key(CODE_ENTER, "↵", width = 1.2f, isFunctional = true, isSpecial = true)
        )
        val layout = row1 + row2 + row3 + row4
        // 初始态：元音转换为 hamza 前缀形式
        return if (!isUyghurActiveForm) transformToInitialForm(layout) else layout
    }

    /**
     * QWERTY 布局 — 英文/中文拼音。
     * isChinese=true 时 shift 替换为手写按钮。
     */
    private fun getQwertyLayout(spaceLabel: String, isChinese: Boolean): List<Key> {
        // 中文模式字母大写显示
        val caps = isChinese
        fun letterLabel(c: String): String = if (caps) c.uppercase() else c

        val row1 = listOf(
            Key(113, letterLabel("q"), "q", secondaryLabel = "1"),
            Key(119, letterLabel("w"), "w", secondaryLabel = "2"),
            Key(101, letterLabel("e"), "e", secondaryLabel = "3"),
            Key(114, letterLabel("r"), "r", secondaryLabel = "4"),
            Key(116, letterLabel("t"), "t", secondaryLabel = "5"),
            Key(121, letterLabel("y"), "y", secondaryLabel = "6"),
            Key(117, letterLabel("u"), "u", secondaryLabel = "7"),
            Key(105, letterLabel("i"), "i", secondaryLabel = "8"),
            Key(111, letterLabel("o"), "o", secondaryLabel = "9"),
            Key(112, letterLabel("p"), "p", secondaryLabel = "0")
        )
        val row2 = listOf(
            Key(97, letterLabel("a"), "a", secondaryLabel = "!"),
            Key(115, letterLabel("s"), "s", secondaryLabel = "@"),
            Key(100, letterLabel("d"), "d", secondaryLabel = "#"),
            Key(102, letterLabel("f"), "f", secondaryLabel = "$"),
            Key(103, letterLabel("g"), "g", secondaryLabel = "%"),
            Key(104, letterLabel("h"), "h", secondaryLabel = "^"),
            Key(106, letterLabel("j"), "j", secondaryLabel = "&"),
            Key(107, letterLabel("k"), "k", secondaryLabel = "*"),
            Key(108, letterLabel("l"), "l", secondaryLabel = "(")
        )
        // Row 3: 中文→手写按钮, 英文→shift(加宽)
        val row3first = if (isChinese) {
            Key(CODE_HANDWRITE, "✎", width = 1.5f, isFunctional = true, isHandwrite = true)
        } else {
            // Shift: 用Canvas绘制自定义图标，label留空
            Key(CODE_SHIFT, "", width = 1.5f, isFunctional = true, isSpecial = true, isShift = true)
        }
        val row3 = if (isChinese) {
            listOf(
                row3first,
                Key(122, letterLabel("z"), "z", secondaryLabel = ")"),
                Key(120, letterLabel("x"), "x", secondaryLabel = "-"),
                Key(99, letterLabel("c"), "c", secondaryLabel = "+"),
                Key(118, letterLabel("v"), "v", secondaryLabel = "="),
                Key(98, letterLabel("b"), "b", secondaryLabel = ":"),
                Key(110, letterLabel("n"), "n", secondaryLabel = ";"),
                Key(109, letterLabel("m"), "m", secondaryLabel = "/"),
                Key(CODE_DELETE, "⌫", width = 1.5f, isFunctional = true)
            )
        } else {
            listOf(
                row3first,
                Key(122, letterLabel("z"), "z", secondaryLabel = ")"),
                Key(120, letterLabel("x"), "x", secondaryLabel = "-"),
                Key(99, letterLabel("c"), "c", secondaryLabel = "+"),
                Key(118, letterLabel("v"), "v", secondaryLabel = "="),
                Key(98, letterLabel("b"), "b", secondaryLabel = ":"),
                Key(110, letterLabel("n"), "n", secondaryLabel = ";"),
                Key(109, letterLabel("m"), "m", secondaryLabel = "/"),
                Key(CODE_DELETE, "⌫", width = 1.5f, isFunctional = true)
            )
        }
        // Row 4: 参照截图 — 123(灰) | 符号切换(人物卡片,灰) | .? | 空格(缩小) | , | 蓝色回车
        val row4 = if (isChinese) {
            listOf(
                Key(CODE_SYMBOL, "123", width = 1.0f, isFunctional = true),
                Key(CODE_SYMBOL_PERSON, "", "", width = 1.0f, isFunctional = true, isSymbolPerson = true),
                Key(CODE_DOT_QUESTION, ".?", ".?", width = 0.8f, isFunctional = true),
                Key(CODE_SPACE, spaceLabel, " ", width = 2.5f, isFunctional = true, isSpace = true),
                Key(CODE_COMMA, ",", ",", width = 0.7f, isFunctional = true),
                Key(CODE_ENTER, "↵", width = 1.0f, isFunctional = true, isSpecial = true)
            )
        } else {
            listOf(
                Key(CODE_SYMBOL, "123", width = 1.2f, isFunctional = true),
                Key(CODE_COMMA, ",", ",", width = 0.8f, isFunctional = true),
                Key(CODE_SPACE, spaceLabel, " ", width = 3.2f, isFunctional = true, isSpace = true),
                Key(CODE_PERIOD, ".", ".", width = 0.8f, isFunctional = true),
                Key(CODE_ENTER, "↵", width = 1.2f, isFunctional = true, isSpecial = true)
            )
        }
        return row1 + row2 + row3 + row4
    }

    /**
     * 九键拼音布局 (T9 风格)
     */
    private fun getNineKeyLayout(): List<Key> {
        val row1 = listOf(
            Key(49, "1", "1"), Key(50, "2 abc", "2"), Key(51, "3 def", "3")
        )
        val row2 = listOf(
            Key(52, "4 ghi", "4"), Key(53, "5 jkl", "5"), Key(54, "6 mno", "6")
        )
        val row3 = listOf(
            Key(55, "7 pqrs", "7"), Key(56, "8 tuv", "8"), Key(57, "9 wxyz", "9")
        )
        val row4 = listOf(
            Key(CODE_HANDWRITE, "✎", width = 1f, isFunctional = true, isHandwrite = true),
            Key(42, "*", "*"),
            Key(48, "0", "0"),
            Key(35, "#", "#"),
            Key(CODE_DELETE, "⌫", width = 1f, isFunctional = true)
        )
        val row5 = listOf(
            Key(CODE_SYMBOL, "123", width = 1.2f, isFunctional = true),
            Key(CODE_COMMA, ",", ",", width = 0.8f, isFunctional = true),
            Key(CODE_SPACE, "中文", " ", width = 4f, isFunctional = true, isSpace = true),
            Key(CODE_PERIOD, ".", ".", width = 0.8f, isFunctional = true),
            Key(CODE_ENTER, "↵", width = 1.2f, isFunctional = true, isSpecial = true)
        )
        return row1 + row2 + row3 + row4 + row5
    }

    /**
     * 符号/数字布局 — 123 页面
     * 百度风格：4行紧凑布局
     * Row1: 1 2 3 4 5 6 7 8 9 0
     * Row2: ! @ # $ % & * ( ) -
     * Row3: + = / \ | ~ ` . , ?
     * Row4: [ABC] [,] [space] [.] [↵]
     */
    private fun getSymbolLayout(): List<Key> {
        // Row 1: 1-0 (10键)
        val row1 = listOf(
            Key(49, "1", "1"), Key(50, "2", "2"), Key(51, "3", "3"),
            Key(52, "4", "4"), Key(53, "5", "5"), Key(54, "6", "6"),
            Key(55, "7", "7"), Key(56, "8", "8"), Key(57, "9", "9"),
            Key(48, "0", "0")
        )
        // Row 2: 符号 (10键)
        val row2 = listOf(
            Key(33, "!", "!"), Key(64, "@", "@"), Key(35, "#", "#"),
            Key(36, "$", "$"), Key(37, "%", "%"), Key(38, "&", "&"),
            Key(42, "*", "*"), Key(40, "(", "("), Key(41, ")", ")"),
            Key(45, "-", "-")
        )
        // Row 3: 运算/标点 (9键 + 删除)
        val row3 = listOf(
            Key(43, "+", "+"), Key(61, "=", "="), Key(47, "/", "/"),
            Key(92, "\\", "\\"), Key(124, "|", "|"), Key(126, "~", "~"),
            Key(96, "`", "`"), Key(95, "_", "_"),
            Key(CODE_DELETE, "⌫", width = 1.5f, isFunctional = true)
        )
        // Row 4: 功能行
        val spaceLabel = if (currentLanguage == Language.UYGHUR) "ئۇيغۇرچە" else "space"
        val row4 = listOf(
            Key(CODE_ABC, "⌨", width = 1.2f, isFunctional = true),
            Key(CODE_COMMA, ",", ",", width = 0.8f, isFunctional = true),
            Key(CODE_SPACE, spaceLabel, " ", width = 4f, isFunctional = true, isSpace = true),
            Key(CODE_PERIOD, ".", ".", width = 0.8f, isFunctional = true),
            Key(CODE_ENTER, "↵", width = 1.2f, isFunctional = true, isSpecial = true)
        )
        return row1 + row2 + row3 + row4
    }

    // ============================================================
    // 布局计算
    // ============================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyRects(w)
    }

    private fun computeKeyRects(viewWidth: Int) {
        val density = resources.displayMetrics.density
        val spacing = keySpacing * density  // 5dp 间距（百度同款）
        val padding = 5 * density           // 5dp 水平内边距（百度同款）
        val keyHeight = 48 * density         // 48dp 按键高度（百度同款）

        // 根据当前布局分行
        val layoutRows = mutableListOf<List<Key>>()

        when {
            isSymbolMode -> {
                // 123页面: 10+10+9+5 = 34
                val sizes = mutableListOf(10, 10, 9, 5)
                var pos = 0
                for (sz in sizes) {
                    if (pos + sz <= keys.size) {
                        layoutRows.add(keys.subList(pos, pos + sz))
                        pos += sz
                    }
                }
            }
            currentLanguage == Language.UYGHUR -> {
                if (uyghurLayoutType == UyghurLayout.LAYOUT_32) {
                    // 32键参照截图: 11+11+11+6 = 39
                    layoutRows.add(keys.subList(0, 11))
                    layoutRows.add(keys.subList(11, 22))
                    layoutRows.add(keys.subList(22, 33))
                    layoutRows.add(keys.subList(33, 39))
                } else {
                    // 26键: 11+11+12+5 = 39
                    layoutRows.add(keys.subList(0, 11))
                    layoutRows.add(keys.subList(11, 22))
                    layoutRows.add(keys.subList(22, 34))
                    layoutRows.add(keys.subList(34, 39))
                }
            }
            currentLanguage == Language.PINYIN && chineseLayoutType == ChineseLayout.NINE_KEY -> {
                // 九键: 3+3+3+5+5 = 19
                layoutRows.add(keys.subList(0, 3))
                layoutRows.add(keys.subList(3, 6))
                layoutRows.add(keys.subList(6, 9))
                layoutRows.add(keys.subList(9, 14))
                layoutRows.add(keys.subList(14, 19))
            }
            else -> {
                // QWERTY: 10+9+row3+row4
                // row1=10(q-p), row2=9(a-l), 英文row3=9(shift+zxcvbnm+del), 中文row3=9(手写+zxcvbnm+del)
                // 英文row4=5, 中文row4=6
                layoutRows.add(keys.subList(0, 10))
                layoutRows.add(keys.subList(10, 19))
                val row3Start = 19
                val row3Size = 9  // 英文和中文都是9键
                layoutRows.add(keys.subList(row3Start, row3Start + row3Size))
                val row4Start = row3Start + row3Size
                val row4Size = keys.size - row4Start
                if (row4Size > 0) {
                    layoutRows.add(keys.subList(row4Start, row4Start + row4Size))
                }
            }
        }

        val rects = mutableListOf<RectF>()
        var y = padding + spacing / 2

        for ((rowIndex, row) in layoutRows.withIndex()) {
            val totalWeight = row.sumOf { it.width.toDouble() }.toFloat()
            // 第二行（ASDF行）缩进 — 参照截图
            val rowIndent = if (rowIndex == 1 && !isSymbolMode &&
                              currentLanguage != Language.UYGHUR) {
                (keyHeight * 0.3f)  // 缩进约半个按键宽度
            } else {
                0f
            }
            var x = padding + rowIndent
            for (key in row) {
                val availableW = viewWidth - 2 * padding - rowIndent - spacing * (row.size + 1)
                val keyW = availableW * key.width / totalWeight
                rects.add(RectF(x + spacing, y, x + spacing + keyW, y + keyHeight))
                x += keyW + spacing
            }
            y += keyHeight + spacing
        }

        keyRects = rects
    }

    // ============================================================
    // 绘制
    // ============================================================

    override fun onDraw(canvas: Canvas) {
        val density = resources.displayMetrics.density
        val cornerR = cornerRadius * density

        // 键盘背景 — 百度同款 #d1d5db
        bgPaint.color = if (isDarkMode) Color.parseColor("#1A1A2E") else keyboardBg
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        for ((index, key) in keys.withIndex()) {
            if (index >= keyRects.size) break
            val rect = keyRects[index]
            val bg: Int
            val tc: Int
            val isPressed = pressedIndex == index

            when {
                key.isSpace -> {
                    bg = if (isDarkMode) Color.parseColor("#2D2D44") else keyBgSpace
                    tc = if (isDarkMode) Color.parseColor("#AAAAAA") else textColorSpace
                }
                key.isShift -> {
                    if (shiftState != ShiftState.NONE) {
                        bg = keyBgSpecial; tc = textColorSpecial
                    } else if (isPressed) {
                        bg = keyBgFunctionalPressed; tc = textColorFunctional
                    } else {
                        bg = if (isDarkMode) Color.parseColor("#3A3A4A") else keyBgFunctional
                        tc = if (isDarkMode) Color.WHITE else textColorFunctional
                    }
                }
                key.isSpecial -> {
                    // 回车键 — 百度同款蓝色 #2a7aff
                    bg = if (isPressed) keyBgSpecialPressed else keyBgSpecial
                    tc = textColorSpecial
                }
                key.isSymbolPerson -> {
                    bg = if (isPressed) keyBgFunctionalPressed else keyBgFunctional
                    tc = if (isDarkMode) Color.WHITE else textColorFunctional
                }
                key.isFunctional -> {
                    // 功能键 — 百度同款灰色 #adb5bd
                    bg = if (isDarkMode) Color.parseColor("#3A3A4A") else keyBgFunctional
                    tc = if (isDarkMode) Color.WHITE else textColorFunctional
                }
                isPressed -> {
                    // 按下状态 — 百度同款灰色 #E8E8E8
                    bg = if (isDarkMode) Color.parseColor("#3A3A4A") else keyBgPressed
                    tc = if (isDarkMode) Color.WHITE else textColorNormal
                }
                isDarkMode -> {
                    bg = Color.parseColor("#8037474F"); tc = Color.WHITE
                }
                else -> {
                    bg = keyBgNormal; tc = textColorNormal
                }
            }

            // 1. 绘制阴影层 — 百度同款 box-shadow: 0 1px 2px rgba(0,0,0,0.12)
            if (!isDarkMode && bg != keyBgSpecial) {
                shadowPaint.color = Color.parseColor("#1F000000")  // 12% 黑色阴影
                shadowPaint.style = Paint.Style.FILL
                val shadowOffset = 1f * density
                val shadowRect = RectF(
                    rect.left, rect.top + shadowOffset,
                    rect.right, rect.bottom + shadowOffset
                )
                canvas.drawRoundRect(shadowRect, cornerR, cornerR, shadowPaint)
                // 内侧底部细线阴影 inset 0 -0.5px
                shadowPaint.color = Color.parseColor("#0A000000")
                val innerRect = RectF(rect.left, rect.bottom - 1f * density, rect.right, rect.bottom)
                canvas.drawRoundRect(innerRect, cornerR, cornerR, shadowPaint)
            }

            // 2. 绘制按键主体 — 圆角矩形
            paint.color = bg
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(rect, cornerR, cornerR, paint)

            // 3. 绘制文字 — 百度同款：21sp 粗体字母
            paint.color = tc
            val isLetterKey = key.code > 0 && key.code < 128 && key.label.isNotEmpty() &&
                               key.label[0].isLetter()
            val useCaps = (currentLanguage == Language.ENGLISH && shiftState != ShiftState.NONE && isLetterKey) ||
                           (currentLanguage == Language.PINYIN)
            val displayLabel = if (useCaps && isLetterKey) key.label.uppercase() else key.label

            // 字号策略（百度同款）
            val textSize = when {
                key.isSpace -> keyLabelSize * density       // 空格键小字
                key.isFunctional -> 14f * density           // 功能键 14sp 粗体
                key.isSpecial -> 13f * density              // 回车键 13sp
                key.isShift -> 24f * density                // Shift键 24sp 大粗体
                displayLabel.isNotEmpty() && displayLabel.length == 1 && displayLabel[0].isDigit() -> 21f * density
                else -> keyTextSize * density               // 字母 21sp
            }
            paint.textSize = textSize
            // 百度同款：字母键粗体，功能键也粗体
            paint.typeface = if (key.isFunctional || key.isSpecial) {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            } else {
                boldTypeface
            }
            paint.textAlign = Paint.Align.CENTER

            // 人物卡片图标按键不绘制文字
            if (!key.isSymbolPerson && !key.isShift) {
                val fm = paint.fontMetrics
                val textHeight = fm.descent - fm.ascent
                // 有副标签时主文字下移；无副标签时居中
                val centerY = if (key.secondaryLabel.isNotEmpty()) {
                    rect.centerY() + textHeight / 2f - fm.descent + 4f * density
                } else {
                    rect.centerY() + textHeight / 2f - fm.descent
                }
                canvas.drawText(displayLabel, rect.centerX(), centerY, paint)

                // 绘制副标签（上标数字/符号）— 百度同款位置
                if (key.secondaryLabel.isNotEmpty()) {
                    paint.textSize = supNumSize * density
                    paint.color = if (isDarkMode) Color.parseColor("#888888") else Color.parseColor("#777777")
                    paint.textAlign = Paint.Align.LEFT
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val secX = rect.left + 5f * density
                    val secY = rect.top + 12f * density
                    canvas.drawText(key.secondaryLabel, secX, secY, paint)
                }
            }

            // Shift键 — 绘制自定义线框图标
            if (key.isShift) {
                drawShiftIcon(canvas, rect, density, shiftState, tc, isPressed)
            }

            // 人物卡片图标按键 — 绘制线条风格图标
            if (key.isSymbolPerson) {
                drawPersonCardIcon(canvas, rect, density, tc)
            }

            // 空格键正中间绘制线条麦克风图标
            if (key.isSpace) {
                drawLineMicrophone(canvas, rect, density)
            }
        }

        // 长按符号弹窗 — 内联绘制（不用PopupWindow）
        if (isSymbolPopupShowing && symbolPopupSymbols.isNotEmpty()) {
            drawSymbolPopup(canvas, density)
        }
    }

    /**
     * 绘制长按符号弹窗 — 百度同款样式。
     * 白色圆角背景 + 阴影 + 等宽符号排列 + 蓝色高亮选中项。
     */
    private fun drawSymbolPopup(canvas: Canvas, density: Float) {
        val rect = symbolPopupRect

        // 阴影层
        shadowPaint.color = Color.parseColor("#30000000")
        shadowPaint.style = Paint.Style.FILL
        val shadowOffset = 2f * density
        val shadowRect = RectF(
            rect.left + shadowOffset, rect.top + shadowOffset,
            rect.right + shadowOffset, rect.bottom + shadowOffset
        )
        canvas.drawRoundRect(shadowRect, 8f * density, 8f * density, shadowPaint)

        // 白色背景
        bgPaint.color = Color.parseColor("#FFFFFF")
        bgPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 8f * density, 8f * density, bgPaint)

        // 边框
        paint.color = Color.parseColor("#E0E0E0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        canvas.drawRoundRect(rect, 8f * density, 8f * density, paint)
        paint.style = Paint.Style.FILL

        // 符号文字
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            style = Paint.Style.FILL
        }

        val sw = symbolPopupSymbolWidth
        for (i in symbolPopupSymbols.indices) {
            val left = rect.left + i * sw
            val right = rect.left + (i + 1) * sw
            val centerX = (left + right) / 2f
            val centerY = rect.centerY()

            if (i == symbolPopupSelectedIndex) {
                val hlRect = RectF(left + 1f * density, rect.top + 2f * density,
                                   right - 1f * density, rect.bottom - 2f * density)
                canvas.drawRoundRect(hlRect, 6f * density, 6f * density, highlightPaint)
                textPaint.color = Color.parseColor("#FFFFFF")
            } else {
                textPaint.color = Color.parseColor("#333333")
            }

            val fm = textPaint.fontMetrics
            val textY = centerY + (fm.descent - fm.ascent) / 2f - fm.descent
            canvas.drawText(symbolPopupSymbols[i], centerX, textY, textPaint)
        }
    }

    /**
     * 绘制 Shift 图标 — 单一 Path 连续轮廓。
     * 上面是向上箭头（三角形），中间是箭杆（竖矩形），底部是方形底座。
     * 普通态：空心描边 ⇧
     * 锁定态：实心填充 ⇪
     */
    private fun drawShiftIcon(canvas: Canvas, rect: RectF, density: Float,
                               state: ShiftState, color: Int, isPressed: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (state == ShiftState.LOCK_CAPS) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val cx = rect.centerX()
        val iconW = 16f * density
        val iconH = 22f * density
        val top = rect.centerY() - iconH / 2f
        val bottom = rect.centerY() + iconH / 2f

        // 尺寸比例
        val arrowH = iconH * 0.38f         // 箭头高度
        val stemH = iconH * 0.42f          // 箭杆高度
        val arrowHalfW = iconW / 2f         // 箭头半宽（最宽）
        val stemHalfW = iconW * 0.22f       // 箭杆半宽（最窄）
        val baseHalfW = iconW * 0.45f      // 底座半宽（比箭杆宽）

        // Y 坐标
        val arrowBottomY = top + arrowH
        val stemBottomY = arrowBottomY + stemH
        val baseBottomY = bottom

        // 单一连续轮廓 Path
        // 箭尖 → 左箭头底 → 左箭杆顶 → 左箭杆底 → 左底座顶 → 左底座底
        // → 右底座底 → 右底座顶 → 右箭杆底 → 右箭杆顶 → 右箭头底 → 闭合
        val path = Path()
        path.moveTo(cx, top)                                      // 箭尖
        path.lineTo(cx - arrowHalfW, arrowBottomY)                // 箭头左下角
        path.lineTo(cx - stemHalfW, arrowBottomY)                 // 箭杆左上角（向内收）
        path.lineTo(cx - stemHalfW, stemBottomY)                  // 箭杆左下角
        path.lineTo(cx - baseHalfW, stemBottomY)                  // 底座左上角（向外扩）
        path.lineTo(cx - baseHalfW, baseBottomY)                 // 底座左下角
        path.lineTo(cx + baseHalfW, baseBottomY)                 // 底座右下角
        path.lineTo(cx + baseHalfW, stemBottomY)                  // 底座右上角
        path.lineTo(cx + stemHalfW, stemBottomY)                  // 箭杆右下角（向内收）
        path.lineTo(cx + stemHalfW, arrowBottomY)                 // 箭杆右上角
        path.lineTo(cx + arrowHalfW, arrowBottomY)                // 箭头右下角（向外扩）
        path.close()                                              // 回到箭尖

        canvas.drawPath(path, paint)
    }

    /**
     * 绘制线条风格麦克风图标 — 不使用emoji，用Canvas路径绘制。
     * 结构：胶囊头部 + U型支架 + 竖线杆 + 水平底座
     */
    private fun drawLineMicrophone(canvas: Canvas, rect: RectF, density: Float) {
        val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AAAAAA")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            strokeCap = Paint.Cap.ROUND
        }

        val cx = rect.centerX()
        val cy = rect.centerY() + 2f * density  // 微微下移居中

        // 图标尺寸
        val iconW = 10f * density
        val iconH = 14f * density

        // 胶囊头部 (圆角矩形)
        val capsuleL = cx - iconW / 2
        val capsuleT = cy - iconH / 2
        val capsuleR = cx + iconW / 2
        val capsuleB = cy - iconH / 2 + iconW * 1.2f
        val capsuleRect = RectF(capsuleL, capsuleT, capsuleR, capsuleB)
        val capsuleRadius = iconW / 2
        canvas.drawRoundRect(capsuleRect, capsuleRadius, capsuleRadius, micPaint)

        // U型支架 — 两段弧线
        val arcRadius = iconW * 0.7f
        val arcRect = RectF(
            cx - arcRadius,
            capsuleB - iconW * 0.3f,
            cx + arcRadius,
            capsuleB - iconW * 0.3f + arcRadius * 2
        )
        canvas.drawArc(arcRect, 20f, 140f, false, micPaint)

        // 竖线杆 — 从U弧底部到底座
        val stemTop = arcRect.bottom
        val stemBottom = stemTop + 2f * density
        canvas.drawLine(cx, stemTop, cx, stemBottom, micPaint)

        // 水平底座 — 横线
        val baseHalfW = iconW * 0.5f
        canvas.drawLine(cx - baseHalfW, stemBottom, cx + baseHalfW, stemBottom, micPaint)
    }

    /**
     * 绘制人物卡片图标 — 参照截图样式。
     * 左侧人形剪影(头+肩)，右侧三条横线(信息行)。
     */
    private fun drawPersonCardIcon(canvas: Canvas, rect: RectF, density: Float, color: Int) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val cx = rect.centerX()
        val cy = rect.centerY()
        val iconW = 18f * density
        val iconH = 14f * density

        // 左侧：人形头部(圆形)
        val headR = 2.5f * density
        val headCx = cx - iconW * 0.25f
        val headCy = cy - iconH * 0.15f
        canvas.drawCircle(headCx, headCy, headR, iconPaint)

        // 左侧：人形肩部(弧形/半圆)
        val shoulderW = 6f * density
        val shoulderH = 3f * density
        val shoulderRect = RectF(
            headCx - shoulderW / 2,
            headCy + headR * 0.5f,
            headCx + shoulderW / 2,
            headCy + headR * 0.5f + shoulderH * 2
        )
        canvas.drawArc(shoulderRect, 0f, 180f, false, iconPaint)

        // 右侧：三条横线 — 代表信息行
        val lineStartX = cx + iconW * 0.05f
        val lineEndX = cx + iconW * 0.4f
        val lineH = 1.5f * density
        val lineSpacing = 3f * density

        for (i in 0 until 3) {
            val lineY = cy - iconH * 0.2f + i * lineSpacing
            val lineRect = RectF(lineStartX, lineY - lineH / 2, lineEndX, lineY + lineH / 2)
            canvas.drawRoundRect(lineRect, lineH / 2, lineH / 2, iconPaint)
        }
    }

    // ============================================================
    // 触摸事件
    // ============================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        val action = event.actionMasked  // 使用 actionMasked 避免多指索引干扰

        // 符号弹窗显示时 — 直接处理滑动选取（内联Canvas，无PopupWindow）
        if (isSymbolPopupShowing) {
            when (action) {
                MotionEvent.ACTION_MOVE -> {
                    // 精准计算：手指 x 相对弹窗左边缘
                    val relX = x - symbolPopupRect.left
                    val newIndex = if (symbolPopupSymbolWidth > 0) {
                        (relX / symbolPopupSymbolWidth).toInt()
                            .coerceIn(0, symbolPopupSymbols.size - 1)
                    } else {
                        0
                    }
                    if (newIndex != symbolPopupSelectedIndex) {
                        symbolPopupSelectedIndex = newIndex
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    // 松开时输入当前选中的符号
                    val selected = symbolPopupSymbols.getOrElse(symbolPopupSelectedIndex) { symbolPopupSymbols.firstOrNull() }
                    if (selected != null) {
                        onLongPressSymbolListener?.invoke(selected)
                    }
                    dismissSymbolPopup()
                    return true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                    dismissSymbolPopup()
                    return true
                }
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    // 新触摸序列开始 — 先关闭弹窗，再正常处理本次按下
                    dismissSymbolPopup()
                    // 不 return，继续往下走正常处理
                }
                else -> {
                    return true  // 消费其他事件，不干扰弹窗
                }
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y
                pressedIndex = findKeyIndex(x, y)
                spaceLongPressFired = false
                isDeleteRepeating = false
                deleteRepeatCount = 0
                invalidate()
                if (pressedIndex >= 0) {
                    val key = keys[pressedIndex]
                    if (key.isShift) {
                        // 英文 shift: 三态切换 (长按不触发，仅单击)
                    } else if (key.isSpace) {
                        // 空格键长按触发语音输入
                        longPressRunnable = Runnable {
                            spaceLongPressFired = true
                            onMicListener?.invoke()
                        }
                        postDelayed(longPressRunnable!!, longPressTimeout.toLong())
                    } else if (key.code == CODE_DELETE) {
                        // 删除键长按连续快速删除 — 使用更短的延迟(200ms)
                        longPressRunnable = Runnable {
                            isDeleteRepeating = true
                            startDeleteRepeat()
                        }
                        postDelayed(longPressRunnable!!, deleteLongPressDelay)
                    } else if (key.secondaryLabel.isNotEmpty() && key.code > 0) {
                        // 有副标签的字母键：长按弹出副字符选择
                        val symbols = getLongPressSymbolsFor(key)
                        if (symbols.isNotEmpty()) {
                            longPressRunnable = Runnable {
                                spaceLongPressFired = true
                                showSymbolPopup(key, symbols, x)
                            }
                            postDelayed(longPressRunnable!!, longPressTimeout.toLong())
                        }
                    } else if (currentLanguage == Language.PINYIN && key.code == CODE_COMMA) {
                        // 逗号键长按弹出标点符号
                        longPressRunnable = Runnable {
                            spaceLongPressFired = true
                            showSymbolPopup(key, commaLongPress, x)
                        }
                        postDelayed(longPressRunnable!!, longPressTimeout.toLong())
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSymbolPopupShowing) {
                    // 符号弹窗显示时 — 不更新 pressedIndex
                    return true
                }
                val newIndex = findKeyIndex(x, y)
                if (newIndex != pressedIndex) {
                    pressedIndex = newIndex; invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { removeCallbacks(it) }
                // 先检查是否正在连续删除，再停止 — 顺序很重要！
                val wasDeleting = isDeleteRepeating
                stopDeleteRepeat()
                val index = findKeyIndex(x, y)
                if (spaceLongPressFired) {
                    // 长按已触发语音，不处理单击
                    pressedIndex = -1; invalidate()
                    return true
                }
                if (wasDeleting) {
                    // 长按连续删除已执行，不再触发单击删除
                    pressedIndex = -1; invalidate()
                    return true
                }
                if (index >= 0 && index < keys.size) handleKeyPress(keys[index])
                pressedIndex = -1; invalidate()

                // 空格上滑切换语言
                val dy = downY - y; val dx = abs(x - downX)
                if (dx < touchSlop && dy > touchSlop) onLangSwitchListener?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { removeCallbacks(it) }
                stopDeleteRepeat()
                spaceLongPressFired = false
                pressedIndex = -1; invalidate()
            }
        }
        return true
    }

    private fun startDeleteRepeat() {
        deleteRepeatCount = 0
        deleteRepeatRunnable = object : Runnable {
            override fun run() {
                if (isDeleteRepeating) {
                    deleteRepeatCount++
                    onKeyListener?.invoke(CODE_DELETE, "⌫", "")
                    // 前3次用较慢间隔(60ms)，之后加速到30ms — 模拟真实键盘手感
                    val interval = if (deleteRepeatCount < 3) deleteRepeatIntervalSlow else deleteRepeatIntervalFast
                    postDelayed(this, interval)
                }
            }
        }
        // 首次立即删除一个
        onKeyListener?.invoke(CODE_DELETE, "⌫", "")
        postDelayed(deleteRepeatRunnable!!, deleteRepeatIntervalSlow)
    }

    private fun stopDeleteRepeat() {
        isDeleteRepeating = false
        deleteRepeatCount = 0
        deleteRepeatRunnable?.let { removeCallbacks(it) }
        deleteRepeatRunnable = null
    }

    private fun findKeyIndex(x: Float, y: Float): Int {
        for ((index, rect) in keyRects.withIndex()) {
            if (rect.contains(x, y)) return index
        }
        return -1
    }

    private fun handleKeyPress(key: Key) {
        when (key.code) {
            CODE_SHIFT -> {
                // 英文三态: NONE → TEMP_CAPS → LOCK_CAPS → NONE
                shiftState = when (shiftState) {
                    ShiftState.NONE -> ShiftState.TEMP_CAPS
                    ShiftState.TEMP_CAPS -> ShiftState.LOCK_CAPS
                    ShiftState.LOCK_CAPS -> ShiftState.NONE
                }
                invalidate()
            }
            CODE_DELETE -> onKeyListener?.invoke(CODE_DELETE, "⌫", "")
            CODE_ENTER -> onKeyListener?.invoke(CODE_ENTER, "↵", "")
            CODE_SYMBOL -> { isSymbolMode = true; updateKeyLayout(); onSymbolListener?.invoke() }
            CODE_ABC -> { isSymbolMode = false; resetUyghurForm(); updateKeyLayout() }
            CODE_LANG_SWITCH -> onLangSwitchListener?.invoke()
            CODE_SPACE -> onKeyListener?.invoke(CODE_SPACE, " ", " ")
            CODE_HANDWRITE -> onHandwriteListener?.invoke()
            CODE_SYMBOL_AT -> {
                // 中文模式：三态切换（简易→完整→收起）
                if (currentLanguage == Language.PINYIN) {
                    onSymbolToggleListener?.invoke(CODE_SYMBOL_AT)
                } else {
                    onSymbolAtListener?.invoke()
                }
            }
            CODE_COMMA -> {
                // 中文模式：符号按钮三态切换；其他模式：直接输出逗号
                if (currentLanguage == Language.PINYIN) {
                    onSymbolToggleListener?.invoke(CODE_COMMA)
                } else {
                    val out = if (currentLanguage == Language.UYGHUR) "،" else ","
                    onKeyListener?.invoke(CODE_COMMA, out, out)
                }
            }
            CODE_PERIOD -> {
                // 中文模式：符号按钮三态切换；其他模式：直接输出句号
                if (currentLanguage == Language.PINYIN) {
                    onSymbolToggleListener?.invoke(CODE_PERIOD)
                } else {
                    onKeyListener?.invoke(CODE_PERIOD, ".", ".")
                }
            }
            CODE_AT -> onKeyListener?.invoke(CODE_AT, "@", "@")
            CODE_QUESTION -> onKeyListener?.invoke(CODE_QUESTION, "؟", "؟")
            CODE_MINUS -> onKeyListener?.invoke(CODE_MINUS, "-", "-")
            CODE_SYMBOL_PERSON -> onSymbolToggleListener?.invoke(CODE_SYMBOL_PERSON)
            CODE_DOT_QUESTION -> onKeyListener?.invoke(CODE_DOT_QUESTION, ".", ".")
            else -> {
                if (key.code > 0 && key.outputText.isNotEmpty()) {
                    val useCaps = shiftState != ShiftState.NONE && key.code < 128
                    val output = if (useCaps) key.outputText.uppercase() else key.outputText
                    onKeyListener?.invoke(key.code, key.label, output)
                    // 维语动态字母切换：按任意字母键后切换为激活态（独立元音）
                    if (currentLanguage == Language.UYGHUR && !isUyghurActiveForm) {
                        isUyghurActiveForm = true
                        updateKeyLayout()
                    }
                    // 临时大写后恢复小写
                    if (shiftState == ShiftState.TEMP_CAPS) {
                        shiftState = ShiftState.NONE
                        invalidate()
                    }
                }
            }
        }
    }

    // ============================================================
    // 公开方法
    // ============================================================

    /**
     * 获取字母键对应的长按符号列表 — 所有模式通用。
     * 维语模式：返回副标签对应字符
     * 英文模式：返回数字+大小写字母
     * 中文模式：使用原有row1/row2/row3映射，回退到副标签
     */
    private fun getLongPressSymbolsFor(key: Key): List<String> {
        // 中文模式优先使用预设映射
        if (currentLanguage == Language.PINYIN) {
            val mapped = row1LongPress[key.code] ?: row2LongPress[key.code] ?: row3LongPress[key.code]
            if (mapped != null) return mapped
        }
        // 所有模式：副标签作为长按选项
        if (key.secondaryLabel.isNotEmpty()) {
            // 构建长按列表：副标签 + 额外相关符号
            val result = mutableListOf(key.secondaryLabel)
            // 英文字母键：添加大写字母
            if (key.code > 0 && key.code < 128 && key.label[0].isLetter()) {
                val upper = key.label.uppercase()
                val lower = key.label.lowercase()
                if (!result.contains(upper)) result.add(upper)
                if (!result.contains(lower) && lower != key.label) result.add(lower)
            }
            return result
        }
        return emptyList()
    }

    /**
     * 获取字母键对应的长按符号列表。
     */
    private fun getLongPressSymbols(code: Int): List<String> {
        return row1LongPress[code] ?: row2LongPress[code] ?: row3LongPress[code] ?: emptyList()
    }

    // ============================================================
    // 长按符号弹窗 — 内联Canvas绘制（不用PopupWindow，避免触摸事件被拦截）
    // ============================================================
    private var isSymbolPopupShowing = false
    private var symbolPopupSymbols: List<String> = emptyList()
    private var symbolPopupSelectedIndex: Int = -1
    private var symbolPopupRect: RectF = RectF()  // 弹窗在键盘视图坐标系的矩形
    private var symbolPopupSymbolWidth: Float = 0f

    /**
     * 关闭符号弹窗 — 统一清理弹窗视觉状态。
     * 不重置 spaceLongPressFired，防止主 ACTION_UP 误输出字母。
     */
    private fun dismissSymbolPopup() {
        isSymbolPopupShowing = false
        symbolPopupSymbols = emptyList()
        symbolPopupSelectedIndex = -1
        symbolPopupRect = RectF()
        symbolPopupSymbolWidth = 0f
        pressedIndex = -1
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
        invalidate()
    }

    /**
     * 显示长按符号弹窗 — 内联绘制版。
     * 不创建PopupWindow，直接在onDraw中绘制，触摸事件完全由onTouchEvent处理。
     */
    private fun showSymbolPopup(key: Key, symbols: List<String>, touchX: Float) {
        val density = resources.displayMetrics.density
        val symbolWidth = 42f * density
        val popupW = (symbols.size * symbolWidth)
        val popupH = 48f * density

        // 计算弹窗位置 — 居中在按键上方
        val keyRect = keyRects.getOrNull(pressedIndex)
        val popupX = if (keyRect != null) {
            (keyRect.centerX() - popupW / 2f)
                .coerceAtLeast(0f)
                .coerceAtMost((width - popupW))
        } else {
            touchX - popupW / 2f
        }
        val popupY = ((keyRect?.top ?: 0f) - popupH - 4f * density).coerceAtLeast(0f)

        // 存储弹窗矩形和符号
        symbolPopupRect = RectF(popupX, popupY, popupX + popupW, popupY + popupH)
        symbolPopupSymbolWidth = symbolWidth
        symbolPopupSymbols = symbols
        symbolPopupSelectedIndex = 0
        isSymbolPopupShowing = true

        // 触觉反馈
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
    }

    fun switchToLanguage(lang: Language) {
        currentLanguage = lang; shiftState = ShiftState.NONE; isSymbolMode = false
        isUyghurActiveForm = false
        updateKeyLayout()
    }

    fun switchToNextLanguage() {
        currentLanguage = when (currentLanguage) {
            Language.UYGHUR -> Language.PINYIN
            Language.PINYIN -> Language.ENGLISH
            Language.ENGLISH -> Language.UYGHUR
        }
        shiftState = ShiftState.NONE; isSymbolMode = false
        isUyghurActiveForm = false
        updateKeyLayout()
    }

    fun setUyghurLayout(layout: UyghurLayout) {
        uyghurLayoutType = layout
        if (currentLanguage == Language.UYGHUR) updateKeyLayout()
    }

    fun setChineseLayout(layout: ChineseLayout) {
        chineseLayoutType = layout
        if (currentLanguage == Language.PINYIN) updateKeyLayout()
    }

    fun exitSymbolMode() {
        isSymbolMode = false
        resetUyghurForm()
        updateKeyLayout()
    }

    /**
     * 重置维语键盘为初始态（hamza 前缀元音）。
     * 由 IME Service 在空格、提交候选词、删除清空 buffer 时调用。
     */
    fun resetUyghurForm() {
        if (currentLanguage == Language.UYGHUR && isUyghurActiveForm) {
            isUyghurActiveForm = false
            updateKeyLayout()
        }
    }

    val isInSymbolMode: Boolean get() = isSymbolMode
    val isDeleting: Boolean get() = isDeleteRepeating
}
