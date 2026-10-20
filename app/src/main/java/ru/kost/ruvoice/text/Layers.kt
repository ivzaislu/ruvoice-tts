// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import ru.kost.ruvoice.SileroData
import java.util.Locale

/** Слова, по которым [SentenceType] узнаёт вопрос: у каждого языка свои. У штатной русской модели они лежат
 * в данных (stress.bin: wh_forms, leading_fillers, tag_patterns), у второй языка — здесь, в его слое. */
interface QuestionLists {
    val whForms: Set<String>
    val leadingFillers: Set<String>
    val tagRe: Regex
    /** Союз альтернативного вопроса («или», белорусские «ці», «або»); ищется в тексте в нижнем регистре. */
    val altRe: Regex
    /** Куски фразы (нижний регистр), где вопросительное слово не ищется: белорусские «калі ласка» и всё после начального
     * «ці». null — таких нет (русский). */
    val notWhRe: Regex? get() = null
}

/**
 * Язык текста: выбирается по полю `lang` пака, которым читается фраза, — а не по сборке приложения.
 * У каждого языка своя подготовка текста (числа, сокращения, латиница) и свои ударения: за ударения
 * отвечает [Stress], которому слой передаётся вместе со словарём (в нём и системный список пака).
 *
 * Новый язык CIS-модели добавляется ещё одной строкой в [Layers]: пока в таблице только `ru` и `be`.
 */
interface TextLayer {
    /** Код языка, как в `pack.json`: `ru`, `be`. */
    val code: String

    /** Язык и страна голосов в TTS API: ISO-3 из неё система спрашивает язык и выбирает голос. */
    val locale: Locale

    /** Ключи [Rules], которые слой не читает: в «Правилах» на вкладке этого языка их нет. Пусто — читает все. */
    val unsupported: Set<String> get() = emptySet()

    /** Слово словаря (ключ и значение) так, как его увидит слой после [prepare]: белорусский снимает апостроф. */
    fun dictWord(w: String): String = w

    /** Текст запроса к модели: числа, сокращения, латиница, знаки — и фильтр по символам модели [allowed]. */
    fun prepare(text: String, allowed: String, rules: Rules): String

    /** Списки для классификации вопроса (см. [QuestionLists]). */
    fun questions(d: SileroData): QuestionLists

    /** Ударения языка: словари (с системным пака) поверх акцентора пака; акцентор — по правилу accentor из [rules].
     * null — русский конвейер [Stress] (таблицы, омографы, «ё»). */
    fun stress(models: StressModels, userDict: Map<String, String>, rules: Rules): ((String) -> String)? = null

    /** Имя буквы, когда запрос — одна буква (правило letter_name, эхо ввода [LetterEcho]): [lone] — в запросе
     * ничего, кроме буквы, [end] — имя последнее во фразе. null — буквы в таблице языка нет. */
    fun letterName(c: Char, lone: Boolean = false, end: Boolean = true): String?

    /** Имя одиночного знака (клавиша «#», знак под курсором); [punct] — эхо удаления [LetterEcho]: там называем
     * и обычную пунктуацию. null — не называем, знак идёт как есть. */
    fun symbolName(c: Char, punct: Boolean = false): String?
}

/** Русский слой — ровно то, что было до появления второго языка: [Normalizer] и данные stress.bin. */
object RuLayer : TextLayer {
    override val code = "ru"
    override val locale = Locale("ru", "RU")
    override fun prepare(text: String, allowed: String, rules: Rules): String = Normalizer.prepare(text, allowed, rules)
    override fun questions(d: SileroData): QuestionLists = d
    override fun letterName(c: Char, lone: Boolean, end: Boolean) = if (lone) Abbrev.loneLetterName(c, end) else Abbrev.letterName(c)
    override fun symbolName(c: Char, punct: Boolean) = (if (punct) LetterEcho.PUNCT_NAMES[c] else null) ?: SymbolNames.of(c)
}

/** Белорусский слой: [BelNormalizer] (числа словами, «!» → «.», апостроф, фильтр по символам пака). */
object BeLayer : TextLayer {
    override val code = "be"
    override val locale = Locale("be", "BY")
    override fun prepare(text: String, allowed: String, rules: Rules): String = BelNormalizer.prepare(text, allowed, rules)
    override fun questions(d: SileroData): QuestionLists = BE_QUESTIONS
    override fun dictWord(w: String): String = w.replace(BelNormalizer.APOSTROPHE, "")
    override fun stress(models: StressModels, userDict: Map<String, String>, rules: Rules) =
        BelStress(models, userDict, rules.on("accentor"))::apply
    /** Белорусские имена букв, латиница — как в аббревиатурах [Latin]; «+» перед первой гласной, как у русских.
     * Второе слово («нескладовае») ударение получает от акцентора. */
    override fun letterName(c: Char, lone: Boolean, end: Boolean) = (BE_LETTERS[c.lowercaseChar()] ?: Latin.letterName(c))?.let { n ->
        val i = n.indexOfFirst { it in "аеёіоуыэюя" }
        if (i < 0) n else n.substring(0, i) + "+" + n.substring(i)
    }
    /** Только пунктуация и только в эхе ([punct]), как у русского: одиночный знак без эха — пауза. Прочие (стрелки,
     * валюты) идут как есть — таблицы SymbolNames и CLDR русские. */
    override fun symbolName(c: Char, punct: Boolean) = if (punct) BE_SYMBOLS[c] else null
}

// формы не проверены носителем; апостроф в «двукроп'е» BelNormalizer снимает, слово остаётся целым: «двукропе»
private val BE_SYMBOLS = mapOf(
    ',' to "коска", '.' to "кропка", '!' to "клічнік", '?' to "пытальнік", ':' to "двукроп'е", ';' to "кропка з коскай",
    '—' to "працяжнік", '–' to "кароткі працяжнік", '-' to "злучок", '…' to "шматкроп'е", '"' to "двукоссе",
    '«' to "адкрываецца двукоссе", '»' to "закрываецца двукоссе", '(' to "адкрываецца дужка", ')' to "закрываецца дужка")

private val BE_LETTERS = mapOf(
    'а' to "а", 'б' to "бэ", 'в' to "вэ", 'г' to "гэ", 'д' to "дэ", 'е' to "е", 'ё' to "ё", 'ж' to "жэ", 'з' to "зэ",
    'і' to "і", 'й' to "і нескладовае", 'к' to "ка", 'л' to "эль", 'м' to "эм", 'н' to "эн", 'о' to "о", 'п' to "пэ",
    'р' to "эр", 'с' to "эс", 'т' to "тэ", 'у' to "у", 'ў' to "у нескладовае", 'ф' to "эф", 'х' to "ха", 'ц' to "цэ",
    'ч' to "чэ", 'ш' to "ша", 'ы' to "ы", 'ь' to "мяккі знак", 'э' to "э", 'ю' to "ю", 'я' to "я")

/** Списки белорусского слоя: те же ключи, что у русского в silero_be.json, — перенесены сюда, потому что
 * вторая языка нет в stress.bin, и данные модели на него не распространяются. */
private object BE_QUESTIONS : QuestionLists {
    // SentenceType сравнивает слова после своей нормализации (ё → е): списки — в том же виде, иначе «чыё» не находилось
    private fun norm(s: Set<String>) = s.map { it.replace('ё', 'е') }.toSet()
    override val whForms = norm(setOf(
        "хто", "што", "дзе", "калі", "чаму", "навошта", "як", "які", "якая", "якое", "якія", "чый", "чыя",
        "чыё", "чыі", "колькі", "куды", "адкуль", "каторы", "чым", "каго", "чаго"))
    override val leadingFillers = norm(setOf(
        "ну", "вось", "а", "дык", "мо", "мабыць", "нават", "проста", "значыць", "так", "але", "ці"))
    // хвост — в конце фразы, как у русских tag_patterns, но только после запятой: «Ты так жа думаеш?», «Гэта праўда?»,
    // «Гэта не праўда?» — общие вопросы
    override val tagRe = Regex(",\\s*(?:праўда|не\\s+праўда|ня\\s+праўд[аы]|ці\\s+не\\s+так|так\\s+жа)\\s*\\?$", RegexOption.IGNORE_CASE)
    // вводные перед частицей «ці» в начале: «А ці ведаеш?», «Ну, ці прыйдзеш?»; «Так ці не?», «Проста ці складана?» —
    // альтернатива
    private val starters = (leadingFillers - "ці" - "так" - "проста").joinToString("|")
    // «або» — альтернатива; «ці» — только между словами («чай ці каву?»): в начале (и после вводного), после тире и запятой
    // это частица общего вопроса («— Ці прыйдзеш?», «А ці ведаеш?», «Скажы, ці прыйдзеш?»). Границы — lookaround: \b в
    // JDK 19+ кириллицу не видит; просмотр назад ограниченной длины и без захвата — ICU на Android
    override val altRe = Regex("(?<![а-яёіў])або(?![а-яёіў])|" +
        "(?<=[а-яёіў]\\s)(?<!^[^а-яёіў]{0,3}(?:(?:$starters)[^а-яёіў]{1,3})?(?:$starters)\\s)ці(?![а-яёіў])")
    // «калі ласка» — не вопрос «калі?»; фраза с «ці» в начале — общий вопрос, «што», «дзе» дальше — придаточное:
    // «Ці ведаеш ты, што заўтра свята?». Повтор вводных — притяжательный «*+»: без него JVM на «ну »×200 падала
    // StackOverflowError (повтор группы — рекурсия), ICU его знает
    override val notWhRe = Regex("(?<![а-яёіў])калі\\s+ласка(?![а-яёіў])|^[^а-яёіў]*(?:(?:$starters)[^а-яёіў]+)*+ці(?![а-яёіў]).*")
}

/** Таблица языков: язык берётся по коду пака, остальной код о языках не знает. */
object Layers {
    private val all = listOf(RuLayer, BeLayer)
    private val table: Map<String, TextLayer> = all.associateBy { it.code }

    /** Русский — умолчание: так читаются паки без поля `lang` и встроенная модель. */
    val RU: TextLayer get() = RuLayer

    fun of(code: String?): TextLayer = table[code?.lowercase()] ?: RuLayer

    /** Есть ли слой для кода пака: пак на языке, которого приложение не знает, не ставится. */
    fun has(code: String) = code in table

    /** Слой по языку из TTS API (ISO-3: «rus», «bel»); null — такого языка нет. */
    fun ofIso3(lang: String?): TextLayer? = all.firstOrNull { it.locale.isO3Language == lang }
}
