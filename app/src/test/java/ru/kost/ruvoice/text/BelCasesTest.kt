// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.kost.ruvoice.TestData
import java.io.File

/** Род и падеж числа (правило cases). Морфология — выборка GrammarDB по словам этих тестов (morph_be_test.bin, собран
 * tools/gdb_morph.py --words), как morph.bin пака cis_be. */
class BelCasesTest {
    private val cis = TestData.cisAllowed
    private fun prep(t: String, rules: Rules = Rules()) = BelNormalizer.prepare(t, cis, rules)

    @Before fun open() { BelNumbers.morph = Morph.open(File(TestData.root(), "app/src/test/resources/morph_be_test.bin")) }
    @After fun close() { BelNumbers.morph = null }

    @Test fun genderAfterOne() {
        assertEquals("адна кніга", prep("1 кніга"))
        assertEquals("дваццаць адна кніга", prep("21 кніга"))
        assertEquals("адно акно", prep("1 акно"))
        assertEquals("адзін дзень", prep("1 дзень"))
        assertEquals("адна ноч", prep("1 ноч"))
        // винительный: «адну кнігу», одушевлённый м. р. — «аднаго сябра»
        assertEquals("прачытаў адну кнігу", prep("прачытаў 1 кнігу"))
        assertEquals("убачыў аднаго брата", prep("убачыў 1 брата"))
    }

    @Test fun pluralAfterTwoToFour() {
        assertEquals("дзве кнігі", prep("2 кнігі"))
        assertEquals("дваццаць дзве кнігі", prep("22 кнігі"))
        assertEquals("два вядры", prep("2 вядры"))
        assertEquals("тры дні", prep("3 дні"))
        assertEquals("дзве гадзіны", prep("2 гадзіны"))
        // прилагательное между — род по существительному
        assertEquals("дзве новыя кнігі", prep("2 новыя кнігі"))
    }

    @Test fun obliqueByNoun() {
        assertEquals("з пяццю сябрамі", prep("з 5 сябрамі"))
        assertEquals("у дзвюх кнігах", prep("у 2 кнігах"))
        assertEquals("у адным доме", prep("у 1 доме"))
        assertEquals("з пяццю новымі кнігамі", prep("з 5 новымі кнігамі"))
        assertEquals("пяць кніг", prep("5 кніг"))
        assertEquals("сто чалавек", prep("100 чалавек"))
    }

    @Test fun byPreposition() {
        assertEquals("каля трох гадзін", prep("каля 3 гадзін"))
        assertEquals("да пяці", prep("да 5"))
        assertEquals("пасля дзвюх гадзін", prep("пасля 2 гадзін"))
        assertEquals("аб адной заяве", prep("аб 1 заяве"))
        assertEquals("без адной хвіліны", prep("без 1 хвіліны"))
        assertEquals("паміж пяццю і дзесяццю", prep("паміж 5 і 10"))
        assertEquals("ад пяці да дзесяці", prep("ад 5 да 10"))
        assertEquals("з пяці па дзесяць", prep("з 5 па 10"))
        assertEquals("да двух-трох дзён", prep("да 2-3 дзён"))
    }

    @Test fun ordinalBySingular() {
        // не 1 перед существительным в косвенном адз. — номер
        assertEquals("на другім паверсе", prep("на 2 паверсе"))
        assertEquals("а сёмай гадзіне", prep("а 7 гадзіне"))
        assertEquals("вучань другога класа", prep("вучань 2 класа"))
        assertEquals("у пятым класе", prep("у 5 класе"))
        // месны без предлога не бывает: «едзе» — глагол
        assertEquals("аўтобус дваццаць адзін едзе", prep("аўтобус 21 едзе"))
        // назоўны — счёт, как написали («25 год» — разговорное)
        assertEquals("дваццаць пяць год", prep("25 год"))
    }

    @Test fun notCounted() {
        // номер, год, дробь, время — как было
        assertEquals("пункт два правілаў", prep("пункт 2 правілаў"))
        assertEquals("да тысяча дзевяцьсот дзевяностага года", prep("да 1990 года"))
        assertEquals("з тысяча дзевяцьсот сорак першага па тысяча дзевяцьсот сорак пяты год.", prep("з 1941 па 1945 г."))
        assertEquals("больш за сто", prep("больш за 100"))
        // без правила — именительный
        assertEquals("адзін кніга", prep("1 кніга", Rules().with("cases", false)))
    }
}
