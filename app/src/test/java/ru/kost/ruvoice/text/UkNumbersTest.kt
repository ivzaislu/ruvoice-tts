// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.TestData

/** Числа, даты, время, единицы и валюты по-украински (без морфологии; род и падеж по существительному — [UkCasesTest]).
 * Текст идёт так, как на телефоне: [Splitter.sentences], потом [UkLayer.prepare] с символами пака. Апостроф доходит до
 * модели «ъ»: «п'ять» → «пъять». Формы — по VESUM и Правопису 2019, не проверено носителем. */
class UkNumbersTest {
    private val cis = TestData.cisAllowed
    private fun prep(t: String, r: Rules = Rules()) = UkLayer.prepare(t, cis, r)
    private fun path(t: String, r: Rules = Rules()) = Splitter.sentences(t, r, layer = UkLayer).joinToString(" | ") { prep(it, r) }
    /** Символы пака плюс цифры: недочитанная цифра доходит до вывода, проверка «цифр нет» может упасть. */
    private val cisDigits = cis + "0123456789"
    private fun pathD(t: String) = Splitter.sentences(t, layer = UkLayer).joinToString(" | ") { UkLayer.prepare(it, cisDigits, Rules()) }

    @Test fun cardinalCases() {
        // склоняется каждое слово (VESUM numr); основная форма — первая в Правописе 2019 («п'ятьма», «сьома», «вісьма»)
        assertEquals("однієї тисячі двохсот п'ятдесяти", UkNumbers.cardinal(1250, Case.GEN))
        assertEquals("двадцятьма п'ятьма", UkNumbers.cardinal(25, Case.INS))
        assertEquals("двом", UkNumbers.cardinal(2, Case.DAT, Gender.F))
        assertEquals("двадцять одну", UkNumbers.cardinal(21, Case.ACC, Gender.F))
        assertEquals("трьох мільйонів", UkNumbers.cardinal(3_000_000, Case.GEN))
        assertEquals("одним мільйоном", UkNumbers.cardinal(1_000_000, Case.INS))
        assertEquals("двомастами сорока", UkNumbers.cardinal(240, Case.INS))
        assertEquals("п'яти тисячах", UkNumbers.cardinal(5000, Case.PRE))
        assertEquals("сьома", UkNumbers.cardinal(7, Case.INS))
        assertEquals("вісьма", UkNumbers.cardinal(8, Case.INS))
        assertEquals("однієї", UkNumbers.cardinal(1, Case.GEN, Gender.F))
        assertEquals("одному", UkNumbers.cardinal(1, Case.PRE))
        assertEquals("одне", UkNumbers.cardinal(1, Case.NOM, Gender.N))
        assertEquals("дві тисячі двадцять дві", UkNumbers.cardinal(2022, Case.NOM, Gender.F))
        assertEquals("нулем", UkNumbers.cardinal(0, Case.INS))
    }

    @Test fun ordinalForms() {
        assertEquals("третя", UkNumbers.ordinal(3, Case.NOM, Gender.F))
        assertEquals("третього", UkNumbers.ordinal(3, Case.GEN))
        assertEquals("третім", UkNumbers.ordinal(3, Case.INS))
        assertEquals("другій", UkNumbers.ordinal(2, Case.PRE, Gender.F))
        assertEquals("сорокове", UkNumbers.ordinal(40, Case.NOM, Gender.N))
        assertEquals("двадцять п'ятих", UkNumbers.ordinal(25, Case.GEN, plural = true))
        assertEquals("соту", UkNumbers.ordinal(100, Case.ACC, Gender.F))
        assertEquals("дві тисячі двадцять шостий", UkNumbers.ordinal(2026))
        assertEquals("двадцятий", UkNumbers.ordinal(20))
    }

    @Test fun ordinalRoundThousandsAndHundreds() {
        // круглые — одним словом, основа в родительном (Правопис 2019: «двохсотий», «двохтисячний»); годы без «одна»
        assertEquals("тисячний", UkNumbers.ordinal(1000))
        assertEquals("двохтисячний", UkNumbers.ordinal(2000))
        assertEquals("двохтисячного", UkNumbers.ordinal(2000, Case.GEN))
        assertEquals("тисяча дев'ятисотий", UkNumbers.ordinal(1900))
        assertEquals("дві тисячі сотий", UkNumbers.ordinal(2100))
        assertEquals("тисяча дев'ятсот дев'яносто восьмий", UkNumbers.ordinal(1998))
        assertEquals("двадцятип'ятитисячний", UkNumbers.ordinal(25_000))
        assertEquals("трьохсоттисячний", UkNumbers.ordinal(300_000))
        assertEquals("один мільйон п'ятий", UkNumbers.ordinal(1_000_005))
        assertEquals("у двохтисячному році", prep("у 2000 році"))
        assertEquals("тисяча девъятсот девъяносто восьмого року", prep("1998 року"))
        assertEquals("у десятитисячному році.", prep("У 10000-му році."))
        assertEquals("у стотисячному році", prep("у 100000-му році"))
        assertEquals("у мільйонному році", prep("у 1000000-му році"))
        assertEquals("до десятитисячного року", prep("до 10000-го року"))
    }

    @Test fun bigNumbersAndDigitGroups() {
        // с миллиарда — по цифрам, без падения и без цифр в выводе
        val big = pathD("10 000 000 000 000")
        assertTrue(big, big.none { it.isDigit() } && big.startsWith("один нуль нуль"))
        assertEquals("вісім нуль чотири чотири один два три чотири пъять шість сім", prep("80441234567", Rules().with("phones", false)))
        assertEquals("один мільйон двісті тридцять чотири тисячі пъятсот шістдесят сім гривень", prep("1 234 567 гривень"))
        // склейка только группами по три
        assertEquals("один два три", prep("1 2 3"))
        assertEquals("чотири тисячі двісті сімдесят шість одна тисяча двісті тридцять чотири", prep("4276 1234"))
        val card = pathD("4276 1234 5678 9012")
        assertTrue(card, "мільйон" !in card && card.none { it.isDigit() })
        assertEquals("три мільйони двісті тисяч", prep("3 200 000"))
        assertEquals("нуль нуль сім", prep("007"))
        // цифры длиннее Long в разделе, диапазоне и степени — не роняют запрос (нашёл фаззинг)
        for (t in listOf("12.05.20267344596879708368", "з 99999999999999999999 до 5 км", "10¹²³⁴⁵⁶⁷⁸⁹⁰¹²³⁴⁵⁶⁷⁸⁹⁰"))
            assertTrue(t, pathD(t).none { it.isDigit() })
        // длинные — по цифрам, но знак и единица не пропадают
        assertTrue(pathD("2³⁴⁵⁶⁷⁸⁹⁰¹²³⁴⁵⁶⁷⁸⁹⁰¹²³").startsWith("два в степені три чотири пъять"))
        assertTrue(pathD("1234567890123456789012345/2").endsWith("пъять на два"))
        assertTrue(pathD("12345678901234567890 кг").endsWith("нуль кілограмів"))
    }

    @Test fun phones() {
        assertEquals("плюс триста вісімдесят, сорок чотири, сто двадцять три, сорок пъять, шістдесят сім", prep("+380 44 123 45 67"))
        assertEquals("нуль сорок чотири, сто двадцять три, сорок пъять, шістдесят сім", prep("044 123-45-67"))
        // слитный номер — по группам, а не «нуль чотириста сорок один мільйон…»
        assertEquals("нуль сорок чотири, сто двадцять три, сорок пъять, шістдесят сім", prep("0441234567"))
        assertEquals("плюс триста вісімдесят, сорок чотири, сто двадцять три, сорок пъять, шістдесят сім", prep("+380441234567"))
        assertEquals("плюс тридцять вісім, нуль сорок чотири, сто двадцять три, сорок пъять, шістдесят сім", prep("+38 (044) 123-45-67"))
        assertEquals("десять тисяч-пъятнадцять тисяч", prep("10 000-15 000"))
        assertEquals("десять-пъятнадцять хвилин", prep("10-15 хвилин"))
    }

    @Test fun decimals() {
        assertEquals("три цілих чотирнадцять сотих", prep("3,14"))
        assertEquals("мінус нуль цілих двадцять пъять сотих", prep("−0,25"))
        assertEquals("нуль цілих пъять десятих", prep("0,5"))
        assertEquals("одна ціла пъять сотих кілограма", prep("1.05 кг"))
        assertEquals("одна ціла пъять десятих кілограма", prep("1,5 кг"))
        assertEquals("двадцять одна ціла сто двадцять пъять тисячних", prep("21,125"))
        assertEquals("до пъяти цілих двох десятих", prep("до 5,2"))
        assertEquals("одна тисяча цілих пъять десятих", prep("1 000,5"))
        assertEquals("дві цілих сімдесят пъять сотих відсотка", prep("2,75 %"))
    }

    @Test fun decimalsAndHalves() {
        assertEquals("півтора мільйона гривень", prep("1,5 млн грн"))
        assertEquals("півтори тисячі.", prep("1,5 тис."))
        assertEquals("дві з половиною тисячі.", prep("2,5 тис."))
        assertEquals("пъять з половиною мільйонів", prep("5,5 млн"))
        assertEquals("близько двох з половиною мільйонів", prep("близько 2,5 млн"))
        assertEquals("двадцять одна ціла пъять десятих мільйона", prep("21,5 млн"))
    }

    @Test fun datesFull() {
        // дата отдельно — именительный («двадцять перше жовтня»), в тексте — родительный («коли?»); год — родительный
        assertEquals("двадцять перше жовтня дві тисячі двадцять шостого року", prep("21.10.2026"))
        assertEquals("зустріч двадцять першого жовтня дві тисячі двадцять шостого року у києві", prep("Зустріч 21.10.2026 у Києві"))
        assertEquals("сьогодні двадцять перше жовтня.", prep("Сьогодні 21 жовтня."))
        assertEquals("народився пъятого березня тисяча девъятсот девъяностого року у львові", prep("Народився 5.03.1990 р. у Львові"))
        assertEquals("перше січня тисяча девъятсот девъяносто девъятого року", prep("01.01.99"))
        assertEquals("перше січня дві тисячі двадцятого року", prep("01.01.20"))
        assertEquals("дванадцяте травня дві тисячі двадцять шостого року", prep("2026-05-12"))
        assertEquals("зустріч дванадцятого травня", prep("Зустріч 12.05"))
        assertEquals("третього травня дві тисячі двадцять шостого року було свято", prep("3 травня 2026 р. було свято"))
    }

    @Test fun datePrepositionCase() {
        assertEquals("до першого вересня", prep("до 1 вересня"))
        assertEquals("з першого вересня", prep("з 1 вересня"))
        assertEquals("на перше вересня", prep("на 1 вересня"))
        assertEquals("перед першим вересня", prep("перед 1 вересня"))
        assertEquals("про третє травня", prep("про 3 травня"))
        assertEquals("на дванадцяте травня дві тисячі двадцять шостого року", prep("на 12.05.2026"))
        assertEquals("з першого по пъяте травня", prep("з 1 по 5 травня"))
        assertEquals("від пъятого до десятого травня", prep("від 5 до 10 травня"))
        assertEquals("свято пъятнадцятого — шістнадцятого травня", prep("Свято 15-16 травня"))
    }

    @Test fun fractionsAreNotDates() {
        assertEquals("1.5 кг", UkNumbers.dates("1.5 кг"))
        assertEquals("1/2 склянки", UkNumbers.dates("1/2 склянки"))
        assertEquals("24/7", UkNumbers.dates("24/7"))
        assertEquals("1.10.2", UkNumbers.dates("1.10.2"))
        assertEquals("5.05 %", UkNumbers.dates("5.05 %"))
        assertEquals("одна друга склянки", prep("1/2 склянки"))
        assertEquals("двадцять чотири на сім", prep("24/7"))
        assertEquals("один крапка десять крапка два", prep("1.10.2"))
        assertEquals("пъять цілих пъять сотих відсотка", prep("5.05 %"))
        assertEquals("дванадцяте травня дві тисячі двадцять шостого року", prep("12/05/2026"))
    }

    @Test fun times() {
        assertEquals("двадцять одна година тридцять хвилин", prep("21:30"))
        assertEquals("девъять годин рівно", prep("9:00"))
        // «о» — порядковое в местном ж. р., минуты количественным (так говорят о часах); не проверено носителем
        assertEquals("о десятій тридцять", prep("о 10:30"))
        assertEquals("о сьомій нуль пъять", prep("о 7:05"))
        assertEquals("о девъятій годині", prep("о 9:00"))
        assertEquals("об одинадцятій годині", prep("об 11:00"))
        // «з … до …» — порядковое в родительном
        assertEquals("з восьмої години до двадцятої тридцяти", prep("з 8:00 до 20:30"))
        assertEquals("з девъятої години по вісімнадцяту годину", prep("з 9:00 по 18:00"))
        assertEquals("перед девъятою годиною", prep("перед 9:00"))
        assertEquals("двадцять одна година тридцять хвилин пъятнадцять секунд", prep("21:30:15"))
        // «24:00» — конец суток, только с нулём минут; двоеточие не доходит до модели
        assertEquals("з восьмої години до двадцять четвертої години", prep("з 8:00 до 24:00"))
        assertEquals("працює до двадцять четвертої години.", path("Працює до 24:00."))
        assertEquals("двадцять чотири години рівно", prep("24:00"))
        // «у/в» перед временем — как «о»
        assertEquals("у сьомій тридцять", prep("у 7.30"))
        assertEquals("в двадцять першій годині", prep("в 21:00"))
        // не время — счёт: тире вместо двоеточия
        assertEquals("матч завершився з рахунком два — один.", path("Матч завершився з рахунком 2:1."))
        for (t in listOf("24:30", "25:00", "2:1")) assertFalse(t, pathD(t).contains(':'))
    }

    @Test fun timeWithDot() {
        assertEquals("початок о девъятнадцятій годині.", prep("Початок о 19.00."))
        assertEquals("о десятій нуль пъять", prep("о 10.05"))
        assertEquals("з девъятої години до вісімнадцятої години", prep("з 9.00 до 18.00"))
        assertEquals("з пъятнадцятої тридцяти по шістнадцяту тридцять", prep("з 15.30 по 16.30"))
        // «з/до…» с минутами 01–12 — дата; без предлога — дата и дробь
        assertEquals("з дванадцятого травня по пъятнадцяте травня", prep("з 12.05 по 15.05"))
        assertEquals("початок десятого травня", prep("Початок 10.05"))
    }

    @Test fun decimalAfterPrepositionIsNotTime() {
        // дробь со словом-единицей — не время; «по» с числом — цена; единица — целым словом
        assertEquals("ціна зросла у одну цілу двадцять пъять сотих раза.", prep("Ціна зросла у 1.25 раза."))
        assertEquals("близько однієї цілої тридцяти сотих мільйона людей.", prep("Близько 1.30 мільйона людей."))
        assertEquals("висота близько однієї цілої сорока пъяти сотих метра.", prep("Висота близько 1.45 метра."))
        assertEquals("яблука по дві цілих пъятдесят сотих за кілограм.", prep("Яблука по 2.50 за кілограм."))
        assertEquals("зустріч о вісімнадцятій тридцять разом з батьками.", prep("Зустріч о 18.30 разом з батьками."))
        assertEquals("о восьмій тридцять метро вже працює.", prep("О 8.30 метро вже працює."))
        assertEquals("о сьомій пъятнадцять тонкий лід тріснув.", prep("О 7.15 тонкий лід тріснув."))
    }

    @Test fun units() {
        assertEquals("три кілометри", prep("3 км"))
        assertEquals("один кілометр", prep("1 км"))
        assertEquals("пъять кілометрів", prep("5 км"))
        assertEquals("пъять кілограмів", prep("5 кг"))
        assertEquals("дві тонни", prep("2 т"))
        assertEquals("пъять грамів солі", prep("5 г солі"))
        assertEquals("пъять відсотків", prep("5 %"))
        assertEquals("дві тисячі кілометрів", prep("2 000 км"))
        assertEquals("шістнадцять гігабайтів", prep("16 ГБ"))
        assertEquals("шістнадцять гігабайтів", prep("16 GB"))
        assertEquals("двісті двадцять вольтів", prep("220 В"))
        assertEquals("пъять амперів", prep("5 А"))
        assertEquals("пъять метрів за секунду", prep("5 м/с"))
        assertEquals("шістдесят кілометрів за годину", prep("60 км/год"))
        assertEquals("дві години тридцять хвилин", prep("2 год 30 хв"))
        assertEquals("один квадратний метр", prep("1 м²"))
        assertEquals("три квадратні кілометри", prep("3 км²"))
        assertEquals("пъять кубічних сантиметрів", prep("5 см³"))
        assertEquals("одна ціла пъять десятих квадратного метра", prep("1,5 м²"))
        assertEquals("сто кіловат-годин", prep("100 кВт·год"))
        assertEquals("сто кіловат-годин", prep("100 kWh"))
        assertEquals("сто двадцять міліметрів ртутного стовпчика.", prep("120 мм рт. ст."))
        assertEquals("пъять кінських сил.", prep("5 к. с."))
        assertEquals("десять тисяч", prep("10k"))
        assertEquals("пъять тисяч гривень", prep("5 тис. грн"))
    }

    @Test fun unitOnlyAsSeparateToken() {
        // единица и сокращение — с границей слова с обеих сторон: слова на «руб», «тон», «метр», «раз», «грам» не трогаем
        assertEquals("рубець тонкий метро разом грамота капуста", prep("рубець тонкий метро разом грамота капуста"))
        assertEquals("три томи", prep("3 томи"))
        assertEquals("пъять грамот", prep("5 грамот"))
        assertEquals("дві тисячі двадцять шостий рік", prep("2026 рік"))
        assertEquals("сто років", prep("100 років"))
        assertEquals("у трьохсотому році", prep("у 300 році"))
        // «т», «м», «с», «р» перед номером или названием — не единица и не год: сокращения-существительные (UkRulesTest)
        assertEquals(listOf("том два", "місто київ", "річка дніпро", "сторінка сорок пъять", "стаття пъятнадцять", "село яблунівка"),
            listOf("т. 2", "м. Київ", "р. Дніпро", "с. 45", "ст. 15", "с. Яблунівка").map { prep(it) })
        // три цифры и «рік» — количество, не год
        assertEquals("їй виповнився сто один рік.", path("Їй виповнився 101 рік."))
        // не на 1 — год: количество с «рік» в однине бывает только при числе на 1
        assertEquals("девъятсот вісімдесят восьмий рік — хрещення русі.", path("988 рік — хрещення Русі."))
        assertEquals("минув сто двадцять один рік.", path("Минув 121 рік."))
        assertEquals("сто одинадцятий рік", prep("111 рік"))
    }

    @Test fun degreesAndAngles() {
        assertEquals("двадцять градусів цельсія", prep("20 °C"))
        assertEquals("пъять градусів цельсія", prep("5°С"))
        assertEquals("мінус двадцять градусів цельсія", prep("-20°С"))
        assertEquals("мінус пъять градусів цельсія", prep("−5 °C"))
        assertEquals("плюс один градус цельсія", prep("+1 °C"))
        assertEquals("двадцять градусів", prep("20°"))
        assertEquals("плюс двадцять один градус", prep("+21°"))
        assertEquals("сто градусів за фаренгейтом", prep("100 °F"))
        assertEquals("тридцять шість цілих шість десятих градуса", prep("36,6°"))
        assertEquals("пъятдесят пъять градусів сорок пъять хвилин", prep("55°45′"))
        assertEquals("сорок два градуси тридцять хвилин", prep("42°30'"))
        assertEquals("двадцять сім дюймів", prep("27″"))
    }

    @Test fun unitCaseByPreposition() {
        assertEquals("до пъяти кілометрів", prep("до 5 км"))
        assertEquals("від пъяти до семи кілометрів", prep("від 5 до 7 км"))
        assertEquals("у пъяти кілометрах від міста", prep("у 5 км від міста"))
        assertEquals("в пъяти кілометрах від міста", prep("в 5 км від міста"))
        assertEquals("при двадцяти градусах цельсія", prep("при 20 °C"))
        assertEquals("при мінус двадцяти градусах", prep("при -20°"))
        assertEquals("близько однієї тонни", prep("близько 1 т"))
        assertEquals("між двома метрами", prep("між 2 м"))
        assertEquals("понад пъять кілометрів", prep("понад 5 км"))
        // знахідний после «на/за/через/понад/про»: «тисячу», «двадцять одну тисячу», «одну годину»
        assertEquals("понад тисячу учасників", prep("понад 1000 учасників"))
        assertEquals("за тисячу гривень", prep("за 1000 гривень"))
        assertEquals("на двадцять одну тисячу гривень", prep("на 21 000 гривень"))
        assertEquals("через тисячу років", prep("через 1000 років"))
        assertEquals("понад тисячу гривень", prep("понад 1000 грн"))
        assertEquals("за одну годину", prep("за 1 год"))
        assertEquals("ціна зросла на тисячу гривень", prep("Ціна зросла на 1000 грн"))
        // «з N до …» — диапазон: оба родовим, и «з» тоже (второе число уже прочли единицы)
        assertEquals("з десяти до пъятнадцяти кілограмів", prep("з 10 до 15 кг"))
        assertEquals("протягом двох-трьох тижнів", prep("протягом 2-3 тижнів"))
        // «о 5 год.» — час доби
        assertEquals("прокинувся о пъятій годині ранку.", prep("Прокинувся о 5 год. ранку."))
        assertEquals("урок триває сорок пъять хвилин.", prep("Урок триває 45 хв."))
        // время с «у» — знахідний
        assertEquals("у дві години", prep("у 2 год"))
    }

    @Test fun currencies() {
        assertEquals("сто гривень", prep("100 грн"))
        assertEquals("одна гривня двадцять копійок", prep("1,20 UAH"))
        assertEquals("пъять доларів", prep("$5"))
        assertEquals("пъять доларів", prep("5 USD"))
        assertEquals("пъятдесят євро", prep("€ 50"))
        assertEquals("сто гривень", prep("100 ₴"))
        assertEquals("сто білоруських рублів", prep("100 BYN"))
        assertEquals("два білоруські рублі", prep("2 BYN"))
        assertEquals("пъять фунтів", prep("£5"))
        assertEquals("сто мільйонів євро", prep("€100 млн"))
        assertEquals("ціна в гривнях", prep("ціна в грн"))
        assertEquals("пъять тисяч рублів.", prep("5 тис. руб."))
    }

    @Test fun moneyWithCents() {
        assertEquals("два рублі одна копійка.", prep("2,01 руб."))
        assertEquals("пъять рублів дві копійки", prep("5,02 ₽"))
        assertEquals("одна тисяча двісті сорок девъять гривень девъяносто копійок", prep("1 249,90 грн"))
        assertEquals("один долар пъятдесят центів", prep("$1.50"))
        assertEquals("три долари один цент", prep("3,01 $"))
        // «євро» — середній рід; не проверено носителем
        assertEquals("двадцять одне євро двадцять два центи", prep("21,22 €"))
        assertEquals("одне євро", prep("1 €"))
        assertEquals("один фунт пъятдесят пенсів", prep("£1.50"))
        assertEquals("сто гривень", prep("100,00 UAH"))
        assertEquals("дві цілих пъять десятих євро", prep("2,5 €"))
    }

    @Test fun currencyBeforeMultiplierWord() {
        assertEquals("втрати сімдесят мільярдів доларів.", prep("Втрати $70 мільярдів."))
        assertEquals("три трильйони доларів", prep("$3 трлн"))
        assertEquals("близько сімнадцять-вісімнадцять мільярдів доларів", prep("Близько $17-18 млрд"))
        assertTrue(prep("US$5").endsWith("пъять доларів"))
        assertEquals("ціна пъять доларів — десять штук.", prep("Ціна $5 — 10 штук."))
        assertEquals("квитки по пъять доларів-десять доларів.", prep("Квитки по $5-$10."))
        assertEquals("один долар, два долари", prep("$1,$2"))
    }

    @Test fun years() {
        // «р.» после числа — год только 1000–2199 или три цифры после «у/в»; падеж по предлогу
        assertEquals("у тисяча вісімсот дванадцятому році.", path("у 1812 р."))
        assertEquals("у тисяча вісімсот дванадцятому році наполеон напав.", path("У 1812 р. Наполеон напав."))
        // точка сокращения в конце фразы — точка предложения
        assertEquals("тисяча девъятсот девъяносто девъятий рік.", prep("1999 р."))
        assertEquals("з тисяча девъятсот девъяностого року.", prep("з 1990 р."))
        assertEquals("в девъятсот вісімдесят восьмому році.", prep("в 988 р."))
        assertEquals("з тисяча девъятсот сорок першого по тисяча девъятсот сорок пъятий рік.", prep("З 1941 по 1945 р."))
        assertEquals("з тисяча девъятсот сорок першого до тисяча девъятсот сорок пъятого року.", prep("з 1941 до 1945 р."))
        assertEquals("від тисяча девъятсот сорок першого до тисяча девъятсот сорок пъятого року.", prep("від 1941 до 1945 рр."))
        assertEquals("з однієї тисячі до двох тисяч гривень", prep("з 1000 до 2000 гривень"))
        assertEquals("у тисяча девъятсот сорок першому — тисяча девъятсот сорок пъятому роках місто", prep("у 1941–1945 рр. місто"))
        assertEquals("тисяча девъятсот сорок перший — тисяча девъятсот сорок пъятий роки", prep("1941-1945 роки"))
        assertEquals("одна тисяча—дві тисячі років", prep("1000–2000 років"))
        // «2026 рік» и «2026 року» — порядковым в падеже слова
        assertEquals("дві тисячі двадцять шостого року", prep("2026 року"))
        assertEquals("у дві тисячі двадцять шостому році", prep("у 2026 році"))
    }

    @Test fun decades() {
        // «1990-х» — полностью, как у белорусского («тисяча девʼятсот девʼяностих»), век не теряется
        assertEquals("у тисяча девъятсот девъяностих роках", prep("у 1990-х роках"))
        assertEquals("у девъяностих", prep("у 90-х"))
        assertEquals("девъяності роки", prep("90-ті роки"))
        assertEquals("у двохтисячних", prep("у 2000-х"))
        assertEquals("тисяча девъятсот девъяності", prep("1990-ті"))
        assertEquals("у тисяча девъятсот девъяностих роках.", prep("у 1990-х рр."))
        assertEquals("початок тисяча девъятсот девъяностих років.", prep("Початок 1990-х рр."))
        assertEquals("мода вісімдесятих років повернулася.", prep("Мода 80-х рр. повернулася."))
        assertEquals("тисяча девъятсот девъяностими", prep("1990-ми"))
    }

    @Test fun ordinalSuffixes() {
        assertEquals("пъятий клас", prep("5-й клас"))
        assertEquals("першого вересня", prep("1-го вересня"))
        assertEquals("у пъятому класі", prep("у 5-му класі"))
        assertEquals("пъята книга", prep("5-та книга"))
        assertEquals("пъяте місце", prep("5-те місце"))
        assertEquals("на пъяту", prep("на 5-ту"))
        assertEquals("у пъятих класах", prep("у 5-х класах"))
        assertEquals("пъятої", prep("5-ї"))
        assertEquals("пъятою", prep("5-ю"))
        assertEquals("другий", prep("2-й"))
        assertEquals("двадцять перший", prep("21-й"))
        assertEquals("третя", prep("3-тя"))
        assertEquals("третє", prep("3-тє"))
        assertEquals("сьома", prep("7-ма"))
        assertEquals("о сьомій", prep("о 7-й"))
        assertEquals("тисячна сторінка", prep("1000-на сторінка"))
        assertEquals("третє місце", prep("3-є місце"))
        assertEquals("він посів перше місце.", prep("Він посів 1-ше місце."))
        // буква класу заглавной — не нарощення «-а» («п'ята»)
        assertEquals("у пъятому-а класі тридцять учнів.", prep("У 5-А класі 30 учнів."))
    }

    @Test fun cardinalCaseSuffixes() {
        // «-ти», «-ми» (русская манера) — родительный, «-ма» не у порядкового — орудный, «-х» у 2–4 — «двох»
        assertEquals("до пъяти", prep("до 5-ти"))
        assertEquals("від пъяти до семи кілометрів", prep("від 5-ти до 7-ми кілометрів"))
        assertEquals("з двома", prep("з 2-ма"))
        assertEquals("трьома", prep("3-ма"))
        assertEquals("з пъятьма", prep("з 5-ма"))
        assertEquals("двох", prep("2-х"))
        assertEquals("один з трьох.", prep("Один з 3-х."))
        assertEquals("у двох кілометрах", prep("у 2-х кілометрах"))
    }

    @Test fun compoundsChaptersLists() {
        assertEquals("пъятирічний", prep("5-річний"))
        assertEquals("сторічний ювілей", prep("100-річний ювілей"))
        assertEquals("двадцятипъятирічний", prep("25-річний"))
        assertEquals("однокімнатна квартира", prep("1-кімнатна квартира"))
        assertEquals("дво-трикімнатна", prep("2–3-кімнатна"))
        assertEquals("десятиповерховий", prep("10-поверховий"))
        assertEquals("сорокарічний", prep("40-річний"))
        assertEquals("триповерховий", prep("3-поверховий"))
        assertEquals("глава пъята", prep("Глава 5"))
        assertEquals("у главі десятій", prep("у главі 10"))
        assertEquals("розділ третій", prep("Розділ 3"))
        assertEquals("частина друга", prep("Частина 2"))
        assertEquals("причини: перше, голод; друге, холод", prep("Причини: 1) голод; 2) холод"))
    }

    @Test fun romanCentury() {
        assertEquals("двадцяте століття", prep("XX століття"))
        assertEquals("література двадцятого століття", prep("Література XX століття"))
        assertEquals("у двадцятому столітті", prep("у XX столітті"))
        assertEquals("двадцять перше століття", prep("XXI століття"))
        assertEquals("девъятнадцяте століття", prep("ХІХ століття"))   // кириллицей
        assertEquals("до чотирнадцятого віку", prep("до XIV віку"))
        assertEquals("а у двадцятому столітті — садиба.", prep("а у XX ст. — садиба."))
        assertEquals("з двадцятого століття до наших днів", prep("з XX ст. до наших днів"))
        assertEquals("девъятнадцяте — двадцяте століття були", prep("XIX–XX ст. були"))
        // точка «ст.» в конце предложения остаётся; после одного предлога в начале фразы перед заглавной — середина
        assertEquals("у девъятнадцятому — двадцятому століттях.", prep("У XIX–XX ст."))
        assertEquals("у двадцятому столітті україна змінилася.", path("У XX ст. Україна змінилася."))
        assertEquals("це у двадцятому столітті. україна змінилася.", path("Це у XX ст. Україна змінилася."))
        assertEquals("і ст.", prep("І ст."))
        // без предлога I–IV — ступінь, не век; с «н. е.» — век
        assertEquals("орден слави третього ступеня.", prep("Орден Слави III ст."))
        assertEquals("диплом першого ступеня з математики", prep("Диплом I ст. з математики"))
        assertEquals("диплом першого — другого ступенів.", prep("Диплом I–II ст."))
        assertTrue(prep("У II ст. до н. е.").startsWith("у другому столітті"))
        // после слова — родовий: «курган третього століття», «події двадцятого століття»
        assertTrue(prep("Курган III ст. до н. е.").startsWith("курган третього століття"))
        assertEquals("події двадцятого століття відомі", prep("Події XX ст. відомі"))
        assertEquals("це пъяте століття.", prep("Це V ст."))
    }

    @Test fun romanAfterNameCases() {
        // мужские имена на -а/-я в називному — називний ч. р.; родовий остаётся родовим (ревью задачи 5, Important 1)
        assertEquals("імператор микола другий підписав маніфест.", path("Імператор Микола II підписав маніфест."))
        assertEquals("указ миколи другого", path("Указ Миколи II"))
        assertEquals("за миколи другого", path("за Миколи II"))
        assertEquals(listOf("сава перший", "ілля другий", "лука перший", "микита перший", "кузьма другий", "хома перший",
            "фома перший", "ярема другий"),
            listOf("Сава I", "Ілля II", "Лука I", "Микита I", "Кузьма II", "Хома I", "Фома I", "Ярема II").map { path(it) })
        // падеж и род имени — в порядковом (Important 2)
        assertEquals("з катериною другою", path("з Катериною II"))
        assertEquals("бачили катерину другу", path("Бачили Катерину II"))
        assertEquals("при катерині другій", path("При Катерині II"))
        assertEquals("з петром першим", path("з Петром I"))
        assertEquals("лист петру першому", path("Лист Петру I"))
        assertEquals("при петрові першому", path("при Петрові I"))
        assertEquals("при петрі першому", path("при Петрі I"))
        assertEquals("з миколою другим", path("з Миколою II"))
        assertEquals("лист миколі другому", path("Лист Миколі II"))
        assertEquals("бачили миколу другого", path("Бачили Миколу II"))
        // одна «I» после имени в давальному — римское, не латиница «ай»
        assertEquals("памъятник петру першому", path("Пам’ятник Петру I"))
        // слово главы с заглавной — его падеж, не окончание имени; «Том II» — називний
        assertEquals("до розділу другого", path("До Розділу II"))
        assertEquals("у главі третій", path("У Главі III"))
        assertEquals("том другий вийшов.", path("Том II вийшов."))
    }

    @Test fun romanAfterName() {
        // род: ж. р. только по списку имён и нарицательных, прочее на -а/-я/-и — родительный м. р.; не проверено носителем
        assertEquals("петро перший і катерина друга", prep("Петро I і Катерина II"))
        assertEquals("указ петра першого був жорстким.", prep("Указ Петра I був жорстким."))
        assertEquals("реформи олександра другого змінили країну.", prep("Реформи Олександра II змінили країну."))
        assertEquals("за миколи першого", prep("за Миколи I"))
        assertEquals("карл дванадцятий", prep("Карл XII"))
        assertEquals("частина третя", prep("Частина III"))
        assertEquals("фаза третя завершена.", prep("Фаза III завершена."))
        assertEquals("єлизавета друга", prep("Єлизавета II"))
        // жіноче ім'я в родовому — родовий ж. р.
        assertEquals("реформи катерини другої", prep("Реформи Катерини II"))
        assertEquals("за марії першої", prep("за Марії I"))
        assertEquals("петро перший заснував місто.", prep("Петро І заснував місто."))
        assertEquals("київ і львів.", prep("Київ І Львів."))
        // одна буква перед вторым инициалом — инициал; латинская «I.» в конце фразы — римское
        // инициалы — именами букв (spell_cyr), не римское
        assertEquals("автор петренко і пе прийшов.", path("Автор Петренко І. П. прийшов."))
        assertEquals("засновник міста — петро перший.", path("Засновник міста — Петро I."))
        assertEquals("день х настав.", prep("День Х настав."))
        // предлог в начале фразы — не имя, число читается порядковым по слову за ним («-ої» — родовий ж. р.)
        assertEquals("після другої світової війни.", prep("Після II світової війни."))
        // без таблицы — по окончанию прилагательного: «-ій» — місцевий ж. р., «-их» — множина
        assertEquals("у другій світовій війні", prep("У II світовій війні"))
        assertEquals("учасники третіх всеукраїнських зборів", prep("учасники III всеукраїнських зборів"))
        // одна буква после имени — только «I» (как у белорусского): «V», «X» — буквы («Спутнік V», «День Х»)
        assertFalse(prep("Вакцина Спутнік V допомогла.").contains("пъят"))
        assertFalse(prep("Карл V зрікся.").contains("пъят"))
        // римское вне имени и века: согласование со словом главы перед ним, иначе називний ч. р.
        assertEquals("том другий вийшов.", prep("Том II вийшов."))
        assertEquals("у розділі четвертому", prep("у розділі IV"))
        assertEquals("у главі другій", prep("у главі II"))
        // длинное слово с апострофами — без StackOverflowError, «II» после него — порядковое
        assertEquals("а".repeat(8001) + " другий", prep("А" + "'а".repeat(8000) + " II"))
    }

    @Test fun signsAndSections() {
        assertEquals("номер пъять", prep("№ 5"))
        assertEquals("номер пъять", prep("№5"))
        assertEquals("під номером сім", prep("під № 7"))
        assertEquals("у параграфі чотири і параграфі пъять", prep("у § 4 і § 5"))
        assertEquals("номери пъять, шість", prep("№№ 5, 6"))
        assertEquals("параграф три", prep("§ 3"))
        assertEquals("параграф чотири крапка два", prep("§ 4.2"))
        // после метки — номер, а не дата «четвертого грудня»
        assertTrue(prep("п. 4.12").endsWith("чотири крапка дванадцять"))
        assertEquals("два крапка десять крапка три", prep("2.10.3"))
        assertEquals("сто девъяносто два крапка сто шістдесят вісім крапка один крапка десять", prep("192.168.1.10"))
        assertEquals("два крапка нуль три крапка один", prep("2.03.1"))
    }

    @Test fun arithmetic() {
        assertEquals("два плюс три дорівнює пъять", prep("2 + 3 = 5"))
        assertEquals("пъять мінус три дорівнює два", prep("5 - 3 = 2"))
        assertEquals("два помножити на три", prep("2×3"))
        assertEquals("десять поділити на два", prep("10 ÷ 2"))
        assertTrue(prep("x = -5").endsWith("дорівнює мінус пъять"))
        assertTrue(prep("x > 5").endsWith("більше за пъять"))
        assertEquals("десять у квадраті", prep("10²"))
        assertEquals("два в кубі", prep("2³"))
        assertEquals("десять у мінус третьому степені", prep("10⁻³"))
        assertEquals("два в степені десять", prep("2^10"))
        assertEquals("пъять на десять метрів", prep("5х10 м"))
        assertEquals("корінь з двох", prep("√2"))
        assertEquals("у пропорції один до чотирьох", prep("у пропорції 1:4"))
        assertEquals("тиск сто двадцять на вісімдесят", prep("тиск 120/80"))
        assertEquals("десять-пъятнадцять", prep("10-15"))
    }

    @Test fun fractions() {
        // 1 — однина ж. р., інакше родовий множини («три четвертих», Правопис і шкільні граматики; «три четверті» теж норма)
        assertEquals("одна друга", prep("1/2"))
        assertEquals("три четвертих кілограма", prep("3/4 кг"))
        assertEquals("дві третіх", prep("2/3"))
        assertEquals("пъять шостих", prep("5/6"))
        assertEquals("одна третя частини", prep("1/3 частини"))
        assertEquals("сім других", prep("7/2"))
        assertEquals("тридцять сотих", prep("30/100"))
        assertEquals("два з половиною кілограми", prep("2 1/2 кг"))
        assertEquals("пъять з половиною кілограмів", prep("5 1/2 кг"))
        assertEquals("півтора кілограма", prep("1 1/2 кг"))
        assertEquals("півтори тонни", prep("1 1/2 т"))
        assertEquals("одна ціла одна третя", prep("1 1/3"))
        assertEquals("дві цілих три четвертих літра", prep("2 3/4 л"))
        assertEquals("половина кілограма", prep("½ кг"))
        assertEquals("два з половиною кілограми", prep("2½ кг"))
        assertEquals("три чверті склянки", prep("¾ склянки"))
        assertEquals("одна восьма", prep("⅛"))
        assertEquals("близько двох пъятих учасників", prep("близько 2/5 учасників"))
    }

    @Test fun spoons() {
        assertEquals("дві чайні ложки", path("2 ч. л."))
        assertEquals("дві чайні ложки цукру", path("2 ч. л. цукру"))
        assertEquals("одна столова ложка солі", path("1 ст. л. солі"))
        assertEquals("пъять столових ложок", prep("5 ст. л."))
        assertEquals("одна друга чайної ложки", prep("1/2 ч. л."))
    }

    @Test fun soCalled() {
        // «т. зв.» — по роду следующего слова; Splitter после «т.», «зв.» не режет
        assertEquals("так звана нова хвиля.", path("Т. зв. нова хвиля."))
        assertEquals("це так званий режим.", path("Це т. зв. режим."))
        assertEquals("так звані нові книги", path("т. зв. нові книги"))
        assertEquals("так зване поле", path("т. зв. поле"))
    }

    @Test fun nonAsciiDigits() {
        assertEquals("три кілометри", prep("٣ км"))
        assertEquals("дванадцять кілометрів", prep("１２ км"))
    }

    @Test fun throughPipeline() {
        // путь сервиса: куски [Pipeline.plan], потом слой (как Pipeline.accent до ударений): числа доходят словами, цифр нет
        val text = "Ціна 1,20 UAH. Зустріч 21.10.2026 о 10:30, у 1812 р. було 3 км і 20 °C; −0,25 та 1,5 млн грн."
        val out = ru.kost.ruvoice.Pipeline.plan(text, TestData.data(), 0, 0, rules = Rules(), layer = UkLayer, allowed = cis)
            .joinToString(" | ") { UkLayer.prepare(it.text, cisDigits, Rules()) }
        assertTrue(out, out.none { it.isDigit() })
        for (w in listOf("гривня", "копійок", "жовтня", "десятій", "році", "кілометри", "цельсія", "сотих", "мільйона"))
            assertTrue("$w: $out", w in out.replace("+", ""))
    }

    @Test fun keysOff() {
        // каждый шаг под своим ключом: выключенный оставляет цифры фильтру (цифр в символах пака нет)
        assertFalse(prep("21.10.2026", Rules().with("dates", false)).contains("жовтня"))
        assertEquals("три км", prep("3 км", Rules().with("units", false)))
        assertEquals("сто грн", prep("100 грн", Rules().with("currency", false)))
        assertFalse(prep("21:30", Rules().with("times", false)).contains("годин"))
        assertEquals("три четвертих", prep("3/4"))
        assertFalse(prep("3/4", Rules().with("fractions", false)).contains("четверт"))
    }
}
