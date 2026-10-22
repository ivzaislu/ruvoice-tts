// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Числа по-украински: количественные и порядковые во всех падежах и родах, дроби, даты, время, единицы, валюты, римские,
 * род и падеж числа по существительному. По образцу [BelNumbers], свои таблицы: числительные — VESUM numr (основная форма —
 * первая в Правописе 2019: «п'ятьма», «сьома», «вісьма»), единицы и валюты — существительные VESUM (родительный ед.,
 * именительный и родительный мн.). Порядок шагов — в [UkNormalizer.prepare], как у белорусского.
 * Регулярки — с IGNORE_CASE, регистрозависимое — в (?-i:…): встроенный (?i:) без флага на JVM кириллицу не видит.
 * Формы не проверены носителем.
 */
object UkNumbers {

    // ---- таблицы количественных (VESUM numr, Правопис 2019 § 106) ----

    private val UNITS = arrayOf(
        "нуль", "один", "два", "три", "чотири", "п'ять", "шість", "сім", "вісім", "дев'ять",
        "десять", "одинадцять", "дванадцять", "тринадцять", "чотирнадцять", "п'ятнадцять",
        "шістнадцять", "сімнадцять", "вісімнадцять", "дев'ятнадцять")
    private val TENS = arrayOf(
        "", "", "двадцять", "тридцять", "сорок", "п'ятдесят", "шістдесят", "сімдесят", "вісімдесят", "дев'яносто")
    private val HUNDREDS = arrayOf(
        "", "сто", "двісті", "триста", "чотириста", "п'ятсот", "шістсот", "сімсот", "вісімсот", "дев'ятсот")

    private fun table(s: String) = s.trim().lines().map { it.trim().split(' ').toTypedArray() }
    // родовий, давальний, орудний, місцевий
    private val UNITS_CASES = table("""
        одного одному одним одному
        двох двом двома двох
        трьох трьом трьома трьох
        чотирьох чотирьом чотирма чотирьох
        п'яти п'яти п'ятьма п'яти
        шести шести шістьма шести
        семи семи сьома семи
        восьми восьми вісьма восьми
        дев'яти дев'яти дев'ятьма дев'яти
        десяти десяти десятьма десяти
        одинадцяти одинадцяти одинадцятьма одинадцяти
        дванадцяти дванадцяти дванадцятьма дванадцяти
        тринадцяти тринадцяти тринадцятьма тринадцяти
        чотирнадцяти чотирнадцяти чотирнадцятьма чотирнадцяти
        п'ятнадцяти п'ятнадцяти п'ятнадцятьма п'ятнадцяти
        шістнадцяти шістнадцяти шістнадцятьма шістнадцяти
        сімнадцяти сімнадцяти сімнадцятьма сімнадцяти
        вісімнадцяти вісімнадцяти вісімнадцятьма вісімнадцяти
        дев'ятнадцяти дев'ятнадцяти дев'ятнадцятьма дев'ятнадцяти""")
    private val ONE_F_CASES = arrayOf("однієї", "одній", "однією", "одній")
    private val TENS_CASES = table("""
        двадцяти двадцяти двадцятьма двадцяти
        тридцяти тридцяти тридцятьма тридцяти
        сорока сорока сорока сорока
        п'ятдесяти п'ятдесяти п'ятдесятьма п'ятдесяти
        шістдесяти шістдесяти шістдесятьма шістдесяти
        сімдесяти сімдесяти сімдесятьма сімдесяти
        вісімдесяти вісімдесяти вісімдесятьма вісімдесяти
        дев'яноста дев'яноста дев'яноста дев'яноста""")
    private val HUNDREDS_CASES = table("""
        ста ста ста ста
        двохсот двомстам двомастами двохстах
        трьохсот трьомстам трьомастами трьохстах
        чотирьохсот чотирьомстам чотирмастами чотирьохстах
        п'ятисот п'ятистам п'ятьмастами п'ятистах
        шестисот шестистам шістьмастами шестистах
        семисот семистам сьомастами семистах
        восьмисот восьмистам вісьмастами восьмистах
        дев'ятисот дев'ятистам дев'ятьмастами дев'ятистах""")
    private val ZERO = arrayOf("нуль", "нуля", "нулю", "нуль", "нулем", "нулі")

    /** С миллиарда — по цифрам: разрядов дальше нет (телефон, номер карты), а таблицы на них кончаются. */
    private const val BIG = 1_000_000_000L

    /** Число по цифрам: «80441234567» → «вісім нуль чотири…». */
    fun digits(raw: String) = raw.map { UNITS[it.digitToInt()] }.joinToString(" ")

    /** Группа цифр как написана: нули в начале — словом каждый («044» — «нуль сорок чотири», «007»). */
    fun group(raw: String): String {
        val rest = raw.trimStart('0')
        val tail = rest.takeIf { it.isNotEmpty() }?.let { r -> r.toLongOrNull()?.let { cardinal(it) } ?: digits(r) }
        return (List(raw.length - rest.length) { UNITS[0] } + listOfNotNull(tail)).joinToString(" ")
    }

    /** Форма существительного после числа: 1 — [one], 2–4 — [few], прочие — [many]. */
    fun plural(n: Long, one: String, few: String, many: String) = when {
        n % 10 == 1L && n % 100 != 11L -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
    private fun isOne(n: Long) = n % 10 == 1L && n % 100 != 11L
    private fun isFew(n: Long) = n % 10 in 2..4 && n % 100 !in 12..14

    /** «тисяча», «мільйон» после числа: назв., род., дав., знах., оруд., місц. однини, потом множини. */
    private val THOUSAND = "тисяча тисячі тисячі тисячу тисячею тисячі тисячі тисяч тисячам тисячі тисячами тисячах".split(' ')
    private val MILLION = "мільйон мільйона мільйону мільйон мільйоном мільйоні мільйони мільйонів мільйонам мільйони мільйонами мільйонах".split(' ')

    /** Количественное в падеже [case] и роде [g] (у 1 и 2: «одна», «одне», «дві»): склоняется каждое слово, «1250» в
     * родовому — «однієї тисячі двохсот п'ятдесяти», «25» в орудному — «двадцятьма п'ятьма». Знахідний — неістота
     * (як називний; «одну» у жіночого); істоту знахідного дає [byNoun]. Знахідний одной тысячи в начале — «тисячу» без
     * «одну» («за тисячу гривень», «понад тисячу учасників»), но «двадцять одну тисячу». От миллиарда — по цифрам. */
    fun cardinal(n: Long, case: Case = Case.NOM, g: Gender = Gender.M): String {
        if (n < 0) return "мінус " + cardinal(-n, case, g)
        if (n == 0L) return ZERO[case.ordinal]
        if (n >= BIG) return digits(n.toString())
        val parts = ArrayList<String>(3)
        fun group(k: Int, noun: List<String>, gg: Gender) {
            if (k == 0) return
            val word = if (case == Case.NOM || case == Case.ACC) noun[if (isOne(k.toLong())) case.ordinal else if (isFew(k.toLong())) 6 else 7]
                else noun[case.ordinal + if (isOne(k.toLong())) 0 else 6]
            parts += if (case == Case.ACC && k == 1 && parts.isEmpty()) word else under1000(k, case, gg) + " " + word
        }
        group((n / 1_000_000).toInt(), MILLION, Gender.M)
        group((n / 1000 % 1000).toInt(), THOUSAND, Gender.F)
        (n % 1000).toInt().let { if (it > 0) parts += under1000(it, case, g) }
        return parts.joinToString(" ")
    }

    /** 1..999 в падеже [case]: каждое слово своим падежом. */
    private fun under1000(n: Int, case: Case, g: Gender): String {
        val r = n % 100
        val h = n / 100
        val u = if (r >= 20) r % 10 else r
        if (case == Case.NOM || case == Case.ACC) {
            val unit = when {
                u == 1 && g == Gender.F -> if (case == Case.ACC) "одну" else "одна"
                u == 1 && g == Gender.N -> "одне"
                u == 2 && g == Gender.F -> "дві"
                else -> UNITS[u]
            }
            return listOfNotNull(HUNDREDS[h].ifEmpty { null }, if (r >= 20) TENS[r / 10] else null, unit.takeIf { u > 0 }).joinToString(" ")
        }
        val c = when (case) { Case.GEN -> 0; Case.DAT -> 1; Case.INS -> 2; else -> 3 }
        return listOfNotNull(
            HUNDREDS_CASES.getOrNull(h - 1)?.get(c),
            if (r >= 20) TENS_CASES[r / 10 - 2][c] else null,
            u.takeIf { it > 0 }?.let { if (it == 1 && g == Gender.F) ONE_F_CASES[c] else UNITS_CASES[it - 1][c] }).joinToString(" ")
    }

    // ---- порядковые ----

    private val ORD = arrayOf(
        "нульовий", "перший", "другий", "третій", "четвертий", "п'ятий", "шостий", "сьомий", "восьмий", "дев'ятий",
        "десятий", "одинадцятий", "дванадцятий", "тринадцятий", "чотирнадцятий", "п'ятнадцятий", "шістнадцятий",
        "сімнадцятий", "вісімнадцятий", "дев'ятнадцятий")
    private val TENS_ORD = arrayOf(
        "", "", "двадцятий", "тридцятий", "сороковий", "п'ятдесятий", "шістдесятий", "сімдесятий", "вісімдесятий", "дев'яностий")

    /** Основа первой части сложного слова — родовий каждого слова, слитно (Правопис 2019: «двохсотий», «двохтисячний»,
     * «двадцятип'ятитисячний», «стотисячний»): 2 → «двох», 25 → «двадцятип'яти», 100 → «сто», 300 → «трьохсот»; 1 —
     * [one]. Для прикметників («двокімнатний», «п'ятирічний») — [adj]: 1–4 «одно», «дво», «три», «чотири», 90 «дев'яносто».
     * ponytail: единица внутри («двадцятиоднотисячний») и десятки на 0 после сотен — по аналогии; не проверено носителем. */
    internal fun compoundStem(n: Int, one: String = "", adj: Boolean = false): String {
        if (n == 1) return one
        fun unit(u: Int) = if (adj && u in 1..4) arrayOf("одно", "дво", "три", "чотири")[u - 1]
            else if (u == 1) "одно" else UNITS_CASES[u - 1][0]
        val h = n / 100
        val r = n % 100
        val hs = when (h) { 0 -> ""; 1 -> "сто"; else -> HUNDREDS_CASES[h - 1][0] }
        val rs = when {
            r == 0 -> ""
            r < 20 -> unit(r)
            else -> (if (adj && r / 10 == 9) "дев'яносто" else TENS_CASES[r / 10 - 2][0]) + (if (r % 10 == 0) "" else unit(r % 10))
        }
        return hs + rs
    }

    /** Порядковое в падеже [case], роде [g] или во множине [plural]: порядковым становится последнее слово, прочие —
     * количественные в називному («дві тисячі двадцять шостий», «тисяча дев'ятсот дев'яносто восьмий» — в годах без
     * «одна»); круглые — одним словом («тисячний», «двохтисячний», «мільйонний»). От миллиарда — по цифрам. */
    fun ordinal(n: Long, case: Case = Case.NOM, g: Gender = Gender.M, plural: Boolean = false): String {
        if (n < 0) return "мінус " + ordinal(-n, case, g, plural)
        if (n >= BIG) return digits(n.toString())
        fun end(stem: String) = adjEnd(stem, case, g, plural)
        if (n >= 1_000_000) {
            val millions = (n / 1_000_000).toInt()
            val rest = n % 1_000_000
            return if (rest == 0L) end(compoundStem(millions) + "мільйонний") else cardinal(n - rest) + " " + ordinal(rest, case, g, plural)
        }
        val thousands = (n / 1000).toInt()
        val rest = (n % 1000).toInt()
        if (n > 0 && rest == 0) return end(compoundStem(thousands) + "тисячний")
        val head = ArrayList<String>(4)
        if (thousands == 1) head += "тисяча"
        else if (thousands > 0) head += under1000(thousands, Case.NOM, Gender.F) + " " + plural(thousands.toLong(), "тисяча", "тисячі", "тисяч")
        val h = rest / 100
        val r = rest % 100
        // круглые сотни порядковым целиком: 1900 → «тисяча дев'ятисотий»
        if (h > 0 && r > 0) head += HUNDREDS[h]
        head += end(when {
            r == 0 && h > 0 -> if (h == 1) "сотий" else UNITS_CASES[h - 1][0] + "сотий"   // «двохсотий», «п'ятисотий»
            r < 20 -> ORD[r]
            r % 10 == 0 -> TENS_ORD[r / 10]
            else -> { head += TENS[r / 10]; ORD[r % 10] }
        })
        return head.joinToString(" ")
    }

    /** Окончание прилагательного (порядкового) по форме назывального м. р.: твёрдое «-ий», мягкое «-ій» («третій»). */
    private fun adjEnd(nomM: String, case: Case, g: Gender, plural: Boolean): String {
        val soft = nomM.endsWith("ій")
        val t = if (soft) ADJ_SOFT else ADJ_HARD
        return nomM.dropLast(2) + t[if (plural) 3 else g.ordinal][case.ordinal]
    }
    // ч., ж., с., мн. × називний, родовий, давальний, знахідний (неістота), орудний, місцевий
    private val ADJ_HARD = table("""
        ий ого ому ий им ому
        а ої ій у ою ій
        е ого ому е им ому
        і их им і ими их""")
    private val ADJ_SOFT = table("""
        ій ього ьому ій ім ьому
        я ьої ій ю ьою ій
        є ього ьому є ім ьому
        і іх ім і іми іх""")

    // ---- дроби ----

    /** «3/4» → «три четвертих»: числитель — количественное ж. р., знаменатель — порядковое ж. р.: при 1 — однина («одна
     * друга»), иначе родовий множини («дві третіх», «п'ять шостих» — так в школьных граматиках; «три четверті» тоже норма);
     * в косвенном падеже [case] — тот же падеж, при 1 — однина («однієї другої»), иначе множина («двох п'ятих»). */
    fun fraction(a: Long, b: Long, case: Case = Case.NOM): String = agreed(a, case) { c, pl -> ordinal(b, c, Gender.F, pl) }

    /** Количественное ж. р. и слово при нём ([word] по падежу и числу) — у дроби и «ціла». */
    private fun agreed(n: Long, case: Case, word: (Case, Boolean) -> String): String {
        val one = isOne(n)
        val w = when {
            case != Case.NOM && case != Case.ACC -> word(case, !one)
            one -> word(case, false)
            else -> word(Case.GEN, true)
        }
        return cardinal(n, case, Gender.F) + " " + w
    }

    /** Дроби словами (правило fractions). Знак-дробь один — словом: «½» → «половина», «¾» → «три чверті»; при целом —
     * смешанное число. Смешанное: половина — «півтора» (ж. р. «півтори») и «два з половиною», прочее — «одна ціла одна
     * третя». Через слэш — когда знаменатель 2–20 и больше числителя или круглый (2, 10, 100…): «3/4», «7/2»; прочее —
     * «двадцять чотири на сім» (так говорят «24/7»). Единица следом — родовий однини: «1/2 кг» → «одна друга кілограма»; после половины — по
     * целой части: «2 1/2 кг» → «два з половиною кілограми». */
    fun fractions(text: String, rules: Rules = Rules()): String {
        if (!rules.on("fractions")) return text
        fun unitOf(u: String) = u.lowercase().takeIf { k -> UNITS_MAP[k]?.let { rules.on(it.rule) } == true }
        var s = VULGAR_RE.replace(text) { m ->
            val c = m.groupValues[2][0]
            val (a, b) = VULGAR.getValue(c)
            if (m.groupValues[1].isNotEmpty()) "${m.groupValues[1]} $a/$b"
            else VULGAR_WORDS[c]?.let { (if (m.range.first > 0 && text[m.range.first - 1].isLetterOrDigit()) " " else "") + it +
                (if (text.getOrNull(m.range.last + 1)?.isLetter() == true) " " else "") } ?: "$a/$b"
        }
        // слово-дробь перед единицей: «половина кілограма»
        s = VULGAR_UNIT_RE.replace(s) { m -> unitOf(m.groupValues[2])?.let { "${m.groupValues[1]} ${unitGen(it)}" } ?: m.value }
        s = MIXED_RE.replace(s) { m ->
            val n = m.groupValues[1].toLong(); val a = m.groupValues[2].toLong(); val b = m.groupValues[3].toLong()
            if (a >= b || b < 2) return@replace m.value
            val u = unitOf(m.groupValues[5])
            // род — у единицы, иначе у существительного за дробью из таблицы: «1 1/2 години» → «півтори години»
            val fem = (u?.let { unit(it).gender } ?: nextNoun(s, m.range.last + 1)?.let { nounGender(it) }) == Gender.F
            val gen = u?.let { " " + unitGen(it) } ?: m.groupValues[4]
            if (a * 2 == b) {
                if (n == 1L) (if (fem) "півтори" else "півтора") + gen
                else cardinal(n, Case.NOM, if (fem) Gender.F else Gender.M) + " з половиною" + (u?.let { " " + unitWord(it, n) } ?: m.groupValues[4])
            } else cardinal(n, Case.NOM, Gender.F) + " " + plural(n, "ціла", "цілих", "цілих") + " " + fraction(a, b) + gen
        }
        return SLASH_RE.replace(s) { m ->
            // длиннее Long — по цифрам, слэш словом «на», а не пропадает
            val a = m.groupValues[1].toLongOrNull() ?: return@replace digits(m.groupValues[1]) + " на " + digits(m.groupValues[2]) + m.groupValues[3]
            val b = m.groupValues[2].toLongOrNull() ?: return@replace digits(m.groupValues[1]) + " на " + digits(m.groupValues[2]) + m.groupValues[3]
            val u = unitOf(m.groupValues[4])
            // падеж по предлогу, как у десятичной: «близько 2/5» → «близько двох п'ятих»; не дробь — «на» («24/7», «1990/91»)
            if (a >= 1 && (b in 2..20 && a < b || b in ROUND_DENOMS))
                fraction(a, b, decimalCase(s, m.range.first) ?: Case.NOM) + (u?.let { " " + unitGen(it) } ?: m.groupValues[3])
            else cardinal(a) + " на " + cardinal(b) + m.groupValues[3]
        }
    }
    private val ROUND_DENOMS = setOf(2L, 10L, 100L, 1000L, 10000L)
    private val VULGAR = mapOf('½' to (1 to 2), '¼' to (1 to 4), '¾' to (3 to 4), '⅓' to (1 to 3), '⅔' to (2 to 3),
        '⅕' to (1 to 5), '⅛' to (1 to 8), '⅜' to (3 to 8), '⅝' to (5 to 8), '⅞' to (7 to 8))
    private val VULGAR_WORDS = mapOf('½' to "половина", '¼' to "чверть", '¾' to "три чверті", '⅓' to "третина", '⅔' to "дві третини")
    private val VULGAR_RE = Regex("""(?:(\d+)\s?)?([½¼¾⅓⅔⅕⅛⅜⅝⅞])""")

    // ---- единицы и валюты ----

    /** Формы единиц: род (I — невідмінюване, с. р.), називний…місцевий однини, називний…місцевий множини. Из VESUM
     * v6.8.6 (давальний — «-у», місцевий — «-і», где VESUM даёт оба); «чоловік» после числа — лічильна форма «чоловік»
     * (VESUM :subst); «євро» — невідмінюване, середній рід (у VESUM ч. і с.). Не проверено носителем. */
    private val UNIT_FORMS: Map<String, Pair<Gender, List<String>>> = """
        метр M метр метра метру метр метром метрі метри метрів метрам метри метрами метрах
        гектар M гектар гектара гектару гектар гектаром гектарі гектари гектарів гектарам гектари гектарами гектарах
        літр M літр літра літру літр літром літрі літри літрів літрам літри літрами літрах
        фут M фут фута футу фут футом футі фути футів футам фути футами футах
        миля F миля милі милі милю милею милі милі миль милям милі милями милях
        дюйм M дюйм дюйма дюйму дюйм дюймом дюймі дюйми дюймів дюймам дюйми дюймами дюймах
        грам M грам грама граму грам грамом грамі грами грамів грамам грами грамами грамах
        тонна F тонна тонни тонні тонну тонною тонні тонни тонн тоннам тонни тоннами тоннах
        центнер M центнер центнера центнеру центнер центнером центнері центнери центнерів центнерам центнери центнерами центнерах
        унція F унція унції унції унцію унцією унції унції унцій унціям унції унціями унціях
        година F година години годині годину годиною годині години годин годинам години годинами годинах
        хвилина F хвилина хвилини хвилині хвилину хвилиною хвилині хвилини хвилин хвилинам хвилини хвилинами хвилинах
        секунда F секунда секунди секунді секунду секундою секунді секунди секунд секундам секунди секундами секундах
        штука F штука штуки штуці штуку штукою штуці штуки штук штукам штуки штуками штуках
        чоловік M чоловік чоловіка чоловіку чоловіка чоловіком чоловікові чоловіки чоловік чоловікам чоловік чоловіками чоловіках
        особа F особа особи особі особу особою особі особи осіб особам осіб особами особах
        примірник M примірник примірника примірнику примірник примірником примірнику примірники примірників примірникам примірники примірниками примірниках
        вольт M вольт вольта вольту вольт вольтом вольті вольти вольтів вольтам вольти вольтами вольтах
        ампер M ампер ампера амперу ампер ампером ампері ампери амперів амперам ампери амперами амперах
        ват M ват вата вату ват ватом ваті вати ватів ватам вати ватами ватах
        герц M герц герца герцу герц герцом герці герци герців герцам герци герцами герцах
        джоуль M джоуль джоуля джоулю джоуль джоулем джоулі джоулі джоулів джоулям джоулі джоулями джоулях
        паскаль M паскаль паскаля паскалю паскаль паскалем паскалі паскалі паскалів паскалям паскалі паскалями паскалях
        фарад M фарад фарада фараду фарад фарадом фараді фаради фарадів фарадам фаради фарадами фарадах
        тесла F тесла тесли теслі теслу теслою теслі тесли тесл теслам тесли теслами теслах
        люмен M люмен люмена люмену люмен люменом люмені люмени люменів люменам люмени люменами люменах
        люкс M люкс люкса люксу люкс люксом люксі люкси люксів люксам люкси люксами люксах
        бекерель M бекерель бекереля бекерелю бекерель бекерелем бекерелі бекерелі бекерелів бекерелям бекерелі бекерелями бекерелях
        зіверт M зіверт зіверта зіверту зіверт зівертом зіверті зіверти зівертів зівертам зіверти зівертами зівертах
        децибел M децибел децибела децибелу децибел децибелом децибелі децибели децибелів децибелам децибели децибелами децибелах
        калорія F калорія калорії калорії калорію калорією калорії калорії калорій калоріям калорії калоріями калоріях
        атмосфера F атмосфера атмосфери атмосфері атмосферу атмосферою атмосфері атмосфери атмосфер атмосферам атмосфери атмосферами атмосферах
        бар M бар бара бару бар баром барі бари барів барам бари барами барах
        моль M моль моля молю моль молем молі молі молів молям молі молями молях
        біт M біт біта біту біт бітом біті біти бітів бітам біти бітами бітах
        байт M байт байта байту байт байтом байті байти байтів байтам байти байтами байтах
        оберт M оберт оберту оберту оберт обертом оберті оберти обертів обертам оберти обертами обертах
        удар M удар удару удару удар ударом ударі удари ударів ударам удари ударами ударах
        градус M градус градуса градусу градус градусом градусі градуси градусів градусам градуси градусами градусах
        відсоток M відсоток відсотка відсотку відсоток відсотком відсотку відсотки відсотків відсоткам відсотки відсотками відсотках
        пункт M пункт пункту пункту пункт пунктом пункті пункти пунктів пунктам пункти пунктами пунктах
        сила F сила сили силі силу силою силі сили сил силам сили силами силах
        рубль M рубль рубля рублю рубль рублем рублі рублі рублів рублям рублі рублями рублях
        копійка F копійка копійки копійці копійку копійкою копійці копійки копійок копійкам копійки копійками копійках
        долар M долар долара долару долар доларом доларі долари доларів доларам долари доларами доларах
        цент M цент цента центу цент центом центі центи центів центам центи центами центах
        пенс M пенс пенса пенсу пенс пенсом пенсі пенси пенсів пенсам пенси пенсами пенсах
        євро I євро євро євро євро євро євро євро євро євро євро євро євро
        фунт M фунт фунта фунту фунт фунтом фунті фунти фунтів фунтам фунти фунтами фунтах
        гривня F гривня гривні гривні гривню гривнею гривні гривні гривень гривням гривні гривнями гривнях
        тенге J тенге тенге тенге тенге тенге тенге тенге тенге тенге тенге тенге тенге
        єна F єна єни єні єну єною єні єни єн єнам єни єнами єнах
        ліра F ліра ліри лірі ліру лірою лірі ліри лір лірам ліри лірами лірах
        вона F вона вони воні вону воною воні вони вон вонам вони вонами вонах
        шекель M шекель шекеля шекелю шекель шекелем шекелі шекелі шекелів шекелям шекелі шекелями шекелях
        рупія F рупія рупії рупії рупію рупією рупії рупії рупій рупіям рупії рупіями рупіях
        тисяча F тисяча тисячі тисячі тисячу тисячею тисячі тисячі тисяч тисячам тисячі тисячами тисячах
        мільйон M мільйон мільйона мільйону мільйон мільйоном мільйоні мільйони мільйонів мільйонам мільйони мільйонами мільйонах
        мільярд M мільярд мільярда мільярду мільярд мільярдом мільярді мільярди мільярдів мільярдам мільярди мільярдами мільярдах
        трильйон M трильйон трильйона трильйону трильйон трильйоном трильйоні трильйони трильйонів трильйонам трильйони трильйонами трильйонах
    """.trim().lines().associate { l -> l.trim().split(' ').let { p ->
        p[0] to (when (p[1]) { "I" -> Gender.N; "J" -> Gender.M; else -> Gender.valueOf(p[1]) } to p.drop(2)) } }

    /** Единица: формы ([UNIT_FORMS], с приставкой «кіло», «мега»…), прилагательное перед ней («квадратний»), хвост («за
     * годину») и правило «Правил»: деньги — currency, градусы и дюйми — degrees, «тис.», «млн» — abbrev, «%» — numbers,
     * прочее — units. */
    class Measure(lemma: String, prefix: String = "", val adj: String? = null, val suffix: String = "", val rule: String = "units") {
        val gender: Gender = UNIT_FORMS.getValue(lemma).first
        val forms: List<String> = UNIT_FORMS.getValue(lemma).second.map { prefix + it }
    }

    /** Ключ — то, что стоит за числом (строчными: текст уже опущен в регистре). Регистр, что различал бы «МВт» и «мВт», потерян —
     * берём частое (мегават, мілівольт); «А» (ампер) и «В» (вольт) [UkNormalizer] заранее делает «амп» и «вольт» (строчные
     * «а», «в» — союз и прийменник), латиницу («GB», «kWh») — кириллицей. Те же единицы, что у белорусского, плюс «ц», «ос». */
    val UNITS_MAP: Map<String, Measure> = buildMap {
        fun add(vararg keys: String, u: Measure) = keys.forEach { put(it, u) }
        val sq = "квадратн"; val cu = "кубічн"
        add("км", u = Measure("метр", "кіло")); add("м", u = Measure("метр")); add("дм", u = Measure("метр", "деци")); add("см", u = Measure("метр", "санти"))
        add("мм", u = Measure("метр", "мілі")); add("мкм", u = Measure("метр", "мікро")); add("нм", u = Measure("метр", "нано")); add("га", u = Measure("гектар"))
        add("м2", "м²", "кв. м", "кв.м", u = Measure("метр", adj = sq)); add("км2", "км²", "кв. км", "кв.км", u = Measure("метр", "кіло", adj = sq))
        add("см2", "см²", u = Measure("метр", "санти", adj = sq)); add("мм2", "мм²", u = Measure("метр", "мілі", adj = sq))
        add("м3", "м³", "куб. м", "куб.м", u = Measure("метр", adj = cu)); add("см3", "см³", u = Measure("метр", "санти", adj = cu))
        add("мм3", "мм³", u = Measure("метр", "мілі", adj = cu))
        add("пог. м", "пог.м", u = Measure("метр", adj = "погонн"))   // «20 пог. м» — погонні метри (труби, плінтус)
        add("л", u = Measure("літр")); add("мл", u = Measure("літр", "мілі")); add("дл", u = Measure("літр", "деци"))
        add("км/год", "км/г", "км/ч", u = Measure("метр", "кіло", suffix = " за годину")); add("м/с", u = Measure("метр", suffix = " за секунду"))
        add("км/с", u = Measure("метр", "кіло", suffix = " за секунду")); add("м/с2", "м/с²", u = Measure("метр", suffix = " за секунду в квадраті"))
        add("мм рт. ст", "мм рт.ст", "мм рт ст", u = Measure("метр", "мілі", suffix = " ртутного стовпчика"))
        add("кг", u = Measure("грам", "кіло")); add("г", "гр", u = Measure("грам")); add("мг", u = Measure("грам", "мілі")); add("мкг", u = Measure("грам", "мікро"))
        add("т", u = Measure("тонна")); add("ц", u = Measure("центнер")); add("унц", u = Measure("унція"))
        add("год", u = Measure("година")); add("хв", u = Measure("хвилина")); add("сек", u = Measure("секунда"))
        add("мс", u = Measure("секунда", "мілі")); add("мкс", u = Measure("секунда", "мікро")); add("нс", u = Measure("секунда", "нано"))
        add("шт", u = Measure("штука")); add("чол", u = Measure("чоловік")); add("ос", u = Measure("особа")); add("екз", u = Measure("примірник"))
        add("вольт", u = Measure("вольт")); add("мв", u = Measure("вольт", "мілі"))
        add("амп", u = Measure("ампер")); add("ма", u = Measure("ампер", "мілі")); add("мка", u = Measure("ампер", "мікро"))
        add("вт", u = Measure("ват")); add("квт", u = Measure("ват", "кіло")); add("мвт", u = Measure("ват", "мега")); add("гвт", u = Measure("ват", "гіга"))
        add("квт·год", "квт⋅год", "квт-год", "квт*год", "квтгод", "квт·г", "квт·ч", u = Measure("година", "кіловат-"))
        add("мвт·год", "мвт·г", u = Measure("година", "мегават-")); add("вт·год", "вт·г", u = Measure("година", "ват-"))
        add("ма·год", "ма·г", "мач", "ма-год", "магод", u = Measure("година", "міліампер-")); add("а·год", "а·г", u = Measure("година", "ампер-"))
        add("гц", u = Measure("герц")); add("кгц", u = Measure("герц", "кіло")); add("мгц", u = Measure("герц", "мега")); add("ггц", u = Measure("герц", "гіга"))
        add("дж", u = Measure("джоуль")); add("кдж", u = Measure("джоуль", "кіло")); add("мдж", u = Measure("джоуль", "мега"))
        add("кпа", u = Measure("паскаль", "кіло")); add("мпа", u = Measure("паскаль", "мега")); add("гпа", u = Measure("паскаль", "гекто"))
        add("мкф", u = Measure("фарад", "мікро")); add("нф", u = Measure("фарад", "нано")); add("пф", u = Measure("фарад", "піко"))
        add("тл", u = Measure("тесла")); add("лм", u = Measure("люмен")); add("лк", u = Measure("люкс")); add("бк", u = Measure("бекерель"))
        add("зв", u = Measure("зіверт")); add("мзв", u = Measure("зіверт", "мілі")); add("мкзв", u = Measure("зіверт", "мікро")); add("дб", u = Measure("децибел"))
        add("кал", u = Measure("калорія")); add("ккал", u = Measure("калорія", "кіло")); add("атм", u = Measure("атмосфера")); add("бар", u = Measure("бар"))
        add("моль", u = Measure("моль")); add("ммоль", u = Measure("моль", "мілі")); add("ммоль/л", u = Measure("моль", "мілі", suffix = " на літр"))
        add("біт", u = Measure("біт")); add("кбіт", u = Measure("біт", "кіло")); add("мбіт", u = Measure("біт", "мега")); add("гбіт", u = Measure("біт", "гіга"))
        add("байт", u = Measure("байт")); add("кб", u = Measure("байт", "кіло")); add("мб", u = Measure("байт", "мега")); add("гб", u = Measure("байт", "гіга"))
        add("тб", u = Measure("байт", "тера"))
        add("кбіт/с", u = Measure("біт", "кіло", suffix = " за секунду")); add("мбіт/с", u = Measure("біт", "мега", suffix = " за секунду"))
        add("гбіт/с", u = Measure("біт", "гіга", suffix = " за секунду")); add("кб/с", u = Measure("байт", "кіло", suffix = " за секунду"))
        add("мб/с", u = Measure("байт", "мега", suffix = " за секунду")); add("гб/с", u = Measure("байт", "гіга", suffix = " за секунду"))
        add("об/хв", u = Measure("оберт", suffix = " за хвилину")); add("уд/хв", u = Measure("удар", suffix = " за хвилину"))
        add("в. п", "в.п", u = Measure("пункт", adj = "відсотков")); add("к. с", "к.с", u = Measure("сила", adj = "кінськ"))
        add("°с", u = Measure("градус", suffix = " цельсія", rule = "degrees")); add("°ф", u = Measure("градус", suffix = " за фаренгейтом", rule = "degrees"))
        add("°", u = Measure("градус", rule = "degrees")); add("″", u = Measure("дюйм", rule = "degrees"))
        add("%", u = Measure("відсоток", rule = "numbers"))
        add("біл. руб", "біл.руб", u = Measure("рубль", adj = "білоруськ", rule = "currency"))
        add("руб", "₽", u = Measure("рубль", rule = "currency")); add("коп", u = Measure("копійка", rule = "currency"))
        add("$", u = Measure("долар", rule = "currency")); add("€", "євро", u = Measure("євро", rule = "currency")); add("£", u = Measure("фунт", rule = "currency"))
        add("₴", "грн", u = Measure("гривня", rule = "currency")); add("₸", u = Measure("тенге", rule = "currency")); add("¥", u = Measure("єна", rule = "currency"))
        add("₺", u = Measure("ліра", rule = "currency")); add("₩", u = Measure("вона", rule = "currency")); add("₪", u = Measure("шекель", rule = "currency"))
        add("₹", u = Measure("рупія", rule = "currency")); add("¢", u = Measure("цент", rule = "currency"))
        add("тис", u = Measure("тисяча", rule = "abbrev")); add("млн", u = Measure("мільйон", rule = "abbrev")); add("млрд", u = Measure("мільярд", rule = "abbrev"))
        add("трлн", u = Measure("трильйон", rule = "abbrev"))
    }
    /** Монета валюты: две цифры после запятой — копійки, центи, пенси ([units]). */
    private val MINOR = mapOf("руб" to "коп", "₽" to "коп", "біл. руб" to "коп", "біл.руб" to "коп", "$" to "¢", "€" to "¢", "євро" to "¢",
        "£" to "пенс", "₴" to "коп", "грн" to "коп")
    private val PENCE = Measure("пенс", rule = "currency")

    /** Единица за числом ([UNITS_MAP]) для регулярок: длинные ключи раньше коротких. Точку съедаем у сокращения на букву («км.»),
     * у знака («5 %.», «$65 000.») и у «°с», «°ф» она — конец предложения. */
    private val UNIT = "(" + UNITS_MAP.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")" +
        """(?:(?<=\p{L})(?<!°с|°ф)\.)?(?!\p{L})"""

    /** Правило «Правил» для единицы ([UNITS_MAP]); те же ключи, что у русского нормализатора. */
    fun unitKey(u: String) = UNITS_MAP[u]?.rule ?: "units"
    private fun unit(key: String) = UNITS_MAP[key] ?: PENCE.takeIf { key == "пенс" } ?: UNITS_MAP.getValue(key)

    /** Единица словами в падеже [c], в числе [pl]: прилагательное, слово, хвост. */
    private fun unitPhrase(u: Measure, c: Case, pl: Boolean): String =
        (u.adj?.let { adjEnd(it + "ий", c, u.gender, pl) + " " } ?: "") + u.forms[c.ordinal + if (pl) 6 else 0] + u.suffix
    /** Единица после дроби — родовий однини: «1,5 кг» → «… кілограма», «1/2 кг», «половина кілограма». */
    private fun unitGen(key: String) = unitPhrase(unit(key), Case.GEN, false)
    /** Единица после целого [n] без числа: «два з половиною кілограми». */
    private fun unitWord(key: String, n: Long) = unit(key).let { u -> unitPhrase(u, if (isOne(n) || isFew(n)) Case.NOM else Case.GEN, !isOne(n)) }

    /** Число и единица в падеже [case] (null — називний: 1 — однина, 2–4 — називний множини, 5+ — родовий множини; непрямий:
     * 1 — однина, иначе множина того же падежа): «дві тонни», «одне євро», «до п'яти кілометрів», «при двадцяти градусах». */
    private fun counted(n: Long, key: String, case: Case? = null): String {
        val u = unit(key)
        val c = case ?: Case.NOM
        val num = cardinal(n, c, u.gender)
        val word = if (c == Case.NOM || c == Case.ACC) (if (isOne(n) && c == Case.ACC) unitPhrase(u, Case.ACC, false) else unitWord(key, n))
            else unitPhrase(u, c, !isOne(n))
        return "$num $word"
    }

    /** «3 км» → «три кілометри», «5 %» → «п'ять відсотків», «2 т» → «дві тонни», «1,5 кг» → «одна ціла п'ять десятих
     * кілограма» ([decimal]). Валюта перед числом — как после: «$65 000» → «… доларів». Деньги с двумя цифрами после запятой —
     * с монетой: «1,20 грн» → «одна гривня двадцять копійок», «$1.50» → «один долар п'ятдесят центів»; нуль монет не читаем,
     * одна цифра («2,5 €») — дробь, «1,5 млн» — «півтора мільйона». Единица после «тис.», «млн» — родовий множини. Предлог
     * перед числом задаёт падеж: «до 5 км» → «до п'яти кілометрів», «при 20 °C» → «при двадцяти градусах цельсія», «у 5 км» →
     * «у п'яти кілометрах» (у времени «у» — знахідний: «у 2 год» → «у дві години»). Угол — правило degrees: «55°45′» →
     * «п'ятдесят п'ять градусів сорок п'ять хвилин». */
    fun units(text: String, rules: Rules = Rules()): String {
        val src = (if (rules.on("currency")) currencyAfter(text) else text).let { if (rules.on("degrees")) angles(it) else it }
        return MULT_UNIT.replace(NUMBER_UNIT.replace(src) { m ->
            val raw = m.groupValues[1].replace(" ", "").replace(" ", "")
            val key = m.groupValues[3].lowercase()
            if (UNITS_MAP[key]?.takeIf { rules.on(it.rule) } == null) return@replace m.value
            // длиннее Long — по цифрам, единица словом (родовий множини), а не буквами
            val n = raw.toLongOrNull() ?: return@replace m.value.substring(0, m.groups[3]!!.range.first - m.range.first) +
                unitPhrase(unit(key), Case.GEN, true)
            val frac = m.groupValues[2]
            // «о 5 год.» — час доби: «о п'ятій годині»
            if (key == "год" && frac.isEmpty() && n in 0..24 && AT_TIME_BEHIND.containsMatchIn(src.substring(maxOf(0, m.range.first - 4), m.range.first)))
                return@replace ordinal(n, Case.PRE, Gender.F) + " годині"
            val case = UNIT_PREP.find(src.substring(maxOf(0, m.range.first - 40), m.range.first))?.groupValues?.get(1)?.lowercase()
                ?.let { p -> if ((p == "у" || p == "в") && key in TIME_UNITS) null else UNIT_PREP_CASE[p] }
            val minor = MINOR[key]?.takeIf { frac.length == 2 }
            if (minor != null) return@replace counted(n, key, case) + frac.toLong().let { if (it == 0L) "" else " " + counted(it, minor, case) }
            if (frac == "5") half(n, key, case)?.let { return@replace it }
            if (frac.isNotEmpty()) return@replace "${decimal(raw, frac, case)} ${unitGen(key)}"
            counted(n, key, case)
        }) { m ->
            val k = m.groupValues[2].lowercase()
            UNITS_MAP[k]?.takeIf { rules.on(it.rule) }?.let { "${m.groupValues[1]} ${unitPhrase(it, Case.GEN, true)}" } ?: m.value
        }
    }
    private val TIME_UNITS = setOf("год", "хв", "сек", "мс", "мкс", "нс")
    /** Предлог перед числом с единицей → падеж ([units]). «з» — нет: «з 5 кг» и родовий, и орудний; «на», «за», «через»,
     * «понад», «про» — знахідний («понад тисячу гривень», «за одну годину»). */
    private val UNIT_PREP_CASE: Map<String, Case> =
        listOf("до", "від", "од", "біля", "коло", "близько", "без", "для", "крім", "окрім", "після", "замість", "серед",
            "протягом", "впродовж", "упродовж", "навколо", "поблизу", "більше", "менше").associateWith { Case.GEN } +
        listOf("у", "в", "при").associateWith { Case.PRE } +
        listOf("на", "за", "через", "понад", "про").associateWith { Case.ACC } +
        listOf("між", "поміж", "над", "під", "перед").associateWith { Case.INS } +
        listOf("завдяки", "всупереч").associateWith { Case.DAT }
    private const val AMPLIFIERS = """(?:приблизно|майже|всього|усього|лише|тільки|вже|уже|ще|плюс|мінус|якихось|цілих)"""
    private val UNIT_PREP = Regex("""(?<![\p{L}-])(${UNIT_PREP_CASE.keys.joinToString("|")})\s+(?:$AMPLIFIERS\s+)*(?:мінус\s+|плюс\s+)?$""",
        RegexOption.IGNORE_CASE)

    private fun angles(text: String) = ANGLE.replace(text) { m ->
        val (deg, min, sec) = m.destructured
        counted(deg.toLong(), "°") + " " + counted(min.toLong(), "хв") + (if (sec.isEmpty()) "" else " " + counted(sec.toLong(), "сек"))
    }
    /** «55°45′», «45°30′15″», «42°30'»: ASCII-кавычка секунд — только после минут. */
    private val ANGLE = Regex("""(?<![\d.,])(\d{1,3})\s*°\s*(\d{1,2})\s*[′'](?:\s*(\d{1,2})\s*[″"])?""")

    /** «$5» → «5 $» ([CURRENCY_BEFORE]) с пробелом впереди: «US$5» — не склейка, «$1,$2» — число не за запятой ([NUMBER_UNIT]).
     * После дефиса или тире за цифрой — без пробела: «$5-$10» — не «доларів- десять». */
    private fun currencyAfter(text: String) = CURRENCY_BEFORE.replace(text) { m ->
        val i = m.range.first
        (if (i > 1 && text[i - 1] in "-–—" && text[i - 2].isDigit()) "" else " ") + m.groupValues[2] + " " + m.groupValues[1]
    }

    /** Десятичная дробь: «3,14» → «три цілих чотирнадцять сотих», «1,05» → «одна ціла п'ять сотих», «0,5» → «нуль цілих п'ять
     * десятих»; знаменатель — по числу знаков («десятих», «сотих», «тисячних», «стотисячних»). В падеже [case]: «близько 1,5»
     * → «близько однієї цілої п'яти десятих». Знаков больше восьми — по цифрам через «кома». */
    fun decimal(intRaw: String, fracRaw: String, case: Case? = null): String {
        val n = intRaw.toLongOrNull()
        val a = fracRaw.toLongOrNull()
        if (n == null || a == null || fracRaw.length > 8)
            return (n?.let { cardinal(it) } ?: digits(intRaw)) + " кома " + group(fracRaw)
        val c = case ?: Case.NOM
        var b = 1L
        repeat(fracRaw.length) { b *= 10 }
        return agreed(n, c) { cc, pl -> adjEnd("цілий", cc, Gender.F, pl) } + " " + fraction(a, b, c)
    }
    /** Падеж дроби без единицы по предлогу перед ней (как у единиц, [UNIT_PREP]); «у», «в», «на», «через», «за» — знахідний:
     * «у 1,5 раза», «на 2,5 відсотка». */
    internal fun decimalCase(text: String, start: Int): Case? =
        DECIMAL_PREP.find(text.substring(maxOf(0, start - 40), start))?.groupValues?.get(1)?.lowercase()
            ?.let { UNIT_PREP_CASE[it]?.takeIf { _ -> it != "у" && it != "в" } ?: Case.ACC }
    private val DECIMAL_PREP = Regex("""(?<![\p{L}-])(${UNIT_PREP_CASE.keys.joinToString("|")})\s+(?:$AMPLIFIERS\s+)*(?:мінус\s+|плюс\s+)?$""",
        RegexOption.IGNORE_CASE)

    /** «1,5 млн» → «півтора мільйона», «2,5 тис.» → «дві з половиною тисячі», «близько 2,5 тис.» → «близько двох з половиною
     * тисяч» — у разрядов; «21,5 млн» — дробью. «Півтора» не склоняется (VESUM): в родовому — «півтора мільйона». */
    private fun half(n: Long, key: String, case: Case?): String? {
        if (key !in HALF_SCALES || n == 0L || n % 10 == 1L && n != 1L && n % 100 != 11L) return null
        val u = unit(key)
        val c = case ?: Case.NOM
        val nom = c == Case.NOM || c == Case.ACC
        if (n == 1L) return (if (u.gender == Gender.F) "півтори" else "півтора") + " " + unitPhrase(u, if (nom || c == Case.GEN) Case.GEN else c, !nom && c != Case.GEN)
        return cardinal(n, c, u.gender) + " з половиною " + if (nom) unitWord(key, n) else unitPhrase(u, c, true)
    }
    private val HALF_SCALES = setOf("тис", "млн", "млрд", "трлн")

    // ---- род и падеж числа (правило cases) ----

    /** Морфология языка из пака (VESUM, `morph.bin`, tools/vesum_morph.py): род и падеж существительного за числом.
     * null — у пака её нет: падеж только по предлогу. */
    @Volatile var morph: Morph? = null

    /** Теги слова: апостроф и «ъ» на его месте — без следа, как в morph.bin («сім'ями», «сімъями» → «сімями»). */
    private fun tags(w: String): Int = morph?.tags(w.lowercase().replace(APOS_HARD_RE, "")) ?: 0
    private val APOS_HARD_RE = Regex("[ъ’'`´ʼ‘]", RegexOption.IGNORE_CASE)

    /** Число словами в роде и падеже, как у белорусского: диапазоны «між 5 і 10» (орудний), «з 5 до 10», «від 5 до 10»
     * (родовий), «з 5 по 10» (первое — родовий); предлог с родовим («до», «близько», «після», «без»…), місцевим («при») или
     * орудним («між», «над», «під», «перед») — «близько 3 годин» → «близько трьох». Род — по существительному следом. Потом по
     * таблице [morph] — по самому существительному: «1 книга» → «одна», «2 книги» → «дві» (после 2–4 — називний множини),
     * «1 вікно» → «одне», «з 5 друзями» → «з п'ятьма», «у 2 книгах» → «у двох», «1 книгу» → «одну», «2 братів» → «двох»;
     * прилагательное между — вместе с существительным: «2 нові книги» → «дві». Номер («пункт 2») не склоняем. */
    fun cases(text: String): String {
        var s = RANGE_BETWEEN.replace(text) { m -> m.chain(1) { cardinal(it, Case.INS) }?.let { a -> m.chain(2) { cardinal(it, Case.INS) }?.let { b -> m.value.replaceGroups(m, 1 to a, 2 to b) } } ?: m.value }
        s = RANGE_FROM_TO.replace(s) { m ->
            // «з 1941 по 1945» — годы ([abbreviations] читает их с «р.»), не количество; «з 1000 до 2000 гривень» — количество
            if (listOf(2, 4).all { m.groupValues[it].length == 4 && m.groupValues[it].toInt() in 1000..2199 } &&
                !COUNTED_AFTER.containsMatchIn(s.substring(m.range.last + 1))) return@replace m.value
            val upTo = m.groupValues[3].equals("до", true)
            // длинные цифры (от миллиарда — по цифрам) не роняют разбор: Long не влезает — как написано
            val a = cardinal(m.groupValues[2].toLongOrNull() ?: return@replace m.value, Case.GEN)
            val b = m.groupValues[4].toLongOrNull() ?: return@replace m.value
            if (upTo) m.value.replaceGroups(m, 2 to a, 4 to cardinal(b, Case.GEN)) else m.value.replaceGroups(m, 2 to a)
        }
        for ((re, case) in TRIGGERS) s = re.replace(s) { m ->
            val noun = nextNoun(s, m.range.last + 1)
            // «з/із/зі» — и родовий («92 бали зі 100», «з 2 боків»), и орудний («з 3 друзями» — ниже по существительному):
            // родовий только с таблицей и без орудного за числом; «з 100» → «зі ста»
            val prep = m.value.takeWhile { !it.isWhitespace() }.lowercase()
            val from = prep in FROM_PREPS
            val after = s.substring(m.range.last + 1)
            // «з 10 до п'ятнадцяти кілограмів» (второе число уже прочли единицы) — диапазон, родовий и без таблицы
            if (from && !FROM_RANGE_AHEAD.containsMatchIn(after) && (morph == null ||
                    noun?.let { Case.INS in Morph.nounCases(it, true) + Morph.nounCases(it, false) } == true ||
                    FROM_YEARS_AHEAD.containsMatchIn(after))) return@replace m.value
            // «на», «за» — и знахідний («на 21 000 гривень»), и місцевий/орудний («на 2 поверсі», «за 2 будинками»): есть
            // существительное в таблице — решает [byNoun] ниже
            if (case == Case.ACC && prep in ACC_AMBIGUOUS && noun != null) return@replace m.value
            val g = noun?.let { nounGender(it) } ?: Gender.M
            m.chain(1) { n -> cardinal(n, case, g) }?.let { m.value.replaceGroups(m, 1 to it) }
                ?.let { if (from) ZI_RE.replace(it) { z -> if (z.value[0] == 'З') "Зі" else "зі" } else it } ?: m.value
        }
        if (morph == null) return s
        return NOUN_AFTER.replace(s) { m ->
            if (LABEL_BEHIND.containsMatchIn(s.substring(maxOf(0, m.range.first - 30), m.range.first))) return@replace m.value
            var t = tags(m.groupValues[2])
            // прилагательное — падеж и число вместе с существительным за ним: «2 нові книги», «з 5 новими книгами»
            if (Morph.isAdjective(t) && !Morph.isNoun(t)) {
                val tn = tags(m.groupValues[3])
                if (!Morph.isNoun(tn) || Morph.isAdjective(tn)) return@replace m.value
                t = agree(t, tn)
            }
            if (!Morph.isNoun(t) || m.groupValues[2].lowercase() in NOUN_STOP) return@replace m.value
            // місцевий — только после предлога: «автобус 21 їде», а не «двадцяти одному»
            val before = s.substring(maxOf(0, m.range.first - 40), m.range.first)
            if (!PREP_BEHIND.containsMatchIn(before))
                t = t and (1 shl (8 + Case.PRE.ordinal)).inv() and (1 shl (14 + Case.PRE.ordinal)).inv()
            val acc = ACC_PREP_BEHIND.containsMatchIn(before)
            m.chain(1) { n -> byNoun(n, t, acc) }?.let { m.value.replaceGroups(m, 1 to it) } ?: m.value
        }
    }

    /** Род существительного; у формы двух лемм разного рода (в VESUM «сім'я» — ж. р., «сім'я» «насіння» — с. р.) — ж. р.,
     * если он среди родов, иначе средний («євро» ч. і с.); ч. і ж. («суддя», «колега») — неизвестен. */
    private fun nounGender(t: Int): Gender? = Morph.gender(t) ?: Morph.genders(t).let { gs ->
        when {
            Gender.F in gs && Gender.M !in gs -> Gender.F
            Gender.N in gs && Gender.F !in gs -> Gender.N
            else -> null
        }
    }

    /** Форма числа перед существительным с тегами [t]: из каждого разбора — своя, одна на все — она, иначе null. После 1 —
     * однина в падеже существительного (знахідний: ч. р. істота — «одного», с. — «одне», ж. — «одну»); после 2–4 —
     * називний/знахідний множини («дві книги»; знахідний істот — «двох братів»), родовий множини — родовий («двох друзів»);
     * после 5+ родовий множини — називний («п'ять книг»); непрямі множини — тот же падеж. Не 1, а существительное только в
     * непрямому однини — номер, порядковое: «на 2 поверсі» → «на другому», «о 7 годині» → «о сьомій», «учень 2 класу» →
     * «другого». После прийменника знахідного ([acc]: «на», «за», «через», «понад», «про», «у/в») прямая форма — знахідний:
     * «за тисячу гривень», «на двадцять одну тисячу», «за одну ніч». Только множинні («двері», «сани») после 1 — «одні»,
     * «двадцять одні двері». */
    private fun byNoun(n: Long, t: Int, acc: Boolean = false): String? {
        val g = nounGender(t)
        val one = isOne(n)
        val few = isFew(n)
        val direct = if (acc) Case.ACC else Case.NOM
        val out = HashSet<String>()
        val sg = Morph.nounCases(t, plural = false)
        val pl = Morph.nounCases(t, plural = true)
        // только множинні: у леммы нет однини, а с ней и рода (tools/vesum_morph.py ставит род по формам однини)
        if (one && sg.isEmpty() && pl.isNotEmpty() && Morph.genders(t).isEmpty()) {
            for (c in pl) out += (if (n > 1) cardinal(n - 1, c) + " " else "") + ONE_PLURAL[c.ordinal]
        } else if (one) {
            if (g == null) return null
            if (acc && Case.ACC in sg) return if (g == Gender.M && Morph.animate(t)) cardinal(n, Case.GEN) else cardinal(n, Case.ACC, g)
            if (Case.NOM in sg) return cardinal(n, Case.NOM, g)
            for (c in sg) out += if (c == Case.ACC && g == Gender.M && Morph.animate(t)) cardinal(n, Case.GEN) else cardinal(n, c, g)
        } else if (pl.isEmpty() && g != null && sg.isNotEmpty() && sg.none { it == Case.NOM || it == Case.ACC }) {
            // «класу» — и родовий, и давальний: номер за словом — родовий («учень другого класу»)
            for (c in if (Case.GEN in sg) setOf(Case.GEN) else sg) out += ordinal(n, c, g)
        } else for (c in pl) when (c) {
            Case.NOM -> if (few) out += cardinal(n, direct, g ?: Gender.M)
            Case.ACC -> if (few) out += if (Morph.animate(t)) cardinal(n, Case.GEN) else cardinal(n, direct, g ?: Gender.M)
            Case.GEN -> out += if (few) cardinal(n, Case.GEN) else cardinal(n, direct, g ?: Gender.M)
            else -> out += cardinal(n, c, g ?: Gender.M)
        }
        return out.singleOrNull()
    }
    /** «Одні» (VESUM numr p) по падежам — у только множинних. */
    private val ONE_PLURAL = arrayOf("одні", "одних", "одним", "одні", "одними", "одних")

    /** Теги существительного [tn], где клетки — только общие с прилагательным [ta] перед ним (истоте знахідний = родовий). */
    private fun agree(ta: Int, tn: Int): Int {
        var sg = ta shr when (nounGender(tn)) { Gender.F -> 20; Gender.N -> 26; else -> 8 } and 63
        var pl = ta shr 14 and 63
        if (Morph.animate(tn)) {
            if (sg shr Case.GEN.ordinal and 1 != 0) sg = sg or (1 shl Case.ACC.ordinal)
            if (pl shr Case.GEN.ordinal and 1 != 0) pl = pl or (1 shl Case.ACC.ordinal)
        }
        return (tn and 0xFF) or ((tn shr 8 and 63 and sg) shl 8) or ((tn shr 14 and 63 and pl) shl 14)
    }

    /** Существительное за числом (прилагательное перед ним пропускаем) — теги или null. */
    private fun nextNoun(s: String, from: Int): Int? {
        if (morph == null) return null
        val w = NEXT_WORDS.find(s.substring(from, minOf(s.length, from + 80))) ?: return null
        return w.groupValues.drop(1).filter { it.isNotEmpty() }.map { tags(it) }.firstOrNull { Morph.isNoun(it) && !Morph.isAdjective(it) }
    }

    /** Числа цепочки в группе [g] («2-3», «3 і 5») — каждое через [f]; null — хоть одно не вышло. */
    private fun MatchResult.chain(g: Int, f: (Long) -> String?): String? {
        var bad = false
        val out = NUM_RE.replace(groupValues[g]) { d -> d.value.filter { it.isDigit() }.toLongOrNull()?.let(f) ?: run { bad = true; d.value } }
        return if (bad) null else out
    }
    private fun String.replaceGroups(m: MatchResult, vararg r: Pair<Int, String>): String {
        var res = this
        for ((g, v) in r.sortedByDescending { m.groups[it.first]!!.range.first }) {
            val gr = m.groups[g]!!.range
            res = res.substring(0, gr.first - m.range.first) + v + res.substring(gr.last + 1 - m.range.first)
        }
        return res
    }

    /** Месяцы в родовому — после дня. */
    private val MONTHS = arrayOf("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня",
        "листопада", "грудня")

    /** Число, можно разрядами через пробел («1 234 567»); без соседних групп («44 123 45»). */
    private const val NUM = """(?:\d{1,3}(?:[ \x{A0}\x{202F}]\d{3})+(?![ \x{A0}\x{202F}]?\d)|\d+)"""
    /** Цепочка чисел: «2-3», «3, 5 і 7»; запятая — только с пробелом («1,1» — дробь). */
    private const val NUM_CHAIN = """$NUM(?:(?:,\s+|\s*[-–—]\s*|\s+(?:і|й|та|чи|або)\s+)$NUM){0,6}"""
    /** Не часть дроби, времени, порядкового («5-го»), процента; не год («1990 року») и не день перед месяцем. */
    private val NUM_TAIL = """(?![:/\d]|[.,]\d)(?!-\p{L})(?!\s?%)(?!(?<=\d{3})\s*(?:рік|року|році|роком|роки|роках|рр?\.)(?!\p{L}))""" +
        """(?!\s+(?:${MONTHS.joinToString("|")})(?!\p{L}))"""
    private fun trigger(alt: String) = Regex("""(?<![\p{L}-])(?:$alt)(?![\p{L}])(?:\s+$AMPLIFIERS(?![\p{L}]))*\s+($NUM_CHAIN)$NUM_TAIL""",
        RegexOption.IGNORE_CASE)
    /** «з», «із», «зі», «зо» — чередование одного предлога: каждый список предлогов — со всеми вариантами. */
    private val FROM_PREPS = setOf("з", "із", "зі", "зо")
    private val TRIGGERS = listOf(
        trigger("""до|від|од|біля|коло|близько|без|для|крім|окрім|після|замість|серед|протягом|впродовж|упродовж|навколо|поблизу|""" +
            """проти|щодо|з-за|із-за|з-під|із-під|більше|менше|старше|молодше""") to Case.GEN,
        trigger("""при""") to Case.PRE,
        trigger("""між|поміж|над|під|перед""") to Case.INS,
        trigger("""завдяки|всупереч|назустріч""") to Case.DAT,
        // знахідний: «понад тисячу учасників», «через дві години», «за тисячу гривень»; «на/за» — только без существительного
        // в таблице ([ACC_AMBIGUOUS], иначе решает [byNoun])
        trigger("""понад|через|про|на|за""") to Case.ACC,
        trigger("""з|із|зі|зо""") to Case.GEN)
    private val ACC_AMBIGUOUS = setOf("на", "за")
    /** Прийменник знахідного перед числом ([byNoun]): прямая форма числа — знахідний. */
    private val ACC_PREP_BEHIND = Regex("""(?<![\p{L}])(?:на|за|через|понад|про|у|в)\s+(?:$AMPLIFIERS\s+)*$""", RegexOption.IGNORE_CASE)
    /** За «з N» — «по/до» с числом: годы «з 2010 по 2015 рік» ([RANGE_FROM_TO] их оставил). */
    private val FROM_YEARS_AHEAD = Regex("""^\s+(?:по|до)\s+\d""", RegexOption.IGNORE_CASE)
    /** За «з N» — «до» и слово: второе число диапазона уже словами ([units] раньше). */
    private val FROM_RANGE_AHEAD = Regex("""^\s+до\s+\p{L}""", RegexOption.IGNORE_CASE)
    /** «з» перед «ст…» — «зі» («зі ста»), как требует милозвучність; «із» — как написано. */
    private val ZI_RE = Regex("""^з(?=\s+ст)""", RegexOption.IGNORE_CASE)
    private val RANGE_BETWEEN = Regex("""(?<![\p{L}])(?:між|поміж)\s+(\d+)$NUM_TAIL\s+(?:і|й|та)\s+(\d+)$NUM_TAIL""", RegexOption.IGNORE_CASE)
    /** «з 5 до 10», «від 5 до 10», «з 5 по 10»; между числами — одно слово («з 10 000 гривень до 1000»). */
    private val RANGE_FROM_TO = Regex("""(?<![\p{L}])(з|із|зі|від|од)\s+(\d+)$NUM_TAIL(?:\s+\p{L}+)?\s+(до|по)\s+(\d+)$NUM_TAIL""", RegexOption.IGNORE_CASE)
    /** Слово за диапазоном — не «рік», «роки», «р.»: тогда числа — количество. */
    private val COUNTED_AFTER = Regex("""^\s+(?!р)\p{L}""", RegexOption.IGNORE_CASE)
    private val NUM_RE = Regex(NUM)
    private val NOUN_AFTER = Regex("""(?<![\p{L}\d,./–-])(?<!\d[ \x{A0}\x{202F}])($NUM_CHAIN)$NUM_TAIL\s+(\p{L}{2,})(?![\p{L}-])(?=(?:\s+(\p{L}{2,}))?)""",
        RegexOption.IGNORE_CASE)
    private val NEXT_WORDS = Regex("""^\s+(\p{L}+)(?:\s+(\p{L}+))?""")
    /** Номер, а не количество: «пункт 22 правил», «номер 5 автобуса», «п. 3 статті». */
    private val LABEL_BEHIND = Regex("""(?<![\p{L}])(?:(?:пункт\p{L}{0,2}|стат(?:т\p{L}{0,2}|ей)|глав\p{L}{0,2}|номер\p{L}{0,2}|том\p{L}{0,2}|""" +
        """розділ\p{L}{0,2}|частин\p{L}{0,2}|параграф\p{L}{0,2}|сторінк\p{L}{0,2}|квартир\p{L}{0,2}|будин\p{L}{0,3}|корпус\p{L}{0,2})\s+|""" +
        """(?:п|пп|ст|гл|ч|рис|табл|с|буд|кв|т)\.\s*)$""", RegexOption.IGNORE_CASE)
    /** Расхождения таблицы с узусом: «євро» — и ч., и с. р. (у нас «одне», [counted]); русизм «раза». */
    private val NOUN_STOP = setOf("євро", "раза")
    /** Прийменник місцевого (и «о» времени: «о 7 годині») перед числом. */
    private val PREP_BEHIND = Regex("""(?<![\p{L}])(?:у|в|на|об|о|при|по)\s+(?:$AMPLIFIERS\s+)*$""", RegexOption.IGNORE_CASE)

    // ---- арифметика ----

    /** Знаки между числами словами (правило arith): «2 + 3 = 5» → «два плюс три дорівнює п'ять», «2×3» — «помножити на»,
     * «÷» — «поділити на»; «-» — мінус только в выражении с «=» (иначе диапазон «10-15»). Размер буквой: «5х10 м» → «п'ять на
     * десять метрів». Сравнение: «x > 5» — «більше за», «≥» — «більше або дорівнює». «√2» — «корінь з двох», степень: «10²» —
     * «у квадраті», «10⁻³» — «у мінус третьому степені» (степінь — ч. р., Правопис). «Тиск 120/80» — «сто двадцять на
     * вісімдесят», «у пропорції 1:4» — «один до чотирьох». «у»/«в» — по милозвучності: после гласной «в» («два в кубі»). */
    fun arith(text: String): String {
        var s = dimensions(text)
        s = TIMES_SIZE_RE.replace(s, " на ")
        s = COMPARE_RE.replace(s) { m -> " " + COMPARE_WORDS.getValue(m.groupValues[1].ifEmpty { m.groupValues[2] }) + " " }
        s = SQRT_RE.replace(s) { m ->
            val n = m.groupValues[2].toLongOrNull() ?: return@replace m.value
            "корінь з " + (if (m.groupValues[1].isNotEmpty()) "мінус " else "") + cardinal(n, Case.GEN)
        }
        s = PRESSURE_RE.replace(s) { m ->
            val before = s.substring(0, m.range.first)
            if (!PRESSURE_CONTEXT_RE.containsMatchIn(before) && !s.substring(m.range.last + 1).trimStart().startsWith("мм рт")) m.value
            else cardinal(m.groupValues[1].toLong()) + " на " + m.groupValues[2]
        }
        s = RATIO_RE.replace(s) { m ->
            val a = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val b = m.groupValues[2].replace(" ", "").toLongOrNull() ?: return@replace m.value
            cardinal(a) + " до " + cardinal(b, Case.GEN)
        }
        s = MINUS_BEFORE_EQUALS_RE.replace(EQUALS_MINUS_RE.replace(s, " дорівнює мінус "), " мінус ")
        s = POWER_OP_RE.replace(s) { m -> m.groupValues[1].toLongOrNull()?.let { cardinal(it) }?.let { b -> "$b ${uv(b)} степені " } ?: m.value }
        s = ARITH_RE.replace(s) { m -> " ${ARITH_OPS.getValue(m.groupValues[1])} " }
        s = EQUALS_RE.replace(s, " дорівнює ")
        // степень — с основанием сразу словами: «у»/«в» по последней букве основы
        return POWER_RE.replace(s) { m ->
            val base = m.groupValues[1].let { r -> r.toLongOrNull()?.let { cardinal(it) } ?: digits(r) }
            val eRaw = m.groupValues[3].map { SUP_DIGITS.indexOf(it) }.joinToString("")
            // показатель длиннее Long — по цифрам, а не пропадает
            val e = eRaw.toLongOrNull() ?: return@replace "$base ${uv(base)} степені ${if (m.groupValues[2] == "⁻") "мінус " else ""}${digits(eRaw)}"
            val minus = m.groupValues[2] == "⁻"
            base + " " + uv(base) + " " + when {
                !minus && e == 2L -> "квадраті"
                !minus && e == 3L -> "кубі"
                else -> (if (minus) "мінус " else "") + ordinal(e, Case.PRE) + " степені"
            }
        }
    }
    /** Размер «3x4», «5х10 м» — «на»; [UkNormalizer] зовёт до латиницы, иначе латинская «x» стала бы «екс». */
    internal fun dimensions(text: String) = DIMENSION_RE.replace(text, " на ")
    /** Формула с буквой-переменной («x² + y² = z²», «a ≥ b», «2x + 3 = 7»): буквы — шкільними назвами (ікс, ігрек, зет), «²» и «³»
     * за буквой — «у квадраті», «у кубі», знаки — словами; числа остаются цифрами. До латиницы: иначе «x» стала бы «екс», а «=»
     * и «≥» между буквами выпадали фильтром. Не проверено носителем. */
    internal fun algebra(text: String) = FORMULA_RE.replace(text) { m ->
        if (m.value.none { it in 'a'..'z' || it in 'A'..'Z' }) return@replace m.value
        val ops = FORMULA_OP_RE.findAll(m.value).map { o -> o.groupValues.drop(1).first { it.isNotEmpty() } }.toList()
        // тире — минус только рядом с другим знаком: «a — b» в тексте — тире
        if (ops.all { it in DASHES }) return@replace m.value
        // одни заглавные через «+» без чисел рядом с клавишей или «натисн…» — сочетание клавиш («Натисніть A + B», «Ctrl + A + B»):
        // буквы английскими именами, как в тексте; без них («Вітаміни A + E») — формула, «а плюс е»
        if (ops.all { it == "+" } && m.value.none { it.isDigit() || it in 'a'..'z' } && KEY_CONTEXT_RE.containsMatchIn(text))
            return@replace m.value
        val s = FORMULA_LETTER_RE.replace(m.value) { l ->
            val name = SCHOOL_LATIN.getValue(l.groupValues[1].lowercase()[0])
            val pow = when (l.groupValues[2]) { "²" -> " ${uv(name)} квадраті"; "³" -> " ${uv(name)} кубі"; else -> "" }
            (if (l.range.first > 0 && m.value[l.range.first - 1].isDigit()) " " else "") + name + pow
        }
        FORMULA_OP_RE.replace(s) { o -> o.groupValues.drop(1).first { it.isNotEmpty() }.let {
            " " + (if (it in DASHES) "мінус" else COMPARE_WORDS[it] ?: ARITH_OPS.getValue(it)) + " " } }
    }
    // коэффициент — только строчной буквой: «2x», «10a»; «4G», «3D», «4K» — не формула
    private const val TERM = """(?:\d+(?:[.,]\d+)?[a-z]?|[A-Za-z])[²³]?"""
    // «<», «>» — только в пробелах, как [COMPARE_RE]; минус в пробелах до слоя уже стал тире (Normalizer.punctuation): «x − 1 = 0»
    // приходит как «x — 1 = 0»
    private const val FORMULA_OP = """\s*([=+−×÷≥≤≠])\s*|\s+([<>—–])\s+"""
    private val DASHES = setOf("—", "–")
    /** Клавиша-модификатор или глагол «натиснути» в куске — «A + B» сочетание клавиш. Не проверено носителем. */
    private val KEY_CONTEXT_RE = Regex("""(?<![\p{L}])(?:ctrl|shift|alt|win|cmd|fn|tab|esc|enter|контрол|шифт|шіфт|альт|ентер|натисн\p{L}*)(?![\p{L}])""",
        RegexOption.IGNORE_CASE)
    private val FORMULA_RE = Regex("""(?<![\p{L}\d.,])$TERM(?:(?:$FORMULA_OP)[-−]?$TERM)+(?![\p{L}\d])""")
    private val FORMULA_LETTER_RE = Regex("""(?<![A-Za-z])([A-Za-z])([²³]?)""")
    private val FORMULA_OP_RE = Regex(FORMULA_OP)
    /** Латинские буквы в математике — как в школе. Не проверено носителем. */
    private val SCHOOL_LATIN = mapOf('a' to "а", 'b' to "бе", 'c' to "це", 'd' to "де", 'e' to "е", 'f' to "еф", 'g' to "же",
        'h' to "аш", 'i' to "і", 'j' to "йот", 'k' to "ка", 'l' to "ель", 'm' to "ем", 'n' to "ен", 'o' to "о", 'p' to "пе", 'q' to "ку",
        'r' to "ер", 's' to "ес", 't' to "те", 'u' to "у", 'v' to "ве", 'w' to "дубль-ве", 'x' to "ікс", 'y' to "ігрек", 'z' to "зет")
    /** «у» после согласной, «в» после гласной (милозвучність): «десять у квадраті», «два в кубі». */
    private fun uv(before: String) = if (before.trimEnd().lastOrNull()?.lowercaseChar()?.let { it in "аеєиіїоуюя" } == true) "в" else "у"
    private val ARITH_OPS = mapOf("+" to "плюс", "×" to "помножити на", "*" to "помножити на", "÷" to "поділити на", "=" to "дорівнює", "−" to "мінус")
    private val ARITH_RE = Regex("""(?<=[\d)])\s*([+×*÷−])\s*(?=[-−]?[\d(])""")
    /** «2^10» — «два в степені десять»: основание словами потом, «в» по цифре: после 2, 3, 4… — гласная. */
    private val POWER_OP_RE = Regex("""(\d+)\s*\^\s*(?=[-−]?\d)""")
    /** «=» словом только рядом с числом: «x = y» остаётся как есть. */
    private val EQUALS_RE = Regex("""(?<=\d)\s*=\s*(?=[-−]?[\p{L}\d(])|(?<=[\p{L}\d)])\s*=\s*(?=[-−]?[\d(])""")
    private val MINUS_BEFORE_EQUALS_RE = Regex("""(?<=\d)\s*[-–]\s*(?=\d+(?:[.,]\d+)?\s*=)""")
    private val EQUALS_MINUS_RE = Regex("""\s*=\s*[-−–—]\s*(?=\d)""")
    /** «5х10», «3x4x5» — размер; не «0x1F» и не латиница за числом («5800X3D»). */
    private val DIMENSION_RE = Regex("""(?<=\d)(?<!(?<![\d.,])0)\s*[xXхХ]\s*(?=\d++(?:[.,]\d++)?+(?![A-Za-z](?!\d)))""", RegexOption.IGNORE_CASE)
    private val TIMES_SIZE_RE = Regex("""(?<=\d)\s*×\s*(?=\d+(?:[.,]\d+)?\s*$UNIT)""", RegexOption.IGNORE_CASE)
    /** «<» и «>» — только в пробелах: «<3» — сердце, «->» — стрелка. */
    private val COMPARE_RE = Regex("""\s*([≥≤≠])\s*(?=[-−]?\d)|(?<=[\p{L}\d)])\s+([<>])\s+(?=[-−]?\d)""")
    private val COMPARE_WORDS = mapOf("≥" to "більше або дорівнює", "≤" to "менше або дорівнює", "≠" to "не дорівнює", "<" to "менше за",
        ">" to "більше за")
    private val SQRT_RE = Regex("""√\s*([-−–]?)\s*(\d+)(?![\d.,])""")
    private val PRESSURE_RE = Regex("""(?<![\d/])(\d{2,3})/(\d{2,3})(?![\d/])""")
    private val PRESSURE_CONTEXT_RE = Regex("""тиск\p{L}*[^.!?]{0,40}$""", RegexOption.IGNORE_CASE)
    private val RATIO_RE = Regex("""(?<=(?:пропорці|масштаб|співвідношенн|відношенн)\p{L}{0,3}\s)(\d+)\s?:\s?(\d{1,3}(?: \d{3})*|\d+)(?![\d:])""",
        RegexOption.IGNORE_CASE)
    private const val SUP_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val POWER_RE = Regex("""(?<![\d.,])(\d+)([⁻⁺]?)([⁰¹²³⁴⁵⁶⁷⁸⁹]+)(?![⁰-⁹])""")

    // ---- знаки номера и разделы ----

    /** «№ 5», «§ 3» — словом в падеже предлога (правило numbers): «під № 7» → «під номером», «у § 4» → «у параграфі»;
     * «№№» — множина. Второй знак через «і», «або», запятую — в падеже первого. */
    fun signs(text: String): String {
        var lastCase: Case? = null; var lastEnd = -100
        return SIGN_RE.replace(text) { m ->
            val prep = m.groupValues[1]
            val sign = m.groupValues[2]
            val chained = prep.isEmpty() && m.range.first - lastEnd < 30 && SIGN_AFTER_AND_RE.containsMatchIn(text.substring(0, m.range.first))
            val case = SIGN_PREP[prep.lowercase()] ?: (if (chained) lastCase else null) ?: Case.NOM
            lastCase = case; lastEnd = m.range.last
            (if (prep.isEmpty()) "" else "$prep ") + SIGN_FORMS.getValue(sign[0])[case.ordinal + if (sign.length == 2) 6 else 0] + " "
        }
    }
    private val SIGN_FORMS = mapOf(
        '№' to "номер номера номеру номер номером номері номери номерів номерам номери номерами номерах".split(' '),
        '§' to "параграф параграфа параграфу параграф параграфом параграфі параграфи параграфів параграфам параграфи параграфами параграфах".split(' '))
    /** Предлог → падеж. «у», «в», «на» — місцевий (не знахідний), «з», «за» — орудний («з номером»). */
    internal val SIGN_PREP: Map<String, Case> =
        listOf("до", "від", "од", "без", "для", "біля", "коло", "близько", "крім", "окрім", "після", "замість", "серед", "вздовж",
            "уздовж", "навколо", "довкола", "проти", "з-під", "із-під", "з-за", "із-за").associateWith { Case.GEN } +
        listOf("завдяки", "назустріч", "всупереч").associateWith { Case.DAT } +
        listOf("через", "про").associateWith { Case.ACC } +
        listOf("з", "із", "зі", "зо", "під", "над", "перед", "за", "між", "поміж").associateWith { Case.INS } +
        listOf("у", "в", "на", "при", "по", "об", "о").associateWith { Case.PRE }
    private val SIGN_RE = Regex("""(?:(?<![\p{L}\d-])(${SIGN_PREP.keys.sortedByDescending { it.length }.joinToString("|")})\s+)?(№№|§§|№|§)\s*(?=\d)""",
        RegexOption.IGNORE_CASE)
    private val SIGN_AFTER_AND_RE = Regex("""(?:\s+(?:і|й|та|або|чи)|,)\s*$""", RegexOption.IGNORE_CASE)

    /** Номер раздела через точку (правило sections): после метки («п. 4.2», «ст. 20.1», «§ 4.2», «табл. 1.5», «ДСТУ 1.5»)
     * точку прячем меткой до дат — иначе «4.12» прочлось бы датой; [sections] после дат делает её словом «крапка», как и в
     * цепочке из трёх и больше чисел: «2.10.3» → «два крапка десять крапка три», «192.168.1.10». */
    fun sectionLabels(text: String): String = SECTION_LABEL_RE.replace(text) { m -> m.value.replace('.', SEC_DOT) }
    fun sections(text: String): String = SECTION_RE.replace(text) { m ->
        (if (m.range.first > 0 && text[m.range.first - 1].isLetter()) " " else "") +
            m.value.split('.', SEC_DOT).joinToString(" крапка ") { p -> if (p.length > 1 && p[0] == '0') digits(p) else p.toLongOrNull()?.let { cardinal(it) } ?: digits(p) }
    }
    /** Метка точки номера раздела ([sectionLabels]); символов частной области [UkNormalizer] в тексте не оставляет. */
    private const val SEC_DOT = ''
    private const val SECTION_LABEL = """(?:п|пп|підп|ст|ч|рис|мал|табл|гл|розд|дод|прим|§|№|пункт[аіуи]?|пунктом|підпункт[аіуи]?|""" +
        """статт[яіюе]|статтею|частин[аиіу]|частиною|розділ[уиі]?|розділом|малюн(?:ок|ка|ку|ком)|рисун(?:ок|ка|ку|ком)|""" +
        """таблиц[яіюе]|таблицею|глав[аиіу]|главою|параграф[аіуи]?|параграфом|додат(?:ок|ка|ку|ком)|ДСТУ|ГОСТ|ДБН)"""
    private val SECTION_LABEL_RE = Regex("""(?<=(?<![\p{L}\d])$SECTION_LABEL\.?\s{0,2})\d+(?:\.\d+)+(?!\.?\d|[.,]\d)""",
        RegexOption.IGNORE_CASE)
    private val SECTION_RE = Regex("""(?<![\d.])\d+(?:\x{E001}\d+)+|(?<![\d.])\d+(?:\.\d+){2,}(?!\.?\d)""")

    // ---- даты ----

    /** «21.10.2026» → «двадцять перше жовтня дві тисячі двадцять шостого року»; «3.09» → «третє вересня». Без года — только
     * «дд.мм» через точку: «1.5 кг», «1/2 склянки», «24/7» — не даты. День — порядковое середнего рода: в тексте родовий
     * («коли?»: «зустріч двадцять першого жовтня»), дата отдельно, после «сьогодні», «завтра», двоеточия — називний
     * («двадцять перше жовтня»). Первая группа — предлог перед датой ([DATE_PREP]): «на/по/про/через/за» — знахідний («на
     * перше вересня»), «перед» — орудний («перед першим вересня»), иначе родовий. Цифровые даты — правило dates, месяц
     * словом и диапазон дней — day_month. Не проверено носителем. */
    fun dates(text: String, rules: Rules = Rules()): String {
        fun date(src: String, m: MatchResult, dRaw: String, moRaw: String, yearRaw: String): String {
            val d = dRaw.toInt(); val mo = moRaw.toInt()
            if (d !in 1..31 || mo !in 1..12) return m.value
            val prep = m.groupValues[1]
            val case = when {
                prep.isNotEmpty() -> if (prep.trim().lowercase() == "перед") Case.INS else Case.ACC
                NOMINATIVE_BEFORE_RE.containsMatchIn(src.substring(0, m.range.first)) && DATE_ALONE_AFTER_RE.matches(src.substring(m.range.last + 1)) ||
                    NOMINATIVE_WORD_BEFORE_RE.containsMatchIn(src.substring(maxOf(0, m.range.first - 20), m.range.first)) -> Case.NOM
                else -> Case.GEN
            }
            val sb = StringBuilder(prep).append(ordinal(d.toLong(), case, Gender.N)).append(' ').append(MONTHS[mo - 1])
            if (yearRaw.isNotEmpty()) {
                val y0 = yearRaw.toLong()
                // двузначный год: «99» — 1999, «20» — 2020
                val y = if (yearRaw.length <= 2) (if (y0 > 30) 1900 else 2000) + y0 else y0
                sb.append(' ').append(ordinal(y, Case.GEN)).append(" року")
            }
            return sb.toString()
        }
        var s = text
        if (rules.on("dates")) {
            s.let { t -> s = DATE.replace(t) { m -> date(t, m, m.groupValues[2], m.groupValues[4], m.groupValues[5]) } }
            s.let { t -> s = ISO_DATE.replace(t) { m -> date(t, m, m.groupValues[4], m.groupValues[3], m.groupValues[2]) } }
            s.let { t -> s = DAY_MONTH.replace(t) { m -> date(t, m, m.groupValues[2], m.groupValues[3], "") } }
        }
        if (!rules.on("day_month")) return s
        // первый день диапазона — родовим: «15-16 травня» → «п'ятнадцятого — шістнадцятого травня», «з 1 по 5 травня»
        s = DAY_RANGE.replace(s) { m ->
            val d = m.groupValues[1].toLong()
            if (d !in 1..31) m.value else ordinal(d, Case.GEN, Gender.N) + m.groupValues[2].ifEmpty { " — " }
        }
        // месяц словом: «3 травня 2026 р.» → «третього травня … шостого року», как у цифровой даты
        val t = s
        return DAY_MONTH_WORD.replace(t) { m ->
            date(t, m, m.groupValues[2], (MONTHS.indexOf(m.groupValues[3].lowercase()) + 1).toString(), m.groupValues[4])
        }
    }
    /** Дата — вся фраза (до неё только знаки, после — знаки до конца): називний. */
    private val NOMINATIVE_BEFORE_RE = Regex("""^[\s\p{P}]*$""")
    private val DATE_ALONE_AFTER_RE = Regex("""[\s\p{P}]*""")
    /** «Сьогодні 21 жовтня», «Дата: 21.10.2026»: називний (тире — нет: «15 — 16 травня» диапазон). */
    private val NOMINATIVE_WORD_BEFORE_RE = Regex("""(?:(?<!\p{L})(?:сьогодні|завтра|вчора|учора|післязавтра|позавчора|дата)\s*:?|:)\s*$""",
        RegexOption.IGNORE_CASE)

    /** Предлог, меняющий падеж дня ([dates]), — первая группа у [DATE], [DAY_MONTH], [DAY_MONTH_WORD]. */
    private const val DATE_PREP = """(?:(?<!\p{L})((?:на|по|про|через|за|перед)\s+))?"""
    /** С годом: «12.05.2026», «12/05/26»; не после цифры и знака — иначе кусок большого числа. Хвост «р.» съедаем: «року»
     * дата уже говорит. */
    private val DATE = Regex("""(?<![\d.,])$DATE_PREP(\d{1,2})([./])(\d{1,2})\3(\d{4}|\d{2})(?!\d)(?:\s*р\.)?""", RegexOption.IGNORE_CASE)
    /** ISO «2026-05-12»: год, месяц, день. */
    private val ISO_DATE = Regex("""(?<![\d.,-])$DATE_PREP(\d{4})-(\d{2})-(\d{2})(?![\d-])""", RegexOption.IGNORE_CASE)
    /** «3 травня», «3 травня 2026 р.», «3 травня 2026 року»: год съедаем вместе с «р.»/«року». */
    private val DAY_MONTH_WORD = Regex("""(?<![\d.,])$DATE_PREP(\d{1,2})\s+(${MONTHS.joinToString("|")})(?!\p{L})(?:\s+(\d{4})(?!\d)(?:\s*р\.|\s+року(?!\p{L}))?)?""",
        RegexOption.IGNORE_CASE)
    /** Первый день диапазона перед «д місяць»: «15-16 травня», «з 1 по 5 травня», «від 5 до 10 травня». */
    private val DAY_RANGE = Regex("""(?<![\d.,])(\d{1,2})(?:\s*[-–—]\s*|(\s+(?:по|до)\s+))(?=\d{1,2}\s+(?:${MONTHS.joinToString("|")})(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    /** Без года: «3.09» — месяц двумя цифрами, не дробь «1.5», не кусок «1.10.2» и не число с единицей «1.05 кг», «5.05 %». */
    private val DAY_MONTH = Regex("""(?<![\d.,])$DATE_PREP(\d{1,2})\.(\d{2})(?!\d|[.,]\d)(?!\s*$UNIT)""", RegexOption.IGNORE_CASE)

    // ---- время ----

    /** 21:30 → «двадцять одна година тридцять хвилин» (9:00 → «дев'ять годин рівно»). С предлогом — порядковое, как говорят
     * о часах: «о 10:30» → «о десятій тридцять», «о 9:00» → «о дев'ятій годині», «з 8:00 до 20:30» → «з восьмої години до
     * двадцятої тридцяти», «по 18:00» → «по вісімнадцяту годину», «перед 9:00» → «перед дев'ятою годиною»; минуты 1–9 — «нуль
     * п'ять». Через точку — только после предлога ([TIME_DOT]): «о 19.00», «у 7.30»; без него «10.05» — дата, «1.5» — дробь.
     * Не проверено носителем. */
    fun times(text: String): String {
        // через точку — раньше: «по» смотрит на цифры времени перед ним («з 9:00 по 18.00»)
        val s = TIME_DOT.replace(text) { m ->
            val p = m.groupValues[1].lowercase()
            // «з/до…» перед «дд.мм» с месяцем 01–12 — скорее дата («з 12.05 по 15.05»); «о», «об», «у», «в» — всегда время
            if (p !in AT_PREPS && m.groupValues[4].toInt() in 1..12) return@replace m.value
            time(m.groupValues[3], m.groupValues[4], p)?.let { m.groupValues[1] + m.groupValues[2] + it } ?: m.value
        }
        // «21:30:15» — с секундами, раньше «21:30»
        val sec = TIME_SEC.replace(s) { m ->
            val sec = m.groupValues[3].toLong()
            time(m.groupValues[1], m.groupValues[2])?.let { t ->
                if (sec == 0L) t else t.removeSuffix(" рівно") + " " + counted(sec, "сек")
            }?.takeIf { sec < 60 } ?: m.value
        }
        val t = TIME.replace(sec) { m ->
            val p = TIME_PREP_BEHIND.find(sec.substring(maxOf(0, m.range.first - 12), m.range.first))?.groupValues?.get(1)?.lowercase()
            time(m.groupValues[1], m.groupValues[2], p) ?: m.value
        }
        // что осталось «N:M» — не время (счёт «2:1», «25:00»): тире вместо двоеточия, числа прочтёт [UkNormalizer]
        return SCORE_RE.replace(t, "$1 — $2")
    }
    private val AT_PREPS = setOf("о", "об", "у", "в")
    private val TIME_PREP_BEHIND = Regex("""(?<![\p{L}])(о|об|у|в|з|із|зі|від|до|близько|біля|після|по|перед)\s+$""", RegexOption.IGNORE_CASE)
    private val SCORE_RE = Regex("""(?<![\d:.,])(\d{1,3}):(\d{1,3})(?![\d:]|[.,]\d)""")

    private fun time(hRaw: String, miRaw: String, prep: String? = null): String? {
        val h = hRaw.toIntOrNull() ?: return null
        val mi = miRaw.toIntOrNull() ?: return null
        // «24:00» — конец суток, только с нулём минут
        if (h !in 0..24 || mi !in 0..59 || h == 24 && mi > 0) return null
        // порядковое часа, без минут — со словом «година» в том же падеже, с минутами — минуты количественным
        fun hour(c: Case, word: String, minCase: Case) = ordinal(h.toLong(), c, Gender.F) +
            if (mi == 0) " $word" else " " + (if (mi < 10) "нуль " else "") + cardinal(mi.toLong(), minCase, Gender.F)
        return when (prep) {
            // «у/в 7.30» — как «о»: так же время доби
            "о", "об", "у", "в" -> hour(Case.PRE, "годині", Case.NOM)
            "з", "із", "зі", "від", "до", "близько", "біля", "після" -> hour(Case.GEN, "години", Case.GEN)
            "по" -> hour(Case.ACC, "годину", Case.NOM)
            "перед" -> if (mi == 0) hour(Case.INS, "годиною", Case.INS) else null
            else -> null
        } ?: (counted(h.toLong(), "год") + if (mi > 0) " " + counted(mi.toLong(), "хв") else " рівно")
    }

    // ---- порядковые ----

    /** «1-го вересня» → «першого вересня», «5-й клас» → «п'ятий клас», «у 5-му класі» → «у п'ятому класі», «1990-ті» →
     * «тисяча дев'ятсот дев'яності». Форма — по нарощенню (Правопис 2019: «5-й», «5-го», «5-му», «5-м», «5-та», «3-тя»,
     * «5-те», «5-ту», «5-ї», «5-ю», «5-ті», «5-х», «5-ми»). «-й» перед существительным ж. р. или после «о» — давальний/
     * місцевий ж. р. («на 5-й сторінці» → «п'ятій», «о 7-й» → «сьомій»). Десятилетие (10–90 и четырёхзначное на 0): «-ті» —
     * називний множини («дев'яності»), «-х» — родовий/місцевий множини («у 1990-х» → «у тисяча дев'ятсот дев'яностих»),
     * «-ми» — орудний. Количественное: «2-х»…«4-х» — «двох», «трьох», «чотирьох»; русская манера «5-ти», «7-ми» — родовий
     * («до п'яти»), «-ма», «-ьма» не у порядкового («2-ма», «5-ма») — орудний («двома», «п'ятьма»); «7-ма», «8-ма» —
     * порядковое «сьома», «восьма». Не проверено носителем. */
    fun ordinals(text: String, rules: Rules = Rules()): String = ORD_SUFFIX.replace(text) { m ->
        val raw = m.groupValues[1]
        val n = raw.toLongOrNull() ?: return@replace m.value
        val sfx = m.groupValues[2].lowercase()
        val decade = n % 10 == 0L && (raw.length == 2 || raw.length == 4)
        val fem = ordinal(n, Case.NOM, Gender.F)
        val case = when {
            sfx == "х" && n in 2..4 -> Case.GEN
            sfx == "ти" || sfx == "ох" || sfx == "ьох" -> Case.GEN
            sfx == "ми" && !(decade && n >= 20) -> Case.GEN
            (sfx == "ма" || sfx == "ьма" || sfx == "ома") && !fem.endsWith(sfx) -> Case.INS
            else -> null
        }
        // падеж количественного — правило gen_suffix, остальные суффиксы — numbers
        if (!rules.on(if (case != null) "gen_suffix" else "numbers")) return@replace m.value
        if (case != null) return@replace cardinal(n, case)
        // «-й» перед существительным ж. р. или после «о» (время) — місцевий ж. р.: «на 5-й сторінці» → «п'ятій», «о 7-й»
        val femNext = sfx == "й" && (nextNoun(text, m.range.last + 1)?.let { nounGender(it) == Gender.F } == true ||
            AT_TIME_BEHIND.containsMatchIn(text.substring(maxOf(0, m.range.first - 4), m.range.first)))
        when {
            femNext -> ordinal(n, Case.PRE, Gender.F)
            sfx == "й" || sfx == "ий" || sfx == "ій" -> ordinal(n)
            sfx.endsWith("го") -> ordinal(n, Case.GEN)
            sfx.endsWith("му") -> ordinal(n, Case.PRE)
            sfx == "м" || sfx == "им" || sfx == "ім" -> ordinal(n, if (LOC_BEHIND.containsMatchIn(text.substring(maxOf(0, m.range.first - 4), m.range.first))) Case.PRE else Case.INS)
            sfx.endsWith("ї") -> ordinal(n, Case.GEN, Gender.F)
            sfx.endsWith("ю") && sfx != "тю" -> ordinal(n, Case.INS, Gender.F)
            sfx.endsWith("у") || sfx == "тю" -> ordinal(n, Case.ACC, Gender.F)
            sfx == "ми" || sfx == "ими" -> ordinal(n, Case.INS, plural = true)
            sfx.endsWith("х") -> ordinal(n, Case.GEN, plural = true)
            sfx.endsWith("і") -> ordinal(n, Case.NOM, plural = true)
            sfx.endsWith("е") || sfx.endsWith("є") -> ordinal(n, Case.NOM, Gender.N)
            sfx.endsWith("а") || sfx.endsWith("я") -> fem
            else -> m.value
        }
    }
    private val AT_TIME_BEHIND = Regex("""(?<![\p{L}])об?\s+$""", RegexOption.IGNORE_CASE)
    private val LOC_BEHIND = Regex("""(?<![\p{L}])(?:у|в|на)\s+$""", RegexOption.IGNORE_CASE)
    /** Число с нарощенням ([ordinals]); не кусок «8-044» и не «1.5-го». До девяти цифр: «у 10000-му році». */
    private val ORD_SUFFIX = Regex("""(?<![\d.,-])(\d{1,9})-(й|ий|ій|го|ого|ього|му|ому|ьому|м|им|ім|а|га|ша|тя|та|ма|ва|на|""" +
        """я|ї|ої|ьої|у|шу|гу|ту|тю|ю|ою|ьою|е|є|ше|ге|тє|те|ме|ве|не|і|ші|гі|ті|мі|ві|ні|х|их|іх|ми|ими|ти|ох|ьох|ьма|ома)(?!\p{L})""",
        RegexOption.IGNORE_CASE)

    /** Сложное прилагательное с числом (правило numbers): «5-річний» → «п'ятирічний», «100-річний» → «сторічний», «1-кімнатна»
     * → «однокімнатна», «2–3-кімнатна» → «дво-трикімнатна» (Правопис 2019: «дво-, трикімнатна» — без пробела перед второй
     * частью у нас). Слово — от трёх букв до окончания: «5-го», «5-ти» — нарощення. */
    fun compounds(text: String): String = COMPOUND_RE.replace(text) { m ->
        fun stem(n: Int) = compoundStem(n, one = "одно", adj = true)
        val a = m.groupValues[1].toInt()
        (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { stem(a) + "-" + stem(it.toInt()) } ?: stem(a)) + m.groupValues[3]
    }
    private val COMPOUND_RE = Regex("""(?<![\d.,-])(\d{1,3})(?:\s*[-–—]\s*(\d{1,3}))?-([а-яєіїґ'’ʼ]{3,}(?:ий|ій|а|я|е|є|і|ого|ього|ому|ьому|им|ім|ої|ьої|у|ю|ою|ьою|их|іх|ими|іми))(?!\p{L})""",
        RegexOption.IGNORE_CASE)

    /** Номер главы, частини, книги, розділу — порядковое в роде и падеже слова (правило numbers): «Глава 5» → «глава п'ята»,
     * «у главі 10» → «у главі десятій», «розділ 3» → «розділ третій». За номером счётное слово в родовому множини («книги
     * 5 авторів») — счёт, не трогаем. */
    fun chapters(text: String) = CHAPTER_RE.replace(text) { m ->
        val (g, case) = CHAPTER_FORMS[m.groupValues[1].lowercase()] ?: return@replace m.value
        val next = NEXT_WORDS.find(text.substring(m.range.last + 1, minOf(text.length, m.range.last + 40)))?.groupValues?.get(1)
        if (next != null && tags(next).let { Morph.isNoun(it) && Case.GEN in Morph.nounCases(it, plural = true) }) return@replace m.value
        m.groupValues[1] + " " + ordinal(m.groupValues[2].toLong(), case, g)
    }
    private val CHAPTER_FORMS: Map<String, Pair<Gender, Case>> = buildMap {
        fun add(g: Gender, vararg forms: String) = forms.forEachIndexed { i, f -> putIfAbsent(f, g to Case.values()[i]) }
        add(Gender.F, "глава", "глави", "главі", "главу", "главою", "главі")
        add(Gender.F, "частина", "частини", "частині", "частину", "частиною", "частині")
        add(Gender.F, "книга", "книги", "книзі", "книгу", "книгою", "книзі")
        add(Gender.M, "розділ", "розділу", "розділу", "розділ", "розділом", "розділі")
    }
    private val CHAPTER_RE = Regex("""(?<![\p{L}])(${CHAPTER_FORMS.keys.sortedByDescending { it.length }.joinToString("|")})\s+(\d{1,3})""" +
        """(?![\d:/]|[.,]\d|\s*[-–—]\s*\d|-\p{L})""", RegexOption.IGNORE_CASE)

    /** Пункты списка после двоеточия (правило numbers): «Причини: 1) голод; 2) холод» → «перше, голод; друге, холод». Скобка у
     * номера без двоеточия во фразе — не пункт. */
    fun lists(text: String) = LIST_ITEM_RE.replace(text) { m ->
        if (':' in text.substring(0, m.range.first).split('.', '!', '?', '…').last()) ordinal(m.groupValues[1].toLong(), Case.NOM, Gender.N) + ","
        else m.value
    }
    private val LIST_ITEM_RE = Regex("""(?<=:\s?|[;,]\s|\s[іта]\s)(\d{1,2})\)(?=\s)""", RegexOption.IGNORE_CASE)

    // ---- римские ----

    /** Римское число I–XXX перед «століття», «вік» — порядковым в падеже существительного: «XX століття» → «двадцяте
     * століття» (одно: «століття» и називний, и родовий; после слова — родовий: «література двадцятого століття»), «у XX
     * столітті» → «у двадцятому». Кириллические «Х», «І» тоже. «ст.» — тоже століття, падеж по предлогу: «у XX ст.» → «у
     * двадцятому столітті», «XIX–XX ст.» → «дев'ятнадцяте — двадцяте століття». После имени с заглавной — порядковое имени:
     * «Петро I» → «петро перший», «Катерина II» → «… друга». Остальную латиницу не трогаем. Век — правило roman, после
     * имени — roman_name. Не проверено носителем. */
    fun roman(text: String, rules: Rules = Rules()): String {
        val src = text
        var s = ROMAN.replace(src) { m ->
            // кириллическое «І» одно — союз: «І віку не вистачило»
            if (m.groupValues[1] == "І" || !rules.on("roman")) return@replace m.value
            val n = romanValue(m.groupValues[1])
            val noun = m.groupValues[2]
            val c = CENTURY_CASE[noun.lowercase()] ?: Case.NOM
            val case = if (c == Case.NOM && WORD_BEFORE_RE.find(src.substring(maxOf(0, m.range.first - 40), m.range.first))
                    ?.groupValues?.get(1)?.lowercase()?.let { it !in NOM_BEFORE_CENTURY } == true) Case.GEN else c
            val g = if (noun.lowercase().startsWith("ст")) Gender.N else Gender.M
            "${ordinal(n, case, g)} $noun"
        }
        val st = s
        s = ROMAN_ST.replace(st) { m ->
            val nums = listOf(m.groupValues[2], m.groupValues[4]).filter { it.isNotEmpty() }
            if (nums == listOf("І") || !rules.on("roman")) return@replace m.value
            // без предлога I–IV — ступінь, родовий: «орден Слави III ст.» → «третього ступеня», «I–II ст.» → «першого — другого
            // ступенів»; с «н. е.» за ним — век
            if (m.groupValues[1].isEmpty() && m.groupValues[7].isEmpty() && nums.all { romanValue(it) <= 4 })
                return@replace nums.joinToString(" — ") { r -> ordinal(romanValue(r), Case.GEN) } +
                    (if (nums.size > 1) " ступенів" else " ступеня") + (if (m.groups[6] != null) " ." else "")
            val upTo = m.groupValues[3].trim().lowercase() == "до"
            val case = if (upTo) Case.GEN else when (m.groupValues[1].trim().lowercase()) {
                "у", "в" -> Case.PRE
                "з", "із", "зі", "до", "від", "од" -> Case.GEN
                // после слова — родовий, как у «століття»: «у 1-й половині XX ст.», «курган III ст. до н. е.»
                else -> if (WORD_BEFORE_RE.find(st.substring(maxOf(0, m.range.first - 40), m.range.first))?.groupValues?.get(1)
                        ?.lowercase()?.let { it !in NOM_BEFORE_CENTURY } == true) Case.GEN else Case.NOM
            }
            val many = nums.size > 1 && !upTo
            val noun = when (case) {
                Case.PRE -> if (many) "століттях" else "столітті"
                Case.GEN -> if (many) "століть" else "століття"
                else -> "століття"
            }
            // точка «ст.» в конце предложения остаётся; перед заглавной после одного предлога в начале фразы («У XX ст.
            // Україна…») — середина, как «У 1941 р. Німеччина»
            val mid = m.groupValues[1].isNotEmpty() && m.groupValues[6].isNotBlank() && st.substring(0, m.range.first).none(Char::isLetter)
            m.groupValues[1] + nums.joinToString(if (upTo) " до " else " — ") { r -> ordinal(romanValue(r), case, Gender.N) } + " " + noun +
                (if (m.groups[6] != null && !mid) " ." else "")
        }
        // ponytail: род по имени: ж. р. — только [FEM_NAMES], прочее на -а/-я/-и — родовий ч. р. («Указ Петра I» — «першого»,
        // «за Миколи I»), согласная и «-о» — називний ч. р. («Карл XII», «Петро I»), прочее не трогаем. Предлог в начале фразы
        // («Після II світової») — не имя. Одна буква — только I/І после кириллического имени: «День Х», «Model X» не трогаем.
        // Кириллическая «І» перед точкой — инициал, перед словом с заглавной — союз («Київ І Львів»), перед строчной — римское
        s = ROMAN_NAME.replace(s) { m ->
            val name = m.groupValues[1]
            val r = m.groupValues[2]
            val next = m.groupValues[3]
            if (name in NOT_NAMES || !rules.on("roman_name")) return@replace m.value
            if (r.length == 1 && (r[0] !in "IІ" || name.last().code < 128 ||
                    r == "І" && (next == "." || next.lastOrNull()?.isUpperCase() == true)))
                return@replace m.value
            val (case, gender) = nameCase(name.replace(APOS_HARD_RE, "'")) ?: return@replace m.value
            "$name ${ordinal(romanValue(r), case, gender)}"
        }
        if (!rules.on("roman")) return s
        // прочие римские от двух букв — порядковым в согласии со словом за ним ([romanAgreed]): «Після II світової війни» →
        // «після другої світової війни». Одна буква («I», «Х», «V») — не трогаем: местоимение, союз, буква, инициал
        val g = s
        return ROMAN_ANY.replace(g) { m -> romanAgreed(romanValue(m.value), g.substring(0, m.range.first), g.substring(m.range.last + 1)) }
    }

    /** Порядковое римского [n] по соседям: слово главы перед ним («розділ IV», «у главі II») — его род и падеж; иначе слово за
     * ним — по таблице [morph] (прилагательное — его род, число, падеж; существительное — тоже), из нескольких разборов — тот,
     * что у предлога перед числом; без таблицы — по окончанию прилагательного («-ої», «-ого»…). Нет согласования — називний
     * ч. р. («том II» → «том другий»). Не проверено носителем. */
    private fun romanAgreed(n: Long, before: String, after: String): String {
        WORD_BEFORE_RE.find(before.takeLast(40))?.groupValues?.get(1)?.lowercase()?.let { CHAPTER_FORMS[it] }
            ?.let { (gg, c) -> return ordinal(n, c, gg) }
        // слово с апострофом целиком («з'їзду»): римские идут до снятия апострофа
        val w = ROMAN_NEXT_WORD.find(after.take(80))?.groupValues?.get(1)?.lowercase() ?: return ordinal(n)
        val forms = LinkedHashMap<String, Case>()
        val t = tags(w)
        if (Morph.isAdjective(t)) {
            for (gg in Gender.values()) for (c in Morph.adjCases(t, gg, false)) forms[ordinal(n, c, gg)] = c
            for (c in Morph.adjCases(t, null, true)) forms[ordinal(n, c, plural = true)] = c
        } else if (Morph.isNoun(t)) {
            val gg = nounGender(t) ?: Gender.M
            val sg = Morph.nounCases(t, false)
            val pl = Morph.nounCases(t, true)
            // только непрямые формы однини с родовим («курсу» — род., дав., місц.) — родовий, как номер в [byNoun]:
            // «студенти II курсу» → «другого курсу»
            for (c in if (pl.isEmpty() && Case.GEN in sg && sg.none { it == Case.NOM || it == Case.ACC }) setOf(Case.GEN) else sg)
                forms[ordinal(n, c, gg)] = c
            for (c in pl) forms[ordinal(n, c, plural = true)] = c
        }
        if (forms.size > 1) {
            val p = WORD_BEFORE_RE.find(before.takeLast(40))?.groupValues?.get(1)?.lowercase()
            val pc = p?.let { SIGN_PREP[it] ?: UNIT_PREP_CASE[it] }
            forms.entries.filter { it.value == pc }.map { it.key }.distinct().singleOrNull()?.let { return it }
        }
        forms.keys.singleOrNull()?.let { return it }
        if (forms.isNotEmpty()) return ordinal(n)
        // без таблицы — окончания прилагательного
        return ROMAN_ADJ_ENDINGS.entries.firstOrNull { w.endsWith(it.key) }?.value
            ?.let { (c, gg) -> if (gg == null) ordinal(n, c, plural = true) else ordinal(n, c, gg) } ?: ordinal(n)
    }
    /** Окончание прилагательного без таблицы → падеж и род (null — множина). «-ій» — давальний/місцевий ж. р. («світовій»:
     * одна форма «другій»), «-их», «-ими» — множина. «-а», «-е» не берём: так кончаются и существительные («II тура»). */
    private val ROMAN_ADJ_ENDINGS: Map<String, Pair<Case, Gender?>> = linkedMapOf("ої" to (Case.GEN to Gender.F),
        "ою" to (Case.INS to Gender.F), "ій" to (Case.PRE to Gender.F), "ого" to (Case.GEN to Gender.M), "ому" to (Case.PRE to Gender.M),
        "ими" to (Case.INS to null), "их" to (Case.GEN to null), "ий" to (Case.NOM to Gender.M))
    private val ROMAN_NEXT_WORD = Regex("""^\s+(\p{L}+(?:[’'`´ʼ‘]\p{L}+)?)""")
    /** Римское от двух букв отдельным словом, регистр важен: «II», «XIV», «ХІХ». */
    private val ROMAN_ANY = Regex("""(?-i:(?<![\p{L}\d'’ʼ-])(?=[IVXІХ]{2})$ROMAN_NUM(?![\p{L}\d'’ʼ]))""", RegexOption.IGNORE_CASE)
    /** Падеж «століття», «вік» по форме; «століття» — називний (родовий — по слову перед ним, [roman]). */
    private val CENTURY_CASE = mapOf("століття" to Case.NOM, "століттю" to Case.DAT, "століттям" to Case.INS, "столітті" to Case.PRE,
        "вік" to Case.NOM, "віку" to Case.GEN, "віці" to Case.PRE, "віком" to Case.INS)
    private val WORD_BEFORE_RE = Regex("""(\p{L}+)[\s,]*$""")
    /** Слова, после которых «століття» — називний: «Це XX століття», «Настало XXI століття». */
    private val NOM_BEFORE_CENTURY = setOf("це", "було", "є", "настало", "почалося", "прийшло", "минуло", "наше", "і", "й", "та", "а", "але")
    /** Женские имена (називний) перед римским; «Частина», «Книга», «Фаза», «Ліга»… — не имена, но так нумеруют. */
    private val FEM_NAMES = setOf("Катерина", "Єлизавета", "Елизавета", "Ганна", "Анна", "Ольга", "Марія", "Софія", "Ядвіга", "Бона",
        "Ізабелла", "Ізабела", "Жанна", "Вікторія", "Кристина", "Маргарита", "Тереза", "Урсула", "Варвара", "Клара", "Рогніда",
        "Елізабет", "Elizabeth", "Анастасія", "Олена", "Ірина", "Христина",
        "Частина", "Книга", "Глава", "Фаза", "Серія", "Група", "Категорія", "Зона", "Ліга")
    /** Мужские имена на -а/-я (називний): «Микола II» → «другий», не родовий «Петра». Не проверено носителем. */
    private val MASC_A_NAMES = setOf("Микола", "Сава", "Ілля", "Лука", "Микита", "Кузьма", "Хома", "Фома", "Ярема", "Данила", "Гаврила")
    /** Имена на «-ем» в називному — не орудний («Артем I»). */
    private val NOM_EM = setOf("Артем")

    /** Падеж и род имени перед римским ([roman]) — по окончанию, род — по спискам [FEM_NAMES] и [MASC_A_NAMES] (основа + «а»/«я»),
     * иначе мужской с основой на согласную: «з Катериною II» → «другою», «Катерину II» → «другу», «при Катерині II» → «другій»,
     * «з Петром I» → «першим», «Петру I», «при Петрові I», «при Петрі I» → «першому», «з Миколою II» → «другим», «Миколу II» →
     * «другого» (знахідний істоти), «Миколі II» → «другому». В VESUM имён нет (:prop не берём), морфология здесь не поможет.
     * null — не трогаем («-е», «-є», латинская гласная). Не проверено носителем. */
    private fun nameCase(name: String): Pair<Case, Gender>? {
        // слово главы с заглавной («Розділу II», «Главі III») — его род и падеж, не окончание имени
        CHAPTER_FORMS[name.lowercase()]?.let { (g, c) -> return c to g }
        val last = name.last().lowercaseChar()
        fun stemIn(cut: Int, set: Set<String>) = listOf("а", "я").any { name.dropLast(cut) + it in set }
        fun ends(vararg e: String) = e.any { name.endsWith(it) }
        return when {
            name in FEM_NAMES || last == 'a' -> Case.NOM to Gender.F
            name in MASC_A_NAMES -> Case.NOM to Gender.M
            ends("ою", "ею", "єю") -> Case.INS to (if (stemIn(2, MASC_A_NAMES)) Gender.M else Gender.F)
            ends("ові", "еві", "єві") -> Case.DAT to Gender.M
            // основа от трёх букв: «Петром», «Ігорем»; «Том II» — том, називний
            ends("ом", "ем", "єм") && name.length >= 5 && name !in NOM_EM -> Case.INS to Gender.M
            last == 'у' || last == 'ю' -> when {
                stemIn(1, FEM_NAMES) -> Case.ACC to Gender.F
                stemIn(1, MASC_A_NAMES) -> Case.GEN to Gender.M   // знахідний істоти = родовий
                else -> Case.DAT to Gender.M
            }
            // давальний и місцевий у этих форм совпадают: «другій», «другому»
            last == 'і' -> Case.PRE to (if (stemIn(1, FEM_NAMES)) Gender.F else Gender.M)
            // родовий жіночого: «Катерини II» → «другої», «Марії I» → «першої»
            (last == 'и' || last == 'ї') && stemIn(1, FEM_NAMES) -> Case.GEN to Gender.F
            last == 'а' || last == 'я' || last == 'и' -> Case.GEN to Gender.M
            last in "еєїyeiu" -> null
            else -> Case.NOM to Gender.M
        }
    }
    /** Предлоги с заглавной в начале фразы — не имя перед римским; «Літера І у…» — сама буква. */
    private val NOT_NAMES = setOf("Після", "До", "Від", "Під", "На", "За", "При", "Про", "По", "Біля", "Для", "Без", "Через",
        "Протягом", "Літера", "Буква", "Над", "Перед", "Між")

    internal fun romanValue(r: String): Long {
        var n = 0; var prev = 0
        for (c in r.reversed()) {
            val v = when (c) { 'I', 'І' -> 1; 'V' -> 5; else -> 10 }
            if (v < prev) n -= v else { n += v; prev = v }
        }
        return n.toLong()
    }

    /** I–XXX латиницей или кириллицей: непустое. */
    private const val ROMAN_NUM = """(?-i:(?=[IVXІХ])[XХ]{0,3}(?:[IІ][XХ]|[IІ]V|V?[IІ]{0,3}))"""
    /** Римское перед «століття», «вік» ([roman]), не часть слова. */
    private val ROMAN = Regex("""(?<![\p{L}\d])($ROMAN_NUM)\s+(столітт(?:я|ю|ям|і)|в(?:ік|іку|іці|іком))(?!\p{L})""", RegexOption.IGNORE_CASE)
    /** «у XX ст.», «XIX–XX ст.», «з XV до XVII ст.»: предлог, одно или два римских (третья группа — тире или «до»), «ст»;
     * шестая группа — за точкой конец или заглавная, седьмая — «н. е.» или «до н. е.». Точку «ст.» съедаем. */
    private val ROMAN_ST = Regex("""(?:(?<!\p{L})((?:у|в|з|із|зі|до|від|од)\s+))?(?<![\p{L}\d])($ROMAN_NUM)(?:(\s*[–—-]\s*|\s+до\s+)($ROMAN_NUM))?""" +
        """\s+(ст)\.(?=(\s*$|\s+(?-i:\p{Lu}))?)(?=(\s+(?:до\s+)?н\.\s*е\.)?)""", RegexOption.IGNORE_CASE)
    /** Римское после слова с заглавной: «Петро I», «Катерина II», «Карл XII». Слово — заглавная и строчные (и апостроф: «Мар'яна»):
     * «ПЕТРО І» не имя. Не перед «ст.» и диапазоном с «ст.» и не перед вторым инициалом; третья группа — точка или слово за ним. */
    private val ROMAN_NAME = Regex("""(?-i:(?<![\p{L}\d])(\p{Lu}\p{Ll}+(?:['’ʼ]\p{Ll}+)?)\s+($ROMAN_NUM)""" +
        """(?![\p{L}\d]|(?:\s*[–—-]\s*$ROMAN_NUM)?\s+ст\.|\.\s*\p{Lu}\.)(?=(\.|\s+\p{L})?))""", RegexOption.IGNORE_CASE)

    // ---- сокращения лет ----

    /** «1999 р.» → «1999 рік», «у 1812 р.» → «у 1812 році», «з 1990 р.» → «з 1990 року»; «рр.» — «роки». Год — 1000–2199 или три
     * цифры после «у/в» ([YEAR_R]); прочее «р.» за числом не трогаем («р. Дніпро» — річка, задача сокращений). */
    fun abbreviations(text: String): String {
        var s = text
        // «з 1941 по 1945 р.» — оба года порядковым: первый родовим, «по …» — знахідний с «рік», «до …» — родовий с «року».
        // Без «р.»/«року» — не трогаем: «з 1000 до 2000 гривень»
        s = YEAR_FROM_TO.replace(s) { m ->
            val (prep, y1, g1, mid, y2, g2) = m.destructured
            if (g1.isEmpty() && g2.isEmpty() || listOf(y1, y2).any { it.toInt() !in 1000..2199 }) return@replace m.value
            val upTo = mid.lowercase() == "до"
            "$prep ${ordinal(y1.toLong(), Case.GEN)} $mid " + (if (upTo) ordinal(y2.toLong(), Case.GEN) + " року" else ordinal(y2.toLong()) + " рік")
        }
        s = YEAR_R.replace(s) { m ->
            val prep = m.groupValues[1]
            val raw = m.groupValues[2]
            val p = prep.trim().lowercase()
            if (if (raw.length < 4) p != "у" && p != "в" else raw.toInt() !in 1000..2199) return@replace m.value
            val word = when (p) { "у", "в" -> "році"; "з", "із", "зі", "до", "від", "од", "після", "близько" -> "року"; else -> "рік" }
            "$prep$raw $word"
        }
        // диапазон лет: оба года порядковым, падеж по слову, у «рр.» — по предлогу: «у 1941–1945 рр.» → «у … першому — …
        // п'ятому роках»
        s = YEARS_RANGE.replace(s) { m ->
            val (y1, y2) = listOf(m.groupValues[2].toLong(), m.groupValues[3].toLong())
            if (y1 !in 1000..2199 || y2 !in 1000..2199) return@replace m.value
            val noun = m.groupValues[4].lowercase()
            val p = m.groupValues[1].trim().lowercase()
            val case = when {
                noun == "роках" || noun.startsWith("рр") && (p == "у" || p == "в") -> Case.PRE
                noun.startsWith("рр") && p in FROM_PREPS + setOf("до", "від", "од") -> Case.GEN
                else -> Case.NOM
            }
            val word = if (!noun.startsWith("рр")) noun else when (case) { Case.PRE -> "роках"; Case.GEN -> "років"; else -> "роки" }
            "${m.groupValues[1]}${ordinal(y1, case)} — ${ordinal(y2, case)} $word"
        }
        s = RR_LOC.replace(s, "$1роках")
        s = RR_GEN.replace(s, "$1років")
        return RR.replace(s, "роки")
    }
    /** «у 1990-х рр.» — [ordinals] уже дали «у … дев'яностих»: місцевий, «роках». В словах чисел апостроф ещё есть (его
     * делает «ъ» [UkNormalizer] после чисел). */
    private val RR_LOC = Regex("""(?<!\p{L})([ув]\s+(?:[\p{L}']+\s+){0,3}[\p{L}']+их\s+)рр\.?""", RegexOption.IGNORE_CASE)
    /** «-х» без «у/в» ([RR_LOC] уже прошёл) — родовий: «початок 1990-х рр.» → «… дев'яностих років». */
    private val RR_GEN = Regex("""(?<![\p{L}'])([\p{L}']+их\s+)рр\.?""", RegexOption.IGNORE_CASE)
    private val RR = Regex("""(?<!\p{L})рр\.?""", RegexOption.IGNORE_CASE)
    private val YEAR_FROM_TO = Regex("""(?<!\p{L})(з|із|зі|від|од)\s+(\d{4})(?!\d)(\s*р\.)?\s+(по|до)\s+(\d{4})(?!\d)(\s*(?:рр?\.|року?(?!\p{L})|рік(?!\p{L})))?""",
        RegexOption.IGNORE_CASE)
    private val YEAR_R = Regex("""(?:(?<!\p{L})((?:у|в|з|із|зі|до|по|від|од|після|близько)\s+))?(?<!\d)(\d{3,4})\s*р\.""", RegexOption.IGNORE_CASE)
    /** «у 1941–1945 рр.», «1941-1945 роках» ([abbreviations]). «років» нет: «1000–2000 років» — количество. */
    private val YEARS_RANGE = Regex("""(?:(?<!\p{L})((?:у|в|з|із|зі|до|від|од)\s+))?(?<![\d.,])(\d{4})\s*[–—-]\s*(\d{4})\s*(рр\.?|роках|роки)(?!\p{L})""",
        RegexOption.IGNORE_CASE)

    /** Сокращение без числа: «руб.» после «5 тис.», «грн» в «ціна в грн». Только отдельным словом: «капуста», «рубець» и уже
     * развёрнутое единицами «два рублі» не трогаем. Каждая единица — правилом единицы ([unitKey]). */
    fun standalone(text: String, rules: Rules = Rules()): String =
        STANDALONE.fold(text) { s, (re, rep, key) -> if (rules.on(key)) re.replace(s, rep) else s }.let { s ->
            // «тис. км» без числа — единица за развёрнутым «тисяч», как в [units]
            MULT_UNIT.replace(s) { m ->
                UNITS_MAP[m.groupValues[2].lowercase()]?.takeIf { rules.on(it.rule) }?.let { "${m.groupValues[1]} ${unitPhrase(it, Case.GEN, true)}" } ?: m.value
            }
        }
    private val STANDALONE = listOf(
        // «ціна в грн», «у руб.» — місцевий множини
        Triple(Regex("""(?<!\p{L})([ув])\s+грн\.?(?!\p{L})""", RegexOption.IGNORE_CASE), "$1 гривнях", unitKey("грн")),
        Triple(Regex("""(?<!\p{L})([ув])\s+руб\.?(?!\p{L})""", RegexOption.IGNORE_CASE), "$1 рублях", unitKey("руб")),
        Triple(Regex("""(?<![\p{L}\d])грн\.?(?!\p{L})""", RegexOption.IGNORE_CASE), "гривень", unitKey("грн")),
        Triple(Regex("""(?<![\p{L}\d])руб\.?(?!\p{L})""", RegexOption.IGNORE_CASE), "рублів", unitKey("руб")),
        Triple(Regex("""(?<![\p{L}\d])коп\.?(?!\p{L})""", RegexOption.IGNORE_CASE), "копійок", unitKey("коп")),
        Triple(Regex("""(?<![\p{L}\d])тис\.(?!\p{L})""", RegexOption.IGNORE_CASE), "тисяч", unitKey("тис")),
        Triple(Regex("""(?<![\p{L}\d])млрд(?!\p{L})""", RegexOption.IGNORE_CASE), "мільярдів", unitKey("млрд")),
        Triple(Regex("""(?<![\p{L}\d])млн(?!\p{L})""", RegexOption.IGNORE_CASE), "мільйонів", unitKey("млн")),
        Triple(Regex("""(?<![\p{L}\d])трлн(?!\p{L})""", RegexOption.IGNORE_CASE), "трильйонів", unitKey("трлн")),
        Triple(Regex("""(?<![\p{L}\d])шт\.(?!\p{L})""", RegexOption.IGNORE_CASE), "штук", unitKey("шт")))

    // ---- общие регулярки ----

    /** Множитель словом: «тисяча», «мільйонів», «трильйони». */
    private const val MULT_WORD = """тисяч[аі]?|мільйон(?:а|и|ів)?|мільярд(?:а|и|ів)?|трильйон(?:а|и|ів)?"""
    /** Единица за развёрнутым «тис.», «млн», «млрд», «трлн» ([units]): «тисячі км.» — точку сокращения съедает, как у числа. */
    private val MULT_UNIT = Regex("""(?<!\p{L})($MULT_WORD)\s+$UNIT""", RegexOption.IGNORE_CASE)
    /** Валюта перед числом: «$65 000», «€ 50» → «65 000 $» для [units]; с множителем (и словом) — за ним: «$75,3 млрд.» →
     * «75,3 млрд. $», «$70 мільярдів» → «70 мільярдів $»; у диапазона без пробелов — в конце: «$17-18 млрд» → «17-18 млрд $»;
     * не «10 $ 20». С пробелами тире — не диапазон: «$5 — 10 штук». */
    private val CURRENCY_BEFORE = Regex("""(?<!\d\s?)([€₽\$£₴₸¥₺₩₪₹])\s*($CUR_NUM(?:[-–—]$CUR_NUM)?""" +
        """(?:\s*(?:тис|млн|млрд|трлн)\.?(?!\p{L})|\s+(?:$MULT_WORD)(?!\p{L}))?)(?![\d.,]?\d)""", RegexOption.IGNORE_CASE)
    private const val CUR_NUM = """\d{1,9}(?:[  ]\d{3})*(?:[.,]\d+)?"""
    /** Число, дробная часть через точку или запятую ([decimal]), единица [UNIT]. */
    private val NUMBER_UNIT = Regex("""(?<![\d.,])(\d+(?:[ \u00A0]\d{3})*)(?:[.,](\d+))?\s*$UNIT""", RegexOption.IGNORE_CASE)
    private val VULGAR_UNIT_RE = Regex("""(?<!\p{L})(половина|чверть|чверті|третина|третини)\s+$UNIT""", RegexOption.IGNORE_CASE)
    /** «2 1/2 кг»: целое, пробел, дробь с однозначным числителем; четвёртая группа — единица с пробелом, пятая — сама единица. */
    private val MIXED_RE = Regex("""(?<![\d/.,])(\d{1,4})\s+(\d)/(\d{1,2})(?![\d/])(\s*$UNIT)?""", RegexOption.IGNORE_CASE)
    /** «3/4», «1/2 кг»: не кусок даты «12/05/2026» и не «1/2/3»; третья группа — единица с пробелом, четвёртая — единица. */
    private val SLASH_RE = Regex("""(?<![\d/.,])(\d+)/(\d+)(?![\d/]|[.,]\d)(\s*$UNIT)?""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")
    private val TIME_SEC = Regex("""(?<![\d:])(\d{1,2}):(\d{2}):(\d{2})(?![\d:])""")
    /** «чч.мм» после предлога ([times]); не кусок даты «о 12.05.2026» и не число с единицей «1.50 грн», «у 1.25 раза», «близько
     * 1.30 мільйона». «по» с числом — чаще цена («по 2.50 за кілограм»), время — только сразу за другим («з 15.30 по 16.30»,
     * «з 9:00 по 18.00»: в [times] идёт раньше [TIME]). Слово-единица — целым словом: «о 18.30 разом», «метро» — время. */
    private val TIME_DOT = Regex("""(?<!\p{L})(о|об|у|в|з|із|зі|до|від|після|близько|біля|перед|(?<=\d[.:]\d\d\s{1,3})по)(\s+)(\d{1,2})\.(\d{2})""" +
        """(?!\d|[.,]\d)(?!\s*$UNIT)(?!\s*(?:раз(?:а|и|ів)?|тонн?[аи]?|штук[аи]?|$MULT_WORD|(?:санти|мілі)?(?:метр|грам|літр)(?:а|и|ів)?|кіло\p{L}+|""" +
        """(?:долар|відсот(?:ок|ка|ки|ків)|градус|гектар)(?:а|и|ів)?|гривн(?:я|і|ень)|копій(?:ка|ки|ок)|рубл(?:я|і|ів)|євро)(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
}
