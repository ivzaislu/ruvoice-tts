package ru.kost.ruvoice.text

import org.junit.Assert.*
import org.junit.Test
import ru.kost.ruvoice.TestData

class ReplacementsTest {
    @Test fun wholeWordCaseInsensitiveLiteralReplacement() {
        val r = Replacements.parse(listOf("Гарри = Г+арри"))
        assertEquals("Г+арри и Г+арри!", r.apply("Гарри и гарри!"))
    }

    /** Неразрывный и узкие пробелы (книги, вставка в «Разбор»: «в\u00a0кому») матчат пробел ключа; в тексте они остаются. */
    @Test fun unicodeSpacesMatchKeySpace() {
        val r = Replacements.parse(listOf("в кому = в к+ому", "старый замок = старый з+амок", "\$Эмма Ватсон = Эмма В+атсон",
            "обливаясь пот* = обливаясь п+от*", "да, ну = д+а, н+у"))
        assertEquals("впала в\u00a0к+ому.", r.apply("впала в\u00a0кому."))
        assertEquals("впала в\u202fк+ому", r.apply("впала в\u202fкому"))
        assertEquals("Старый\u2009з+амок", r.apply("Старый\u2009замок"))
        assertEquals("Эмма\u00a0В+атсон", r.apply("Эмма\u00a0Ватсон"))
        assertEquals("обливаясь п+отом", r.apply("обливаясь\u00a0потом"))
        assertEquals("д+а, н+у", r.apply("да,\u00a0ну"))
    }

    @Test fun systemVowelInterjectionDoubled() {
        val line = java.io.File(TestData.root(), "app/src/main/assets/dicts/replace/Системный.txt").readLines().single { it.startsWith("~(?<![\\p{L}+-])([аоэу])") }
        val r = Replacements.parse(listOf(line))
        assertEquals("АА! Ну ОО? а ты, а он, ах! на!", r.apply("А! Ну О? а ты, а он, ах! на!"))
    }

    @Test fun systemReduplicationBeforeA() {
        val line = java.io.File(TestData.root(), "app/src/main/assets/dicts/replace/Системный.txt").readLines().single { it.startsWith("~(?<![\\p{L}+-])(\\p{L}+)-") }
        val r = Replacements.parse(listOf(line))
        assertEquals("Что что, а драться он умел. Кто кто, а он знал. Кое-что, а чуть-чуть и что-то.", r.apply("Что-что, а драться он умел. Кто-кто, а он знал. Кое-что, а чуть-чуть и что-то."))
    }

    /** Отчёт пользователя 29.09.2026: фразы и первые части сложных слов из системного словаря замен. */
    @Test fun systemUserReportPhrases() {
        val r = Replacements.parse(java.io.File(TestData.root(), "app/src/main/assets/dicts/replace/Системный.txt").readLines())
        assertEquals("можно и ед+у из других", r.apply("можно и еду из других"))
        assertEquals("+еду из города", r.apply("еду из города"))
        assertEquals("глаза заливало п+отом.", r.apply("глаза заливало потом."))
        assertEquals("на моём берег+у", r.apply("на моём берегу"))
        assertEquals("всё съ+ем", r.apply("всё съем"))
        assertEquals("б+ело-голубая", r.apply("бело-голубая"))
        assertEquals("св+етло-серый", r.apply("светло-серый"))
        assertEquals("распад +эс+эсэс+эр.", r.apply("распад СССР."))
        // 21.10.2026
        assertEquals("Солдаты в стро+ю.", r.apply("Солдаты в строю."))
        assertEquals("Ему х+оду нет.", r.apply("Ему ходу нет."))
        assertEquals("кр+асна д+евица", r.apply("красна девица"))
        assertEquals("царьб+атюшка и царьб+атюшку", r.apply("царь-батюшка и царь-батюшку"))
        assertEquals("служил в двух полк+ах", r.apply("служил в двух полках"))
        assertEquals("На улице сто+им.", r.apply("На улице стоим."))
        assertEquals("ком+уто", r.apply("кому-то"))
        assertEquals("Он ч+уть ч+уть опоздал, почем+у то.", r.apply("Он чуть-чуть опоздал, почему-то."))
        assertEquals("почем+унибудь", r.apply("почему-нибудь"))
        assertEquals("ч+уть ч+уть душ+и", r.apply("чуть-чуть души"))
        // 30.09.2026: ударный предлог и частица, исключения длиннее голой пары
        assertEquals("жаловаться н+еначто", r.apply("жаловаться не на что"))
        assertEquals("надел очки н+анос и ушёл на н+ос корабл+я", r.apply("надел очки на нос и ушёл на нос корабля"))
        // 10.10.2026: «конским потом», «совершенный» в прилагательном; причастие оставляем модели
        assertEquals("разило конским п+отом.", r.apply("разило конским потом."))
        assertEquals("Более соверш+енного метода нет, это соверш+енный вид.", r.apply("Более совершенного метода нет, это совершенный вид."))
        assertEquals("преступление, совершенное им", r.apply("преступление, совершенное им"))
    }

    @Test fun phraseWithStressOnNeighbourWord() {
        val r = Replacements.parse(listOf("старый замок = старый з+амок"))
        assertEquals("Старый з+амок стоял.", r.apply("Старый замок стоял."))
    }

    @Test fun emptyReplacementDeletesKeyAndCollapsesSpaces() {
        val r = Replacements.parse(listOf("[1] ="))
        assertEquals("текст дальше", r.apply("текст[1] дальше"))
    }

    @Test fun deletesWholeWordOnly() {
        val r = Replacements.parse(listOf("мама = "))
        assertEquals("мамаша", r.apply("мамаша"))
    }

    @Test fun longerKeyWinsOverShorter() {
        val r = Replacements.parse(listOf("кот = к+от", "кот в сапогах = к+от в сапог+ах"))
        assertEquals("к+от в сапог+ах", r.apply("кот в сапогах"))
    }

    @Test fun ignoresCommentsAndLinesWithoutEquals() {
        val r = Replacements.parse(listOf("# коммент", "без равенства", "", "кот = к+от"))
        assertEquals("к+от", r.apply("кот"))
    }

    @Test fun regexRuleWithBackreferences() {
        val r = Replacements.parse(listOf("""~(\d+)-(\d+) = $1 по $2"""))
        assertEquals("стр. 5 по 7 и 10 по 12", r.apply("стр. 5-7 и 10-12"))
    }

    @Test fun brokenRegexRuleIsSkippedSilently() {
        val r = Replacements.parse(listOf("~[( = что-то", "кот = к+от"))
        assertEquals("к+от", r.apply("кот"))
    }

    @Test fun skipMarkerActsAsEmptyReplacement() {
        val r = Replacements.parse(listOf("Автор = {skip}"))
        assertEquals("Глава 1", r.apply("Глава 1 Автор"))
    }

    @Test fun regexRuleWithMissingGroupDoesNotCrashOtherRules() {
        // $2 при одной группе — на подстановке вылетает IndexOutOfBoundsException, правило
        // должно быть пропущено (текст этого куска остаётся как есть), но не ронять apply()
        val r = Replacements.parse(listOf("""~(\d+) = $2""", "кот = к+от"))
        assertEquals("5 к+от", r.apply("5 кот"))
    }

    @Test fun regexRuleWithLoneDollarDoesNotCrashOtherRules() {
        // одинокий $ в замене — IllegalArgumentException на подстановке, правило пропускается
        val r = Replacements.parse(listOf("""~цена(\d+) = $ руб.""", "кот = к+от"))
        assertEquals("цена100 к+от", r.apply("цена100 кот"))
    }
}

class ReplacementsRegexTest {
    @Test fun regexKeyMayContainEqualsInsideLookaround() {
        // разделитель для regex-строк — « = » с пробелами, «=» внутри (?<=…) строку не рвёт
        val r = Replacements.parse(listOf("""~(?<=\d)=(?=\d) = равно"""))
        assertEquals("2равно2", r.apply("2=2"))
        assertEquals("(?<=\\d)=(?=\\d)" to "равно", Replacements.split("""~(?<=\d)=(?=\d) = равно""")?.let { it.first.removePrefix("~") to it.second })
    }

    @Test fun regexLineWithoutSpacedSeparatorSplitsAtFirstEquals() {
        assertEquals("~\\d+" to "число", Replacements.split("~\\d+=число"))
    }

    @Test fun inlineFlagTurnsCaseSensitivityOn() {
        val r = Replacements.parse(listOf("~(?-i)Бог = Б+ог"))
        assertEquals("Б+ог и бог", r.apply("Бог и бог"))
    }

    @Test fun patternErrorReportsBrokenRegexOnly() {
        assertNotNull(Replacements.patternError("[("))
        assertNull(Replacements.patternError("""(\d+)\s*шт\.?"""))
    }

    @Test fun replacementErrorCatchesWhatWouldFailAtSubstitution() {
        assertNotNull(Replacements.replacementError("""(\d+)""", "$2"))
        assertNull(Replacements.replacementError("""(\d+)""", "$1 и $0"))
        assertNotNull(Replacements.replacementError("x", "$ руб."))
        assertNull(Replacements.replacementError("x", "\\$ руб."))
        assertNotNull(Replacements.replacementError("x", "хвост\\"))
        assertNull(Replacements.replacementError("[(", "$1"))
    }

    /** Длинная фраза перекрывает короткую: правила отсортированы по убыванию длины ключа,
     * а после первой замены в тексте стоит «+», и короткий ключ своё вхождение уже не находит. */
    @Test fun longerPhraseWins() {
        val r = Replacements.parse(listOf("стены и = ст+ены и", "каменной стены и = каменной стен+ы и"))
        assertEquals("вдоль каменной стен+ы и дальше", r.apply("вдоль каменной стены и дальше"))
        assertEquals("сложены ст+ены и крыша", r.apply("сложены стены и крыша"))
    }
    /** Жалобы 28.10.2026: фразы-подсказки для омографов из системного словаря замен. */
    @Test fun systemComplaintPhrasesOct28() {
        val r = Replacements.parse(java.io.File(TestData.root(), "app/src/main/assets/dicts/replace/Системный.txt").readLines())
        assertEquals("день прошел в сует+е.", r.apply("день прошел в суете."))
        assertEquals("зачем вы суете нос?", r.apply("зачем вы суете нос?"))
        assertEquals("пог+оню организовать", r.apply("погоню организовать"))
        assertEquals("глаз+а не опускает", r.apply("глаза не опускает"))
        assertEquals("плеснула вин+а в кубок", r.apply("плеснула вина в кубок"))
        assertEquals("у китай-г+орода", r.apply("у китай-города"))
        assertEquals("как с д+ыбы сняли?", r.apply("как с дыбы сняли?"))
        assertEquals("встал на дыбы", r.apply("встал на дыбы"))
        assertEquals("под корень извест+и", r.apply("под корень извести"))
        assertEquals("ват+ажке не чета", r.apply("ватажке не чета"))
        assertEquals("расс+орить их", r.apply("рассорить их"))
        assertEquals("вы меня тор+опите", r.apply("вы меня торопите"))
        assertEquals("по обе ст+ороны дороги", r.apply("по обе стороны дороги"))
        // «-ого» модель читает [ово]: «настрово»; по образцу «дорого» — «-ога»
        assertEquals("ему стр+ого-н+астрога запретили", r.apply("ему строго-настрого запретили"))
    }
}
