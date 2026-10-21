// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.Pipeline
import ru.kost.ruvoice.TestData

/** Таблица языков: слой берётся по коду из pack.json, русский — умолчание. */
class LayersTest {

    @Test fun tableByCode() {
        assertSame(RuLayer, Layers.of("ru"))
        assertSame(BeLayer, Layers.of("be"))
        assertSame(BeLayer, Layers.of("BE"))          // регистр кода пака не важен
        assertSame(RuLayer, Layers.of(null))           // пак без lang — русский
        assertSame(RuLayer, Layers.of("kk"))           // язык, которого в таблице пока нет
        assertSame(RuLayer, Layers.RU)
        assertTrue(Layers.has("be")); assertTrue(!Layers.has("kk"))
    }

    @Test fun localesForTtsApi() {
        // ISO-3 страны — из Locale: у Беларуси BLR (BEL — Бельгия)
        assertEquals(listOf("rus", "RUS"), RuLayer.locale.let { listOf(it.isO3Language, it.isO3Country) })
        assertEquals(listOf("bel", "BLR"), BeLayer.locale.let { listOf(it.isO3Language, it.isO3Country) })
        assertSame(BeLayer, Layers.ofIso3("bel"))
        assertSame(RuLayer, Layers.ofIso3("rus"))
        assertEquals(null, Layers.ofIso3("kaz"))
        assertEquals(null, Layers.ofIso3(null))
    }

    @Test fun belPrepare() {
        // числа словами, латиница транслитом, фильтр по символам модели
        assertEquals("дзве тысячы дваццаць шэсць", BeLayer.prepare("2026", cis, Rules()))
        // «!» и апостроф в модель не идут (служебные символы cis-пака); разделительный апостроф во всех написаниях — «ъ»,
        // как в русском «подъезд» (слушатель: «абём» звучало мягко), прочий — без следа
        assertEquals("прывъет.", BeLayer.prepare("Прыв'ет!", cis, Rules()))
        for (a in listOf("'", "’", "`", "´", "ʼ", "‘")) assertEquals("абъява", BeLayer.prepare("аб${a}ява", cis, Rules()))
        assertEquals("слова", BeLayer.prepare("'слова'", cis, Rules()))
        // «?!» — вопрос, как у русского cis («что?»), а не «?.»
        assertEquals("што?", BeLayer.prepare("Што?!", cis, Rules()))
        assertEquals("што?", BeLayer.prepare(Splitter.sentences("Што!?").single(), cis, Rules()))
    }

    @Test fun belQuestions() {
        val d = TestData.data()
        val lists = BeLayer.questions(d)
        assertTrue("хто" in lists.whForms)
        assertEquals("wh_q", SentenceType.classify("Дзе ты быў?", lists))
        assertEquals("general_q", SentenceType.classify("Ты прыйдзеш заўтра?", lists))
        assertEquals("exclam", SentenceType.classify("Паспеем да цягніка!", lists))
        assertEquals("st", SentenceType.classify("Сёння 2026 год.", lists))
        // правило выключено — тип не считается (так же, как у русского)
        assertEquals("st", SentenceType.classify("Дзе ты быў?", lists, Rules(setOf("intonation"))))
    }

    private val cis = TestData.cisAllowed

    @Test fun belAccentMarksAndInvisibleChars() {
        // знак ударения над гласной («Книга с ударениями» пишет U+0301) — «+» перед ней, а не пробел посреди слова
        assertEquals("дз+якуй", BeLayer.prepare("дзя́куй", cis, Rules()))
        assertEquals("дз+якуй", BeLayer.prepare("дзя̀куй", cis, Rules()))
        // мягкий перенос, ZWSP/ZWJ/ZWNJ/WJ/BOM — без следа; знак над согласной — тоже
        assertEquals("перанос слова", BeLayer.prepare("пера­нос сло​ва‌‍⁠﻿", cis, Rules()))
        assertEquals("дзякуй", BeLayer.prepare("дз́якуй", cis, Rules()))
    }

    @Test fun belDashReachesModel() {
        // Splitter делает из « - » «–», в символах cis только «—»: на нём пауза тире (dashPauseMs)
        val d = TestData.data()
        val seg = Pipeline.plan("Мінск - сталіца", d, 0, 0, layer = BeLayer, allowed = cis).single().text
        assertEquals("мінск — сталіца", BeLayer.prepare(seg, cis, Rules()))
        assertEquals("мінск — сталіца", BeLayer.prepare("Мінск — сталіца", cis, Rules()))
        assertEquals("хто-небудзь", BeLayer.prepare("хто-небудзь", cis, Rules()))
        val pack = org.json.JSONObject(java.io.File(TestData.root(), "app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack")
        assertTrue('—' in ru.kost.ruvoice.Symbols.fromJson(pack).symbolToId)
    }

    @Test fun belQuotesWithoutStrayCommas() {
        assertEquals("так, — сказаў ён.", BeLayer.prepare("\"Так\", — сказаў ён.", cis, Rules()))
        assertEquals("так. — сказаў ён.", BeLayer.prepare("«Так!» — сказаў ён.", cis, Rules()))
        assertEquals("ён сказаў: так.", BeLayer.prepare("Ён сказаў: «Так».", cis, Rules()))
        assertEquals("кніга, том два, тут", BeLayer.prepare("Кніга (том 2) тут", cis, Rules()))
        // после тире запятая от кавычки или скобки не нужна
        assertEquals("адказ адзін — не.", BeLayer.prepare("Адказ адзін — «не».", cis, Rules()))
        assertEquals("— так, — сказаў ён.", BeLayer.prepare("— «Так», — сказаў ён.", cis, Rules()))
        assertEquals("— ціха, так.", BeLayer.prepare("— (ціха) Так.", cis, Rules()))
        assertEquals("сказаў: так.", BeLayer.prepare("Сказаў: «так».", cis, Rules()))
        // кавычки посреди фразы — без паузы, как у русского («газета сообщила»); скобки — запятая по pause_parens
        assertEquals("газета звязда паведаміла.", BeLayer.prepare("Газета «Звязда» паведаміла.", cis, Rules()))
        assertEquals("газета звязда паведаміла.", BeLayer.prepare("Газета \"Звязда\" паведаміла.", cis, Rules()))
        assertEquals("газета сообщила.", RuLayer.prepare("«Газета» сообщила.", cis, Rules()))
        assertEquals("кніга том два тут", BeLayer.prepare("Кніга (том 2) тут", cis, Rules(setOf("pause_parens"))))
    }

    @Test fun belQuestionWords() {
        val q = BeLayer.questions(TestData.data())
        // «і», «ў» внутри слова: «калі», «колькі», «чыі»
        assertEquals("wh_q", SentenceType.classify("Калі ты вернешся?", q))
        assertEquals("wh_q", SentenceType.classify("Колькі гэта каштуе?", q))
        assertEquals("wh_q", SentenceType.classify("Чыі гэта рэчы?", q))
        // «ё» нормализуется в «е» — и в списках тоже
        assertEquals("wh_q", SentenceType.classify("Чыё гэта паліто?", q))
        assertEquals("tag_q", SentenceType.classify("Ты прыйдзеш, ня праўда?", q))
        assertEquals("tag_q", SentenceType.classify("Добра атрымалася, ці не так?", q))
        // союз альтернативы — белорусский; «ці» в начале — частица общего вопроса
        assertEquals("alternative_q", SentenceType.classify("Вам чай ці каву?", q))
        assertEquals("alternative_q", SentenceType.classify("Пойдзем пешшу або паедзем?", q))
        assertEquals("general_q", SentenceType.classify("Ці прыйдзеш ты заўтра?", q))
        assertEquals("general_q", SentenceType.classify("Вам чай или каву?", q))
        // «ці» после тире и запятой — тоже частица, не альтернатива
        assertEquals("general_q", SentenceType.classify("— Ці прыйдзеш?", q))
        assertEquals("general_q", SentenceType.classify("Скажы, ці прыйдзеш?", q))
        assertEquals("alternative_q", SentenceType.classify("Чай ці каву?", q))
        // хвост — только в конце после запятой; нормативные «праўда», «не праўда»
        assertEquals("general_q", SentenceType.classify("Ты так жа думаеш?", q))
        assertEquals("general_q", SentenceType.classify("Гэта праўда?", q))
        assertEquals("general_q", SentenceType.classify("Гэта не праўда?", q))
        assertEquals("tag_q", SentenceType.classify("Ты прыйдзеш, праўда?", q))
        assertEquals("tag_q", SentenceType.classify("Ты прыйдзеш, не праўда?", q))
        assertEquals("tag_q", SentenceType.classify("Ты прыйдзеш, так жа?", q))
        // русский союз остаётся у русского
        assertEquals("alternative_q", SentenceType.classify("Чай или кофе?", RuLayer.questions(TestData.data())))
        // «калі ласка» — не вопросительное слово; «ці» в начале (и после вводных) — общий вопрос, «што» дальше — придаточное
        assertEquals("general_q", SentenceType.classify("Ці прыйдзеш, калі ласка?", q))
        assertEquals("general_q", SentenceType.classify("Ты прыйдзеш, калі ласка?", q))
        assertEquals("general_q", SentenceType.classify("Калі ласка, ці можаце вы дапамагчы?", q))
        assertEquals("general_q", SentenceType.classify("Ці ведаеш ты, што заўтра свята?", q))
        assertEquals("general_q", SentenceType.classify("А ці ведаеш?", q))
        assertEquals("general_q", SentenceType.classify("Ну ці прыйдзеш?", q))
        assertEquals("general_q", SentenceType.classify("— Ну а ці прыйдзеш?", q))
        assertEquals("wh_q", SentenceType.classify("Скажыце, калі ласка, як прайсці да вакзала?", q))
        assertEquals("wh_q", SentenceType.classify("Калі ты прыйдзеш?", q))
        // альтернатива после начального «ці» и «так ці не» — по-прежнему альтернатива
        assertEquals("alternative_q", SentenceType.classify("Ці будзеш чай ці каву?", q))
        assertEquals("alternative_q", SentenceType.classify("Так ці не?", q))
        assertEquals("alternative_q", SentenceType.classify("Проста ці складана?", q))
    }

    @Test fun belQuestionFocusSkipsVerbs() {
        val d = TestData.data()
        val q = BeLayer.questions(d)
        fun focus(t: String) = Marks.question(t, null, q::verb)
        // с глаголом — без метки: подъём модель и так ставит на глагол
        for (t in listOf("Ты ідзеш?", "Ты прыйдзеш заўтра?", "Вы былі ў Мінску?", "Ты чуў?", "Яна сказала праўду?", "Вы ведаеце?",
            "Ты можаш дапамагчы?", "Ён працуе тут?", "Будзем?", "Пойдзем у кіно?", "Ці ёсць у вас кава?", "Вы сустрэліся?",
            "Ён вучыцца?", "Пайшлі?", "Купіце хлеба?", "Яны жывуць тут?", "Ты бачыў яго?", "Ён прыйшоў?"))
            assertEquals(t, t, focus(t))
        // без глагола — метка на последнем слове; существительные, прилагательные, местоимения глаголом не считаются
        for ((t, last) in listOf("Гэта праўда?" to "праўда", "Вы з Мінска?" to "Мінска", "Ты дома?" to "дома",
            "Гэта ваш сын?" to "сын", "Уся сям'я тут?" to "тут", "Новая кніга?" to "кніга", "Ты з намі?" to "намі",
            "Кава з малаком?" to "малаком", "Сёння панядзелак?" to "панядзелак", "Ваша маці настаўніца?" to "настаўніца",
            "Хлеб свежы?" to "свежы", "Гэта ўсё?" to "ўсё", "Зноў дождж?" to "дождж", "Гэта яе кніжка?" to "кніжка",
            "Гэта наш дом?" to "дом", "Табе холадна?" to "холадна", "Хоць адзін?" to "адзін", "Твая памяць?" to "памяць"))
            assertEquals(t, t.replace("$last?", "*$last*?"), focus(t))
        // русский — свой verbLike
        assertEquals("Ты придёшь завтра?", Marks.question("Ты придёшь завтра?", null, RuLayer.questions(d)::verb))
        assertEquals("Ты *ідзеш*?", Marks.question("Ты ідзеш?", null, RuLayer.questions(d)::verb))
    }

    @Test fun belDehyphen() {
        fun join(t: String, layer: TextLayer = BeLayer) = Splitter.paragraphs(t, Rules(), layer).single()
        // дефисные написания — по GrammarDB
        for (w in listOf("па-беларуску", "па-нямецку", "па-мойму", "па-новаму", "па-першае", "па-другое", "па-чалавечы", "з-за",
            "з-пад", "хто-небудзь", "што-колечы", "дзе-кольвек", "усё-такі", "абы-хто", "абы-як"))
            assertEquals(w, w, join(w.replaceFirst("-", "-\n")))
        // перенос: «па», «абы», «за» перед словом — не приставка с дефисом; «і», «ў» — буквы слова
        for (t in listOf("па-\nмяць", "па-\nчатак", "абы-\nякавы", "за-\nўтра", "рэч-\nка", "ха-\nта", "Ві-\nцебск", "ўз-\nровень"))
            assertEquals(t, t.replace("-\n", ""), join(t))
        assertEquals("Санкт-Пецярбург", join("Санкт-\nПецярбург"))
        // слой доходит от Pipeline.plan
        assertEquals("па-беларуску", Pipeline.plan("па-\nбеларуску", TestData.data(), 0, 0, layer = BeLayer, allowed = cis).single().text)
        // русский — свои списки: «по-русски», а «па-\nмять» склеивается
        assertEquals("по-русски", join("по-\nрусски", RuLayer))
        assertEquals("память", join("па-\nмять", RuLayer))
    }

    @Test fun belNotWhLongFillersNoStackOverflow() {
        // повтор вводных перед «ці» — притяжательный: на JVM без него StackOverflowError
        assertEquals(" ", BeLayer.questions(TestData.data()).notWhRe!!.replace("ну ".repeat(5000) + "ці?", " "))
    }

    @Test fun belLetterNames() {
        val d = TestData.data()
        fun first(t: String) = Pipeline.plan(t, d, 0, 0, layer = BeLayer, allowed = cis).single().text
        assertEquals("+эль", first("л"))
        assertEquals("+у нескладовае", first("Ў"))
        assertEquals("+і нескладовае", first("й"))
        assertEquals("м+яккі знак", first("ь"))
        assertEquals("б+э", first("b"))
        assertEquals("прописная буква +эр", first("прописная буква Р."))
        // эхо ввода — тоже белорусским именем
        assertEquals("удаление, +эль, заглавная", Pipeline.plan("Удаление заглавная Л", d, 0, 0, rules = Rules().with("letter_echo_all", true), layer = BeLayer).single().text)
        // русский голос — как был
        assertEquals("+эл.", Pipeline.plan("л", d, 0, 0).single().text)
        assertEquals("удаление, +эл, заглавная", Pipeline.plan("Удаление заглавная Л", d, 0, 0, rules = Rules().with("letter_echo_all", true)).single().text)
    }

    @Test fun belSymbolNames() {
        val d = TestData.data()
        val echo = Rules().with("letter_echo_all", true)
        fun first(t: String, rules: Rules = Rules()) = Pipeline.plan(t, d, 0, 0, rules = rules, layer = BeLayer, allowed = cis).single().text
        // эхо удаления — белорусскими именами (формы не проверены носителем)
        assertEquals("клічнік", BeLayer.symbolName('!', punct = true))
        assertEquals("кропка з коскай", BeLayer.symbolName(';', punct = true))
        assertEquals("адкрываецца дужка", BeLayer.symbolName('(', punct = true))
        assertEquals("закрываецца двукоссе", BeLayer.symbolName('»', punct = true))
        assertEquals("кароткі працяжнік", BeLayer.symbolName('–', punct = true))
        assertEquals("удаление, коска", first("Удаление ,", echo))
        assertEquals("удаление, адкрываецца двукоссе", first("Удаление «", echo))
        // без эха обычная пунктуация не называется — как у русского
        assertEquals(null, BeLayer.symbolName('!'))
        assertEquals(null, BeLayer.symbolName(','))
        // апостроф «двукроп'е», «шматкроп'е» снимает BelNormalizer — слово целое
        assertEquals("двукропъе", BeLayer.prepare(BeLayer.symbolName(':', punct = true)!!, cis, Rules()))
        assertEquals("шматкропъе", BeLayer.prepare(BeLayer.symbolName('…', punct = true)!!, cis, Rules()))
        // прочие знаки — белорусскими именами, не русскими
        assertEquals("рашотка", BeLayer.symbolName('#'))
        assertEquals("сабака", BeLayer.symbolName('@', punct = true))
        // русский — как был: в эхе своя пунктуация, иначе SymbolNames
        assertEquals("запятая", RuLayer.symbolName(',', punct = true))
        assertEquals(null, RuLayer.symbolName(',', punct = false))
        assertEquals("решётка", RuLayer.symbolName('#'))
    }

    @Test fun ruQuestionsAreItsOwn() {
        val d = TestData.data()
        assertEquals("wh_q", SentenceType.classify("Где ты был?", RuLayer.questions(d)))
        assertTrue("где" in RuLayer.questions(d).whForms)
        assertTrue("где" !in BeLayer.questions(d).whForms)
    }
}
