package io.github.shore6.keyboard

/** 変換候補。length は入力 (読み) の先頭から何文字ぶんを置き換えるか */
data class Candidate(val text: String, val length: Int)

/**
 * Mozc の辞書と連接コストを使った簡易かな漢字変換。
 * ラティスを作って Viterbi で最小コスト経路 (文節全体の変換) を求め、
 * 先頭から始まる単語は「その後ろを最良に変換したときの総コスト」で順位づけする。
 */
class Converter(private val dict: Dictionary) {

    private class Node(
        val start: Int,
        val end: Int,
        val lid: Int,
        val rid: Int,
        val cost: Int,
        val text: String,
    ) {
        var total = INF
        var prev: Node? = null
        var back = INF
    }

    private class Scored(val text: String, val length: Int, val score: Int)

    fun convert(input: String): List<Candidate> {
        if (input.isEmpty()) return emptyList()
        val n = input.length
        val byStart = Array(n + 1) { ArrayList<Node>() }
        val byEnd = Array(n + 1) { ArrayList<Node>() }
        val headWords = ArrayList<Node>() // 先頭から始まる単語 (候補一覧用、多めに取る)

        fun add(node: Node) {
            byStart[node.start].add(node)
            byEnd[node.end].add(node)
        }

        for (i in 0 until n) {
            var lo = 0
            var hi = dict.readingCount
            var len = 0
            while (i + len < n && len < MAX_READING) {
                val r = dict.narrow(lo, hi, len, input[i + len]) ?: break
                lo = r[0]; hi = r[1]
                len++
                if (dict.exactAt(lo, len)) {
                    val reading = input.substring(i, i + len)
                    val range = dict.entryRange(lo)
                    val limit = if (i == 0) HEAD_LIMIT else LATTICE_LIMIT
                    var count = 0
                    for (e in range) {
                        if (count >= limit) break
                        val node = Node(i, i + len, dict.lid(e), dict.rid(e), dict.cost(e), dict.word(e, reading))
                        if (count < LATTICE_LIMIT) add(node)
                        if (i == 0) headWords.add(node)
                        count++
                    }
                }
            }
            // 未知語: ひらがなは 1 文字、それ以外は同じ文字種の連続をひとまとまりに
            val c = input[i]
            if (Kana.isHiragana(c)) {
                add(Node(i, i + 1, UNK_ID, UNK_ID, UNK_HIRAGANA_COST, c.toString()))
            } else if (i == 0 || charClass(input[i - 1]) != charClass(c) || charClass(c) == 0) {
                var j = i + 1
                if (charClass(c) != 0) while (j < n && charClass(input[j]) == charClass(c)) j++
                add(Node(i, j, UNK_ID, UNK_ID, UNK_OTHER_COST, input.substring(i, j)))
            }
        }
        if (byStart[0].isEmpty()) return listOf(Candidate(input, n))

        // 前向き
        for (pos in 0..n) {
            for (node in byStart[pos]) {
                if (pos == 0) {
                    node.total = dict.connection(0, node.lid) + node.cost
                } else {
                    var best = INF
                    var bp: Node? = null
                    for (p in byEnd[pos]) {
                        if (p.total >= INF) continue
                        val t = p.total + dict.connection(p.rid, node.lid)
                        if (t < best) { best = t; bp = p }
                    }
                    if (bp != null) { node.total = best + node.cost; node.prev = bp }
                }
            }
        }
        // 後ろ向き
        for (pos in n downTo 1) {
            for (node in byEnd[pos]) node.back = backCost(pos, node.rid, n, byStart)
        }

        val result = ArrayList<Candidate>()
        val seen = HashSet<String>()
        fun push(text: String, len: Int) {
            if (seen.add("$len\u0000$text")) result.add(Candidate(text, len))
        }

        // 1. 全体の最良変換
        var eos: Node? = null
        var eosCost = INF
        for (p in byEnd[n]) {
            if (p.total >= INF) continue
            val t = p.total + dict.connection(p.rid, 0)
            if (t < eosCost) { eosCost = t; eos = p }
        }
        if (eos != null) {
            val parts = ArrayList<String>()
            var cur = eos
            while (cur != null) { parts.add(cur.text); cur = cur.prev }
            push(parts.asReversed().joinToString(""), n)
        }

        // 2. 先頭の単語 + 予測
        val scored = ArrayList<Scored>()
        for (w in headWords) {
            val b = backCost(w.end, w.rid, n, byStart)
            if (b >= INF) continue
            scored.add(Scored(w.text, w.end, dict.connection(0, w.lid) + w.cost + b))
        }
        scored.addAll(predict(input))
        scored.sortBy { it.score }
        for (s in scored) {
            if (result.size >= MAX_CANDIDATES) break
            push(s.text, s.length)
        }

        // 3. ひらがな・カタカナ
        push(input, n)
        push(Kana.toKatakana(input), n)
        return result
    }

    private fun backCost(pos: Int, rid: Int, n: Int, byStart: Array<ArrayList<Node>>): Int {
        if (pos == n) return dict.connection(rid, 0)
        var best = INF
        for (q in byStart[pos]) {
            if (q.back >= INF) continue
            val t = dict.connection(rid, q.lid) + q.cost + q.back
            if (t < best) best = t
        }
        return best
    }

    /** 入力で始まる、より長い読みの単語 (入力全体を置き換える) */
    private fun predict(input: String): List<Scored> {
        if (input.length < 2) return emptyList()
        var lo = 0
        var hi = dict.readingCount
        for (k in input.indices) {
            val r = dict.narrow(lo, hi, k, input[k]) ?: return emptyList()
            lo = r[0]; hi = r[1]
        }
        val out = ArrayList<Scored>()
        val end = minOf(hi, lo + PREDICT_SCAN)
        for (ri in lo until end) {
            if (dict.readingLength(ri) == input.length) continue
            val reading = dict.reading(ri)
            var count = 0
            for (e in dict.entryRange(ri)) {
                if (count++ >= 2) break
                val score = dict.connection(0, dict.lid(e)) + dict.cost(e) + dict.connection(dict.rid(e), 0) +
                    PREDICT_PENALTY + (reading.length - input.length) * 300
                out.add(Scored(dict.word(e, reading), input.length, score))
            }
        }
        out.sortBy { it.score }
        return if (out.size > PREDICT_MAX) out.subList(0, PREDICT_MAX) else out
    }

    private fun charClass(c: Char): Int = when {
        c in 'ァ'..'ヺ' || c == 'ー' -> 1
        c in '0'..'9' || c in '０'..'９' -> 2
        c in 'a'..'z' || c in 'A'..'Z' -> 3
        else -> 0
    }

    companion object {
        private const val INF = Int.MAX_VALUE / 4
        private const val MAX_READING = 24
        private const val LATTICE_LIMIT = 24
        private const val HEAD_LIMIT = 120
        private const val MAX_CANDIDATES = 80
        private const val UNK_ID = 1851 // 名詞,一般
        private const val UNK_HIRAGANA_COST = 14000
        private const val UNK_OTHER_COST = 4000
        private const val PREDICT_SCAN = 3000
        private const val PREDICT_MAX = 20
        private const val PREDICT_PENALTY = 2500
    }
}
