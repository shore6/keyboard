package io.github.shore6.keyboard

import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import java.io.FileInputStream
import java.nio.channels.FileChannel

class KeyboardIME : InputMethodService(), KeyboardView.Listener {

    private enum class Mode { EN, SYM1, SYM2, JA, JA_NUM }

    private lateinit var keyboardView: KeyboardView
    private lateinit var candidateBar: CandidateBar
    private var converter: Converter? = null

    private var mode = Mode.JA
    private var shift = 0 // 0: off, 1: 次の 1 文字, 2: ロック
    private var lastShiftTap = 0L

    // 日本語入力の状態
    private val composing = StringBuilder()
    private var toggleKey: Key? = null
    private var toggleIndex = 0
    private var candidates = listOf<Candidate>()
    private var selected = -1

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        converter = try {
            Converter(Dictionary(mapAsset("dict.bin"), mapAsset("conn.bin")))
        } catch (e: Exception) {
            Log.e(TAG, "辞書の読み込みに失敗", e)
            null
        }
        mode = if (prefs.getString("lang", "ja") == "en") Mode.EN else Mode.JA
    }

    private fun mapAsset(name: String) = assets.openFd(name).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { ch ->
            ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
        }
    }

    override fun onCreateInputView(): View {
        candidateBar = CandidateBar(this) { index ->
            val ic = currentInputConnection
            ic?.beginBatchEdit()
            try {
                pickCandidate(index)
            } finally {
                ic?.endBatchEdit()
            }
            keyboardView.invalidate()
        }
        keyboardView = KeyboardView(this, this)
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(0xFF111417.toInt())
            // Android 15 以降 (targetSdk 35) は IME ウィンドウもナビゲーションバーの下まで広がるので、
            // その分だけ下に余白を入れてキーが戻る・ホームボタンと重ならないようにする
            setOnApplyWindowInsetsListener { v, insets ->
                val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                v.setPadding(0, 0, 0, bottom)
                insets
            }
            addView(candidateBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * density).toInt()))
            addView(keyboardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetComposing()
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        mode = when {
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME -> Mode.SYM1
            cls == InputType.TYPE_CLASS_TEXT && variation in listOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_URI,
            ) -> Mode.EN
            else -> if (prefs.getString("lang", "ja") == "en") Mode.EN else Mode.JA
        }
        shift = 0
        applyLayout()
        updateAutoShift()
        updateCandidates()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        resetComposing()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composing.clear()
        toggleKey = null
        candidates = emptyList()
        selected = -1
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // 入力中にカーソルが外から動かされたら、変換をやめて確定する
        if (composing.isNotEmpty() && (candidatesEnd < 0 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            resetComposing()
            updateCandidates()
        }
        if (mode == Mode.EN && shift != 2) {
            updateAutoShift()
        }
    }

    private fun applyLayout() {
        if (!::keyboardView.isInitialized) return
        keyboardView.layout = when (mode) {
            Mode.EN -> Layouts.english
            Mode.SYM1 -> Layouts.symbols1
            Mode.SYM2 -> Layouts.symbols2
            Mode.JA -> Layouts.japanese
            Mode.JA_NUM -> Layouts.japaneseNumber
        }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density
        keyboardView.keyboardHeight = ((if (landscape) 170 else 250) * density).toInt()
    }

    // ---------------- KeyboardView.Listener ----------------

    override fun shiftState() = if (mode == Mode.EN) shift else 0

    override fun dynamicLabel(key: Key): String? = when (key.code) {
        Code.ENTER -> if (composing.isNotEmpty()) "OK" else enterLabel()
        Code.SPACE -> when (mode) {
            Mode.JA, Mode.JA_NUM -> if (composing.isNotEmpty()) "候補" else "日本語"
            else -> null
        }
        Code.JA_NUMBER -> if (composing.isNotEmpty()) "変換" else null
        else -> null
    }

    private fun enterLabel(): String {
        val opts = currentInputEditorInfo?.imeOptions ?: 0
        if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "改行"
        return when (opts and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "移動"
            EditorInfo.IME_ACTION_SEARCH -> "検索"
            EditorInfo.IME_ACTION_SEND -> "送信"
            EditorInfo.IME_ACTION_NEXT -> "次へ"
            EditorInfo.IME_ACTION_DONE -> "完了"
            EditorInfo.IME_ACTION_PREVIOUS -> "前へ"
            else -> "改行"
        }
    }

    override fun onLongPress(key: Key): Boolean {
        when (key.code) {
            Code.GLOBE -> {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                return true
            }
            Code.TEXT -> {
                if (key.hint == null) return false
                onKey(key, Dir.UP)
                return true
            }
            Code.KANA -> {
                val digit = key.hint ?: return false
                val ic = currentInputConnection
                ic?.beginBatchEdit()
                commitSelection()
                toggleKey = null
                composing.append(digit)
                updateComposing()
                ic?.endBatchEdit()
                return true
            }
            else -> return false
        }
    }

    override fun onKey(key: Key, dir: Int) {
        // 確定と入力中文字列の更新を 1 回の変更としてエディタに伝える
        val ic = currentInputConnection
        ic?.beginBatchEdit()
        try {
            if (mode == Mode.JA || mode == Mode.JA_NUM) onKeyJapanese(key, dir) else onKeyEnglish(key, dir)
        } finally {
            ic?.endBatchEdit()
        }
        if (::keyboardView.isInitialized) keyboardView.invalidate()
    }

    // ---------------- 英語 ----------------

    private fun onKeyEnglish(key: Key, dir: Int) {
        val ic = currentInputConnection ?: return
        when (key.code) {
            Code.TEXT -> {
                var t = (if (dir != Dir.CENTER) key.flick?.getOrNull(dir) else null) ?: key.text ?: return
                if (shift > 0 && mode == Mode.EN && t.length == 1) t = t.uppercase()
                ic.commitText(t, 1)
                if (shift == 1) shift = 0
            }
            Code.SPACE -> ic.commitText(" ", 1)
            Code.BACKSPACE -> deleteBackward()
            Code.ENTER -> performEnter()
            Code.SHIFT -> {
                val now = System.currentTimeMillis()
                shift = when {
                    shift == 2 -> 0
                    shift == 1 && now - lastShiftTap < 350 -> 2
                    shift == 1 -> 0
                    else -> 1
                }
                lastShiftTap = now
            }
            Code.SYMBOL -> setMode(Mode.SYM1)
            Code.ABC -> setMode(Mode.EN)
            Code.SYMBOL_PAGE -> setMode(if (mode == Mode.SYM1) Mode.SYM2 else Mode.SYM1)
            Code.GLOBE -> { setMode(Mode.JA); saveLang("ja") }
            else -> {}
        }
    }

    private fun updateAutoShift() {
        if (mode != Mode.EN || shift == 2) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val caps = info.inputType and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val newShift = if (caps != 0 && ic.getCursorCapsMode(info.inputType) != 0) 1 else 0
        if (newShift != shift) {
            shift = newShift
            if (::keyboardView.isInitialized) keyboardView.invalidate()
        }
    }

    // ---------------- 日本語 ----------------

    private fun onKeyJapanese(key: Key, dir: Int) {
        val ic = currentInputConnection ?: return
        when (key.code) {
            Code.KANA -> {
                val cycle = key.cycle ?: return
                if (dir == Dir.CENTER) {
                    if (toggleKey === key && selected < 0 && composing.isNotEmpty()) {
                        toggleIndex = (toggleIndex + 1) % cycle.length
                        composing.setCharAt(composing.length - 1, cycle[toggleIndex])
                    } else {
                        commitSelection()
                        composing.append(cycle[0])
                        toggleKey = key
                        toggleIndex = 0
                    }
                } else {
                    commitSelection()
                    composing.append(key.flick?.getOrNull(dir) ?: return)
                    toggleKey = null
                }
                updateComposing()
            }
            Code.TEXT -> {
                val t = (if (dir != Dir.CENTER) key.flick?.getOrNull(dir) else null) ?: key.text ?: return
                commitSelection()
                composing.append(t)
                toggleKey = null
                updateComposing()
            }
            Code.DAKUTEN -> {
                if (selected >= 0 || composing.isEmpty()) return
                val next = Kana.nextVariant(composing[composing.length - 1]) ?: return
                composing.setCharAt(composing.length - 1, next)
                toggleKey = null
                updateComposing()
            }
            Code.UNDO -> {
                val k = toggleKey ?: return
                val cycle = k.cycle ?: return
                if (selected >= 0 || composing.isEmpty()) return
                toggleIndex = (toggleIndex - 1 + cycle.length) % cycle.length
                composing.setCharAt(composing.length - 1, cycle[toggleIndex])
                updateComposing()
            }
            Code.BACKSPACE -> {
                when {
                    selected >= 0 -> {
                        selected = -1
                        ic.setComposingText(composing, 1)
                        candidateBar.setSelected(-1)
                    }
                    composing.isNotEmpty() -> {
                        composing.deleteCharAt(composing.length - 1)
                        toggleKey = null
                        updateComposing()
                    }
                    else -> deleteBackward()
                }
            }
            Code.SPACE, Code.JA_NUMBER -> {
                if (composing.isNotEmpty()) {
                    nextCandidate(1)
                } else if (key.code == Code.JA_NUMBER) {
                    setMode(Mode.JA_NUM)
                } else {
                    ic.commitText(" ", 1)
                }
            }
            Code.ENTER -> {
                if (composing.isNotEmpty()) {
                    if (selected >= 0) pickCandidate(selected) else commitComposing()
                } else {
                    performEnter()
                }
            }
            Code.LEFT -> when {
                selected >= 0 -> nextCandidate(-1)
                composing.isNotEmpty() -> toggleKey = null
                else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }
            Code.RIGHT -> when {
                selected >= 0 -> nextCandidate(1)
                composing.isNotEmpty() -> toggleKey = null
                else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
            Code.JA_KANA -> setMode(Mode.JA)
            Code.GLOBE -> {
                commitComposing()
                setMode(Mode.EN)
                saveLang("en")
            }
            else -> {}
        }
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        selected = -1
        if (composing.isEmpty()) {
            ic.setComposingText("", 1)
            ic.finishComposingText()
        } else {
            ic.setComposingText(composing, 1)
        }
        updateCandidates()
    }

    private fun updateCandidates() {
        candidates = if (composing.isEmpty()) emptyList() else try {
            converter?.convert(composing.toString()) ?: listOf(Candidate(composing.toString(), composing.length))
        } catch (e: Exception) {
            Log.e(TAG, "変換に失敗", e)
            listOf(Candidate(composing.toString(), composing.length))
        }
        selected = -1
        if (::candidateBar.isInitialized) candidateBar.setCandidates(candidates, -1)
        if (::keyboardView.isInitialized) keyboardView.invalidate()
    }

    private fun nextCandidate(delta: Int) {
        if (candidates.isEmpty()) return
        selected = if (selected < 0) 0 else (selected + delta + candidates.size) % candidates.size
        toggleKey = null
        val c = candidates[selected]
        currentInputConnection?.setComposingText(c.text + composing.substring(c.length), 1)
        candidateBar.setSelected(selected)
    }

    /** 候補を確定し、残りの読みは入力中のまま残す */
    private fun pickCandidate(index: Int) {
        val c = candidates.getOrNull(index) ?: return
        val ic = currentInputConnection ?: return
        val rest = composing.substring(c.length)
        composing.clear()
        toggleKey = null
        selected = -1
        ic.commitText(c.text, 1)
        if (rest.isNotEmpty()) {
            composing.append(rest)
            ic.setComposingText(composing, 1)
        }
        updateCandidates()
    }

    /** 候補を選んでいる最中に文字を打ったら、その候補を確定する */
    private fun commitSelection() {
        if (selected >= 0) pickCandidate(selected)
    }

    private fun commitComposing() {
        if (composing.isEmpty()) return
        if (selected >= 0) {
            pickCandidate(selected)
            if (composing.isEmpty()) return
        }
        currentInputConnection?.commitText(composing.toString(), 1)
        composing.clear()
        toggleKey = null
        updateCandidates()
    }

    private fun resetComposing() {
        if (composing.isNotEmpty()) currentInputConnection?.finishComposingText()
        composing.clear()
        toggleKey = null
        selected = -1
        candidates = emptyList()
        if (::candidateBar.isInitialized) candidateBar.setCandidates(emptyList(), -1)
    }

    // ---------------- 共通 ----------------

    private fun setMode(m: Mode) {
        mode = m
        if (m != Mode.EN) shift = 0
        applyLayout()
        updateAutoShift()
    }

    private fun saveLang(lang: String) = prefs.edit().putString("lang", lang).apply()

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            ic.commitText("", 1)
        }
    }

    private fun performEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    companion object {
        private const val TAG = "S6Keyboard"
    }
}
