package com.tilpad.ime

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 维语词典助手 — 从 ugfromlatin.dict.yaml 解析 12.7 万词条到 SQLite。
 *
 * 功能：
 * - 首次启动时从 assets/rime/ugfromlatin.dict.yaml 解析词典
 * - 存储到 SQLite，latin 列建索引
 * - 支持前缀查询（输入拉丁前缀 → 返回候选维语词列表）
 * - 支持字母级别转换（无词匹配时逐字母转换）
 *
 * 数据格式（YAML 实际是 tab 分隔）：
 *   ئا    A
 *   ڭ     ng
 *   بىڭ   bing
 *   تەڭرىقۇتىنىڭ  tengriqutining
 */
class UyghurDictHelper(
    private val context: Context
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "uyghur_dict.db"
        private const val DB_VERSION = 1
        private const val TABLE = "dict"
        private const val COL_ID = "id"
        private const val COL_LATIN = "latin"
        private const val COL_ARABIC = "arabic"

        // 字母级别映射（拉丁 → 维语阿拉伯字母）
        // 大写 = 词首形式（带 hamza ئ），小写 = 词中/词末形式
        private val LETTER_MAP = mapOf(
            // 元音 — 词首形式（大写）
            "A" to "ئا", "E" to "ئە", "I" to "ئى", "O" to "ئو", "U" to "ئۇ",
            "Ev" to "ئې", "Ov" to "ئۆ", "Uv" to "ئۈ", "Q" to "ئ",
            // 元音 — 词中/词末形式（小写）
            "a" to "ا", "e" to "ە", "i" to "ى", "o" to "و", "u" to "ۇ",
            "ev" to "ې", "ov" to "ۆ", "uv" to "ۈ",
            // 辅音
            "b" to "ب", "p" to "پ", "t" to "ت", "j" to "ج", "ch" to "چ",
            "x" to "خ", "d" to "د", "r" to "ر", "z" to "ز", "zh" to "ژ",
            "s" to "س", "sh" to "ش", "gh" to "غ", "f" to "ف", "q" to "ق",
            "k" to "ك", "ng" to "ڭ", "g" to "گ", "l" to "ل", "m" to "م",
            "n" to "ن", "h" to "ھ", "w" to "ۋ", "y" to "ي"
        )

        // 多字母组合优先匹配顺序（长前缀优先）
        private val MULTI_CHAR_KEYS = listOf("ng", "ch", "sh", "gh", "zh", "ev", "ov", "uv")
    }

    private var initialized = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LATIN TEXT NOT NULL,
                $COL_ARABIC TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_latin ON $TABLE($COL_LATIN)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /**
     * 从 assets 词典文件初始化 SQLite 数据库。首次调用时执行。
     */
    fun initIfNeeded() {
        if (initialized) return

        val db = readableDatabase
        // 检查是否已有数据
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()

        if (count < 100) {
            // 数据库为空，从 assets 解析词典
            loadFromAssets()
        }

        initialized = true
    }

    /**
     * 从 assets/rime/ugfromlatin.dict.yaml 解析词条到 SQLite。
     */
    private fun loadFromAssets() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val input = context.assets.open("rime/ugfromlatin.dict.yaml")
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            var line: String?
            var batch = 0

            while (reader.readLine().also { line = it } != null) {
                val l = line!!.trim()
                // 跳过注释和空行
                if (l.isEmpty() || l.startsWith("#") || l.startsWith("---") || l.startsWith("...")) continue
                // 跳过 YAML 头部字段
                if (l.contains(":") && !l.contains("\t")) continue

                // 解析 tab 分隔的词条：arabic \t latin
                val parts = l.split("\t")
                if (parts.size >= 2) {
                    val arabic = parts[0].trim()
                    val latin = parts[1].trim()
                    if (arabic.isNotEmpty() && latin.isNotEmpty()) {
                        db.execSQL(
                            "INSERT INTO $TABLE ($COL_LATIN, $COL_ARABIC) VALUES (?, ?)",
                            arrayOf(latin, arabic)
                        )
                        batch++
                        if (batch % 5000 == 0) {
                            db.setTransactionSuccessful()
                            db.endTransaction()
                            db.beginTransaction()
                        }
                    }
                }
            }
            reader.close()
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            // 解析失败不影响基础功能
        } finally {
            try { db.endTransaction() } catch (e: Exception) {}
        }
    }

    /**
     * 前缀查询 — 返回与拉丁前缀匹配的候选维语词列表。
     * 结果按词长排序（短词优先，最可能匹配的在前）。
     *
     * @param prefix 拉丁输入前缀（如 "bin"）
     * @return 候选列表，每项包含 arabic 文字
     */
    fun lookup(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()

        val db = readableDatabase
        val results = mutableListOf<Pair<String, Int>>()

        // 1. 精确匹配优先
        val cursor = db.rawQuery(
            "SELECT $COL_ARABIC, length($COL_LATIN) as llen FROM $TABLE WHERE $COL_LATIN = ? ORDER BY llen LIMIT 5",
            arrayOf(prefix)
        )
        while (cursor.moveToNext()) {
            val arabic = cursor.getString(0)
            val llen = cursor.getInt(1)
            results.add(arabic to llen)
        }
        cursor.close()

        // 2. 前缀匹配
        val cursor2 = db.rawQuery(
            "SELECT $COL_ARABIC, length($COL_LATIN) as llen FROM $TABLE WHERE $COL_LATIN LIKE ? ORDER BY llen LIMIT 15",
            arrayOf("$prefix%")
        )
        while (cursor2.moveToNext()) {
            val arabic = cursor2.getString(0)
            val llen = cursor2.getInt(1)
            if (results.none { it.first == arabic }) {
                results.add(arabic to llen)
            }
        }
        cursor2.close()

        return results.sortedBy { it.second }.map { it.first }
    }

    /**
     * 阿拉伯文前缀查询 — 根据已输入的阿拉伯字母查找词库候选词。
     * 用于维语 composing 模式：用户直接按维语字母键，实时联想出完整词汇。
     *
     * @param arabicPrefix 用户已输入的阿拉伯文字母串（如 "سالا"）
     * @return 候选词列表，按词长排序
     */
    fun lookupByArabic(arabicPrefix: String): List<String> {
        if (arabicPrefix.isEmpty()) return emptyList()

        val db = readableDatabase
        val results = mutableListOf<Pair<String, Int>>()

        // 1. 精确匹配优先
        val cursor = db.rawQuery(
            "SELECT $COL_ARABIC, length($COL_ARABIC) as alen FROM $TABLE WHERE $COL_ARABIC = ? ORDER BY alen LIMIT 5",
            arrayOf(arabicPrefix)
        )
        while (cursor.moveToNext()) {
            val arabic = cursor.getString(0)
            val alen = cursor.getInt(1)
            results.add(arabic to alen)
        }
        cursor.close()

        // 2. 前缀匹配（以输入串开头的词）
        val cursor2 = db.rawQuery(
            "SELECT $COL_ARABIC, length($COL_ARABIC) as alen FROM $TABLE WHERE $COL_ARABIC LIKE ? ORDER BY alen LIMIT 20",
            arrayOf("$arabicPrefix%")
        )
        while (cursor2.moveToNext()) {
            val arabic = cursor2.getString(0)
            val alen = cursor2.getInt(1)
            if (results.none { it.first == arabic }) {
                results.add(arabic to alen)
            }
        }
        cursor2.close()

        return results.sortedBy { it.second }.map { it.first }
    }

    /**
     * 字母级别转换 — 当词典无匹配时，逐字母将拉丁转写转为维语阿拉伯文字。
     * 处理多字母组合（ng → ڭ, ch → چ, sh → ش, gh → غ, zh → ژ）优先匹配。
     *
     * @param latin 拉丁输入文本
     * @param isWordStart 是否在词首位置（影响元音形式）
     * @return 维语阿拉伯文字
     */
    fun transcribe(latin: String): String {
        if (latin.isEmpty()) return ""

        val result = StringBuilder()
        var i = 0
        val len = latin.length

        while (i < len) {
            // 尝试匹配多字母组合
            var matched = false

            // 检查 2 字母组合
            if (i + 1 < len) {
                val two = latin.substring(i, i + 2)
                if (MULTI_CHAR_KEYS.contains(two)) {
                    // 词首大写形式
                    if (i == 0 && latin.length > 1) {
                        val upper = two[0].uppercaseChar() + two.substring(1)
                        LETTER_MAP[upper]?.let {
                            result.append(it)
                            i += 2
                            matched = true
                        }
                    }
                    if (!matched) {
                        LETTER_MAP[two]?.let {
                            result.append(it)
                            i += 2
                            matched = true
                        }
                    }
                }
            }

            // 检查 3 字母组合 (如 Ev, Ov, Uv — 但这些已经被 2 字母匹配了)
            if (!matched && i + 2 < len) {
                val three = latin.substring(i, i + 3)
                if (three == "ng" || three == "ch" || three == "sh" || three == "gh" || three == "zh") {
                    LETTER_MAP[three]?.let {
                        result.append(it)
                        i += 3
                        matched = true
                    }
                }
            }

            // 单字母匹配
            if (!matched) {
                val ch = latin[i].toString()
                // 词首大写形式
                if (i == 0) {
                    val upper = ch.uppercase()
                    // 尝试 Ev/Ov/Uv 组合
                    if (i + 1 < len && (ch == "e" || ch == "o" || ch == "u")) {
                        val next = latin[i + 1]
                        if (next == 'v') {
                            val combo = "${upper}v"
                            LETTER_MAP[combo]?.let {
                                result.append(it)
                                i += 2
                                matched = true
                            }
                        }
                    }
                    if (!matched) {
                        LETTER_MAP[upper]?.let {
                            result.append(it)
                            i++
                            matched = true
                        }
                    }
                }
                if (!matched) {
                    LETTER_MAP[ch]?.let {
                        result.append(it)
                        i++
                        matched = true
                    }
                }
            }

            // 无法匹配的字符原样输出
            if (!matched) {
                result.append(latin[i])
                i++
            }
        }

        return result.toString()
    }

    /**
     * 获取输入提示文本 — 在候选栏显示用户正在输入的拉丁转写。
     */
    fun getComposingText(latin: String): String {
        return transcribe(latin)
    }
}
