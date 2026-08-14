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
 * 用户在拉丁 QWERTY 键盘上输入字母，每个字母追加到 [composingBuffer]，
 * 然后调用 [UyghurConverter.convert] 将整个缓冲区转换为阿拉伯字母维语，
 * 通过 [InputConnection.setComposingText] 实时预览（带下划线）。
 * 当用户按下空格或回车时，调用 [commitComposingText] 将转换结果
 * 通过 [InputConnection.commitText] 正式上屏，并清空缓冲区。
 *
 * ### 中文 / 英文模式
 * 直接将字符通过 [InputConnection.commitText] 上屏，不做转换。
 *
 * ## RTL 处理
 * 阿拉伯文是从右向左（RTL）书写的。上屏前在文本前添加
 * U+200F（Right-to-Left Mark），确保在 LTR 上下文中也能正确渲染方向。
 *
 * ## 语言切换
 * 按下语言切换键（🌐）时，先提交当前 composing 文本，
 * 再调用 [NurKeyboardView.switchToNextLanguage] 切换布局（维→中→英→维循环）。
 */
@Suppress("DEPRECATION")
class NurInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: NurKeyboardView

    /** 拉丁维语 composing 缓冲区（仅维语模式使用） */
    private val composingBuffer = StringBuilder()

    /** 转换引擎实例 */
    private val converter = UyghurConverter

    // ============================================================
    // 生命周期
    // ============================================================

    /**
     * 系统首次需要显示键盘时回调。
     * 加载 [R.layout.keyboard_view] 布局，设置按键监听器，返回键盘视图。
     */
    override fun onCreateInputView(): View? {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as NurKeyboardView
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    /**
     * 开始输入（进入新的文本框）时回调。
     * 清空 composing 缓冲区，避免上一个文本框的残留。
     */
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        composingBuffer.clear()
    }

    /**
     * 结束输入时回调。
     * 提交未完成的 composing 文本。
     */
    override fun onFinishInput() {
        super.onFinishInput()
        currentInputConnection?.let { commitComposingText(it) }
    }

    // ============================================================
    // KeyboardView.OnKeyboardActionListener
    // ============================================================

    /**
     * 按键回调核心方法。根据 [primaryCode] 分发到对应处理逻辑。
     */
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            // 语言切换键
            NurKeyboardView.KEYCODE_LANGUAGE_SWITCH -> switchLanguage()

            // 清空 composing 缓冲区键
            NurKeyboardView.KEYCODE_CLEAR -> {
                composingBuffer.clear()
                ic.setComposingText("", 0)
                ic.finishComposingText()
            }

            // 退格 / 删除键
            Keyboard.KEYCODE_DELETE -> handleBackspace(ic)

            // 回车 / 完成键
            Keyboard.KEYCODE_DONE -> handleEnter(ic)

            // Shift 键（原型暂不处理大小写切换）
            Keyboard.KEYCODE_SHIFT -> { /* no-op */ }

            else -> {
                // 普通字符键（code > 0 表示 ASCII 字符码）
                if (primaryCode > 0) {
                    val ch = primaryCode.toChar()
                    handleCharacter(ch, ic)
                }
            }
        }
    }

    // ============================================================
    // 按键处理
    // ============================================================

    /**
     * 处理普通字符输入。
     *
     * - **维语模式**：
     *   - 空格键：先提交当前 composing 文本（上屏阿拉伯文），再插入空格。
     *   - 其他字符：追加到 [composingBuffer]，转换后通过 setComposingText 实时预览。
     * - **中文/英文模式**：直接 commitText 上屏该字符。
     */
    private fun handleCharacter(ch: Char, ic: InputConnection) {
        when (keyboardView.currentLanguage) {
            NurKeyboardView.Language.UYGHUR -> {
                if (ch == ' ') {
                    // 空格：先提交当前词，再插入空格
                    commitComposingText(ic)
                    ic.commitText(" ", 1)
                } else {
                    composingBuffer.append(ch)
                    val converted = converter.convert(composingBuffer.toString())
                    ic.setComposingText(applyRtl(converted), 1)
                }
            }

            NurKeyboardView.Language.CHINESE,
            NurKeyboardView.Language.ENGLISH -> {
                // 中英文模式直接上屏
                commitComposingText(ic)
                ic.commitText(ch.toString(), 1)
            }
        }
    }

    /**
     * 处理退格键。
     *
     * - **维语模式 + 缓冲区非空**：删除缓冲区最后一个字符，更新预览。
     * - **其他情况**：删除光标前一个已上屏字符（deleteSurroundingText）。
     */
    private fun handleBackspace(ic: InputConnection) {
        if (keyboardView.currentLanguage == NurKeyboardView.Language.UYGHUR
            && composingBuffer.isNotEmpty()
        ) {
            composingBuffer.deleteCharAt(composingBuffer.length - 1)
            if (composingBuffer.isEmpty()) {
                ic.setComposingText("", 0)
                ic.finishComposingText()
            } else {
                val converted = converter.convert(composingBuffer.toString())
                ic.setComposingText(applyRtl(converted), 1)
            }
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    /**
     * 处理回车/完成键。
     *
     * 先提交 composing 文本，再根据 [EditorInfo.imeOptions] 执行对应动作
     *（DONE / GO / NEXT / SEND / SEARCH）。若无特定动作则插入换行符。
     */
    private fun handleEnter(ic: InputConnection) {
        commitComposingText(ic)

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
     * 提交 composing 缓冲区中的文本。
     *
     * 维语模式下：将缓冲区中的拉丁文转换为阿拉伯文，通过 commitText 正式上屏。
     * 注意：commitText 会自动替换当前 composing 文本，因此不需要先调用 finishComposingText()，
     * 否则会导致文本重复上屏。
     *
     * 中英文模式下：缓冲区为空，此方法为空操作。
     */
    private fun commitComposingText(ic: InputConnection) {
        if (composingBuffer.isNotEmpty()) {
            val converted = converter.convert(composingBuffer.toString())
            // commitText 自动替换当前 composing 文本并结束 composing 状态
            ic.commitText(applyRtl(converted), 1)
            composingBuffer.clear()
        }
    }

    /**
     * 切换语言（维 → 中 → 英 → 维 循环）。
     * 切换前先提交未完成的 composing 文本，避免丢失输入。
     */
    private fun switchLanguage() {
        currentInputConnection?.let { commitComposingText(it) }
        keyboardView.switchToNextLanguage()
    }

    /**
     * 为阿拉伯文添加 RTL 标记（U+200F Right-to-Left Mark）。
     *
     * 阿拉伯字母本身具有内在的 RTL 方向性，Android 文本引擎会自动做字符
     * shaping 和双向排列。但在 LTR 上下文（如英文 EditText）中混排时，
     * 段落方向可能默认为 LTR，导致标点位置异常。
     * 在文本前添加 RLM 可提示渲染引擎将段落方向设为 RTL。
     *
     * 注：RLM 是零宽不可见字符，不会影响可见文本内容。
     */
    private fun applyRtl(text: String): String {
        return "\u200F$text"
    }

    // ============================================================
    // OnKeyboardActionListener 其余回调（原型留空）
    // ============================================================

    /** 处理 Keyboard XML 中 keyOutputText 设置的文本输出（直接上屏） */
    override fun onText(text: CharSequence?) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}

    override fun swipeLeft() {}

    override fun swipeRight() {}

    override fun swipeDown() {}

    override fun swipeUp() {}
}
