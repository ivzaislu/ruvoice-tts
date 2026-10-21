package ru.kost.ruvoice.text

object Splitter {
    // Не разбивать после одной буквы с точкой — инициалы («Л. Н. Толстой»), сокращения («т. е.», «г.»).
    // «і», «ў» — белорусские инициалы («Акадэмік І. Шамякін»), в русском тексте их нет.
    private val sentenceEnd = Regex("(?<=[.!?…])(?<!\\s[а-яёіўa-z]\\.)(?<!^[а-яёіўa-z]\\.)\\s+", RegexOption.IGNORE_CASE)

    // Многобуквенные сокращения, после которых тоже не разрываем предложение (13).
    // «ок» намеренно не в списке — совпадает с разговорным «Ок.» в диалогах (review t17 п.6).
    private val abbrevStopWords = setOf("оз", "гр", "гг", "вв", "стр", "св", "см", "др", "пр", "рис", "табл", "ср",
        "им", "ул", "корп", "кв", "проф", "акад", "чл", "мл", "ст", "изд", "тыс", "руб", "коп", "напр", "англ",
        "нем", "фр", "лат", "греч", "букв", "прим", "перев", "мин", "макс", "обл",
        // белорусское «на вул. Леніна»: русского слова «вул» нет; «гл. 3», «Гл. рыс. 4»
        "вул", "гл", "рыс")
    // белорусские «г. зн. у панядзелак», «і інш. выданні», «т. зв. рэжым» — только строчными: русские «0,25 Зв.» (зиверт),
    // «вселенной ЗВ.» — конец предложения
    // и сокращения перед номером или назвай ([BelNormalizer]: «п. 2 арт. 15», «на воз. Нарач», «кн. II», «мм рт. ст.»):
    // кусок, разрезанный после них, нормализатор уже не разворачивал — «воз» читалось словом, «ст.» станцыей
    private val abbrevStopWordsLower = setOf("зн", "інш", "зв", "пп", "арт", "мал", "дад", "заўв", "кн", "разд", "пав", "пад",
        "тэл", "пл", "пас", "воз", "мкр", "зав", "бул", "праф", "дац", "вобл", "рт", "хв", "гадз", "шт", "кап", "чал", "экз")
    // Перед сокращением может стоять скобка или кавычка («[прим. ред.]», «(см. ниже)»). «і», «ў» — белорусские («інш»).
    private val abbrevWordAtEndRe = Regex("""(?:^|[\s(\[«"])([а-яёіўa-z]+)\.$""", RegexOption.IGNORE_CASE)

    private fun endsWithAbbrev(piece: String) = abbrevWordAtEndRe.find(piece)?.groupValues?.get(1)
        ?.let { it.lowercase() in abbrevStopWords || it in abbrevStopWordsLower } == true

    // Де-гифенация переносов (task 18 п.1): строчная буква после переноса — слово разбито
    // переносом, дефис и \n убираем («со-\nбака» → «собака»). Заглавная — вероятно составное
    // слово («Санкт-\nПетербург»), убираем только перенос, дефис оставляем.
    // Дефис также оставляем (review round 1 п.3), если это на самом деле не разрыв слова
    // переносом, а обычное дефисное написание, случайно совпавшее со строкой: списки у языка
    // ([TextLayer.keepHyphen]). «і», «ў» — белорусские буквы («Ві-\nцебск»), в русском тексте их нет.
    private val hyphenBreakRe = Regex("""([а-яёіўА-ЯЁІЎ]+)-\n[ \t]*([а-яёіўА-ЯЁІЎ]+)""")
    private fun dehyphenate(text: String, layer: TextLayer) = hyphenBreakRe.replace(text) { m ->
        val (before, after) = m.destructured
        val keepHyphen = after[0].isUpperCase() || layer.keepHyphen(before.lowercase(), after.lowercase())
        if (keepHyphen) "$before-$after" else before + after
    }

    // Одиночный \n перед строчной буквой (task 18 п.2) — мягкий перенос строки внутри
    // предложения (частый случай в PDF/книгах), не новый абзац — меняем на пробел.
    // Два и более \n подряд, а также \n перед заглавной — настоящий абзац, не трогаем.
    // «і», «ў» — белорусские строчные («…\nі пайшоў»), в русском тексте их нет.
    private val softLineBreakRe = Regex("""(?<!\n)\n(?=[ \t]*[а-яёіў])""")

    /** [layer] — язык текста: его дефисные написания правило dehyphen не склеивает. */
    fun paragraphs(text: String, rules: Rules = Rules(), layer: TextLayer = Layers.RU): List<String> {
        // CRLF → LF первым делом (review final-fix п.9): иначе hyphenBreakRe требует «-\n» вплотную
        // и не видит перенос через «-\r\n» — дефис после разрыва строки не убирается.
        var s = text.replace("\r\n", "\n")
        if (rules.on("dehyphen")) s = dehyphenate(s, layer)
        if (rules.on("soft_break")) s = softLineBreakRe.replace(s, " ")
        return s.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** [measure] — длина текста для модели (после нормализации); задан — предел куска меряется ею, а не исходником
     * (у экранного чтеца: ссылка, эмодзи, число раздуваются в 2–3 раза, а длинный кусок держит перебившую фразу). */
    fun sentences(text: String, rules: Rules = Rules(), measure: ((String) -> Int)? = null): List<String> {
        // Реальный пайплайн режет на предложения ДО Normalizer.prepare() (review round 1 п.1):
        // без этого «Всё. . . Дальше.» резалось бы по каждой точке многоточия. punctuation()
        // идемпотентна, повторный вызов внутри prepare() ничего не портит.
        val cleaned = Normalizer.punctuation(text, rules)
        val maxLen = rules.maxLen
        val merged = mutableListOf<String>()
        for (piece in sentenceEnd.split(cleaned.trim())) {
            if (merged.isNotEmpty() && endsWithAbbrev(merged.last())) merged[merged.size - 1] += " $piece"
            else merged += piece
        }
        return merged.filter { it.isNotBlank() }.flatMap { limit(it.trim(), maxLen, measure) }
    }

    private const val inflating = "@#%&/_=+~$€₽"
    /** В тексте есть чему раздуться при нормализации: цифры, латиница, значки, эмодзи. Нет — мерить незачем. */
    fun mayInflate(t: String) = t.any { it in inflating || it.isDigit() || it.code >= 0x2000 || it in 'a'..'z' || it in 'A'..'Z' }

    /** Разрез как [cut], но голова не длиннее [maxLen] по [measure]: раздулась — режем раньше, пропорционально
     * раздуванию, пока левее есть запятая или пробел. Без раздувания разрез тот же, что у [cut]. */
    fun cutMeasured(t: String, maxLen: Int, measure: (String) -> Int): Int {
        var c = cut(t, maxLen)
        while (c + 1 < t.length) {
            val len = measure(head(t, c))
            if (len <= maxLen) break
            val limit = minOf(c - 1, c * maxLen / len)
            val next = cut(t, limit)
            if (limit <= 0 || next >= c || (t[next] != ',' && t[next] != ' ')) break
            c = next
        }
        return c
    }
    fun head(t: String, c: Int) = t.substring(0, c + 1).trim().trimEnd(',')

    /** Индекс разреза строки длиннее maxLen: последняя запятая, иначе пробел, иначе ровно maxLen. Внутри адреса
     * ([Normalizer.urlRe]) не режем: запятая в «?ids=1,2» или адрес длиннее maxLen иначе рвали ссылку, и хвост
     * читался по частям; нет разреза левее — режем сразу после адреса. */
    fun cut(s: String, maxLen: Int): Int {
        if (!Normalizer.hasUrl(s)) return s.lastIndexOf(',', maxLen).takeIf { it > 0 } ?: s.lastIndexOf(' ', maxLen).takeIf { it > 0 } ?: maxLen
        val urls = Normalizer.urlRe.findAll(s).map { it.range }.toList()
        fun last(c: Char): Int? { var i = s.lastIndexOf(c, maxLen); while (i > 0 && urls.any { i in it }) i = s.lastIndexOf(c, i - 1); return i.takeIf { it > 0 } }
        return last(',') ?: last(' ') ?: urls.firstOrNull { maxLen in it }?.last ?: maxLen
    }

    private fun limit(s: String, maxLen: Int, measure: ((String) -> Int)?): List<String> {
        if (s.length <= maxLen && (measure == null || !mayInflate(s) || measure(s) <= maxLen)) return listOf(s)
        val cut = if (measure == null) cut(s, maxLen) else cutMeasured(s, maxLen, measure)
        if (cut + 1 >= s.length) return listOf(s)  // адрес до самого конца или короткий кусок без запятой и пробела — резать негде
        return listOf(head(s, cut)) + limit(s.substring(cut + 1).trim(), maxLen, measure)
    }
}
