# S6 Keyboard

自作の Android キーボード (IME) です。英語は QWERTY、日本語はフリック入力で、Mozc のオープンソース辞書を使った簡易かな漢字変換を内蔵しています。

## インストール

1. [Releases の `latest`](../../releases/tag/latest) から `s6-keyboard.apk` をダウンロードしてインストールします (GitHub Actions が main への push ごとにビルドします)
2. アプリ「S6 Keyboard」を開き、「入力方法の設定を開く」で有効にします
3. 「キーボードを切り替える」で S6 Keyboard を選びます

## 使い方

| キー | 動作 |
| --- | --- |
| 🌐 | 日本語 ⇔ 英語。長押しでほかのキーボードに切り替え |
| 英字キー | 長押しまたは上フリックで右上の記号 |
| `!#1` / `ABC` / `1/2` | 記号ページへ / 英字へ戻る / 記号ページ切替 |
| ⇧ | 1 回で次の 1 文字だけ大文字、2 回続けて押すと大文字ロック |
| かなキー | タップでトグル (あ→い→う…)、上下左右へのフリックで各段の文字。長押しで右上の数字 |
| ゛゜小 | 直前の文字を濁音・半濁音・小書きに切り替え |
| ↺ | トグルを 1 つ戻す |
| ‹ › | カーソル移動。トグル中は › で次の文字へ、候補選択中は候補の移動 |
| 候補 (空白) / 変換 | 入力中に押すと候補を順に選択 |
| OK (Enter) | 選んでいる候補、またはひらがなのまま確定 |
| 候補バー | タップした候補で確定。文の先頭部分だけの候補を選ぶと、残りは入力中のまま残ります |

## 仕組み

- `KeyboardView` — キーの描画とタップ・フリック・長押し判定 (Canvas で自前描画)
- `KeyboardIME` — `InputMethodService`。入力モードの切り替え、入力中文字列、候補選択
- `Converter` — かな漢字変換。辞書引きでラティスを作り、Mozc の品詞連接コストを使って Viterbi で最良の変換を求めます。先頭の単語の候補は「その後ろを最良に変換したときの総コスト」で並べます
- `buildSrc/BuildDictionaryTask` — ビルド時に Mozc の `src/data/dictionary_oss` (固定コミット) をダウンロードし、mmap で読める二進形式 (`dict.bin`, `conn.bin`) に変換します。辞書は APK 内に無圧縮で入り、アプリはそれを直接 mmap します

## ビルド

```bash
./gradlew :app:assembleRelease
```

- JDK 17 以上と Android SDK (platform 35) が必要です
- 初回は Mozc の辞書ファイル (約 100MB) を `.dict-cache/` にダウンロードします
- `./gradlew :app:testDebugUnitTest` で変換のテストと、各レイアウトの画像出力 (`app/build/screenshots/`) を行います

### 署名について

リリース APK は環境変数 `SIGNING_STORE_FILE` / `SIGNING_STORE_PASSWORD` / `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD` で指定した鍵で署名し、未指定ならデバッグ鍵で署名します。

GitHub Actions のビルドを毎回同じ鍵で署名する (= 上書きアップデートできるようにする) には、リポジトリの Settings → Secrets and variables → Actions に次を登録します。

- `KEYSTORE_BASE64`: キーストアを `base64 -w0 release.jks` した文字列
- `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`

署名鍵が変わると上書きインストールできないため、一度アンインストールしてから入れ直してください。

## 今後の課題 (未実装)

- 英語の予測変換・自動修正
- 文節の区切り直し、学習 (よく使う候補を上に)、ユーザー辞書
- 絵文字・顔文字パレット、全角/半角切り替え
- テーマやキーの高さなどの設定画面
- 辞書サイズの削減 (現在 APK は約 50MB)

## ライセンス

かな漢字変換の辞書データは [Mozc](https://github.com/google/mozc) の `dictionary_oss` (IPAdic ほか) を使用しています。ライセンス表記はアプリ内の設定画面と、ビルド時に生成される `dictionary-license.txt` にあります。
