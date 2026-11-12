package io.github.shore6.keyboard

import java.nio.ByteBuffer

/** BuildDictionaryTask が生成した dict.bin / conn.bin を読む (Android 非依存) */
class Dictionary(private val dict: ByteBuffer, private val conn: ByteBuffer) {
    val readingCount: Int
    private val entryCount: Int
    private val readingOffPos: Int
    private val entryOffPos: Int
    private val entriesPos: Int
    private val readingCharsPos: Int
    private val wordCharsPos: Int
    private val connSize: Int

    init {
        require(dict.getInt(0) == 0x4D5A4431) { "bad dict.bin" }
        require(conn.getInt(0) == 0x4D5A4331) { "bad conn.bin" }
        readingCount = dict.getInt(4)
        entryCount = dict.getInt(8)
        val readingCharsLen = dict.getInt(12)
        readingOffPos = 20
        entryOffPos = readingOffPos + (readingCount + 1) * 4
        entriesPos = entryOffPos + (readingCount + 1) * 4
        readingCharsPos = entriesPos + entryCount * ENTRY_SIZE
        wordCharsPos = readingCharsPos + readingCharsLen * 2
        connSize = conn.getInt(4)
    }

    fun connection(rid: Int, lid: Int): Int = conn.getShort(8 + (rid * connSize + lid) * 2).toInt()

    private fun readingStart(i: Int) = dict.getInt(readingOffPos + i * 4)
    fun readingLength(i: Int) = readingStart(i + 1) - readingStart(i)
    private fun readingChar(i: Int, k: Int) = dict.getChar(readingCharsPos + (readingStart(i) + k) * 2)

    fun reading(i: Int): String {
        val len = readingLength(i)
        val sb = StringBuilder(len)
        for (k in 0 until len) sb.append(readingChar(i, k))
        return sb.toString()
    }

    /**
     * 読み番号 [lo, hi) のうち、先頭 depth 文字が一致しているものの中から、
     * depth 文字目が c のものの範囲を返す (無ければ null)。
     */
    fun narrow(lo: Int, hi: Int, depth: Int, c: Char): IntArray? {
        // 下限: depth 文字目 >= c となる最初
        var a = lo
        var b = hi
        while (a < b) {
            val m = (a + b) ushr 1
            if (charAt(m, depth) < c.code) a = m + 1 else b = m
        }
        val start = a
        b = hi
        while (a < b) {
            val m = (a + b) ushr 1
            if (charAt(m, depth) <= c.code) a = m + 1 else b = m
        }
        return if (start < a) intArrayOf(start, a) else null
    }

    /** 範囲の先頭の読みがちょうど len 文字か (= 完全一致する読みがあるか) */
    fun exactAt(index: Int, len: Int) = readingLength(index) == len

    private fun charAt(i: Int, k: Int): Int =
        if (k < readingLength(i)) readingChar(i, k).code else -1

    fun entryRange(reading: Int): IntRange = dict.getInt(entryOffPos + reading * 4) until dict.getInt(entryOffPos + (reading + 1) * 4)

    fun lid(e: Int) = dict.getShort(entriesPos + e * ENTRY_SIZE).toInt()
    fun rid(e: Int) = dict.getShort(entriesPos + e * ENTRY_SIZE + 2).toInt()
    fun cost(e: Int) = dict.getShort(entriesPos + e * ENTRY_SIZE + 4).toInt()

    fun word(e: Int, reading: String): String {
        val base = entriesPos + e * ENTRY_SIZE
        val off = dict.getInt(base + 8)
        return when (off) {
            -1 -> reading
            -2 -> Kana.toKatakana(reading)
            else -> {
                val len = dict.getShort(base + 6).toInt()
                val sb = StringBuilder(len)
                for (k in 0 until len) sb.append(dict.getChar(wordCharsPos + (off + k) * 2))
                sb.toString()
            }
        }
    }

    companion object {
        private const val ENTRY_SIZE = 12
    }
}
