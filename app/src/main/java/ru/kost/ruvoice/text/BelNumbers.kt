// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Числа в реальной жизни: даты, время, единицы, валюты.
 *
 * Зачем отдельно от [BelNormalizer]: там числа просто читаются словами, а «12.05.2026», «21:30»
 * и «3 км» так читать нельзя — получается «дванаццаць пяць дзве тысячы…». Здесь они раскрываются
 * по-белорусски: «дванаццатага мая дзве тысячы дваццаць шостага года», «дзевятая гадзіна трыццаць
 * хвілін», «тры кіламетры».
 *
 * Порядок важен: сначала даты и время (у них внутри цифры со знаками), потом единицы, и только
 * потом BelNormalizer читает оставшиеся числа как количественные.
 */
object BelNumbers {

    private val MONTHS = arrayOf(
        "студзеня", "лютага", "сакавіка", "красавіка", "мая", "чэрвеня",
        "ліпеня", "жніўня", "верасня", "кастрычніка", "лістапада", "снежня")

    /** Женский род: гадзіна, хвіліна, тысяча. */
    private val FEM = arrayOf(
        "нуль", "адна", "дзве", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")

    private val TENS = arrayOf(
        "", "", "дваццаць", "трыццаць", "сорак", "пяцьдзясят", "шэсцьдзясят", "семдзесят",
        "восемдзесят", "дзевяноста")

    /** Белорусское склонение после числа: 1 — адзін, 2–4 — два, 5 — пяць. */
    fun plural(n: Long, one: String, few: String, many: String): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..14 -> many
            n10 == 1L -> one
            n10 in 2..4 -> few
            else -> many
        }
    }

    /** Женское количественное: 2 → «дзве», 21 → «дваццаць адна». */
    fun femCardinal(n: Long): String {
        if (n < 20) return FEM[n.toInt()]
        val t = TENS[(n / 10 % 10).toInt()]
        val u = n % 10
        return if (u == 0L) t else "$t ${FEM[u.toInt()]}"
    }

    // ---- даты ----

    /** 12.05.2026 → «дванаццатага мая дзве тысячы дваццаць шостага года»; 3.09 → «трэцяга верасня».
     * Без года — только «дд.мм» через точку: «1.5 кг», «1/2 шклянкі», «24/7» — не даты, их читают числа.
     * Первая группа у всех трёх — «па» перед датой: день тогда вінавальны ніякага роду, «з 1 па 5 мая» → «з першага па
     * пятае мая» (не проверено носителем). Цифровые даты — правило dates, месяц словом и диапазон дней — day_month. */
    fun dates(text: String, rules: Rules = Rules()): String {
        fun date(m: MatchResult, dRaw: String, moRaw: String, yearRaw: String): String {
            val d = dRaw.toInt(); val mo = moRaw.toInt()
            if (d !in 1..31 || mo !in 1..12) return m.value
            val pa = m.groupValues[1]
            val sb = StringBuilder(pa).append(if (pa.isEmpty()) BelNormalizer.ordinal(d.toLong(), BelNormalizer.Form.GEN) else neuter(d.toLong()))
            sb.append(' ').append(MONTHS[mo - 1])
            if (yearRaw.isNotEmpty()) {
                val y0 = yearRaw.toLong()
                // двузначный год: «99» — 1999, «20» — 2020
                val y = if (yearRaw.length <= 2) (if (y0 > 30) 1900 else 2000) + y0 else y0
                sb.append(' ').append(BelNormalizer.ordinal(y, BelNormalizer.Form.GEN)).append(" года")
            }
            return sb.toString()
        }
        var s = text
        if (rules.on("dates")) {
            s = DATE.replace(s) { m -> date(m, m.groupValues[2], m.groupValues[4], m.groupValues[5]) }
            s = DAY_MONTH.replace(s) { m -> date(m, m.groupValues[2], m.groupValues[3], "") }
        }
        if (!rules.on("day_month")) return s
        // первый день диапазона — тем же родительным: «15-16 мая» → «пятнаццатага — шаснаццатага мая», «з 1 па 5 мая»
        s = DAY_RANGE.replace(s) { m ->
            val d = m.groupValues[1].toLong()
            if (d !in 1..31) m.value
            else BelNormalizer.ordinal(d, BelNormalizer.Form.GEN) + m.groupValues[2].ifEmpty { " — " }
        }
        // месяц словом: «3 мая 2026 г.» → «трэцяга мая … шостага года», как у цифровой даты
        return DAY_MONTH_WORD.replace(s) { m ->
            date(m, m.groupValues[2], (MONTHS.indexOf(m.groupValues[3].lowercase()) + 1).toString(), m.groupValues[4])
        }
    }

    // ---- время ----

    /** 21:30 → «дваццаць адна гадзіна трыццаць хвілін» (часы словами, потом минуты; 9:00 → «дзевяць гадзін роўна»).
     * Через точку — только после предлога ([TIME_DOT]): «а 19.00», «у 7.30»; без него «10.05» — дата, «1.5» — дробь. */
    fun times(text: String): String {
        // через точку — раньше: «па» смотрит на цифры времени перед ним («з 9:00 па 18.00»)
        val s = TIME_DOT.replace(text) { m ->
            val p = m.groupValues[1].lowercase()
            // «з/да…» перед «дд.мм» с месяцем 01–12 — скорее дата («з 12.05 па 15.05»); «а», «у», «ў» — всегда время
            if (p != "а" && p != "у" && p != "ў" && m.groupValues[4].toInt() in 1..12) return@replace m.value
            time(m.groupValues[3], m.groupValues[4])?.let { m.groupValues[1] + m.groupValues[2] + it } ?: m.value
        }
        return TIME.replace(s) { m -> time(m.groupValues[1], m.groupValues[2]) ?: m.value }
    }

    private fun time(hRaw: String, miRaw: String): String? {
        val h = hRaw.toIntOrNull() ?: return null
        val mi = miRaw.toIntOrNull() ?: return null
        if (h !in 0..23 || mi !in 0..59) return null
        val hb = StringBuilder(femCardinal(h.toLong()))
        hb.append(' ').append(plural(h.toLong(), "гадзіна", "гадзіны", "гадзін"))
        if (mi > 0) {
            hb.append(' ').append(femCardinal(mi.toLong()))
            hb.append(' ').append(plural(mi.toLong(), "хвіліна", "хвіліны", "хвілін"))
        } else {
            hb.append(' ').append("роўна")
        }
        return hb.toString()
    }

    // ---- единицы и валюты ----

    /** Единица по точному токену: ключ — то, что стоит сразу за числом (без точки). */
    private val UNITS: Map<String, Triple<String, String, String>> = mapOf(
        "км/г" to Triple("кіламетр у гадзіну", "кіламетры ў гадзіну", "кіламетраў у гадзіну"),
        "км" to Triple("кіламетр", "кіламетры", "кіламетраў"),
        "см" to Triple("сантыметр", "сантыметры", "сантыметраў"),
        "мм" to Triple("міліметр", "міліметры", "міліметраў"),
        "кг" to Triple("кілаграм", "кілаграмы", "кілаграмаў"),
        "мл" to Triple("мілілітр", "мілілітры", "мілілітраў"),
        "мг" to Triple("міліграм", "міліграмы", "міліграмаў"),
        "га" to Triple("гектар", "гектары", "гектараў"),
        "м2" to Triple("квадратны метр", "квадратныя метры", "квадратных метраў"),
        "м²" to Triple("квадратны метр", "квадратныя метры", "квадратных метраў"),
        "м" to Triple("метр", "метры", "метраў"),
        "л" to Triple("літр", "літры", "літраў"),
        "г" to Triple("грам", "грамы", "грамаў"),
        "т" to Triple("тона", "тоны", "тон"),
        "шт" to Triple("штука", "штукі", "штук"),
        // «с» кириллицей: BelNormalizer сводит к ней «°C» до латиницы. Строчными: регистр опущен до единиц,
        // а заглавной «Ц» в символах модели нет
        "°с" to Triple("градус цэльсія", "градусы цэльсія", "градусаў цэльсія"),
        "%" to Triple("працэнт", "працэнты", "працэнтаў"),
        "руб" to Triple("рубель", "рублі", "рублёў"),
        "кап" to Triple("капейка", "капейкі", "капеек"),
        "тыс" to Triple("тысяча", "тысячы", "тысяч"),
        "млн" to Triple("мільён", "мільёны", "мільёнаў"),
        "млрд" to Triple("мільярд", "мільярды", "мільярдаў"),
        "трлн" to Triple("трыльён", "трыльёны", "трыльёнаў"),     // не проверено носителем
        "₽" to Triple("рубель", "рублі", "рублёў"),
        "$" to Triple("долар", "долары", "долараў"),
        "€" to Triple("еўра", "еўра", "еўра"),
    )
    /** Единицы женского рода: счётное слово согласуется с ними, а не наоборот. */
    private val FEM_UNITS = setOf("т", "шт", "кап", "тыс")
    /** Единица после десятичной дроби — родительный единственного, как после дробных числительных: «1,5 кг» —
     * «… кілаграма». Формы не проверены носителем. */
    private val UNITS_FRAC = mapOf(
        "км/г" to "кіламетра ў гадзіну", "км" to "кіламетра", "см" to "сантыметра", "мм" to "міліметра", "кг" to "кілаграма",
        "мл" to "мілілітра", "мг" to "міліграма", "га" to "гектара", "м2" to "квадратнага метра", "м²" to "квадратнага метра",
        "м" to "метра", "л" to "літра", "г" to "грама", "т" to "тоны", "шт" to "штукі", "°с" to "градуса цэльсія",
        "%" to "працэнта", "руб" to "рубля", "кап" to "капейкі", "тыс" to "тысячы", "млн" to "мільёна", "млрд" to "мільярда",
        "трлн" to "трыльёна", "₽" to "рубля", "$" to "долара", "€" to "еўра")

    /** «3 км» → «тры кіламетры», «5 %» → «пяць працэнтаў», «2 руб.» → «два рублі», «2 т» → «дзве тоны»;
     * «1,5 кг» → «адзін коска пяць кілаграма» ([decimal]). Валюта перед числом — как после: «$65 000» → «… долараў».
     * Единица после «тыс.», «млн», «млрд» — родны мн.: «1,2 тыс. км» → «… тысячы кіламетраў».
     * Каждая единица — под своим правилом ([unitKey]), перенос валюты вперёд — под currency. */
    fun units(text: String, rules: Rules = Rules()): String = MULT_UNIT.replace(NUMBER_UNIT.replace(if (rules.on("currency")) currencyAfter(text) else text) { m ->
        val raw = m.groupValues[1].replace("\u00A0", "").replace(" ", "")
        val n = raw.toLongOrNull() ?: return@replace m.value
        val rest = m.groupValues[3].lowercase()
        val u = UNITS[rest]?.takeIf { rules.on(unitKey(rest)) } ?: return@replace m.value
        if (m.groupValues[2].isNotEmpty()) return@replace "${decimal(raw, m.groupValues[2])} ${UNITS_FRAC[rest] ?: u.third}"
        // единицы женского рода: «дзве тоны», «дзве капейкі», «дзве штукі», «дзве тысячы», а не «два»
        val num = if (rest in FEM_UNITS) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)
        "$num ${plural(n, u.first, u.second, u.third)}"
    }) { m ->
        val k = m.groupValues[2].lowercase()
        UNITS[k]?.takeIf { rules.on(unitKey(k)) }?.let { "${m.groupValues[1]} ${it.third}" } ?: m.value
    }

    /** Правило «Правил» для единицы (ключ [UNITS]): деньги — currency, «°с» — degrees, «тыс.», «млн» — abbrev, «%» —
     * numbers, прочее — units. Те же ключи, что у русского нормализатора. */
    fun unitKey(u: String) = when (u) {
        "руб", "кап", "₽", "$", "€" -> "currency"
        "°с" -> "degrees"
        "тыс", "млн", "млрд", "трлн" -> "abbrev"
        "%" -> "numbers"
        else -> "units"
    }

    /** «$5» → «5 $» ([CURRENCY_BEFORE]) с пробелом впереди: «US$5» — не «у-эспяць», «$1,$2» — число не за запятой
     * ([NUMBER_UNIT]). После дефиса или тире за цифрой — без пробела: «$5-$10» — не «долараў- дзесяць». */
    private fun currencyAfter(text: String) = CURRENCY_BEFORE.replace(text) { m ->
        val i = m.range.first
        (if (i > 1 && text[i - 1] in "-–—" && text[i - 2].isDigit()) "" else " ") + m.groupValues[2] + " " + m.groupValues[1]
    }

    /** Десятичная дробь: «1,05» → «адзін коска нуль пяць». Нули в начале дробной части — словом каждый, иначе
     * «1,05» звучало бы как «1,5». ponytail: разговорное «коска», а не «адна цэлая пяць сотых»; не проверено носителем. */
    fun decimal(intRaw: String, fracRaw: String): String =
        (intRaw.toLongOrNull()?.let { BelNormalizer.cardinal(it) } ?: BelNormalizer.digits(intRaw)) + " коска " + BelNormalizer.group(fracRaw)

    // ---- порядковые ----

    /** «1-га верасня» → «першага верасня», «5-ы клас» → «пяты клас», «у 5-м класе» → «у пятым класе», «1990-я» →
     * «тысяча дзевяцьсот дзевяностыя». Форма — по концу суффикса: «-га» родны (м./н.), «-ы/-і» (-гі, -ці, -шы) назоўны
     * м., «-м» месны/творны м.; у десятилетия (10–90 и четырёхзначное на 0) «-я» — назоўны мн. («васьмідзясятыя»), «-х» —
     * родны/месны мн. («у 1990-х» → «у тысяча дзевяцьсот дзевяностых»). С «-я» десятилетие без «гады»/«гг.» за ним —
     * только четырёхзначное, кроме 1000: «10-я кніга» не трогаем, «у 2000-я» — «двухтысячныя», 1000 — ж. р. («1000-я
     * старонка» → «тысячная»). «2-х»…«4-х» — количественное: «двух», «трох», «чатырох» (род не знаем, «дзвюх» не
     * бывает). «-й» — русский суффикс: число без него («2-й» → «два»). Прочее («5-я» — «пятая»?, «5-х») не трогаем. Не проверено носителем. */
    fun ordinals(text: String, rules: Rules = Rules()): String = ORD_SUFFIX.replace(text) { m ->
        val raw = m.groupValues[1]
        val n = raw.toLongOrNull() ?: return@replace m.value
        val sfx = m.groupValues[2].lowercase()
        // «2-х»…«4-х» — правило gen_suffix, остальные суффиксы — numbers
        if (!rules.on(if (sfx == "х" && n in 2..4) "gen_suffix" else "numbers")) return@replace m.value
        val decade = n % 10 == 0L && (raw.length == 2 || raw.length == 4)
        val thousand = n == 1000L
        when {
            sfx.endsWith("га") -> BelNormalizer.ordinal(n, BelNormalizer.Form.GEN)
            sfx.endsWith("м") -> BelNormalizer.ordinal(n, BelNormalizer.Form.LOC)
            sfx.endsWith("ы") || sfx.endsWith("і") -> BelNormalizer.ordinal(n)
            sfx == "я" && decade && (raw.length == 4 && !thousand || m.groupValues[3].isNotEmpty()) -> BelNormalizer.ordinal(n) + "я"
            sfx == "я" && thousand -> gender(n, "ая", FEMININE)
            sfx == "х" && decade -> gender(n, "ых", emptyMap())
            sfx == "х" && n in 2..4 -> CARDINAL_GEN[n.toInt() - 2]
            sfx == "й" -> raw
            else -> m.value
        }
    }
    private val CARDINAL_GEN = arrayOf("двух", "трох", "чатырох")

    /** Римское число I–XXX перед «стагоддзе», «век» — порядковым в падеже существительного: «XX стагоддзе» →
     * «дваццатае стагоддзе», «XX стагоддзя» → «дваццатага», «у XX стагоддзі» → «у дваццатым». Кириллические «Х», «І»
     * тоже: так часто набирают. «ст.», «стст.» — тоже стагоддзе, падеж по предлогу, как у года: «у XX ст.» → «у
     * дваццатым стагоддзі», «XIX–XX стст.» → «дзевятнаццатае — дваццатае стагоддзі». После имени с заглавной — порядковое
     * имени: «Пётр I» → «пётр першы», «Кацярына II» → «… другая». Остальную латиницу не трогаем. Средний род, «ст.»,
     * род по имени — не проверено носителем. Век — правило roman, после имени — roman_name. */
    fun roman(text: String, rules: Rules = Rules()): String {
        var s = ROMAN.replace(text) { m ->
            // кириллическое «І» одно — союз: «І веку не хапіла»
            if (m.groupValues[1] == "І" || !rules.on("roman")) return@replace m.value
            val n = romanValue(m.groupValues[1])
            val noun = m.groupValues[2]
            val word = when (noun.lowercase()) {
                "стагоддзе" -> neuter(n)
                "век" -> BelNormalizer.ordinal(n)
                "стагоддзя", "века" -> BelNormalizer.ordinal(n, BelNormalizer.Form.GEN)
                else -> BelNormalizer.ordinal(n, BelNormalizer.Form.LOC)   // стагоддзі, стагоддзем, веку, веце, векам
            }
            "$word $noun"
        }
        val st = s
        s = ROMAN_ST.replace(st) { m ->
            val nums = listOf(m.groupValues[2], m.groupValues[4]).filter { it.isNotEmpty() }
            if (nums == listOf("І") || !rules.on("roman")) return@replace m.value
            // без предлога I–IV — скорее ступень («ордэн … II ст.», «Дыплом I ст.»): не трогаем; с «н. э.» за ним — век
            // («Курган III ст. да н. э.»)
            if (m.groupValues[1].isEmpty() && m.groupValues[7].isEmpty() && nums.all { romanValue(it) <= 4 }) return@replace m.value
            // «ад XV да XVII ст.» — оба родным, «стагоддзя» адно
            val upTo = m.groupValues[3].trim().lowercase() == "да"
            val form = if (upTo) BelNormalizer.Form.GEN else when (m.groupValues[1].trim().lowercase()) {
                "у", "ў" -> BelNormalizer.Form.LOC
                "з", "да", "ад" -> BelNormalizer.Form.GEN
                else -> null
            }
            val many = nums.size > 1 && !upTo || m.groupValues[5].length > 2
            val noun = when (form) {
                BelNormalizer.Form.LOC -> if (many) "стагоддзях" else "стагоддзі"
                BelNormalizer.Form.GEN -> if (many) "стагоддзяў" else "стагоддзя"
                else -> if (many) "стагоддзі" else "стагоддзе"
            }
            // точка сокращения в конце предложения остаётся, как у [BelNormalizer] ABBR_END_RE; перед заглавной после
            // одного предлога в начале фразы («У XX ст. Беларусь…») — середина, как «У 1941 г. Германія»
            val mid = m.groupValues[1].isNotEmpty() && m.groupValues[6].isNotBlank() &&
                st.substring(0, m.range.first).none(Char::isLetter)
            m.groupValues[1] + nums.joinToString(if (upTo) " да " else " — ") { r ->
                romanValue(r).let { if (form == null) neuter(it) else BelNormalizer.ordinal(it, form) }
            } + " " + noun + (if (m.groups[6] != null && !mid) " ." else "")
        }
        // ponytail: род по имени: ж. р. — только [FEM_NAMES] (и латиница на «a»), прочее на -а/-я — родны м. р. («Указ
        // Пятра I» — «першага»), согласная — назоўны м. р. («Elizabeth II» — «другі»), прочее не трогаем. Предлог в начале
        // фразы («Пасля II сусветнай») — не имя. Одна буква — только I/І после кириллического имени: «Дзень Х», «Літара V»,
        // «Then I», «Model X» не трогаем. Кириллическая «І» перед точкой — инициал («Шамякін І. П.»), перед словом с
        // заглавной — союз («Мінск І Гомель»), перед строчной — римское («Пётр І заснаваў»); латинская «I» перед точкой —
        // римское («Пётр I.»)
        return ROMAN_NAME.replace(s) { m ->
            val name = m.groupValues[1]
            val r = m.groupValues[2]
            val next = m.groupValues[3]
            if (name in NOT_NAMES || !rules.on("roman_name")) return@replace m.value
            if (r.length == 1 && (r[0] !in "IІ" || name.last().code < 128 ||
                    r == "І" && (next == "." || next.lastOrNull()?.isUpperCase() == true)))
                return@replace m.value
            val last = name.last().lowercaseChar()
            val word = when {
                name.replace(BelNormalizer.APOSTROPHE, "'") in FEM_NAMES || last == 'a' -> gender(romanValue(r), "ая", FEMININE)
                last == 'а' || last == 'я' -> BelNormalizer.ordinal(romanValue(r), BelNormalizer.Form.GEN)
                last in "еёіоуыэюeiouy" -> return@replace m.value
                else -> BelNormalizer.ordinal(romanValue(r))
            }
            "$name $word"
        }
    }
    /** Женские имена (назоўны) перед римским; «Частка», «Кніга», «Фаза», «Ліга»… — не имена, но так нумеруют. Список и
     * формы не проверены носителем. */
    private val FEM_NAMES = setOf("Кацярына", "Лізавета", "Елізавета", "Ганна", "Марыя", "Ядвіга", "Бона", "Софія", "Соф'я",
        "Ізабела", "Ізабэла", "Жанна", "Вікторыя", "Іаана", "Мар'я",
        "Частка", "Кніга", "Глава", "Фаза", "Серыя", "Група", "Катэгорыя", "Зона", "Ліга")
    /** Предлоги с заглавной в начале фразы — не имя перед римским; однобуквенные «У», «З» [ROMAN_NAME] и так не ловит.
     * «Літара І ў…» — сама буква. */
    private val NOT_NAMES = setOf("Пасля", "Да", "Ад", "Падчас", "На", "За", "Пры", "Пра", "Па", "Каля", "Для", "Без", "Праз",
        "Літара")

    private fun romanValue(r: String): Long {
        var n = 0; var prev = 0
        for (c in r.reversed()) {
            val v = when (c) { 'I', 'І' -> 1; 'V' -> 5; else -> 10 }
            if (v < prev) n -= v else { n += v; prev = v }
        }
        return n.toLong()
    }

    /** Порядковое другого рода или числа из мужского: меняется окончание последнего слова («дваццаць першае»); «другі»,
     * «трэці» на «-і» заменой окончания не выводятся — [special]. */
    private fun gender(n: Long, end: String, special: Map<String, String>): String = BelNormalizer.ordinal(n).let { o ->
        val last = o.substringAfterLast(' ')
        o.dropLast(last.length) + (special[last] ?: (last.removeSuffix("ы") + end))
    }
    private fun neuter(n: Long) = gender(n, "ае", NEUTER)
    private val NEUTER = mapOf("другі" to "другое", "трэці" to "трэцяе")
    private val FEMININE = mapOf("другі" to "другая", "трэці" to "трэцяя")

    // ---- сокращения ----

    /** «1999 г.» → «1999 год», «у 1812 г.» → «у 1812 годзе», «90-я гг.» → «90-я гады», «5 тыс.» уже разобрано единицами.
     * «5 г.», «500 г. мукі» — граммы (units): год — 1000–2199 или три цифры после «у/ў» ([YEAR_G]). */
    fun abbreviations(text: String): String {
        var s = text
        s = YEAR_G.replace(s) { m ->
            val prep = m.groupValues[1]
            val raw = m.groupValues[2]
            val p = prep.trim().lowercase()
            // год — 1000–2199 или три цифры после «у/ў» («у 988 г.»); «з 500 г.», «па 500 г. мукі» — граммы
            if (if (raw.length < 4) p != "у" && p != "ў" else raw.toInt() !in 1000..2199) return@replace m.value
            // падеж по предлогу: у/ў — месны, з/да/ад — родны, па — вінавальны; не проверено носителем
            val word = when (p) { "у", "ў" -> "годзе"; "з", "да", "ад" -> "года"; else -> "год" }
            "$prep$raw $word"
        }
        // диапазон лет: оба года порядковым, падеж по слову, у «гг.» — по предлогу, как у одного года: «у 1941–1945 гг.» →
        // «у тысяча дзевяцьсот сорак першым — … пятым гадах». Не проверено носителем
        s = YEARS_RANGE.replace(s) { m ->
            val (y1, y2) = listOf(m.groupValues[2].toLong(), m.groupValues[3].toLong())
            if (y1 !in 1000..2199 || y2 !in 1000..2199) return@replace m.value
            val noun = m.groupValues[4].lowercase()
            val p = m.groupValues[1].trim().lowercase()
            val form = when {
                noun == "гадах" || noun.startsWith("гг") && (p == "у" || p == "ў") -> BelNormalizer.Form.LOC
                noun.startsWith("гг") && (p == "з" || p == "да" || p == "ад") -> BelNormalizer.Form.GEN
                else -> BelNormalizer.Form.NOM
            }
            val word = if (!noun.startsWith("гг")) noun else when (form) {
                BelNormalizer.Form.LOC -> "гадах"; BelNormalizer.Form.GEN -> "гадоў"; else -> "гады"
            }
            "${m.groupValues[1]}${BelNormalizer.ordinal(y1, form)} — ${BelNormalizer.ordinal(y2, form)} $word"
        }
        s = GG_LOC.replace(s, "$1гадах")
        s = GG_GEN.replace(s, "$1гадоў")
        s = GG.replace(s, "гады")
        return s
    }
    /** «у 1990-х гг.» — [ordinals] уже дали «у … дзевяностых»: месны, «гадах»; не проверено носителем. */
    private val GG_LOC = Regex("""(?<!\p{L})([уў]\s+(?:\p{L}+\s+){0,3}\p{L}+ых\s+)гг\.?""", RegexOption.IGNORE_CASE)
    /** «-х» без «у/ў» ([GG_LOC] уже прошёл) — родны: «пачатак 1990-х гг.» → «… дзевяностых гадоў»; не проверено носителем. */
    private val GG_GEN = Regex("""(?<!\p{L})(\p{L}+ых\s+)гг\.?""", RegexOption.IGNORE_CASE)

    /** Сокращение без числа: «руб.» после «5 тыс.» или «г.» перед названием города. Только отдельным словом:
     * «капуста», «рубель» и уже развёрнутое единицами «два рублі» не трогаем. «г.» — правило abbrev, единицы — правилом
     * единицы ([unitKey]): иначе с выключенной валютой «5 руб.» читалось бы здесь «пяць рублёў». */
    fun standalone(text: String, rules: Rules = Rules()): String =
        STANDALONE.fold(text) { s, (re, rep, key) -> if (rules.on(key)) re.replace(s, rep) else s }

    private val STANDALONE = listOf(
        // за числом «г.» — граммы или год, не горад: «100 г.» с выключенными единицами остаётся как есть
        Triple(Regex("""(?<!\p{L})(?<!\d\s?)г\.(?=\s)"""), "горад ", "abbrev"),
        // «цана ў руб.» — месны мн.; не проверено носителем
        Triple(Regex("""(?<!\p{L})([уў])\s+руб\.?(?!\p{L})"""), "$1 рублях", unitKey("руб")),
        Triple(Regex("""(?<![\p{L}\d])руб\.?(?!\p{L})"""), "рублёў", unitKey("руб")),
        Triple(Regex("""(?<![\p{L}\d])кап\.?(?!\p{L})"""), "капеек", unitKey("кап")),
        Triple(Regex("""(?<![\p{L}\d])тыс\.(?!\p{L})"""), "тысяч", unitKey("тыс")),
        Triple(Regex("""(?<![\p{L}\d])млрд(?!\p{L})"""), "мільярдаў", unitKey("млрд")),
        Triple(Regex("""(?<![\p{L}\d])млн(?!\p{L})"""), "мільёнаў", unitKey("млн")),
        Triple(Regex("""(?<![\p{L}\d])шт\.(?!\p{L})"""), "штук", unitKey("шт")))

    /** С годом: «12.05.2026», «12/05/26»; не после цифры и знака — иначе кусок большого числа. Хвост «г.» съедаем:
     * «года» дата уже говорит, иначе «…года горад». */
    private val DATE = Regex("""(?<![\d.,])$PA(\d{1,2})([./])(\d{1,2})\3(\d{4}|\d{2})(?!\d)(?:\s*г\.)?""")
    /** «па» перед датой — первая группа у [DATE], [DAY_MONTH], [DAY_MONTH_WORD]. */
    private const val PA = """(?:(?<!\p{L})(па\s+))?"""
    /** «3 мая», «3 мая 2026 г.», «3 мая 2026 года»: год съедаем вместе с «г.»/«года» — date() скажет «года» сам. */
    private val DAY_MONTH_WORD = Regex("""(?<![\d.,])$PA(\d{1,2})\s+(${MONTHS.joinToString("|")})(?!\p{L})(?:\s+(\d{4})(?!\d)(?:\s*г\.|\s+года(?!\p{L}))?)?""",
        RegexOption.IGNORE_CASE)
    /** Первый день диапазона перед «д месяц»: «15-16 мая», «з 1 па 5 мая», «ад 5 да 10 мая». */
    private val DAY_RANGE = Regex("""(?<![\d.,])(\d{1,2})(?:\s*[-–—]\s*|(\s+(?:па|да)\s+))(?=\d{1,2}\s+(?:${MONTHS.joinToString("|")})(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    /** Число с суффиксом порядкового ([ordinals]); не кусок «8-029» и не «1.5-га». Третья группа — «гады»/«гг.» за ним. */
    private val ORD_SUFFIX = Regex("""(?<![\d.,-])(\d{1,4})-(га|ы|і|гі|ці|шы|ты|мы|м|ым|ім|я|х|й)(?!\p{L})(?=(\s+(?:гады|гг)(?!\p{L}))?)""",
        RegexOption.IGNORE_CASE)
    /** I–XXX латиницей или кириллицей: непустое. */
    private const val ROMAN_NUM = """(?=[IVXІХ])[XХ]{0,3}(?:[IІ][XХ]|[IІ]V|V?[IІ]{0,3})"""
    /** Римское перед «стагоддзе», «век» ([roman]), не часть слова. */
    private val ROMAN = Regex("""(?<![\p{L}\d])($ROMAN_NUM)\s+([Сс]тагоддз(?:е|я|і|ем)|[Вв](?:ек|ека|еку|еце|екам))(?!\p{L})""")
    /** «у XX ст.», «XIX–XX стст.», «ад XV да XVII ст.»: предлог, одно или два римских (третья группа — тире или «да»),
     * «ст»/«стст»; шестая группа — за точкой конец предложения или заглавная, седьмая — «н. э.» или «да н. э.». */
    private val ROMAN_ST = Regex("""(?:(?<!\p{L})((?:[Уу]|[Ўў]|[Зз]|[Дд]а|[Аа]д)\s+))?(?<![\p{L}\d])($ROMAN_NUM)(?:(\s*[–—-]\s*|\s+[Дд]а\s+)($ROMAN_NUM))?""" +
        """\s+(стст|ст)\.(?=(\s*$|\s+\p{Lu})?)(?=(\s+(?:да\s+)?н\.\s*э\.)?)""")
    /** Римское после слова с заглавной: «Пётр I», «Кацярына II», «Карл XII». Слово — заглавная и строчные (и апостроф:
     * «Соф'я»): «ЯНКА І» не имя. Не перед «ст.» и диапазоном с «ст.» («Дыплом I ст.», «Гэта I–IV ст.» — [ROMAN_ST]) и не
     * перед вторым инициалом («Шамякін I. П.»); третья группа — точка или слово за ним. */
    private val ROMAN_NAME = Regex("""(?<![\p{L}\d])(\p{Lu}\p{Ll}+(?:['’]\p{Ll}+)?)\s+($ROMAN_NUM)""" +
        """(?![\p{L}\d]|(?:\s*[–—-]\s*$ROMAN_NUM)?\s+(?:стст|ст)\.|\.\s*\p{Lu}\.)(?=(\.|\s+\p{L})?)""")
    // длинные варианты раньше коротких: иначе «5 тыс.» разобралось бы как «5 т» (тоны).
    // Справа не буква: иначе «2026 год» — это «2026 г» (граммы) и «од», а «3 тамы» — «3 т» (тоны) и «амы».
    // Точку съедаем только у сокращений: за знаком («5 %.», «$65 000.») она — конец предложения.
    private const val UNIT = """(км/г|тыс|млрд|млн|трлн|руб|кап|км|см|мм|кг|мл|мг|м2|м²|га|шт|°с|%|₽|\$|€|м|л|г|т)(?:(?<![%₽€\$]|°с)\.)?(?![\p{L}])"""
    /** Без года: «3.09» — месяц двумя цифрами, не дробь «1.5», не кусок «1.10.2» и не число с единицей «1.05 кг».
     * «г.» после «дд.мм» — горад, а не граммы: «15.06 г. Гомель». */
    private val DAY_MONTH = Regex("""(?<![\d.,])$PA(\d{1,2})\.(\d{2})(?!\d|[.,]\d)(?!\s*(?!г\.)$UNIT)""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")
    /** «чч.мм» после предлога ([times]); не кусок даты «у 12.05.2026» и не число с единицей «1.50 руб.», «ў 1.25 раза»,
     * «каля 1.30 мільёна». «па» с числом — чаще цена («па 2.50 за кілаграм»), время — только сразу за другим
     * («з 15.30 па 16.30», «з 9:00 па 18.00»: в [times] идёт раньше [TIME]). Слово-единица — целым словом: «а 18.30 разам»,
     * «метро», «грамадскі» — время. */
    private val TIME_DOT = Regex("""(?<!\p{L})(а|у|ў|з|да|ад|каля|пасля|перад|(?<=\d[.:]\d\d\s{1,3})па)(\s+)(\d{1,2})\.(\d{2})""" +
        """(?!\d|[.,]\d)(?!\s*(?!г\.)$UNIT)(?!\s*(?:(?:раз|тон|штук)[аыі]?|$MULT_WORD|(?:санты|мілі)?(?:метр|грам|літр)(?:а|ы|аў)?|кіла\p{L}+|""" +
        """(?:долар|працэнт|градус|гектар)(?:а|ы|аў)?|рубл(?:я|і|ёў)|капе(?:йк[аі]|ек)|еўра)(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    private val YEAR_G = Regex("""(?:(?<!\p{L})((?:у|ў|з|да|па|ад)\s+))?(?<!\d)(\d{3,4})\s*г\.""", RegexOption.IGNORE_CASE)
    /** «у 1941–1945 гг.», «1941-1945 гадах» ([abbreviations]). «гадоў» нет: «1000–2000 гадоў» — количество. */
    private val YEARS_RANGE = Regex("""(?:(?<!\p{L})((?:у|ў|з|да|ад)\s+))?(?<![\d.,])(\d{4})\s*[–—-]\s*(\d{4})\s*(гг\.?|гадах|гады)(?!\p{L})""",
        RegexOption.IGNORE_CASE)
    /** Множитель словом: «тысяча», «мільёнаў», «трыльёны». */
    private const val MULT_WORD = """тысяч[аы]?|мільён(?:а|ы|аў)?|мільярд(?:а|ы|аў)?|трыльён(?:а|ы|аў)?"""
    /** Единица за развёрнутым «тыс.», «млн», «млрд», «трлн» ([units]): «тысячы км.» — точку сокращения съедает, как у числа. */
    private val MULT_UNIT = Regex("""(?<!\p{L})($MULT_WORD)\s+$UNIT""", RegexOption.IGNORE_CASE)
    /** Валюта перед числом: «$65 000», «€ 50» → «65 000 $» для [units]; с множителем (и словом) — за ним: «$75,3 млрд.» →
     * «75,3 млрд. $», «$70 мільярдаў» → «70 мільярдаў $»; у диапазона без пробелов — в конце: «$17-18 млрд» → «17-18 млрд $»;
     * не «10 $ 20». С пробелами тире — не диапазон: «$5 — 10 штук». */
    private val CURRENCY_BEFORE = Regex("""(?<!\d\s?)([€₽\$])\s*($CUR_NUM(?:[-–—]$CUR_NUM)?""" +
        """(?:\s*(?:тыс|млн|млрд|трлн)\.?(?!\p{L})|\s+(?:$MULT_WORD)(?!\p{L}))?)(?![\d.,]?\d)""")
    private const val CUR_NUM = """\d{1,9}(?:[  ]\d{3})*(?:[.,]\d+)?"""
    private val GG = Regex("""(?<!\p{L})гг\.?""", RegexOption.IGNORE_CASE)
    /** Число, дробная часть через точку или запятую ([decimal]), единица [UNIT]. */
    private val NUMBER_UNIT = Regex(
        """(?<![\d.,])(\d{1,9}(?:[ \u00A0]\d{3})*)(?:[.,](\d+))?\s*$UNIT""",
        RegexOption.IGNORE_CASE)
}
