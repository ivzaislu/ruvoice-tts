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
            // три цифры и «рік» — количество: «виповнився 101 рік» → «сто один рік» (год 988 в називному — редкость)
            if (word == "рік" && m.groupValues[1].length == 3) return@words m.value
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
     * капсом («5-Й КЛАС») — заголовок, не трогаем. ponytail: «у п'ять а класі», лучше «у п'ятому-А» — по имени буквы (задача 4). */
    private val CLASS_LETTER_RE = Regex("""(?-i:(?<=\d)-([А-ЯЄІЇҐ])(?=\s+\p{Ll}|\s*[.,;:!?)»]|\s*$))""", RegexOption.IGNORE_CASE)
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
        s = OTHER_DIGIT_RE.replace(s) { Character.digit(it.value.codePointAt(0), 10).toString() }
        // «°C», «°F» латиницей — до латиницы и до регистра: шкалу через пробел узнаём по заглавной
        s = FAHRENHEIT_RE.replace(CELSIUS_RE.replace(s, "°с"), "°ф")
        // «1,20 UAH», «5 USD» — валюта единицей, а не буквами; тоже до латиницы
        step("currency") { CURRENCY_CODE_RE.replace(it) { m -> " " + CURRENCY_CODES.getValue(m.groupValues[1].uppercase()) } }
        s = K_THOUSAND_RE.replace(s, " тис")   // без точки: в конце текста она стала бы точкой предложения
        step("units") { VOLT_RE.replace(AMPERE_RE.replace(LATIN_UNIT_RE.replace(it) { m -> " " + LATIN_UNITS.getValue(m.groupValues[1]) }, "амп"), "вольт") }
        s = UkNumbers.roman(s, rules)                 // «XX століття» — до латиницы и регистра: римские узнаём по заглавным
        step("spoons", ::spoons)                      // «2 ст. л.» — до регистра: точка перед заглавной — конец фразы
        step("numbers") { CLASS_LETTER_RE.replace(it, " $1") }   // «5-А клас» — буква класу, не нарощення
        s = s.lowercase()
        step("degrees") { ANGLE_APOS_RE.replace(it, "′") }   // «42°30'» — до снятия апострофа
        // разделительный апостроф — «ъ», как в русском «подъезд»: до чисел (морфология и регулярки видят слово целым, [UkNumbers]
        // ищет его без «ъ») и ещё раз после — у слов чисел («п'ять») свой апостроф; [LangStress] ищет слово без «ъ»
        val hard = if ('ъ' in allowed) "ъ" else ""
        fun apos(t: String) = SEPARATING_APOS_RE.replace(t, hard).replace(APOSTROPHE, "")
        s = apos(s)
        step("abbrev", ::soCalled)                    // «т. зв. нова хвиля» — «так звана»
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
