package com.tilpad.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 剪贴板管理器 — 自动监听系统剪贴板变化。
 *
 * 功能：
 * 1. 监听系统剪贴板，自动记录复制的历史文本
 * 2. 保留最近 20 条剪贴板记录
 * 3. 在键盘上提供剪贴板面板，用户可以快速粘贴历史记录
 * 4. 支持自动识别维语、中文、英文内容
 */
object ClipboardHelper {

    /** 最大历史记录数 */
    private const val MAX_HISTORY = 20

    /** 剪贴板历史记录（最新的在前） */
    private val history = mutableListOf<String>()

    /** 获取剪贴板管理器 */
    private fun getClipboardManager(context: Context): ClipboardManager? {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    /**
     * 检查系统剪贴板，如果有新内容则加入历史。
     * 应在输入法 onStartInput 或 onUpdate 时调用。
     */
    fun checkClipboard(context: Context) {
        val cm = getClipboardManager(context) ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0).coerceToText(context).toString()
        if (text.isBlank()) return
        if (text.length > 500) return  // 忽略超长文本

        // 去重：如果最新记录和当前内容相同，不重复添加
        if (history.isNotEmpty() && history[0] == text) return

        // 加入历史（插入到最前面）
        history.add(0, text)

        // 限制历史长度
        while (history.size > MAX_HISTORY) {
            history.removeAt(history.size - 1)
        }
    }

    /**
     * 获取剪贴板历史记录列表。
     */
    fun getHistory(): List<String> = history.toList()

    /**
     * 获取当前剪贴板内容。
     */
    fun getCurrentClipboard(context: Context): String? {
        val cm = getClipboardManager(context) ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context).toString()
    }

    /**
     * 直接粘贴当前剪贴板内容到输入框。
     */
    fun pasteToInputConnection(context: Context, ic: android.view.inputmethod.InputConnection): Boolean {
        val text = getCurrentClipboard(context) ?: return false
        ic.commitText(text, 1)
        return true
    }

    /**
     * 清空历史记录。
     */
    fun clearHistory() {
        history.clear()
    }

    /**
     * 删除指定索引的历史记录。
     */
    fun removeHistoryItem(index: Int) {
        if (index in history.indices) {
            history.removeAt(index)
        }
    }
}
