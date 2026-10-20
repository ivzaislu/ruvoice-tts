package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.DictCache
import ru.kost.ruvoice.Pipeline
import ru.kost.ruvoice.TestData
import java.io.File
import java.security.MessageDigest
import java.util.stream.Collectors

/**
 * Страховка русского пути на время правок белорусского слоя: вывод RuLayer.prepare и полного пути куска
 * (Pipeline.plan → Pipeline.accent → Stress, системные словари) на наборе текстов при разных правилах сверяется
 * с эталоном ru_snapshot.txt («вариант<TAB>sha256»). Эталон снят до правок; перезапись —
 * ./gradlew :app:testFullDebugUnitTest --tests '*RuPathSnapshotTest*' -Druvoice.snapshot.write=true
 */
class RuPathSnapshotTest {
    private val root = TestData.root()
    private val d = TestData.data()
    private fun asset(p: String) = File(root, "app/src/main/assets/$p")
    private val snapshot = File(root, "app/src/test/resources/ru_snapshot.txt")

    /** Акцентор — ударение на последнюю гласную, без «ё»; омограф — по чётности длины: вывод зависит от того,
     * какие слова дошли до моделей. */
    private val models = object : StressModels {
        override fun accentor(words: List<String>) = Pair(
            Array(words.size) { i -> FloatArray(10).also { it[(words[i].count { c -> c in "аоуыэиеяёю" } - 1).coerceIn(0, 9)] = 1f } },
            Array(words.size) { FloatArray(7) })
        override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray) = FloatArray(ids.size) { if (ids[it].size % 2 == 0) 0.9f else 0.1f }
    }

    // по 3–15 примеров на раздел нормализатора, из NumbersTest, CasesTest, RulesTest, AbbrevTest, LatinSymbolsTest и др.
    private val examples = listOf(
        // числа
        "0", "21000", "1 200 000 человек", "3,5", "1,25", "-5", "№7", "§3", "33000000000000", "2 + 3 = 5", "5+3", "+15%",
        "8 000 000 000", "10-15 минут", "5-метровый", "121-летний юбилей", "30%-ная надбавка", "12 345 678 человек", "глава 1 200 читателей",
        // падежи
        "около 500 рублей", "у 3 сестёр", "с 5 детьми", "между 2 и 5 км", "от 1 до 3 мин", "к 25 годам", "более 2-3 раз",
        "у 2-х друзей", "в 5-ти км от города", "ученик 1 класса", "в 19 веке", "1 книгу", "21 минуту",
        // даты
        "05.12.2020", "6/10/2024", "2024-05-12", "с 23 февраля 2024 г.", "20 июня 2050 года", "с 1 по 5 сентября", "к 1 сентября",
        "3-15 мая", "Встреча 3 дек.", "22 сент. 2026 г. в 19:00", "20.09.26, 18:06", "05.12",
        // время
        "в 14:30", "7:01 утра", "до 18:30", "к 1:00", "23:59:01", "в 12:30:05.25", "Вчера, 16:00",
        // годы
        "в 1990 г.", "в 1941–1945 гг.", "490 г. до н. э.", "в 1990-х годах", "2000-е годы", "в 2024-м году", "В 1990 Году", "1917 год",
        // единицы и градусы
        "Ветер до 14 м/сек.", "Давление 760 мм рт.ст., ветер слабый.", "Сеть 127 В, потом 380 В.", "12.5 км/ч", "1,5 л",
        "в 100 г продукта", "500 г муки", "−5 °C", "22°", "−20°F", "Температура 5 гр. С.", "-14 dB", "Рост 180 см. Он был высок.",
        // ложки
        "2 ч. л. соли", "1/2 ст. л.", "0,5 ч. л.",
        // валюты
        "\$5", "5 руб. 30 коп.", "2,5 €", "1 \$ 99 центов", "от \$5 до \$10", "1 200 ₽", "5 тыс. руб.", "3 500 \$",
        // проценты и дроби
        "50%", "1%", "10 %", "Дробь 1/2.", "3/4", "25/3",
        // римские
        "Людовик XIV", "Пётр I", "в XX веке", "Европа XIX в.", "глава iv", "Windows XP", "Размер XL", "в ХХ веке", "Глава I",
        // сокращения
        "и т. д.", "т.е.", "на стр. 5", "см. рис. 3", "согласно п. 3", "г. Москва", "Московская Обл.", "в Ленинском р-н.",
        "Роза (лат. Rosa) цветёт.", "Агент ФСБ и порт USB.", "Служил в НАТО.", "Т-72Б3, 11К65М.", "АК-74, ГАЗ-66, Ту-154.",
        "P.S. Привет.", "пункт 2.10.3 и табл.", "Л. Н. Толстой родился в 1828 г. в Ясной Поляне.",
        // латиница и аббревиатуры
        "iPhone и YouTube", "В 2024-м году вышел iPhone 15.", "По Wi-Fi, wifi и WiFi, hi-fi.", "Нет SIM-карты, PIN-код 1234.",
        "I love you", "Язык C++", "Видео в 4K.", "Samsung Galaxy S20 FE", "прoблема", "fp32 и rgb и css", "4PDA", "Brian.wav",
        // ссылки и почта
        "см. https://example.com/page", "Подробнее: https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=4 и habr.com/ru/1, почта a@b.ru",
        "пиши на mail.ru@example.com, не на тот", "www.site.ru", "Node.js/Express и vk.com",
        "Смотрите https://t.me/c/1234567890/98765?thread=4321 тут всё написано",
        // эмодзи и символы
        "Привет 😀", "Готово 👍!", "👨‍👩‍👧", "🇦🇶", "1️⃣", "❤️", "а → б", "а ∑ б", "5 * 3", "Глава 1 | Начало", "* * *",
        "внутри)) Или", "В 3:)", "#", "@", ":thank_you:", "Loading…[][][][]50%",
        // телефоны
        "+7 (495) 123-45-67", "8 800 555-35-35", "123-45-67", "+996 555 123 456", "+1 212 555 1234",
        "тел. 8 012 345 06 78 или 8 800 555 35 35", "Позвони на 80123450678!",
        // коды и номера
        "Ваш код: 482913", "Карта 1234 5678 9012 3456", "ИНН 7707083893", "СНИЛС 123-456-789 01", "ISBN 978-5-17-118366-0",
        "Версия 0.14.10", "Иоанна 3:16",
        // знаки, сноски, разное
        "Ну!!! Да...", "Что?!", "Всё. . .", "иди - сюда", "— Привет, — сказал он.", "текст[1] дальше", "(- Иллюстрация -)",
        "Держать ухо вос­тро.", "Глáза левого", "<speak>Раз.</speak>", "1) первое 2) второе", "*Он* пришёл?", "Ура!!!",
        // одна буква и эхо ввода
        "А", "ю", "Заглавная Б", "Удаление заглавная Р",
        // ударения: «ё», «э», омографы
        "Ежик пошел в лес.", "Он узнает, что все уже там.", "Мэр сказал: «Это кафе — лучшее».", "Мы пили чай, а потом обливались потом.",
        "Стрелы, прочертив дугу, упали", "Ст+ены домов украсили", "Вы барон Гордеев?", "Ты придёшь завтра?",
    )

    // несколько абзацев: деление на куски, реплики, паузы, SSML, длинный абзац под maxLen
    private val paragraphs = listOf(
        "Глава 1\n\nПоздним вечером, 12 мая 1999 г., он вернулся домой. — Ты где был? — спросила мать.\n— Гулял, — ответил он и ушёл к себе.\n\nНа часах было 23:45.",
        "Он шёл\nдомой по-\nтихоньку.\n\nСо-\nбака ждала у ворот. «Привет», — сказал он. «Как дела?» — «Хорошо!»",
        "Раз.{pause:700}Два. Три...\n\nЧетыре?! Пять; шесть: семь (восемь) — девять.",
        "<speak><prosody rate=\"fast\">Раз. Два.</prosody><break time=\"1s\"/> Три.</speak>",
        "Иван Петров, Смотрите https://t.me/c/1234567890/98765?thread=4321 тут всё написано, 19:05",
        "Он открыл YouTube и включил песню Yesterday by The Beatles, а потом ещё одну.",
        List(12) { "Старик долго смотрел на море, на серые волны, на чаек над водой и на далёкий берег, где ${it + 1} дом стоял у самой воды" }
            .joinToString(", ") + ".",
    )

    private val inputs by lazy { TestData.golden().let { g -> List(g.length()) { g.getJSONObject(it).getString("text") } } + examples + paragraphs }

    /** Умолчание, чтец, cis-пак и каждый ключ правил, переключённый относительно умолчания (плюс служебные);
     * ключи sr_* и en_proxy_sr действуют только у чтеца — с ними screenReader(). */
    private fun variants(): List<Triple<String, Rules, String>> {
        val ru = d.allowed; val cis = TestData.cisAllowed
        val keys = Rules.KEYS + listOf("speech", Rules.LINK_WORD, Rules.LETTER_ECHO, Rules.SCREEN_READER)
        return listOf(Triple("default", Rules(), ru), Triple("sr", Rules().screenReader(), ru),
            Triple("cis", Rules(), cis), Triple("cis-sr", Rules().screenReader(), cis)) +
            keys.map { k -> Rules().let { r -> r.with(k, !r.on(k)).let { t ->
                Triple("$k=${if (r.on(k)) 0 else 1}", if (k.startsWith("sr_") || k == "en_proxy_sr") t.screenReader() else t, ru) } } }
    }

    private fun sha(lines: List<String>) = MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray())
        .joinToString("") { "%02x".format(it) }

    @Test fun ruPathMatchesSnapshot() {
        // общие таблицы — как в SileroModels.data(): морфология, «ё», твёрдое «э», эмодзи, имена знаков
        val morph = Normalizer.morph; val yo = YoDict.shared; val hardE = HardE.shared; val emoji = Emoji.shared; val cldr = SymbolNames.cldr
        Normalizer.morph = Morph.open(asset("morph.bin"))
        YoDict.shared = asset("eyo_safe.txt").bufferedReader().useLines { YoDict(it) }
        HardE.shared = HardE(asset("hard_e.txt").readLines())
        Emoji.shared = asset("emoji_ru.tsv").bufferedReader().useLines { Emoji(it) }
        SymbolNames.cldr = asset("symbols_ru.tsv").bufferedReader().useLines { SymbolNames.parse(it) }
        try {
            val stressDict = listOf(asset("dicts/stress/Системный.txt"))
            val userDict = DictCache.stress(stressDict)
            val repl = DictCache.replacements(listOf(asset("dicts/replace/Системный.txt")), stress = stressDict)
            // варианты независимы: параллельно (~25 с подряд), порядок строк — по списку вариантов
            val got = variants().parallelStream().map { (name, r, allowed) ->
                val s = Stress(d, models, userDict, r)
                listOf("prepare/$name" to sha(inputs.map { RuLayer.prepare(it, allowed, r) }), "full/$name" to sha(inputs.flatMap { t ->
                    // английский кусок другому движку (en_proxy_*) — от трёх слов
                    val en = if (r.on(if (r.on(Rules.SCREEN_READER)) "en_proxy_sr" else "en_proxy_books")) 3 else 0
                    Pipeline.plan(t, d, 100, 300, repl, r, en, allowed = allowed).map { seg ->
                        // фокус и тип — как в synthSegment сервиса: в accent их не видно
                        var ft = if (r.on("exclaim")) Marks.exclaim(seg.text) else seg.text
                        if (r.on("question") && SentenceType.classify(ft, d, r) == "general_q") ft = Marks.question(ft)
                        val marks = Marks.parse(ft, r.focusLevel)
                        val acc = Pipeline.accent(seg.text, d, s, allowed, r)
                        val (end, dot) = s.modelEnd(acc)
                        val spoken = s.stretchShort(end)
                        "$seg\t${marks.words}\t$acc\t$dot\t${s.forModel(spoken)}\t${s.yaOnset(spoken)}\t${s.clusterOnset(spoken)}\t" +
                            SentenceType.classify(marks.text, d, r)
                    }
                }))
            }.collect(Collectors.toList()).flatten().toMap()
            if (System.getProperty("ruvoice.snapshot.write") == "true") {
                snapshot.writeText("# RuPathSnapshotTest: вариант<TAB>sha256 вывода; перезапись -Druvoice.snapshot.write=true\n" +
                    got.entries.joinToString("") { "${it.key}\t${it.value}\n" })
                return
            }
            val want = snapshot.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { it.substringBefore('\t') to it.substringAfter('\t') }
            val bad = (got.keys + want.keys).filter { got[it] != want[it] }.map {
                it + when { want[it] == null -> " (нет в эталоне)"; got[it] == null -> " (пропал)"; else -> "" }
            }
            assertTrue("русский путь разошёлся с ru_snapshot.txt (${bad.size} из ${want.size}):\n" + bad.joinToString("\n"), bad.isEmpty())
        } finally {
            Normalizer.morph = morph; YoDict.shared = yo; HardE.shared = hardE; Emoji.shared = emoji; SymbolNames.cldr = cldr
        }
    }

    @Test fun rulesCompatibleWithV101() {
        // настройки пользователей хранят выключенные правила по именам ключей: ключи v1.0.1 не пропадают, умолчания те же
        val v101 = listOf(
            "sr_fast_start", "sr_symbols", "sr_link_word", "sr_quote_off", "sr_lead_in_off", "sr_pauses_off", "sr_keep_loaded", "sr_phrase_disk",
            "symbol_names", "emoji", "letter_name", "letter_echo_all", "lead_in", "fast_start", "prefetch", "drop_links", "link_domain", "drop_emails", "drop_images", "read_links",
            "phones", "codes", "numbers", "arith", "cases", "roman", "roman_name", "dates", "day_month", "years", "times", "units",
            "degrees", "currency", "fractions", "spoons", "gen_suffix", "sections", "thousands", "footnotes",
            "abbrev", "spell_cyr", "spell_lat", "letter_digit", "latin", "homoglyphs",
            "en_proxy_books", "en_proxy_sr",
            "dehyphen", "soft_break", "punct", "ssml",
            "gram", "first_pl", "homo", "accentor", "yo", "hard_e", "prefix_space", "end_dot", "end_oxy", "short_word", "intonation", "focus", "exclaim", "question",
            "pause_semicolon", "pause_parens", "pause_min", "fast_cores",
            "verbose_log",
        )
        assertEquals(emptyList<String>(), v101 - Rules.KEYS.toSet())
        assertEquals(setOf("symbol_names", "fast_start", "drop_links", "drop_emails", "drop_images", "en_proxy_books", "en_proxy_sr",
            "letter_echo_all", "letter_echo", "verbose_log", "sr_link_word", "link_word", "screen_reader"), Rules.DEFAULT_OFF)
    }
}
