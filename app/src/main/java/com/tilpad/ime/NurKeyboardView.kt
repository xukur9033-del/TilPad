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
 * - 提供 [switchToNextLanguage] 方法，切换时自动加载对应布局。
 *
 * 按键事件通过 [KeyboardView.OnKeyboardActionListener] 接口回调给
 * [NurInputMethodService] 处理（Service 实现该接口并 setOnKeyboardActionListener）。
 *
 * 注：[KeyboardView] 和 [Keyboard] 自 API 29 起标记为 deprecated，
 * 但仍可正常使用，适合原型阶段快速搭建。生产环境可考虑自绘 KeyboardView。
 */
@Suppress("DEPRECATION")
class NurKeyboardView(
    context: Context,
    attrs: AttributeSet?
) : KeyboardView(context, attrs) {

    /** 支持的语言模式 */
    enum class Language {
        /** 维语：拉丁字母输入 → 实时转换为阿拉伯字母上屏 */
        UYGHUR,

        /** 中文：拼音直接上屏（原型不含词库匹配） */
        CHINESE,

        /** 英文：字符直接上屏 */
        ENGLISH
    }

    /** 当前语言（初始为维语） */
    var currentLanguage: Language = Language.UYGHUR
        private set

    /** 三种语言对应的 Keyboard 实例，在 init 时一次性加载 */
    private val keyboards: Map<Language, Keyboard> = mapOf(
        Language.UYGHUR  to Keyboard(context, R.xml.keyboard_uyghur),
        Language.CHINESE to Keyboard(context, R.xml.keyboard_chinese),
        Language.ENGLISH to Keyboard(context, R.xml.keyboard_english)
    )

    init {
        // 设置初始键盘
        setKeyboard(keyboards[Language.UYGHUR]!!)
        // 启用按键预览弹出
        isPreviewEnabled = true
    }

    /**
     * 切换到下一种语言（维 → 中 → 英 → 维 循环）。
     * 自动加载对应的键盘 XML 布局。
     */
    fun switchToNextLanguage() {
        currentLanguage = when (currentLanguage) {
            Language.UYGHUR  -> Language.CHINESE
            Language.CHINESE -> Language.ENGLISH
            Language.ENGLISH -> Language.UYGHUR
        }
        setKeyboard(keyboards[currentLanguage]!!)
    }

    /**
     * 直接切换到指定语言。
     */
    fun switchToLanguage(language: Language) {
        if (currentLanguage != language) {
            currentLanguage = language
            setKeyboard(keyboards[language]!!)
        }
    }

    companion object {
        /**
         * 语言切换键的自定义 keyCode。
         * 使用负值避免与 ASCII 字符码（0–127）冲突。
         * 框架 Keyboard 保留 -1 ~ -6（SHIFT / MODE_CHANGE / CANCEL / DONE / DELETE / ALT），
         * 这里从 -101 开始使用自定义码。
         */
        const val KEYCODE_LANGUAGE_SWITCH = -101

        /**
         * 清空 composing 缓冲区的自定义 keyCode。
         * 用于一键清除正在编辑中的未提交文本。
         */
        const val KEYCODE_CLEAR = -102
    }
}
