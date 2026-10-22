// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский и украинский текстовые слои для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/** Общее у чисел белорусского и украинского слоёв: то, где язык — только таблица. Таблицы и порядок шагов у каждого
 * языка свои ([BelNormalizer], [BelNumbers], [UkNumbers]). */
internal object CisNumbers {
    /** Число по цифрам; [units] — имена цифр 0–9. */
    fun digits(raw: String, units: Array<String>) = raw.map { units[it.digitToInt()] }.joinToString(" ")

    /** Группа цифр как написана: нули в начале — словом каждый, остальное — [cardinal], не влезло в Long — по цифрам. */
    fun group(raw: String, units: Array<String>, cardinal: (Long) -> String): String {
        val rest = raw.trimStart('0')
        val tail = rest.takeIf { it.isNotEmpty() }?.let { r -> r.toLongOrNull()?.let(cardinal) ?: digits(r, units) }
        return (List(raw.length - rest.length) { units[0] } + listOfNotNull(tail)).joinToString(" ")
    }

    /** Римское I–XXX латиницей или кириллицей («І», «Х»); форму проверяет регулярка слоя. */
    fun romanValue(r: String): Long {
        var n = 0; var prev = 0
        for (c in r.reversed()) {
            val v = when (c) { 'I', 'І' -> 1; 'V' -> 5; else -> 10 }
            if (v < prev) n -= v else { n += v; prev = v }
        }
        return n.toLong()
    }
}
