package com.tilpad.ime

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * TilPad 输入法服务 —— 整个 IME 的核心控制器。
 *
 * ## 关键设计
 *
 * ### 维语连写修复（composing 模式）
 * 维语字母必须连写。之前每按一个字母就 commitText 一次，
 * 导致字母之间被系统断开，无法连写。
 *
 * 现在改为：维语字母先累积到 [composingBuffer]，通过
 * `setComposingText` 一次性发送给系统。系统看到完整的阿拉伯
 * 文本后，会自动处理字母连写（shaping）。
 *
 * 空格/回车/切换语言时，调用 [commitComposing] 把缓冲区
 * 正式上屏（commitText），然后清空缓冲区。
 *
 * 退格时，从缓冲区删除最后一个字符，更新 composing 文本。
 *
 * ### 删除键修复
 * 1. 维语 composing 模式下：从缓冲区删最后一个字符
 * 2. 有选中文本时：用 commitText("") 删除选中的文本
 * 3. 普通模式：deleteSurroundingText(1, 0)
 *
 * ### 中文模式
 * 当前原型不含拼音引擎，中文模式直接输出字母。
 * 后续可集成 Rime 引擎实现真正的中文输入。
 */
@Suppress("DEPRECATION")
class NurInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: NurKeyboardView

    /** 维语 composing 缓冲区 — 累积字母，用 setComposingText 显示连写 */
    private val composingBuffer = StringBuilder()

    // ============================================================
    // 生命周期
    // ============================================================

    override fun onCreateInputView(): View? {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as NurKeyboardView
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // 切换输入框时，清空缓冲区（系统会自动清除 composing 状态）
        composingBuffer.clear()
    }

    override fun onFinishInput() {
        // 先提交未完成的 composing 文本
        commitComposing()
        super.onFinishInput()
    }

    // ============================================================
    // 按键事件处理
    // ============================================================

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            // 语言切换键
            NurKeyboardView.KEYCODE_LANGUAGE_SWITCH -> {
                commitComposing()
                keyboardView.switchToNextLanguage()
            }

            // 清空键
            NurKeyboardView.KEYCODE_CLEAR -> {
                composingBuffer.clear()
                ic.deleteSurroundingText(1000, 0)
            }

            // 退格 / 删除键
            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)

            // 回车 / 完成键
            Keyboard.KEYCODE_DONE -> handleEnter(ic)

            // Shift 键 — 维语切换到 Shift 层，英文/中文用框架内置 Shift
            Keyboard.KEYCODE_SHIFT -> {
                if (keyboardView.currentLanguage == NurKeyboardView.Language.UYGHUR && !keyboardView.isSymbolMode) {
                    commitComposing()
                    keyboardView.toggleUyghurShift()
                } else {
                    // 英文/中文模式：用框架内置的大小写切换
                    keyboardView.isShifted = !keyboardView.isShifted
                }
            }

            // 数字符号切换键（123 / ABC）
            NurKeyboardView.KEYCODE_SYMBOL_SWITCH -> {
                commitComposing()
                if (keyboardView.isSymbolMode) {
                    keyboardView.switchBackFromSymbols()
                } else {
                    keyboardView.switchToSymbols()
                }
            }

            else -> {
                // 普通字符键（code > 0 表示 ASCII 字符码）
                if (primaryCode > 0) {
                    val ch = primaryCode.toChar()
                    handleCharacter(ch, ic)
                }
            }
        }
    }

    /**
     * 处理 keyOutputText 按键 — 维语字母直接输出。
     * 维语模式下，字母累积到 composingBuffer，通过 setComposingText 显示连写效果。
     */
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
                // 维语模式：累积到 composing 缓冲区
                // setComposingText 让系统看到完整阿拉伯文本，自动处理字母连写
                composingBuffer.append(outputText)
                ic.setComposingText(composingBuffer.toString(), 1)
            }
            NurKeyboardView.Language.CHINESE,
            NurKeyboardView.Language.ENGLISH -> {
                ic.commitText(outputText, 1)
            }
        }
    }

    // ============================================================
    // 字符处理
    // ============================================================

    private fun handleCharacter(ch: Char, ic: InputConnection) {
        // 符号模式下，直接输出
        if (keyboardView.isSymbolMode) {
            ic.commitText(ch.toString(), 1)
            return
        }

        when (keyboardView.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                if (ch == ' ') {
                    // 空格：先提交 composing 文本，再输出空格
                    commitComposing()
                    ic.commitText(" ", 1)
                } else {
                    composingBuffer.append(ch)
                    ic.setComposingText(composingBuffer.toString(), 1)
                }
            }
            NurKeyboardView.Language.CHINESE,
            NurKeyboardView.Language.ENGLISH -> {
                ic.commitText(ch.toString(), 1)
            }
        }
    }

    // ============================================================
    // 退格处理（修复删除功能）
    // ============================================================

    private fun handleBackspace(ic: InputConnection) {
        // 1. 维语 composing 模式：从缓冲区删最后一个字符
        if (keyboardView.currentLanguage == NurKeyboardView.Language.UYGHUR
            && !keyboardView.isSymbolMode
            && composingBuffer.isNotEmpty()
        ) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)
            if (composingBuffer.isNotEmpty()) {
                // 更新 composing 文本（剩余字母仍然连写）
                ic.setComposingText(composingBuffer.toString(), 1)
            } else {
                // 缓冲区空了，清除 composing 状态
                ic.setComposingText("", 0)
            }
            return
        }

        // 2. 有选中文本时：删除选中的内容
        val selectedText = ic.getSelectedText(0)
        if (selectedText != null && selectedText.isNotEmpty()) {
            // commitText("") 会替换选中文本为空，即删除
            ic.commitText("", 1)
            return
        }

        // 3. 普通删除：删除光标前一个字符
        ic.deleteSurroundingText(1, 0)
    }

    // ============================================================
    // 回车处理
    // ============================================================

    private fun handleEnter(ic: InputConnection) {
        // 先提交 composing 文本
        commitComposing()

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
        // 默认插入换行
        ic.commitText("\n", 1)
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 提交 composing 缓冲区中的文本到编辑器。
     * 在空格、回车、切换语言、切换符号页时调用。
     * commitText 会自动清除 composing 状态（下划线消失）。
     */
    private fun commitComposing() {
        if (composingBuffer.isEmpty()) return
        val ic = currentInputConnection ?: run {
            composingBuffer.clear()
            return
        }
        ic.commitText(composingBuffer.toString(), 1)
        composingBuffer.clear()
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
