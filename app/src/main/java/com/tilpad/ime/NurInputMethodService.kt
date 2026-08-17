package com.tilpad.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * TilPad 输入法服务 v5 — 修复崩溃 + UI 渲染问题。
 *
 * 关键修复：
 * - 不再使用反射修改 KeyboardView 内部字段，改用父类公开 API
 * - 震动使用 VibrationEffect（API 26+）
 * - 处理 -100 按键码（符号切换备用）
 * - 所有视图操作增加空安全检查
 */
@Suppress("DEPRECATION")
class NurInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: NurKeyboardView? = null
    private var langBtnUyghur: TextView? = null
    private var langBtnChinese: TextView? = null
    private var langBtnEnglish: TextView? = null
    private var candidateScroll: HorizontalScrollView? = null
    private var candidateContainer: LinearLayout? = null
    private var panelContainer: View? = null
    private var emojiGrid: GridView? = null
    private var clipboardList: android.widget.ListView? = null
    private var btnEmoji: View? = null
    private var btnSettings: View? = null
    private var btnQuickUyghur: View? = null
    private var btnClipboard: View? = null
    private var rootView: View? = null

    private val composingBuffer = StringBuilder()
    private val pinyinBuffer = StringBuilder()
    private var candidates: List<String> = emptyList()

    private var emojiPanelVisible = false
    private var clipboardPanelVisible = false

    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        ClipboardHelper.checkClipboard(this)
        clipboardAdapter?.refresh(ClipboardHelper.getHistory())
    }

    private var clipboardAdapter: ClipboardAdapter? = null

    private val emojiList = listOf(
        "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆",
        "😉", "😊", "😋", "😎", "😍", "😘", "🥰", "😗",
        "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😣",
        "😥", "😮", "🤐", "😯", "😪", "😫", "🥱", "😴",
        "😛", "😜", "😝", "🤤", "😒", "😓", "😔", "😕",
        "🙃", "🤑", "😖", "😡", "😢", "😭", "😤", "👬",
        "👎", "👌", "✌", "🤞", "🤟", "🤙", "👈", "👉",
        "👆", "👇", "☝", "✋", "🤚", "🖐", "👋", "🤝",
        "💪", "🙏", "🤲", "❤", "🧡", "💛", "💚", "💙",
        "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞",
        "🔥", "⭐", "🌟", "✨", "⚡", "☀", "🌙", "☁",
        "🌈", "☂", "❄", "☃", "⚽", "🏀", "🏈", "⚾",
        "🎉", "🎊", "🎈", "🎁", "🎂", "🍰", "☕", "🍵",
        "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🥝",
        "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑",
        "🏠", "🏢", "🏥", "🏦", "🏨", "🏫", "🏬", "🏭",
        "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗"
    )

    private val symbolList = listOf(
        "，", "。", "、", "；", "：", "？", "！", "·",
        "「", "」", "【", "】", "（", "）", "《", "》",
        "…", "—", "～", "｜", "／", "＼", "＆", "％",
        "￥", "＠", "＃", "※", "☆", "★", "○", "●",
        "◎", "⊙", "◇", "◆", "□", "■", "△", "▲",
        "〡", "〢", "〣", "〤", "〥", "〦", "〧", "〨",
        "㈠", "㈡", "㈢", "㈣", "㈤", "㈥", "㈦", "㈧",
        "℃", "℉", "°", "‰", "§", "№", "〇", "⊕",
        "⊗", "⊥", "∥", "∠", "∟", "≡", "≌", "∽"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("tilpad_settings", Context.MODE_PRIVATE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.removePrimaryClipChangedListener(clipboardListener)
        super.onDestroy()
    }

    private fun isInputViewReady(): Boolean {
        return keyboardView != null && panelContainer != null && rootView != null
    }

    override fun onCreateInputView(): View? {
        val container = layoutInflater.inflate(R.layout.input_view_container, null) as View
        rootView = container

        keyboardView = container.findViewById(R.id.keyboard)
        keyboardView?.setOnKeyboardActionListener(this)

        langBtnUyghur = container.findViewById(R.id.btn_lang_uyghur)
        langBtnChinese = container.findViewById(R.id.btn_lang_chinese)
        langBtnEnglish = container.findViewById(R.id.btn_lang_english)

        candidateScroll = container.findViewById(R.id.candidate_scroll)
        candidateContainer = container.findViewById(R.id.candidate_container)

        panelContainer = container.findViewById(R.id.panel_container)
        emojiGrid = container.findViewById(R.id.emoji_grid)
        clipboardList = container.findViewById(R.id.clipboard_list)

        btnEmoji = container.findViewById(R.id.btn_emoji)
        btnSettings = container.findViewById(R.id.btn_settings)
        btnQuickUyghur = container.findViewById(R.id.btn_quick_uyghur)
        btnClipboard = container.findViewById(R.id.btn_clipboard)

        langBtnUyghur?.setOnClickListener { switchToLanguage(NurKeyboardView.Language.UYGHUR) }
        langBtnChinese?.setOnClickListener { switchToLanguage(NurKeyboardView.Language.CHINESE) }
        langBtnEnglish?.setOnClickListener { switchToLanguage(NurKeyboardView.Language.ENGLISH) }

        btnEmoji?.setOnClickListener { toggleEmojiPanel() }

        btnSettings?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        btnQuickUyghur?.setOnClickListener { switchToLanguage(NurKeyboardView.Language.UYGHUR) }
        btnClipboard?.setOnClickListener { toggleClipboardPanel() }

        setupEmojiGrid()
        setupClipboardList()
        applySkin()
        updateLanguageBar()

        ClipboardHelper.checkClipboard(this)

        return container
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingBuffer.clear()
        pinyinBuffer.clear()
        if (isInputViewReady()) {
            hideCandidates()
            hideEmojiPanel()
            hideClipboardPanel()
        }
        ClipboardHelper.checkClipboard(this)
        clipboardAdapter?.refresh(ClipboardHelper.getHistory())
    }

    override fun onFinishInput() {
        commitComposing()
        commitPinyin()
        super.onFinishInput()
    }

    // ============================================================
    // 震动和音效 — 修复：使用 VibrationEffect（API 26+）
    // ============================================================

    private fun performKeyFeedback() {
        // 震动
        if (prefs.getBoolean("vibration_enabled", true)) {
            try {
                val v = vibrator
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(15)
                    }
                }
            } catch (e: Exception) {
                // 震动失败不影响输入
            }
        }
        // 音效
        if (prefs.getBoolean("sound_enabled", false)) {
            try {
                val soundType = prefs.getInt("sound_type", 0)
                val effectId = when (soundType) {
                    0 -> AudioManager.FX_KEYPRESS_STANDARD
                    1 -> AudioManager.FX_KEYPRESS_SPACEBAR
                    2 -> AudioManager.FX_KEYPRESS_DELETE
                    3 -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
                audioManager?.playSoundEffect(effectId, 1.0f)
            } catch (e: Exception) {
                // 音效失败不影响输入
            }
        }
    }

    // ============================================================
    // 皮肤系统 — 修复：使用 updateKeyTextColor 而非反射
    // ============================================================

    private fun applySkin() {
        val skinType = prefs.getInt("skin_type", 0)
        val container = rootView ?: return
        val kv = keyboardView ?: return

        // 获取深色按键背景 Drawable
        val darkKeyBg = ContextCompat.getDrawable(this, R.drawable.key_background_dark)
        val lightKeyBg = ContextCompat.getDrawable(this, R.drawable.key_background)

        when (skinType) {
            0 -> {
                container.setBackgroundColor(Color.parseColor("#E8EAED"))
                kv.setBackgroundColor(Color.parseColor("#E8EAED"))
                kv.updateKeyTextColor(Color.parseColor("#333333"))
                lightKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(0)
            }
            1 -> {
                container.setBackgroundColor(Color.parseColor("#1A1A2E"))
                kv.setBackgroundColor(Color.parseColor("#16213E"))
                kv.updateKeyTextColor(Color.WHITE)
                darkKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(1)
            }
            2 -> {
                container.setBackgroundColor(Color.parseColor("#1A237E"))
                kv.setBackgroundColor(Color.parseColor("#283593"))
                kv.updateKeyTextColor(Color.WHITE)
                darkKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(2)
            }
            3 -> {
                container.setBackgroundColor(Color.parseColor("#1B5E20"))
                kv.setBackgroundColor(Color.parseColor("#2E7D32"))
                kv.updateKeyTextColor(Color.WHITE)
                darkKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(3)
            }
            4 -> {
                container.setBackgroundColor(Color.parseColor("#880E4F"))
                kv.setBackgroundColor(Color.parseColor("#AD1457"))
                kv.updateKeyTextColor(Color.WHITE)
                darkKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(4)
            }
            5 -> {
                container.setBackgroundColor(Color.parseColor("#E65100"))
                kv.setBackgroundColor(Color.parseColor("#EF6C00"))
                kv.updateKeyTextColor(Color.WHITE)
                darkKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(5)
            }
            6 -> {
                container.setBackgroundColor(Color.parseColor("#4A148C"))
                kv.setBackgroundColor(Color.parseColor("#6A1B9A"))
                kv.updateKeyTextColor(Color.WHITE)
                darkKeyBg?.let { kv.updateKeyBackground(it) }
                kv.setSkinType(6)
            }
            100 -> {
                val bgPath = prefs.getString("skin_image_path", null)
                if (bgPath != null) {
                    try {
                        val drawable = Drawable.createFromPath(bgPath)
                        if (drawable != null) {
                            container.background = drawable
                            kv.setBackgroundColor(Color.TRANSPARENT)
                            kv.updateKeyTextColor(Color.WHITE)
                            darkKeyBg?.let { kv.updateKeyBackground(it) }
                            kv.setSkinType(100)
                        }
                    } catch (e: Exception) {
                        container.setBackgroundColor(Color.parseColor("#E8EAED"))
                        kv.setBackgroundColor(Color.parseColor("#E8EAED"))
                        kv.updateKeyTextColor(Color.parseColor("#333333"))
                        lightKeyBg?.let { kv.updateKeyBackground(it) }
                        kv.setSkinType(0)
                    }
                }
            }
        }

        val fontSize = prefs.getFloat("font_size", 22f)
        kv.updateKeyTextSize(fontSize)
    }

    // ============================================================
    // 表情面板
    // ============================================================

    private fun setupEmojiGrid() {
        val grid = emojiGrid ?: return
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            emojiList
        )
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val ic = currentInputConnection ?: return@setOnItemClickListener
            ic.commitText(emojiList[position], 1)
            performKeyFeedback()
        }
    }

    private fun toggleEmojiPanel() {
        if (emojiPanelVisible) hideEmojiPanel() else showEmojiPanel()
    }

    private fun showEmojiPanel() {
        val panel = panelContainer ?: return
        val grid = emojiGrid ?: return
        val clist = clipboardList ?: return
        val kv = keyboardView ?: return

        emojiPanelVisible = true
        clipboardPanelVisible = false
        grid.visibility = View.VISIBLE
        clist.visibility = View.GONE
        kv.visibility = View.GONE
        panel.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (240 * resources.displayMetrics.density).toInt()
        )
        panel.visibility = View.VISIBLE
    }

    private fun hideEmojiPanel() {
        val panel = panelContainer ?: return
        val kv = keyboardView ?: return
        emojiPanelVisible = false
        panel.visibility = View.GONE
        kv.visibility = View.VISIBLE
    }

    // ============================================================
    // 剪贴板面板
    // ============================================================

    private fun setupClipboardList() {
        val list = clipboardList ?: return
        val items = ClipboardHelper.getHistory().toMutableList()
        clipboardAdapter = ClipboardAdapter(
            context = this,
            items = items,
            onPaste = { text ->
                val ic = currentInputConnection
                if (ic != null) {
                    ic.commitText(text, 1)
                    hideClipboardPanel()
                }
            },
            onDelete = { position ->
                ClipboardHelper.removeHistoryItem(position)
                clipboardAdapter?.refresh(ClipboardHelper.getHistory())
                if (ClipboardHelper.getHistory().isEmpty()) {
                    hideClipboardPanel()
                }
            }
        )
        list.adapter = clipboardAdapter
    }

    private fun toggleClipboardPanel() {
        if (clipboardPanelVisible) hideClipboardPanel() else showClipboardPanel()
    }

    private fun showClipboardPanel() {
        val panel = panelContainer ?: return
        val grid = emojiGrid ?: return
        val clist = clipboardList ?: return
        val kv = keyboardView ?: return

        ClipboardHelper.checkClipboard(this)
        clipboardAdapter?.refresh(ClipboardHelper.getHistory())

        clipboardPanelVisible = true
        emojiPanelVisible = false
        clist.visibility = View.VISIBLE
        grid.visibility = View.GONE
        kv.visibility = View.GONE
        panel.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (240 * resources.displayMetrics.density).toInt()
        )
        panel.visibility = View.VISIBLE

        if (ClipboardHelper.getHistory().isEmpty()) {
            Toast.makeText(this, "暂无剪贴板记录\n复制文字后会自动显示在这里", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideClipboardPanel() {
        val panel = panelContainer ?: return
        val kv = keyboardView ?: return
        clipboardPanelVisible = false
        panel.visibility = View.GONE
        kv.visibility = View.VISIBLE
    }

    // ============================================================
    // 语言切换
    // ============================================================

    private fun switchToLanguage(lang: NurKeyboardView.Language) {
        commitComposing()
        commitPinyin()
        hideEmojiPanel()
        hideClipboardPanel()
        keyboardView?.switchToLanguage(lang)
        updateLanguageBar()
    }

    private fun updateLanguageBar() {
        val kv = keyboardView ?: return
        val activeColor = Color.parseColor("#2563EB")
        val activeText = Color.WHITE
        val inactiveBg = Color.parseColor("#F5F5F5")
        val inactiveText = Color.parseColor("#666666")

        val current = kv.currentLanguage

        langBtnUyghur?.setBackgroundColor(if (current == NurKeyboardView.Language.UYGHUR) activeColor else inactiveBg)
        langBtnUyghur?.setTextColor(if (current == NurKeyboardView.Language.UYGHUR) activeText else inactiveText)

        langBtnChinese?.setBackgroundColor(if (current == NurKeyboardView.Language.CHINESE) activeColor else inactiveBg)
        langBtnChinese?.setTextColor(if (current == NurKeyboardView.Language.CHINESE) activeText else inactiveText)

        langBtnEnglish?.setBackgroundColor(if (current == NurKeyboardView.Language.ENGLISH) activeColor else inactiveBg)
        langBtnEnglish?.setTextColor(if (current == NurKeyboardView.Language.ENGLISH) activeText else inactiveText)
    }

    // ============================================================
    // 按键事件 — 修复：处理 -100 按键码
    // ============================================================

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        performKeyFeedback()

        when (primaryCode) {
            NurKeyboardView.KEYCODE_LANGUAGE_SWITCH -> {
                val kv = keyboardView ?: return
                switchToLanguage(
                    when (kv.currentLanguage) {
                        NurKeyboardView.Language.UYGHUR -> NurKeyboardView.Language.CHINESE
                        NurKeyboardView.Language.CHINESE -> NurKeyboardView.Language.ENGLISH
                        NurKeyboardView.Language.ENGLISH -> NurKeyboardView.Language.UYGHUR
                    }
                )
            }

            NurKeyboardView.KEYCODE_CLEAR -> {
                composingBuffer.clear()
                pinyinBuffer.clear()
                hideCandidates()
                ic.deleteSurroundingText(1000, 0)
            }

            NurKeyboardView.KEYCODE_SYMBOL_SWITCH,
            NurKeyboardView.KEYCODE_SYMBOL_SWITCH_ALT -> {
                val kv = keyboardView ?: return
                commitComposing()
                commitPinyin()
                if (kv.isSymbolMode) {
                    kv.switchBackFromSymbols()
                } else {
                    kv.switchToSymbols()
                }
            }

            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)
            Keyboard.KEYCODE_DONE -> handleEnter(ic)

            Keyboard.KEYCODE_SHIFT -> {
                val kv = keyboardView ?: return
                if (kv.currentLanguage == NurKeyboardView.Language.UYGHUR && !kv.isSymbolMode) {
                    commitComposing()
                    kv.toggleUyghurShift()
                } else {
                    kv.isShifted = !kv.isShifted
                }
            }

            else -> {
                if (primaryCode > 0) {
                    handleCharacter(primaryCode.toChar(), ic)
                }
            }
        }
    }

    override fun onText(text: CharSequence?) {
        val ic = currentInputConnection ?: return
        val outputText = text?.toString() ?: return
        val kv = keyboardView ?: return
        performKeyFeedback()

        if (kv.isSymbolMode) {
            ic.commitText(outputText, 1)
            return
        }

        when (kv.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                composingBuffer.append(outputText)
                ic.setComposingText(composingBuffer.toString(), 1)
            }
            NurKeyboardView.Language.CHINESE -> {
                ic.commitText(outputText, 1)
            }
            NurKeyboardView.Language.ENGLISH -> {
                ic.commitText(outputText, 1)
            }
        }
    }

    // ============================================================
    // 字符处理
    // ============================================================

    private fun handleCharacter(ch: Char, ic: InputConnection) {
        val kv = keyboardView ?: return

        if (kv.isSymbolMode) {
            ic.commitText(ch.toString(), 1)
            return
        }

        when (kv.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                if (ch == ' ') {
                    commitComposing()
                    ic.commitText(" ", 1)
                } else {
                    composingBuffer.append(ch)
                    ic.setComposingText(composingBuffer.toString(), 1)
                }
            }
            NurKeyboardView.Language.CHINESE -> {
                if (ch == ' ') {
                    if (candidates.isNotEmpty()) {
                        ic.commitText(candidates[0], 1)
                        pinyinBuffer.clear()
                        hideCandidates()
                    } else if (pinyinBuffer.isNotEmpty()) {
                        ic.commitText(pinyinBuffer.toString(), 1)
                        pinyinBuffer.clear()
                        hideCandidates()
                    } else {
                        ic.commitText(" ", 1)
                    }
                } else if (ch.isLetter()) {
                    pinyinBuffer.append(ch.lowercaseChar())
                    updatePinyinCandidates(ic)
                } else {
                    commitPinyin()
                    ic.commitText(ch.toString(), 1)
                }
            }
            NurKeyboardView.Language.ENGLISH -> {
                ic.commitText(ch.toString(), 1)
            }
        }
    }

    // ============================================================
    // 中文拼音候选词
    // ============================================================

    private fun updatePinyinCandidates(ic: InputConnection) {
        val pinyin = pinyinBuffer.toString()
        if (pinyin.isEmpty()) {
            hideCandidates()
            return
        }

        candidates = PinyinEngine.lookup(pinyin)

        if (candidates.isEmpty()) {
            ic.setComposingText(pinyin, 1)
            hideCandidates()
        } else {
            ic.setComposingText(candidates[0], 1)
            showCandidates()
        }
    }

    private fun showCandidates() {
        val scroll = candidateScroll ?: return
        val container = candidateContainer ?: return
        container.removeAllViews()
        if (candidates.isEmpty()) {
            hideCandidates()
            return
        }

        for ((index, candidate) in candidates.withIndex()) {
            val tv = TextView(this)
            tv.text = candidate
            tv.textSize = 16f
            tv.setTextColor(Color.parseColor("#333333"))
            tv.setPadding(24, 0, 24, 0)
            tv.gravity = android.view.Gravity.CENTER_VERTICAL
            tv.setOnClickListener {
                val ic = currentInputConnection
                if (ic != null) {
                    ic.commitText(candidate, 1)
                    pinyinBuffer.clear()
                    hideCandidates()
                }
            }
            container.addView(tv)

            if (index < candidates.size - 1) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(1, 24).apply {
                    setMargins(0, 6, 0, 6)
                }
                divider.setBackgroundColor(Color.parseColor("#DDDDDD"))
                container.addView(divider)
            }
        }

        scroll.visibility = View.VISIBLE
    }

    private fun hideCandidates() {
        candidateScroll?.visibility = View.GONE
        candidates = emptyList()
    }

    // ============================================================
    // 退格处理
    // ============================================================

    private fun handleBackspace(ic: InputConnection) {
        val kv = keyboardView ?: return

        if (kv.currentLanguage == NurKeyboardView.Language.CHINESE
            && !kv.isSymbolMode
            && pinyinBuffer.isNotEmpty()
        ) {
            pinyinBuffer.deleteCharAt(pinyinBuffer.length - 1)
            if (pinyinBuffer.isNotEmpty()) {
                updatePinyinCandidates(ic)
            } else {
                ic.setComposingText("", 0)
                hideCandidates()
            }
            return
        }

        if (kv.currentLanguage == NurKeyboardView.Language.UYGHUR
            && !kv.isSymbolMode
            && composingBuffer.isNotEmpty()
        ) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)
            if (composingBuffer.isNotEmpty()) {
                ic.setComposingText(composingBuffer.toString(), 1)
            } else {
                ic.setComposingText("", 0)
            }
            return
        }

        val selectedText = ic.getSelectedText(0)
        if (selectedText != null && selectedText.isNotEmpty()) {
            ic.commitText("", 1)
            return
        }

        ic.deleteSurroundingText(1, 0)
    }

    // ============================================================
    // 回车处理
    // ============================================================

    private fun handleEnter(ic: InputConnection) {
        commitComposing()
        commitPinyin()

        val editorInfo = currentInputEditorInfo
        if (editorInfo != null) {
            val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            when (action) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_SEARCH -> {
                    ic.performEditorAction(action)
                    return
                }
            }
        }
        ic.commitText("\n", 1)
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private fun commitComposing() {
        if (composingBuffer.isEmpty()) return
        val ic = currentInputConnection ?: run {
            composingBuffer.clear()
            return
        }
        ic.commitText(composingBuffer.toString(), 1)
        composingBuffer.clear()
    }

    private fun commitPinyin() {
        if (pinyinBuffer.isEmpty()) return
        val ic = currentInputConnection ?: run {
            pinyinBuffer.clear()
            return
        }
        if (candidates.isNotEmpty()) {
            ic.commitText(candidates[0], 1)
        } else {
            ic.commitText(pinyinBuffer.toString(), 1)
        }
        pinyinBuffer.clear()
        hideCandidates()
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
