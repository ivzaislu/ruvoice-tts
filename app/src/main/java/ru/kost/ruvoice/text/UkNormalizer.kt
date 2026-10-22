// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Украинская подготовка текста перед моделью, по образцу [BelNormalizer]:
 *  - эмодзи и смайлы украинскими словами (CLDR uk, [Emoji.uk]);
 *  - знак ударения над гласной → «+», невидимые символы — без следа;
 *  - разделительный апостроф → «ъ» (в символах cis-пака апостроф служебный, до модели не доходит; без «ъ» «м'ясо» звучит
 *    мягко «мясо»);
 *  - числа словами в роде и падеже, даты, время, единицы, валюты, дроби, римские ([UkNumbers]) — шаги в порядке
 *    белорусского [BelNormalizer.prepare];
 *  - ссылки, сноски, коды, сокращения, латиница ([UkLatin]), греческие, омоглифы, аббревиатуры и коды кириллицей, ложки,
 *    разряды точкой, «~» — те же шаги и ключи «Правил», что у белорусского;
 *  - «!» → «.», скобки → запятая по правилу pause_parens, «–» → «—»;
 *  - всё, чего нет в таблице символов модели, — словом ([SymbolNames.ofUk], правило symbol_names) или пробелом.
 * Регулярки — с IGNORE_CASE, регистрозависимое — в (?-i:…): встроенный (?i:) без флага на JVM кириллицу не видит.
 */
object UkNormalizer {

    /** Год словом за числом — порядковым в падеже слова: «2026 рік» → «… шостий рік», «у 2026 році» → «… шостому році». Слева не
     * цифра и не разряд: «1 000 рік», «12345 рік» — не год «000» и «2345». «роки», «років» — количество. */
    private val YEAR_RE = Regex("(?<![\\d.,])(?<!\\d[ \\u00A0\\u202F])(\\d{3,4})\\s*(рік|року|році|роком)(?![а-яєіїґ'’ʼ])", RegexOption.IGNORE_CASE)
    /** Разряды тысяч через пробел — только группами по три («1 000 000»): телефон «44 123 45 67» и «1 2 3» — отдельные числа. */
    private val DIGIT_GROUP_RE = Regex("(?<!\\d[ \\u00A0\\u202F]?)\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?![ \\u00A0\\u202F]?\\d)|\\d+")
    /** Десятичная дробь: целая часть (можно разрядами, как [DIGIT_GROUP_RE]), точка или запятая, цифры; не кусок «1.10.2». */
    private val DECIMAL_RE = Regex("""(?<![\d.,]|\d[ \x{A0}\x{202F}])(\d{1,3}(?:[ \x{A0}\x{202F}]\d{3})+|\d+)[.,](\d+)(?![.,]?\d)""")

    /** Число словами: до 999 999 999, длиннее — по цифрам; с годом — порядковым в падеже слова (правило years); дробь —
     * [UkNumbers.decimal] с падежом по предлогу; остальное — правило numbers. Буква вплотную к числу — через пробел: «8к74»
     * иначе слиплось бы в одно слово. */
    private fun numberWords(text: String, rules: Rules): String {
        fun Regex.words(src: String, f: (MatchResult) -> String) = replace(src) { m ->
            (if (src.getOrNull(m.range.first - 1)?.isLetter() == true) " " else "") + f(m) +
                (if (src.getOrNull(m.range.last + 1)?.isLetter() == true) " " else "")
        }
        var s = YEAR_RE.words(text) { m ->
            if (!rules.on("years")) return@words m.value
            val n = m.groupValues[1].toLongOrNull() ?: return@words m.value
            val word = m.groupValues[2].lowercase()
            // три цифры на 1 (не 11) и «рік» — количество: «виповнився 101 рік» → «сто один рік»; «988 рік» — год (количество
            // с «рік» в однине бывает только при числе на 1)
            if (word == "рік" && m.groupValues[1].length == 3 && n % 10 == 1L && n % 100 != 11L) return@words m.value
            val case = when (word) { "року" -> Case.GEN; "році" -> Case.PRE; "роком" -> Case.INS; else -> Case.NOM }
            UkNumbers.ordinal(n, case) + " " + word
        }
        if (!rules.on("numbers")) return s
        val d = s
        s = DECIMAL_RE.words(d) { m -> UkNumbers.decimal(m.groupValues[1].filter { it.isDigit() }, m.groupValues[2], UkNumbers.decimalCase(d, m.range.first)) }
        return DIGIT_GROUP_RE.words(s) { m ->
            val raw = m.value.filter { it.isDigit() }
            // нуль в начале трёх и больше цифр — код («044», «007»), как в телефоне; «05» — минуты и месяц
            if (raw.length > 2 && raw[0] == '0') UkNumbers.group(raw) else raw.toLongOrNull()?.let { UkNumbers.cardinal(it) } ?: UkNumbers.digits(raw)
        }
    }

    /** Ложки (правило spoons): «1 ч. л.» → «одна чайна ложка», «2 ст. л.» → «дві столові ложки»; дробь — родовий однини: «1/2
     * ч. л.» → «1/2 чайної ложки». Точка после «л» — точка предложения перед заглавной. */
    private fun spoons(t: String) = SPOON_RE.replace(t) { m ->
        val (num, kind) = m.destructured
        val n = num.toLongOrNull()
        val k = kind.lowercase()
        if (n == null) "$num ${SPOON_GEN.getValue(k)}"
        else UkNumbers.cardinal(n, Case.NOM, Gender.F) + " " + SPOONS.getValue(k).let { UkNumbers.plural(n, it.first, it.second, it.third) }
    }
    private val SPOONS = mapOf("ч" to Triple("чайна ложка", "чайні ложки", "чайних ложок"),
        "ст" to Triple("столова ложка", "столові ложки", "столових ложок"))
    private val SPOON_GEN = mapOf("ч" to "чайної ложки", "ст" to "столової ложки")
    private val SPOON_RE = Regex("""((?:\d+/)?\d+(?:[.,]\d+)?)\s*(ч|ст)\.?\s*л(?![\p{L}\d])(?:\.(?!\s*(?-i:\p{Lu})))?""", RegexOption.IGNORE_CASE)

    /** Градусы Цельсия латинской или кириллической «C» — к одной записи «°с» (кириллица), Фаренгейта — «°ф». Шкала через пробел —
     * только заглавной: «30° с ухилом» — не шкала. */
    private val CELSIUS_RE = Regex("(?-i:°(?:[CcСс]|\\s+[CС])(?!\\p{L}))", RegexOption.IGNORE_CASE)
    private val FAHRENHEIT_RE = Regex("(?-i:°(?:[FfФф]|\\s+[FФ])(?!\\p{L}))", RegexOption.IGNORE_CASE)
    /** Минуты угла ASCII-апострофом («42°30'») — штрихом, пока апостроф не снят ([UkNumbers.units]). */
    private val ANGLE_APOS_RE = Regex("(?<=\\d°\\s?\\d{1,2}\\s?)'")
    /** Код валюты после числа — её знак ([UkNumbers.units]): «1,20 UAH» — гривні, «5 USD» — долари, «100 BYN» — білоруські
     * рублі; без числа — как было (латиница). */
    private val CURRENCY_CODE_RE = Regex("(?<=\\d)\\s*(UAH|USD|EUR|RUB|RUR|GBP|BYN|BYR|Br|KZT|JPY|TRY|KRW|ILS|INR)(?![A-Za-z])",
        RegexOption.IGNORE_CASE)
    private val CURRENCY_CODES = mapOf("UAH" to "₴", "USD" to "$", "EUR" to "€", "RUB" to "₽", "RUR" to "₽", "GBP" to "£",
        "BYN" to "біл. руб", "BYR" to "біл. руб", "BR" to "біл. руб", "KZT" to "₸", "JPY" to "¥", "TRY" to "₺", "KRW" to "₩",
        "ILS" to "₪", "INR" to "₹")
    /** Единица латиницей после числа — ключом [UkNumbers.UNITS_MAP], пока латиница не стала буквами: «100 kWh», «5 MHz», «12V». */
    private val LATIN_UNIT_RE = Regex("""(?<=\d)\s?(kWh|MWh|mAh|kbps|Mbps|Gbps|km/h|kHz|MHz|GHz|Hz|km|cm|mm|ml|mg|kg|KB|kB|MB|GB|TB|Kb|Mb|Gb|kW|MW|GW|mV|W|V|rpm|dB)(?![A-Za-z])""")
    private val LATIN_UNITS = mapOf("kWh" to "квт·год", "MWh" to "мвт·год", "mAh" to "ма·год", "kbps" to "кбіт/с", "Mbps" to "мбіт/с",
        "Gbps" to "гбіт/с", "km/h" to "км/год", "kHz" to "кгц", "MHz" to "мгц", "GHz" to "ггц", "Hz" to "гц", "km" to "км", "cm" to "см",
        "mm" to "мм", "ml" to "мл", "mg" to "мг", "kg" to "кг", "KB" to "кб", "kB" to "кб", "MB" to "мб", "GB" to "гб", "TB" to "тб",
        "Kb" to "кбіт", "Mb" to "мбіт", "Gb" to "гбіт", "kW" to "квт", "MW" to "мвт", "GW" to "гвт", "mV" to "мв", "W" to "вт", "V" to "вольт",
        "rpm" to "об/хв", "dB" to "дб")
    /** Заглавная «А» и «В» за числом — ампер и вольт: строчные после опускания регистра — союз «а» и прийменник «в». */
    private val AMPERE_RE = Regex("""(?-i:(?<=\d\s?)А(?![\p{L}\d-]))""", RegexOption.IGNORE_CASE)
    private val VOLT_RE = Regex("""(?-i:(?<=\d\s?)В(?![\p{L}\d-]))""", RegexOption.IGNORE_CASE)
    /** «10k» — тисячі, как у русского kThousandRe. Заглавная — код или разрешение («4К», «8K»). */
    private val K_THOUSAND_RE = Regex("(?-i:(?<=\\d)[kк](?![\\p{L}\\d]))", RegexOption.IGNORE_CASE)
    /** Буква класу за числом заглавной: «у 5-А класі» — не нарощення порядкового («5-а» прочлось бы «п'ята»); перед словом
     * капсом («5-Й КЛАС») — заголовок, не трогаем. Перед «клас» — [classes], здесь остаток: «у п'ять а». */
    private val CLASS_LETTER_RE = Regex("""(?-i:(?<=\d)-([А-ЯЄІЇҐ])(?=\s+\p{Ll}|\s*[.,;:!?)»]|\s*$))""", RegexOption.IGNORE_CASE)
    /** «у 5-А класі» → «у п'ятому-а класі»: порядковое в падеже и числе слова «клас», буква — именем. Не проверено носителем. */
    private fun classes(t: String) = CLASS_RE.replace(t) { m ->
        val (c, pl) = CLASS_FORMS.getValue(m.groupValues[3])
        UkNumbers.ordinal(m.groupValues[1].toLong(), c, Gender.M, pl) + "-" + letter(m.groupValues[2][0]) + " "
    }
    private val CLASS_FORMS = mapOf("клас" to (Case.NOM to false), "класу" to (Case.GEN to false), "класі" to (Case.PRE to false),
        "класом" to (Case.INS to false), "класи" to (Case.NOM to true), "класів" to (Case.GEN to true), "класам" to (Case.DAT to true),
        "класами" to (Case.INS to true), "класах" to (Case.PRE to true))
    private val CLASS_RE = Regex("""(?-i:(?<![\d.,-])(\d{1,2})-([А-ЯЄІЇҐ])\s+(?=(клас(?:у|і|ом|и|ів|ам|ами|ах)?)(?!\p{L})))""",
        RegexOption.IGNORE_CASE)
    /** Слитный украинский номер: «0441234567» → «044 123-45-67», «+380441234567» → «+380 44 123 45 67» — дальше его делит на
     * группы [Normalizer.phones]; иначе «нуль чотириста сорок один мільйон…». */
    private val UA_PHONE_RE = Regex("""(?<![\d+])0(\d{2})(\d{3})(\d{2})(\d{2})(?!\d)""")
    private val UA_PHONE_PLUS_RE = Regex("""(?<!\d)\+380(\d{2})(\d{3})(\d{2})(\d{2})(?!\d)""")
    /** «т. зв.» — по роду следующего слова (как белорусский): «-і/-ї/-и» — множина, «-а/-я» — ж. р., «-е/-є/-о» — с. р.
     * ponytail: род по окончанию, без морфологии; не проверено носителем. */
    private val SO_CALLED_RE = Regex("""(?<!\p{L})т(?:\.|ак)\s*зв\.\s*(?=([\p{L}ъ]*))""", RegexOption.IGNORE_CASE)
    private fun soCalled(t: String) = SO_CALLED_RE.replace(t) { m ->
        val next = m.groupValues[1]
        when (next.lastOrNull()) {
            'і', 'ї', 'и' -> "так звані "
            'а', 'я' -> "так звана "
            'е', 'є', 'о' -> "так зване "
            else -> "так званий "
        }
    }

    // ---- коды, сноски, разряды ----

    /** Коды, пароли, PIN, номера карт, реквизиты — по цифрам группами (правило codes), как у русского и белорусского: «код
     * 482913» → «код чотири вісім два, дев'ять один три», карта «4276 1234 5678 9012», маска «•• 1234», «ЄДРПОУ 12345678». До
     * латиницы: «PIN», «CVC» узнаём, пока они латиницей. */
    private fun codes(text: String): String {
        fun spell(v: String, whole: Boolean = false) = Normalizer.spellDigits(v, whole, DIGIT_NAMES)
        var s = CARD_RE.replace(text) { m -> spell(listOf(1, 3, 4, 5, 6).mapNotNull { m.groups[it]?.value }.joinToString(" ")) }
        s = CARD_MASK_RE.replace(s) { m -> spell(m.groupValues[1], whole = true) }
        s = REQUISITE_RE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + spell(m.groupValues[3]) }
        s = POSTAL_RE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + spell(m.groupValues[3]) }
        s = CODE_AFTER_WORD_RE.replace(s) { m -> m.groupValues[1] + m.groupValues[2] + spell(m.groupValues[3]) }
        return CODE_BEFORE_WORD_RE.replace(s) { m -> spell(m.groupValues[1]) }
    }
    private val DIGIT_NAMES = Array(10) { UkNumbers.digits(it.toString()) }
    private const val CODE_WORD = """(?:пін-?код|код|пароль|одноразовий\s+пароль|pin|cvc2?|cvv2?|otp|code|passcode)"""
    private const val CODE_WORD_FORMS = """(?:пін-?код(?:у|а|ом|і|и|ів)?|код(?:у|а|ом|і|и|ів)?|парол(?:ь|я|ю|ем|і|ів)|pin|cvc2?|cvv2?|otp|code|passcode)"""
    /** «поштовий індекс 01001» — пять цифр сразу за словами, по цифрам (числом было бы «нуль одна тисяча один»); «поштовий
     * індекс змінився у 2020 році» — не индекс. */
    private val POSTAL_RE = Regex("""(?<!\p{L})(поштов\p{L}*\s+індекс\p{L}*)(\s*:?\s*)(\d{5})(?![\d\p{L}]|[.,]\d)""", RegexOption.IGNORE_CASE)
    private const val CODE_DIGITS = """\d{2,4}(?:[ -]\d{2,4}){1,2}|\d{3,8}"""
    private const val CODE_NOT_AFTER = """(?!\d|[.,:]\d|\s*(?:%|₴|\$|€|°|грн|руб|р\.|рік|рок|році|шт|хв|сек|раз|км|кг|м(?!\p{L})))"""
    private val CODE_AFTER_WORD_RE = Regex("""(?<![\p{L}\d])($CODE_WORD_FORMS)(?!\p{L})([^\d.!?\n]{0,40}?)(?<![\p{L}\d,.])($CODE_DIGITS)$CODE_NOT_AFTER""",
        RegexOption.IGNORE_CASE)
    private val CODE_BEFORE_WORD_RE = Regex("""(?<![\p{L}\d,.+])(\d{4,8})(?!\d|[.,]\d)(?=\s*[-–—:]?\s*(?:це\s+)?(?:ваш[аі]?\s+)?$CODE_WORD(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    private val CARD_RE = Regex("""(?<![\d.,])(\d{4})([ -])(\d{4})\2(\d{4})\2(\d{4})(?:\2(\d{1,3}))?(?![\d.,]\d|\d)""")
    private val CARD_MASK_RE = Regex("""(?-i:(?<![\p{L}\d])[*•·xх]{1,12}\s?(\d{4})(?!\d))""", RegexOption.IGNORE_CASE)
    private val REQUISITE_RE = Regex("""(?-i:(?<![\p{L}])(ЄДРПОУ|ЕДРПОУ|РНОКПП|ІПН|ІНН|ИНН|МФО|КПП)(\s*:?\s*)(\d[\d -]{2,20}\d)(?![\d.,]\d))""",
        RegexOption.IGNORE_CASE)

    /** Сноски — не число в тексте (правило footnotes, как у русского): «[1]», «{5}», номер вплотную к слову («дослідники2.»,
     * «словах¹», «інструмента(12)»). Слово — от трёх строчных; «м²» короче, «#Наука2025» — хештег. */
    private fun footnotes(t: String) = GLUED_FOOTNOTE_RE.replace(FOOTNOTE_RE.replace(t, ""), "").replace(DOUBLE_SPACE_RE, " ")
    private val FOOTNOTE_RE = Regex("""\[\d+\]|\{\d+\}""")
    private val GLUED_FOOTNOTE_RE = Regex("""(?-i:(?<=(?<![#@\p{L}])\p{L}?[а-яєіїґ'’ʼ]{3,40})(?:(\d{1,3})(?=[.,;:!?)»…—–]|$)|(?<=[еєіїюя])(\d{1,2})(?=\s)|""" +
        """[¹²³⁴⁵⁶⁷⁸⁹⁰]{1,3}(?=[\s.,;:!?)»…—–]|$)|\(\d{1,3}\)(?=[\s.,;:!?)»…—–]|$)))""", RegexOption.IGNORE_CASE)
    private val DOUBLE_SPACE_RE = Regex(" {2,}")

    /** Разряды точкой и у старых книг (правило thousands, как у русского): «1.000.000», «10,000 чол.» — одно число. */
    private fun thousands(t: String) = OLD_THOUSANDS_RE.replace(DOT_THOUSANDS_RE.replace(t) { it.value.replace(".", "") }) { it.groupValues[1] + "000" }
    private val DOT_THOUSANDS_RE = Regex("""(?<![\d.])(?<!\d,)\d{1,3}\.\d{3}\.\d{3}(?![\d.]|,\d)""")
    private val OLD_THOUSANDS_RE = Regex("""(?<![\d.,])([1-9]\d{0,2})[.,](000)(?![\d.,]\d|\d)""")

    // ---- сокращения ----

    /** Сокращения-существительные — словом в падеже предлога (правило abbrev, как у русского nounAbbrevs и белорусского).
     * Перед номером (цифра, римское, «+», «(», «:»): «буд. 5», «кв.», «корп.», «п.», «пп.», «підп.», «ст.» (стаття), «ч.»,
     * «гл.», «розд.», «т.», «с.», «стор.», «рис.», «мал.», «табл.», «дод.», «прим.», «кн.», «вип.», «пов.», «тел.», «арт.»;
     * перед назвою с заглавной: «м. Київ» — місто, «с.» — село, «смт» — селище міського типу, «р.» — річка, «оз.», «вул.»,
     * «просп.», «пр.», «пл.», «бул.», «пров.», «мкр.», «ст.» (станція), «акад.», «проф.», «доц.». «з/із/зі» перед місцем —
     * родовий («з м. Києва» → «з міста»), иначе падеж по [UkNumbers.SIGN_PREP]. До опускания регистра: назву узнаём по заглавной,
     * до римских: «кн. II» — «книга друга». Формы VESUM; не проверено носителем. */
    private fun nounAbbrs(text: String): String {
        val t = SMT_RE.replace(text, "смт.")
        return NOUN_ABBR_RE.replace(t) { m ->
            val prep = m.groupValues[1]
            val key = m.groupValues[2]
            val roman = m.groupValues[3]
            val end = roman.isEmpty() && t.substring(m.range.last + 1).isBlank()
            if (end && key.lowercase() !in END_ABBRS) return@replace m.value
            val digit = end || roman.isNotEmpty() || m.groupValues[4].isNotEmpty()
            val a = (if (digit) DIGIT_ABBRS else NAME_ABBRS)[key.lowercase()] ?: return@replace m.value
            // перед назвою — только строчная: «С. Петренко», «Р. П.» — инициалы; «XX ст. Україна» — століття, «5 м. Далі» — метр,
            // «мм рт. ст. Далі» — ртутний стовпчик
            if (!digit && (key[0].isUpperCase() || ROMAN_OR_DIGIT_BEHIND.containsMatchIn(t.substring(maxOf(0, m.range.first - 8), m.range.first))))
                return@replace m.value
            val p = prep.lowercase()
            val before = t.substring(maxOf(0, m.range.first - 60), m.range.first)
            val case = when {
                p in FROM_PREPS && a.place -> Case.GEN
                // «в/у/на» перед місцем: назва в місцевому («Києві», «Одесі») — місцевий при любом глаголе; назва в прямій формі
                // («Київ») — знахідний только после глагола движения («Їду в м. Київ»), иначе місцевий («проживає у м. Київ»)
                p in MOTION_PREPS && a.place && !LOC_NAME_RE.containsMatchIn(t.substring(m.range.last + 1).take(40)) &&
                    motionBefore(before) -> Case.ACC
                // «п. 2 ст. 15» — пункт статті: целое за частью в родовому
                p.isEmpty() && digit && key.lowercase() in WHOLE_ABBRS && PART_BEFORE_RE.containsMatchIn(before) -> Case.GEN
                p.isEmpty() && SEE_BEFORE_RE.containsMatchIn(before) -> Case.ACC
                else -> UkNumbers.SIGN_PREP[p] ?: Case.NOM
            }
            (if (prep.isEmpty()) "" else "$prep ") + a.forms[case.ordinal] + (if (end) "." else " ") +
                if (roman.isEmpty()) "" else UkNumbers.ordinal(UkNumbers.romanValue(roman), case, a.gender) + " "
        }
    }
    /** «пов.» за числом (оголошення): после предлога — порядковое в падеже предлога («на 5 пов.» → «на п'ятому поверсі», «з 5
     * пов.» → «з п'ятого поверху»); без предлога перед словом — прикметник («5 пов. будинок» → «п'ятиповерховий будинок», род —
     * по окончанию слова); иначе «п'ятий поверх». Точка в конце куска — конец предложения. Не проверено носителем. */
    private fun floors(text: String): String = FLOOR_RE.replace(text) { m ->
        val prep = m.groupValues[1]
        val n = m.groupValues[2].toLongOrNull() ?: return@replace m.value
        val rest = text.substring(m.range.last + 1)
        val end = if (rest.isBlank()) "." else " "
        val p = prep.lowercase()
        if (p.isEmpty()) FLOOR_NOUN_RE.find(rest)?.let { w -> FLOOR_ADJ[w.groupValues[1]] }?.let { ending ->
            return@replace UkNumbers.compoundStem(n.toInt(), one = "одно", adj = true) + "поверх" + ending + end
        }
        val case = if (p in FROM_PREPS) Case.GEN else UkNumbers.SIGN_PREP[p] ?: Case.NOM
        (if (prep.isEmpty()) "" else "$prep ") + UkNumbers.ordinal(n, case) + " " + FLOOR_FORMS[case.ordinal] + end
    }
    private val FLOOR_FORMS = "поверх поверху поверху поверх поверхом поверсі".split(' ')
    private val FLOOR_RE = Regex("""(?:(?<![\p{L}\d])(${UkNumbers.SIGN_PREP.keys.sortedByDescending { it.length }.joinToString("|")})\s+)?""" +
        """(?<![\d.,])(\d{1,3})\s*пов\.\s*""", RegexOption.IGNORE_CASE)
    /** Слово за «5 пов.» — строчное; прикметник только перед зданием из [FLOOR_ADJ] (окончание прикметника по его форме). */
    private val FLOOR_NOUN_RE = Regex("""^(?-i:(\p{Ll}{3,}))(?!\p{L})""", RegexOption.IGNORE_CASE)
    private val FLOOR_ADJ = listOf("будинок", "дім", "корпус", "комплекс", "гуртожиток").associateWith { "овий" } +
        listOf("будинку", "корпусу", "комплексу", "гуртожитку").associateWith { "ового" } +
        listOf("будинки").associateWith { "ові" } + listOf("будинків").associateWith { "ових" } +
        listOf("будівля", "новобудова").associateWith { "ова" } + listOf("будівлі", "новобудови").associateWith { "ової" }
    private class NounAbbr(forms: String, val place: Boolean = false, val gender: Gender = Gender.M) {
        val forms = forms.split(' ').map { it.replace('_', ' ') }
    }
    private val FROM_PREPS = setOf("з", "із", "зі", "зо")
    private val MOTION_PREPS = setOf("в", "у", "на")
    private val DIGIT_ABBRS = mapOf(
        "буд" to NounAbbr("будинок будинку будинку будинок будинком будинку"),
        "кв" to NounAbbr("квартира квартири квартирі квартиру квартирою квартирі", gender = Gender.F),
        "корп" to NounAbbr("корпус корпусу корпусу корпус корпусом корпусі"),
        "п" to NounAbbr("пункт пункту пункту пункт пунктом пункті"),
        "пп" to NounAbbr("пункти пунктів пунктам пункти пунктами пунктах"),
        "підп" to NounAbbr("підпункт підпункту підпункту підпункт підпунктом підпункті"),
        "ст" to NounAbbr("стаття статті статті статтю статтею статті", gender = Gender.F),
        "ч" to NounAbbr("частина частини частині частину частиною частині", gender = Gender.F),
        "гл" to NounAbbr("глава глави главі главу главою главі", gender = Gender.F),
        "розд" to NounAbbr("розділ розділу розділу розділ розділом розділі"),
        "т" to NounAbbr("том тому тому том томом томі"),
        "с" to NounAbbr("сторінка сторінки сторінці сторінку сторінкою сторінці", gender = Gender.F),
        "стор" to NounAbbr("сторінка сторінки сторінці сторінку сторінкою сторінці", gender = Gender.F),
        "рис" to NounAbbr("рисунок рисунка рисунку рисунок рисунком рисунку"),
        "мал" to NounAbbr("малюнок малюнка малюнку малюнок малюнком малюнку"),
        "табл" to NounAbbr("таблиця таблиці таблиці таблицю таблицею таблиці", gender = Gender.F),
        "дод" to NounAbbr("додаток додатка додатку додаток додатком додатку"),
        "прим" to NounAbbr("примітка примітки примітці примітку приміткою примітці", gender = Gender.F),
        "кн" to NounAbbr("книга книги книзі книгу книгою книзі", gender = Gender.F),
        "вип" to NounAbbr("випуск випуску випуску випуск випуском випуску"),
        "пов" to NounAbbr("поверх поверху поверху поверх поверхом поверсі"),
        "тел" to NounAbbr("телефон телефону телефону телефон телефоном телефоні"),
        "арт" to NounAbbr("артикул артикулу артикулу артикул артикулом артикулі"))
    private val NAME_ABBRS = mapOf(
        "м" to NounAbbr("місто міста місту місто містом місті", true, Gender.N),
        "с" to NounAbbr("село села селу село селом селі", true, Gender.N),
        "смт" to NounAbbr("селище_міського_типу селища_міського_типу селищу_міського_типу селище_міського_типу " +
            "селищем_міського_типу селищі_міського_типу", true, Gender.N),
        "р" to NounAbbr("річка річки річці річку річкою річці", true, Gender.F),
        "оз" to NounAbbr("озеро озера озеру озеро озером озері", true, Gender.N),
        "вул" to NounAbbr("вулиця вулиці вулиці вулицю вулицею вулиці", true, Gender.F),
        "просп" to NounAbbr("проспект проспекту проспекту проспект проспектом проспекті", true),
        "пр" to NounAbbr("проспект проспекту проспекту проспект проспектом проспекті", true),
        "пл" to NounAbbr("площа площі площі площу площею площі", true, Gender.F),
        "бул" to NounAbbr("бульвар бульвару бульвару бульвар бульваром бульварі", true),
        "пров" to NounAbbr("провулок провулка провулку провулок провулком провулку", true),
        "мкр" to NounAbbr("мікрорайон мікрорайону мікрорайону мікрорайон мікрорайоном мікрорайоні", true),
        "мкрн" to NounAbbr("мікрорайон мікрорайону мікрорайону мікрорайон мікрорайоном мікрорайоні", true),
        "ст" to NounAbbr("станція станції станції станцію станцією станції", true, Gender.F),
        "акад" to NounAbbr("академік академіка академіку академіка академіком академікові"),
        "проф" to NounAbbr("професор професора професору професора професором професорові"),
        "доц" to NounAbbr("доцент доцента доценту доцента доцентом доцентові"))
    /** Сокращение с точкой: перед ним предлог (по желанию); за ним римское (третья группа, его заменяем вместе с сокращением),
     * цифра, «+», «(», «:» номера (четвёртая) или слово с заглавной. */
    private val NOUN_ABBR_RE = Regex("""(?:(?<![\p{L}\d])(${UkNumbers.SIGN_PREP.keys.sortedByDescending { it.length }.joinToString("|")})\s+)?""" +
        """(?<![\p{L}\d.])(${(DIGIT_ABBRS.keys + NAME_ABBRS.keys).sortedByDescending { it.length }.joinToString("|")})\.\s*""" +
        """(?-i:(?:((?:[IVX]|І(?=[IVXІХ]|\s*$))[IVXІХ]*)(?![\p{L}\d])|(?=([\d+(:]))|(?=\p{Lu})|(?=\s*$)))""", RegexOption.IGNORE_CASE)
    /** Сокращение в конце куска («Дзвоніть за тел. Ми чекаємо.» — Splitter режет после «тел.» перед заглавной): словом в падеже
     * предлога, точка — конец предложения. */
    private val END_ABBRS = setOf("тел")
    /** «м.» после числа, римского, «рт.», «кв.», «куб.», «пог.» — метр, а не місто: «50 кв. м. Далі», «5 млн куб. м. Газу». */
    private val ROMAN_OR_DIGIT_BEHIND = Regex("""(?-i:(?:[IVXІХ]+|\d+|рт\.|(?<!\p{L})(?:кв|куб|пог|к)\.?)\s*$)""", RegexOption.IGNORE_CASE)
    /** Назва за сокращением в місцевому: «Києві», «Львові», «Одесі», «Житомирі» (-і, -ї, в т. ч. -ові, -еві, -єві). */
    private val LOC_NAME_RE = Regex("""^\s*\p{L}+[іїІЇ](?![\p{L}'’ʼ])""", RegexOption.IGNORE_CASE)
    /** Глагол движения перед предлогом (до двух слов между ними, без знака препинания): «Їду в м. Київ», «Приїхав сьогодні у
     * м. Київ». Только целые словоформы из [MOTION_WORDS]: «їдять», «їдальні», «прибуток» — не движение. */
    private fun motionBefore(before: String): Boolean =
        // точка известного сокращения («з м. Одеси в м. Київ») — не конец фразы, само сокращение — не слово
        WORD_RE.findAll(ABBR_WITH_DOT_RE.replace(before, " ").substringAfterLast(',').split(CLAUSE_END_RE).last())
            .map { it.value.lowercase() }.toList().takeLast(3).any { it in MOTION_WORDS }
    private val ABBR_WITH_DOT_RE = Regex("""(?<![\p{L}\d])(?:${(DIGIT_ABBRS.keys + NAME_ABBRS.keys + listOf("обл", "див", "напр", "ім")).distinct()
        .sortedByDescending { it.length }.joinToString("|")})\.""", RegexOption.IGNORE_CASE)
    private val WORD_RE = Regex("""\p{L}+(?:['’ʼ]\p{L}+)*""")
    private val CLAUSE_END_RE = Regex("""[.;:!?…—–()«»"]""")
    /** Словоформы глаголов (и нескольких существительных) движения: основа × окончания; «_» — пустое окончание. Не проверено
     * носителем. */
    private val MOTION_WORDS: Set<String> = buildSet {
        fun add(stems: String, ends: String) { for (s in stems.split(' ')) for (e in ends.split(' ')) add(s + e.replace("_", "")) }
        val ride = "по при пере ви за до"
        add("їд " + ride.split(' ').joinToString(" ") { it + "їд" }, "у еш е емо ете уть")
        add("їха " + ride.split(' ').joinToString(" ") { it + "їха" }, "в ла ло ли ти")
        add(listOf("при", "пере", "ви", "за", "до").joinToString(" ") { it + "їжджа" }, "ю єш є ємо єте ють в ла ли ти")
        add("ід йд", "у еш е емо ете уть ти")
        add("ішо йшо пішо прийшо зайшо увійшо вийшо дійшо", "в")
        add("ішл йшл пішл прийшл зайшл увійшл вийшл дійшл", "а и о")
        add("пі прий зай ді", "ду деш де демо дете дуть ти")
        // всі особи теперішнього (майбутнього), минулий, інфінітив; приставки — «по при пере ви за до від»
        val pref = listOf("", "по", "при", "пере", "ви", "за", "до", "від")
        fun verb(vararg forms: String) = pref.forEach { p -> forms.forEach { f -> add(p + f) } }
        verb("лечу", "летиш", "летить", "летимо", "летите", "летять", "летів", "летіла", "летіло", "летіли", "летіти")
        verb("літаю", "літаєш", "літає", "літаємо", "літаєте", "літають", "літав", "літала", "літали", "літати")
        verb("пливу", "пливеш", "пливе", "пливемо", "пливете", "пливуть", "плив", "пливла", "пливли", "пливти", "плисти")
        verb("біжу", "біжиш", "біжить", "біжимо", "біжите", "біжать", "біг", "бігла", "бігли", "бігти")
        verb("мчу", "мчиш", "мчить", "мчимо", "мчите", "мчать", "мчав", "мчала", "мчали", "мчати")
        verb("їжджу", "їздиш", "їздить", "їздимо", "їздите", "їздять", "їздив", "їздила", "їздили", "їздити")
        verb("ходжу", "ходиш", "ходить", "ходимо", "ходите", "ходять", "ходив", "ходила", "ходили", "ходити")
        add("прибу", "в ла ли де ду деш демо дете дуть ти ваю ваєш ває ваємо ваєте вають вав вала вали вати")
        add("вируши", "в ла ли ти")
        add("вируш", "у иш ить имо ите ать")
        add("вируша", "ю єш є ємо єте ють в ла ли ти")
        add("поверну", "вся лася лися лись ся тися")
        add("поверн", "ешся еться емося етеся уться")
        add("поверта", "юся єшся ється ємося єтеся ються вся лася лися тися")
        add("відправи", "вся лася лися лись ли")
        add("переселився переселилася переселилися", "_")
        add("пряму", "ю єш є ємо єте ють вав вала вали вати")
        add("переїзд поїздка поїздку повернення переселення подорож", "_")
    }
    /** «див. с. 45», «Див. табл. 3» — знахідний: «дивись сторінку», «дивись таблицю»; и дальше по перечню через «і», «та»,
     * запятую: «див. мал. 4 і табл. 2» — «і таблицю»; так же после «читай(те)»: «Читайте розд. 3, т. 2, с. 45» — «сторінку». */
    private val SEE_BEFORE_RE = Regex("""(?<!\p{L})(?:див\.?|дивись|дивіться|читай|читайте)\s*(?:\p{L}{1,6}\.\s*\d+[\d.,]*\s*(?:,|і|й|та)\s+)*$""",
        RegexOption.IGNORE_CASE)
    /** Часть документа с номером перед целым: «п. 2 ст. 15» — «пункт два статті п'ятнадцять», «ч. 1 ст. 5». Не проверено носителем. */
    private val PART_BEFORE_RE = Regex("""(?<!\p{L})(?:п|пп|підп|ч)\.\s*\d+(?:[.,]\d+)*\s*$""", RegexOption.IGNORE_CASE)
    private val WHOLE_ABBRS = setOf("ст", "розд", "гл", "дод")
    /** «смт Ворохта» пишут без точки: перед назвою — с точкой, как прочие сокращения [NOUN_ABBR_RE]. */
    private val SMT_RE = Regex("""(?<![\p{L}\d.])смт(?=\s+(?-i:\p{Lu}))""", RegexOption.IGNORE_CASE)

    /** Сокращения словами (правило abbrev): «і т. д.», «і т. ін.», «і т. п.», «та ін.», «див.», «напр.», «ім.», «до н. е.»,
     * «вул.» без назви, «обл.» после прилагательного (падеж по его окончанию), «р-н». До опускания регистра: «н. е.» строчными
     * — эра, «Н. Е.» — инициалы. Пробел в конце замены: «напр.Київ» — не «наприкладкиїв». Не проверено носителем. */
    private val WORD_ABBR = listOf(
        Regex("""(?<!\p{L})і\s+т\.\s*д\.""", RegexOption.IGNORE_CASE) to "і так далі ",
        Regex("""(?<!\p{L})і\s+т\.\s*ін\.""", RegexOption.IGNORE_CASE) to "і таке інше ",
        Regex("""(?<!\p{L})і\s+т\.\s*п\.""", RegexOption.IGNORE_CASE) to "і тому подібне ",
        Regex("""(?<!\p{L})(і|й|та)\s+ін\.""", RegexOption.IGNORE_CASE) to "$1 інші ",
        Regex("""(?<!\p{L})і\s+под\.""", RegexOption.IGNORE_CASE) to "і подібне ",
        Regex("""(?<!\p{L})див\.""", RegexOption.IGNORE_CASE) to "дивись ",
        Regex("""(?<!\p{L})напр\.""", RegexOption.IGNORE_CASE) to "наприклад ",
        Regex("""(?<!\p{L})ім\.""", RegexOption.IGNORE_CASE) to "імені ",
        Regex("""(?-i:(?<!\p{L})н\.\s*е\.)""", RegexOption.IGNORE_CASE) to "нашої ери ",
        Regex("""(?<!\p{L})вул\.""", RegexOption.IGNORE_CASE) to "вулиця ",
        // после назви: «Київська обл.», «у Київській обл.», «з Київською обл.»
        Regex("""(?<=\p{L}(?:ій|ої)\s)обл\.""", RegexOption.IGNORE_CASE) to "області ",
        Regex("""(?<=\p{L}ою\s)обл\.""", RegexOption.IGNORE_CASE) to "областю ",
        Regex("""(?<!\p{L})обл\.""", RegexOption.IGNORE_CASE) to "область ",
        Regex("""(?<!\p{L})р-н(\p{L}*)""", RegexOption.IGNORE_CASE) to "район$1")

    /** Точка сокращения, закрывшая предложение: единица или год за числом («2024 р. Тепер», «7,5 грн.» в конце текста), «рр.» за
     * «-х», «і т. д.», «та ін.», «до н. е.», «обл.». Splitter после них не режет, а разворот точку съедает — ставим за ней метку
     * [ABBR_DOT]: она переживает разворот и в конце становится точкой; сокращение не развернулось (правило выключено) — точка
     * его осталась, и метка уходит без следа, иначе было бы «км..». «р.» за «дд.мм» — річка, не год. Регистр — до lowercase. */
    private val ABBR_END_RE = Regex("""(?:(?-i:(?<=\d-х[ \x{A0}])рр)|(?-i:(?<=\d[ \x{A0}]?|(?:тис|млн|млрд|трлн)\.?[ \x{A0}]))""" +
        // сокращения единиц ([UkNumbers.UNITS_MAP] на букву; регистр — любой: «МВт.», «кВт.»), «рр», «р» не за «дд.мм»
        """(?:${UkNumbers.UNITS_MAP.keys.filter { it.last().isLetter() && it[0] != '°' }.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }}""" +
        """|(?-i:рр|(?<!\.\d\d[ \x{A0}]?)р))""" +
        // только там, где [WORD_ABBR] их развернёт: иначе точка осталась бы двойной
        """|(?-i:(?<=(?<!\p{L})[ій]\s{1,3}т\.\s{0,3})(?:д|ін|п)|(?<=(?<!\p{L})(?:і|й|та)\s{1,3})(?:ін|под)|(?<=(?<!\p{L})н\.\s{0,3})е|""" +
        """(?<=\p{L}[ \x{A0}])обл))\.(?-i:(?=\s+\p{Lu}|\s*$))""", RegexOption.IGNORE_CASE)
    /** Метка точки сокращения ([ABBR_END_RE]); такой символ в самом тексте [prepare] сразу делает пробелом. */
    private const val ABBR_DOT = '\uE000'
    private val ABBR_DOT_KEPT_RE = Regex("\\.\\s*$ABBR_DOT")
    /** Начало фразы до «р.»/«рр.»: предлог и год (или два года, или десятилетие на «-х») — «У 1941 », «З 1941–1945 », «У 1990-х ».
     * Не «Народився у 1990 р. Його…»: там слова перед предлогом, и это конец предложения. */
    private val YEAR_LEAD_RE = Regex("""\P{L}*(?:у|в|з|із|зі|до|від|по|після|близько|перед)\s+(?:\d{4}(?:\s*[–—-]\s*\d{4})?|\d{2,4}-х)[ \x{A0}]?""",
        RegexOption.IGNORE_CASE)
    /** «~5 км» — «приблизно». */
    private val TILDE_RE = Regex("(?<![\\p{L}\\d])~\\s*(?=\\d)")
    /** Пометка картинки AlReaderX «(- Иллюстрация -)» (на языке интерфейса читалки) — словом с точкой, по drop_images — без следа. */
    private val IMAGE_MARK_RE = Regex("""\(\s*[-–—]?\s*(?:Иллюстрация|Ілюстрація)\s*[-–—]?\s*\)""", RegexOption.IGNORE_CASE)

    // ---- латиница, греческие, омоглифы ----

    /** Омоглифы: латинская буква-двойник в кириллическом слове — опечатка раскладки («cік», «iнший», «Kиїв»), латиница прочла бы
     * её по-английски («кік»), а «i» выпала бы фильтром («нший»). Слово меняем, только если вся латиница в нём — двойники, кириллицы
     * не меньше и латиница не идёт подряд по три: «файлmicrosoft», марку «TOYOTAвський», «TAXIст» не трогаем. «ï» — «ї». */
    private val HOMOGLYPHS = "aceopxyiABCEHIKMOPTXYïÏ".zip("асеорхуіАВСЕНІКМОРТХУїЇ").toMap()
    private val LETTERS_RE = Regex("\\p{L}+")
    private val LATIN_RUN_RE = Regex("[A-Za-zïÏ]{3}")
    private fun homoglyphs(text: String) = LETTERS_RE.replace(text) { m ->
        val w = m.value
        fun latin(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == 'ï' || c == 'Ï'
        val lat = w.count(::latin)
        val cyr = w.count { it in 'а'..'я' || it in 'А'..'Я' || it in "ёєіїґЁЄІЇҐ" }
        if (lat == 0 || cyr < lat || w.any { latin(it) && it !in HOMOGLYPHS } || LATIN_RUN_RE.containsMatchIn(w)) w
        else w.map { HOMOGLYPHS[it] ?: it }.joinToString("")
    }
    /** «file.txt» — точка расширения словом, расширение без гласных — по буквам, как у русского fileExtRe. */
    private val FILE_EXT_RE = Regex("""(?<=[\p{L}\d_-])\.(fb2|epub|txt|pdf|docx?|rtf|mobi|zip|rar|jpe?g|png|gif|mp3|mp4|apk|exe|html?|xlsx?|pptx?|csv|json)(?![\p{L}\d])""",
        RegexOption.IGNORE_CASE)
    /** Одиночная греческая буква — названием («кут α», «α-розпад», «π»), как у русского greek; слова греческими не трогаем. */
    private val GREEK = mapOf('α' to "альфа", 'β' to "бета", 'γ' to "гамма", 'δ' to "дельта", 'ε' to "епсилон", 'ζ' to "дзета",
        'η' to "ета", 'θ' to "тета", 'ι' to "йота", 'κ' to "каппа", 'λ' to "лямбда", 'μ' to "мю", 'ν' to "ню", 'ξ' to "ксі", 'π' to "пі",
        'ρ' to "ро", 'σ' to "сигма", 'τ' to "тау", 'υ' to "іпсилон", 'φ' to "фі", 'χ' to "хі", 'ψ' to "псі", 'ω' to "омега",
        'Δ' to "дельта", 'Σ' to "сигма", 'Ω' to "омега", 'Π' to "пі", 'Λ' to "лямбда", 'Γ' to "гамма", 'Θ' to "тета", 'Φ' to "фі",
        'Ψ' to "псі")
    private val GREEK_RE = Regex("(?<!\\p{L})[α-ωΑ-Ω](?!\\p{L})")

    // ---- аббревиатуры и коды кириллицей ----

    /** Код кириллицей заглавными — буквы и цифры, можно через дефис: «8К74», «2А3», «Т-34» (как русский cyrCodes). Смешанный
     * регистр («Ан-225», «КрАЗ-255») — слово, не трогаем. Одна буква — именем, отдельным словом: «8К74» → «вісім к+а сімдесят
     * чотири»; несколько букв подряд — дальше [spellCyr] («БТР-4» → «бе-те-ер-чотири»). Число с единицей вплотную («5Т») и
     * буква класу («5-А») не трогаем. */
    private fun cyrCodes(text: String) = CYR_CODE_RE.replace(text) { m ->
        val runs = CODE_RUN_RE.findAll(m.value).map { it.value }.toList()
        if (runs[0][0].isDigit() && (runs.size == 2 && runs[1].lowercase() in UkNumbers.UNITS_MAP || runs.size == 3 && runs[1] == "-"))
            return@replace m.value
        runs.withIndex().joinToString("") { (i, r) ->
            (if (i > 0 && r != "-" && runs[i - 1] != "-") " " else "") + (if (r.length == 1 && r[0].isLetter()) UkLayer.letterName(r[0]) ?: r else r)
        }
    }
    private val CYR_CODE_RE = Regex("""(?-i:(?<![\p{L}\d-])(?=[А-ЯЄІЇҐ\d-]*[А-ЯЄІЇҐ])(?=[А-ЯЄІЇҐ\d-]*\d)[А-ЯЄІЇҐ\d]+(?:-[А-ЯЄІЇҐ\d]+)*(?![\p{L}\d]|-[\p{L}\d]))""",
        RegexOption.IGNORE_CASE)
    private val CODE_RUN_RE = Regex("""(?-i:[А-ЯЄІЇҐ]+|\d+|-)""", RegexOption.IGNORE_CASE)
    /** Аббревиатура кириллицей по буквам (правило spell_cyr), как у русского [Abbrev]: «ЗСУ» → «зе-ес-у», «США» → «ес-ша-а»,
     * «ЄС» → «є-ес». Токен — 2–6 заглавных; по буквам, если гласных нет, если в [SPELL] или из трёх букв с одной гласной с краю
     * («КНУ», «СБУ»); иначе слово («НАТО», «ООН», «ЗНО»). Рядом слово капсом — заголовок («УКРАЇНА ПОНАД УСЕ»): с краю гласную
     * не берём. Инициалы перед фамилией — именем буквы без точки: «Т. Г. Шевченко» → «те ге шевченко». */
    private fun spellCyr(text: String): String {
        val inits = INITIAL_RE.replace(text) { m ->
            letter(m.value[0]) + if (text.getOrNull(m.range.last + 1)?.isLetter() == true) " " else ""
        }
        return CYR_TOKEN_RE.replace(inits) { m ->
            val t = m.value
            // единица за числом («16 ГБ») — её читает [UkNumbers.units], не по буквам
            if (t.lowercase() in UkNumbers.UNITS_MAP && DIGIT_BEFORE_RE.containsMatchIn(inits.substring(maxOf(0, m.range.first - 2), m.range.first)))
                return@replace t
            val caps = CAPS_BEFORE_RE.containsMatchIn(inits.substring(maxOf(0, m.range.first - 40), m.range.first)) ||
                CAPS_AFTER_RE.containsMatchIn(inits.substring(m.range.last + 1, minOf(inits.length, m.range.last + 41)))
            val edge = !caps && t.length == 3 && t.count { it in CYR_VOWELS } == 1 && (t[0] in CYR_VOWELS || t[2] in CYR_VOWELS) &&
                t.none { it in "ЙЬ" } && t.lowercase() !in EDGE_WORDS
            if (t.none { it in CYR_VOWELS } || t in SPELL || edge) t.map(::letter).joinToString("-") else t
        }
    }
    /** Строчные без гласных — сокращение («кмн», «рнокпп»: так пишут в чатах), как у русского lowerAbbr; единицы без числа и
     * междометия — [LOWER_NOT_ABBR]. После чисел и единиц: «км» без числа не сокращение. */
    private fun spellLower(text: String) = LOWER_TOKEN_RE.replace(text) { m ->
        val t = m.value
        if (t.none { it.uppercaseChar() in CYR_VOWELS || it in "йь" } && t.toSet().size > 1 && t !in LOWER_NOT_ABBR)
            t.map(::letter).joinToString("-") else t
    }
    /** Имя буквы без ударения ([UK_LETTERS]); русские «ё ы э ъ» — по-украински. */
    private fun letter(c: Char) = LETTER_NAMES[c.uppercaseChar()] ?: c.lowercase()
    private val LETTER_NAMES: Map<Char, String> = UK_LETTERS.entries.associate { (k, v) -> k.uppercaseChar() to v.replace("+", "") } +
        mapOf('Ё' to "йо", 'Ы' to "и", 'Э' to "е", 'Ъ' to "твердий знак")
    private const val CYR_VOWELS = "АЕЄИІЇОУЮЯЁЫЭ"
    /** С гласными, но по буквам; не проверено носителем. */
    private val SPELL = setOf("ЄС", "АЕС", "АТ", "ПАТ", "ЄІБ", "ОДА", "ОСББ", "ЄСПЛ", "ОБСЄ", "ЄБРР", "ОАЕ", "ЕС", "ЄДРПОУ", "ЕДРПОУ",
        "РНОКПП")
    /** Частые слова из трёх букв с гласной с краю — не сокращение и капсом. ponytail: стоп-лист, пополнять по жалобам. */
    private val EDGE_WORDS = setOf("хто", "два", "три", "сто", "для", "дні", "дно", "зло", "сни", "все", "всі", "вся", "зле", "вже",
        "пси", "пса", "шви", "акт", "арт", "ост", "спа", "бра", "зно", "тре", "мла", "мрі", "дня", "сну")
    private val DIGIT_BEFORE_RE = Regex("""\d\s?$""")
    private val CYR_TOKEN_RE = Regex("""(?-i:(?<!\d-)(?<![\p{L}\d'’ʼ])[А-ЯЁЄІЇҐ]{2,6}(?![\p{L}\d'’ʼ]))""", RegexOption.IGNORE_CASE)
    private val CAPS_BEFORE_RE = Regex("""(?-i:(?<![\p{L}\d])[А-ЯЁЄІЇҐ]{4,}[\s,:;—–-]*$)""", RegexOption.IGNORE_CASE)
    private val CAPS_AFTER_RE = Regex("""(?-i:^[\s,:;—–-]*[А-ЯЁЄІЇҐ]{4,}(?![\p{L}\d]))""", RegexOption.IGNORE_CASE)
    /** Инициал перед фамилией или вторым инициалом; второй инициал после первого («Т. Г. Шевченко», «Шевченко Т. Г.»). */
    private val INITIAL_RE = Regex("""(?-i:(?<![\p{L}\d])[А-ЯЄІЇҐ]\.(?=\s*[А-ЯЄІЇҐ](?:[а-яєіїґ'’ʼ]|\.))|(?<=(?<![\p{L}\d])[А-ЯЄІЇҐ]\.\s?)[А-ЯЄІЇҐ]\.)""",
        RegexOption.IGNORE_CASE)
    private val LOWER_TOKEN_RE = Regex("""(?-i:(?<![\p{L}\d+́-])[а-яєіїґ]{2,5}(?![\p{L}\d+́-]))""", RegexOption.IGNORE_CASE)
    private val LOWER_NOT_ABBR = setOf(
        // единицы и сокращения без числа
        "мм", "см", "км", "дм", "кг", "мг", "гр", "мл", "кв", "шт", "ст", "рр", "вв", "пп", "пр", "св", "гл", "мкм", "мкг", "мс", "нм",
        "млн", "млрд", "трлн", "кб", "мб", "гб", "тб", "вт", "квт", "мвт", "гвт", "гц", "кгц", "мгц", "ггц", "дж", "кдж", "мдж", "кпа", "мпа",
        "гпа", "мкф", "нф", "пф", "тл", "лм", "лк", "бк", "зв", "мзв", "мкзв", "дб", "ккал", "хв", "грн", "мкс", "нс", "мв", "мка",
        "пн", "ср", "чт", "пт", "сб", "нд", "др", "тд", "тп", "спс", "плз", "крч",
        // междометия
        "хм", "гм", "гмм", "кхм", "хмм", "тс", "тсс", "тссс", "пст", "псс", "псст", "брр", "шш", "пф", "пфф", "фф", "цц", "мгм", "бр", "тпр")

    // ---- ссылки ----

    /** Ссылки и почта, как у русского ([Normalizer.urlRe]): read_links — по частям, знаки словами («крапка», «скісна риска»,
     * «собачка»), цифры по одной, буквы — латиницей для [UkLatin]; link_domain (и у чтеца sr_link_word) — «посилання, сайт» без
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
            '@' !in url && (word || rules.on("link_domain") && !rules.on("drop_links")) -> "посилання, " + spellUrl(URL_HOST_RE.find(url)?.groupValues?.get(1) ?: url)
            rules.on(if ('@' in url) "drop_emails" else "drop_links") -> ""
            rules.on("read_links") -> spellUrl(url)
            else -> url
        }
    }
    private fun spellUrl(url: String): String {
        val sb = StringBuilder()
        for (c in url.lowercase().removePrefix("https://").removePrefix("http://")) when {
            c in '0'..'9' -> sb.append(' ').append(DIGIT_NAMES[c - '0']).append(' ')
            c in URL_SEPARATORS -> sb.append(' ').append(URL_SEPARATORS[c]).append(' ')
            else -> sb.append(c)
        }
        // одиночная буква («?x=1») — названием, а не транслитом
        return URL_SINGLE_LETTER_RE.replace(sb) { UK_LATIN[it.value[0]] ?: it.value }.replace(WS_RE, " ").trim()
    }
    private val URL_SEPARATORS = mapOf(':' to "двокрапка", '/' to "скісна риска", '.' to "крапка", '?' to "знак питання",
        '&' to "амперсанд", '=' to "дорівнює", '-' to "дефіс", '_' to "підкреслення", '#' to "решітка", '%' to "відсоток", '+' to "плюс",
        '@' to "собачка", '~' to "тильда")
    private val URL_SINGLE_LETTER_RE = Regex("""(?<![\p{L}])[a-z](?![\p{L}])""")
    private val URL_HOST_RE = Regex("""^(?:https?://)?(?:www ?\.)?([^/?#:]+)""", RegexOption.IGNORE_CASE)

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
        s = IMAGE_MARK_RE.replace(s, if (rules.on("drop_images")) "" else "ілюстрація.")
        s = links(s, rules)                           // адрес — до кодов и чисел: цифры в нём по одной
        // «·», «→» между словами — пауза; с symbol_names (у чтеца — sr_symbols) знак называется по имени в фильтре ниже
        if (!rules.on("symbol_names")) s = Normalizer.symbolPauses(s)
        s = OTHER_DIGIT_RE.replace(s) { Character.digit(it.value.codePointAt(0), 10).toString() }
        step("footnotes", ::footnotes)                // «[1]», «словах¹» — до скобок-пауз и чисел
        step("codes", ::codes)                        // «код 4829», «PIN 1234», «ЄДРПОУ …» — до латиницы и spell_cyr
        step("abbrev", ::floors)                      // «на 5 пов.» — «на п'ятому поверсі»: число ещё цифрами
        step("abbrev", ::nounAbbrs)                   // «буд. 5», «м. Київ», «кн. II» — по заглавной и до римских
        // «°C», «°F» латиницей — до латиницы и до регистра: шкалу через пробел узнаём по заглавной
        s = FAHRENHEIT_RE.replace(CELSIUS_RE.replace(s, "°с"), "°ф")
        // «1,20 UAH», «5 USD» — валюта единицей, а не буквами; тоже до латиницы
        step("currency") { CURRENCY_CODE_RE.replace(it) { m -> " " + CURRENCY_CODES.getValue(m.groupValues[1].uppercase()) } }
        s = K_THOUSAND_RE.replace(s, " тис")   // без точки: в конце текста она стала бы точкой предложения
        step("units") { VOLT_RE.replace(AMPERE_RE.replace(LATIN_UNIT_RE.replace(it) { m -> " " + LATIN_UNITS.getValue(m.groupValues[1]) }, "амп"), "вольт") }
        step("thousands", ::thousands)                // «1.000.000» — до номеров разделов «2.10.3»
        s = UkNumbers.roman(s, rules)                 // «XX століття» — до латиницы и регистра: римские узнаём по заглавным
        step("spoons", ::spoons)                      // «2 ст. л.» — до регистра: точка перед заглавной — конец фразы
        step("numbers", ::classes)                    // «у 5-А класі» — «у п'ятому-а класі»
        step("numbers") { CLASS_LETTER_RE.replace(it, " $1") }   // «5-А» без «клас» — буква класу, не нарощення
        step("arith", UkNumbers::dimensions)          // «3x4» — до латиницы, иначе «x» стала бы «екс»
        step("arith", UkNumbers::algebra)             // «x² + y² = z²» — тоже до латиницы
        step("homoglyphs", ::homoglyphs)              // «cік», «iнший» латиницей — до латиницы, иначе «кік», «нший»
        // латиницы в символах модели нет вовсе — «FilmBell», «USB», «Карл V» раньше выпадали
        step("latin") { FILE_EXT_RE.replace(it) { m -> " крапка " + m.groupValues[1].let { e -> if (e.none { c -> c in "aeiouy" }) e.uppercase() else e } } }
        step("latin") { UkLatin.translate(it, rules) }
        step("latin") { GREEK_RE.replace(it) { m -> GREEK.getValue(m.value[0]) } }
        step("letter_digit", ::cyrCodes)              // до lowercase: код узнаём по заглавным
        step("spell_cyr", ::spellCyr)                 // «ЗСУ», «Т. Г. Шевченко» — тоже по заглавным
        // точка сокращения в конце предложения — после латиницы («5kg.» — уже «5 кг.»), но до lowercase
        val lat = s
        s = ABBR_END_RE.replace(lat) { m ->
            // «У 1941 р. Німеччина…»: перед годом только предлог — середина предложения, не конец
            val mid = m.value.startsWith("р") && lat.substring(m.range.last + 1).isNotBlank() && YEAR_LEAD_RE.matches(lat.substring(0, m.range.first))
            if (mid) m.value else "${m.value} $ABBR_DOT"
        }
        // до lowercase: «н. е.» строчными — эра, «Н. Е.» — инициалы (spell_cyr их уже прочёл, но он может быть выключен)
        step("abbrev") { WORD_ABBR.fold(it) { t, (re, rep) -> re.replace(t, rep) } }
        s = s.lowercase()
        step("degrees") { ANGLE_APOS_RE.replace(it, "′") }   // «42°30'» — до снятия апострофа
        // разделительный апостроф — «ъ», как в русском «подъезд»: до чисел (морфология и регулярки видят слово целым, [UkNumbers]
        // ищет его без «ъ») и ещё раз после — у слов чисел («п'ять») свой апостроф; [LangStress] ищет слово без «ъ»
        val hard = if ('ъ' in allowed) "ъ" else ""
        fun apos(t: String) = SEPARATING_APOS_RE.replace(t, hard).replace(APOSTROPHE, "")
        s = apos(s)
        step("abbrev", ::soCalled)                    // «т. зв. нова хвиля» — «так звана»
        s = TILDE_RE.replace(s, "приблизно ")
        // телефоны — разбор русского слоя ([Normalizer.phones]: +380, скобки), группы — нашими числами; слитный украинский —
        // сначала по группам
        step("phones") { Normalizer.phones(UA_PHONE_PLUS_RE.replace(UA_PHONE_RE.replace(it, "0$1 $2-$3-$4"), "+380 $1 $2 $3 $4"), UkNumbers::group) }
        // знаки между числами — до «+5», «-5»: «x = -5» иначе теряло «дорівнює»
        step("arith", UkNumbers::arith)
        step("numbers") { MINUS_NUMBER_RE.replace(PLUS_NUMBER_RE.replace(it, " плюс "), " мінус ") }
        step("sections", UkNumbers::sectionLabels)    // «п. 4.12» — до дат и до «№»: номер, а не «четвертого грудня»
        step("numbers", UkNumbers::signs)             // «№ 5», «§ 3»
        // даты и время — до единиц: у них внутри цифры со знаками (12.05.2026, 21:30); время раньше: «о 10.05» — не дата
        step("times", UkNumbers::times)
        s = UkNumbers.dates(s, rules)
        step("sections", UkNumbers::sections)         // «2.10.3» — после дат: «12.05.2026» им уже прочитано
        s = UkNumbers.ordinals(s, rules)              // «1-го вересня», «5-й клас», «1990-ті»
        step("numbers") { UkNumbers.lists(UkNumbers.chapters(UkNumbers.compounds(it))) }   // «5-річний», «Глава 5», «: 1) …»
        s = UkNumbers.fractions(s, rules)             // «1/2 кг», «½» — до единиц: иначе «2 кг» из «1/2 кг»
        step("years", UkNumbers::abbreviations)       // «1999 р.» → «1999 рік»
        s = UkNumbers.units(s, rules)                 // «3 км» → «три кілометри»
        s = UkNumbers.standalone(s, rules)            // «руб.», «грн» без числа
        step("cases", UkNumbers::cases)               // «близько 3 годин», «2 книги», «з 5 друзями» — рід і відмінок
        s = numberWords(s, rules)
        s = apos(s)
        step("spell_cyr", ::spellLower)               // «кмн» — после единиц: «км» без числа не сокращение
        s = ABBR_DOT_KEPT_RE.replace(s, ".").replace(ABBR_DOT, '.')
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
