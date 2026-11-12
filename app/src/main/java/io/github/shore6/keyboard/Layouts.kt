package io.github.shore6.keyboard

/** キーの種類 */
enum class Code {
    TEXT,        // 文字を入力 (英字・記号・数字)
    KANA,        // フリック／トグル入力のかなキー
    BACKSPACE,
    ENTER,
    SPACE,
    SHIFT,
    SYMBOL,      // 英語 → 記号
    ABC,         // 記号 → 英語
    SYMBOL_PAGE, // 記号 1/2 ⇔ 2/2
    GLOBE,       // 日本語 ⇔ 英語
    LEFT,
    RIGHT,
    UNDO,        // トグルを戻す
    DAKUTEN,     // ゛゜小
    JA_NUMBER,   // かな → 数字 (入力中は「変換」)
    JA_KANA,     // 数字 → かな
    SPACER,      // 何もしない空白
}

enum class KeyStyle { NORMAL, FUNCTION, ENTER, SPACER }

/**
 * @param flick 中央・左・上・右・下 の順の文字 (null は割当なし)
 * @param cycle トグル入力の順序 (KANA のみ)
 * @param hint  右上に表示する小さい文字 (英字キーの長押し記号、かなキーの長押し数字)
 * @param sub   ラベルの下に表示する補助テキスト (数字キーのフリック文字)
 */
class Key(
    val code: Code,
    val label: String,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    val text: String? = null,
    val flick: Array<String?>? = null,
    val cycle: String? = null,
    val hint: String? = null,
    val sub: String? = null,
    val repeatable: Boolean = false,
)

class KeyboardLayout(val rows: List<List<Key>>)

object Dir {
    const val CENTER = 0
    const val LEFT = 1
    const val UP = 2
    const val RIGHT = 3
    const val DOWN = 4
}

object Layouts {
    private fun ch(c: String, hint: String? = null) =
        Key(Code.TEXT, c, text = c, hint = hint, flick = arrayOf(c, null, hint, null, null))

    private fun sym(c: String) = Key(Code.TEXT, c, text = c)

    private fun backspace(w: Float = 1.5f) = Key(Code.BACKSPACE, "", w, KeyStyle.FUNCTION, repeatable = true)
    private fun enter(w: Float = 1.5f) = Key(Code.ENTER, "", w, KeyStyle.ENTER)
    private fun globe(w: Float = 1f) = Key(Code.GLOBE, "", w, KeyStyle.FUNCTION)
    private fun spacer(w: Float) = Key(Code.SPACER, "", w, KeyStyle.SPACER)

    private val numberRow = "1234567890".map { sym(it.toString()) }

    // ---------------- 英語 QWERTY ----------------
    val english = KeyboardLayout(
        listOf(
            numberRow,
            listOf(
                ch("q", "+"), ch("w", "×"), ch("e", "÷"), ch("r", "="), ch("t", "/"),
                ch("y", "_"), ch("u", "<"), ch("i", ">"), ch("o", "["), ch("p", "]"),
            ),
            listOf(
                spacer(0.5f),
                ch("a", "!"), ch("s", "@"), ch("d", "#"), ch("f", "$"), ch("g", "%"),
                ch("h", "&"), ch("j", "*"), ch("k", "("), ch("l", ")"),
                spacer(0.5f),
            ),
            listOf(
                Key(Code.SHIFT, "", 1.5f, KeyStyle.FUNCTION),
                ch("z", "-"), ch("x", "'"), ch("c", "\""), ch("v", ":"), ch("b", ";"),
                ch("n", ","), ch("m", "?"),
                backspace(),
            ),
            listOf(
                Key(Code.SYMBOL, "!#1", 1.5f, KeyStyle.FUNCTION),
                globe(),
                sym("/"),
                Key(Code.SPACE, "EN(US)", 4f),
                sym("."),
                enter(),
            ),
        )
    )

    private fun symbolBottom() = listOf(
        Key(Code.ABC, "ABC", 1.5f, KeyStyle.FUNCTION),
        globe(),
        sym("/"),
        Key(Code.SPACE, "EN(US)", 2.5f),
        sym("."),
        Key(Code.TEXT, ".com", 1.5f, text = ".com"),
        enter(),
    )

    val symbols1 = KeyboardLayout(
        listOf(
            numberRow,
            "+×÷=/_<>[]".map { sym(it.toString()) },
            "!@#$%^&*()".map { sym(it.toString()) },
            listOf(Key(Code.SYMBOL_PAGE, "1/2", 1.5f, KeyStyle.FUNCTION)) +
                "-'\":;,?".map { sym(it.toString()) } + listOf(backspace()),
            symbolBottom(),
        )
    )

    val symbols2 = KeyboardLayout(
        listOf(
            numberRow,
            "`~\\|{}€£¥₩".map { sym(it.toString()) },
            "°•○●□■♤♡◇♧".map { sym(it.toString()) },
            listOf(Key(Code.SYMBOL_PAGE, "2/2", 1.5f, KeyStyle.FUNCTION)) +
                "☆▪¤《》¡¿".map { sym(it.toString()) } + listOf(backspace()),
            symbolBottom(),
        )
    )

    // ---------------- 日本語フリック ----------------
    private const val SIDE = 1f
    private const val MID = 1.32f

    private fun kana(label: String, flick: String, cycle: String, digit: String?) = Key(
        Code.KANA, label, MID,
        flick = flick.map { it.toString() }.toTypedArray<String?>(),
        cycle = cycle, hint = digit,
    )

    private fun kana(label: String, flick: Array<String?>, cycle: String, digit: String?) = Key(
        Code.KANA, label, MID, flick = flick, cycle = cycle, hint = digit,
    )

    private fun fn(code: Code, label: String, repeatable: Boolean = false) =
        Key(code, label, SIDE, KeyStyle.FUNCTION, repeatable = repeatable)

    val japanese = KeyboardLayout(
        listOf(
            listOf(
                fn(Code.UNDO, "↺"),
                kana("あ", "あいうえお", "あいうえおぁぃぅぇぉ", "1"),
                kana("か", "かきくけこ", "かきくけこ", "2"),
                kana("さ", "さしすせそ", "さしすせそ", "3"),
                Key(Code.BACKSPACE, "", SIDE, KeyStyle.FUNCTION, repeatable = true),
            ),
            listOf(
                fn(Code.LEFT, "‹", repeatable = true),
                kana("た", "たちつてと", "たちつてとっ", "4"),
                kana("な", "なにぬねの", "なにぬねの", "5"),
                kana("は", "はひふへほ", "はひふへほ", "6"),
                fn(Code.RIGHT, "›", repeatable = true),
            ),
            listOf(
                fn(Code.JA_NUMBER, "!#1"),
                kana("ま", "まみむめも", "まみむめも", "7"),
                kana("や", arrayOf("や", "「", "ゆ", "」", "よ"), "やゆよゃゅょ", "8"),
                kana("ら", "らりるれろ", "らりるれろ", "9"),
                Key(Code.SPACE, "日本語", SIDE, KeyStyle.FUNCTION),
            ),
            listOf(
                Key(Code.GLOBE, "", SIDE, KeyStyle.FUNCTION),
                Key(Code.DAKUTEN, "゛゜小", MID),
                kana("わ", arrayOf("わ", "を", "ん", "ー", null), "わをんゎー", "0"),
                kana("、。?!", "、。？！…", "、。？！…", null),
                Key(Code.ENTER, "", SIDE, KeyStyle.ENTER),
            ),
        )
    )

    private fun num(main: String, flick: String, sub: String = flick) = Key(
        Code.TEXT, main, MID, text = main,
        flick = (listOf(main) + flick.map { it.toString() }).toTypedArray<String?>(),
        sub = sub,
    )

    val japaneseNumber = KeyboardLayout(
        listOf(
            listOf(
                fn(Code.UNDO, "↺"),
                num("1", "/:@~"),
                num("2", "\"\\%'"),
                num("3", "+×÷−"),
                Key(Code.BACKSPACE, "", SIDE, KeyStyle.FUNCTION, repeatable = true),
            ),
            listOf(
                fn(Code.LEFT, "‹", repeatable = true),
                num("4", "*;&·"),
                num("5", "¥₩€$"),
                num("6", "<^>="),
                fn(Code.RIGHT, "›", repeatable = true),
            ),
            listOf(
                fn(Code.JA_KANA, "あいう"),
                num("7", "#♪〒※"),
                num("8", "[{}]"),
                num("9", "☆○◇♡"),
                Key(Code.SPACE, "日本語", SIDE, KeyStyle.FUNCTION),
            ),
            listOf(
                Key(Code.SPACER, "", SIDE, KeyStyle.FUNCTION),
                num("…", "「『」』", "「」"),
                num("0", "(_)"),
                num(".", ",?!"),
                Key(Code.ENTER, "", SIDE, KeyStyle.ENTER),
            ),
        )
    )
}
