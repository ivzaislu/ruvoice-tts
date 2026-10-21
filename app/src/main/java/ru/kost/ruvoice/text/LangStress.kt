// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский и украинский текстовые слои для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/** Таблицы языка для [LangStress]: гласные (по ним акцентор отдаёт вероятности), служебные слова без «+» и слово
 * в тексте (буквы языка, «ъ» на месте апострофа, «+»; через дефис — целиком). */
class StressTable(val vowels: String, val noMark: Set<String>, val wordRe: Regex)

/** Белорусский: «і», «ў», «ё» — буквы языка. */
val BE_STRESS = StressTable(
    "аеёіоуыэюя",
    setOf(
        "ў", "у", "з", "с", "на", "да", "па", "за", "ад", "аб", "для", "пры", "між", "пад", "над",
        "без", "праз", "пра", "а", "і", "не", "ні", "бы", "жа", "ці", "то", "як", "што", "хто",
        "дзе", "калі", "так", "ну", "вось", "што", "чым", "каб", "але", "або", "усе", "усё"),
    Regex("[а-яёіўъ+]+(?:-[а-яёіўъ+]+)*", RegexOption.IGNORE_CASE))

/** Украинский: гласные и служебные слова — те же, что у tools/export_pack.py (VOWELS['uk'], NO_MARK['uk']): по ним
 * собран stress.txt пака. В слове и русские «ё», «ы», «э» (а-я их покрывает, кроме «ё»): русская фраза украинским
 * голосом не режется на куски. */
val UK_STRESS = StressTable(
    "аеєиіїоуюя",
    ("у в з із зі зо о об на до по за від од для при між під над без про а і й та не ні б би ж же чи то як що " +
        "хто де коли так ну ось щоб але або аби це").split(' ').toSet(),
    Regex("[а-яёєіїґъ+]+(?:-[а-яёєіїґъ+]+)*", RegexOption.IGNORE_CASE))

/**
 * Ударения второго языка CIS-модели поверх акцентора silero-stress пака (`accentor-bel.pt`, `accentor-ukr`, MIT).
 *
 * Почему свой класс, а не [Stress]: русский конвейер ставит ударения в четыре прохода —
 * таблица AOT, омографы по BERT, словарь «ё» eyo и акцентор. Для белорусского и украинского первых трёх нет
 * (AOT и eyo русские, омографов в акценторах silero-stress не бывает), остаётся акцентор и словарь.
 *
 * Правила поверх акцентора:
 *  - служебные слова и предлоги ([StressTable.noMark]: «ў», «на», «з», «да») не метим: акцентор ставит на них «+»,
 *    и модель тянет слог — лишнее;
 *  - односложные слова не метим: ударение там единственное;
 *  - слова из словаря пользователя не трогаем — словарь главнее акцентора;
 *  - слова, где «+» уже стоит (из замены, словаря или от руки), не трогаем.
 *
 * [userDict] — слитые списки языка, в нём и поправки пака (список «Системный <lang>», свои списки его перебивают).
 * [accentor] — правило «Ударения нейросетью»: выключено — модель не зовём, остаются словари, как у русского.
 * [table] — гласные, служебные слова и слово языка.
 */
class LangStress(private val models: StressModels, private val userDict: Map<String, String> = emptyMap(),
                 private val accentor: Boolean = true, private val table: StressTable = BE_STRESS) {

    private fun dict(w: String) = userDict[w]

    // в companion: LangStress создаётся на каждый запрос
    private companion object {
        fun bare(w: String) = if ('ъ' in w || 'Ъ' in w) w.filter { it != 'ъ' && it != 'Ъ' } else w

        /** «ъ» (нормализатор ставит его на место апострофа) — обратно в слово [marked] с «+», найденное без него. «+» из
         * самого текста ([orig] «пъ+ять») для сверки снимаем: иначе «ъ» после него вставал не на место или терялся. */
        fun withHard(orig0: String, marked: String): String {
            if ('ъ' !in orig0) return marked
            val orig = orig0.replace("+", "")
            val sb = StringBuilder(marked.length + 1)
            var j = 0
            for (c in marked) {
                while (j < orig.length && orig[j].lowercaseChar() == 'ъ') { sb.append(orig[j]); j++ }
                sb.append(c)
                if (c != '+') j++
            }
            return sb.toString()
        }
    }

    /** Ставит «+» перед ударной гласной: `дзякуй` → `дз+якуй`. */
    fun apply(sentence: String): String {
        val vowels = table.vowels
        val wordRe = table.wordRe
        // словарь и акцентор знают слово без апострофа: «абъём» ищем как «абём»
        val words = wordRe.findAll(sentence).map { bare(it.value).lowercase() }.filter { dict(it) == null }
            .flatMap { it.split('-') }
            .filter { w -> '+' !in w && dict(w) == null && w !in table.noMark && w.count { c -> c in vowels } >= 2 }
            .distinct().toList()

        val marked = HashMap<String, String>(words.size * 2)
        if (accentor && words.isNotEmpty()) {
            val probs = models.accentor(words).first
            for ((i, w) in words.withIndex()) {
                val p = probs.getOrNull(i) ?: continue
                val vowelsAt = w.indices.filter { w[it] in vowels }
                if (vowelsAt.isEmpty()) continue
                var best = 0
                for (k in vowelsAt.indices) {
                    val pk = if (k < p.size) p[k] else Float.NEGATIVE_INFINITY
                    val pb = if (best < p.size) p[best] else Float.NEGATIVE_INFINITY
                    if (pk > pb) best = k
                }
                val pos = vowelsAt[best]
                marked[w] = w.substring(0, pos) + "+" + w.substring(pos)
            }
        }
        if (marked.isEmpty() && userDict.isEmpty()) return sentence

        val out = StringBuilder(sentence.length + 8)
        var last = 0
        // слово из одних «ъ» («Ъ», «ъ-ъ») без них пустое: w[0] падал, и запрос молчал целиком (фаззинг 22.10)
        fun cased(w: String, repl: String) = if (w.isNotEmpty() && w[0].isUpperCase() && repl.isNotEmpty()) repl.replaceFirstChar { it.uppercaseChar() } else repl
        fun one(w: String): String {
            val lower = w.lowercase()
            return cased(w, if ('+' in w) w else (dict(lower) ?: marked[lower] ?: w))
        }
        for (m in wordRe.findAll(sentence)) {
            out.appendRange(sentence, last, m.range.first)
            val w = bare(m.value)
            val r = dict(w.lowercase())?.let { cased(w, it) } ?: w.split('-').joinToString("-", transform = ::one)
            // то же слово (с готовым «+» или без) — «ъ» возвращаем; словарь мог дать другое написание — как есть
            out.append(if (r.replace("+", "").equals(w.replace("+", ""), true)) withHard(m.value, r) else r)
            last = m.range.last + 1
        }
        out.appendRange(sentence, last, sentence.length)
        return out.toString()
    }
}
