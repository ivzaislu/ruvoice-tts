package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.TestData

class SplitterTest {
    @Test fun paragraphsByNewline() {
        assertEquals(listOf("Первый абзац.", "Второй."), Splitter.paragraphs("Первый абзац.\n\nВторой.\n"))
    }

    @Test fun dehyphenatesLineWrap() {
        // Task 18 п.1: строчная после переноса — деление слова, дефис и перенос убираем.
        assertEquals(listOf("собака"), Splitter.paragraphs("со-\nбака"))
    }

    @Test fun keepsHyphenForCapitalizedCompound() {
        // Task 18 п.1: заглавная после переноса — составное слово, дефис оставляем.
        assertEquals(listOf("Санкт-Петербург"), Splitter.paragraphs("Санкт-\nПетербург"))
    }

    @Test fun crlfLineEndingsAreNormalizedForDehyphenation() {
        // финальный fix-раунд п.9: hyphenBreakRe ждёт «-\n» вплотную, «-\r\n» без нормализации
        // не матчится — дефис после переноса строки не убирается.
        assertEquals(listOf("собака"), Splitter.paragraphs("со-\r\nбака"))
    }

    @Test fun singleNewlineBeforeLowercaseIsSpace() {
        // Task 18 п.2: одиночный перенос строки внутри предложения — не новый абзац.
        assertEquals(listOf("Он шёл домой.", "Новый абзац."),
            Splitter.paragraphs("Он шёл\nдомой.\n\nНовый абзац."))
    }

    @Test fun singleNewlineBeforeBelarusianLowercaseIsSpace() {
        // перенос строки перед «і …», «ўсё» в белорусской книге — не абзац
        assertEquals(listOf("Ён прыйшоў і сеў, ўсё."), Splitter.paragraphs("Ён прыйшоў\nі сеў,\nўсё."))
    }

    @Test fun singleNewlineBeforeUppercaseIsParagraphBreak() {
        assertEquals(listOf("Он шёл", "Домой"), Splitter.paragraphs("Он шёл\nДомой"))
    }

    @Test fun keepsHyphenForParticleAfterBreak() {
        // review round 1 п.3: «то» после переноса — частица, не окончание слова.
        assertEquals(listOf("кто-то"), Splitter.paragraphs("кто-\nто"))
    }

    @Test fun keepsHyphenForShortPrefixBeforeBreak() {
        // review round 1 п.3: «по» перед переносом — короткая приставка-предлог.
        assertEquals(listOf("по-русски"), Splitter.paragraphs("по-\nрусски"))
        assertEquals(listOf("из-за"), Splitter.paragraphs("из-\nза"))
        assertEquals(listOf("кое-кто"), Splitter.paragraphs("кое-\nкто"))
        assertEquals(listOf("во-первых"), Splitter.paragraphs("во-\nпервых"))
    }

    // review round 1 п.1: реальный пайплайн режет на предложения ДО Normalizer.prepare(),
    // поэтому Splitter.sentences() сам должен чистить пунктуацию через Normalizer.punctuation().
    @Test fun sentencesCleanUpPunctuationBeforeSplitting() {
        assertEquals(listOf("Всё…", "Дальше."), Splitter.sentences("Всё. . . Дальше."))
    }

    @Test fun sentencesByPunctuation() {
        assertEquals(listOf("Он ждал.", "Никто не пришёл!", "Почему?", "Всё…", "Конец"),
            Splitter.sentences("Он ждал. Никто не пришёл! Почему? Всё… Конец"))
    }

    @Test fun keepsInitialsTogether() {
        assertEquals(listOf("Л. Н. Толстой родился в 1828 г. в Ясной Поляне.", "Он писал."),
            Splitter.sentences("Л. Н. Толстой родился в 1828 г. в Ясной Поляне. Он писал."))
        assertEquals(listOf("Раз.", "Два!"), Splitter.sentences("Раз. Два!"))
        // белорусский инициал «І.» — тоже не конец предложения
        assertEquals(listOf("Акадэмік І. Шамякін напісаў раман.", "Далей."),
            Splitter.sentences("Акадэмік І. Шамякін напісаў раман. Далей."))
    }

    @Test fun keepsAbbreviationStopWordsTogether() {
        assertEquals(listOf("Смотри стр. 5 и рис. 2.", "Дальше идёт текст."),
            Splitter.sentences("Смотри стр. 5 и рис. 2. Дальше идёт текст."))
        assertEquals(listOf("Он ушёл.", "Она осталась."), Splitter.sentences("Он ушёл. Она осталась."))
        // сокращение за скобкой — тоже не конец предложения
        assertEquals(listOf("Текст [прим. ред.] конец."), Splitter.sentences("Текст [прим. ред.] конец."))
    }

    @Test fun belarusianStreetAbbrevKeepsSentence() {
        // белорусское «вул.» (вуліца) — не конец предложения
        assertEquals(listOf("Музей на вул. Леніна, 5 адкрыўся.", "Далей."), Splitter.sentences("Музей на вул. Леніна, 5 адкрыўся. Далей."))
        // русскому тексту не мешает: формы «вул» в морфологии AOT нет, «вулкан.» — конец предложения
        assertEquals(0, Morph.open(java.io.File(TestData.root(), "app/src/main/assets/morph.bin")).tags("вул"))
        assertEquals(listOf("Там был вулкан.", "Он спал."), Splitter.sentences("Там был вулкан. Он спал."))
        // «г. зн.», «і інш.», «т. зв.» — тоже; «і», «ў» в классе букв сокращения русскому не мешают: в русском их нет,
        // а «зн», «зв» словами AOT не бывают
        assertEquals(listOf("Праз тыдзень, г. зн. у панядзелак."), Splitter.sentences("Праз тыдзень, г. зн. у панядзелак."))
        assertEquals(listOf("Кнігі і інш. выданні."), Splitter.sentences("Кнігі і інш. выданні."))
        assertEquals(listOf("Гэта т. зв. рэжым."), Splitter.sentences("Гэта т. зв. рэжым."))
        val morph = Morph.open(java.io.File(TestData.root(), "app/src/main/assets/morph.bin"))
        assertEquals(0, morph.tags("зн")); assertEquals(0, morph.tags("зв"))
        assertEquals(listOf("Он знал.", "Она звала."), Splitter.sentences("Он знал. Она звала."))
        assertEquals(listOf("Смотри стр. 5 и рис. 2.", "Дальше."), Splitter.sentences("Смотри стр. 5 и рис. 2. Дальше."))
        // «зн», «інш», «зв» — только строчными: русские «Зв.» (зиверт) и «ЗВ.» — конец предложения
        assertEquals(listOf("Пороговая доза примерно 0,25 Зв.", "Порог толерантности индивидуален."),
            Splitter.sentences("Пороговая доза примерно 0,25 Зв. Порог толерантности индивидуален."))
        assertEquals(listOf("Как Дисней поглумится над вселенной ЗВ.", "Интересно, как?"),
            Splitter.sentences("Как Дисней поглумится над вселенной ЗВ. Интересно, как?"))
    }

    @Test fun belarusianAbbrevBeforeNumberOrNameKeepsSentence() {
        // кусок, разрезанный после них, нормализатор уже не разворачивал: «воз» словом, «ст.» станцыей
        for (t in listOf("Глядзіце п. 2 арт. 15.", "Улетку мы ездзілі на воз. Нарач.", "Пра гэта напісана ў кн. II.",
                "Атмасферны ціск 760 мм рт. ст.", "Чытайце гл. 3, т. 2.", "Гл. рыс. 4 і табл. 2."))
            assertEquals(listOf(t), Splitter.sentences(t))
    }

    @Test fun ukrainianAbbrevBeforeNumberOrNameKeepsSentence() {
        // кусок, разрезанный после них, украинский нормализатор уже не разворачивал: «тис.» без «км», «год.» без «ранку»
        for (t in listOf("Дивіться п. 2 ст. 15.", "Влітку ми їздили на оз. Світязь.", "Про це написано у кн. II.",
                "Атмосферний тиск 760 мм рт. ст.", "Читайте гл. 3, т. 2, с. 45.", "Див. рис. 4 і табл. 2.", "Див. розд. 3 і дод. 2.",
                "Ціна 5 тис. грн. за кілограм.", "Пробіг 1,2 тис. км.", "У 1941–1945 рр. місто зруйнували.", "Прокинувся о 5 год. ранку.",
                "Чекали 20 хв. після дзвінка.", "Живе на просп. Перемоги.", "Живе на вул. Хрещатик, буд. 5.", "Виступив акад. Патон.",
                "Доповідали проф. Іваненко і доц. Петренко.", "Навчається в КНУ ім. Шевченка.", "Живе в смт. Ворохта.",
                "Прийшло 5 тис. чол. на мітинг.", "Тираж 300 екз. розійшовся.", "Яблука, груші та ін. Потім", "Ціна 50 коп. за штуку.",
                "Т. Г. Шевченко народився.", "Пише Ї. Петренко.", "Живе в пров. Тихий.", "Читай прим. 3 і стор. 5.",
                "Див. вип. 4.", "Живе на 5 пов. будинку.", "Довідки за тел. 044 123-45-67.", "Тел. 044 123-45-67.",
                "Див. Додаток 2."))
            assertEquals(listOf(t), Splitter.sentences(t, layer = UkLayer))
        // тис, грн, рр, год, ос, чол, екз — только перед строчной: перед заглавной — конец фразы
        assertEquals(listOf("Ціна 5 тис. грн.", "За кілограм."), Splitter.sentences("Ціна 5 тис. грн. За кілограм.", layer = UkLayer))
        assertEquals(listOf("Прокинувся о 5 год.", "Ранок."), Splitter.sentences("Прокинувся о 5 год. Ранок.", layer = UkLayer))
        assertEquals(listOf("Зібрали 5 тис.", "Потім пішли."), Splitter.sentences("Зібрали 5 тис. Потім пішли.", layer = UkLayer))
        // «тел.» — только перед номером («+», «(», цифра) или строчной; «пов.» — только перед строчной
        assertEquals(listOf("Дзвоніть за тел.", "Ми чекаємо."), Splitter.sentences("Дзвоніть за тел. Ми чекаємо.", layer = UkLayer))
        assertEquals(1, Splitter.sentences("Тел. +380 44 123 45 67, тел. (044) 123-45-67.", layer = UkLayer).size)
        assertEquals(listOf("Квартира на 5 пов.", "Ціна договірна."), Splitter.sentences("Квартира на 5 пов. Ціна договірна.", layer = UkLayer))
    }

    @Test fun ukrainianAbbreviationsOnlyForUkrainianLayer() {
        // украинские сокращения — только у голоса uk ([TextLayer.keepsSentence]): русский и белорусский текст режется, как до них
        for (layer in listOf(Layers.RU, BeLayer))
            for (t in listOf("Это был трудный год. Потом всё изменилось.", "Налетело много ос. Все убежали.", "Его звали Пров. Он был актёром.",
                    "В зале вип. Затем гости.", "Нашли десятки тел. Полиция работает.", "Места на 2021-2022 год. 365 детей пойдут в сады.",
                    "Тел. 8 012 345 06 78", "Она завоевала титул чемпионки Див. Бетси проиграла.", "Я ганаруся ім. Ён добры чалавек.",
                    "Мы сказалі ім. Яны пайшлі.", "Дзякуй ім. Усё.", "Бензин стоит 36 грн. за литр.", "Живёт на просп. Мира."))
                assertEquals("$layer: $t", 2, Splitter.sentences(t, layer = layer).size)
        assertEquals(listOf("Довідки за тел. 044 123-45-67."), Splitter.sentences("Довідки за тел. 044 123-45-67.", layer = UkLayer))
    }

    @Test fun abbreviationBeforeLowercaseOrDigitKeepsSentence() {
        // «прил.», «чел.» — русские сокращения: перед строчной или цифрой не конец предложения, перед заглавной — как было
        assertEquals(listOf("Согласно прил. 2 к приказу всё готово."), Splitter.sentences("Согласно прил. 2 к приказу всё готово."))
        assertEquals(listOf("Согласно прил. 2 к договору."), Splitter.sentences("Согласно прил. 2 к договору."))
        assertEquals(2, Splitter.sentences("См. прил. Там всё есть.").size)
        assertEquals(listOf("Пришли 20 тыс. чел. и остались до вечера."), Splitter.sentences("Пришли 20 тыс. чел. и остались до вечера."))
        assertEquals(listOf("Пришли 300 чел. 2 группы уехали."), Splitter.sentences("Пришли 300 чел. 2 группы уехали."))
        assertEquals(listOf("Пришло 300 чел. потом ещё 50."), Splitter.sentences("Пришло 300 чел. потом ещё 50."))
        assertEquals(listOf("В списке 12 чел. а мест всего 10."), Splitter.sentences("В списке 12 чел. а мест всего 10."))
        assertEquals(listOf("Пришли 300 чел.", "Потом ушли."), Splitter.sentences("Пришли 300 чел. Потом ушли."))
        // «ум.» (умер) — так же
        assertEquals(listOf("Сын Иоанна Предтечи (ум. ок. 28 г.) жил долго."), Splitter.sentences("Сын Иоанна Предтечи (ум. ок. 28 г.) жил долго."))
        assertEquals(listOf("Иван Петров (1890, ум. 1945) — поэт."), Splitter.sentences("Иван Петров (1890, ум. 1945) — поэт."))
        assertEquals(listOf("Поэт ум. в 1945 году."), Splitter.sentences("Поэт ум. в 1945 году."))
        assertEquals(2, Splitter.sentences("Он был очень ум. Потом ушёл.").size)
        // «род.» (родился; родительный падеж) — так же
        assertEquals(listOf("Иван Петров (род. 1890) — поэт."), Splitter.sentences("Иван Петров (род. 1890) — поэт."))
        assertEquals(listOf("Слово стоит в род. падеже."), Splitter.sentences("Слово стоит в род. падеже."))
        assertEquals(listOf("Это был древний род.", "Потом он угас."), Splitter.sentences("Это был древний род. Потом он угас."))
        // «ок.» перед строчной — одна фраза; перед цифрой — «около», если перед «ок.» обычное слово или «(»; в начале куска,
        // после «ну/да/ладно/хорошо…» и после «,», «—», «:» — «ок» в реплике («Ок. 5 минут» читалось «около пяти минут»)
        assertEquals(listOf("Ну ок. давай завтра."), Splitter.sentences("Ну ок. давай завтра."))
        assertEquals(1, Splitter.sentences("— Ок. ладно, пойдём, — сказал он.").size)
        assertEquals(listOf("Хорошо, ок. а что потом?"), Splitter.sentences("Хорошо, ок. а что потом?"))
        assertEquals(listOf("Я сказал ок. он кивнул."), Splitter.sentences("Я сказал ок. он кивнул."))
        assertEquals(2, Splitter.sentences("— Ок. 5 минут и выходим.").size)
        assertEquals(listOf("Ну ок.", "2 часа ждать придётся."), Splitter.sentences("Ну ок. 2 часа ждать придётся."))
        assertEquals(listOf("Хорошо, ок.", "10 раз повторю."), Splitter.sentences("Хорошо, ок. 10 раз повторю."))
        assertEquals(listOf("Расстояние ок. 300 км."), Splitter.sentences("Расстояние ок. 300 км."))
        assertEquals(listOf("Рудольф II умер ок. 990 г. в Альтдорфе."), Splitter.sentences("Рудольф II умер ок. 990 г. в Альтдорфе."))
        assertEquals(listOf("Статья — Арран (ок. 837 г.)."), Splitter.sentences("Статья — Арран (ок. 837 г.)."))
        assertEquals(listOf("Восточнее Франкфурта; длина ок. 100 км; высшая точка 982 м."),
            Splitter.sentences("Восточнее Франкфурта; длина ок. 100 км; высшая точка 982 м."))
        assertEquals(listOf("На трассе М-4 ок. 300 км пробок."), Splitter.sentences("На трассе М-4 ок. 300 км пробок."))
        assertEquals(listOf("Грегоре; ок. 540 — 604; папа с 590 г."), Splitter.sentences("Грегоре; ок. 540 — 604; папа с 590 г."))
        assertEquals(2, Splitter.sentences("Ок. 300 км до города.").size)
        assertEquals(2, Splitter.sentences("Он ответил: «Ок. 5 минут».").size)
        assertEquals(2, Splitter.sentences("Ответ был короткий: ок. 5 минут.").size)
        assertEquals(2, Splitter.sentences("Да ок. 3 раза уже проверил.").size)
        assertEquals(2, Splitter.sentences("Угу, ок. 2 штуки возьму.").size)
        assertEquals(listOf("Хорошо, ок.", "Идём дальше."), Splitter.sentences("Хорошо, ок. Идём дальше."))
        // повторное ревью ru-fixes-2 M-c: длинная цепочка сокращений без конца фразы — не квадрат (было 7 с на 8000 «род.»)
        for (s in listOf("род. ".repeat(8000), "Петрова (род. 1900 — ум. 2000) ".repeat(1000), "ок. ".repeat(8000))) {
            val t0 = System.nanoTime()
            Splitter.sentences(s)
            assertTrue("${s.take(20)}: ${(System.nanoTime() - t0) / 1_000_000} мс", System.nanoTime() - t0 < 2_000_000_000L)
        }
        // «пос.» перед заглавной — название посёлка, как «г. Москва»; перед словом, которым начинают фразу, — конец фразы
        assertEquals(listOf("Мы жили в пос. городского типа."), Splitter.sentences("Мы жили в пос. городского типа."))
        assertEquals(listOf("Мы жили в пос. Лесной до 2020 года."), Splitter.sentences("Мы жили в пос. Лесной до 2020 года."))
        assertEquals(listOf("Мы приехали в пос. Лесной в пятницу."), Splitter.sentences("Мы приехали в пос. Лесной в пятницу."))
        assertEquals(listOf("Мы приехали в пос.", "Он был пуст."), Splitter.sentences("Мы приехали в пос. Он был пуст."))
        assertEquals(listOf("Дорога вела в пос.", "А там уже ждали."), Splitter.sentences("Дорога вела в пос. А там уже ждали."))
        assertEquals(listOf("Машина довезла до пос.", "Дальше пешком."), Splitter.sentences("Машина довезла до пос. Дальше пешком."))
        // список русский: белорусский путь его не видит
        assertEquals(listOf("Жыў у пос.", "Лясны быў побач."), Splitter.sentences("Жыў у пос. Лясны быў побач.", layer = BeLayer))
        // белорусское «гадз.» и украинское «год.» — своими списками
        assertEquals(listOf("Працуем з 9 да 18 гадз. і ў суботу."),
            Splitter.sentences("Працуем з 9 да 18 гадз. і ў суботу.", layer = BeLayer))
        assertEquals(listOf("Прыём да 18 гадз. 30 хв."), Splitter.sentences("Прыём да 18 гадз. 30 хв.", layer = BeLayer))
        assertEquals(listOf("Працюємо з 9 до 18 год. щодня."), Splitter.sentences("Працюємо з 9 до 18 год. щодня.", layer = UkLayer))
        assertEquals(listOf("Прийом до 18 год. 30 хв."), Splitter.sentences("Прийом до 18 год. 30 хв.", layer = UkLayer))
        assertEquals(listOf("Прийшли о 5 год.", "Потім пішли."), Splitter.sentences("Прийшли о 5 год. Потім пішли.", layer = UkLayer))
    }

    @Test fun okIsNotAnAbbreviationStopWord() {
        // review t17 п.6 (Splitter.kt:10): «ок» — разговорное слово в диалогах, не сокращение.
        assertEquals(listOf("Хорошо, ок.", "Идём дальше."), Splitter.sentences("Хорошо, ок. Идём дальше."))
    }

    @Test fun longSentenceSplitsAtComma() {
        val long = (1..30).joinToString(", ") { "слово$it" } + "."
        val parts = Splitter.sentences(long, Rules(maxLen = 80))
        assertTrue(parts.toString(), parts.all { it.length <= 80 })
        assertEquals(long.replace(",", ""), parts.joinToString(" ").replace(",", "").replace("  ", " "))
    }

    @Test fun decimalCommaIsNotACut() {
        // запятая между цифрами — дробь: длинную фразу резали по ней («и 49,2 тысячи» → «сорок девять | две тысячи»)
        val ru = "Банк заявил о пропаже 15 миллионов рублей, 49,5 тысячи долларов (3,6 миллиона рублей) и 49,2 тысячи евро."
        assertEquals(listOf("Банк заявил о пропаже 15 миллионов рублей", "49,5 тысячи долларов (3,6 миллиона рублей) и 49,2 тысячи евро."),
            Splitter.sentences(ru, Rules(maxLen = 92)))
        // список дробей: режем по запятой с пробелом, не внутри числа
        assertEquals(listOf("Составили 1,2", "3,4 и", "5,6 процента."), Splitter.sentences("Составили 1,2, 3,4 и 5,6 процента.", Rules(maxLen = 18)))
        // запятой вне числа левее нет — пробел, и не между предлогом и числом
        assertEquals(listOf("Вырос", "на 27,2."), Splitter.sentences("Вырос на 27,2.", Rules(maxLen = 11)))
        // белорусский и украинский путь режет тем же Splitter
        assertEquals(listOf("Прададзена 15 кніг", "у суполцы (27,2 тысячы) і 41,1 тысячы."),
            Splitter.sentences("Прададзена 15 кніг, у суполцы (27,2 тысячы) і 41,1 тысячы.", Rules(maxLen = 50), layer = BeLayer))
        assertEquals(listOf("Продано 15 книжок", "у спільноті (27,2 тисячі) і 41,1 тисячі."),
            Splitter.sentences("Продано 15 книжок, у спільноті (27,2 тисячі) і 41,1 тисячі.", Rules(maxLen = 50), layer = UkLayer))
        // фраза с адресом — то же
        assertEquals(listOf("См. https://a.ru/x, в нём 1,5", "2,5 тысячи."),
            Splitter.sentences("См. https://a.ru/x, в нём 1,5, 2,5 тысячи.", Rules(maxLen = 32)))
        // мерка чтеца (число раздувается) тоже не ставит разрез внутрь дроби
        val parts = Splitter.sentences("Подписчиков стало 41,1 тысячи, а через год 27,2 тысячи.", Rules(maxLen = 60), { s: String -> s.length * 3 })
        val joined = parts.joinToString(" ")
        assertTrue(parts.toString(), "41,1" in joined && "27,2" in joined)
    }

    @Test fun spaceInsideOrAfterNumberIsNotACut() {
        // пробел между разрядами — не разрез («1 026 | 000» читалось «одна тысяча двадцать шесть | ноль»)
        assertEquals(listOf("Собрали 15 000 рублей", "и 1 026 000 долларов."),
            Splitter.sentences("Собрали 15 000 рублей и 1 026 000 долларов.", Rules(maxLen = 30)))
        assertEquals(listOf("Всего", "1 026 000."), Splitter.sentences("Всего 1 026 000.", Rules(maxLen = 9)))
        // NBSP и узкий пробел между разрядами не режутся и так; пробел за числом — тоже нет
        assertEquals(listOf("Собрали", "15 000 рублей."), Splitter.sentences("Собрали 15 000 рублей.", Rules(maxLen = 16)))
        assertEquals(listOf("Собрали", "15 000 рублей."), Splitter.sentences("Собрали 15 000 рублей.", Rules(maxLen = 16)))
        // пробел сразу за числом отрывал его от слова — берём пробел левее (и не перед числом)
        assertEquals(listOf("Рост", "составил", "27,2 тысячи", "человек."), Splitter.sentences("Рост составил 27,2 тысячи человек.", Rules(maxLen = 19)))
        assertEquals(listOf("Он", "отдал", "15 рублей за", "хлеб."), Splitter.sentences("Он отдал 15 рублей за хлеб.", Rules(maxLen = 12)))
        // левее пробела нет — режем за числом, а не по пределу посреди слова
        assertEquals(listOf("Цена", "15", "рублей."), Splitter.sentences("Цена 15 рублей.", Rules(maxLen = 9)))
        assertEquals(listOf("Сабралі 15 000 рублёў", "і 1 026 000 долараў."),
            Splitter.sentences("Сабралі 15 000 рублёў і 1 026 000 долараў.", Rules(maxLen = 30), layer = BeLayer))
        assertEquals(listOf("Зібрали 15 000 гривень", "і 1 026 000 доларів."),
            Splitter.sentences("Зібрали 15 000 гривень і 1 026 000 доларів.", Rules(maxLen = 30), layer = UkLayer))
        // разряд — только группа из трёх цифр: два отдельных числа через пробел режутся, как раньше
        assertEquals(listOf("12345678", "87654321"), Splitter.sentences("12345678 87654321", Rules(maxLen = 12)))
        // мерка чтеца: число длиннее предела — разрез по пределу не встаёт на пробел между разрядами
        for (layer in listOf(Layers.RU, BeLayer, UkLayer)) {
            val parts = Splitter.sentences("На 1 026 000 000 000 было", Rules(maxLen = 20), { s: String -> s.length * 4 }, layer)
            assertTrue(parts.toString(), parts.zipWithNext().none { (a, b) -> a.last().isDigit() && b.first().isDigit() })
        }
    }

    @Test fun prepositionAndAbbrevStayWithNumber() {
        // предел ровно до конца числа: раньше разрез вставал между предлогом (сокращением) и числом
        fun cutAt(s: String, num: String, layer: TextLayer = Layers.RU) =
            Splitter.sentences(s, Rules(maxLen = s.indexOf(num) + num.length - 1), layer = layer)
        assertEquals(listOf("Автобусы ходят каждый день", "с 10:30 до 11:45 без перерыва."),
            cutAt("Автобусы ходят каждый день с 10:30 до 11:45 без перерыва.", "11:45"))
        assertEquals(listOf("Лизинговые платежи учитываются", "по ст. 346.15 и ст. 346.17 НК."),
            cutAt("Лизинговые платежи учитываются по ст. 346.15 и ст. 346.17 НК.", "346.17"))
        assertEquals(listOf("Компания потеряла в прошлом году", "больше 2 000 000 000 долларов."),
            cutAt("Компания потеряла в прошлом году больше 2 000 000 000 долларов.", "2 000 000 000"))
        assertEquals(listOf("Билеты на премьеру в театр продавали", "по 1000 рублей за штуку."),
            cutAt("Билеты на премьеру в театр продавали по 1000 рублей за штуку.", "1000"))
        assertEquals(listOf("За отмену закона проголосовали всего", "против 87 депутатов."),
            cutAt("За отмену закона проголосовали всего против 87 депутатов.", "87"))
        assertEquals(listOf("Офис компании переехал в соседний дом на улице Ленина", "д. 15 в прошлом месяце."),
            cutAt("Офис компании переехал в соседний дом на улице Ленина д. 15 в прошлом месяце.", "15"))
        assertEquals(listOf("Аўтобусы ходзяць кожны дзень", "з 10:30 да 11:45 без перапынку."),
            cutAt("Аўтобусы ходзяць кожны дзень з 10:30 да 11:45 без перапынку.", "11:45", BeLayer))
        assertEquals(listOf("Плацяжы ўлічваюцца паводле", "арт. 346 і арт. 347 падатковага кодэкса."),
            cutAt("Плацяжы ўлічваюцца паводле арт. 346 і арт. 347 падатковага кодэкса.", "347", BeLayer))
        assertEquals(listOf("Автобуси ходять щодня", "з 10:30 до 11:45 без перерви на обід."),
            cutAt("Автобуси ходять щодня з 10:30 до 11:45 без перерви на обід.", "11:45", UkLayer))
        assertEquals(listOf("Платежі враховуються", "за ст. 346 і ст. 347 податкового", "кодексу."),
            cutAt("Платежі враховуються за ст. 346 і ст. 347 податкового кодексу.", "347", UkLayer))
        assertEquals(listOf("Компанія втратила минулого року", "понад 2 000 000 000 доларів."),
            cutAt("Компанія втратила минулого року понад 2 000 000 000 доларів.", "2 000 000 000", UkLayer))
        // голова из одного слова — только если другого разреза нет
        assertEquals(listOf("а 1 000 1 000 1 000 1 000", "1 000 1 000 1 000 1 000 рублей"),
            Splitter.sentences("а 1 000 1 000 1 000 1 000 1 000 1 000 1 000 1 000 рублей", Rules(maxLen = 30)))
        // знак номера — как цифра: «под | № 7» читалось «под | номер семь»
        assertEquals(listOf("Заявка в общем списке стоит", "под № 7 до конца месяца."),
            cutAt("Заявка в общем списке стоит под № 7 до конца месяца.", "№ 7"))
        assertEquals(listOf("Порядок оплаты определяется", "по § 3 и § 4 договора."),
            cutAt("Порядок оплаты определяется по § 3 и § 4 договора.", "§ 3"))
        assertEquals(listOf("Порядок оплаты определяется", "по §3 договора."), cutAt("Порядок оплаты определяется по §3 договора.", "§3"))
        assertEquals(listOf("Заяўка ў агульным спісе стаіць", "пад № 7 да канца месяца."),
            cutAt("Заяўка ў агульным спісе стаіць пад № 7 да канца месяца.", "№ 7", BeLayer))
        assertEquals(listOf("Заявка в загальному списку стоїть", "під № 7 до кінця місяця."),
            cutAt("Заявка в загальному списку стоїть під № 7 до кінця місяця.", "№ 7", UkLayer))
        // короткое слово перед сокращением с точкой — предлог при нём: «на | вул. Леніна» читалось «вуліца» без «на»
        assertEquals(listOf("Справаздача апублікаваная", "на вул. Леніна, д. 15."),
            cutAt("Справаздача апублікаваная на вул. Леніна, д. 15.", "вул.", BeLayer))
        assertEquals(listOf("Платежи там учитываются", "по ст. 15 и ст. 16 кодекса."),
            cutAt("Платежи там учитываются по ст. 15 и ст. 16 кодекса.", "ст."))
        assertEquals(listOf("Табліца дадзеных прыведзена", "на с. 245 другога тома."),
            cutAt("Табліца дадзеных прыведзена на с. 245 другога тома.", "с.", BeLayer))
        assertEquals(listOf("Делегация из Минска приехала", "в г. Казань рано утром."),
            cutAt("Делегация из Минска приехала в г. Казань рано утром.", "г."))
        assertEquals(listOf("Офіс компанії розташований", "на вул. Хрещатик, буд. 15."),
            cutAt("Офіс компанії розташований на вул. Хрещатик, буд. 15.", "вул.", UkLayer))
        // но ради длинной головы число от слова не отрываем («21 | книгу» — «двадцать один | книгу»)
        assertEquals(listOf("Данные и и 8 800 555 35 35", "21 книгу выдали читателям"),
            Splitter.sentences("Данные и и 8 800 555 35 35 21 книгу выдали читателям", Rules(maxLen = 30)))
    }

    @Test fun sequenceMatchesGolden() {
        val d = TestData.data()
        val g = TestData.golden()
        for (i in 0 until g.length()) {
            val o = g.getJSONObject(i)
            val exp = o.getJSONArray("ids").let { a -> LongArray(a.length()) { a.getLong(it) } }
            assertEquals(o.getString("text"), exp.toList(), d.sequence(o.getString("accented")).toList())
        }
    }
}
