package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
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

    @Test fun okIsNotAnAbbreviationStopWord() {
        // review t17 п.6 (Splitter.kt:10): «ок» — разговорное слово в диалогах, не сокращение.
        assertEquals(listOf("Хорошо, ок.", "Идём дальше."), Splitter.sentences("Хорошо, ок. Идём дальше."))
    }

    @Test fun longSentenceSplitsAtComma() {
        val long = (1..30).joinToString(", ") { "слово$it" } + "."
        val parts = Splitter.sentences(long, Rules(maxLen = 80))
        assert(parts.all { it.length <= 80 }) { parts }
        assertEquals(long.replace(",", ""), parts.joinToString(" ").replace(",", "").replace("  ", " "))
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
