package com.tilpad.ime

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * Emoji 表情面板视图，继承自 [android.widget.FrameLayout]。
 *
 * 布局结构（自上而下）：
 *  1. 顶栏：左上角返回按钮 + 中部页码指示（如 "1 / 3"）。
 *  2. 中部：[ViewPager2]，支持左右滑动翻页；每页是一个 [GridView]，列数按分类可变（默认 8 列，颜文字 4 列）。
 *  3. 底部：10 个分类标签（最近、笑脸、手势与身体、动物、食物、活动、物品、符号、旗帜、颜文字）。
 *
 * 交互：
 *  - 底部标签切换分类；[ViewPager2] 在分类内部左右滑动翻页。
 *  - 点击 emoji 通过 [setOnEmojiClickListener] 回调输出，同时自动加入「最近」分类（去重置顶，最多 30 个，持久化至 SharedPreferences）。
 *  - 点击左上角返回按钮通过 [setOnBackListener] 回调（通常用于返回键盘）。
 *  - [setDarkMode] 切换暗色模式。
 *
 * 面板位置由父容器决定（显示在键盘位置），本视图填满父容器。
 * 每页 emoji 数量会根据可用高度动态计算，使页面尽量填满高度。
 *
 * 使用示例：
 * ```
 * val panel = EmojiPanelView(context)
 * panel.setOnEmojiClickListener { emoji -> inputConnection.commitText(emoji, 1) }
 * panel.setOnBackListener { hidePanel() }
 * panel.setDarkMode(true)
 * ```
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

    // ============================================================
    // emoji 分类数据结构（支持每分类可变列数）
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
    private var firstVisibleEmojiIndex = 0   // 当前可见页首个 emoji 在分类列表中的绝对下标，用于重建页面时保持位置
    private val emojiColumns = 8
    private var rowsPerPage = 6   // 每页行数，布局后按高度重算
    private var emojisPerPage = emojiColumns * rowsPerPage   // 默认每页数量，布局后按高度重算

    // ============================================================
    // 最近 emoji 持久化（SharedPreferences）
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
    private lateinit var pageIndicator: TextView
    private lateinit var viewPager: ViewPager2
    private lateinit var tabScrollView: HorizontalScrollView
    private lateinit var tabContainer: LinearLayout
    private val tabViews = mutableListOf<TextView>()
    private var pageAdapter: EmojiPageAdapter

    // ============================================================
    // 回调
    // ============================================================
    private var onEmojiClickListener: ((String) -> Unit)? = null
    private var onBackListener: (() -> Unit)? = null

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
            "🕺", "👯", "🕴", "🚶", "🏃", "👫", "👭", "👬", "👍🏻", "👍🏼",
            "👍🏽", "👍🏾", "👍🏿", "👎🏻", "👎🏼", "👎🏽", "👎🏾", "👎🏿", "👏🏻", "👏🏼",
            "👏🏽", "👏🏾", "👏🏿", "🙌🏻", "🙌🏼", "🙌🏽", "🙌🏾", "🙌🏿", "🙏🏻", "🙏🏼",
            "🙏🏽", "🙏🏾", "🙏🏿", "🤝🏻", "🤝🏼", "🤝🏽", "🤝🏾", "🤝🏿", "💪🏻", "💪🏼",
            "💪🏽", "💪🏾", "💪🏿", "✌🏻", "✌🏼", "✌🏽", "✌🏾", "✌🏿", "🤞🏻", "🤞🏼",
            "🤞🏽", "🤞🏾", "🤞🏿", "🤟🏻", "🤟🏼", "🤟🏽", "🤟🏾", "🤟🏿", "🤘🏻", "🤘🏼",
            "🤘🏽", "🤘🏾", "🤘🏿", "👈🏻", "👈🏼", "👈🏽", "👈🏾", "👈🏿", "👉🏻", "👉🏼",
            "👉🏽", "👉🏾", "👉🏿", "👆🏻", "👆🏼", "👆🏽", "👆🏾", "👆🏿", "👇🏻", "👇🏼",
            "👇🏽", "👇🏾", "👇🏿", "☝🏻", "☝🏼", "☝🏽", "☝🏾", "☝🏿", "✋🏻", "✋🏼",
            "✋🏽", "✋🏾", "✋🏿", "🤚🏻", "🤚🏼", "🤚🏽", "🤚🏾", "🤚🏿", "🖐🏻", "🖐🏼",
            "🖐🏽", "🖐🏾", "🖐🏿", "🖖🏻", "🖖🏼", "🖖🏽", "🖖🏾", "🖖🏿", "👋🏻", "👋🏼",
            "👋🏽", "👋🏾", "👋🏿", "🤙🏻", "🤙🏼", "🤙🏽", "🤙🏾", "🤙🏿", "✍🏻", "✍🏼",
            "✍🏽", "✍🏾", "✍🏿", "💅🏻", "💅🏼", "💅🏽", "💅🏾", "💅🏿", "🤳🏻", "🤳🏼",
            "🤳🏽", "🤳🏾", "🤳🏿"
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
            "🦩", "🐇", "🦝", "🦨", "🦡", "🐁", "🐀", "🐿️", "🦔", "🐾",
            "🐉", "🐲", "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "☘️",
            "🍀", "🎍", "🎋", "🍃", "🍂", "🍁", "🍄", "🐚", "🌾", "💐",
            "🌷", "🌹", "🥀", "🌺", "🌸", "🌼", "🌻", "🌞", "🌝", "🌛",
            "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔",
            "🌙", "🌎", "🌍", "🌏", "💫", "⭐️", "🌟", "✨", "⚡️", "☄️",
            "💥", "🔥", "🌪", "🌈", "☀️", "🌤", "🌥", "☁️", "🌦", "🌨",
            "🌬", "☔️"
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
            "🇨🇿", "🇸🇰", "🇭🇺", "🇦🇹", "🇨🇭", "🇷🇺", "🇺🇦", "🇧🇾", "🇲🇩", "🇷🇴",
            "🇧🇬", "🇬🇷", "🇷🇸", "🇲🇪", "🇲🇰", "🇭🇷", "🇸🇮", "🇦🇱", "🇧🇦", "🇲🇹",
            "🇺🇸", "🇨🇦", "🇲🇽", "🇬🇹", "🇧🇿", "🇭🇳", "🇸🇻", "🇳🇮", "🇨🇷", "🇵🇦",
            "🇨🇺", "🇯🇲", "🇭🇹", "🇩🇴", "🇧🇸", "🇧🇧", "🇨🇴", "🇻🇪", "🇪🇨", "🇵🇪",
            "🇧🇴", "🇵🇾", "🇺🇾", "🇧🇷", "🇦🇷", "🇨🇱", "🇬🇾", "🇸🇷", "🇦🇺", "🇳🇿",
            "🇵🇬", "🇫🇯", "🇸🇧", "🇻🇺", "🇼🇸", "🇹🇴", "🇰🇮", "🇹🇻", "🇳🇷", "🇵🇼",
            "🇫🇲", "🇲🇭", "🇵🇳", "🇨🇰", "🇳🇺", "🇨🇨", "🇿🇦", "🇳🇬", "🇰🇪", "🇪🇹",
            "🇬🇭", "🇹🇿", "🇺🇬", "🇷🇼", "🇧🇮", "🇿🇲", "🇿🇼", "🇧🇼", "🇳🇦", "🇲🇿",
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
        "(ノ_<。)", "(Ｔ▽Ｔ)", "(；′⌒`)", "(╯°□°）╯", "(°ロ°) ≡", "(д)ノ", "( ﾟдﾟ)", "(¬_¬)",
        "(¬‿¬)", "(づ￣ 3￣)づ", "(→_→)", "(←_←)", "(.sharpshooter)", "(￣┏Д┓￣)", "(oﾟvﾟ)ノ", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧",
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
        pageAdapter = EmojiPageAdapter()
        viewPager.adapter = pageAdapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                firstVisibleEmojiIndex = position * emojisPerPage
                updatePageIndicator()
            }
        })
        buildTabs()
        showCategory(0, preservePosition = false)
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

        // ---- 顶栏：返回按钮 + 页码指示 + 右侧占位 ----
        topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
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
        pageIndicator = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
        }
        // 右侧占位，使页码指示居中
        val rightSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
        }
        topBar.addView(backBtn)
        topBar.addView(pageIndicator)
        topBar.addView(rightSpacer)

        // ---- 中部 ViewPager2 ----
        viewPager = ViewPager2(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
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
            // tabContainer 是 HorizontalScrollView 的直接子 View，
            // 需使用 FrameLayout.LayoutParams，宽度 WRAP_CONTENT 以支持横向滚动
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
            )
        }
        tabScrollView.addView(tabContainer)

        root.addView(topBar)
        root.addView(viewPager)
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
                setOnClickListener { showCategory(index, preservePosition = false) }
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
    private fun showCategory(index: Int, preservePosition: Boolean) {
        if (index !in emojiData.indices) return
        currentCategoryIndex = index
        recalcEmojisPerPage()
        if (!preservePosition) firstVisibleEmojiIndex = 0
        rebuildPages(preservePosition = preservePosition)
        updateTabColors()
        scrollTabIntoView(index)
    }

    // ============================================================
    // 重建当前分类的分页（按 emojisPerPage 切块）
    // ============================================================
    private fun rebuildPages(preservePosition: Boolean) {
        val category = emojiData[currentCategoryIndex]
        val catEmojis = category.emojis
        val pages = if (catEmojis.isEmpty()) listOf(emptyList()) else catEmojis.chunked(emojisPerPage)
        val targetPage = if (preservePosition) {
            (firstVisibleEmojiIndex / emojisPerPage).coerceIn(0, pages.size - 1)
        } else {
            0
        }
        pageAdapter.columns = category.columns
        pageAdapter.smallText = category.smallText
        pageAdapter.pages = pages
        viewPager.setCurrentItem(targetPage, false)
        firstVisibleEmojiIndex = targetPage * emojisPerPage
        updatePageIndicator()
    }

    // ============================================================
    // 页码指示器
    // ============================================================
    private fun updatePageIndicator() {
        val total = pageAdapter.pages.size
        if (total <= 1) {
            pageIndicator.text = ""
            pageIndicator.visibility = View.INVISIBLE
            return
        }
        val cur = (viewPager.currentItem + 1).coerceIn(1, total)
        pageIndicator.text = "$cur / $total"
        pageIndicator.visibility = View.VISIBLE
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
    // 根据可用高度动态计算每页 emoji 数量
    // ============================================================
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            viewPager.post { updatePageSizeFromLayout() }
        }
    }

    private fun updatePageSizeFromLayout() {
        val vpHeight = viewPager.height
        if (vpHeight <= 0) return
        val usable = vpHeight - dp(12)   // GridView 上下 padding 各 6dp
        val cellH = dp(44)
        val newRows = (usable / cellH).coerceAtLeast(1)
        if (newRows != rowsPerPage) {
            rowsPerPage = newRows
            recalcEmojisPerPage()
            rebuildPages(preservePosition = true)
        }
    }

    // ============================================================
    // 辅助方法：列数重算、最近 emoji 管理
    // ============================================================

    /** 根据当前分类的列数和已计算的行数，重新计算每页 emoji 数量 */
    private fun recalcEmojisPerPage() {
        if (currentCategoryIndex !in emojiData.indices) return
        val cols = emojiData[currentCategoryIndex].columns
        emojisPerPage = (cols * rowsPerPage).coerceAtLeast(cols)
    }

    /** 构建完整 emojiData：最近 + 8 原始分类 + 颜文字 */
    private fun buildEmojiData() {
        emojiData.clear()
        // 0: 最近（初始为空，运行时由 SharedPreferences 填充）
        emojiData.add(EmojiCategory("最近", mutableListOf()))
        // 1-8: 静态分类
        staticEmojiData.forEach { (name, emojis) ->
            emojiData.add(EmojiCategory(name, emojis.toMutableList()))
        }
        // 9: 颜文字（4 列，小字号）
        emojiData.add(EmojiCategory("颜文字", textFaceData.toMutableList(), columns = 4, smallText = true))
    }

    /** 从 SharedPreferences 加载最近 emoji 列表 */
    private fun loadRecentEmojis() {
        val saved = prefs.getString(recentPrefsKey, "") ?: ""
        recentEmojiList.clear()
        if (saved.isNotEmpty()) {
            recentEmojiList.addAll(saved.split("\n").filter { it.isNotEmpty() })
        }
        // 同步到 emojiData 的「最近」分类
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
        // 去重：若已存在则先移除
        recentEmojiList.remove(emoji)
        // 添加到最前
        recentEmojiList.add(0, emoji)
        // 限制最大数量
        while (recentEmojiList.size > maxRecent) {
            recentEmojiList.removeAt(recentEmojiList.size - 1)
        }
        // 持久化到 SharedPreferences
        prefs.edit().putString(recentPrefsKey, recentEmojiList.joinToString("\n")).apply()
        // 更新「最近」分类数据
        syncRecentToEmojiData()
        // 若当前正在查看「最近」分类，刷新页面
        if (currentCategoryIndex == 0) {
            rebuildPages(preservePosition = false)
        }
    }

    // ============================================================
    // 暗色模式 / 颜色（按 isDarkMode 取值）
    // ============================================================
    private val bgColor: Int
        get() = if (isDarkMode) colorBgDark else colorBgLight
    private val topBarColor: Int
        get() = if (isDarkMode) colorTopBarDark else colorTopBarLight
    private val tabStripColor: Int
        get() = if (isDarkMode) colorTabStripDark else colorTabStripLight
    private val tabActiveColor: Int
        get() = if (isDarkMode) colorTabActiveDark else colorTabActiveLight
    private val textColor: Int
        get() = if (isDarkMode) colorTextDark else colorTextLight
    private val cellColor: Int
        get() = if (isDarkMode) colorCellDark else colorCellLight
    private val backBtnColor: Int
        get() = if (isDarkMode) colorBackBtnDark else colorBackBtnLight
    private val hintColor: Int
        get() = if (isDarkMode) colorHintDark else colorHintLight

    private fun applyColors() {
        setBackgroundColor(bgColor)
        topBar.setBackgroundColor(topBarColor)
        backBtn.setBackgroundColor(backBtnColor)
        backBtn.setTextColor(textColor)
        pageIndicator.setTextColor(hintColor)
        tabContainer.setBackgroundColor(tabStripColor)
        viewPager.setBackgroundColor(bgColor)
        updateTabColors()
        // 通知已绑定页面刷新单元格颜色（页面数量不变，ViewPager2 会保持当前页）
        pageAdapter.notifyDataSetChanged()
        updatePageIndicator()
    }

    // ============================================================
    // 公开 API
    // ============================================================

    /** 设置 emoji 点击回调，参数为被点击的 emoji 字符串 */
    fun setOnEmojiClickListener(listener: (String) -> Unit) {
        onEmojiClickListener = listener
    }

    /** 设置返回回调（点击左上角返回按钮触发，通常用于返回键盘） */
    fun setOnBackListener(listener: () -> Unit) {
        onBackListener = listener
    }

    /** 切换暗色模式 */
    fun setDarkMode(dark: Boolean) {
        isDarkMode = dark
        applyColors()
    }

    /** 切换到指定分类（0=最近, 1=笑脸, 2=手势与身体, 3=动物, 4=食物, 5=活动, 6=物品, 7=符号, 8=旗帜, 9=颜文字） */
    fun setCategory(index: Int) {
        showCategory(index, preservePosition = false)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ============================================================
    // ViewPager2 适配器：每页一个 GridView
    // ============================================================
    private inner class EmojiPageAdapter : RecyclerView.Adapter<EmojiPageAdapter.PageViewHolder>() {

        var pages: List<List<String>> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }
        var columns: Int = emojiColumns
            set(value) {
                if (field != value) {
                    field = value
                    notifyDataSetChanged()
                }
            }
        var smallText: Boolean = false
            set(value) {
                if (field != value) {
                    field = value
                    notifyDataSetChanged()
                }
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val grid = GridView(parent.context).apply {
                numColumns = columns
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                horizontalSpacing = dp(2)
                verticalSpacing = dp(2)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                isVerticalScrollBarEnabled = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return PageViewHolder(grid)
        }

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.grid.numColumns = columns
            holder.bind(pages[position])
        }

        inner class PageViewHolder(val grid: GridView) : RecyclerView.ViewHolder(grid) {
            private val cellAdapter = EmojiCellAdapter()

            init {
                grid.adapter = cellAdapter
                grid.setOnItemClickListener { _, _, pos, _ ->
                    val emoji = cellAdapter.currentList.getOrNull(pos) ?: return@setOnItemClickListener
                    onEmojiClickListener?.invoke(emoji)
                    addToRecent(emoji)
                }
            }

            fun bind(emojis: List<String>) {
                cellAdapter.smallText = smallText
                cellAdapter.currentList = emojis
                cellAdapter.notifyDataSetChanged()
            }
        }
    }

    // ============================================================
    // 单页 emoji 网格适配器
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
