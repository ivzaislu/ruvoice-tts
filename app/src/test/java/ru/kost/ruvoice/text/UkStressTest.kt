// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test
import ru.kost.ruvoice.DictLines
import ru.kost.ruvoice.TestData
import java.io.File
import java.util.zip.ZipFile

/** Украинские ударения: [LangStress] с [UK_STRESS] поверх акцентора пака и словарей языка. */
class UkStressTest {
    private val d = TestData.data()

    /** Акцентор-заглушка: каждому слову — вероятности по его гласным (так отдаёт accentor.ptl). */
    private class Fake(private val probs: FloatArray) : StressModels {
        var asked: List<String> = emptyList()
        override fun accentor(words: List<String>): Pair<Array<FloatArray>, Array<FloatArray>> {
            asked = words
            return Pair(Array(words.size) { probs }, emptyArray())
        }
        override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray): FloatArray = FloatArray(0)
    }

    private fun stress(m: StressModels, dict: Map<String, String> = emptyMap(), rules: Rules = Rules()) =
        Stress(d, m, dict.entries.associate { (k, v) -> UkLayer.dictWord(k) to UkLayer.dictWord(v) }, rules, layer = UkLayer)

    @Test fun serviceAndShortWordsNotMarked() {
        val m = Fake(floatArrayOf(0.1f, 0.9f))
        // «на», «що», «або», «це» — служебные; «кіт» односложное: акцентор спрашиваем только про «столі»
        assertEquals("на стол+і що кіт або це", stress(m).apply("на столі що кіт або це"))
        assertEquals(listOf("столі"), m.asked)
    }

    @Test fun userDictWinsOverAccentor() {
        val m = Fake(floatArrayOf(0.1f, 0.9f))
        assertEquals("м+ама", stress(m, mapOf("мама" to "м+ама")).apply("мама"))
        assertEquals(emptyList<String>(), m.asked)
    }

    @Test fun hardSignLookedUpWithoutAndKept() {
        // «мъясо» (апостроф → «ъ» в UkNormalizer) ищется как «мясо»: словарь — с апострофом, как пишет пользователь
        assertEquals("мъ+ясо", stress(Fake(floatArrayOf(0.9f, 0.1f)), mapOf("м'ясо" to "м'+ясо")).apply("мъясо"))
        val m = Fake(floatArrayOf(0.1f, 0.9f))
        assertEquals("пъятн+иця", stress(m).apply("пъятниця"))
        assertEquals(listOf("пятниця"), m.asked)
        // через нормализатор, как на телефоне: «’» и «ʼ» тоже
        for (a in listOf("'", "’", "ʼ"))
            assertEquals("підъ+їзд", stress(Fake(floatArrayOf(0.1f, 0.9f))).apply(UkLayer.prepare("Під${a}їзд", TestData.cisAllowed, Rules())))
    }

    @Test fun readyPlusKept() {
        // готовое «+» (из текста, словаря, имени буквы) не режется и второй раз не размечается; «ъ» рядом с ним остаётся
        val m = Fake(floatArrayOf(0.9f, 0.1f))
        assertEquals("з+амок і пъ+ять і мъяк+ий", stress(m).apply("з+амок і пъ+ять і мъяк+ий"))
        assertEquals("п+ідъїзд", stress(m).apply("п+ідъїзд"))
        assertEquals(emptyList<String>(), m.asked)
    }

    @Test fun loneHardSignDoesNotFail() {
        // «ъ» без букв вокруг: без него слово пустое — у обоих языков не падает, остальные слова размечаются (сам «ъ» звука не
        // даёт; последний в слове withHard не возвращает — как было у белорусского)
        for (layer in listOf(UkLayer, BeLayer))
            assertEquals(", ъ- м+ама", Stress(d, Fake(floatArrayOf(0.9f, 0.1f)), mapOf("x" to "y"), Rules(), layer = layer).apply("Ъ, ъ-ъ мама"))
    }

    @Test fun hyphenWordWholeThenParts() {
        val m = Fake(floatArrayOf(0.9f, 0.1f))
        val s = stress(m, mapOf("по-українськи" to "по-укра+їнськи"))
        assertEquals("По-укра+їнськи", s.apply("По-українськи"))
        assertEquals(emptyList<String>(), m.asked)
        assertEquals("Ч+орно-б+ілий", s.apply("Чорно-білий"))
        assertEquals(listOf("чорно", "білий"), m.asked)
    }

    @Test fun accentorRuleOffLeavesOnlyDict() {
        val boom = object : StressModels {
            override fun accentor(words: List<String>): Pair<Array<FloatArray>, Array<FloatArray>> = throw AssertionError("акцентор: $words")
            override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray): FloatArray = throw AssertionError("homo")
        }
        assertEquals("дякую +село", stress(boom, mapOf("село" to "+село"), Rules(setOf("accentor"))).apply("дякую село"))
    }

    @Test fun russianWordThroughUkrainianVoice() {
        // «ё», «ы», «э» — буквы слова: русское слово не режется; гласные по украинской таблице
        val m = Fake(floatArrayOf(0.1f, 0.9f))
        assertEquals("прив+ет, это ёлка", stress(m).apply("привет, это ёлка"))
        assertEquals(listOf("привет"), m.asked)
        assertEquals("съезд объ+явлен", stress(Fake(floatArrayOf(0.1f, 0.9f, 0f))).apply("съезд объявлен"))
    }

    @Test fun tablesMatchExportPack() {
        // NO_MARK['uk'] и VOWELS['uk'] из tools/export_pack.py: по ним собран stress.txt пака
        val py = File(TestData.root(), "tools/export_pack.py").readText()
        val noMark = Regex("""'uk': set\('([^']*)'\s*'([^']*)'\.split\(\)\)""").find(py)!!.groupValues.drop(1).joinToString("")
        assertEquals(noMark.split(' ').filter { it.isNotEmpty() }.toSet(), UK_STRESS.noMark)
        assertEquals(Regex("""'uk': '([^']*)'""").find(py)!!.groupValues[1], UK_STRESS.vowels)
    }

    /** Каждая запись stress.txt собранного пака находится и ставится: слово через [UkLayer.prepare] и [Stress] со списком пака
     * (как «Системный uk» после [ru.kost.ruvoice.DictCache]) выходит ровно с этим «+». Пака нет (dist/ не собран) — пропуск. */
    @Test fun packStressTxtAllEntriesApply() {
        val zip = File(TestData.root(), "dist/ruvoice-pack-cis_uk.zip")
        Assume.assumeTrue("нет $zip", zip.exists())
        val lines = ZipFile(zip).use { z -> z.getInputStream(z.getEntry("stress.txt")).bufferedReader().readLines() }
        val entries = lines.mapNotNull { DictLines.parseStress(it) }
        val dict = entries.associate { (w, v) -> UkLayer.dictWord(w.lowercase()) to UkLayer.dictWord(v.lowercase()) }
        // акцентор ставит ударение на последнюю гласную: запись, до которой словарь не дошёл, выйдет иначе
        val s = Stress(d, object : StressModels {
            override fun accentor(words: List<String>) = Pair(Array(words.size) { i ->
                FloatArray(words[i].count { it in UK_STRESS.vowels }).also { if (it.isNotEmpty()) it[it.size - 1] = 1f } }, emptyArray<FloatArray>())
            override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray) = FloatArray(0)
        }, dict, Rules(), layer = UkLayer)
        val dead = entries.filter { (w, v) -> s.apply(UkLayer.prepare(w, TestData.cisAllowed, Rules())) != v.lowercase() }
        println("stress.txt: ${entries.size} записей, не сработали ${dead.size}: ${dead.take(20)}")
        assertEquals(dead.take(20).toString(), 0, dead.size)
    }
}
