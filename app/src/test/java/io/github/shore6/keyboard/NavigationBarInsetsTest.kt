package io.github.shore6.keyboard

import android.graphics.Insets
import android.view.WindowInsets
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** issue #1: ナビゲーションバー (戻る・ホーム) の分だけキーボードを上げる */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationBarInsetsTest {
    @Test
    fun padsBottomByNavigationBarHeight() {
        val ime = Robolectric.buildService(KeyboardIME::class.java).create().get()
        val view = ime.onCreateInputView()
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 126))
            .build()
        view.dispatchApplyWindowInsets(insets)
        assertEquals(126, view.paddingBottom)
    }
}
