package com.tilpad.ime

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
 * Emoji 表情面板视图 — 上下滑动式布局。
 *
 * 布局结构（自上而下）：
 *  1. 顶栏：返回按钮 + 删除按钮。
 *  2. 中部：单个 [GridView]，上下滑动浏览当前分类的所有 emoji。
 *  3. 底部：10 个分类标签（横向可滚动），点击切换分类。
 *
 * 交互：
 *  - 点击 emoji 通过 [setOnEmojiClickListener] 回调输出，同时加入「最近」分类。
 *  - 点击返回按钮通过 [setOnBackListener] 回调。
 *  - [setDarkMode] 切换暗色模式。
 */
class EmojiPanelView @JvmOverloads constructor(
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
    private val colorTabActiveLight = Color.parseColor("#2563EB")
    private val colorTabActiveDark = Color.parseColor("#3AADEE")
    private val colorTextLight = Color.parseColor("#333333")
    private val colorTextDark = Color.parseColor("#E8E8E8")
    private val colorCellLight = Color.parseColor("#FAFAFA")
    private val colorCellDark = Color.parseColor("#262638")
    private val colorBackBtnLight = Color.parseColor("#E4E6EB")
    private val colorBackBtnDark = Color.parseColor("#3A3A4A")
    private val colorHintLight = Color.parseColor("#888888")
    private val colorHintDark = Color.parseColor("#9AA0A6")
    private val colorDeleteBtnLight = Color.parseColor("#E4E6EB")
    private val colorDeleteBtnDark = Color.parseColor("#3A3A4A")

    // ============================================================
    // emoji 分类数据结构
    // ============================================================
    private data class EmojiCategory(
        val name: String,
        val emojis: MutableList<String>,
        val columns: Int = 8,
        val smallText: Boolean = false
    )

    // ============================================================
    // 状态
    // ============================================================
    private var isDarkMode = false
    private var currentCategoryIndex = 0
    private val emojiColumns = 8

    // ============================================================
    // 最近 emoji 持久化
    // ============================================================
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("nur_ime_prefs", Context.MODE_PRIVATE)
    }
    private val recentEmojiList = mutableListOf<String>()
    private val maxRecent = 30
    private val recentPrefsKey = "recent_emoji_list"

    // ============================================================
    // 视图引用
    // ============================================================
    private lateinit var topBar: LinearLayout
    private lateinit var backBtn: TextView
    private lateinit var deleteBtn: TextView
    private lateinit var gridView: GridView
    private lateinit var tabScrollView: HorizontalScrollView
    private lateinit var tabContainer: LinearLayout
    private val tabViews = mutableListOf<TextView>()
    private val cellAdapter = EmojiCellAdapter()

    // ============================================================
    // 回调
    // ============================================================
    private var onEmojiClickListener: ((String) -> Unit)? = null
    private var onBackListener: (() -> Unit)? = null
    private var onDeleteListener: (() -> Unit)? = null

    // ============================================================
    // emoji 静态分类数据（8 个原始分类，共 1203 emoji）
    // ============================================================
    private val staticEmojiData: List<Pair<String, List<String>>> = listOf(
        "笑脸" to listOf(
            "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊",
            "🙂", "🙃", "😋", "😎", "😍", "😘", "🥰", "😗", "😙", "😚",
            "😐", "😑", "😶", "😏", "😣", "😥", "😮", "🤐", "😯", "😪",
            "😫", "🥱", "😴", "😌", "😛", "😜", "😝", "🤤", "😒", "😓",
            "😔", "😕", "🙁", "😖", "😞", "😟", "😤", "😢", "😭", "😦",
            "😧", "😨", "😩", "🤯", "😬", "😰", "😱", "😳", "🥵", "🥶",
            "😡", "😠", "🤬", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥴",
            "🤠", "🥳", "🥺", "🤗", "🤩", "🤔", "🤨", "🤥", "🤫", "🫣",
            "🫡", "🤓", "🧐", "🥲", "🥹", "🫠", "😶‍🌫️", "🥸", "🫢", "🫨",
            "😇", "😈", "👿", "👹", "👺", "👻", "💀", "☠️", "👽", "👾",
            "🤖", "💩", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿",
            "🙈", "🙉", "🙊"
        ),
        "手势与身体" to listOf(
            "👍", "👎", "👏", "🙌", "🙏", "🤝", "💪", "✌️", "🤞", "🤟",
            "🤘", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖",
            "👋", "🤙", "✍️", "💅", "🤳", "🦾", "🦿", "🦵", "🦶", "👂",
            "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄", "💋",
            "💘", "💝", "💖", "💗", "💓", "💞", "💕", "❣️", "💔", "❤️",
            "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "👶", "👧",
            "🧒", "👦", "👩", "👨", "👵", "👴", "👲", "👳", "👮", "👷",
            "💂", "🤵", "👰", "👸", "🤴", "🦸", "🦹", "🤶", "🎅", "🧙",
            "🧚", "🧛", "🧟", "🧜", "👼", "🤰", "🤱", "🙇", "💁", "🙅",
            "🙆", "🤦", "🙋", "🤷", "🙎", "🙍", "💇", "💆", "🧖", "💃",
            "🕺", "👯", "🕴", "🚶", "🏃", "👫", "👭", "👬"
        ),
        "动物" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒",
            "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇",
            "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐜", "🕷️",
            "🕸️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐",
            "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊",
            "🐅", "🐆", "🦓", "🦍", "🦧", "🐘", "🦣", "🐪", "🐫", "🦒",
            "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🐐",
            "🦌", "🐕", "🐩", "🦮", "🐈", "🐓", "🦃", "🦚", "🦜", "🦢",
            "🦩", "🐇", "🦝", "🦨", "🦡", "🐁", "🐀", "🐿️", "🦔", "🐾"
        ),
        "食物" to listOf(
            "🍎", "🍏", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
            "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
            "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅",
            "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
            "🧇", "🥞", "🥓", "🥩", "🍗", "🍖", "🌭", "🍔", "🍟", "🍕",
            "🥪", "🥙", "🌮", "🌯", "🥗", "🥘", "🫔", "🥫", "🍝", "🍜",
            "🍲", "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘",
            "🍥", "🥠", "🥮", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰",
            "🧁", "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "🍼", "🥛", "☕",
            "🍵", "🫖", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻", "🥂", "🍷",
            "🥃", "🍸", "🍹"
        ),
        "活动" to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
            "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳",
            "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "⛸️",
            "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️", "🤺",
            "🤾", "🏌️", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴", "🏆",
            "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️", "🎪",
            "🤹", "🎭", "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁",
            "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "♟️", "🎯", "🎳", "🎮",
            "🎰", "🧩"
        ),
        "物品" to listOf(
            "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚐",
            "🚚", "🚛", "🚜", "🛴", "🚲", "🛵", "🏍", "🚨", "🚔", "🚍",
            "🚠", "🚡", "🚖", "🚘", "🚟", "🚋", "🚃", "🚞", "🚈", "🚅",
            "🚄", "🚝", "🚂", "🚉", "🛩", "🛸", "🚤", "🛥", "🚁", "💺",
            "✈️", "🚆", "🚇", "🛫", "🛰", "🛶", "🛳", "⛴", "⛵️", "🚀",
            "🛬", "🚊", "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️",
            "🖲️", "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹",
            "🎥", "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️",
            "🎚️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋",
            "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴",
            "💶", "💷", "💰", "💳", "💎", "⚖️", "🧰", "🔧", "🔨", "⚒️",
            "🛠️", "⛏️", "🔩", "⚙️", "🧱", "⛓️", "🧲", "🔫", "💣", "🧨",
            "🪄", "🔮", "🏠", "🏡", "🏢", "🏣", "🏥", "🏦", "🏨", "🏩",
            "🏪", "🏫", "🏬", "🏭", "🏯", "🏰", "💒", "🗼", "🗽", "🗿",
            "⛲️", "⛺️", "🌁", "🌃", "🌄", "🌅", "🌆", "🌇", "🌉", "🎇",
            "🎆", "🌌", "🎠", "🎡", "🎢", "💈", "🎪", "⛽️", "🚦", "🚧",
            "🚥", "🗺"
        ),
        "符号" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "🔱", "💠",
            "🔰", "♻️", "✳️", "❇️", "🌐", "⭕️", "✅", "☑️", "✔️", "❌",
            "❎", "➕", "➖", "✖️", "➗", "♾️", "‼️", "⁉️", "❓", "❔",
            "❕", "❗", "〰️", "💱", "💲", "📛", "🕉️", "☸️", "✡️", "🔯",
            "🕎", "☯️", "☦️", "☪️", "☮️", "✝️", "🔔", "🔕", "🆗", "🆒",
            "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣",
            "🔟", "#️⃣", "*️⃣", "🔢", "🔠", "🔡", "🔤", "🅰️", "🅱️", "🆑",
            "🆓", "ℹ️", "🆖", "Ⓜ️", "🆕", "🆙", "🆘", "🆚", "🈁", "🈂️",
            "🈚️", "🈯️", "🈲", "🈳", "🈴", "🈵", "🈶", "🈷️", "🈸", "🈹",
            "🈺", "🉐", "🉑", "㊗️", "㊙️", "🅾️", "🅿️", "🆎", "🆔", "←",
            "→", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙", "↩",
            "↪", "⤴", "⤵", "⇦", "⇧", "⇨", "⇩", "⇄", "⇅", "■",
            "□", "◆", "◇", "○", "●", "△", "▽", "▲", "▼", "◐",
            "◑", "◒", "◓", "◔", "◕", "◖", "◗", "◯", "◧", "◨",
            "◩", "◪", "◢", "◣", "◤", "◥", "▪", "▫", "◾", "◽",
            "◼", "◻", "⬛", "⬜", "🔲", "🔳", "🔶", "🔷", "🔸", "🔹"
        ),
        "旗帜" to listOf(
            "🇨🇳", "🇭🇰", "🇲🇴", "🇹🇼", "🇯🇵", "🇰🇷", "🇰🇵", "🇲🇳", "🇻🇳", "🇹🇭",
            "🇲🇲", "🇱🇦", "🇰🇭", "🇲🇾", "🇸🇬", "🇮🇩", "🇵🇭", "🇧🇳", "🇹🇱", "🇮🇳",
            "🇵🇰", "🇧🇩", "🇱🇰", "🇳🇵", "🇧🇹", "🇲🇻", "🇰🇿", "🇺🇿", "🇹🇲", "🇰🇬",
            "🇹🇯", "🇦🇫", "🇮🇷", "🇮🇶", "🇸🇾", "🇯🇴", "🇱🇧", "🇮🇱", "🇵🇸", "🇸🇦",
            "🇾🇪", "🇴🇲", "🇦🇪", "🇶🇦", "🇧🇭", "🇰🇼", "🇹🇷", "🇨🇾", "🇬🇪", "🇦🇲",
            "🇦🇿", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹", "🇪🇸", "🇵🇹", "🇳🇱", "🇧🇪", "🇱🇺",
            "🇮🇪", "🇸🇪", "🇳🇴", "🇩🇰", "🇫🇮", "🇮🇸", "🇱🇻", "🇪🇪", "🇱🇹", "🇵🇱",
            "🇨🇿", "🇸🇰", "🇭🇺", "🇦🇹", "🇨🇭", "🇷🇺", "🇺🇦", "🇧🇾", "🇲🇩", "🇷🇸",
            "🇧🇬", "🇬🇷", "🇷🇸", "🇲🇪", "🇲🇰", "🇭🇷", "🇸🇮", "🇦🇱", "🇧🇦", "🇲🇹",
            "🇺🇸", "🇨🇦", "🇲🇽", "🇬🇹", "🇧🇿", "🇭🇳", "🇸🇻", "🇳🇮", "🇨🇷", "🇵🇦",
            "🇨🇺", "🇯🇲", "🇭🇹", "🇩🇴", "🇧🇸", "🇧🇧", "🇨🇴", "🇻🇪", "🇪🇨", "🇵🇪",
            "🇧🇴", "🇵🇾", "🇺🇾", "🇧🇷", "🇦🇷", "🇨🇱", "🇬🇾", "🇸🇷", "🇦🇺", "🇳🇿",
            "🇵🇬", "🇫🇯", "🇸🇧", "🇻🇺", "🇼🇸", "🇹🇱", "🇰🇮", "🇹🇷", "🇳🇷", "🇵🇼",
            "🇫🇱", "🇲🇭", "🇵🇳", "🇨🇰", "🇳🇦", "🇨🇳", "🇿🇦", "🇳🇬", "🇰🇪", "🇪🇹",
            "🇬🇭", "🇹🇿", "🇺🇬", "🇷🇼", "🇧🇲", "🇿🇲", "🇿🇼", "🇧🇼", "🇳🇦", "🇲🇿",
            "🇦🇴", "🇨🇲", "🇨🇮", "🇸🇳", "🇲🇱", "🇳🇪", "🇹🇩", "🇨🇩", "🇲🇦", "🇩🇿",
            "🇹🇳", "🇱🇾", "🇸🇩", "🇸🇸", "🇪🇷", "🇩🇯", "🇰🇲", "🇸🇨", "🇲🇷", "🇱🇷",
            "🇸🇱", "🇬🇶", "🇬🇦", "🇨🇫", "🇲🇬", "🇪🇬", "🇪🇺", "🇺🇳"
        ),
    )

    // ============================================================
    // 颜文字（Text Face）数据，4 列显示
    // ============================================================
    private val textFaceData: List<String> = listOf(
        "(^_^)", "(^o^)", "(>_<)", "(T_T)", "(-_-;)", "(o_0)", "(╯°□°）╯︵ ┻━┻", "(ง'̀-'́)ง",
        "（＾ｖ＾）", "(￣▽￣)", "(￣ω￣)", "(๑•̀ㅂ•́)و✧", "(✿◡‿◡)", "(▰˘◡˘▰)", "( ͡° ͜ʖ ͡°)", "(　・ω・)→)",
        "(^^ゞ)", "(；一_一)", "(￣∩￣)", "(⊙_⊙)", "(・_・)", "( ﾟ▽ﾟ)", "(✪ω✪)", "(^_^)v",
        "(=^・ω・^=)", "╮(╯▽╰)╭", "(〜￣△￣)〜", "( ̄ε ̄)", "(´∀`)", "(・∀・)", "(」・ω・)」", "(／・ω・)/",
        "ヽ(・∀・)ﾉ", "(´・ω・`)", "(｡・ω・｡)", "(≧▽≦)", "(＞＜)", "(；・∀・)", "(`・ω・´)", "(b・∀・)b",
        "ε=ε=ε=┌(;*´Д`)ﾉ", "(☝｀▽´)☝", "ヽ(○´∀`)○ﾉ", "∑(O_O；)", "(°▽°；)", "(；￣Д￣)", "( ー̀дー́)", "(ﾟДﾟ)",
        "(╬ Ò ‸ Ó)", "(ノಠ益ಠ)ノ彡┻━┻", "┳━┳ ヽ(ಠ益ಠ)ﾉ ┳━┳", "(´；ω；`)", "(；ω；)", "(TдT)", "(╥﹏╥)", "(ಥ_ಥ)",
        "(ノ_<。)", "(´▽`)", "(；′⌒`)", "(╯°□°）╯", "(°ロ°) ≡", "(д)ノ", "( ﾟдﾟ)", "(¬_¬)",
        "(¬‿¬)", "(づ￣ 3￣)づ", "(→_→)", "(←_←)", "(　'ω')", "(￣┏Д┓￣)", "(oﾟvﾟ)ノ", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧",
        "(✧ω✧)", "(っ˘ڡ˘ς)", "(　'ω')", "(^ム^)", "(◕ω◕)", "(=ω=)", "( ˘ ³˘)♥", "(⊃｡•́‿•̀｡)⊃",
        "(っ´ω`)ﾉ", "(╥_╥)つ", "ヽ(ー_ー)ノ", "(´-ω-)", "(^・ω・^)", "(/・ω・)/", "( ^)o(^ )", "(^・ω・^ )",
        "(´･ω･`)", "( ˙-˙ )", "(°o°；)", "o_O", "(・o・)", "( ˘•ω•˘ )", "(´c_`)", "( ͡° ͜ ͡°)"
    )

    // ============================================================
    // 完整 emoji 分类数据（运行时构建：最近 + 8 原始分类 + 颜文字）
    // ============================================================
    private val emojiData: MutableList<EmojiCategory> = mutableListOf()

    init {
        buildEmojiData()
        loadRecentEmojis()
        buildLayout()
        buildTabs()
        showCategory(0)
        applyColors()
    }

    // ============================================================
    // 构建整体布局
    // ============================================================
    private fun buildLayout() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ---- 顶栏：返回按钮 + 删除按钮 ----
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
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
        }
        deleteBtn = TextView(context).apply {
            text = "⌫"
            gravity = Gravity.CENTER
            textSize = 18f
            isClickable = true
            setOnClickListener { onDeleteListener?.invoke() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
        }
        topBar.addView(backBtn)
        topBar.addView(spacer)
        topBar.addView(deleteBtn)

        // ---- 中部 GridView（上下滑动） ----
        gridView = GridView(context).apply {
            numColumns = emojiColumns
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(2)
            verticalSpacing = dp(2)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        gridView.adapter = cellAdapter
        gridView.setOnItemClickListener { _, _, pos, _ ->
            val emoji = cellAdapter.currentList.getOrNull(pos) ?: return@setOnItemClickListener
            onEmojiClickListener?.invoke(emoji)
            addToRecent(emoji)
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
        emojiData.forEachIndexed { index, (name, _) ->
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
    // 切换分类 — 直接刷新 GridView 数据
    // ============================================================
    private fun showCategory(index: Int) {
        if (index !in emojiData.indices) return
        currentCategoryIndex = index
        val category = emojiData[index]
        gridView.numColumns = category.columns
        cellAdapter.smallText = category.smallText
        cellAdapter.currentList = category.emojis.toList()
        cellAdapter.notifyDataSetChanged()
        gridView.setSelection(0)
        updateTabColors()
        scrollTabIntoView(index)
    }

    // ============================================================
    // 标签栏：高亮当前分类 + 滚动到可见
    // ============================================================
    private fun updateTabColors() {
        tabViews.forEachIndexed { i, tab ->
            val selected = i == currentCategoryIndex
            tab.setBackgroundColor(if (selected) tabActiveColor else Color.TRANSPARENT)
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
    // 辅助方法：最近 emoji 管理
    // ============================================================

    /** 构建完整 emojiData：最近 + 8 原始分类 + 颜文字 */
    private fun buildEmojiData() {
        emojiData.clear()
        emojiData.add(EmojiCategory("最近", mutableListOf()))
        staticEmojiData.forEach { (name, emojis) ->
            emojiData.add(EmojiCategory(name, emojis.toMutableList()))
        }
        emojiData.add(EmojiCategory("颜文字", textFaceData.toMutableList(), columns = 4, smallText = true))
    }

    /** 从 SharedPreferences 加载最近 emoji 列表 */
    private fun loadRecentEmojis() {
        val saved = prefs.getString(recentPrefsKey, "") ?: ""
        recentEmojiList.clear()
        if (saved.isNotEmpty()) {
            recentEmojiList.addAll(saved.split("\n").filter { it.isNotEmpty() })
        }
        syncRecentToEmojiData()
    }

    /** 将 recentEmojiList 同步到 emojiData[0] 的「最近」分类 */
    private fun syncRecentToEmojiData() {
        if (emojiData.isEmpty()) return
        emojiData[0].emojis.clear()
        emojiData[0].emojis.addAll(recentEmojiList)
    }

    /** 将 emoji 加入最近列表（去重、置顶、限制数量），并持久化 */
    private fun addToRecent(emoji: String) {
        recentEmojiList.remove(emoji)
        recentEmojiList.add(0, emoji)
        while (recentEmojiList.size > maxRecent) {
            recentEmojiList.removeAt(recentEmojiList.size - 1)
        }
        prefs.edit().putString(recentPrefsKey, recentEmojiList.joinToString("\n")).apply()
        syncRecentToEmojiData()
        if (currentCategoryIndex == 0) {
            cellAdapter.currentList = emojiData[0].emojis.toList()
            cellAdapter.notifyDataSetChanged()
        }
    }

    // ============================================================
    // 暗色模式 / 颜色
    // ============================================================
    private val bgColor: Int get() = if (isDarkMode) colorBgDark else colorBgLight
    private val topBarColor: Int get() = if (isDarkMode) colorTopBarDark else colorTopBarLight
    private val tabStripColor: Int get() = if (isDarkMode) colorTabStripDark else colorTabStripLight
    private val tabActiveColor: Int get() = if (isDarkMode) colorTabActiveDark else colorTabActiveLight
    private val textColor: Int get() = if (isDarkMode) colorTextDark else colorTextLight
    private val cellColor: Int get() = if (isDarkMode) colorCellDark else colorCellLight
    private val backBtnColor: Int get() = if (isDarkMode) colorBackBtnDark else colorBackBtnLight
    private val deleteBtnColor: Int get() = if (isDarkMode) colorDeleteBtnDark else colorDeleteBtnLight
    private val hintColor: Int get() = if (isDarkMode) colorHintDark else colorHintLight

    private fun applyColors() {
        setBackgroundColor(bgColor)
        topBar.setBackgroundColor(topBarColor)
        backBtn.setBackgroundColor(backBtnColor)
        backBtn.setTextColor(textColor)
        deleteBtn.setBackgroundColor(deleteBtnColor)
        deleteBtn.setTextColor(textColor)
        tabContainer.setBackgroundColor(tabStripColor)
        gridView.setBackgroundColor(bgColor)
        updateTabColors()
        cellAdapter.notifyDataSetChanged()
    }

    // ============================================================
    // 公开 API
    // ============================================================

    fun setOnEmojiClickListener(listener: (String) -> Unit) {
        onEmojiClickListener = listener
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
        showCategory(index)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ============================================================
    // 单页 emoji 网格适配器 — 上下滑动显示当前分类全部 emoji
    // ============================================================
    private inner class EmojiCellAdapter : BaseAdapter() {
        var currentList: List<String> = emptyList()
        var smallText: Boolean = false

        override fun getCount(): Int = currentList.size
        override fun getItem(position: Int): Any = currentList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT
                maxLines = 1
                setPadding(dp(2), dp(6), dp(2), dp(6))
                layoutParams = AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
                )
            }
            tv.text = currentList[position]
            tv.textSize = if (smallText) 14f else 24f
            tv.setBackgroundColor(cellColor)
            return tv
        }
    }
}
