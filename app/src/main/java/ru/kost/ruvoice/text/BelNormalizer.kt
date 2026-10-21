// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Белорусская подготовка текста перед моделью.
 *
 * Зачем свой, а не [Normalizer]: русский нормализатор раскрывает русские сокращения, склоняет
 * по таблицам AOT и переводит латиницу в русские буквы — для белорусского это порча: «и», «щ», «ъ»
 * в таблице cis есть, и модель прочла бы русские формы. Здесь только то, без чего модель не читает:
 *  - числа словами (в таблице символов модели цифр нет — они выпадали из речи);
 *  - «!» → «.» (восклицательный знак стоит в первых трёх служебных символах, Silero их вырезает,
 *    а токен «!» при обучении не использовался; интонацию восклицания делаем питчем отдельно);
 *  - апостроф убираем (белорусское «аб'ява»: в символах cis-пака он служебный и до модели не доходит);
 *  - скобки → запятая по правилу pause_parens, кавычки — без паузы (как в русском RuVoice);
 *  - всё, чего в таблице символов нет, выкидываем.
 */
object BelNormalizer {

    private val VOWELS = "аеёіоуыэюя"

    private val UNITS = arrayOf(
        "нуль", "адзін", "два", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")
    private val TENS = arrayOf(
        "", "", "дваццаць", "трыццаць", "сорак", "пяцьдзясят", "шэсцьдзясят", "семдзесят",
        "восемдзесят", "дзевяноста")
    private val HUNDREDS = arrayOf(
        "", "сто", "дзвесце", "трыста", "чатырыста", "пяцьсот", "шэсцьсот", "семсот",
        "восемсот", "дзевяцьсот")
    /** Тысячи женского рода: «адна тысяча», «дзве тысячы». */
    private val THOUSAND_F = arrayOf(
        "нуль", "адна", "дзве", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")

    /** Порядковые (мужской род): именительный, родительный, предложный. */
    private val ORD_NOM = arrayOf(
        "нулявы", "першы", "другі", "трэці", "чацвёрты", "пяты", "шосты", "сёмы", "восьмы",
        "дзявяты", "дзясяты", "адзінаццаты", "дванаццаты", "трынаццаты", "чатырнаццаты",
        "пятнаццаты", "шаснаццаты", "сямнаццаты", "васямнаццаты", "дзевятнаццаты")
    private val ORD_GEN = arrayOf(
        "нулявога", "першага", "другога", "трэцяга", "чацвёртага", "пятага", "шостага", "сёмага",
        "восьмага", "дзявятага", "дзясятага", "адзінаццатага", "дванаццатага", "трынаццатага",
        "чатырнаццатага", "пятнаццатага", "шаснаццатага", "сямнаццатага", "васямнаццатага",
        "дзевятнаццатага")
    private val ORD_LOC = arrayOf(
        "нулявым", "першым", "другім", "трэцім", "чацвёртым", "пятым", "шостым", "сёмым",
        "восьмым", "дзявятым", "дзясятым", "адзінаццатым", "дванаццатым", "трынаццатым",
        "чатырнаццатым", "пятнаццатым", "шаснаццатым", "сямнаццатым", "васямнаццатым",
        "дзевятнаццатым")

    private val TENS_ORD = mapOf(
        20 to Triple("дваццаты", "дваццатага", "дваццатым"),
        30 to Triple("трыццаты", "трыццатага", "трыццатым"),
        40 to Triple("саракавы", "саракавога", "саракавым"),
        50 to Triple("пяцідзясяты", "пяцідзясятага", "пяцідзясятым"),
        60 to Triple("шасцідзясяты", "шасцідзясятага", "шасцідзясятым"),
        70 to Triple("сямідзясяты", "сямідзясятага", "сямідзясятым"),
        80 to Triple("васьмідзясяты", "васьмідзясятага", "васьмідзясятым"),
        90 to Triple("дзевяносты", "дзевяностага", "дзевяностым"))

    private val HUNDREDS_ORD = mapOf(
        100 to Triple("соты", "сотага", "сотым"),
        200 to Triple("двухсоты", "двухсотага", "двухсотым"),
        300 to Triple("трохсоты", "трохсотага", "трохсотым"),
        400 to Triple("чатырохсоты", "чатырохсотага", "чатырохсотым"),
        500 to Triple("пяцісоты", "пяцісотага", "пяцісотым"),
        600 to Triple("шасцісоты", "шасцісотага", "шасцісотым"),
        700 to Triple("сямісоты", "сямісотага", "сямісотым"),
        800 to Triple("васьмісоты", "васьмісотага", "васьмісотым"),
        900 to Triple("дзевяцісоты", "дзевяцісотага", "дзевяцісотым"))

    /** Падеж последнего слова у порядкового числительного в году. */
    enum class Form { NOM, GEN, LOC }

    /** 0..999 в мужском роде. */
    private fun under1000(n: Int, fem: Boolean = false): String {
        val sb = StringBuilder()
        val h = n / 100
        if (h > 0) sb.append(HUNDREDS[h])
        val r = n % 100
        if (r > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            if (r < 20) sb.append(if (fem) THOUSAND_F[r] else UNITS[r])
            else {
                sb.append(TENS[r / 10])
                val u = r % 10
                if (u > 0) { sb.append(' '); sb.append(if (fem) THOUSAND_F[u] else UNITS[u]) }
            }
        }
        return sb.toString()
    }

    /** Количественное: 1234 → «адна тысяча дзвесце трыццаць чатыры». */
    fun cardinal(n: Long): String = count(n, fem = false)

    /** То же для единиц женского рода («тона», «капейка», «штука», «тысяча»): 2 → «дзве», а не «два». */
    fun cardinalFem(n: Long): String = count(n, fem = true)

    /** Слово «тысяча» по числу: 1 → тысяча, 2–4 → тысячы, 5+ → тысяч. */
    private fun thousandWord(n: Int) = when {
        n % 10 == 1 && n % 100 != 11 -> "тысяча"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "тысячы"
        else -> "тысяч"
    }

    /** Число по цифрам: «80291234567» → «восем нуль два…». */
    fun digits(raw: String) = raw.map { UNITS[it.digitToInt()] }.joinToString(" ")

    /** Группа цифр как написана: нули в начале — словом каждый («029» — «нуль дваццаць дзевяць», «05» в дроби). */
    fun group(raw: String): String {
        val rest = raw.trimStart('0')
        val tail = rest.takeIf { it.isNotEmpty() }?.let { r -> r.toLongOrNull()?.let { cardinal(it) } ?: digits(r) }
        return (List(raw.length - rest.length) { UNITS[0] } + listOfNotNull(tail)).joinToString(" ")
    }

    /** С миллиарда — по цифрам: разрядов дальше нет (телефон, номер карты), а under1000 на них падал. */
    private const val BIG = 1_000_000_000L

    private fun count(n: Long, fem: Boolean): String {
        if (n < 0) return "мінус " + count(-n, fem)
        if (n == 0L) return UNITS[0]
        if (n >= BIG) return digits(n.toString())
        val parts = ArrayList<String>()
        val millions = (n / 1_000_000).toInt()
        val thousands = ((n / 1000) % 1000).toInt()
        val rest = (n % 1000).toInt()
        if (millions > 0) parts.add(under1000(millions) + " " + BelNumbers.plural(millions.toLong(), "мільён", "мільёны", "мільёнаў"))
        if (thousands > 0) parts.add(under1000(thousands, fem = true) + " " + thousandWord(thousands))
        if (rest > 0) parts.add(under1000(rest, fem = fem))
        return parts.joinToString(" ")
    }

    /** Порядковое по последнему компоненту: 2026 → «дзве тысячы дваццаць шосты», 1998 → «тысяча дзевяцьсот
     * дзевяноста восьмы» (в годах без «адна»), 2000 → «двухтысячны». */
    fun ordinal(n: Long, form: Form = Form.NOM): String {
        if (n == 0L) return pick(ORD_NOM[0], ORD_GEN[0], ORD_LOC[0], form)
        // от миллиарда — по цифрам, как у количественного; круглые миллионы — одним словом («мільённы», «двухмільённы»),
        // прочее — миллионы количественным, порядковым остаток: «адзін мільён пяты»
        if (n >= BIG) return cardinal(n)
        if (n >= 1_000_000) {
            val millions = (n / 1_000_000).toInt()
            val rest = n % 1_000_000
            return if (rest == 0L) compoundStem(millions) + pick("мільённы", "мільённага", "мільённым", form)
            else cardinal(n - rest) + " " + ordinal(rest, form)
        }
        val head = StringBuilder()
        fun add(s: String) { if (s.isNotEmpty()) { if (head.isNotEmpty()) head.append(' '); head.append(s) } }
        val thousands = (n / 1000).toInt()
        val rest = (n % 1000).toInt()
        // круглые тысячи — одним словом: 1000 → «тысячны», 2000 → «двухтысячны», 10000 → «дзесяцітысячны»
        if (rest == 0) return compoundStem(thousands) + pick("тысячны", "тысячнага", "тысячным", form)
        if (thousands == 1) add("тысяча")
        else if (thousands > 0) add(under1000(thousands, fem = true) + " " + thousandWord(thousands))
        val h = rest / 100
        val r = rest % 100
        // круглые сотни порядковым целиком: 1900 → «тысяча дзевяцісоты», а не «…дзевяцьсот дзевяцісоты»
        if (h > 0 && r > 0) add(HUNDREDS[h])
        val tail = buildString {
            when {
                r == 0 -> append(pickOrd(h * 100, form))
                r < 20 -> append(pick(ORD_NOM[r], ORD_GEN[r], ORD_LOC[r], form))
                else -> {
                    // порядковым становится только последнее слово: 26 → «дваццаць шосты»,
                    // а не «дваццаты шосты»; десятки целиком (20, 30…) берём из ORD-таблицы
                    val u = r % 10
                    if (u == 0) {
                        val t = TENS_ORD[r]!!
                        append(pick(t.first, t.second, t.third, form))
                    } else {
                        append(TENS[r / 10])
                        append(' ')
                        append(pick(ORD_NOM[u], ORD_GEN[u], ORD_LOC[u], form))
                    }
                }
            }
        }
        // у составного числительного порядковым становится только последнее слово
        return (head.toString() + " " + tail).trim()
    }

    /** Первая часть сложного слова от числа — каждое слово в родным склоне, слитно, как в GrammarDB «двухтысячны»,
     * «дваццаціпяцітысячны», «стотысячны»: 2 → «двух», 10 → «дзесяці», 25 → «дваццаціпяці», 100 → «сто», 300 → «трохсот»;
     * 1 — пусто («тысячны»). ponytail: «дзесяцітысячны», «саракатысячны», единица внутри («дваццаціадна…») и сотни от
     * двухсот («двухсоттысячны») — по аналогии, в GrammarDB нет; не проверено носителем. */
    internal fun compoundStem(n: Int, hundred: String = "сто", one: String = ""): String {
        if (n == 1) return one
        val (units, tens) = BelNumbers.CARD_GEN
        fun unit(u: Int) = if (u == 1) "адна" else units[u - 1]
        val h = n / 100
        val r = n % 100
        val hs = when (h) { 0 -> ""; 1 -> hundred; else -> unit(h) + "сот" }
        val rs = when {
            r == 0 -> ""
            r < 20 -> unit(r)
            else -> tens[r / 10 - 2] + (if (r % 10 == 0) "" else unit(r % 10))
        }
        return hs + rs
    }

    private fun pickOrd(v: Int, form: Form): String =
        HUNDREDS_ORD[v]?.let { pick(it.first, it.second, it.third, form) } ?: ""

    private fun pick(nom: String, gen: String, loc: String, form: Form) = when (form) {
        Form.NOM -> nom
        Form.GEN -> gen
        Form.LOC -> loc
    }

    // «гадоў», «гадах» — после количественного («100 гадоў»), год порядковым только в единственном числе
    // слева не цифра и не разряд: «1 000 год», «12345 год» — не год «000» и «2345»
    private val YEAR_RE = Regex("(?<![\\d.,])(?<!\\d[ \\u00A0\\u202F])(\\d{3,4})\\s*(год|года|годзе)(?![а-яёіў])", RegexOption.IGNORE_CASE)
    /** Разряды тысяч через пробел — только группами по три («1 000 000») и без соседних групп: телефон
     * «29 123 45 67», номер карты «4276 1234…» и «1 2 3» — отдельные числа. */
    private val DIGIT_GROUP_RE = Regex("(?<!\\d[ \\u00A0\\u202F]?)\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?![ \\u00A0\\u202F]?\\d)|\\d+")

    /** Десятичная дробь: целая часть (можно разрядами, как [DIGIT_GROUP_RE]), точка или запятая, цифры; не кусок
     * «1.10.2» или IP-адреса. */
    private val DECIMAL_RE = Regex("""(?<![\d.,]|\d[ \x{A0}\x{202F}])(\d{1,3}(?:[ \x{A0}\x{202F}]\d{3})+|\d+)[.,](\d+)(?![.,]?\d)""")

    /** Число словами: до 999 999 999, длиннее — по цифрам; с годом — порядковым и в нужном падеже; дробь — [BelNumbers.decimal].
     * Год — правило years, остальное — numbers. */
    fun numberWords(text: String, rules: Rules = Rules()): String {
        // буква вплотную к числу — через пробел: «8к74», «3тамы» иначе слипались в «восемксемдзесят», «трытамы»
        fun Regex.words(src: String, f: (MatchResult) -> String) = replace(src) { m ->
            (if (src.getOrNull(m.range.first - 1)?.isLetter() == true) " " else "") + f(m) +
                (if (src.getOrNull(m.range.last + 1)?.isLetter() == true) " " else "")
        }
        var s = YEAR_RE.words(text) { m ->
            if (!rules.on("years")) return@words m.value
            val n = m.groupValues[1].toLongOrNull() ?: return@words m.value
            val word = m.groupValues[2].lowercase()
            val form = when (word) {
                "годзе" -> Form.LOC
                "года" -> Form.GEN
                else -> Form.NOM
            }
            ordinal(n, form) + " " + word
        }
        if (!rules.on("numbers")) return s
        // дробь без единицы: «3,14», «1.5»; «12.05» сюда не доходит — его уже прочли датой
        val d = s
        s = DECIMAL_RE.words(d) { m -> BelNumbers.decimal(m.groupValues[1].filter { it.isDigit() }, m.groupValues[2], BelNumbers.decimalCase(d, m.range.first)) }
        s = DIGIT_GROUP_RE.words(s) { m ->
            val raw = m.value.filter { it.isDigit() }
            // нуль в начале трёх и больше цифр — код («8-029-123», «007»): читаем, как в телефоне ([group]); «05» — месяц, минуты
            if (raw.length > 2 && raw[0] == '0') group(raw) else raw.toLongOrNull()?.let { cardinal(it) } ?: digits(raw)
        }
        return s
    }

    /** Коды, пароли, PIN, номера карт, реквизиты — по цифрам группами (правило codes), как у русского codes: «код 482913» →
     * «код чатыры восем два, дзевяць адзін тры», карта «4276 1234 5678 9012», маска «•• 1234», «УНП 190123456». До латиницы:
     * «PIN», «CVC» узнаём, пока они латиницей. */
    private fun codes(text: String): String {
        fun spell(v: String, whole: Boolean = false) = Normalizer.spellDigits(v, whole, DIGIT_NAMES)
        var s = CARD_RE.replace(text) { m -> spell(listOf(1, 3, 4, 5, 6).mapNotNull { m.groups[it]?.value }.joinToString(" ")) }
        s = CARD_MASK_RE.replace(s) { m -> spell(m.groupValues[1], whole = true) }
        s = REQUISITE_RE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + spell(m.groupValues[3]) }
        s = CODE_AFTER_WORD_RE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + spell(m.groupValues[3]) }
        return CODE_BEFORE_WORD_RE.replace(s) { m -> spell(m.groupValues[1]) }
    }
    private val DIGIT_NAMES = UNITS.copyOfRange(0, 10)
    private const val CODE_WORD = """(?:пін-?код|код|пароль|аднаразовы\s+пароль|pin|cvc2?|cvv2?|otp|code|passcode)"""
    private const val CODE_WORD_FORMS = """(?:пін-?код(?:а|у|ам|е|ы|аў)?|код(?:а|у|ам|е|ы|аў)?|парол(?:ь|я|ю|ем|і|яў)|pin|cvc2?|cvv2?|otp|code|passcode)"""
    private const val CODE_DIGITS = """\d{2,4}(?:[ -]\d{2,4}){1,2}|\d{3,8}"""
    private const val CODE_NOT_AFTER = """(?!\d|[.,:]\d|\s*(?:%|₽|\$|€|°|руб|г\.|год|гад|шт|мін|сек|раз|км|кг|м(?!\p{L})))"""
    private val CODE_AFTER_WORD_RE = Regex("""(?<![\p{L}\d])($CODE_WORD_FORMS)(?!\p{L})([^\d.!?\n]{0,40}?)(?<![\p{L}\d,.])($CODE_DIGITS)$CODE_NOT_AFTER""",
        RegexOption.IGNORE_CASE)
    private val CODE_BEFORE_WORD_RE = Regex("""(?<![\p{L}\d,.+])(\d{4,8})(?!\d|[.,]\d)(?=\s*[-–—:]?\s*(?:гэта\s+)?(?:ваш[аы]?\s+)?$CODE_WORD(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    private val CARD_RE = Regex("""(?<![\d.,])(\d{4})([ -])(\d{4})\2(\d{4})\2(\d{4})(?:\2(\d{1,3}))?(?![\d.,]\d|\d)""")
    private val CARD_MASK_RE = Regex("""(?<![\p{L}\d])[*•·xх]{1,12}\s?(\d{4})(?!\d)""")
    private val REQUISITE_RE = Regex("""(?<![\p{L}])(УНП|АКПА|ОКПО|ІНН|ИНН|КПП|БІК|БИК)(\s*:?\s*)(\d[\d -]{2,20}\d)(?![\d.,]\d)""")

    /** Сноски — не число в тексте (правило footnotes, как у русского): «[1]», «{5}», номер вплотную к слову («даследчыкі2.»,
     * «словах¹», «інструмента(12)»). Слово — от трёх строчных; «м²» короче, «#Навука2025» — хэштэг. */
    private fun footnotes(t: String) = GLUED_FOOTNOTE_RE.replace(FOOTNOTE_RE.replace(t, ""), "").replace(DOUBLE_SPACE_RE, " ")
    private val FOOTNOTE_RE = Regex("""\[\d+\]|\{\d+\}""")
    private val GLUED_FOOTNOTE_RE = Regex("""(?<=(?<![#@\p{L}])\p{L}?[а-яёіў]{3,40})(?:(\d{1,3})(?=[.,;:!?)»…—–]|$)|(?<=[еіюя])(\d{1,2})(?=\s)|""" +
        """[¹²³⁴⁵⁶⁷⁸⁹⁰]{1,3}(?=[\s.,;:!?)»…—–]|$)|\(\d{1,3}\)(?=[\s.,;:!?)»…—–]|$))""")
    private val DOUBLE_SPACE_RE = Regex(" {2,}")

    /** Разряды точкой и у старых книг (правило thousands, как у русского): «1.000.000», «10,000 чал.» — одно число. */
    private fun thousands(t: String) = OLD_THOUSANDS_RE.replace(DOT_THOUSANDS_RE.replace(t) { it.value.replace(".", "") }) { it.groupValues[1] + "000" }
    private val DOT_THOUSANDS_RE = Regex("""(?<![\d.])(?<!\d,)\d{1,3}\.\d{3}\.\d{3}(?![\d.]|,\d)""")
    private val OLD_THOUSANDS_RE = Regex("""(?<![\d.,])([1-9]\d{0,2})[.,](000)(?![\d.,]\d|\d)""")

    /** Лыжкі (правило spoons, как у русского): «1 ч. л.» → «адна чайная лыжка», «2 ст. л.» → «дзве сталовыя лыжкі»; дробь —
     * родны адз.: «1/2 ч. л.» → «1/2 чайнай лыжкі». Точка после «л» — точка предложения перед заглавной. */
    private fun spoons(t: String) = SPOON_RE.replace(t) { m ->
        val (num, kind) = m.destructured
        val n = num.toLongOrNull()
        val k = kind.lowercase()
        if (n == null) "$num ${SPOON_GEN.getValue(k)}"
        else cardinalFem(n) + " " + SPOONS.getValue(k).let { BelNumbers.plural(n, it.first, it.second, it.third) }
    }
    private val SPOONS = mapOf("ч" to Triple("чайная лыжка", "чайныя лыжкі", "чайных лыжак"),
        "ст" to Triple("сталовая лыжка", "сталовыя лыжкі", "сталовых лыжак"))
    private val SPOON_GEN = mapOf("ч" to "чайнай лыжкі", "ст" to "сталовай лыжкі")
    private val SPOON_RE = Regex("""((?:\d+/)?\d+(?:[.,]\d+)?)\s*(ч|ст)\.?\s*л(?![\p{L}\d])(?:\.(?!\s*(?-i:[А-ЯЁІЎ])))?""", RegexOption.IGNORE_CASE)

    /** Гласная со знаком ударения над ней (U+0301 пишет «Книга с ударениями», гравис U+0300 — словари): «+» перед ней. */
    private val COMBINING_ACUTE_RE = Regex("([аеёіоуыэюяАЕЁІОУЫЭЮЯ])[\\u0301\\u0300]")
    /** Мягкий перенос книг и невидимые соединители: выкидываем, иначе фильтр рвал слово пробелом. */
    private val ZERO_WIDTH_RE = Regex("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]")
    /** «+» не перед гласной — не ударение: «C++» — «плюс». */
    private val PLUS_NOT_STRESS_RE = Regex("\\+(?![аеёіоуыэюя])")
    /** Знак перед числом — до чисел: иначе «+1» становится «+адзін» (ударение), а «-20» теряет минус. Минус — не после
     * буквы и цифры: «10-15», «8-029», «1990-я», «Ту-154» не трогаем. */
    private val PLUS_NUMBER_RE = Regex("\\+(?=\\s*\\d)")
    private val MINUS_NUMBER_RE = Regex("(?<![\\p{L}\\d])[-−](?=\\d)")
    /** Цифры Nd не из ASCII (арабо-индийские, полноширинные): \d в ICU их ловит, а таблицы чисел — нет. */
    private val OTHER_DIGIT_RE = Regex("(?![0-9])\\p{Nd}")
    /** Апостроф во всех написаниях. В символах cis-пака он есть, но служебный (первые три, Silero их вырезает): до
     * модели не доходит, «аб'ява» читается «абява». Тем же классом его снимает [BeLayer.dictWord] в словах словаря
     * языка, в поправках пака тоже (список «Системный be», [ru.kost.ruvoice.Dicts.syncPacks]). */
    val APOSTROPHE = Regex("[’'`´ʼ‘]")
    private val SEPARATING_APOS_RE = Regex("(?<=[а-яёіўА-ЯЁІЎ])[’'`´ʼ‘](?=[яеёюіЯЕЁЮІ])")
    /** Градусы Цельсия латинской или кириллической «C» — к одной записи «°с» (кириллица), Фаренгейта — «°ф». Шкала через
     * пробел — только заглавной, как у русского: «30° с ухілам» — предлог. */
    private val CELSIUS_RE = Regex("°(?:[CcСс]|\\s+[CС])(?!\\p{L})")
    private val FAHRENHEIT_RE = Regex("°(?:[FfФф]|\\s+[FФ])(?!\\p{L})")
    /** Минуты угла ASCII-апострофом («42°30'») — штрихом, пока апостроф не снят ([BelNumbers.units]). */
    private val ANGLE_APOS_RE = Regex("(?<=\\d°\\s?\\d{1,2}\\s?)'")
    /** Код валюты после числа — её знак или «руб» ([BelNumbers.units]): «1,20 BYN», «100 Br» — рублі, «5 USD» — долары;
     * без числа — как было, по буквам. */
    private val CURRENCY_CODE_RE = Regex("(?<=\\d)\\s*(BYN|BYR|Br|USD|EUR|RUB|RUR|GBP|UAH|KZT|JPY|TRY|KRW|ILS|INR)(?![A-Za-z])",
        RegexOption.IGNORE_CASE)
    private val CURRENCY_CODES = mapOf("BYN" to "руб", "BYR" to "руб", "BR" to "руб", "USD" to "$", "EUR" to "€", "RUB" to "₽",
        "RUR" to "₽", "GBP" to "£", "UAH" to "₴", "KZT" to "₸", "JPY" to "¥", "TRY" to "₺", "KRW" to "₩", "ILS" to "₪", "INR" to "₹")
    /** Единица латиницей после числа — ключом [BelNumbers.UNITS], пока латиница не стала буквами («16 GB» иначе «гэ-бэ»):
     * «100 kWh», «5 MHz», «12V». Заглавная «А» за числом — ампер («амп»): строчная после опускания регистра — союз «а». */
    private val LATIN_UNIT_RE = Regex("""(?<=\d)\s?(kWh|MWh|mAh|kbps|Mbps|Gbps|km/h|kHz|MHz|GHz|Hz|km|cm|mm|ml|mg|kg|KB|kB|MB|GB|TB|Kb|Mb|Gb|kW|MW|GW|mV|W|V|rpm|dB)(?![A-Za-z])""")
    private val LATIN_UNITS = mapOf("kWh" to "квт·г", "MWh" to "мвт·г", "mAh" to "ма·г", "kbps" to "кбіт/с", "Mbps" to "мбіт/с",
        "Gbps" to "гбіт/с", "km/h" to "км/г", "kHz" to "кгц", "MHz" to "мгц", "GHz" to "ггц", "Hz" to "гц", "km" to "км", "cm" to "см",
        "mm" to "мм", "ml" to "мл", "mg" to "мг", "kg" to "кг", "KB" to "кб", "kB" to "кб", "MB" to "мб", "GB" to "гб", "TB" to "тб",
        "Kb" to "кбіт", "Mb" to "мбіт", "Gb" to "гбіт", "kW" to "квт", "MW" to "мвт", "GW" to "гвт", "mV" to "мв", "W" to "вт", "V" to "в",
        "rpm" to "аб/мін", "dB" to "дб")
    private val AMPERE_RE = Regex("""(?<=\d\s?)А(?![\p{L}\d-])""")
    /** «10k» — тысячи, как у русского kThousandRe; до латиницы, иначе «ка». Заглавная — код или разрешение («4К», «8K»). */
    private val K_THOUSAND_RE = Regex("(?<=\\d)[kк](?![\\p{L}\\d])")
    /** «~5 км» — «прыблізна»; «≈», «±», «&» — словами (у русского symbols), иначе фильтр молча их выкидывал. */
    private val TILDE_RE = Regex("(?<![\\p{L}\\d])~\\s*(?=\\d)")
    /** Одиночная греческая буква — названием («вугал α», «α-распад», «π»), как у русского greek; слова греческими не трогаем. */
    private val GREEK = mapOf('α' to "альфа", 'β' to "бэта", 'γ' to "гама", 'δ' to "дэльта", 'ε' to "эпсілон", 'ζ' to "дзэта",
        'η' to "эта", 'θ' to "тэта", 'ι' to "ёта", 'κ' to "капа", 'λ' to "лямбда", 'μ' to "мю", 'ν' to "ню", 'ξ' to "ксі", 'π' to "пі",
        'ρ' to "ро", 'σ' to "сігма", 'τ' to "тау", 'υ' to "іпсілон", 'φ' to "фі", 'χ' to "хі", 'ψ' to "псі", 'ω' to "амега",
        'Δ' to "дэльта", 'Σ' to "сігма", 'Ω' to "амега", 'Π' to "пі", 'Λ' to "лямбда", 'Γ' to "гама", 'Θ' to "тэта", 'Φ' to "фі",
        'Ψ' to "псі")
    private val FILE_EXT_RE = Regex("""(?<=[A-Za-z\d_-])\.(fb2|epub|txt|pdf|docx?|rtf|mobi|zip|rar|jpe?g|png|gif|mp3|mp4|apk|exe|html?|xlsx?|pptx?|csv|json)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE)
    private val GREEK_RE = Regex("(?<!\\p{L})[α-ωΑ-Ω](?!\\p{L})")
    private val COMMA_RUN_RE = Regex(",(?:\\s*,)+")
    // и после тире: «— «Так», —» и «— (ціха) Так» не дают «—, так»
    private val COMMA_EDGE_RE = Regex("^[\\s,]+|(?<=[:;.?…—])\\s*,|\\s*,(?=\\s*(?:[.?…;:]|$))")
    private val SPACE_BEFORE_PUNCT_RE = Regex("\\s+(?=[.,?…:;])")
    private val WS_RE = Regex("\\s+")
    /** Точка сокращения, закрывшая предложение: единица или год за числом («2024 г. Цяпер», «7,5 руб.» в конце текста),
     * «гг.» за «-х» («Пачатак 1990-х гг.»), «і г. д.», «і інш.», «н. э.». Splitter после них не режет, а разворот точку съедает —
     * ставим за ней вторую, отдельным знаком: она переживает разворот. «г.» за «дд.мм» — горад («15.06 г. Мінск»), регистр — до lowercase.
     * Вторая точка до конца разворотов — меткой [ABBR_DOT]: сокращение не развернулось (его правило выключено) — точка его
     * осталась, и метка уходит без следа, иначе было бы «км..». */
    // Регистр: IGNORE_CASE на всё, регистрозависимое — в (?-i:), как у русского: встроенный (?i:) без флага на JVM кириллицу
    // не видел («ГБ.», «мА·г.» теряли точку в тестах, на Android — нет)
    private val ABBR_END_RE = Regex("""(?:(?-i:(?<=\d-х[ \x{A0}])гг)|(?-i:(?<=\d[ \x{A0}]?|\d-я[ \x{A0}]|(?:тыс|млн|млрд)\.?[ \x{A0}]))""" +
        // сокращения единиц ([BelNumbers.UNITS] на букву; регистр — любой: «МВт.», «кВт.»), «гг», «г» не за «дд.мм»
        """(?:${BelNumbers.UNITS.keys.filter { it.last().isLetter() && it != "г" }.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }}""" +
        """|(?-i:гг|(?<!\.\d\d[ \x{A0}]?)г))""" +
        // только там, где [WORD_ABBR] их развернёт: иначе точка осталась бы двойной
        """|(?-i:(?<=(?<!\p{L})і\s{1,3}[гт]\.\s{0,3})д|(?<=(?<!\p{L})і\s{1,3})(?:інш|пад)|(?<=(?<!\p{L})н\.\s{0,3})э))\.(?-i:(?=\s+\p{Lu}|\s*$))""",
        RegexOption.IGNORE_CASE)
    /** Символ частной области Unicode. В тексте он бывает разве что значком шрифта — такой [prepare] сразу делает пробелом,
     * как сделал бы фильтр. */
    private val ABBR_DOT = Char(0xE000)
    /** Метки разбора ([ABBR_DOT], точка раздела у [BelNumbers.sectionLabels]): такие символы в самом тексте — пробел. */
    private val PRIVATE_USE_RE = Regex("[\\uE000\\uE001]")
    private val ABBR_DOT_KEPT_RE = Regex("\\.\\s*$ABBR_DOT")
    /** Омоглифы: латинская буква-двойник в кириллическом слове — опечатка раскладки («cок», «Мiнск»), [Latin] прочёл бы её
     * латиницей («кок»). Слово меняем, только если вся латиница в нём — двойники и кириллицы не меньше: «файлmicrosoft» и
     * марку с окончанием «TOYOTAўскі» не трогаем. */
    private val HOMOGLYPHS = "aceopxyiABCEHIKMOPTXY".zip("асеорхуіАВСЕНІКМОРТХУ").toMap()
    private val LETTERS_RE = Regex("\\p{L}+")
    private fun homoglyphs(text: String) = LETTERS_RE.replace(text) { m ->
        val w = m.value
        val lat = w.count { it in 'a'..'z' || it in 'A'..'Z' }
        val cyr = w.count { it in 'а'..'я' || it in 'А'..'Я' || it in "ёіўЁІЎ" }
        if (lat == 0 || cyr < lat || w.any { (it in 'a'..'z' || it in 'A'..'Z') && it !in HOMOGLYPHS }) w
        else w.map { HOMOGLYPHS[it] ?: it }.joinToString("")
    }
    /** Код кириллицей заглавными — буквы и цифры, можно через дефис: «8К74», «2А3», «Т-34» (как русский cyrCodes). Смешанный
     * регистр («Ту-154», «МіГ-29») — слово, не трогаем. */
    private val CYR_CODE_RE = Regex("""(?<![\p{L}\d-])(?=[А-ЯЁІЎ\d-]*[А-ЯЁІЎ])(?=[А-ЯЁІЎ\d-]*\d)[А-ЯЁІЎ\d]+(?:-[А-ЯЁІЎ\d]+)*(?![\p{L}\d]|-[\p{L}\d])""")
    private val CODE_RUN_RE = Regex("""[А-ЯЁІЎ]+|\d+|-""")
    /** Одна буква в коде — именем, отдельным словом: «8К74» → «восем к+а семдзесят чатыры», «Т-34» → «т+э-трыццаць чатыры»;
     * несколько букв подряд — словом, как было. Число с единицей вплотную («5Т») и суффикс порядкового капсом («5-Ы») не трогаем. */
    private fun cyrCodes(text: String) = CYR_CODE_RE.replace(text) { m ->
        val runs = CODE_RUN_RE.findAll(m.value).map { it.value }.toList()
        if (runs[0][0].isDigit() && (runs.size == 2 && runs[1].lowercase() in BelNumbers.UNITS || runs.size == 3 && runs[1] == "-"))
            return@replace m.value
        runs.withIndex().joinToString("") { (i, r) ->
            (if (i > 0 && r != "-" && runs[i - 1] != "-") " " else "") + (if (r.length == 1 && r[0].isLetter()) BeLayer.letterName(r[0]) ?: r else r)
        }
    }
    /** Аббревиатура кириллицей по буквам (правило spell_cyr), как у русского [Abbrev]: «БДУ» → «бэ-дэ-у», «ЗША» →
     * «зэ-ша-а», «СССР», «РБ». Токен — 2–6 заглавных; по буквам, если гласных нет, если в [SPELL] или из трёх букв с одной
     * гласной с краю («БДУ», «ВНУ»); иначе слово («ААН», «НАТА»). Рядом слово капсом — заголовок («ДЗЕ ТЫ»): с краю гласную
     * не берём. Инициалы перед фамилией — именем буквы без точки: «І. Шамякін» → «і шамякін», «Я. Купала». Имена букв —
     * [BeLayer.letterName], через дефис, как латиница [Latin]. */
    private fun spellCyr(text: String): String {
        val inits = INITIAL_RE.replace(text) { m ->
            letter(m.value[0]) + if (text.getOrNull(m.range.last + 1)?.isLetter() == true) " " else ""
        }
        return CYR_TOKEN_RE.replace(inits) { m ->
            val t = m.value
            // единица за числом («16 ГБ», «220 В») — её читает [BelNumbers.units], не по буквам
            if (t.lowercase() in BelNumbers.UNITS && DIGIT_BEFORE_RE.containsMatchIn(inits.substring(maxOf(0, m.range.first - 2), m.range.first)))
                return@replace t
            val caps = CAPS_BEFORE_RE.containsMatchIn(inits.substring(maxOf(0, m.range.first - 40), m.range.first)) ||
                CAPS_AFTER_RE.containsMatchIn(inits.substring(m.range.last + 1, minOf(inits.length, m.range.last + 41)))
            val edge = !caps && t.length == 3 && t.count { it in CYR_VOWELS } == 1 && (t[0] in CYR_VOWELS || t[2] in CYR_VOWELS) &&
                t.none { it in "ЙЬЎ" } && t.lowercase() !in EDGE_WORDS
            if (t.none { it in CYR_VOWELS } || t in SPELL || edge) t.map(::letter).joinToString("-") else t
        }
    }
    /** Строчные без гласных — сокращение («рб», «бнф», «жкг»: так пишут в чатах), как у русского lowerAbbr; после единиц:
     * «км», «шт» без числа и междометия — [LOWER_NOT_ABBR]. */
    private fun spellLower(text: String) = LOWER_TOKEN_RE.replace(text) { m ->
        val t = m.value
        if ((t.length > 2 || t[0].isLowerCase()) && t.none { it.uppercaseChar() in CYR_VOWELS || it in "йьўЙЬЎ" } &&
            t.lowercase().toSet().size > 1 && t.lowercase() !in LOWER_NOT_ABBR) t.uppercase().map(::letter).joinToString("-") else t
    }
    private fun letter(c: Char) = BE_LETTER_NAMES.getValue(c.uppercaseChar())
    private val BE_LETTER_NAMES = "АБВГДЕЁЖЗІЙКЛМНОПРСТУЎФХЦЧШЫЬЭЮЯИЩЪ".associateWith {
        BeLayer.letterName(it)?.replace("+", "") ?: it.lowercase()
    } + mapOf('И' to "і", 'Щ' to "шча", 'Ъ' to "цвёрды знак")
    private const val CYR_VOWELS = "АЕЁІОУЫЭЮЯИ"
    /** С гласными, но по буквам (вузы, органы, «ЕС», «ІТ»); не проверено носителем. */
    private val SPELL = setOf("МУС", "ЕС", "АТ", "ІТ", "ІП", "АЭС", "ААЭ", "ААТ", "ТАА", "ЕАЭС", "ЕГУ", "БДУІР", "БДЭУ", "БДПУ", "БДМУ",
        "БНТУ", "БДАТУ", "БДУКІ", "БДУФК", "ГДУ", "ВДУ", "ПДУ", "МДУ", "ЭВМ", "ПТВ", "ЦРУ", "ДАІ", "АДКБ", "УВД", "ПВА", "ЦТ", "ДЗ", "АЗС")
    /** Частые слова из трёх букв с гласной с краю — не сокращение и капсом. ponytail: стоп-лист, пополнять по жалобам. */
    private val EDGE_WORDS = setOf("што", "хто", "дзе", "два", "тры", "сто", "мне", "для", "дні", "дно", "дна", "дня", "зло", "зла",
        "сны", "сна", "рта", "рту", "пса", "псы", "мха", "шва", "швы", "акт", "іск", "арт", "ост", "спа", "бра", "трэ", "збо")
    private val DIGIT_BEFORE_RE = Regex("""\d\s?$""")
    private val CYR_TOKEN_RE = Regex("""(?<!\d-)(?<![\p{L}\d'’])[А-ЯЁІЎ]{2,6}(?![\p{L}\d'’])""")
    private val CAPS_BEFORE_RE = Regex("""(?<![\p{L}\d])[А-ЯЁІЎ]{4,}[\s,:;—–-]*$""")
    private val CAPS_AFTER_RE = Regex("""^[\s,:;—–-]*[А-ЯЁІЎ]{4,}(?![\p{L}\d])""")
    /** Инициал перед фамилией или вторым инициалом; второй инициал после первого («Я. К. Чорны», «Шамякін І. П.»). */
    private val INITIAL_RE = Regex("""(?<![\p{L}\d])[А-ЯЁІЎ]\.(?=\s*[А-ЯЁІЎ](?:[а-яёіў]|\.))|(?<=(?<![\p{L}\d])[А-ЯЁІЎ]\.\s?)[А-ЯЁІЎ]\.""")
    private val LOWER_TOKEN_RE = Regex("""(?<![\p{L}\d+́-])[А-ЯЁІЎа-яёіў][а-яёіў]{1,4}(?![\p{L}\d+́-])""")
    private val LOWER_NOT_ABBR = setOf(
        // единицы и сокращения без числа
        "мм", "см", "км", "дм", "кг", "мг", "гр", "мл", "кв", "шт", "ст", "гг", "вв", "тт", "пп", "пр", "св", "гл", "стр", "мн",
        "мкм", "мкг", "мс", "нм", "млн", "млрд", "трлн", "кб", "мб", "гб", "тб", "вт", "квт", "гц", "дж", "сб", "нд", "пн", "чц", "пт",
        "др", "тд", "тп", "спс", "плз", "крч",
        // междометия
        "хм", "гм", "гмм", "кхм", "хмм", "тс", "тсс", "тссс", "пст", "псс", "псст", "брр", "шш", "пф", "пфф", "фф", "цц", "мгм", "бр", "тпр")

    /** Начало фразы до «г.»/«гг.»: предлог и год (или два года, или десятилетие на «-х») — «У 1941 », «З 1941–1945 »,
     * «У 1990-х », «У 90-х ». Не «Нарадзіўся ў 1990 г. Яго…»: там слова перед предлогом, и это конец предложения. */
    private val YEAR_LEAD_RE = Regex("""\P{L}*(?:у|ў|з|да|ад|па|пасля|каля|перад)\s+(?:\d{4}(?:\s*[–—-]\s*\d{4})?|\d{2,4}-х)[ \x{A0}]?""",
        RegexOption.IGNORE_CASE)
    /** Сокращения словами; «вуліцы» после «на/па» (месны) и предлогов родительного, «вуліцу» после «праз», «і іншае»,
     * «так званы» (род по следующему слову), «(да) нашай эры» — не проверено носителем. Пробел в конце замены: «напр.Мінск» — не
     * «напрыкладмінск». */
    /** Сокращения-существительные — словом в падеже предлога (правило abbrev, как у русского nounAbbrevs). Перед номером:
     * «д. 5» → «дом», «у д. 5» → «у доме», «кв.», «корп.», «п.», «пп.», «арт.», «ч.», «гл.», «т.», «с.», «рыс.», «мал.», «табл.»,
     * «дад.», «заўв.», «кн.», «разд.», «пав.», «пад.», «тэл.»; перед назвай з вялікай — «в. Ракаў» → «вёска», «пр.», «пл.»,
     * «пас.», «р.», «воз.», «мкр.», «зав.», «бул.», «ст.» (станцыя), «акад.», «праф.», «дац.», «г. п.» — «гарадскі пасёлак».
     * «з» перед месцам — родны («з в. Ракаў» → «з вёскі»), иначе творны, как у [BelNumbers.signs]. До опускания регистра:
     * назву узнаём по заглавной. Формы GrammarDB; не проверено носителем. */
    private fun nounAbbrs(text: String): String = NOUN_ABBR_RE.replace(GP_RE.replace(text, "гарадскі пасёлак ")) { m ->
        val prep = m.groupValues[1]
        val key = m.groupValues[2]
        val a = NOUN_ABBRS[key.lowercase()] ?: return@replace m.value
        val roman = m.groupValues[3]
        val digit = roman.isNotEmpty() || m.groupValues[4].isNotEmpty()
        // перед назвай — только строчная: «В. Быкаў», «Р. П.» — инициалы; «XX ст. Беларусь» — стагоддзе, не станцыя
        if (if (digit) !a.digit else !a.name || key[0].isUpperCase() || ROMAN_OR_DIGIT_BEHIND.containsMatchIn(text.substring(maxOf(0, m.range.first - 8), m.range.first)))
            return@replace m.value
        val p = prep.lowercase()
        val case = if ((p == "з" || p == "са") && a.place) Case.GEN else BelNumbers.SIGN_PREP[p] ?: Case.NOM
        (if (prep.isEmpty()) "" else "$prep ") + a.forms[case.ordinal] + " " +
            // «кн. II», «т. III» — номер римскими: порядковое в роде слова («кніга другая»), латиница прочла бы «і-і»
            if (roman.isEmpty()) "" else BelNumbers.ordinal(BelNumbers.romanValue(roman), case, a.gender) + " "
    }
    private class NounAbbr(forms: String, val digit: Boolean, val name: Boolean = false, val place: Boolean = false, val gender: Gender = Gender.M) {
        val forms = forms.split(' ')
    }
    private val NOUN_ABBRS = mapOf(
        "д" to NounAbbr("дом дома дому дом домам доме", true),
        "кв" to NounAbbr("кватэра кватэры кватэры кватэру кватэрай кватэры", true, gender = Gender.F),
        "корп" to NounAbbr("корпус корпуса корпусу корпус корпусам корпусе", true),
        "п" to NounAbbr("пункт пункта пункту пункт пунктам пункце", true),
        "пп" to NounAbbr("падпункт падпункта падпункту падпункт падпунктам падпункце", true),
        "арт" to NounAbbr("артыкул артыкула артыкулу артыкул артыкулам артыкуле", true),
        "ч" to NounAbbr("частка часткі частцы частку часткай частцы", true, gender = Gender.F),
        "гл" to NounAbbr("глава главы главе главу главой главе", true, gender = Gender.F),
        "т" to NounAbbr("том тома тому том томам томе", true),
        "с" to NounAbbr("старонка старонкі старонцы старонку старонкай старонцы", true, gender = Gender.F),
        "рыс" to NounAbbr("рысунак рысунка рысунку рысунак рысункам рысунку", true),
        "мал" to NounAbbr("малюнак малюнка малюнку малюнак малюнкам малюнку", true),
        "табл" to NounAbbr("табліца табліцы табліцы табліцу табліцай табліцы", true, gender = Gender.F),
        "дад" to NounAbbr("дадатак дадатку дадатку дадатак дадаткам дадатку", true),
        "заўв" to NounAbbr("заўвага заўвагі заўвазе заўвагу заўвагай заўвазе", true, gender = Gender.F),
        "кн" to NounAbbr("кніга кнігі кнізе кнігу кнігай кнізе", true, gender = Gender.F),
        "разд" to NounAbbr("раздзел раздзела раздзелу раздзел раздзелам раздзеле", true),
        "пав" to NounAbbr("паверх паверха паверху паверх паверхам паверсе", true),
        "пад" to NounAbbr("падезд падезду падезду падезд падездам падездзе", true),
        "тэл" to NounAbbr("тэлефон тэлефона тэлефону тэлефон тэлефонам тэлефоне", true),
        "в" to NounAbbr("вёска вёскі вёсцы вёску вёскай вёсцы", false, true, true),
        "пр" to NounAbbr("праспект праспекта праспекту праспект праспектам праспекце", false, true, true),
        "пл" to NounAbbr("плошча плошчы плошчы плошчу плошчай плошчы", false, true, true),
        "пас" to NounAbbr("пасёлак пасёлка пасёлку пасёлак пасёлкам пасёлку", false, true, true),
        "р" to NounAbbr("рака ракі рацы раку ракай рацы", false, true, true),
        "воз" to NounAbbr("возера возера возеру возера возерам возеры", false, true, true),
        "мкр" to NounAbbr("мікрараён мікрараёна мікрараёну мікрараён мікрараёнам мікрараёне", false, true, true),
        "зав" to NounAbbr("завулак завулка завулку завулак завулкам завулку", false, true, true),
        "бул" to NounAbbr("бульвар бульвара бульвару бульвар бульварам бульвары", false, true, true),
        "ст" to NounAbbr("станцыя станцыі станцыі станцыю станцыяй станцыі", false, true, true),
        "акад" to NounAbbr("акадэмік акадэміка акадэміку акадэміка акадэмікам акадэміку", false, true),
        "праф" to NounAbbr("прафесар прафесара прафесару прафесара прафесарам прафесару", false, true),
        "дац" to NounAbbr("дацэнт дацэнта дацэнту дацэнта дацэнтам дацэнце", false, true))
    /** Сокращение с точкой: перед ним предлог (по желанию); за ним римское (третья группа, его заменяем вместе с сокращением),
     * цифра, «+», «(», «:» номера (четвёртая) или слово с заглавной. */
    private val NOUN_ABBR_RE = Regex("""(?:(?<![\p{L}\d])(${BelNumbers.SIGN_PREP.keys.sortedByDescending { it.length }.joinToString("|")})\s+)?""" +
        """(?<![\p{L}\d.])(${NOUN_ABBRS.keys.sortedByDescending { it.length }.joinToString("|")})\.\s*""" +
        """(?-i:(?:((?:[IVX]|І(?=[IVXІХ]|\s*$))[IVXІХ]*)(?![\p{L}\d])|(?=([\d+(:]))|(?=[А-ЯЁІЎ])))""", RegexOption.IGNORE_CASE)
    // и «мм рт. ст. Далей» — ртутны слуп, не станцыя
    private val ROMAN_OR_DIGIT_BEHIND = Regex("""(?:[IVXІХ]+|\d+|рт\.)\s*$""")
    private val GP_RE = Regex("""(?<![\p{L}\d.])[Гг]\.\s*п\.\s*(?=[А-ЯЁІЎ])""")

    private val WORD_ABBR = listOf(
        Regex("""(?<!\p{L})і\s+г\.\s*д\.""") to "і гэтак далей ",
        Regex("""(?<!\p{L})і\s+т\.\s*д\.""") to "і так далей ",
        Regex("""(?<!\p{L})і\s+пад\.""") to "і падобнае ",
        // «гл. 3» — глава (nounAbbrs, раньше), «гл. вышэй», «гл. рыс.» — глядзі
        Regex("""(?<!\p{L})гл\.""") to "глядзі ",
        Regex("""(?<!\p{L})н\.\s*э\.""") to "нашай эры ",
        Regex("""(?<!\p{L})г\.\s*зн\.""") to "гэта значыць ",
        Regex("""(?<!\p{L})і\s+інш\.""") to "і іншае ",
        // ponytail: род «т. зв.» по концу следующего слова: «-ыя/-ія» мн., «-а/-я» ж., «-е/-о» н.; «мора» — ж.
        Regex("""(?<!\p{L})т(?:\.|ак)\s*зв\.(?=\s*\p{L}*[ыі]я(?!\p{L}))""") to "так званыя ",
        Regex("""(?<!\p{L})т(?:\.|ак)\s*зв\.(?=\s*\p{L}+[ая](?!\p{L}))""") to "так званая ",
        Regex("""(?<!\p{L})т(?:\.|ак)\s*зв\.(?=\s*\p{L}+[еоё](?!\p{L}))""") to "так званае ",
        Regex("""(?<!\p{L})т(?:\.|ак)\s*зв\.""") to "так званы ",
        Regex("""(?<!\p{L})напр\.""") to "напрыклад ",
        Regex("""(?<!\p{L})(на|па|з|са|ад|да|каля|ля|для|без|уздоўж|насупраць|вакол|пасля)\s+вул\.""") to "$1 вуліцы ",
        Regex("""(?<!\p{L})праз\s+вул\.""") to "праз вуліцу ",
        Regex("""(?<!\p{L})вул\.""") to "вуліца ",
        // после назвы: «Мінская вобл.», «у Мінскай вобл.» — род. по прилагательному, «Ленінскі р-н», «у Ленінскім р-не»
        Regex("""(?<=\p{L}(?:ай|яй|ой)\s)вобл\.""") to "вобласці ",
        Regex("""(?<!\p{L})вобл\.""") to "вобласць ",
        Regex("""(?<!\p{L})р-н(\p{L}*)""") to "раён$1")
    /** Смесь «?!» (Normalizer.punctuation сводит к ней «!?», «?!?») — вопрос, как у русского cis: «!» туда не доходит. */
    private val QUEST_EXCL_RE = Regex("\\?!|!\\?")
    /** Скобки — пауза запятой (pause_parens, как у русского); кавычки — без паузы, их выкидывает фильтр. */
    private val PARENS_RE = Regex("[()\\[\\]]")

    /** Смайлы словами по-белорусски ([Normalizer.smileys] даёт русские имена): глаголом, как имена эмодзи CLDR. Не проверено
     * носителем. */
    private val SMILEYS = mapOf("улыбается" to "усміхаецца", "смеётся" to "смяецца", "подмигивает" to "падміргвае", "грустит" to "сумуе",
        "плачет" to "плача", "показывает язык" to "паказвае язык", "удивляется" to "здзіўляецца", "целует" to "цалуе",
        "недоволен" to "незадаволены", "сердце" to "сэрца", "радуется" to "радуецца", "катается от смеха" to "качаецца ад смеху",
        "смеётся до слёз" to "смяецца да слёз", "злится" to "злуецца", "краснеет" to "чырванее", "цензура" to "цэнзура",
        "рука-лицо" to "рука-твар", "в шоке" to "у шоку", "в замешательстве" to "разгубіўся", "недоумевает" to "не разумее",
        "сомневается" to "сумняваецца", "моргает" to "міргае", "аплодирует" to "пляскае", "класс" to "клас",
        "большой палец вверх" to "вялікі палец уверх", "большой палец вниз" to "вялікі палец уніз", "спасибо" to "дзякуй", "да" to "так",
        "нет" to "не", "окей" to "окей", "огонь" to "агонь", "круто" to "крута", "машет рукой" to "махае рукой", "спит" to "спіць",
        "болеет" to "хварэе", "испуган" to "спалоханы", "закатывает глаза" to "закочвае вочы")
    private val SMILEY_REPEAT_RE = Regex("""(?<![а-яёіў])(усміхаецца|смяецца|сумуе|плача|падміргвае)(?:[\s.,…!?]+\1(?![а-яёіў]))+""")
    /** Пометка картинки AlReaderX «(- Иллюстрация -)» (на языке интерфейса читалки) — словом с точкой, по drop_images — без следа. */
    private val IMAGE_MARK_RE = Regex("""\(\s*[-–—]?\s*(?:Иллюстрация|Ілюстрацыя)\s*[-–—]?\s*\)""")

    /** Ссылки и почта, как у русского ([Normalizer.urlRe]): read_links — по частям, знаки словами («кропка», «касая рыса»,
     * «сабака»), цифры по одной, буквы — латиницей для [Latin]; link_domain (и у чтеца sr_link_word) — «спасылка, сайт» без
     * «www.»; drop_links, drop_emails — без следа. Путь к файлу книги — как ссылка. */
    private fun links(text: String, rules: Rules): String = if (!Normalizer.hasUrl(text)) text else Normalizer.urlRe.replace(text) { m ->
        val url = m.value
        val word = rules.on(Rules.LINK_WORD)
        when {
            url.startsWith('/') -> when {
                word || rules.on("link_domain") || rules.on("drop_links") -> ""
                rules.on("read_links") -> spellUrl(url)
                else -> url
            }
            '@' !in url && (word || rules.on("link_domain") && !rules.on("drop_links")) -> "спасылка, " + spellUrl(URL_HOST_RE.find(url)?.groupValues?.get(1) ?: url)
            rules.on(if ('@' in url) "drop_emails" else "drop_links") -> ""
            rules.on("read_links") -> spellUrl(url)
            else -> url
        }
    }
    private fun spellUrl(url: String): String {
        val sb = StringBuilder()
        for (c in url.lowercase().removePrefix("https://").removePrefix("http://")) when {
            c.isDigit() -> sb.append(' ').append(UNITS[c - '0']).append(' ')
            c in URL_SEPARATORS -> sb.append(' ').append(URL_SEPARATORS[c]).append(' ')
            else -> sb.append(c)
        }
        // одиночная буква («?x=1») — названием, а не транслитом
        return URL_SINGLE_LETTER_RE.replace(sb) { Latin.letterName(it.value[0]) ?: it.value }.replace(WS_RE, " ").trim()
    }
    private val URL_SEPARATORS = mapOf(':' to "двукроп'е", '/' to "касая рыса", '.' to "кропка", '?' to "пытальнік", '&' to "амперсанд",
        '=' to "роўна", '-' to "злучок", '_' to "падкрэсліванне", '#' to "рашотка", '%' to "працэнт", '+' to "плюс", '@' to "сабака",
        '~' to "тыльда")
    private val URL_SINGLE_LETTER_RE = Regex("""(?<![\p{L}])[a-z](?![\p{L}])""")
    private val URL_HOST_RE = Regex("""^(?:https?://)?(?:www ?\.)?([^/?#:]+)""", RegexOption.IGNORE_CASE)

    /** «У» после числа словами на гласную — «ў», как в правапісе: «2 узнагароды» → «дзве ўзнагароды», «5 удзельнікаў» → «пяці
     * ўдзельнікаў». Только после числительных ([NUM_VOWEL_WORDS]): свой текст автор и так пишет с «ў». */
    private fun uAfterNumber(s: String) = U_AFTER_WORD_RE.replace(s) { m ->
        if (m.groupValues[1] in NUM_VOWEL_WORDS) m.groupValues[1] + m.groupValues[2] + "ў" else m.value
    }
    private val U_AFTER_WORD_RE = Regex("""(?<![\p{L}])(\p{L}+)( +)у(?=\p{L}|\s)""")
    /** Все формы количественных и порядковых ([BelNumbers.cardinal], [BelNumbers.ordinal]), что кончаются на гласную. */
    private val NUM_VOWEL_WORDS: Set<String> by lazy {
        val base = (0L..19L) + (20L..90L step 10) + (100L..900L step 100) + listOf(1000L, 1_000_000L)
        buildSet {
            for (n in base) for (c in Case.values()) {
                for (fem in listOf(false, true)) addAll(BelNumbers.cardinal(n, c, fem).split(' '))
                for (g in Gender.values()) addAll(BelNumbers.ordinal(n, c, g).split(' '))
                addAll(BelNumbers.ordinal(n, c, plural = true).split(' '))
            }
            addAll(listOf("адно", "паўтара", "паўтары"))
        }.filterTo(HashSet()) { it.isNotEmpty() && it.last() in VOWELS }
    }

    /** Итоговая подготовка: латиница, числа, знаки, фильтр по таблице символов модели. Чтение — под ключами [Rules], теми же,
     * что у русского слоя (их нет у слоя в [BeLayer.unsupported]); служебное (знак ударения, невидимые символы, регистр,
     * апостроф, «!», «+» не перед гласной, фильтр) — всегда. */
    fun prepare(text: String, allowed: String, rules: Rules = Rules()): String {
        var s = text
        fun step(key: String, f: (String) -> String) { if (rules.on(key)) s = f(s) }
        // эмодзи — до снятия соединителей: «👨‍👩‍👧» держится на U+200D; смайлы «:)», «))» — общим разбором, белорусскими словами
        step("emoji") { Normalizer.smileys(Emoji.be?.apply(it) ?: it, { w -> SMILEYS[w] ?: w }, SMILEY_REPEAT_RE) }
        s = COMBINING_ACUTE_RE.replace(s) { "+" + it.groupValues[1] }.replace(ZERO_WIDTH_RE, "").replace(PRIVATE_USE_RE, " ")
        s = IMAGE_MARK_RE.replace(s, if (rules.on("drop_images")) "" else "ілюстрацыя.")
        s = links(s, rules)                           // адрес — до кодов и чисел: цифры в нём по одной
        // «·», «→» между словами — пауза; с symbol_names (у чтеца — sr_symbols) знак называется по имени в фильтре ниже
        if (!rules.on("symbol_names")) s = Normalizer.symbolPauses(s)
        s = OTHER_DIGIT_RE.replace(s) { Character.digit(it.value.codePointAt(0), 10).toString() }
        step("footnotes", ::footnotes)                // «[1]», «словах¹» — до скобок-пауз и чисел
        step("codes", ::codes)                        // «код 4829», «PIN 1234», «УНП …» — до латиницы и spell_cyr
        step("abbrev", ::nounAbbrs)                   // «д. 5», «в. Ракаў», «кн. II» — по заглавной и до римских
        // «°C», «°F» латиницей — до Latin, иначе она делает из «C» «к» и единица не находится
        s = FAHRENHEIT_RE.replace(CELSIUS_RE.replace(s, "°с"), "°ф")
        // «1,20 BYN», «5 USD» — валюта единицей, а не «бэ-уай-эн»; тоже до Latin
        step("currency") { CURRENCY_CODE_RE.replace(it) { m -> " " + CURRENCY_CODES.getValue(m.groupValues[1].uppercase()) } }
        s = K_THOUSAND_RE.replace(s, " тыс")   // без точки: в конце текста она стала бы точкой предложения
        step("units") { AMPERE_RE.replace(LATIN_UNIT_RE.replace(it) { m -> " " + LATIN_UNITS.getValue(m.groupValues[1]) }, "амп") }
        step("thousands", ::thousands)                // «1.000.000» — до номеров разделов «2.10.3»
        s = BelNumbers.roman(s, rules)                // «XX стагоддзе» — до Latin, иначе «экс-экс»
        step("homoglyphs", ::homoglyphs)              // «cок» латинской «c» — до Latin, иначе «кок»
        // латиницы в таблице символов модели нет вовсе — «FilmBell» раньше просто выпадало
        // «file.txt» — точка расширения словом, расширение без гласных — по буквам, как у русского fileExtRe
        step("latin") { FILE_EXT_RE.replace(it) { m -> " кропка " + m.groupValues[1].let { e -> if (e.none { c -> c in "aeiouy" }) e.uppercase() else e } } }
        step("latin") { Latin.translate(it, rules) }
        step("latin") { GREEK_RE.replace(it) { m -> GREEK.getValue(m.value[0]) } }
        step("letter_digit", ::cyrCodes)              // до lowercase: код узнаём по заглавным
        step("spell_cyr", ::spellCyr)                 // «БДУ», «І. Шамякін» — тоже по заглавным
        step("spoons", ::spoons)                      // «2 ст. л.» — до lowercase: точка перед заглавной — конец фразы
        // точка сокращения в конце предложения — после латиницы («5kg.» — уже «5 кг.»), но до lowercase
        val lat = s
        s = ABBR_END_RE.replace(lat) { m ->
            // «У 1941 г. Германія…»: перед годом только предлог — середина предложения, не конец
            val mid = m.value.startsWith("г") && lat.substring(m.range.last + 1).isNotBlank() && YEAR_LEAD_RE.matches(lat.substring(0, m.range.first))
            if (mid) m.value else "${m.value} $ABBR_DOT"
        }.lowercase()
        step("degrees") { ANGLE_APOS_RE.replace(it, "′") }
        // разделительный апостроф — «ъ», как в русском «подъезд»: без него «аб'ём» звучало «абём»; [LangStress] ищет слово без «ъ»
        s = SEPARATING_APOS_RE.replace(s, if ('ъ' in allowed) "ъ" else "").replace(APOSTROPHE, "")
        step("abbrev") { WORD_ABBR.fold(it) { t, (re, rep) -> re.replace(t, rep) } }   // до «г.» — «горад» и до года
        s = TILDE_RE.replace(s, "прыблізна ")
        // телефоны — разбор русского слоя ([Normalizer.phones]: +375, скобки, слитный номер), группы — нашими числами
        step("phones") { Normalizer.phones(it, ::group) }
        // знаки между числами — до «+5», «-5»: «x = -5» иначе теряло «роўна»
        step("arith", BelNumbers::arith)
        step("numbers") { MINUS_NUMBER_RE.replace(PLUS_NUMBER_RE.replace(it, " плюс "), " мінус ") }
        step("sections", BelNumbers::sectionLabels)   // «п. 4.12» — до дат и до «№»: номер, а не «чацвёртага снежня»
        step("numbers", BelNumbers::signs)            // «№ 5», «§ 3»
        // даты и время — до единиц: у них внутри цифры со знаками (12.05.2026, 21:30); время раньше: «а 10.05» — не дата
        step("times", BelNumbers::times)
        s = BelNumbers.dates(s, rules)
        step("sections", BelNumbers::sections)        // «2.10.3» — после дат: «12.05.2026» им уже прочитано
        s = BelNumbers.ordinals(s, rules)             // «1-га верасня», «5-ы клас», «1990-я»
        step("numbers") { BelNumbers.lists(BelNumbers.chapters(BelNumbers.compounds(it))) }   // «5-гадовы», «Глава 5», «: 1) …»
        s = BelNumbers.fractions(s, rules)            // «1/2 кг», «½» — до единиц: иначе «2 кг» из «1/2 кг»
        step("years", BelNumbers::abbreviations)      // «1999 г.» → «1999 год»
        s = BelNumbers.units(s, rules)                // «3 км» → «тры кіламетры»
        s = BelNumbers.standalone(s, rules)           // «руб.» без числа
        step("cases", BelNumbers::cases)              // «каля 3 гадзін», «2 кнігі», «з 5 сябрамі» — род и падеж
        s = numberWords(s, rules)
        step("spell_cyr", ::spellLower)               // «рб», «жкг» — после единиц: «км» без числа не сокращение
        step("numbers", ::uAfterNumber)              // «дзве ўзнагароды»: из «2 узнагароды» правапіс сам не получится
        s = ABBR_DOT_KEPT_RE.replace(s, ".").replace(ABBR_DOT, '.')
        s = QUEST_EXCL_RE.replace(s, "?").replace('!', '.')   // «!» модель не получает (служебный символ)
        if (rules.on("pause_parens")) s = PARENS_RE.replace(s, ", ")
        // тире — то, что знает модель: у cis-пака только «—», а Splitter делает из « - » «–»; на «—» и пауза тире
        s = s.replace('–', '—')
        s = PLUS_NOT_STRESS_RE.replace(s, " плюс ").replace("±", " плюс-мінус ").replace("≈", " прыблізна ").replace("&", " і ")
        val sb = StringBuilder(s.length)
        val names = rules.on("symbol_names")
        for (c in s) {
            when {
                c in allowed -> sb.append(c)
                // надстрочный знак не над гласной — без следа, пробелом он рвал бы слово
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {}
                // знак, который модель не знает, — словом (правило symbol_names, у чтеца sr_symbols); пунктуацию CLDR не называет
                names && SymbolNames.ofBe(c) != null -> sb.append(' ').append(SymbolNames.ofBe(c)).append(' ')
                else -> sb.append(' ')
            }
        }
        // скобки дали запятые: подряд — одна, в начале и рядом с точкой — не нужна
        val out = COMMA_RUN_RE.replace(sb.toString().replace(WS_RE, " "), ",")
        return SPACE_BEFORE_PUNCT_RE.replace(COMMA_EDGE_RE.replace(out, ""), "").replace(WS_RE, " ").trim()
    }
}
