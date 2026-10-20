// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.TestData

/** Ключи «Правил» у белорусского слоя: те же, что у русского, где смысл совпадает; прочие — в [BeLayer.unsupported]. */
class BelRulesTest {

    private val cis = TestData.cisAllowed
    private fun prep(t: String, rules: Rules = Rules()) = BeLayer.prepare(t, cis, rules)
    private fun off(vararg keys: String) = keys.fold(Rules()) { r, k -> r.with(k, false) }

    /** Ключ, который слой читает → пример, где без него вывод другой. */
    private val read = mapOf(
        "currency" to "Кошт 1,20 BYN.",
        "roman" to "XX стагоддзе",
        "roman_name" to "Пётр I",
        "latin" to "Кніга FilmBell",
        "spell_lat" to "USB",
        "letter_digit" to "Galaxy S24",
        "homoglyphs" to "cок",
        "abbrev" to "Яблыкі і г. д.",
        "numbers" to "тэмпература +11",
        "phones" to "8 029 123-45-67",
        "times" to "21:30",
        "dates" to "12.05.2026",
        "day_month" to "3 мая",
        "gen_suffix" to "Адзін з 3-х.",
        "years" to "2026 год",
        "units" to "3 км",
        "degrees" to "20 °C",
        "pause_parens" to "Кніга (том 2) тут",
    )

    /** Примеры из BelNumbersTest и LayersTest: ключи из unsupported их вывод не меняют. */
    private val samples = listOf(
        "2026", "Прыв'ет!", "Што?!", "дзя́куй", "Мінск — сталіца", "\"Так\", — сказаў ён.", "Кніга (том 2) тут",
        "Сёння 2026 год, 3 тамы", "2 руб.", "5 тыс. руб.", "цана ў руб.", "80291234567", "+375 29 123 45 67", "1 000 000",
        "у 2000 годзе", "1998 года", "100 гадоў", "5 г. солі", "1999 г.", "1.5 кг", "1,05 кг", "3,14", "Сустрэча 15.06 г. Мінск",
        "1.10.2", "у 1812 г.", "з 500 г.", "12.05.2026 г. было", "3 мая 2026 года было", "8 029 123-45-67", "222-33-44",
        "10 000-15 000", "007", "+1 °C", "-20°С", "ад -5 да +3", "Ту-154", "1-га верасня", "у 5-м класе", "у 1990-я гг.",
        "у 1990-х гадах", "Адзін з 3-х.", "яна — 2-й", "XX стагоддзе", "ХІХ стагоддзе", "FBI", "а ў XX ст. — сядзіба.",
        "XIX–XX стст. былі", "Пётр I і Кацярына II", "Указ Пятра I быў жорсткі.", "Шамякін I. П.", "Курган III ст. да н. э.",
        "Цана вырасла ў 1.25 раза.", "з 9:00 па 18.00", "У 1990-х гг. Мінск рос.", "Пачатак а 19.00.", "з 12.05 па 15.05",
        "Кошт 1,20 BYN.", "Курс BYN", "Кошт $75,3 млрд. Яшчэ", "€100 млн", "5 %.", "у 1941–1945 гг. горад", "15-16 мая",
        "з 1 па 5 мая", "1-2 разы", "١٢ км", "20 °C", "1/2 шклянкі", "Падзея адбылася ў 2024 г. Цяпер там музей.",
        "Кошт 5 тыс. руб. Тэлефон ёсць.", "Вага 5kg.", "60 км/г. Далей", "LTE 4G", "Galaxy S24", "Wi-Fi", "USB-C",
        "Яблыкі, грушы і г. д.", "Гэта, г. зн., памылка.", "Грушы і інш. Потым", "Музей на вул. Леніна, 5.", "21:30", "cок",
        "C++", "д+якуй", "Мiнск", "https://example.com", "Тэл. 1234-5678-9012-3456", "2 + 3 = 5", "3/4 шклянкі", "1 ч. л.",
        "§ 5", "п. 2.10.3", "1 000 000", "Тэкст[1].", "ПРЫВІТАННЕ СВЕТ", "😀 Добра", "Вось → там", "па-беларуску",
    )

    @Test fun eachReadKeyChangesOutput() {
        for ((k, t) in read) assertNotEquals(k, prep(t), prep(t, off(k)))
        assertTrue(read.keys.all { it in Rules.KEYS })
        assertTrue(read.keys.none { it in BeLayer.unsupported })
    }

    @Test fun unsupportedKeysDoNotChangeOutput() {
        assertTrue(BeLayer.unsupported.all { it in Rules.KEYS })
        // акцентор белорусский тоже читает (BelStress), он не в списке
        assertFalse("accentor" in BeLayer.unsupported)
        for (k in BeLayer.unsupported) {
            val flipped = Rules().with(k, !Rules().on(k))
            for (t in samples) assertEquals("$k: $t", prep(t), prep(t, flipped))
        }
    }

    @Test fun romanOff() {
        assertEquals("XX стагоддзе", BelNumbers.roman("XX стагоддзе", off("roman")))
        // без правила римское — латиница по буквам; после имени — своё правило roman_name
        assertEquals("экс-экс стагоддзе", prep("XX стагоддзе", off("roman")))
        assertEquals("пётр першы і экс-экс стагоддзе", prep("Пётр I і XX стагоддзе", off("roman")))
        assertEquals("пётр і і дваццатае стагоддзе", prep("Пётр I і XX стагоддзе", off("roman_name")))
    }

    @Test fun currencyOff() {
        // единицы не валюта: «км» читается как был
        assertEquals("тры кіламетры", BelNumbers.units("3 км", off("currency")))
        assertEquals("2 руб.", BelNumbers.units("2 руб.", off("currency")))
        assertEquals("два руб.", prep("2 руб.", off("currency")))
        assertEquals("сто бэ-уай-эн", prep("100 BYN", off("currency")))
        assertEquals("пяць", prep("$5", off("currency")))
        assertEquals("пяць кілаграмаў", prep("5 кг", off("currency")))
    }

    @Test fun unitKeys() {
        assertEquals("тры км", prep("3 км", off("units")))
        assertEquals("тры кіламетры, два рублі.", prep("3 км, 2 руб.", off("degrees")))
        assertEquals("дваццаць с", prep("20 °C", off("degrees")))
        // «тыс.», «млн» — сокращения: правило abbrev
        assertEquals("пяць тыс. рублёў. тэлефон ёсць.", prep("Кошт 5 тыс. руб. Тэлефон ёсць.", off("abbrev")).removePrefix("кошт "))
        // «г.» за числом без единиц — не «горад»
        assertEquals("мука — сто г.", prep("Мука — 100 г.", off("units")))
    }

    @Test fun abbrevOffKeepsSingleDot() {
        assertEquals("яблыкі, грушы і г. д.", prep("Яблыкі, грушы і г. д.", off("abbrev")))
        assertEquals("грушы і інш. потым", prep("Грушы і інш. Потым", off("abbrev")))
        assertEquals("музей на вул. леніна, пяць.", prep("Музей на вул. Леніна, 5.", off("abbrev")))
        // точка сокращения, которое не развернулось, — одна
        for (t in listOf("Вада — 5 л. Мука — 100 г. Цукар — 2 шт.", "Кошт 5 тыс. руб. Тэлефон ёсць.", "Аўтапрабег 1,2 тыс. км.",
                "Падзея адбылася ў 2024 г. Цяпер там музей.", "Пачатак 1990-х гг.", "Курган III ст. н. э. Потым"))
            for (k in listOf("units", "currency", "abbrev", "years", "numbers", "roman"))
                assertFalse("$k: $t", ".." in prep(t, off(k)))
    }

    @Test fun numberKeys() {
        assertEquals("тэмпература плюс", prep("тэмпература +11", off("numbers")))
        assertEquals("адзін з тры-х.", prep("Адзін з 3-х.", off("gen_suffix")))
        assertEquals("дзве тысячы дваццаць шэсць год", prep("2026 год", off("years")))
        assertEquals("тры мая", prep("3 мая", off("day_month")))
        assertEquals("трэцяга верасня", prep("3.09", off("day_month")))
        assertEquals("тры верасня", prep("3 верасня", off("day_month")))
    }

    @Test fun latinKeys() {
        assertEquals("усб", prep("USB", off("spell_lat")))
        assertEquals("галаксі с дваццаць чатыры", prep("Galaxy S24", off("letter_digit")))
        assertEquals("кніга", prep("Кніга FilmBell", off("latin")))
    }

    @Test fun homoglyphs() {
        // латинская буква-двойник вплотную к кириллице — кириллицей; i — белорусская «і»
        assertEquals("сок", prep("cок"))
        assertEquals("кок", prep("cок", off("homoglyphs")))
        assertEquals("мінск", prep("Мiнск"))
        assertEquals("нельга", prep("Hельга"))
        assertEquals("хельга", prep("Hельга", off("homoglyphs")))
        assertEquals("стоп таксі", prep("Cтоп TаксI"))
        // кусок не из одних двойников и отдельное латинское слово — латиница, как было
        assertEquals(prep("файлmicrosoft", off("homoglyphs")), prep("файлmicrosoft"))
        assertEquals("кул", prep("cool"))
    }

    @Test fun allKeysOff() {
        val none = Rules.KEYS.fold(Rules()) { r, k -> r.with(k, false) }
        val plusNotStress = Regex("\\+(?![аеёіоуыэюя])")
        for (t in samples + listOf("Прыв'ет! Ура!!", "C++ і +А", "аб’ява", "+5 і −5", "Што!?", "XX стагоддзе, USB, 5G")) {
            val out = prep(t, none)
            assertTrue("$t → $out", out.all { it in cis })
            assertFalse("$t → $out", '!' in out || BelNormalizer.APOSTROPHE.containsMatchIn(out))
            assertFalse("$t → $out", plusNotStress.containsMatchIn(out))
        }
    }
}
