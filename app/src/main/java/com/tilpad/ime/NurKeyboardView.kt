package com.tilpad.ime

import android.content.Context
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * 自定义键盘视图，继承自框架 [KeyboardView]。
 *
 * 职责：
 * - 预加载三种语言的 [Keyboard] 布局（维语 / 中文 / 英文）。
 * - 维护当前语言状态 [currentLanguage]。
 * - 维护符号键盘切换状态 [isSymbolMode]。
 * - 维护维语 Shift 层切换状态 [isUyghurShifted]。
 * - 提供 [switchToNextLanguage] 方法，切换时自动加载对应布局。
 */
@Suppress("DEPRECATION")
class NurKeyboardView(
    context: Context,
    attrs: AttributeSet?
) : KeyboardView(context, attrs) {

    /** 支持的语言模式 */
    enum class Language {
        UYGHUR,
        CHINESE,
        ENGLISH
    }

    /** 当前语言（初始为维语） */
    var currentLanguage: Language = Language.UYGHUR
        private set

    /** 三种语言对应的 Keyboard 实例 */
    private val keyboards: Map<Language, Keyboard> = mapOf(
        Language.UYGHUR  to Keyboard(context, R.xml.keyboard_uyghur),
        Language.CHINESE to Keyboard(context, R.xml.keyboard_chinese),
        Language.ENGLISH to Keyboard(context, R.xml.keyboard_english)
    )

    /** 维语 Shift 层键盘（补全缺失字母） */
    private val uyghurShiftKeyboard: Keyboard = Keyboard(context, R.xml.keyboard_uyghur_shift)

    /** 数字符号键盘 */
    private val symbolsKeyboard: Keyboard = Keyboard(context, R.xml.keyboard_symbols)

    /** 当前是否在符号/数字模式 */
    var isSymbolMode: Boolean = false
        private set

    /** 当前维语是否在 Shift 层 */
    var isUyghurShifted: Boolean = false
        private set

    init {
        setKeyboard(keyboards[Language.UYGHUR]!!)
        isPreviewEnabled = true
    }

    /**
     * 切换到下一种语言（维 → 中 → 英 → 维 循环）。
     */
    fun switchToNextLanguage() {
        currentLanguage = when (currentLanguage) {
            Language.UYGHUR  -> Language.CHINESE
            Language.CHINESE -> Language.ENGLISH
            Language.ENGLISH -> Language.UYGHUR
        }
        isUyghurShifted = false
        isSymbolMode = false
        setKeyboard(keyboards[currentLanguage]!!)
    }

    /**
     * 维语 Shift 切换：在主键盘和 Shift 层之间来回切换。
     */
    fun toggleUyghurShift() {
        if (currentLanguage != Language.UYGHUR || isSymbolMode) return
        isUyghurShifted = !isUyghurShifted
        if (isUyghurShifted) {
            setKeyboard(uyghurShiftKeyboard)
        } else {
            setKeyboard(keyboards[Language.UYGHUR]!!)
        }
    }

    /**
     * 切换到数字符号键盘。
     */
    fun switchToSymbols() {
        isSymbolMode = true
        setKeyboard(symbolsKeyboard)
    }

    /**
     * 从数字符号键盘返回到当前语言的字母键盘。
     */
    fun switchBackFromSymbols() {
        isSymbolMode = false
        if (isUyghurShifted) {
            setKeyboard(uyghurShiftKeyboard)
        } else {
            setKeyboard(keyboards[currentLanguage]!!)
        }
    }

    companion object {
        const val KEYCODE_LANGUAGE_SWITCH = -101
        const val KEYCODE_CLEAR = -102
        const val KEYCODE_SYMBOL_SWITCH = -103
    }
}
