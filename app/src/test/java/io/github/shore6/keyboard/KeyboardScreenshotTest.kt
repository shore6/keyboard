package io.github.shore6.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 各レイアウトを PNG に描画する (build/screenshots/) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xxhdpi")
class KeyboardScreenshotTest {
    private fun render(name: String, layout: KeyboardLayout, composing: Boolean = false) {
        val ctx = RuntimeEnvironment.getApplication()
        val view = KeyboardView(ctx, object : KeyboardView.Listener {
            override fun onKey(key: Key, dir: Int) {}
            override fun onLongPress(key: Key) = false
            override fun dynamicLabel(key: Key): String? = when (key.code) {
                Code.ENTER -> if (composing) "OK" else "移動"
                Code.SPACE -> if (layout === Layouts.japanese || layout === Layouts.japaneseNumber) (if (composing) "候補" else "日本語") else null
                Code.JA_NUMBER -> if (composing) "変換" else null
                else -> null
            }
            override fun shiftState() = 0
        })
        view.layout = layout
        val w = 1080
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, w, view.measuredHeight)
        val bmp = Bitmap.createBitmap(w, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderAll() {
        render("0_en", Layouts.english)
        render("1_sym1", Layouts.symbols1)
        render("2_sym2", Layouts.symbols2)
        render("3_ja", Layouts.japanese)
        render("4_ja_num", Layouts.japaneseNumber)
        render("5_ja_composing", Layouts.japanese, composing = true)
    }
}
