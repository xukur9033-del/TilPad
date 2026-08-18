package com.tilpad.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.inputmethodservice.InputMethodService
import java.util.Locale

/**
 * TilPadInputMethodService v5 — 三语输入法服务（高级中文模式）。
 *
 * 功能：
 * - 维语：composing模式，实时候选栏刷新，ڭ独立按键
 * - 中文：拼音候选，上方显示拼音，不自动上屏
 *   - 模糊拼音支持（z/zh, c/ch, s/sh, n/l, f/h）
 *   - 句子级拼音分割（动态规划算法）
 *   - 字频排序（高频字优先）
 *   - TTS 语音朗读（选中候选词后自动朗读）
 *   - 200+ 常用词语字典
 * - 英文：直出
 * - 顶部一体工具栏：键盘图标(布局选择) + 表情图标(emoji面板) + 齿轮(快捷工具栏)
 * - Emoji面板：10分类（最近/笑脸/手势/动物/食物/活动/物品/符号/旗帜/颜文字）
 *   - 最近使用emoji持久化存储
 *   - 颜文字4列布局
 * - 候选栏实时同步刷新
 * - 回车键蓝色背景白色箭头保留
 * - 空格键长按触发语音输入
 * - 删除键长按连续快速删除
 */
class TilPadInputMethodService : InputMethodService() {

    private var keyboardView: TilPadKeyboardView? = null
    private var candidateScroll: HorizontalScrollView? = null
    private var candidateContainer: LinearLayout? = null
    private var candidateCloseBtn: TextView? = null
    private var clipboardBar: View? = null
    private var clipboardPreview: TextView? = null
    private var rootView: View? = null
    private var langBtnUyghur: TextView? = null
    private var langBtnChinese: TextView? = null
    private var langBtnEnglish: TextView? = null
    private var panelContainer: ViewGroup? = null
    private var pinyinLabel: TextView? = null

    // 面板视图
    private var emojiPanel: EmojiPanelView? = null
    private var symbolPanel: SymbolPanelView? = null
    private var quickToolbar: QuickToolbarView? = null
    private var simpleSymbolView: SimpleSymbolView? = null

    private enum class PanelType { NONE, EMOJI, SYMBOL_FULL, QUICK_TOOLBAR, SIMPLE_SYMBOL }
    private var currentPanel = PanelType.NONE

    // 符号按钮三态切换：简易符号栏 → 完整符号面板 → 收起
    private var lastSymbolButtonCode: Int = -1
    private var symbolToggleState: Int = 0  // 0=隐藏, 1=简易, 2=完整

    private val inputBuffer = StringBuilder()
    private var candidates: List<String> = emptyList()

    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var uyghurDict: UyghurDictHelper? = null

    // TTS 语音朗读引擎 — 中文模式高级功能
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("tilpad_settings", Context.MODE_PRIVATE)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        uyghurDict = UyghurDictHelper(this)
        uyghurDict?.initIfNeeded()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.addPrimaryClipChangedListener(clipboardListener)

        // 初始化 TTS 语音朗读引擎
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINA)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 回退到中文
                    tts?.setLanguage(Locale.CHINESE)
                }
                ttsReady = true
            } else {
                ttsReady = false
            }
        }
    }

    override fun onDestroy() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.removePrimaryClipChangedListener(clipboardListener)
        // 释放 TTS 引擎
        tts?.let {
            it.stop()
            it.shutdown()
        }
        tts = null
        super.onDestroy()
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        ClipboardHelper.checkClipboard(this)
        // 百度同款：剪贴板有新内容时显示粘贴提示条
        showClipboardBar()
    }

    /**
     * 显示剪贴板粘贴提示条 — 百度同款。
     * 当剪贴板有文本内容时，在键盘上方显示预览 + 粘贴按钮。
     */
    private fun showClipboardBar() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this).toString()
        if (text.isEmpty()) return
        // 预览最多显示30个字符
        val preview = if (text.length > 30) text.substring(0, 30) + "..." else text
        clipboardPreview?.text = preview
        clipboardBar?.visibility = View.VISIBLE
    }

    override fun onCreateInputView(): View? {
        val inflater = LayoutInflater.from(this)
        val container = inflater.inflate(R.layout.input_view_tilpad, null) as View
        rootView = container

        keyboardView = container.findViewById(R.id.tilpad_keyboard)
        candidateScroll = container.findViewById(R.id.candidate_scroll)
        candidateContainer = container.findViewById(R.id.candidate_container)
        candidateCloseBtn = container.findViewById(R.id.btn_candidate_close)
        clipboardBar = container.findViewById(R.id.clipboard_bar)
        clipboardPreview = container.findViewById(R.id.clipboard_preview)
        langBtnUyghur = container.findViewById(R.id.btn_lang_uyghur)
        langBtnChinese = container.findViewById(R.id.btn_lang_chinese)
        langBtnEnglish = container.findViewById(R.id.btn_lang_english)
        panelContainer = container.findViewById(R.id.panel_container)
        pinyinLabel = container.findViewById(R.id.pinyin_label)

        // 初始化面板视图
        initPanels()

        // 键盘回调
        keyboardView?.onKeyListener = { code, label, output ->
            onKeyPressed(code, label, output)
        }
        keyboardView?.onLangSwitchListener = { switchToNextLanguage() }
        keyboardView?.onMicListener = {
            Toast.makeText(this, "语音输入功能开发中", Toast.LENGTH_SHORT).show()
        }
        keyboardView?.onSymbolListener = {
            // 点击123按键 — 键盘已切换到符号模式，确保关闭任何打开的面板
            showPanel(PanelType.NONE)
        }
        keyboardView?.onSymbolAtListener = {
            // 非 中文模式：点击 @?! 按键 — 弹出简易符号页
            showPanel(PanelType.SIMPLE_SYMBOL)
        }
        keyboardView?.onSymbolToggleListener = { code ->
            // 中文模式：符号按钮三态切换
            handleSymbolToggle(code)
        }
        keyboardView?.onHandwriteListener = {
            Toast.makeText(this, "手写功能开发中", Toast.LENGTH_SHORT).show()
        }
        // 百度同款：长按字母键弹出符号选择 → 松开输入选中的符号
        keyboardView?.onLongPressSymbolListener = { symbol ->
            val ic = currentInputConnection
            if (ic != null) {
                // 先提交可能存在的composing文本
                commitComposing()
                ic.commitText(symbol, 1)
                // 维语模式：输入符号后恢复初始态元音
                keyboardView?.resetUyghurForm()
            }
        }

        // 顶部工具栏按钮
        container.findViewById<View>(R.id.btn_collapse)?.setOnClickListener {
            requestHideSelf(0)
        }
        // 键盘图标 — 弹出布局选择菜单
        container.findViewById<View>(R.id.btn_layout)?.setOnClickListener {
            showLayoutSwitchMenu()
        }
        // 表情图标 — 唤起emoji面板
        container.findViewById<View>(R.id.btn_emoji)?.setOnClickListener {
            showPanel(PanelType.EMOJI)
        }
        // 小齿轮图标 — 展开快捷工具栏
        container.findViewById<View>(R.id.btn_settings_gear)?.setOnClickListener {
            showPanel(PanelType.QUICK_TOOLBAR)
        }

        // 百度同款：候选栏关闭按钮 — 清空拼音输入并隐藏候选栏
        candidateCloseBtn?.setOnClickListener {
            inputBuffer.clear()
            val ic = currentInputConnection
            ic?.setComposingText("", 0)
            ic?.finishComposingText()
            candidates = emptyList()
            hideCandidates()
            keyboardView?.resetUyghurForm()
        }

        // 百度同款：剪贴板粘贴提示 — 点击粘贴按钮
        container.findViewById<View>(R.id.btn_paste)?.setOnClickListener {
            pasteClipboard()
        }
        // 关闭剪贴板提示条
        container.findViewById<View>(R.id.btn_clipboard_close)?.setOnClickListener {
            clipboardBar?.visibility = View.GONE
        }

        // 语言按钮
        langBtnUyghur?.setOnClickListener { switchToLanguage(TilPadKeyboardView.Language.UYGHUR) }
        langBtnChinese?.setOnClickListener { switchToLanguage(TilPadKeyboardView.Language.PINYIN) }
        langBtnEnglish?.setOnClickListener { switchToLanguage(TilPadKeyboardView.Language.ENGLISH) }

        applySettings()
        updateLanguageButtons()

        return container
    }

    // ============================================================
    // 面板初始化
    // ============================================================

    private fun initPanels() {
        val context = this

        // Emoji面板
        emojiPanel = EmojiPanelView(context).apply {
            setOnEmojiClickListener { emoji ->
                val ic = currentInputConnection
                ic?.commitText(emoji, 1)
                // TTS 朗读选中的表情（仅中文模式）
                val lang = keyboardView?.currentLanguage
                if (lang == TilPadKeyboardView.Language.PINYIN) {
                    speakText(emoji)
                }
            }
            setOnBackListener {
                // 点击面板左上角返回按钮 → 收起 emoji 面板，回到键盘
                showPanel(PanelType.NONE)
            }
            setOnDeleteListener {
                val ic = currentInputConnection
                ic?.deleteSurroundingText(1, 0)
            }
        }

        // 完整符号大页面
        symbolPanel = SymbolPanelView(context).apply {
            setOnSymbolSelectListener { symbol ->
                val ic = currentInputConnection
                ic?.commitText(symbol, 1)
            }
            setOnBackListener {
                showPanel(PanelType.NONE)
            }
            setOnDeleteListener {
                val ic = currentInputConnection
                ic?.deleteSurroundingText(1, 0)
            }
        }

        // 快捷工具栏
        quickToolbar = QuickToolbarView(context).apply {
            setOnPasteListener {
                pasteClipboard()
            }
            setOnPhraseListener { phrase ->
                val ic = currentInputConnection
                ic?.commitText(phrase, 1)
            }
        }

        // 简易符号页
        simpleSymbolView = SimpleSymbolView(context).apply {
            setOnSymbolSelectListener { symbol ->
                val ic = currentInputConnection
                ic?.commitText(symbol, 1)
                showPanel(PanelType.NONE)
            }
            setOnExpandListener {
                // 点击展开图标 → 完整符号面板
                showPanel(PanelType.SYMBOL_FULL)
                symbolToggleState = 2
            }
        }
    }

    private fun showPanel(type: PanelType) {
        val container = panelContainer ?: return
        container.removeAllViews()

        when (type) {
            PanelType.NONE -> {
                container.visibility = View.GONE
                keyboardView?.visibility = View.VISIBLE  // 恢复键盘显示
                currentPanel = PanelType.NONE
                // 重置符号按钮切换状态
                symbolToggleState = 0
                lastSymbolButtonCode = -1
            }
            PanelType.EMOJI -> {
                keyboardView?.visibility = View.GONE  // 隐藏键盘，面板在键盘位置弹出
                container.addView(emojiPanel)
                container.visibility = View.VISIBLE
                currentPanel = PanelType.EMOJI
                symbolToggleState = 0
                lastSymbolButtonCode = -1
            }
            PanelType.SYMBOL_FULL -> {
                keyboardView?.visibility = View.GONE
                container.addView(symbolPanel)
                container.visibility = View.VISIBLE
                currentPanel = PanelType.SYMBOL_FULL
            }
            PanelType.QUICK_TOOLBAR -> {
                keyboardView?.visibility = View.GONE
                container.addView(quickToolbar)
                container.visibility = View.VISIBLE
                currentPanel = PanelType.QUICK_TOOLBAR
                symbolToggleState = 0
                lastSymbolButtonCode = -1
            }
            PanelType.SIMPLE_SYMBOL -> {
                keyboardView?.visibility = View.GONE
                // 根据当前语言设置简易符号模式
                val lang = keyboardView?.currentLanguage
                val mode = when (lang) {
                    TilPadKeyboardView.Language.UYGHUR -> SimpleSymbolView.MODE_UYGHUR
                    TilPadKeyboardView.Language.ENGLISH -> SimpleSymbolView.MODE_ENGLISH
                    else -> SimpleSymbolView.MODE_CHINESE
                }
                simpleSymbolView?.setCurrentMode(mode)
                container.addView(simpleSymbolView)
                container.visibility = View.VISIBLE
                currentPanel = PanelType.SIMPLE_SYMBOL
            }
        }
    }

    // ============================================================
    // 符号按钮三态切换
    // ============================================================

    /**
     * 中文模式符号按钮三态切换：
     * - 同一按钮：隐藏 → 简易符号栏 → 完整符号面板 → 隐藏
     * - 不同按钮：直接显示简易符号栏
     */
    private fun handleSymbolToggle(code: Int) {
        if (code == lastSymbolButtonCode && symbolToggleState > 0) {
            // 同一个按钮：循环切换
            when (symbolToggleState) {
                1 -> {
                    // 简易 → 完整
                    showPanel(PanelType.SYMBOL_FULL)
                    symbolToggleState = 2
                }
                2 -> {
                    // 完整 → 隐藏
                    showPanel(PanelType.NONE)
                    symbolToggleState = 0
                    lastSymbolButtonCode = -1
                }
                else -> {
                    showPanel(PanelType.SIMPLE_SYMBOL)
                    symbolToggleState = 1
                }
            }
        } else {
            // 不同按钮：重置为简易符号栏
            lastSymbolButtonCode = code
            symbolToggleState = 1
            showPanel(PanelType.SIMPLE_SYMBOL)
        }
    }

    // ============================================================
    // 粘贴剪贴板
    // ============================================================

    private fun pasteClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            if (text.isNotEmpty()) {
                val ic = currentInputConnection
                ic?.commitText(text, 1)
                showPanel(PanelType.NONE)
                // 粘贴后隐藏剪贴板提示条
                clipboardBar?.visibility = View.GONE
                // 粘贴后 → 恢复初始态元音
                keyboardView?.resetUyghurForm()
            } else {
                Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
        }
    }

    // ============================================================
    // 布局选择菜单
    // ============================================================

    private fun showLayoutSwitchMenu() {
        val lang = keyboardView?.currentLanguage ?: return
        val builder = android.app.AlertDialog.Builder(this)

        when (lang) {
            TilPadKeyboardView.Language.PINYIN -> {
                builder.setTitle("选择中文布局")
                val items = arrayOf("26键全拼", "9键拼音", "手写")
                builder.setItems(items) { _, which ->
                    when (which) {
                        0 -> keyboardView?.setChineseLayout(TilPadKeyboardView.ChineseLayout.QWERTY_26)
                        1 -> keyboardView?.setChineseLayout(TilPadKeyboardView.ChineseLayout.NINE_KEY)
                        2 -> keyboardView?.setChineseLayout(TilPadKeyboardView.ChineseLayout.HANDWRITE)
                    }
                }
            }
            TilPadKeyboardView.Language.UYGHUR -> {
                builder.setTitle("选择维语布局")
                val items = arrayOf("32字母键盘", "26字母键盘")
                builder.setItems(items) { _, which ->
                    when (which) {
                        0 -> keyboardView?.setUyghurLayout(TilPadKeyboardView.UyghurLayout.LAYOUT_32)
                        1 -> keyboardView?.setUyghurLayout(TilPadKeyboardView.UyghurLayout.LAYOUT_26)
                    }
                }
            }
            TilPadKeyboardView.Language.ENGLISH -> {
                builder.setTitle("English Layout")
                builder.setItems(arrayOf("QWERTY")) { _, _ ->
                    keyboardView?.setUyghurLayout(TilPadKeyboardView.UyghurLayout.LAYOUT_32)
                }
            }
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputBuffer.clear()
        candidates = emptyList()
        hideCandidates()
        showPanel(PanelType.NONE)
        // 百度同款：输入框获得焦点时检查剪贴板，有内容则显示粘贴提示
        showClipboardBar()
    }

    override fun onFinishInput() {
        commitComposing()
        super.onFinishInput()
    }

    private fun applySettings() {
        val darkMode = prefs.getBoolean("dark_mode", false)
        keyboardView?.setDarkMode(darkMode)
        emojiPanel?.setDarkMode(darkMode)
        symbolPanel?.setDarkMode(darkMode)
        quickToolbar?.setDarkMode(darkMode)
        simpleSymbolView?.setDarkMode(darkMode)
        val container = rootView ?: return
        if (darkMode) {
            container.setBackgroundColor(Color.parseColor("#1A1A2E"))
        } else {
            container.setBackgroundColor(Color.parseColor("#E8EAED"))
        }
    }

    // ============================================================
    // 按键处理
    // ============================================================

    private fun onKeyPressed(code: Int, label: String, output: String) {
        // 连续删除时跳过震动/音效反馈，避免每30ms触发一次造成卡顿
        val isRepeat = keyboardView?.isDeleting == true
        if (!isRepeat) {
            performKeyFeedback()
        }
        val ic = currentInputConnection ?: return

        when (code) {
            TilPadKeyboardView.CODE_DELETE -> handleBackspace(ic)
            TilPadKeyboardView.CODE_ENTER -> handleEnter(ic)
            TilPadKeyboardView.CODE_SPACE -> handleSpace(ic)
            TilPadKeyboardView.CODE_COMMA,
            TilPadKeyboardView.CODE_PERIOD,
            TilPadKeyboardView.CODE_AT,
            TilPadKeyboardView.CODE_QUESTION,
            TilPadKeyboardView.CODE_MINUS,
            TilPadKeyboardView.CODE_DOT_QUESTION -> {
                commitComposing()
                ic.commitText(output, 1)
            }
            else -> {
                if (code > 0 && output.isNotEmpty()) {
                    handleCharacter(code, label, output, ic)
                }
            }
        }
    }

    private fun handleCharacter(code: Int, label: String, output: String, ic: InputConnection) {
        val lang = keyboardView?.currentLanguage ?: return

        when (lang) {
            TilPadKeyboardView.Language.UYGHUR -> {
                // 维语：composing模式 — 累积字母，实时刷新候选栏
                inputBuffer.append(output)
                ic.setComposingText(inputBuffer.toString(), 1)
                updateUyghurCandidates(ic)
            }
            TilPadKeyboardView.Language.PINYIN -> {
                // 中文拼音：累积输入 → 候选
                inputBuffer.append(output)
                updatePinyinCandidates(ic)
            }
            TilPadKeyboardView.Language.ENGLISH -> {
                // 英文：直接输出（已调试好，不改动）
                ic.commitText(output, 1)
            }
        }
    }

    // ============================================================
    // 维语候选栏实时刷新
    // ============================================================

    private fun updateUyghurCandidates(ic: InputConnection) {
        val composing = inputBuffer.toString()
        if (composing.isEmpty()) { hideCandidates(); return }

        candidates = uyghurDict?.lookupByArabic(composing) ?: emptyList()

        if (candidates.isEmpty()) {
            hideCandidates()
        } else {
            showCandidates()
        }
    }

    // ============================================================
    // 中文拼音候选
    // ============================================================

    private fun updatePinyinCandidates(ic: InputConnection) {
        val pinyin = inputBuffer.toString()
        if (pinyin.isEmpty()) { hideCandidates(); return }

        // 高级拼音引擎：先查词语/单字候选，再尝试句子级转换
        val lookupResults = PinyinEngine.lookup(pinyin).toMutableList()

        // 如果输入较长（>3字符），尝试句子级转换作为第一个候选
        if (pinyin.length > 3) {
            val sentence = PinyinEngine.lookupSentence(pinyin)
            if (sentence.isNotEmpty() && !lookupResults.contains(sentence)) {
                lookupResults.add(0, sentence)
            }
        }

        candidates = lookupResults.distinct().take(20)

        // 拼音不在输入框显示 composing 文本 — 仅在候选栏上方显示大写拼音
        // 使用空 composing 文本避免输入框出现拼音字母
        ic.setComposingText("", 0)

        if (candidates.isNotEmpty()) {
            showCandidates()
        } else {
            hideCandidates()
        }
    }

    // ============================================================
    // 候选栏
    // ============================================================

    private fun showCandidates() {
        val scroll = candidateScroll ?: return
        val container = candidateContainer ?: return
        container.removeAllViews()
        if (candidates.isEmpty()) { hideCandidates(); return }

        // 中文模式：候选栏上方显示对应拼音（大写）
        val lang = keyboardView?.currentLanguage
        if (lang == TilPadKeyboardView.Language.PINYIN) {
            pinyinLabel?.text = inputBuffer.toString().uppercase()
            pinyinLabel?.visibility = View.VISIBLE
        } else {
            pinyinLabel?.visibility = View.GONE
        }

        // 最多显示前 12 个候选字，末尾加「更多」按钮
        val maxDisplay = 12
        val displayList = if (candidates.size > maxDisplay) {
            candidates.take(maxDisplay)
        } else {
            candidates
        }

        for ((index, candidate) in displayList.withIndex()) {
            val tv = TextView(this)
            tv.text = candidate
            // 百度同款：第一个候选词蓝色粗体19sp，其余19sp深色
            tv.textSize = 19f
            if (index == 0) {
                tv.setTextColor(Color.parseColor("#2a7aff"))
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.setTextColor(Color.parseColor("#333333"))
            }
            tv.setPadding(16, 3, 16, 3)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setOnClickListener { selectCandidate(index) }
            container.addView(tv)

            if (index < displayList.size - 1) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(1, 28).apply { setMargins(0, 4, 0, 4) }
                divider.setBackgroundColor(Color.parseColor("#E0E0E0"))
                container.addView(divider)
            }
        }

        // 如果候选数超过最大显示数，添加「更多」按钮
        if (candidates.size > maxDisplay) {
            val moreDivider = View(this)
            moreDivider.layoutParams = LinearLayout.LayoutParams(1, 28).apply { setMargins(0, 4, 0, 4) }
            moreDivider.setBackgroundColor(Color.parseColor("#E0E0E0"))
            container.addView(moreDivider)

            val moreBtn = TextView(this)
            moreBtn.text = "更多"
            moreBtn.textSize = 14f
            moreBtn.setTextColor(Color.parseColor("#2a7aff"))
            moreBtn.setPadding(28, 0, 28, 0)
            moreBtn.gravity = Gravity.CENTER_VERTICAL
            moreBtn.setOnClickListener {
                // 展开全量候选字 — 在候选栏中循环显示所有候选
                showAllCandidates()
            }
            container.addView(moreBtn)
        }

        scroll.visibility = View.VISIBLE
        candidateCloseBtn?.visibility = View.VISIBLE
        scroll.post { scroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT) }
    }

    /**
     * 展开全量候选字 — 支持左右滑动翻页浏览。
     */
    private var candidatePage = 0
    private val candidatePageSize = 12

    private fun showAllCandidates() {
        val scroll = candidateScroll ?: return
        val container = candidateContainer ?: return
        container.removeAllViews()
        if (candidates.isEmpty()) return

        pinyinLabel?.text = inputBuffer.toString().uppercase() + " (共${candidates.size}个)"
        pinyinLabel?.visibility = View.VISIBLE

        // 显示全部候选字，支持横向滑动 — 百度同款样式
        for ((index, candidate) in candidates.withIndex()) {
            val tv = TextView(this)
            tv.text = candidate
            tv.textSize = 19f
            if (index == 0) {
                tv.setTextColor(Color.parseColor("#2a7aff"))
                tv.setTypeface(null, Typeface.BOLD)
            } else {
                tv.setTextColor(Color.parseColor("#333333"))
            }
            tv.setPadding(16, 3, 16, 3)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setOnClickListener { selectCandidate(index) }
            container.addView(tv)

            if (index < candidates.size - 1) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(1, 28).apply { setMargins(0, 4, 0, 4) }
                divider.setBackgroundColor(Color.parseColor("#E0E0E0"))
                container.addView(divider)
            }
        }

        scroll.visibility = View.VISIBLE
        candidateCloseBtn?.visibility = View.VISIBLE
        scroll.post { scroll.fullScroll(HorizontalScrollView.FOCUS_LEFT) }
    }

    private fun hideCandidates() {
        candidateScroll?.visibility = View.GONE
        candidateCloseBtn?.visibility = View.GONE
        pinyinLabel?.visibility = View.GONE
    }

    /**
     * 显示候选列表（不重置拼音标签 — 用于联想候选显示）。
     */
    private fun showCandidateList() {
        val scroll = candidateScroll ?: return
        val container = candidateContainer ?: return
        container.removeAllViews()
        if (candidates.isEmpty()) { hideCandidates(); return }

        for ((index, candidate) in candidates.withIndex()) {
            val tv = TextView(this)
            tv.text = candidate
            tv.textSize = 16f
            tv.setTextColor(Color.parseColor("#333333"))
            tv.setPadding(28, 0, 28, 0)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setOnClickListener { selectCandidate(index) }
            container.addView(tv)

            if (index < candidates.size - 1) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(1, 28).apply { setMargins(0, 4, 0, 4) }
                divider.setBackgroundColor(Color.parseColor("#E0E0E0"))
                container.addView(divider)
            }
        }

        scroll.visibility = View.VISIBLE
        candidateCloseBtn?.visibility = View.VISIBLE
        scroll.post { scroll.fullScroll(HorizontalScrollView.FOCUS_LEFT) }
    }

    private fun selectCandidate(index: Int) {
        if (index >= candidates.size) return
        val ic = currentInputConnection ?: return
        val selected = candidates[index]
        ic.commitText(selected, 1)
        // TTS 朗读选中的候选词
        speakText(selected)
        // 候选词上屏后 → 恢复初始态元音
        keyboardView?.resetUyghurForm()

        // 中文模式：选完候选后保持候选栏打开，继续联想推荐
        val lang = keyboardView?.currentLanguage
        if (lang == TilPadKeyboardView.Language.PINYIN) {
            // 基于已选汉字继续联想后续候选
            inputBuffer.clear()
            // 获取输入框中已输入的文字，用于联想
            val committedText = ic.getTextBeforeCursor(20, 0)?.toString() ?: ""
            // 取最近几个字符做联想
            val recentChars = committedText.takeLast(2)
            candidates = PinyinEngine.lookupByCharacter(recentChars)
            if (candidates.isNotEmpty()) {
                // 显示联想候选，标题改为"联想"
                pinyinLabel?.text = "联想"
                pinyinLabel?.visibility = View.VISIBLE
                showCandidateList()
            } else {
                inputBuffer.clear()
                candidates = emptyList()
                hideCandidates()
            }
        } else {
            // 维语等其他模式：清空buffer，关闭候选栏
            inputBuffer.clear()
            candidates = emptyList()
            hideCandidates()
        }
    }

    // ============================================================
    // 空格
    // ============================================================

    private fun handleSpace(ic: InputConnection) {
        val lang = keyboardView?.currentLanguage ?: return
        when (lang) {
            TilPadKeyboardView.Language.UYGHUR -> {
                commitComposing()
                ic.commitText(" ", 1)
                // 空格打断连写 → 恢复初始态元音
                keyboardView?.resetUyghurForm()
            }
            TilPadKeyboardView.Language.PINYIN -> {
                // 中文模式：空格键关闭候选栏
                if (inputBuffer.isNotEmpty()) {
                    // 有拼音输入时，选第一个候选词并输出空格
                    if (candidates.isNotEmpty()) {
                        ic.commitText(candidates[0], 1)
                    }
                    inputBuffer.clear()
                    candidates = emptyList()
                    hideCandidates()
                    keyboardView?.resetUyghurForm()
                } else {
                    // 无拼音输入时，如果有联想候选也关闭
                    if (candidates.isNotEmpty()) {
                        candidates = emptyList()
                        hideCandidates()
                    }
                    ic.commitText(" ", 1)
                }
            }
            TilPadKeyboardView.Language.ENGLISH -> {
                // 英文空格也关闭候选栏
                if (candidates.isNotEmpty()) {
                    candidates = emptyList()
                    hideCandidates()
                }
                ic.commitText(" ", 1)
            }
        }
    }

    // ============================================================
    // 退格 — 修复删除键错位下移bug
    // ============================================================

    private fun handleBackspace(ic: InputConnection) {
        val lang = keyboardView?.currentLanguage ?: return
        // 维语和中文都有composing buffer，退格时先从buffer删除
        if (lang == TilPadKeyboardView.Language.UYGHUR || lang == TilPadKeyboardView.Language.PINYIN) {
            if (inputBuffer.isNotEmpty()) {
                inputBuffer.deleteCharAt(inputBuffer.length - 1)
                if (inputBuffer.isNotEmpty()) {
                    ic.setComposingText(inputBuffer.toString(), 1)
                    when (lang) {
                        TilPadKeyboardView.Language.UYGHUR -> updateUyghurCandidates(ic)
                        TilPadKeyboardView.Language.PINYIN -> updatePinyinCandidates(ic)
                        else -> {}
                    }
                } else {
                    // 清除composing文本，避免错位
                    ic.setComposingText("", 0)
                    ic.finishComposingText()
                    hideCandidates()
                    // buffer清空 → 恢复初始态元音
                    if (lang == TilPadKeyboardView.Language.UYGHUR) {
                        keyboardView?.resetUyghurForm()
                    }
                }
                return
            }
        }
        // 普通删除 — 使用 deleteSurroundingText 避免错位
        val sel = ic.getSelectedText(0)
        if (sel != null && sel.isNotEmpty()) {
            ic.commitText("", 1)
            return
        }
        ic.deleteSurroundingText(1, 0)
    }

    // ============================================================
    // 回车
    // ============================================================

    private fun handleEnter(ic: InputConnection) {
        commitComposing()
        val ei = currentInputEditorInfo
        if (ei != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            when (action) {
                EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_SEARCH -> {
                    ic.performEditorAction(action); return
                }
            }
        }
        ic.commitText("\n", 1)
    }

    // ============================================================
    // 语言切换
    // ============================================================

    private fun switchToNextLanguage() {
        commitComposing()
        keyboardView?.switchToNextLanguage()
        updateLanguageButtons()
        hideCandidates()
        // 如果有面板打开，关闭
        showPanel(PanelType.NONE)
    }

    private fun switchToLanguage(lang: TilPadKeyboardView.Language) {
        commitComposing()
        keyboardView?.switchToLanguage(lang)
        updateLanguageButtons()
        hideCandidates()
        showPanel(PanelType.NONE)
    }

    private fun updateLanguageButtons() {
        val lang = keyboardView?.currentLanguage ?: return
        langBtnUyghur?.apply {
            setBackgroundResource(R.drawable.lang_btn_inactive)
            setTextColor(Color.parseColor("#888888"))
        }
        langBtnChinese?.apply {
            setBackgroundResource(R.drawable.lang_btn_inactive)
            setTextColor(Color.parseColor("#888888"))
        }
        langBtnEnglish?.apply {
            setBackgroundResource(R.drawable.lang_btn_inactive)
            setTextColor(Color.parseColor("#888888"))
        }
        when (lang) {
            TilPadKeyboardView.Language.UYGHUR -> {
                langBtnUyghur?.apply {
                    setBackgroundResource(R.drawable.lang_btn_active)
                    setTextColor(Color.WHITE)
                }
            }
            TilPadKeyboardView.Language.PINYIN -> {
                langBtnChinese?.apply {
                    setBackgroundResource(R.drawable.lang_btn_active)
                    setTextColor(Color.WHITE)
                }
            }
            TilPadKeyboardView.Language.ENGLISH -> {
                langBtnEnglish?.apply {
                    setBackgroundResource(R.drawable.lang_btn_active)
                    setTextColor(Color.WHITE)
                }
            }
        }
    }

    // ============================================================
    // 辅助
    // ============================================================

    private fun commitComposing() {
        if (inputBuffer.isEmpty()) return
        val ic = currentInputConnection ?: run { inputBuffer.clear(); return }
        val lang = keyboardView?.currentLanguage
        when (lang) {
            TilPadKeyboardView.Language.PINYIN -> {
                // 中文：不自动上屏，提交拼音原文
                ic.commitText(inputBuffer.toString(), 1)
            }
            else -> ic.commitText(inputBuffer.toString(), 1)
        }
        inputBuffer.clear()
        candidates = emptyList()
        hideCandidates()
        // composing上屏后 → 恢复初始态元音
        keyboardView?.resetUyghurForm()
    }

    private fun performKeyFeedback() {
        if (prefs.getBoolean("vibration_enabled", true)) {
            try {
                vibrator?.let { v ->
                    if (v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION") v.vibrate(15)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        if (prefs.getBoolean("sound_enabled", false)) {
            try { audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 1.0f) }
            catch (e: Exception) {}
        }
    }

    // ============================================================
    // TTS 语音朗读 — 高级功能
    // ============================================================

    /**
     * 朗读指定文本 — 中文模式选词后自动朗读。
     * 使用 QUEUE_FLUSH 模式，立即播放最新内容。
     */
    private fun speakText(text: String) {
        if (!prefs.getBoolean("tts_enabled", false)) return
        if (!ttsReady || tts == null) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nurime_tts")
        } catch (e: Exception) {
            Log.e("TTS", "朗读失败", e)
        }
    }
}
