// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.DictLines
import ru.kost.ruvoice.Pipeline
import ru.kost.ruvoice.TestData
import java.io.File

/** Украинский слой: таблица языков, базовый нормализатор, имена букв и знаков, перенос по дефису, вопросы. Текст идёт так,
 * как на телефоне: [Splitter.sentences] (или [Pipeline.plan]), потом [UkLayer.prepare] с символами пака. */
class UkLayerTest {
    private val cis = TestData.cisAllowed
    private fun prep(t: String, r: Rules = Rules()) = UkLayer.prepare(t, cis, r)
    private fun full(t: String, r: Rules = Rules()) = Splitter.sentences(t, r).joinToString(" | ") { prep(it, r) }
    private fun plan(t: String, r: Rules = Rules()) =
        Pipeline.plan(t, TestData.data(), 0, 0, rules = r, layer = UkLayer, allowed = cis).joinToString(" | ") { it.text }

    @Test fun tableAndLocale() {
        assertSame(UkLayer, Layers.of("uk"))
        assertSame(UkLayer, Layers.of("UK"))
        assertSame(UkLayer, Layers.ofIso3("ukr"))
        assertTrue(Layers.has("uk"))
        assertEquals(listOf("ukr", "UKR"), UkLayer.locale.let { listOf(it.isO3Language, it.isO3Country) })
        assertEquals(BeLayer.unsupported, UkLayer.unsupported)
    }

    @Test fun apostropheIsHardSign() {
        val out = full("П'ять м’ясних мʼятних!")
        for (w in listOf("пъять", "мъясних", "мъятних")) assertTrue(out, w in out)
        assertTrue(out, out.endsWith("."))
        // все написания апострофа; несколько подряд — один «ъ»
        for (a in listOf("'", "’", "`", "´", "ʼ", "‘", "ʼ`")) assertEquals(a, "мъясо", prep("м${a}ясо"))
        assertEquals("пъятниця, объєкт", prep("П'ятниця, об'єкт"))
        assertEquals("зъїзд, півъяблука", prep("З'їзд, пів'яблука"))
        // на краю слова и не перед «я ю є ї» — не «ъ», без следа
        assertEquals("слово", prep("'слово'"))
        assertEquals("пъятниця", prep("‘п’ятниця’"))
        assertEquals("оконнор", prep("О'Коннор"))
        // ударение над гласной после апострофа: «ъ» и «+»
        assertEquals("пъ+ять", prep("п'я́ть"))
        // апострофа в символах пака нет: до модели он не доходит
        assertFalse('\'' in cis)
    }

    @Test fun emptyAndOddInputDoNotFail() {
        // пусто, пробелы, одни знаки, эмодзи без таблицы — без букв и без исключения
        for (t in listOf("", "   ", "…", "😀", "...", "?!", "—", "''", "ʼ", "-", "", "́", "«»", "()"))
            assertTrue(t, full(t).none { it.isLetterOrDigit() })
        assertEquals("плюс", full("+"))
        // одна буква — имя
        assertEquals("ґ+е", plan("ґ"))
        assertEquals("+ї", plan("Ї"))
    }

    @Test fun emojiAndSmileys() {
        Emoji.uk = File(TestData.root(), "app/src/main/assets/emoji_uk.tsv").bufferedReader().useLines { Emoji(it) }
        try {
            assertEquals("привіт, широко всміхнене обличчя", full("Привіт 😀"))
            assertEquals("широко всміхнене обличчя", full("😀"))
            assertEquals("дякую, усміхається", full("Дякую :)"))
            assertEquals("дякую, усміхається", full("Дякую))"))
            // повтор подряд — один раз
            assertEquals("дякую, усміхається", full("Дякую :) :) :)"))
            assertEquals("смішно, сміється", full("Смішно :D"))
        } finally { Emoji.uk = null }
        // правило выключено — смайл без слов, эмодзи — фильтром
        assertEquals("привіт", full("Привіт 😀", Rules().with("emoji", false)))
    }

    @Test fun russianTextPassesAsIs() {
        // буквы из символов пака («ё», «ы», «э», «ъ») проходят, украинские правила русское слово не портят
        assertEquals("привет, это ёлка", prep("Привет, это ёлка"))
        assertEquals("съезд объявлен.", full("Съезд объявлен!"))
        for (c in "ёыэъ") assertTrue(c in cis)
    }

    @Test fun cardinalNumbers() {
        assertEquals("дві тисячі двадцять шість", prep("2026"))
        assertEquals("один мільйон", prep("1 000 000"))
        assertEquals("триста сорок пъять мільйонів шістсот сімдесят вісім тисяч девъятсот один", prep("345678901"))
        assertEquals("нуль", prep("0"))
        assertEquals("одна тисяча", prep("1000"))
        // от миллиарда — по цифрам, не падает
        assertEquals("один нуль нуль нуль нуль нуль нуль нуль нуль нуль", prep("1000000000"))
        // не-ASCII цифры
        assertEquals("дві тисячі двадцять шість", prep("٢٠٢٦"))
        // знак перед числом словом; «+» не перед гласной — не ударение
        assertEquals("плюс пъять, мінус двадцять", prep("+5, -20"))
        assertEquals("сі плюс плюс", prep("Сі++"))
        // буква вплотную к числу — отдельным словом; дефис между числами не минус
        assertEquals("вісім к сімдесят чотири, десять-пъятнадцять", prep("8к74, 10-15"))
        // ключ numbers выключен — цифры фильтр не пропускает
        assertEquals("", prep("2026", Rules().with("numbers", false)))
    }

    @Test fun accentMarksAndInvisibleChars() {
        // U+0301 и U+0300 над гласной — «+» перед ней; мягкий перенос и невидимые — без следа
        assertEquals("в+ихід", prep("ви́хід"))
        assertEquals("в+ихід", prep("вѝхід"))
        assertEquals("перенос слова", prep("пере­нос сло​ва‌‍⁠﻿"))
        // знак над согласной — без следа; символ частной области из текста — пробел, не точка
        assertEquals("слово", prep("сл́ово"))
        assertEquals("раз два", prep("раздва"))
        assertEquals("раз два", prep("раздва"))
    }

    @Test fun dashParensQuotes() {
        // Splitter делает из « - » «–», в символах пака только «—»
        assertEquals("київ — столиця", plan("Київ - столиця").let { prep(it) })
        assertEquals("київ — столиця", prep("Київ – столиця"))
        assertEquals("хто-небудь", prep("хто-небудь"))
        assertEquals("так, — сказав він.", prep("— «Так», — сказав він.").removePrefix("— "))
        assertEquals("книга, том два, тут", prep("Книга (том 2) тут"))
        assertEquals("книга том два тут", prep("Книга (том 2) тут", Rules().with("pause_parens", false)))
        assertEquals("газета день повідомила.", prep("Газета «День» повідомила."))
        assertEquals("що?", prep("Що?!"))
        assertEquals("що?", prep(Splitter.sentences("Що!?").single()))
    }

    @Test fun symbolNamesInFilter() {
        // без имён знаков (книги) — паузы; у чтеца (sr_symbols → symbol_names) — украинскими именами
        assertEquals("ось — там, а тут, далі", full("Ось → там, а тут · далі"))
        assertEquals("ось стрілка праворуч там, а тут крапка посередині далі", full("Ось → там, а тут · далі", Rules().screenReader()))
        assertEquals("пошта равлик", full("Пошта @", Rules().with("symbol_names", true)))
        // имя CLDR с апострофом и латиницей — тоже «ъ» и фильтр
        SymbolNames.cldrUk = SymbolNames.parse(File(TestData.root(), "app/src/main/assets/symbols_uk.tsv").readLines().asSequence())
        try {
            assertEquals("сто въєтнамський донг", full("100 ₫", Rules().with("symbol_names", true)))
            // «n» латиницей фильтр выкидывает (латиница — позже), дефис остаётся
            assertEquals("-арне объєднання", full("⋃", Rules().with("symbol_names", true)))
        } finally { SymbolNames.cldrUk = null }
    }

    @Test fun unsupportedKeysDoNotChangeOutput() {
        val samples = listOf("П'ять м’ясних мʼятних!", "Ось → там, а тут · далі", "2026 рік (так) — «добре»", "Чи прийдеш?",
            "Привет, это ёлка", "Сі++ і ±5 ≈ 6 & 7")
        assertFalse("accentor" in UkLayer.unsupported)
        for (k in UkLayer.unsupported) {
            val flipped = Rules().with(k, !Rules().on(k))
            for (t in samples) {
                assertEquals("$k: $t", full(t), full(t, flipped))
                assertEquals("$k, чтец: $t", full(t, Rules().screenReader()), full(t, flipped.screenReader()))
            }
        }
    }

    @Test fun letterAndSymbolNames() {
        assertEquals("ґ+е", UkLayer.letterName('ґ'))
        assertNotNull(UkLayer.letterName('Ї', lone = true))
        assertEquals("й+от", UkLayer.letterName('й'))
        assertEquals("м'як+ий знак", UkLayer.letterName('ь'))
        assertEquals("+ікс", UkLayer.letterName('x'))
        // имя мягкого знака до модели — с «ъ» и ударением
        assertEquals("мъяк+ий знак", prep(plan("ь")))
        assertEquals("кома", UkLayer.symbolName(',', punct = true))
        assertEquals("відкривні лапки", UkLayer.symbolName('«', punct = true))
        // без эха пунктуация не называется — как у русского и белорусского
        assertEquals(null, UkLayer.symbolName(','))
        assertEquals("решітка", UkLayer.symbolName('#'))
        assertEquals("удаление, кома", plan("Удаление ,", Rules().with("letter_echo_all", true)))
        // эхо ввода и одна буква — украинским именем, не русским «эл»
        assertEquals("удаление, +ел, заглавная", plan("Удаление заглавная Л", Rules().with("letter_echo_all", true)))
        assertEquals("+ел", plan("л"))
        // русский — как был
        assertEquals("запятая", RuLayer.symbolName(',', punct = true))
    }

    @Test fun keepHyphen() {
        for ((a, b) in listOf("будь" to "хто", "по" to "українськи", "хтозна" to "що", "по" to "моєму", "по" to "німецьки",
            "по" to "перше", "по" to "п'яте", "з" to "за", "з" to "під", "аби" to "хто", "казна" to "де", "хто" to "небудь",
            "як" to "от", "все" to "таки"))
            assertTrue("$a-$b", UkLayer.keepHyphen(a, b))
        for ((a, b) in listOf("ха" to "та", "по" to "милки", "по" to "купки", "аби" to "який", "з" to "вук", "ри" to "ба"))
            assertFalse("$a-$b", UkLayer.keepHyphen(a, b))
        // через Splitter: перенос склеивается, дефисное написание остаётся; слово с апострофом целиком
        fun join(t: String) = Splitter.paragraphs(t, Rules(), UkLayer).single()
        assertEquals("по-українськи", join("по-\nукраїнськи"))
        assertEquals("по-п'яте", join("по-\nп'яте"))
        assertEquals("помилки", join("по-\nмилки"))
        assertEquals("Ґанок їжака", join("Ґа-\nнок їжа-\nка"))
        assertEquals("пʼятниця", join("пʼят-\nниця"))
    }

    @Test fun questions() {
        val q = UkLayer.questions(TestData.data())
        fun t(s: String) = SentenceType.classify(s, q)
        assertEquals("wh_q", t("Хто прийшов?"))
        assertEquals("general_q", t("Чи прийдеш?"))
        assertEquals("alternative_q", t("Чай чи каву?"))
        assertEquals("tag_q", t("Ти прийдеш, чи не так?"))
        // «є», «ї» внутри вопросительного слова
        assertEquals("wh_q", t("Чиє це пальто?"))
        assertEquals("wh_q", t("Чиї це речі?"))
        assertEquals("wh_q", t("Звідки ти?"))
        // «чи» после тире, запятой и вводного — частица общего вопроса
        assertEquals("general_q", t("— Чи прийдеш?"))
        assertEquals("general_q", t("Скажи, чи прийдеш?"))
        assertEquals("general_q", t("А чи знаєш?"))
        assertEquals("general_q", t("Ну а чи прийдеш?"))
        assertEquals("alternative_q", t("Так чи ні?"))
        assertEquals("alternative_q", t("Підемо пішки або поїдемо?"))
        assertEquals("alternative_q", t("Чи будеш чай чи каву?"))
        // хвост — только в конце после запятой
        assertEquals("tag_q", t("Ти прийдеш, правда?"))
        assertEquals("general_q", t("Це правда?"))
        // «будь ласка» — не «як»; «що», «як» после глагола и запятой — союз; «Скажи, як…» — вопрос
        assertEquals("general_q", t("Ти прийдеш, будь ласка?"))
        assertEquals("wh_q", t("Скажіть, будь ласка, як пройти до вокзалу?"))
        assertEquals("general_q", t("Ти знаєш, що він прийде?"))
        assertEquals("general_q", t("Ви чули, як він співає?"))
        assertEquals("general_q", t("Чи знаєш ти, що завтра свято?"))
        assertEquals("wh_q", t("Скажи, як пройти?"))
        assertEquals("st", t("Я знаю, що він прийде."))
        // русский — свои списки
        assertEquals("alternative_q", SentenceType.classify("Чай или кофе?", RuLayer.questions(TestData.data())))
        assertTrue("где" !in q.whForms)
    }

    @Test fun questionFocusSkipsVerbs() {
        val q = UkLayer.questions(TestData.data())
        fun focus(t: String) = Marks.question(t, null, q::verb)
        for (t in listOf("Ти прийдеш?", "Ви знаєте?", "Ти бачив його?", "Вона сказала правду?", "Будемо?", "Підемо в кіно?",
            "Ти можеш допомогти?", "Вони живуть тут?", "Ви зустрілися?"))
            assertEquals(t, t, focus(t))
        for ((t, last) in listOf("Це правда?" to "правда", "Ти вдома?" to "вдома", "Це ваш син?" to "син", "Хліб свіжий?" to "свіжий",
            "Сьогодні понеділок?" to "понеділок", "Це твоя мати?" to "мати"))
            assertEquals(t, t.replace("$last?", "*$last*?"), focus(t))
    }

    @Test fun notWhLongFillersNoStackOverflow() {
        assertEquals(" ", UkLayer.questions(TestData.data()).notWhRe!!.replace("ну ".repeat(5000) + "чи?", " "))
    }

    @Test fun sharedLetterClasses() {
        // Splitter: инициал «Є.», «Ґ.» — не конец предложения; мягкий перенос строки перед «ї»
        assertEquals(1, Splitter.sentences("Автор Є. Ґудзь пише.").size)
        assertEquals(1, Splitter.sentences("Пише Ґ. Їжак.").size)
        assertEquals("сказав їжак", Splitter.paragraphs("сказав\nїжак", Rules(), UkLayer).single())
        // DictLines: слово с «є», «ї», «ґ»
        assertEquals(0 until 5, DictLines.wordRangeAt("ґанок їжака", 2))
        assertEquals(6 until 11, DictLines.wordRangeAt("ґанок їжака", 8))
        assertEquals(0 until 3, DictLines.wordRangeAt("моє", 3))
        // Stress: «є», «ї» — гласные (ударение не на последнем слоге — точка остаётся), «ґ» — согласная короткого слова
        val stress = Stress(TestData.data(), object : StressModels {
            override fun accentor(words: List<String>) = Pair(Array(words.size) { FloatArray(0) }, Array(words.size) { FloatArray(0) })
            override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray) = FloatArray(0)
        }, emptyMap(), Rules(), layer = UkLayer)
        assertEquals("тут зн+ає." to false, stress.modelEnd("тут зн+ає."))
        assertEquals("тут кр+аї." to false, stress.modelEnd("тут кр+аї."))
        assertEquals("ґ+ее.", stress.stretchShort("ґ+е."))
    }
}
