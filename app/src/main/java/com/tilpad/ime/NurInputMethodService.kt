package com.tilpad.ime

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
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

/**
 * TilPad 输入法服务 v3 — 完整版。
 *
 * 功能：
 * 1. 顶部工具栏（下拉/齿轮/布局/表情/头像/快捷图标/维语/中文/En）
 * 2. 候选词栏（中文拼音）
 * 3. 表情面板（可切换显示）
 * 4. 维语 composing 连写（不改动）
 * 5. 中文拼音输入
 * 6. 删除键修复
 */
@Suppress("DEPRECATION")
class NurInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: NurKeyboardView
    private lateinit var langBtnUyghur: TextView
    private lateinit var langBtnChinese: TextView
    private lateinit var langBtnEnglish: TextView
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var candidateContainer: LinearLayout
    private lateinit var panelContainer: View
    private lateinit var emojiGrid: GridView
    private lateinit var btnEmoji: View
    private lateinit var btnSettings: View
    private lateinit var btnQuickUyghur: View

    private val composingBuffer = StringBuilder()
    private val pinyinBuffer = StringBuilder()
    private var candidates: List<String> = emptyList()

    /** 表情面板是否显示 */
    private var emojiPanelVisible = false

    /** Emoji 表情列表 */
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

    override fun onCreateInputView(): View? {
        val container = layoutInflater.inflate(R.layout.input_view_container, null) as View

        keyboardView = container.findViewById(R.id.keyboard)
        keyboardView.setOnKeyboardActionListener(this)

        langBtnUyghur = container.findViewById(R.id.btn_lang_uyghur)
        langBtnChinese = container.findViewById(R.id.btn_lang_chinese)
        langBtnEnglish = container.findViewById(R.id.btn_lang_english)

        candidateScroll = container.findViewById(R.id.candidate_scroll)
        candidateContainer = container.findViewById(R.id.candidate_container)

        panelContainer = container.findViewById(R.id.panel_container)
        emojiGrid = container.findViewById(R.id.emoji_grid)

        btnEmoji = container.findViewById(R.id.btn_emoji)
        btnSettings = container.findViewById(R.id.btn_settings)
        btnQuickUyghur = container.findViewById(R.id.btn_quick_uyghur)

        // 语言切换按钮
        langBtnUyghur.setOnClickListener { switchToLanguage(NurKeyboardView.Language.UYGHUR) }
        langBtnChinese.setOnClickListener { switchToLanguage(NurKeyboardView.Language.CHINESE) }
        langBtnEnglish.setOnClickListener { switchToLanguage(NurKeyboardView.Language.ENGLISH) }

        // 表情按钮 — 切换表情面板
        btnEmoji.setOnClickListener { toggleEmojiPanel() }

        // 设置按钮 — 打开设置页
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        // 维语快捷图标 — 直接切换到维语
        btnQuickUyghur.setOnClickListener { switchToLanguage(NurKeyboardView.Language.UYGHUR) }

        // 设置 emoji grid adapter
        setupEmojiGrid()

        updateLanguageBar()

        return container
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingBuffer.clear()
        pinyinBuffer.clear()
        hideCandidates()
        hideEmojiPanel()
    }

    override fun onFinishInput() {
        commitComposing()
        commitPinyin()
        super.onFinishInput()
    }

    // ============================================================
    // 表情面板
    // ============================================================

    private fun setupEmojiGrid() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            emojiList
        )
        emojiGrid.adapter = adapter
        emojiGrid.setOnItemClickListener { _, _, position, _ ->
            val ic = currentInputConnection ?: return@setOnItemClickListener
            ic.commitText(emojiList[position], 1)
        }
    }

    private fun toggleEmojiPanel() {
        if (emojiPanelVisible) hideEmojiPanel() else showEmojiPanel()
    }

    private fun showEmojiPanel() {
        emojiPanelVisible = true
        keyboardView.visibility = View.GONE
        panelContainer.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            240 * resources.displayMetrics.density.toInt()
        )
        panelContainer.visibility = View.VISIBLE
    }

    private fun hideEmojiPanel() {
        emojiPanelVisible = false
        panelContainer.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
    }

    // ============================================================
    // 语言切换
    // ============================================================

    private fun switchToLanguage(lang: NurKeyboardView.Language) {
        commitComposing()
        commitPinyin()
        hideEmojiPanel()
        keyboardView.switchToLanguage(lang)
        updateLanguageBar()
    }

    private fun updateLanguageBar() {
        val activeColor = Color.parseColor("#2563EB")
        val activeText = Color.WHITE
        val inactiveBg = Color.parseColor("#F5F5F5")
        val inactiveText = Color.parseColor("#666666")

        val current = keyboardView.currentLanguage

        langBtnUyghur.setBackgroundColor(if (current == NurKeyboardView.Language.UYGHUR) activeColor else inactiveBg)
        langBtnUyghur.setTextColor(if (current == NurKeyboardView.Language.UYGHUR) activeText else inactiveText)

        langBtnChinese.setBackgroundColor(if (current == NurKeyboardView.Language.CHINESE) activeColor else inactiveBg)
        langBtnChinese.setTextColor(if (current == NurKeyboardView.Language.CHINESE) activeText else inactiveText)

        langBtnEnglish.setBackgroundColor(if (current == NurKeyboardView.Language.ENGLISH) activeColor else inactiveBg)
        langBtnEnglish.setTextColor(if (current == NurKeyboardView.Language.ENGLISH) activeText else inactiveText)
    }

    // ============================================================
    // 按键事件
    // ============================================================

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            NurKeyboardView.KEYCODE_LANGUAGE_SWITCH -> {
                switchToLanguage(
                    when (keyboardView.currentLanguage) {
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

            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)
            Keyboard.KEYCODE_DONE -> handleEnter(ic)

            Keyboard.KEYCODE_SHIFT -> {
                if (keyboardView.currentLanguage == NurKeyboardView.Language.UYGHUR && !keyboardView.isSymbolMode) {
                    commitComposing()
                    keyboardView.toggleUyghurShift()
                } else {
                    keyboardView.isShifted = !keyboardView.isShifted
                }
            }

            NurKeyboardView.KEYCODE_SYMBOL_SWITCH -> {
                commitComposing()
                commitPinyin()
                if (keyboardView.isSymbolMode) {
                    keyboardView.switchBackFromSymbols()
                } else {
                    keyboardView.switchToSymbols()
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

        if (keyboardView.isSymbolMode) {
            ic.commitText(outputText, 1)
            return
        }

        when (keyboardView.currentLanguage) {
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
        if (keyboardView.isSymbolMode) {
            ic.commitText(ch.toString(), 1)
            return
        }

        when (keyboardView.currentLanguage) {
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
        candidateContainer.removeAllViews()
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
            candidateContainer.addView(tv)

            if (index < candidates.size - 1) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(1, 24).apply {
                    setMargins(0, 6, 0, 6)
                }
                divider.setBackgroundColor(Color.parseColor("#DDDDDD"))
                candidateContainer.addView(divider)
            }
        }

        candidateScroll.visibility = View.VISIBLE
    }

    private fun hideCandidates() {
        candidateScroll.visibility = View.GONE
        candidates = emptyList()
    }

    // ============================================================
    // 退格处理
    // ============================================================

    private fun handleBackspace(ic: InputConnection) {
        // 1. 中文拼音模式
        if (keyboardView.currentLanguage == NurKeyboardView.Language.CHINESE
            && !keyboardView.isSymbolMode
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

        // 2. 维语 composing 模式
        if (keyboardView.currentLanguage == NurKeyboardView.Language.UYGHUR
            && !keyboardView.isSymbolMode
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

        // 3. 有选中文本
        val selectedText = ic.getSelectedText(0)
        if (selectedText != null && selectedText.isNotEmpty()) {
            ic.commitText("", 1)
            return
        }

        // 4. 普通删除
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
