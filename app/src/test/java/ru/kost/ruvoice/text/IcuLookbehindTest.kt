package ru.kost.ruvoice.text

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * На Android регулярки исполняет ICU: просмотр назад обязан иметь ограниченную длину, иначе Pattern
 * не компилируется и падает весь объект Normalizer. JVM такое прощает, поэтому проверяем сами: все Regex-поля
 * Normalizer (и lazy) — внутри (?<= и (?<! не должно быть *, + и {n,}, а захватывающих групп — тоже: их значение
 * изнутри просмотра назад на ICU ненадёжно. \G тоже нельзя: ICU не привязывает его к start в find(text, start).
 */
class IcuLookbehindTest {
    /** Regex-поля объекта (и lazy): имя и сама регулярка. */
    private fun regexes(o: Any): List<Pair<String, Regex>> = o::class.java.declaredFields.mapNotNull { f ->
        f.isAccessible = true
        val v = try { f.get(o) } catch (_: Exception) { null }
        when (v) {
            is Regex -> f.name to v
            is Lazy<*> -> (v.value as? Regex)?.let { f.name to it }
            else -> null
        }
    }

    /** Украинский слой: поля [UkNormalizer] и регулярки вопросов [UkLayer]. */
    private fun ukRegexes(): List<Pair<String, Regex>> = regexes(UkNormalizer) +
        UkLayer.questions(ru.kost.ruvoice.TestData.data()).let { q -> listOfNotNull("altRe" to q.altRe, "tagRe" to q.tagRe, q.notWhRe?.let { "notWhRe" to it }) }

    private fun patterns(): List<Pair<String, String>> = (regexes(Normalizer) + ukRegexes()).map { (n, r) -> n to r.pattern }

    @Test fun ukrainianCaseInsensitiveWithoutInlineFlag() {
        // встроенный (?i:) без флага на JVM кириллицу не видит, на Android видит: только RegexOption.IGNORE_CASE
        for ((name, r) in ukRegexes()) {
            assertTrue("$name: голый (?i", !Regex("""(?<!\\)\(\?i""").containsMatchIn(r.pattern))
            if (Regex("[а-яА-ЯєіїґЄІЇҐ]").containsMatchIn(r.pattern)) assertTrue("$name без IGNORE_CASE", RegexOption.IGNORE_CASE in r.options)
        }
    }

    /** Содержимое каждой группы просмотра назад. */
    private fun lookbehinds(p: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < p.length) {
            if (p[i] == '\\') { i += 2; continue }
            if (p.startsWith("(?<=", i) || p.startsWith("(?<!", i)) {
                var depth = 0; var j = i; var inClass = false
                while (j < p.length) {
                    val c = p[j]
                    if (c == '\\') { j += 2; continue }
                    if (inClass) { if (c == ']') inClass = false }
                    else if (c == '[') inClass = true
                    else if (c == '(') depth++
                    else if (c == ')') { depth--; if (depth == 0) break }
                    j++
                }
                out += p.substring(i + 4, j)
            }
            i++
        }
        return out
    }

    private fun unbounded(body: String): Boolean {
        var i = 0; var inClass = false
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') { i += 2; continue }
            if (inClass) { if (c == ']') inClass = false }
            else when (c) {
                '[' -> inClass = true
                '*', '+' -> if (i > 0 && body[i - 1] != '(' && body[i - 1] != '?') return true
                '{' -> if (Regex("""\{\d+,}""").matchesAt(body, i)) return true
            }
            i++
        }
        return false
    }

    /** Захватывающая группа внутри просмотра назад: на ICU её значение ненадёжно, берём группы снаружи. */
    private fun captures(body: String): Boolean {
        var i = 0; var inClass = false
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') { i += 2; continue }
            if (inClass) { if (c == ']') inClass = false } else if (c == '[') inClass = true else if (c == '(' && body.getOrNull(i + 1) != '?') return true
            i++
        }
        return false
    }

    @Test fun noCapturesInLookbehinds() {
        val bad = patterns().flatMap { (name, p) -> lookbehinds(p).filter(::captures).map { "$name: (?<…$it)" } }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun noContinuationAnchor() {
        val bad = patterns().filter { (_, p) -> Regex("""(?<!\\)(?:\\\\)*\\G""").containsMatchIn(p) }.map { it.first }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test fun lookbehindsAreBounded() {
        val bad = patterns().flatMap { (name, p) -> lookbehinds(p).filter(::unbounded).map { "$name: (?<…$it)" } }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }
}
