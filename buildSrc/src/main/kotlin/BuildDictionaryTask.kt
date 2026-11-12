import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URI

/**
 * Mozc のオープンソース辞書 (src/data/dictionary_oss) をダウンロードし、
 * キーボードが mmap で直接読める二進形式に変換する。
 *
 * 出力:
 *  - dict.bin : 読み → 単語エントリ
 *  - conn.bin : 品詞連接コスト行列
 *  - dictionary-license.txt : Mozc / IPAdic のライセンス表記
 *
 * dict.bin のレイアウト (ビッグエンディアン)
 *   int magic 'MZD1', int nReadings, int nEntries, int readingCharsLen, int wordCharsLen
 *   int readingOff[nReadings + 1]   (readingChars 内の文字オフセット)
 *   int entryOff[nReadings + 1]     (entries 内のエントリ番号)
 *   entries[nEntries] : short lid, short rid, short cost, short wordLen, int wordOff
 *       wordOff == -1 : 単語 = 読み (ひらがな)
 *       wordOff == -2 : 単語 = 読みのカタカナ
 *   char readingChars[readingCharsLen]  (読みは UTF-16 の辞書順にソート済み)
 *   char wordChars[wordCharsLen]
 *
 * conn.bin のレイアウト
 *   int magic 'MZC1', int n, short cost[n * n]  (index = 前の右ID * n + 次の左ID)
 */
abstract class BuildDictionaryTask : DefaultTask() {
    @get:Input
    abstract val mozcCommit: Property<String>

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private class Entry(val lid: Int, val rid: Int, val cost: Int, val word: String)

    @TaskAction
    fun run() {
        val commit = mozcCommit.get()
        val cache = cacheDir.get().asFile.resolve(commit).apply { mkdirs() }
        val base = "https://raw.githubusercontent.com/google/mozc/$commit/src/data/dictionary_oss/"
        val names = (0..9).map { "dictionary0$it.txt" } + listOf("connection_single_column.txt", "README.txt")
        for (name in names) {
            val f = cache.resolve(name)
            if (!f.exists() || f.length() == 0L) {
                logger.lifecycle("Downloading $name")
                val tmp = File(cache, "$name.part")
                URI(base + name).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(f)
            }
        }

        val out = outputDir.get().asFile.apply { mkdirs() }

        // ---- 辞書 ----
        val map = HashMap<String, MutableList<Entry>>(1 shl 20)
        for (i in 0..9) {
            cache.resolve("dictionary0$i.txt").forEachLine(Charsets.UTF_8) { line ->
                val p = line.split('\t')
                if (p.size >= 5) {
                    map.getOrPut(p[0]) { ArrayList(2) }
                        .add(Entry(p[1].toInt(), p[2].toInt(), p[3].toInt().coerceAtMost(Short.MAX_VALUE.toInt()), p[4]))
                }
            }
        }
        val readings = map.keys.sorted()
        val wordPool = StringBuilder()
        val wordIndex = HashMap<String, Int>()
        val readingChars = StringBuilder()
        val readingOff = IntArray(readings.size + 1)
        val entryOff = IntArray(readings.size + 1)
        val entryBytes = java.io.ByteArrayOutputStream(16 shl 20)
        val ed = DataOutputStream(entryBytes)
        var nEntries = 0
        readings.forEachIndexed { idx, r ->
            readingOff[idx] = readingChars.length
            entryOff[idx] = nEntries
            readingChars.append(r)
            val kata = toKatakana(r)
            // 同じ (単語, 左ID, 右ID) は最小コストのみ残し、コスト順に並べる
            val list = map[r]!!
                .groupBy { Triple(it.word, it.lid, it.rid) }
                .map { (_, v) -> v.minBy { it.cost } }
                .sortedBy { it.cost }
            for (e in list) {
                ed.writeShort(e.lid)
                ed.writeShort(e.rid)
                ed.writeShort(e.cost)
                when (e.word) {
                    r -> { ed.writeShort(0); ed.writeInt(-1) }
                    kata -> { ed.writeShort(0); ed.writeInt(-2) }
                    else -> {
                        val off = wordIndex.getOrPut(e.word) {
                            val o = wordPool.length
                            wordPool.append(e.word)
                            o
                        }
                        ed.writeShort(e.word.length)
                        ed.writeInt(off)
                    }
                }
                nEntries++
            }
        }
        readingOff[readings.size] = readingChars.length
        entryOff[readings.size] = nEntries
        ed.flush()

        DataOutputStream(BufferedOutputStream(out.resolve("dict.bin").outputStream(), 1 shl 16)).use { o ->
            o.writeInt(0x4D5A4431) // MZD1
            o.writeInt(readings.size)
            o.writeInt(nEntries)
            o.writeInt(readingChars.length)
            o.writeInt(wordPool.length)
            readingOff.forEach { o.writeInt(it) }
            entryOff.forEach { o.writeInt(it) }
            entryBytes.writeTo(o)
            o.writeChars(readingChars.toString())
            o.writeChars(wordPool.toString())
        }
        logger.lifecycle("dict.bin: ${readings.size} readings, $nEntries entries")

        // ---- 連接コスト ----
        cache.resolve("connection_single_column.txt").bufferedReader().use { br ->
            val n = br.readLine().trim().toInt()
            DataOutputStream(BufferedOutputStream(out.resolve("conn.bin").outputStream(), 1 shl 16)).use { o ->
                o.writeInt(0x4D5A4331) // MZC1
                o.writeInt(n)
                repeat(n * n) {
                    val v = br.readLine().trim().toInt()
                    o.writeShort(v.coerceAtMost(Short.MAX_VALUE.toInt()))
                }
            }
        }

        cache.resolve("README.txt").copyTo(out.resolve("dictionary-license.txt"), overwrite = true)
    }

    private fun toKatakana(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
        return sb.toString()
    }
}
