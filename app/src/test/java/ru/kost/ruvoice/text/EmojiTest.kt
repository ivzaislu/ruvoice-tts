package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.TestData
import java.io.File

/** Эмодзи по имени (assets/emoji_ru.tsv, правило emoji). */
class EmojiTest {
    private val emoji = File(TestData.root(), "app/src/main/assets/emoji_ru.tsv").bufferedReader().useLines { Emoji(it) }

    @Test fun table() = assertTrue(emoji.size > 3000)

    @Test fun inText() {
        assertEquals("Привет, широко улыбается", emoji.apply("Привет 😀"))
        assertEquals("Ок, алое сердце, спасибо", emoji.apply("Ок ❤️ спасибо"))
        assertEquals("большой палец вверх", emoji.apply("👍"))
        assertEquals("Готово, большой палец вверх!", emoji.apply("Готово 👍!"))
        assertEquals("широко улыбается, большой палец вверх", emoji.apply("😀👍"))
    }

    @Test fun sequences() {
        // флаг, тон кожи (без тона в имени), вариант начертания U+FE0F и без него, клавиша
        assertEquals("флаг: Антарктида", emoji.apply("🇦🇶"))
        assertEquals("машет рукой", emoji.apply("👋🏽"))
        assertEquals(emoji.apply("❤️"), emoji.apply("❤"))
        assertEquals("клавиша 1", emoji.apply("1️⃣"))
        // семья держится на U+200D — одно имя, не три человека
        assertTrue(emoji.apply("👨‍👩‍👧").split(",").size == 1)
    }

    @Test fun repeatsOnce() {
        assertEquals("Ха, смеется до слез", emoji.apply("Ха 😂😂😂"))
        assertEquals("смеется до слез, широко улыбается", emoji.apply("😂😂😀"))
    }

    @Test fun plainTextUntouched() {
        for (t in listOf("Цена 100 ₽, № 5, «цитата» — 1", "# заголовок", "5 * 3"))
            assertEquals(t, emoji.apply(t))
    }

    @Test fun rule() {
        val allowed = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяАБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ .,!?-–:;+«»()"
        Emoji.shared = emoji
        try {
            assertEquals("привет, широко улыбается", Normalizer.prepare("Привет 😀", allowed))
            assertEquals("привет", Normalizer.prepare("Привет 😀", allowed, Rules(off = setOf("emoji"))))
        } finally { Emoji.shared = null }
    }

    /** Таблица языка слоя ([Emoji.be], [Emoji.uk], CLDR-знаки be/uk): без контекста — только поставленное руками; с
     * контекстом — читается один раз при первом обращении; поставленное руками (и null) больше не перечитывается. */
    @Test fun assetTableLazy() {
        var opened = 0
        var v: String? by AssetTable("x.tsv") { _, name -> opened++; name }
        assertEquals(null, v); assertEquals(0, opened)
        AssetTable.context = android.content.ContextWrapper(null)
        try {
            assertEquals("x.tsv", v); assertEquals("x.tsv", v); assertEquals(1, opened)
            v = null
            assertEquals(null, v); assertEquals(1, opened)
        } finally { AssetTable.context = null }
    }
}
