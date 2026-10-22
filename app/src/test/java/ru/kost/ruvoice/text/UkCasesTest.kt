// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.kost.ruvoice.TestData
import java.io.File

/** Род и падеж числа (правило cases). Морфология — выборка VESUM по словам этих тестов (morph_uk_test.bin, собран
 * tools/vesum_morph.py --words), как morph.bin пака cis_uk. Текст идёт через [Splitter.sentences] и [UkLayer.prepare]. */
class UkCasesTest {
    private val cis = TestData.cisAllowed
    private fun prep(t: String, rules: Rules = Rules()) = Splitter.sentences(t, rules).joinToString(" | ") { UkLayer.prepare(it, cis, rules) }

    @Before fun open() { UkNumbers.morph = Morph.open(File(TestData.root(), "app/src/test/resources/morph_uk_test.bin")) }
    @After fun close() { UkNumbers.morph = null }

    @Test fun genderAfterOne() {
        assertEquals("одна книга", prep("1 книга"))
        assertEquals("двадцять одна книга", prep("21 книга"))
        assertEquals("одна гривня", prep("1 гривня"))
        assertEquals("один рік", prep("1 рік"))
        assertEquals("одне вікно", prep("1 вікно"))
        assertEquals("один день", prep("1 день"))
        assertEquals("одна ніч", prep("1 ніч"))
        // знахідний: «одну книгу», істота ч. р. — «одного брата»
        assertEquals("прочитав одну книгу", prep("прочитав 1 книгу"))
        assertEquals("побачив одного брата", prep("побачив 1 брата"))
    }

    @Test fun pluralAfterTwoToFour() {
        assertEquals("дві книги", prep("2 книги"))
        assertEquals("двадцять дві книги", prep("22 книги"))
        assertEquals("два вікна", prep("2 вікна"))
        assertEquals("три дні", prep("3 дні"))
        assertEquals("дві години", prep("2 години"))
        assertEquals("дві студентки", prep("2 студентки"))
        assertEquals("двадцять один студент", prep("21 студент"))
        // прикметник між — рід за іменником; знахідний істот 2–4 — як родовий
        assertEquals("дві нові книги", prep("2 нові книги"))
        assertEquals("побачив двох братів", prep("побачив 2 братів"))
    }

    @Test fun obliqueByNoun() {
        assertEquals("з пъятьма друзями", prep("з 5 друзями"))
        assertEquals("з трьома друзями", prep("з 3 друзями"))
        assertEquals("у двох книгах", prep("у 2 книгах"))
        assertEquals("у двох селах", prep("у 2 селах"))
        assertEquals("з пъятьма новими книгами", prep("з 5 новими книгами"))
        assertEquals("з пъятьма сімъями", prep("з 5 сім'ями"))
        assertEquals("пъять книг", prep("5 книг"))
        assertEquals("сто учасників", prep("100 учасників"))
    }

    @Test fun byPreposition() {
        assertEquals("близько трьох годин", prep("близько 3 годин"))
        assertEquals("до пъяти", prep("до 5"))
        assertEquals("після двох годин", prep("після 2 годин"))
        assertEquals("при одній заяві", prep("при 1 заяві"))
        assertEquals("без однієї хвилини", prep("без 1 хвилини"))
        assertEquals("між пъятьма і десятьма", prep("між 5 і 10"))
        assertEquals("від пъяти до десяти", prep("від 5 до 10"))
        assertEquals("з пъяти по десять", prep("з 5 по 10"))
        assertEquals("до двох-трьох днів", prep("до 2-3 днів"))
        // «понад» — знахідний: «понад сто учасників»
        assertEquals("понад сто учасників", prep("понад 100 учасників"))
    }

    @Test fun ordinalBySingular() {
        // не 1 перед іменником у непрямому відмінку однини — номер
        assertEquals("на другому поверсі", prep("на 2 поверсі"))
        assertEquals("о сьомій годині", prep("о 7 годині"))
        assertEquals("учень другого класу", prep("учень 2 класу"))
        assertEquals("у пъятому класі", prep("у 5 класі"))
        assertEquals("на пъятій сторінці", prep("на 5-й сторінці"))
        // місцевий без прийменника не буває: «їде» — дієслово
        assertEquals("автобус двадцять один їде", prep("автобус 21 їде"))
    }

    @Test fun fromWithGenitive() {
        // «з/із/зі» без орудного за числом — родовий, перед «ст» — «зі»; орудний — за іменником; роки — як були
        assertEquals("девъяносто два бали зі ста.", prep("92 бали з 100."))
        assertEquals("девъяносто два бали зі ста.", prep("92 бали зі 100."))
        assertEquals("з двох боків", prep("з 2 боків"))
        assertEquals("із трьох сторін", prep("із 3 сторін"))
        assertEquals("з дві тисячі десятого по дві тисячі пъятнадцятий рік", prep("з 2010 по 2015 рік"))
        assertEquals("один мільйон двісті тридцять чотири тисячі пъятсот шістдесят сім гривень", prep("1 234 567 гривень"))
        assertEquals("близько одного мільйона двохсот тридцяти чотирьох тисяч пъятисот шістдесяти семи гривень",
            prep("близько 1 234 567 гривень"))
    }

    @Test fun accusativeAfterPreposition() {
        // «на/за/через/понад/про» — знахідний: тысяча без «одну», «одну» у ж. р.; «на» с місцевим — номер, как было
        assertEquals("понад тисячу учасників", prep("понад 1000 учасників"))
        assertEquals("за тисячу гривень", prep("за 1000 гривень"))
        assertEquals("на двадцять одну тисячу гривень", prep("на 21 000 гривень"))
        assertEquals("за одну годину", prep("за 1 годину"))
        assertEquals("за одну ніч", prep("за 1 ніч"))
        assertEquals("на два тижні", prep("на 2 тижні"))
        assertEquals("через дві години", prep("через 2 години"))
        assertEquals("на другому поверсі", prep("на 2 поверсі"))
    }

    @Test fun twoGenderNouns() {
        // «сім'я» в VESUM — ж. р. и с. р. («насіння»): берём ж. р.
        assertEquals("одна сімъя", prep("1 сім'я"))
        assertEquals("допомогу отримали дві сімъї.", prep("Допомогу отримали 2 сім'ї."))
        assertEquals("для однієї сімъї", prep("для 1 сім'ї"))
        assertEquals("двадцять одна сімъя", prep("21 сім'я"))
        // только множинні — «одні»
        assertEquals("одні двері", prep("1 двері"))
    }

    @Test fun mixedFractionGenderByNoun() {
        assertEquals("півтори години", prep("1 1/2 години"))
        assertEquals("дві з половиною години", prep("2 1/2 години"))
    }

    @Test fun romanAgreesWithNoun() {
        assertEquals("після другої світової війни.", prep("Після II світової війни."))
        assertEquals("у другій світовій війні", prep("У II світовій війні"))
    }

    @Test fun notCounted() {
        assertEquals("пункт два правил", prep("пункт 2 правил"))
        assertEquals("до тисяча девъятсот девъяностого року", prep("до 1990 року"))
        assertEquals("більше за сто", prep("більше за 100"))
        assertEquals("один книга", prep("1 книга", Rules().with("cases", false)))
    }
}
