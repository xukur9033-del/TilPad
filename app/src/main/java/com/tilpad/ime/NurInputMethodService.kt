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
 * ## 工作流程
 *
 * ### 维语模式（Language.UYGHUR）
 * 键盘直接显示维文阿拉伯字母，按下按键后直接输出对应字母，无需转换。
 * 按下空格键插入空格，退格键删除最后一个字符。
 * 文本上屏前添加 U+200F（RTL Mark）确保从右向左正确渲染。
 *
 * ### 中文 / 英文模式
 * 直接将字符通过 commitText 上屏，不做转换。
 *
 * ## 语言切换
 * 按下语言切换键（🌐）时，调用 switchToNextLanguage 切换布局（维→中→英→维循环）。
 */
@Suppress("DEPRECATION")
class NurInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: NurKeyboardView

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
    }

    override fun onFinishInput() {
        super.onFinishInput()
    }

    // ============================================================
    // KeyboardView.OnKeyboardActionListener
    // ============================================================

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            // 语言切换键
            NurKeyboardView.KEYCODE_LANGUAGE_SWITCH -> switchLanguage()

            // 清空键
            NurKeyboardView.KEYCODE_CLEAR -> {
                ic.deleteSurroundingText(1000, 0)
            }

            // 退格 / 删除键
            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)

            // 回车 / 完成键
            Keyboard.KEYCODE_DONE -> handleEnter(ic)

            // Shift 键
            Keyboard.KEYCODE_SHIFT -> { /* no-op */ }

            // 数字符号切换键（123 / ABC）
            NurKeyboardView.KEYCODE_SYMBOL_SWITCH -> {
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
     * 当键盘 XML 中设置了 android:keyOutputText 时，按键事件通过此回调触发。
     */
    override fun onText(text: CharSequence?) {
        val ic = currentInputConnection ?: return
        val outputText = text?.toString() ?: return

        when (keyboardView.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                // 维语模式：直接输出维文字母，添加 RTL 标记
                ic.commitText(applyRtl(outputText), 1)
            }
            NurKeyboardView.Language.CHINESE,
            NurKeyboardView.Language.ENGLISH -> {
                ic.commitText(outputText, 1)
            }
        }
    }

    // ============================================================
    // 按键处理
    // ============================================================

    private fun handleCharacter(ch: Char, ic: InputConnection) {
        when (keyboardView.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                // 维语模式：空格直接输出
                if (ch == ' ') {
                    ic.commitText(" ", 1)
                } else {
                    ic.commitText(ch.toString(), 1)
                }
            }
            NurKeyboardView.Language.CHINESE,
            NurKeyboardView.Language.ENGLISH -> {
                // 中英文模式直接上屏
                ic.commitText(ch.toString(), 1)
            }
        }
    }

    private fun handleBackspace(ic: InputConnection) {
        // 所有模式：删除光标前一个字符
        ic.deleteSurroundingText(1, 0)
    }

    private fun handleEnter(ic: InputConnection) {
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

    private fun switchLanguage() {
        keyboardView.switchToNextLanguage()
    }

    /**
     * 为阿拉伯文添加 RTL 标记（U+200F Right-to-Left Mark）。
     * 确保维文在 LTR 上下文中也能正确从右向左渲染。
     */
    private fun applyRtl(text: String): String {
        return "\u200F$text"
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
