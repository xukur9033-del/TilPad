package com.tilpad.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
 * - 通过父类公开 API setKeyTextColor / setKeyTextSize 设置文字颜色和大小。
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

    /** 当前皮肤类型 — 0=默认浅色, 1-6=深色主题, 100=图片背景 */
    var currentSkinType: Int = 0
        private set

    /**
     * 设置皮肤类型，用于 onDraw 绘制不同颜色的按键背景。
     */
    fun setSkinType(type: Int) {
        currentSkinType = type
        invalidate()
    }

    /**
     * 覆写 onDraw — 实现截图风格的按键颜色方案：
     * - shift(⇧) / 回车(↵)：鲜亮蓝色背景 + 纯白色文字/图标
     * - 普通字母按键：白色底色 + 黑色文字（由父类 XML 属性绘制）
     * - 符号键/数字切换/删除等功能键：浅灰色背景
     *
     * 实现方式：先调用 super.onDraw() 让父类绘制所有按键（白色底+黑字），
     * 然后在 shift/回车 按键上叠加蓝色背景 + 白色文字，
     * 在功能键上叠加灰色背景 + 深色文字。
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val keyboard = this.keyboard ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val density = resources.displayMetrics.density
        val cornerRadius = 8f * density

        // 获取按键文字大小（通过反射读取父类的 mKeyTextSize 字段）
        val textSizePx = try {
            val field = KeyboardView::class.java.getDeclaredField("mKeyTextSize")
            field.isAccessible = true
            field.getFloat(this)
        } catch (e: Exception) {
            22f * density
        }

        paint.textSize = textSizePx
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.CENTER

        val isDarkSkin = currentSkinType in 1..6 || currentSkinType == 100

        for (key in keyboard.keys) {
            val code = if (key.codes.isNotEmpty()) key.codes[0] else 0

            when (code) {
                // Shift(⇧) 和 回车(↵) — 鲜亮蓝色背景 + 白色文字
                Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_DONE -> {
                    drawKeyBackground(canvas, paint, key, 0xFF2563EB.toInt(), cornerRadius)
                    drawKeyLabel(canvas, paint, key, Color.WHITE, textSizePx)
                }
                // 删除(⌫) / 符号切换 / 数字切换 / 语言切换 — 浅灰色背景
                Keyboard.KEYCODE_DELETE,
                KEYCODE_SYMBOL_SWITCH,
                KEYCODE_SYMBOL_SWITCH_ALT,
                KEYCODE_LANGUAGE_SWITCH,
                KEYCODE_CLEAR -> {
                    if (isDarkSkin) {
                        // 深色主题：功能键用深灰
                        drawKeyBackground(canvas, paint, key, 0xFF3A3A4A.toInt(), cornerRadius)
                        drawKeyLabel(canvas, paint, key, Color.WHITE, textSizePx)
                    } else {
                        // 浅色主题：功能键用浅灰
                        drawKeyBackground(canvas, paint, key, 0xFFD3D7DB.toInt(), cornerRadius)
                        drawKeyLabel(canvas, paint, key, 0xFF333333.toInt(), textSizePx)
                    }
                }
                // 其他按键不处理，保留父类绘制结果
            }
        }
    }

    /**
     * 绘制按键圆角背景。
     */
    private fun drawKeyBackground(
        canvas: Canvas,
        paint: Paint,
        key: Keyboard.Key,
        color: Int,
        cornerRadius: Float
    ) {
        paint.color = color
        val rect = RectF(
            key.x.toFloat(),
            key.y.toFloat(),
            (key.x + key.width).toFloat(),
            (key.y + key.height).toFloat()
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    /**
     * 绘制按键文字标签（居中）。
     */
    private fun drawKeyLabel(
        canvas: Canvas,
        paint: Paint,
        key: Keyboard.Key,
        color: Int,
        textSizePx: Float
    ) {
        val label = key.label
        if (label == null || label.isEmpty()) return

        paint.color = color
        paint.textSize = textSizePx
        paint.textAlign = Paint.Align.CENTER

        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val centerY = key.y + key.height / 2f + textHeight / 2f - fm.descent
        val centerX = key.x + key.width / 2f

        canvas.drawText(label.toString(), centerX, centerY, paint)
    }

    init {
        setKeyboard(keyboards[Language.UYGHUR]!!)
        isPreviewEnabled = true
    }

    /**
     * 更新按键文字颜色 — 使用反射调用 KeyboardView 隐藏方法。
     * SDK 34 中 setKeyTextColor 已从公开 API 移除，需用反射。
     */
    fun updateKeyTextColor(color: Int) {
        try {
            val method = KeyboardView::class.java.getMethod("setKeyTextColor", Int::class.javaPrimitiveType)
            method.invoke(this, color)
        } catch (e: Exception) {
            // 反射失败不影响输入
        }
    }

    /**
     * 更新按键文字大小 — 使用反射调用 KeyboardView 隐藏方法。
     * @param sizeSp 文字大小（sp 单位），内部自动转换为 px。
     */
    fun updateKeyTextSize(sizeSp: Float) {
        try {
            val sizePx = sizeSp * resources.displayMetrics.density
            val method = KeyboardView::class.java.getMethod("setKeyTextSize", Float::class.javaPrimitiveType)
            method.invoke(this, sizePx)
        } catch (e: Exception) {
            // 反射失败不影响输入
        }
    }

    /**
     * 更新按键背景 Drawable — 使用反射设置 KeyboardView 的 mKeyBackground 字段。
     * 用于深色主题切换时，将普通按键背景从白色切换为深色。
     */
    fun updateKeyBackground(drawable: android.graphics.drawable.Drawable) {
        try {
            val field = KeyboardView::class.java.getDeclaredField("mKeyBackground")
            field.isAccessible = true
            field.set(this, drawable)
            invalidate()
        } catch (e: Exception) {
            // 反射失败不影响输入
        }
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
     * 直接切换到指定语言（顶部语言栏用）。
     */
    fun switchToLanguage(lang: Language) {
        if (currentLanguage != lang) {
            currentLanguage = lang
            isUyghurShifted = false
            isSymbolMode = false
            setKeyboard(keyboards[lang]!!)
        }
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
        const val KEYCODE_SYMBOL_SWITCH_ALT = -100
    }
}
