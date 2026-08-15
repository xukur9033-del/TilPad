package com.tilpad.ime

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * TilPad 输入法服务 —— 核心控制器（完整版）。
 *
 * ## 功能列表
 *
 * ### 1. 顶部语言切换栏（Badam 风格）
 * 横条上显示 维语 / 中文 / En 三个标签，点击直接切换。
 * 当前语言的标签高亮蓝色，其他灰色。
 *
 * ### 2. 维语连写（composing 模式，已修复，不改动）
 * 维语字母累积到 [composingBuffer]，通过 setComposingText 显示连写效果。
 * 空格/回车/切换时 commitText 上屏。
 *
 * ### 3. 中文拼音输入（新增）
 * 中文模式下，输入的拼音字母累积到 [pinyinBuffer]。
 * 调用 [PinyinEngine] 查询候选词，显示在候选词栏。
 * 用户点击候选词 → commitText 上屏汉字。
 *
 * ### 4. 删除键（已修复）
 * 维语 composing：从缓冲区删最后字符
 * 中文拼音：从缓冲区删最后字符
 * 有选中文本：commitText("") 删除选中
 * 普通模式：deleteSurroundingText(1, 0)
 */
@Suppress("DEPRECATION")
class NurInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: NurKeyboardView
    private lateinit var langBtnUyghur: TextView
    private lateinit var langBtnChinese: TextView
    private lateinit var langBtnEnglish: TextView
    private lateinit var candidateScroll: HorizontalScrollView
    private lateinit var candidateContainer: LinearLayout

    /** 维语 composing 缓冲区 */
    private val composingBuffer = StringBuilder()

    /** 中文拼音缓冲区 */
    private val pinyinBuffer = StringBuilder()

    /** 当前候选词列表 */
    private var candidates: List<String> = emptyList()

    // ============================================================
    // 生命周期
    // ============================================================

    override fun onCreateInputView(): View? {
        val container = layoutInflater.inflate(R.layout.input_view_container, null) as View

        keyboardView = container.findViewById(R.id.keyboard)
        keyboardView.setOnKeyboardActionListener(this)

        langBtnUyghur = container.findViewById(R.id.btn_lang_uyghur)
        langBtnChinese = container.findViewById(R.id.btn_lang_chinese)
        langBtnEnglish = container.findViewById(R.id.btn_lang_english)

        candidateScroll = container.findViewById(R.id.candidate_scroll)
        candidateContainer = container.findViewById(R.id.candidate_container)

        // 设置语言切换按钮点击事件
        langBtnUyghur.setOnClickListener { switchToLanguage(NurKeyboardView.Language.UYGHUR) }
        langBtnChinese.setOnClickListener { switchToLanguage(NurKeyboardView.Language.CHINESE) }
        langBtnEnglish.setOnClickListener { switchToLanguage(NurKeyboardView.Language.ENGLISH) }

        updateLanguageBar()

        return container
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingBuffer.clear()
        pinyinBuffer.clear()
        hideCandidates()
    }

    override fun onFinishInput() {
        commitComposing()
        commitPinyin()
        super.onFinishInput()
    }

    // ============================================================
    // 语言切换
    // ============================================================

    private fun switchToLanguage(lang: NurKeyboardView.Language) {
        // 先提交当前未完成的内容
        commitComposing()
        commitPinyin()

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
            // 语言切换键（保留兼容旧布局，现在主用顶部栏）
            NurKeyboardView.KEYCODE_LANGUAGE_SWITCH -> {
                switchToLanguage(
                    when (keyboardView.currentLanguage) {
                        NurKeyboardView.Language.UYGHUR -> NurKeyboardView.Language.CHINESE
                        NurKeyboardView.Language.CHINESE -> NurKeyboardView.Language.ENGLISH
                        NurKeyboardView.Language.ENGLISH -> NurKeyboardView.Language.UYGHUR
                    }
                )
            }

            // 清空键
            NurKeyboardView.KEYCODE_CLEAR -> {
                composingBuffer.clear()
                pinyinBuffer.clear()
                hideCandidates()
                ic.deleteSurroundingText(1000, 0)
            }

            // 退格
            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)

            // 回车
            Keyboard.KEYCODE_DONE -> handleEnter(ic)

            // Shift
            Keyboard.KEYCODE_SHIFT -> {
                if (keyboardView.currentLanguage == NurKeyboardView.Language.UYGHUR && !keyboardView.isSymbolMode) {
                    commitComposing()
                    keyboardView.toggleUyghurShift()
                } else {
                    keyboardView.isShifted = !keyboardView.isShifted
                }
            }

            // 数字符号切换
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
                    val ch = primaryCode.toChar()
                    handleCharacter(ch, ic)
                }
            }
        }
    }

    override fun onText(text: CharSequence?) {
        val ic = currentInputConnection ?: return
        val outputText = text?.toString() ?: return

        // 符号模式下，直接输出
        if (keyboardView.isSymbolMode) {
            ic.commitText(outputText, 1)
            return
        }

        when (keyboardView.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                // 维语 composing 模式（已修复的连写逻辑，不改动）
                composingBuffer.append(outputText)
                ic.setComposingText(composingBuffer.toString(), 1)
            }
            NurKeyboardView.Language.CHINESE -> {
                // 中文模式：维语字母键不应该在中文模式下有 outputText
                // 如果有就直接输出
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
                // 中文拼音模式
                if (ch == ' ') {
                    // 空格：选第一个候选词，或直接输出拼音
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
                    // 累积拼音
                    pinyinBuffer.append(ch.lowercaseChar())
                    updatePinyinCandidates(ic)
                } else {
                    // 标点符号等直接输出
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

        // 查找候选词
        candidates = PinyinEngine.lookup(pinyin)

        if (candidates.isEmpty()) {
            // 没有候选词，显示原始拼音
            ic.setComposingText(pinyin, 1)
            hideCandidates()
        } else {
            // 显示第一个候选词为 composing，其余在候选栏
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
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setOnClickListener {
                val ic = currentInputConnection
                if (ic != null) {
                    ic.commitText(candidate, 1)
                    pinyinBuffer.clear()
                    hideCandidates()
                }
            }
            candidateContainer.addView(tv)

            // 添加分隔线
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
        // 1. 中文拼音模式：从拼音缓冲区删
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

        // 2. 维语 composing 模式：从缓冲区删
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

        // 3. 有选中文本时：删除选中内容
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
        // 如果有候选词，提交第一个；否则提交原始拼音
        if (candidates.isNotEmpty()) {
            ic.commitText(candidates[0], 1)
        } else {
            ic.commitText(pinyinBuffer.toString(), 1)
        }
        pinyinBuffer.clear()
        hideCandidates()
    }

    // ============================================================
    // OnKeyboardActionListener 其余回调
    // ============================================================

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
