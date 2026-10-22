// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.Pipeline
import ru.kost.ruvoice.TestData
import java.io.File

/** Ключи «Правил» у украинского слоя и правила чтения сверх чисел: сокращения, латиница, омоглифы, аббревиатуры, коды,
 * ссылки, эмодзи. Текст идёт, как на телефоне: [Splitter.sentences], потом [UkLayer.prepare] с символами пака. */
class UkRulesTest {

    private val cis = TestData.cisAllowed
    private fun prep(t: String, rules: Rules = Rules()) = UkLayer.prepare(t, cis, rules)
    private fun off(vararg keys: String) = keys.fold(Rules()) { r, k -> r.with(k, false) }
    /** Путь сервиса: Splitter (его Normalizer.punctuation читает общие ключи) и слой; куски — через « | ». */
    private fun full(t: String, r: Rules = Rules()) = Splitter.sentences(t, r, layer = UkLayer).joinToString(" | ") { prep(it, r) }
    private fun plan(t: String, r: Rules = Rules()) =
        Pipeline.plan(t, TestData.data(), 0, 0, rules = r, layer = UkLayer, allowed = cis).joinToString(" | ") { it.text }

    /** Ключ, который слой читает → пример, где без него вывод другой. */
    private val read = mapOf(
        "currency" to "Ціна 1,20 UAH.",
        "roman" to "XX століття",
        "roman_name" to "Петро I",
        "latin" to "Книга FilmBell",
        "spell_lat" to "USB",
        "letter_digit" to "Galaxy S24",
        "homoglyphs" to "cік",
        "abbrev" to "Яблука і т. д.",
        "numbers" to "температура +11",
        "phones" to "+380 44 123 45 67",
        "times" to "21:30",
        "dates" to "12.05.2026",
        "day_month" to "3 травня",
        "gen_suffix" to "Один з 3-х.",
        "years" to "2026 рік",
        "units" to "3 км",
        "degrees" to "20 °C",
        "pause_parens" to "Книга (том 2) тут",
        "arith" to "2 + 3 = 5",
        "fractions" to "3/4",
        "sections" to "2.10.3",
        "spell_cyr" to "Навчається в КНУ",
        "cases" to "близько 3 годин",
        "codes" to "Код 482913",
        "spoons" to "2 ст. л. цукру",
        "thousands" to "1.000.000",
        "footnotes" to "Текст[1].",
        "emoji" to "Супер :) так",
        "link_domain" to "Дивись https://example.com/page тут",
        "read_links" to "Пишіть на info@mail.ua",
    )

    /** Примеры из UkNumbersTest, UkLayerTest и этого теста: ключи из unsupported их вывод не меняют. */
    private val samples = listOf(
        "2026", "П'ять м'ясних страв!", "Що?!", "ви́хід", "Київ — столиця", "\"Так\", — сказав він.", "Книга (том 2) тут",
        "Сьогодні 2026 рік, 3 томи", "2 грн.", "5 тис. грн.", "ціна в грн", "0441234567", "+380 44 123 45 67", "1 000 000",
        "у 2000 році", "1998 року", "100 років", "5 г солі", "1999 р.", "1.5 кг", "1,05 кг", "3,14", "1.10.2", "у 1812 р.",
        "12.05.2026 р. було", "3 травня 2026 року було", "044 123-45-67", "10 000-15 000", "007", "+1 °C", "-20°С",
        "від -5 до +3", "Ан-225", "1-го вересня", "у 5-му класі", "у 1990-х рр.", "Один з 3-х.", "вона — 2-га", "XX століття",
        "ХІХ століття", "FBI", "у XX ст. — садиба.", "XIX–XX ст. були", "Петро I і Катерина II", "Указ Петра I був жорстокий.",
        "Петренко I. П.", "Курган III ст. до н. е.", "Ціна зросла у 1.25 раза.", "з 9:00 по 18.00", "У 1990-х рр. Київ ріс.",
        "Ціна 1,20 UAH.", "Курс UAH", "Ціна $75,3 млрд. Ще", "€100 млн", "5 %.", "у 1941–1945 рр. місто", "15-16 травня",
        "з 1 по 5 травня", "1-2 рази", "١٢ км", "20 °C", "1/2 склянки", "Подія відбулася у 2024 р. Тепер там музей.",
        "Ціна 5 тис. грн. Телефон є.", "Вага 5kg.", "60 км/год. Далі", "LTE 4G", "Galaxy S24", "Wi-Fi", "USB-C",
        "Яблука, груші і т. д.", "Груші та ін. Потім", "Музей на вул. Хрещатик, 5.", "21:30", "cік", "C++", "д+якую", "Kиїв",
        "https://example.com", "Тел. 1234-5678-9012-3456", "2 + 3 = 5", "3/4 склянки", "1 ч. л.", "§ 5", "п. 2.10.3",
        "Текст[1].", "ПРИВІТ СВІТ", "😀 Добре", "Ось → там", "по-українськи", "8К74", "Т-34", "до 5-ти", "з 2-ма",
        "на 1 вересня", "м. Київ", "смт Ворохта", "ЗСУ і ЄС", "Т. Г. Шевченко", "USB x", "Карл V", "x = -5", "у 5-А класі",
        "напр.Київ", "до н. е.", "Код 482913", "info@mail.ua", "www.ukr.net", "~5 км", "кут α", "(- Иллюстрация -)",
    )

    @Test fun eachReadKeyChangesOutput() {
        for ((k, t) in read) assertNotEquals(k, full(t), full(t, off(k)))
        assertTrue(read.keys.all { it in Rules.KEYS })
        assertTrue(read.keys.none { it in UkLayer.unsupported })
        // те же ключи, что у белорусского слоя
        assertEquals(setOf("currency", "roman", "roman_name", "latin", "spell_lat", "letter_digit", "homoglyphs", "abbrev", "numbers",
            "phones", "times", "dates", "day_month", "gen_suffix", "years", "units", "degrees", "pause_parens", "arith", "fractions",
            "sections", "spell_cyr", "cases", "codes", "spoons", "thousands", "footnotes", "emoji", "link_domain", "read_links"), read.keys)
    }

    @Test fun unsupportedKeysDoNotChangeOutput() {
        assertTrue(UkLayer.unsupported.all { it in Rules.KEYS })
        for (k in UkLayer.unsupported) {
            val flipped = Rules().with(k, !Rules().on(k))
            for (t in samples) {
                assertEquals("$k: $t", full(t, Rules()), full(t, flipped))
                assertEquals("$k, чтец: $t", full(t, Rules().screenReader()), full(t, flipped.screenReader()))
            }
        }
    }

    @Test fun abbreviations() {
        assertEquals("яблука, груші і так далі.", full("Яблука, груші і т. д."))
        assertEquals("і таке інше, і тому подібне.", full("і т. ін., і т. п."))
        // «ін.» — стоп-слово Splitter: одна фраза, точка сокращения остаётся точкой
        assertEquals("груші та інші. потім", full("Груші та ін. Потім"))
        assertEquals("так звана нова хвиля", full("т. зв. нова хвиля"))
        assertEquals("наприклад київ", full("напр. Київ"))
        // без пробела — замена с пробелом
        assertEquals("наприклад київ", full("напр.Київ"))
        assertEquals("дивись таблицю", full("див. таблицю"))
        assertEquals("до нашої ери.", full("до н. е."))
        assertEquals("пъяте століття до нашої ери.", full("V ст. до н. е."))
        // «Н. Е.» заглавными — инициалы, не «нашої ери»: регистр смотрим до lowercase
        assertEquals("автор ен е петренко", full("Автор Н. Е. Петренко"))
        assertEquals("автор н. е. петренко", full("Автор Н. Е. Петренко", off("spell_cyr")))
        assertEquals("ка-ен-у імені шевченка", full("КНУ ім. Шевченка"))
        assertEquals("київська область.", full("Київська обл."))
        assertEquals("у київській області.", full("у Київській обл."))
        assertEquals("шевченківський район, у шевченківському районі", full("Шевченківський р-н, у Шевченківському р-ні"))
    }

    @Test fun nounAbbreviations() {
        assertEquals("місто київ", full("м. Київ"))
        assertEquals("у місті києві.", full("У м. Києві."))
        assertEquals("з міста києва", full("з м. Києва"))
        assertEquals("вулиця хрещатик, пъять", full("вул. Хрещатик, 5"))
        assertEquals("на вулиці хрещатик", full("на вул. Хрещатик"))
        assertEquals("вулиця хрещатик, будинок пъять, квартира два", full("вул. Хрещатик, буд. 5, кв. 2"))
        assertEquals("проспект перемоги, площа ринок", full("просп. Перемоги, пл. Ринок"))
        assertEquals("селище міського типу ворохта, у селищі міського типу ворохта", full("смт Ворохта, у смт Ворохта"))
        assertEquals("село яблунівка, річка дніпро", full("с. Яблунівка, р. Дніпро"))
        assertEquals("на озері світязь", full("на оз. Світязь"))
        assertEquals("сторінка сорок пъять, на сторінці сорок пъять", full("с. 45, на с. 45"))
        assertEquals("дивись пункт два стаття пъятнадцять", full("див. п. 2 ст. 15"))
        assertEquals("у книзі другій", full("у кн. II"))
        assertEquals("том другий", full("т. II"))
        assertEquals("пункт чотири крапка дванадцять", full("п. 4.12"))
        assertEquals("академік патон і професор іваненко", full("акад. Патон і проф. Іваненко"))
        assertEquals("станція фастів", full("ст. Фастів"))
        // не сокращение-существительное: метр за числом, «р.» за годом, «ст.» за римским, инициалы
        assertEquals("довжина пъять метрів. далі", full("Довжина 5 м. Далі"))
        assertEquals("у тисяча девъятсот девъяносто девъятому році київ", full("у 1999 р. Київ"))
        assertEquals("у двадцятому столітті україна", full("у XX ст. Україна"))
        assertEquals("сімсот шістдесят міліметрів ртутного стовпчика.", full("760 мм рт. ст."))
        // правило abbrev выключено — как написано
        assertEquals("м. київ", full("м. Київ", off("abbrev")))
        // «м.» после «кв.», «куб.», «пог.» — метр, не місто
        assertEquals("площа пъятдесят квадратних метрів. далі кухня.", full("Площа 50 кв. м. Далі кухня."))
        assertEquals("видобуто пъять мільйонів кубічних метрів. газу багато.", full("Видобуто 5 млн куб. м. Газу багато."))
        assertEquals("труба двадцять погонних метрів. далі.", full("Труба 20 пог. м. Далі."))
        // падеж: назва в місцевому — місцевий при любом глаголе; в прямій формі — знахідний только после глагола движения;
        // в родовому после «до», «з» — родовий
        assertEquals("їду в місто київ.", full("Їду в м. Київ."))
        assertEquals("приїхав у місто київ.", full("Приїхав у м. Київ."))
        assertEquals("переїхав у місто львів.", full("Переїхав у м. Львів."))
        assertEquals("їду в місто київ і живу там.", full("Їду в м. Київ і живу там."))
        assertEquals("проживає у місті київ.", full("Проживає у м. Київ."))
        assertEquals("живу в місті києві.", full("Живу в м. Києві."))
        assertEquals("вони їдять у місті києві.", full("Вони їдять у м. Києві."))
        assertEquals("обідаю в їдальні в місті києві.", full("Обідаю в їдальні в м. Києві."))
        assertEquals("прибуток у місті києві зріс.", full("Прибуток у м. Києві зріс."))
        assertEquals("їхав у місті києві трамваєм.", full("Їхав у м. Києві трамваєм."))
        assertEquals("вони їдять у місті київ.", full("Вони їдять у м. Київ."))
        assertEquals("доїхав до міста києва.", full("Доїхав до м. Києва."))
        assertEquals("прийшов з міста одеси.", full("Прийшов з м. Одеси."))
        // «тел.» перед заглавной — конец фразы, словом в падеже предлога
        assertEquals("дзвоніть за телефоном. | ми чекаємо.", full("Дзвоніть за тел. Ми чекаємо."))
        assertEquals("довідки за телефоном нуль сорок чотири, сто двадцять три, сорок пъять, шістдесят сім.",
            full("Довідки за тел. 044 123-45-67."))
        // «пов.» за числом: после предлога — порядковое, перед зданием — прикметник, иначе «п'ятий поверх»
        assertEquals("квартира на пъятому поверсі. | ціна договірна.", full("Квартира на 5 пов. Ціна договірна."))
        assertEquals("пъятиповерховий будинок, двоповерхова будівля, у девъятому поверсі будинку",
            full("5 пов. будинок, 2 пов. будівля, у 9 пов. будинку"))
        assertEquals("квартира, третій поверх, з пъятого поверху.", full("Квартира, 3 пов., з 5 пов."))
        // глагол движения через другое сокращение и в 1 мн.: «з м. Одеси в м. Київ», «Летимо в м. Ужгород»
        assertEquals("переїхав з міста одеси в місто київ.", full("Переїхав з м. Одеси в м. Київ."))
        assertEquals("летимо в місто ужгород.", full("Летимо в м. Ужгород."))
        for ((v, t) in listOf("Летите" to "летите", "Летять" to "летять", "Пливемо" to "пливемо", "Біжимо" to "біжимо", "Мчимо" to "мчимо",
                "Вирушаємо" to "вирушаємо", "Прилітають" to "прилітають", "Повертаємося" to "повертаємося", "Йдемо" to "йдемо",
                "Прямуємо" to "прямуємо", "Прибуваємо" to "прибуваємо", "Їдемо" to "їдемо"))
            assertEquals(v, "$t у місто ужгород.", full("$v у м. Ужгород."))
        // после «див.» — знахідний
        assertEquals("дивись сторінку сорок пъять, дивись таблицю три.", full("Див. с. 45, див. табл. 3."))
        // целые слова, начинающиеся как сокращение, не трогаем
        assertEquals("будинок, дивно, імла, областю, районний, просто", full("Будинок, дивно, імла, областю, районний, просто"))
    }

    @Test fun abbrevDotEndsSentence() {
        // точка сокращения, закрывшая предложение, — ровно одна
        assertEquals("народилася першого січня двохтисячного року.", full("Народилася 1 січня 2000 р."))
        assertEquals("пъять кілометрів.", full("5 км."))
        assertEquals("одна ціла дві десятих тисячі кілометрів.", full("1,2 тис. км."))
        assertEquals("ціна пъять тисяч гривень за кілограм.", full("Ціна 5 тис. грн. за кілограм."))
        assertEquals("ціна пъять гривень.", full("Ціна 5 грн."))
        assertEquals("на півтори тисячі гривень", full("на 1,5 тис. грн"))
        assertEquals("температура плюс одинадцять, вітер пъять метрів за секунду.", full("Температура +11, вітер 5 м/с."))
        // перед годом только предлог — середина предложения, точки нет
        assertEquals("у тисяча девъятсот сорок першому році німеччина напала.", full("У 1941 р. Німеччина напала."))
        assertEquals("він переїхав у тисяча девъятсот сорок першому році. потім повернувся.", full("Він переїхав у 1941 р. Потім повернувся."))
        // правило выключено — точка сокращения одна, без двойной
        for (t in listOf("Вода — 5 л. Борошно — 100 г. Цукор — 2 шт.", "Ціна 5 тис. грн. Телефон є.", "Пробіг 1,2 тис. км.",
                "Подія відбулася у 2024 р. Тепер там музей.", "Початок 1990-х рр.", "Курган III ст. до н. е. Потім", "Яблука і т. д.",
                "Груші та ін. Потім", "Київська обл."))
            for (k in listOf("units", "currency", "abbrev", "years", "numbers", "roman"))
                assertFalse("$k: $t", ".." in full(t, off(k)))
    }

    @Test fun classLetter() {
        assertEquals("у пъятому-а класі.", full("У 5-А класі."))
        assertEquals("до сьомого-бе класу", full("до 7-Б класу"))
        assertEquals("одинадцятий-ве клас", full("11-В клас"))
    }

    @Test fun homoglyphs() {
        // латинская буква-двойник в украинском слове — кириллицей; «i» — украинская «і»
        assertEquals("сік", full("cік"))
        assertEquals("кік", full("cік", off("homoglyphs")))
        assertEquals("інший", full("iнший"))
        assertEquals("київ", full("Kиїв"))
        assertEquals("стоп таксі", full("Cтоп TаксI"))
        assertEquals("ми на", full("Mи Hа"))
        assertEquals("їжак", full("ïжак"))
        // марка с украинским окончанием, латиницы больше или слово латиницей — как было
        for (t in listOf("TOYOTAвський", "TAXIст", "файлmicrosoft", "cool")) assertEquals(t, full(t, off("homoglyphs")), full(t))
        assertEquals("тойотавський", full("TOYOTAвський"))
    }

    @Test fun latin() {
        assertEquals("книга фільмб+ел", full("Книга FilmBell"))
        assertEquals("ю-ес-бі", full("USB"))
        // латиница не пропадает целиком
        assertEquals("ю-ес-бі екс", full("USB x"))
        assertEquals("галаксі ес двадцять чотири", full("Galaxy S24"))
        // «g» — «г» (правопис 2019, § 122), «pH» — «пе-аш», буквы не теряются
        assertEquals("блог, л+огін, пе-+аш, ай-о-+ес", full("blog, login, pH, iOS"))
        assertEquals("пъять джі, три ді-друк", full("5G, 3D-друк"))
        assertEquals("вайф+ай, айф+он, гугл", full("Wi-Fi, iPhone, Google"))
        assertEquals("ем-пі три", full("MP3"))
        // одна буква — именем: после имени (римское только I), в формуле, сама по себе
        assertEquals("карл ві зрікся.", full("Карл V зрікся."))
        assertEquals("вакцина спутнік ві допомогла.", full("Вакцина Спутнік V допомогла."))
        assertEquals("день екс настав.", full("День X настав."))
        assertEquals("день х настав.", full("День Х настав."))
        assertEquals("екс дорівнює мінус пъять", full("x = -5"))
        assertEquals("екс більше за пъять", full("x > 5"))
        assertEquals("план бі", full("план B"))
        assertEquals("ей і бі", full("A & B"))
        assertEquals("сі плюс плюс", full("C++"))
        assertEquals("елізабет друга", full("Elizabeth II"))
        // размер латинской «x» — «на», как кириллической
        assertEquals("три на чотири метри", full("3x4 м"))
        assertEquals("файл крапка ті-екс-ті, репорт крапка пі-ді-еф", full("файл.txt, report.pdf"))
        assertEquals("кут альфа, пі", full("кут α, π"))
        assertEquals("кільк+ість", full("кільк+ість"))
        assertEquals("книга", full("Книга FilmBell", off("latin")))
        assertEquals("усб", full("USB", off("spell_lat")))
        assertEquals("галаксі с двадцять чотири", full("Galaxy S24", off("letter_digit")))
    }

    @Test fun spellCyr() {
        assertEquals("зе-ес-у", full("ЗСУ"))
        assertEquals("є-ес", full("ЄС"))
        assertEquals("ес-ша-а", full("США"))
        assertEquals("навчається в ка-ен-у.", full("Навчається в КНУ."))
        assertEquals("ем-ве-ес, ес-бе-у, пе-де-ве", full("МВС, СБУ, ПДВ"))
        assertEquals("о-бе-ес-є, є-бе-ер-ер, о-а-е, о-те-ге", full("ОБСЄ, ЄБРР, ОАЕ, ОТГ"))
        // словом: гласных больше одной или в середине, частые слова
        assertEquals("нато, оон, зно, моз", full("НАТО, ООН, ЗНО, МОЗ"))
        // заголовок капсом — словами
        assertEquals("україна понад усе", full("УКРАЇНА ПОНАД УСЕ"))
        // код и марка с числом: буквы капсом — по буквам, «Ан» — слово
        assertEquals("бе-те-ер-чотири, ан-двісті двадцять пъять", full("БТР-4, Ан-225"))
        assertEquals("вісім к+а сімдесят чотири, т+е-тридцять чотири", full("8К74, Т-34"))
        // инициалы — именами букв, одна фраза
        assertEquals("те ге шевченко народився.", full("Т. Г. Шевченко народився."))
        assertEquals("шевченко те ге народився.", full("Шевченко Т. Г. народився."))
        for (t in listOf("Автор Є. Ґудзь пише.", "Пише Ї. Петренко.", "Пише І. Франко.", "Пише Ґ. Ґудзь."))
            assertEquals(t, 1, Splitter.sentences(t, layer = UkLayer).size)
        assertEquals("автор є ґудзь пише.", full("Автор Є. Ґудзь пише."))
        // строчные без гласных — по буквам; единицы и междометия — нет
        assertEquals("ка-ем-ен", full("кмн"))
        assertEquals("хм, кв, шт", full("хм, кв, шт"))
        assertEquals("зсу", full("ЗСУ", off("spell_cyr")))
    }

    @Test fun codes() {
        assertEquals("код чотири вісім два, девъять один три", full("Код 482913"))
        assertEquals("пін один два, три чотири", full("PIN 1234"))
        assertEquals("карта чотири два сім шість, один два три чотири, пъять шість сім вісім, девъять нуль один два",
            full("Карта 4276 1234 5678 9012"))
        assertEquals("один два три чотири", full("•• 1234"))
        assertEquals("єдрпоу один два три, чотири пъять шість, сім вісім", full("ЄДРПОУ 12345678"))
        assertEquals("ваш код: один два, три чотири", full("Ваш код: 1234"))
        // не код: число с единицей, год
        assertEquals("код пъять кілометрів", full("Код 5 км"))
    }

    @Test fun linksEmojiAndMarks() {
        assertEquals("дивись посилання, ексампле крапка ком тут", full("Дивись https://example.com/page тут"))
        assertEquals("посилання, укр крапка нет", full("www.ukr.net"))
        assertEquals("пишіть на інфо собачка мейл крапка уа", full("Пишіть на info@mail.ua"))
        assertEquals("пишіть на", full("Пишіть на info@mail.ua", Rules().with("drop_emails", true)))
        assertEquals("супер, усміхається, так", full("Супер :) так"))
        assertEquals("текст.", full("Текст[1]."))
        assertEquals("дослідники.", full("дослідники2."))
        assertEquals("один мільйон", full("1.000.000"))
        assertEquals("приблизно пъять кілометрів", full("~5 км"))
        assertEquals("ілюстрація.", full("(- Иллюстрация -)"))
        assertEquals("", full("(- Ілюстрація -)", Rules().with("drop_images", true)))
    }

    @Test fun throughPipeline() {
        // одна буква латиницей и аббревиатура — через Pipeline.plan, как на телефоне
        assertEquals("ю-ес-бі екс", prep(plan("USB x")))
        assertEquals("зе-ес-у", prep(plan("ЗСУ")))
        assertEquals("місто київ", prep(plan("м. Київ")))
        // Pipeline.plan с украинским голосом режет по украинским сокращениям: «тел.» перед номером — одна фраза
        fun planned(t: String) = Pipeline.plan(t, TestData.data(), 0, 0, layer = UkLayer, allowed = cis).joinToString(" | ") { prep(it.text) }
        assertEquals("довідки за телефоном нуль сорок чотири, сто двадцять три, сорок пъять, шістдесят сім.",
            planned("Довідки за тел. 044 123-45-67."))
        assertEquals("телефон нуль сорок чотири, сто двадцять три, сорок пъять, шістдесят сім.", planned("Тел. 044 123-45-67."))
        assertEquals("навчається в ка-ен-у імені шевченка.", planned("Навчається в КНУ ім. Шевченка."))
        assertEquals("дивись додаток два.", planned("Див. Додаток 2."))
        // тот же текст русским голосом режется, как до украинских сокращений
        assertEquals(2, Pipeline.plan("Довідки за тел. 044 123-45-67.", TestData.data(), 0, 0, allowed = cis).size)
    }

    /** Латиница и цифры не доходят до модели: фильтр пропускал бы их, но шаги слоя их уже прочли. */
    @Test fun noLatinOrDigitsLeak() {
        val wide = cis + ('a'..'z').joinToString("") + ('A'..'Z').joinToString("") + "0123456789"
        Emoji.uk = File(TestData.root(), "app/src/main/assets/emoji_uk.tsv").bufferedReader().useLines { Emoji(it) }
        try {
            for (r in listOf(Rules(), Rules().screenReader())) for (t in samples + read.values) {
                val out = Splitter.sentences(t, r, layer = UkLayer).joinToString(" ") { UkLayer.prepare(it, wide, r) }
                assertFalse("$t → $out", out.any { it in 'a'..'z' || it in 'A'..'Z' || it.isDigit() })
            }
        } finally { Emoji.uk = null }
    }

    @Test fun allKeysOff() {
        val none = Rules.KEYS.fold(Rules()) { r, k -> r.with(k, false) }
        val plusNotStress = Regex("\\+(?![аеєиіїоуюяёыэ])")
        for (t in samples + listOf("Привіт! Ура!!", "C++ і +А", "об’єкт", "+5 і −5", "Що!?", "XX століття, USB, 5G")) {
            val out = prep(t, none)
            assertTrue("$t → $out", out.all { it in cis })
            assertFalse("$t → $out", '!' in out || UkNormalizer.APOSTROPHE.containsMatchIn(out))
            assertFalse("$t → $out", plusNotStress.containsMatchIn(out))
        }
    }
}
