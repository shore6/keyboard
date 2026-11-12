package io.github.shore6.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 有効化の手順と動作確認用の入力欄 */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun text(s: String, sp: Float = 15f) = TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(text(getString(R.string.app_name), 24f))
        root.addView(text("1. 下のボタンから「${getString(R.string.ime_name)}」をオンにします。"))
        root.addView(Button(this).apply {
            text = "入力方法の設定を開く"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        root.addView(text("2. キーボードを「${getString(R.string.ime_name)}」に切り替えます。"))
        root.addView(Button(this).apply {
            text = "キーボードを切り替える"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        root.addView(text("3. ここで試し打ちできます。"))
        root.addView(EditText(this).apply { hint = "テスト入力" })
        root.addView(EditText(this).apply {
            hint = "テスト入力 (複数行)"
            minLines = 3
        })
        root.addView(text("使い方", 18f))
        root.addView(
            text(
                "・地球儀キー: 日本語 ⇔ 英語 (長押しで別のキーボードへ切り替え)\n" +
                    "・英語: 長押しまたは上フリックで右上の記号\n" +
                    "・日本語: タップでトグル入力、上下左右にフリック。長押しで右上の数字\n" +
                    "・入力中は「候補」「変換」で候補を選び、「OK」で確定。候補バーをタップしても確定できます\n" +
                    "・↺: トグルを 1 つ戻す / ‹ ›: カーソル移動 (候補選択中は候補の移動)"
            )
        )
        root.addView(text("辞書のライセンス", 18f))
        val license = try {
            assets.open("dictionary-license.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "(読み込めませんでした)"
        }
        root.addView(text("かな漢字変換には Mozc のオープンソース辞書 (IPAdic ほか) を使用しています。\n\n$license", 12f))
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
