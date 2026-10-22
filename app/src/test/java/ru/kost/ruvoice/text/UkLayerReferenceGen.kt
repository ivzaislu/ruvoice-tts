// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ru.kost.ruvoice.Symbols
import ru.kost.ruvoice.TestData
import java.io.File
import java.util.zip.ZipFile

/**
 * Эталон для телефонного `UkrainianPackDeviceTest.layerSameAsJvm`: строковые литералы из `Uk*Test.kt` (входы) и их вывод
 * на JVM через [Splitter.sentences] + [UkLayer.prepare], в `app/build/uk_layer_in.txt` и `app/build/uk_layer_jvm.txt`
 * (строка на пример, «\n» и «\\» экранированы). Морфология и символы — из `dist/ruvoice-pack-cis_uk.zip`, русская
 * морфология и таблицы эмодзи и знаков — из ассетов, как их грузит `SileroModels.data` на телефоне. Пака нет — пропуск.
 *
 * Пересобрать и отдать телефону после правки слоя:
 * `./gradlew :app:testFullDebugUnitTest --tests '*UkLayerReferenceGen*' --rerun`, затем
 * `adb push app/build/uk_layer_in.txt app/build/uk_layer_jvm.txt /data/local/tmp/`.
 */
class UkLayerReferenceGen {
    private val lit = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")
    private val template = Regex("(?<!\\\\)\\$[{A-Za-z_]")
    private val letter = Regex("[0-9А-Яа-яЄєІіЇїҐґ]")

    /** Литерал Kotlin → строка: \uXXXX, \n, \t, \r, \b, остальное — сам знак. Сырые строки и шаблоны пропускаются. */
    private fun inputs(dir: File): List<String> {
        val out = LinkedHashSet<String>()
        for (f in dir.listFiles { f -> f.name.startsWith("Uk") && f.name.endsWith("Test.kt") }!!.sortedBy { it.name }) {
            val src = f.readText().replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
            for (m in lit.findAll(src)) {
                val raw = m.groupValues[1]
                if (template.containsMatchIn(raw)) continue
                val s = Regex("\\\\u([0-9a-fA-F]{4})|\\\\(.)").replace(raw) { e ->
                    if (e.groupValues[1].isNotEmpty()) e.groupValues[1].toInt(16).toChar().toString()
                    else when (val c = e.groupValues[2]) { "n" -> "\n"; "t" -> "\t"; "r" -> "\r"; "b" -> "\b"; else -> c }
                }
                if (s.length >= 2 && letter.containsMatchIn(s)) out += s
            }
        }
        return out.toList()
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n")

    @Test fun writeReference() {
        val root = TestData.root()
        val zip = File(root, "dist/ruvoice-pack-cis_uk.zip")
        Assume.assumeTrue("нет $zip", zip.exists())
        val build = File(root, "app/build").also { it.mkdirs() }
        // свой файл на прогон: full и lite идут параллельно, общий путь в app/build они перезаписывали бы друг у друга
        val morphFile = File.createTempFile("uk_layer_morph", ".bin")
        val packJson = ZipFile(zip).use { z ->
            z.getInputStream(z.getEntry("morph.bin")).use { i -> morphFile.outputStream().use { i.copyTo(it) } }
            z.getInputStream(z.getEntry("pack.json")).bufferedReader().readText()
        }
        val allowed = Symbols.fromJson(JSONObject(packJson)).allowed
        val a = File(root, "app/src/main/assets")
        val saved = listOf(UkNumbers.morph, Normalizer.morph, Emoji.shared, Emoji.be, Emoji.uk, SymbolNames.cldr, SymbolNames.cldrBe, SymbolNames.cldrUk)
        try {
            UkNumbers.morph = Morph.open(morphFile)
            Normalizer.morph = Morph.open(File(a, "morph.bin"))
            Emoji.shared = File(a, "emoji_ru.tsv").bufferedReader().useLines { Emoji(it) }
            Emoji.be = File(a, "emoji_be.tsv").bufferedReader().useLines { Emoji(it) }
            Emoji.uk = File(a, "emoji_uk.tsv").bufferedReader().useLines { Emoji(it) }
            SymbolNames.cldr = SymbolNames.parse(File(a, "symbols_ru.tsv").readLines().asSequence())
            SymbolNames.cldrBe = SymbolNames.parse(File(a, "symbols_be.tsv").readLines().asSequence())
            SymbolNames.cldrUk = SymbolNames.parse(File(a, "symbols_uk.tsv").readLines().asSequence())
            val ins = inputs(File(root, "app/src/test/java/ru/kost/ruvoice/text"))
            val outs = ins.map { t ->
                runCatching { Splitter.sentences(t, Rules(), layer = UkLayer).joinToString(" | ") { UkLayer.prepare(it, allowed, Rules()) } }
                    .getOrElse { "!${it.javaClass.simpleName}: ${it.message}" }
            }
            File(build, "uk_layer_in.txt").writeText(ins.joinToString("") { esc(it) + "\n" })
            File(build, "uk_layer_jvm.txt").writeText(outs.joinToString("") { esc(it) + "\n" })
            println("uk_layer: ${ins.size} примеров → app/build/uk_layer_in.txt, uk_layer_jvm.txt")
            assertTrue("примеров ${ins.size}", ins.size > 1000)
            val crashed = ins.indices.filter { outs[it].startsWith("!") }
            assertTrue("исключения: ${crashed.take(5).map { ins[it] to outs[it] }}", crashed.isEmpty())
        } finally {
            @Suppress("UNCHECKED_CAST")
            run {
                UkNumbers.morph = saved[0] as Morph?; Normalizer.morph = saved[1] as Morph?
                Emoji.shared = saved[2] as Emoji?; Emoji.be = saved[3] as Emoji?; Emoji.uk = saved[4] as Emoji?
                SymbolNames.cldr = saved[5] as Map<Char, String>?; SymbolNames.cldrBe = saved[6] as Map<Char, String>?
                SymbolNames.cldrUk = saved[7] as Map<Char, String>?
            }
            morphFile.delete()
        }
    }
}
