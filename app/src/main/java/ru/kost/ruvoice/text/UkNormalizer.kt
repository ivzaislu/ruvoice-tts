// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Украинская подготовка текста перед моделью, по образцу [BelNormalizer]. Пока базовая: то, без чего модель не читает.
 *  - эмодзи и смайлы украинскими словами (CLDR uk, [Emoji.uk]);
 *  - знак ударения над гласной → «+», невидимые символы — без следа;
 *  - разделительный апостроф → «ъ» (в символах cis-пака апостроф служебный, до модели не доходит; без «ъ» «м'ясо» звучит
 *    мягко «мясо»);
 *  - количественные числа словами (именительный падеж, мужской род);
 *  - «!» → «.», скобки → запятая по правилу pause_parens, «–» → «—»;
 *  - всё, чего нет в таблице символов модели, — словом ([SymbolNames.ofUk], правило symbol_names) или пробелом.
 * Регулярки — с IGNORE_CASE, регистрозависимое — в (?-i:…): встроенный (?i:) без флага на JVM кириллицу не видит.
 */
object UkNormalizer {

    // формы — по Викисловарю (kaikki); не проверено носителем
    private val UNITS = arrayOf(
        "нуль", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять",
        "десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять", "п'ятнадцять",
        "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять")
    private val TENS = arrayOf(
        "", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят", "сімдесят", "вісімдесят", "дев'яносто")
    private val HUNDREDS = arrayOf(
        "", "сто", "двісті", "триста", "чотириста", "п'ятсот", "шістсот", "сімсот", "вісімсот", "дев'ятсот")

    /** 0..999; [fem] — «одна», «дві» (тисячі). */
    private fun under1000(n: Int, fem: Boolean = false): String {
        val parts = ArrayList<String>(3)
        if (n >= 100) parts += HUNDREDS[n / 100]
        val r = n % 100
        val unit = { u: Int -> if (fem && u == 1) "одна" else if (fem && u == 2) "дві" else UNITS[u] }
        when {
            r == 0 -> {}
            r < 20 -> parts += unit(r)
            else -> { parts += TENS[r / 10]; if (r % 10 > 0) parts += unit(r % 10) }
        }
        return parts.joinToString(" ")
    }

    /** Форма существительного после числа: 1 — [one], 2–4 — [few], прочие — [many]. */
    private fun plural(n: Int, one: String, few: String, many: String) = when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }

    /** С миллиарда — по цифрам: разрядов дальше нет (телефон, номер карты), а таблицы на них кончаются. */
    private const val BIG = 1_000_000_000L

    /** Число по цифрам: «80291234567» → «вісім нуль два…». */
    fun digits(raw: String) = raw.map { UNITS[it.digitToInt()] }.joinToString(" ")

    /** Количественное в именительном падеже, мужской род: 2026 → «дві тисячі двадцять шість». */
    fun cardinal(n: Long): String {
        if (n < 0) return "мінус " + cardinal(-n)
        if (n == 0L) return UNITS[0]
        if (n >= BIG) return digits(n.toString())
        val millions = (n / 1_000_000).toInt()
        val thousands = ((n / 1000) % 1000).toInt()
        val rest = (n % 1000).toInt()
        val parts = ArrayList<String>(3)
        if (millions > 0) parts += under1000(millions) + " " + plural(millions, "мільйон", "мільйони", "мільйонів")
        if (thousands > 0) parts += under1000(thousands, fem = true) + " " + plural(thousands, "тисяча", "тисячі", "тисяч")
        if (rest > 0) parts += under1000(rest)
        return parts.joinToString(" ")
    }

    /** Группа цифр как написана: нули в начале — словом каждый («007» — «нуль нуль сім»). */
    private fun group(raw: String): String {
        val rest = raw.trimStart('0')
        val tail = rest.takeIf { it.isNotEmpty() }?.let { r -> r.toLongOrNull()?.let { cardinal(it) } ?: digits(r) }
        return (List(raw.length - rest.length) { UNITS[0] } + listOfNotNull(tail)).joinToString(" ")
    }

    /** Разряды тысяч через пробел — только группами по три («1 000 000»): телефон «44 123 45 67» и «1 2 3» — отдельные числа. */
    private val DIGIT_GROUP_RE = Regex("(?<!\\d[ \\u00A0\\u202F]?)\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?![ \\u00A0\\u202F]?\\d)|\\d+")

    /** Числа словами (правило numbers); буква вплотную к числу — через пробел: «8к74» иначе слиплось бы в одно слово. */
    private fun numberWords(s: String) = DIGIT_GROUP_RE.replace(s) { m ->
        val raw = m.value.filter { it.isDigit() }
        // нуль в начале трёх и больше цифр — код («044», «007»), как в телефоне; «05» — минуты и месяц
        val w = if (raw.length > 2 && raw[0] == '0') group(raw) else raw.toLongOrNull()?.let { cardinal(it) } ?: digits(raw)
        (if (s.getOrNull(m.range.first - 1)?.isLetter() == true) " " else "") + w +
            (if (s.getOrNull(m.range.last + 1)?.isLetter() == true) " " else "")
    }

    /** Гласная со знаком ударения над ней (U+0301 пишет «Книга с ударениями», гравис U+0300 — словари): «+» перед ней. */
    private val COMBINING_ACUTE_RE = Regex("([аеєиіїоуюяёыэ])[\\u0301\\u0300]", RegexOption.IGNORE_CASE)
    /** Мягкий перенос книг и невидимые соединители: выкидываем, иначе фильтр рвал слово пробелом. */
    private val ZERO_WIDTH_RE = Regex("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]")
    /** Символы частной области, которыми нормализаторы помечают разбор (U+E000, U+E001, как у [BelNormalizer]): такие в самом
     * тексте — пробел, а не точка. */
    private val PRIVATE_USE_RE = Regex("[\\uE000\\uE001]")
    /** Цифры Nd не из ASCII (арабо-индийские, полноширинные): \d в ICU их ловит, а таблицы чисел — нет. */
    private val OTHER_DIGIT_RE = Regex("(?![0-9])\\p{Nd}")
    /** Знак перед числом — до чисел: иначе «+1» становится ударением, а «-20» теряет минус. Минус — не после буквы и цифры:
     * «10-15», «Ан-225» не трогаем. */
    private val PLUS_NUMBER_RE = Regex("\\+(?=\\s*\\d)")
    private val MINUS_NUMBER_RE = Regex("(?<![\\p{L}\\d])[-−](?=\\d)")
    /** «+» не перед гласной — не ударение: «Сі++» — «плюс». */
    private val PLUS_NOT_STRESS_RE = Regex("\\+(?![аеєиіїоуюяёыэ])", RegexOption.IGNORE_CASE)
    /** Апостроф во всех написаниях — один класс на слой: им же [UkLayer.dictWord] снимает его в словах словаря. */
    val APOSTROPHE = Regex("[’'`´ʼ‘]")
    /** Разделительный апостроф: между согласной и «я ю є ї» (можно с «+» от знака ударения) — «ъ», как в русском «подъезд».
     * На краю слова (кавычка) и в «О'Коннор» — не разделительный. Несколько подряд («пʼ`ять») — один. */
    private val SEPARATING_APOS_RE = Regex("(?<=[бвгґджзклмнпрстфхцчшщ])[’'`´ʼ‘]+(?=\\+?[яюєї])", RegexOption.IGNORE_CASE)
    /** Смесь «?!» (Normalizer.punctuation сводит к ней «!?», «?!?») — вопрос: «!» модель не получает. */
    private val QUEST_EXCL_RE = Regex("\\?!|!\\?")
    /** Скобки — пауза запятой (pause_parens, как у русского); кавычки — без паузы, их выкидывает фильтр. */
    private val PARENS_RE = Regex("[()\\[\\]]")
    private val COMMA_RUN_RE = Regex(",(?:\\s*,)+")
    // и после тире: «— «Так», —» и «— (тихо) Так» не дают «—, так»
    private val COMMA_EDGE_RE = Regex("^[\\s,]+|(?<=[:;.?…—])\\s*,|\\s*,(?=\\s*(?:[.?…;:]|$))")
    private val SPACE_BEFORE_PUNCT_RE = Regex("\\s+(?=[.,?…:;])")
    private val WS_RE = Regex("\\s+")

    /** Смайлы словами по-украински ([Normalizer.smileys] даёт русские имена): глаголом, как имена эмодзи CLDR. Не проверено
     * носителем. */
    private val SMILEYS = mapOf("улыбается" to "усміхається", "смеётся" to "сміється", "подмигивает" to "підморгує",
        "грустит" to "сумує", "плачет" to "плаче", "показывает язык" to "показує язика", "удивляется" to "дивується",
        "целует" to "цілує", "недоволен" to "незадоволений", "сердце" to "серце", "радуется" to "радіє",
        "катается от смеха" to "качається від сміху", "смеётся до слёз" to "сміється до сліз", "злится" to "сердиться",
        "краснеет" to "червоніє", "цензура" to "цензура", "рука-лицо" to "рука-обличчя", "в шоке" to "у шоці",
        "в замешательстве" to "розгубився", "недоумевает" to "не розуміє", "сомневается" to "сумнівається",
        "моргает" to "кліпає", "аплодирует" to "аплодує", "класс" to "клас", "большой палец вверх" to "великий палець угору",
        "большой палец вниз" to "великий палець униз", "спасибо" to "дякую", "да" to "так", "нет" to "ні", "окей" to "окей",
        "огонь" to "вогонь", "круто" to "круто", "машет рукой" to "махає рукою", "спит" to "спить", "болеет" to "хворіє",
        "испуган" to "наляканий", "закатывает глаза" to "закочує очі")
    private val SMILEY_REPEAT_RE = Regex("""(?<![а-яёєіїґ’'`´ʼ‘])(усміхається|сміється|сумує|плаче|підморгує)(?:[\s.,…!?]+\1(?![а-яёєіїґ’'`´ʼ‘]))+""",
        RegexOption.IGNORE_CASE)

    /** Подготовка: чтение — под ключами [Rules] (их нет у слоя в [UkLayer.unsupported]); служебное (знак ударения, невидимые
     * символы, регистр, апостроф, «!», «+» не перед гласной, фильтр) — всегда. */
    fun prepare(text: String, allowed: String, rules: Rules = Rules()): String {
        var s = text
        fun step(key: String, f: (String) -> String) { if (rules.on(key)) s = f(s) }
        // эмодзи — до снятия соединителей: «👨‍👩‍👧» держится на U+200D; смайлы «:)», «))» — общим разбором, украинскими словами
        step("emoji") { Normalizer.smileys(Emoji.uk?.apply(it) ?: it, { w -> SMILEYS[w] ?: w }, SMILEY_REPEAT_RE) }
        s = COMBINING_ACUTE_RE.replace(s) { "+" + it.groupValues[1] }.replace(ZERO_WIDTH_RE, "").replace(PRIVATE_USE_RE, " ")
        // «·», «→» между словами — пауза; с symbol_names (у чтеца — sr_symbols) знак называется по имени в фильтре ниже
        if (!rules.on("symbol_names")) s = Normalizer.symbolPauses(s)
        s = OTHER_DIGIT_RE.replace(s) { Character.digit(it.value.codePointAt(0), 10).toString() }.lowercase()
        step("numbers") { numberWords(MINUS_NUMBER_RE.replace(PLUS_NUMBER_RE.replace(it, " плюс "), " мінус ")) }
        // после чисел: «п'ять» из них тоже получает «ъ»; [LangStress] ищет слово без «ъ»
        val hard = if ('ъ' in allowed) "ъ" else ""
        fun apos(t: String) = SEPARATING_APOS_RE.replace(t, hard).replace(APOSTROPHE, "")
        s = apos(s)
        s = QUEST_EXCL_RE.replace(s, "?").replace('!', '.')   // «!» модель не получает (служебный символ)
        if (rules.on("pause_parens")) s = PARENS_RE.replace(s, ", ")
        // тире — то, что знает модель: у cis-пака только «—», а Splitter делает из « - » «–»; на «—» и пауза тире
        s = s.replace('–', '—')
        s = PLUS_NOT_STRESS_RE.replace(s, " плюс ").replace("±", " плюс-мінус ").replace("≈", " приблизно ").replace("&", " і ")
        val sb = StringBuilder(s.length)
        val names = rules.on("symbol_names")
        for (c in s) {
            when {
                c in allowed -> sb.append(c)
                // надстрочный знак не над гласной — без следа, пробелом он рвал бы слово
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {}
                // знак, который модель не знает, — словом (правило symbol_names, у чтеца sr_symbols); пунктуацию CLDR не называет.
                // В именах CLDR есть апостроф и латиница («вʼєтнамський донг», «n-арне обʼєднання») — через тот же фильтр
                names && SymbolNames.ofUk(c) != null -> {
                    sb.append(' ')
                    for (n in apos(SymbolNames.ofUk(c)!!)) sb.append(if (n in allowed) n else ' ')
                    sb.append(' ')
                }
                else -> sb.append(' ')
            }
        }
        // скобки дали запятые: подряд — одна, в начале и рядом с точкой — не нужна
        val out = COMMA_RUN_RE.replace(sb.toString().replace(WS_RE, " "), ",")
        return SPACE_BEFORE_PUNCT_RE.replace(COMMA_EDGE_RE.replace(out, ""), "").replace(WS_RE, " ").trim()
    }
}
