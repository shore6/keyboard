package io.github.shore6.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

class ConverterTest {
    companion object {
        private lateinit var converter: Converter

        private fun map(name: String) = RandomAccessFile(File("build/generated/assets/buildDictionary", name), "r").use {
            it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length())
        }

        @BeforeClass
        @JvmStatic
        fun setUp() {
            converter = Converter(Dictionary(map("dict.bin"), map("conn.bin")))
        }
    }

    private fun top(s: String) = converter.convert(s).first().text

    @Test
    fun sentences() {
        for (s in listOf("こんにちは", "きょうはいいてんきです", "わたしのなまえはなかのです", "にほんごをにゅうりょくする", "かんじへんかん")) {
            val start = System.nanoTime()
            val list = converter.convert(s)
            println("$s -> ${list.take(8).joinToString(" | ") { "${it.text}(${it.length})" }}  ${(System.nanoTime() - start) / 1000}us")
        }
        assertEquals("今日はいい天気です", top("きょうはいいてんきです"))
    }

    @Test
    fun includesKanaFallbacks() {
        val list = converter.convert("てすと")
        assertTrue(list.any { it.text == "てすと" })
        assertTrue(list.any { it.text == "テスト" })
    }

    @Test
    fun unknownCharacters() {
        val list = converter.convert("3じ、")
        assertTrue(list.first().text.startsWith("3"))
        println(list.take(5))
    }
}
