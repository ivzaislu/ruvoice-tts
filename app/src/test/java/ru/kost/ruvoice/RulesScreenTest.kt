package ru.kost.ruvoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.text.QuestionLists
import ru.kost.ruvoice.text.Rules
import ru.kost.ruvoice.text.RuLayer
import ru.kost.ruvoice.text.TextLayer
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** Экран «Правила»: строки на трёх языках интерфейса и что видно на вкладке языка (Rules.rows). */
class RulesScreenTest {
    private val locales = listOf("values", "values-en", "values-zh")

    /** Имена <string> из strings.xml локали. */
    private fun names(loc: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(TestData.root(), "app/src/main/res/$loc/strings.xml"))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }

    @Test fun stringsInAllLocales() {
        val all = locales.associateWith(::names)
        for ((loc, n) in all) {
            for (key in Rules.KEYS) {
                assertTrue("$loc: rule_$key", "rule_$key" in n)
                assertTrue("$loc: rule_${key}_hint", "rule_${key}_hint" in n)
            }
            for (s in Rules.SECTIONS.values) assertTrue("$loc: rules_section_$s", "rules_section_$s" in n)
            for (s in listOf("rules_lang_ru", "rules_lang_be", "rules_lang_hint")) assertTrue("$loc: $s", s in n)
        }
        // подсказка вкладки языка (rule_<key>_hint_<код>) — во всех трёх или ни в одной, и только у настоящего ключа
        val langHint = Regex("rule_(.+)_hint_([a-z]{2})")
        for (name in all.values.flatten().toSet()) {
            val m = langHint.matchEntire(name) ?: continue
            assertTrue("$name: нет ключа ${m.groupValues[1]}", m.groupValues[1] in Rules.KEYS)
            for ((loc, n) in all) assertTrue("$loc: $name", name in n)
        }
    }

    /** Слой с выбранными [unsupported]; остальное экрану правил не нужно. */
    private class FakeLayer(override val unsupported: Set<String>) : TextLayer {
        override val code = "xx"
        override val locale: Locale = Locale.ROOT
        override fun prepare(text: String, allowed: String, rules: Rules) = text
        override fun questions(d: SileroData): QuestionLists = error("не нужен")
        override fun letterName(c: Char, lone: Boolean, end: Boolean): String? = null
        override fun symbolName(c: Char, punct: Boolean): String? = null
    }

    private fun heads(rows: List<Pair<String, String?>>) = rows.filter { it.second != null }.associate { it.first to it.second }

    @Test fun russianTabIsTheWholeScreen() {
        val rows = Rules.rows(RuLayer)
        val english = Rules.KEYS.dropWhile { it != "en_proxy_books" }.takeWhile { it != "dehyphen" }
        assertEquals(Rules.KEYS - english.toSet(), rows.map { it.first })
        assertEquals(Rules.SECTIONS.filterValues { it != "english" }, heads(rows))
        // страница «Английский» из меню ⋮ — только её раздел
        assertEquals(listOf("en_proxy_books" to "english", "en_proxy_sr" to null), Rules.rows(RuLayer, english = true))
    }

    @Test fun hiddenKeysMoveSectionHeader() {
        // первые ключи разделов скрыты — заголовок над первым видимым; раздел целиком скрыт — заголовка нет
        val rows = Rules.rows(FakeLayer(setOf("sr_fast_start", "phones", "codes", "gram", "verbose_log")))
        val keys = rows.map { it.first }
        assertTrue("phones" !in keys && "codes" !in keys && "verbose_log" !in keys)
        assertEquals(mapOf("sr_symbols" to "talkback", "symbol_names" to "misc", "numbers" to "numbers", "abbrev" to "abbrev",
            "dehyphen" to "split", "first_pl" to "stress", "pause_semicolon" to "audio"), heads(rows))
        // у каждого видимого раздела заголовок ровно один
        assertEquals(heads(rows).values.toSet().size, heads(rows).size)
        assertEquals(listOf("en_proxy_sr" to "english"), Rules.rows(FakeLayer(setOf("en_proxy_books")), english = true))
    }
}
