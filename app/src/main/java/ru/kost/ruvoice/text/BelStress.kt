// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Белорусские ударения поверх акцентора silero-stress (`accentor-bel.pt`, MIT).
 *
 * Почему свой класс, а не [Stress]: русский конвейер ставит ударения в четыре прохода —
 * таблица AOT, омографы по BERT, словарь «ё» eyo и акцентор. Для белорусского первых трёх нет
 * (AOT и eyo русские, омографов в белорусском акценторе не бывает), остаётся акцентор и словарь.
 *
 * Правила поверх акцентора:
 *  - служебные слова и предлоги («ў», «на», «з», «да») не метим: акцентор ставит на них «+»,
 *    и модель тянет слог — для белорусского это лишнее;
 *  - односложные слова не метим: ударение там единственное;
 *  - слова из словаря пользователя не трогаем — словарь главнее акцентора;
 *  - слова, где «+» уже стоит (из замены, словаря или от руки), не трогаем.
 *
 * [userDict] — слитые списки языка, в нём и поправки пака (список «Системный <lang>», свои списки его перебивают).
 * [accentor] — правило «Ударения нейросетью»: выключено — модель не зовём, остаются словари, как у русского.
 */
class BelStress(private val models: StressModels, private val userDict: Map<String, String> = emptyMap(),
                private val accentor: Boolean = true) {

    private fun dict(w: String) = userDict[w]

    // в companion: BelStress создаётся на каждый запрос, регулярка и набор — один раз
    private companion object {
        const val vowels = "аеёіоуыэюя"

        /** Служебные слова: ударение им не нужно, а «+» тянет слог. */
        val noMark = setOf(
            "ў", "у", "з", "с", "на", "да", "па", "за", "ад", "аб", "для", "пры", "між", "пад", "над",
            "без", "праз", "пра", "а", "і", "не", "ні", "бы", "жа", "ці", "то", "як", "што", "хто",
            "дзе", "калі", "так", "ну", "вось", "што", "чым", "каб", "але", "або", "усе", "усё")

        /** Слово с «+» (готовое ударение не режем на куски) и через дефис целиком: его ищем в словаре, потом по частям. */
        val wordRe = Regex("[а-яёіў+]+(?:-[а-яёіў+]+)*", RegexOption.IGNORE_CASE)
    }

    /** Ставит «+» перед ударной гласной: `дзякуй` → `дз+якуй`. */
    fun apply(sentence: String): String {
        val words = wordRe.findAll(sentence).map { it.value.lowercase() }.filter { dict(it) == null }
            .flatMap { it.split('-') }
            .filter { w -> '+' !in w && dict(w) == null && w !in noMark && w.count { c -> c in vowels } >= 2 }
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
        fun cased(w: String, repl: String) = if (w[0].isUpperCase() && repl.isNotEmpty()) repl.replaceFirstChar { it.uppercaseChar() } else repl
        fun one(w: String): String {
            val lower = w.lowercase()
            return cased(w, if ('+' in w) w else (dict(lower) ?: marked[lower] ?: w))
        }
        for (m in wordRe.findAll(sentence)) {
            out.appendRange(sentence, last, m.range.first)
            val w = m.value
            out.append(dict(w.lowercase())?.let { cased(w, it) } ?: w.split('-').joinToString("-", transform = ::one))
            last = m.range.last + 1
        }
        out.appendRange(sentence, last, sentence.length)
        return out.toString()
    }
}
