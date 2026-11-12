package io.github.shore6.keyboard

object Kana {
    private val cycles: Map<Char, String>

    init {
        val list = mutableListOf<String>()
        // 濁点のみ
        for (c in "かきくけこさしすせそたちてと") list.add("$c${c + 1}")
        list.add("つっづ")
        // 濁点・半濁点
        for (c in "はひふへほ") list.add("$c${c + 1}${c + 2}")
        // 小書き
        list.add("あぁ"); list.add("いぃ"); list.add("うぅゔ"); list.add("えぇ"); list.add("おぉ")
        list.add("やゃ"); list.add("ゆゅ"); list.add("よょ"); list.add("わゎ")
        val m = HashMap<Char, String>()
        for (s in list) for (c in s) m[c] = s
        cycles = m
    }

    /** ゛゜小 キー: 次の形に変える (変えられなければ null) */
    fun nextVariant(c: Char): Char? {
        val s = cycles[c] ?: return null
        return s[(s.indexOf(c) + 1) % s.length]
    }

    fun isHiragana(c: Char) = c in 'ぁ'..'ゖ' || c == 'ー'

    fun toKatakana(s: CharSequence): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
        return sb.toString()
    }
}
