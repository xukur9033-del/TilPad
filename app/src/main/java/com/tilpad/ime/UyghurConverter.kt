package com.tilpad.ime

import java.text.Normalizer
import java.util.Locale

/**
 * 拉丁维语 (ULY, Uyghur Latin Yéziqi) → 阿拉伯字母维语 (UAY, Uyghur Arab Yéziqi) 转换引擎。
 *
 * 采用 **最长匹配（longest-match）贪心算法**：
 * 从左到右遍历输入串，每个位置优先尝试匹配 2 字母组合（digraph），
 * 若未命中再尝试 1 字母。两者都不匹配时原样保留该字符。
 *
 * 这样可以保证 "sh"→ش（而非 s→س + h→ھ）、"ch"→چ、"gh"→غ 等正确转换。
 *
 * 示例：
 * - "salam"      → سالام
 * - "yaxshimusiz" → ياخشىمۇسىز
 * - "xosh"       → خوش
 */
object UyghurConverter {

    // ------------------------------------------------------------------
    // 双字母映射表（digraphs）—— 必须先于单字母匹配
    // ------------------------------------------------------------------
    private val digraphMap: Map<String, String> = mapOf(
        "ch" to "چ",  // U+0686
        "sh" to "ش",  // U+0634
        "zh" to "ژ",  // U+0698
        "ng" to "ڭ",  // U+06AD
        "gh" to "غ"   // U+063A
    )

    // ------------------------------------------------------------------
    // 单字母映射表
    // ------------------------------------------------------------------
    private val singleMap: Map<String, String> = mapOf(
        "a" to "ا",   // U+0627
        "e" to "ە",   // U+06D5
        "é" to "ې",   // U+06D0
        "b" to "ب",   // U+0628
        "p" to "پ",   // U+067E
        "t" to "ت",   // U+062A
        "j" to "ج",   // U+062C
        "x" to "خ",   // U+062E
        "d" to "د",   // U+062F
        "r" to "ر",   // U+0631
        "z" to "ز",   // U+0632
        "s" to "س",   // U+0633
        "f" to "ف",   // U+0641
        "l" to "ل",   // U+0644
        "m" to "م",   // U+0645
        "h" to "ھ",   // U+06BE  (Uyghur Heh Doachashmee)
        "o" to "و",   // U+0648
        "u" to "ۇ",   // U+06C7
        "ö" to "ۆ",   // U+06C6
        "ü" to "ۈ",   // U+06C8
        "w" to "ۋ",   // U+06CB
        "i" to "ى",   // U+0649  (Alef Maksura)
        "y" to "ي",   // U+064A
        "q" to "ق",   // U+0642
        "k" to "ك",   // U+0643
        "g" to "گ",   // U+06AF
        "n" to "ن"    // U+0646
    )

    /**
     * 将拉丁维语字符串转换为阿拉伯字母维语。
     *
     * 处理步骤：
     * 1. **NFC 归一化**：将带附加符号的分解形式（如 e + ◌´）合并为预组合形式（é），
     *    保证 "é" 能被 [singleMap] 正确匹配。
     * 2. **统一小写**：使用 `Locale.ROOT` 避免 Turkish locale 中 I→ı 的问题。
     * 3. **贪心遍历**：逐字符扫描，优先匹配 2 字母 digraph，再匹配 1 字母。
     * 4. **未匹配保留**：数字、标点、空格等不在映射表中的字符原样输出。
     *
     * @param latin 拉丁维语输入串（大小写不限，可含标点和空格）
     * @return 转换后的阿拉伯字母维语字符串
     */
    fun convert(latin: String): String {
        if (latin.isEmpty()) return ""

        // 1. NFC 归一化
        val normalized = Normalizer.normalize(latin, Normalizer.Form.NFC)
        // 2. 统一小写（Locale.ROOT 避免 Turkish locale 问题）
        val lower = normalized.lowercase(Locale.ROOT)

        val result = StringBuilder(lower.length)
        var i = 0
        while (i < lower.length) {
            var matched = false

            // 3a. 先尝试匹配双字母（longest-match 优先）
            if (i + 1 < lower.length) {
                val pair = lower.substring(i, i + 2)
                val pairResult = digraphMap[pair]
                if (pairResult != null) {
                    result.append(pairResult)
                    i += 2
                    matched = true
                }
            }

            // 3b. 双字母未命中 → 尝试单字母
            if (!matched) {
                val single = lower.substring(i, i + 1)
                val singleResult = singleMap[single]
                if (singleResult != null) {
                    result.append(singleResult)
                    i += 1
                    matched = true
                }
            }

            // 4. 都不匹配 → 原样保留
            if (!matched) {
                result.append(lower[i])
                i += 1
            }
        }

        return result.toString()
    }
}
