package com.tilpad.ime

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

/**
 * 剪贴板历史列表适配器。
 *
 * 功能：
 * 1. 显示复制的文本预览（最多2行）
 * 2. 每项有删除按钮
 * 3. 点击整项 → 粘贴该文本
 * 4. 点击删除按钮 → 从历史中移除
 */
class ClipboardAdapter(
    private val context: Context,
    private val items: MutableList<String>,
    private val onPaste: (String) -> Unit,
    private val onDelete: (Int) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): String = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_clipboard, parent, false)

        val text = items[position]
        val tvText = view.findViewById<TextView>(R.id.clip_text)
        val btnDelete = view.findViewById<ImageView>(R.id.clip_delete)

        // 显示文本预览（替换换行为空格，方便阅读）
        val preview = text.replace("\n", " ").replace("\r", "")
        tvText.text = if (preview.length > 60) preview.substring(0, 60) + "…" else preview

        // 点击整项 → 粘贴
        view.setOnClickListener { onPaste(text) }

        // 长按 → 也粘贴（备用）
        view.setOnLongClickListener {
            onPaste(text)
            true
        }

        // 删除按钮
        btnDelete.setOnClickListener {
            onDelete(position)
        }

        return view
    }

    /**
     * 刷新数据源。
     */
    fun refresh(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
