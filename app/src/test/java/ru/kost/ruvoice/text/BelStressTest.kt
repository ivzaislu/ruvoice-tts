// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.TestData

/** Язык текста ударений: белорусский конвейер включается слоем пака, русский остаётся по умолчанию. */
class BelStressTest {
    private val d = TestData.data()

    /** Акцентор-заглушка: одно слово — вероятности по его гласным (так отдаёт accentor.ptl). */
    private class Fake(private val probs: Array<FloatArray>) : StressModels {
        var asked: List<String> = emptyList()
        override fun accentor(words: List<String>): Pair<Array<FloatArray>, Array<FloatArray>> {
            asked = words
            return Pair(probs, emptyArray())
        }
        override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray): FloatArray = FloatArray(0)
    }

    @Test fun accentorChoosesVowel() {
        // «дзякуй»: гласные «я» и «у» — акцентор выбирает вторую
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), emptyMap(), Rules(), layer = BeLayer)
        assertEquals("дзяк+уй", stress.apply("дзякуй"))
    }

    @Test fun shortAndServiceWordsNotMarked() {
        val m = Fake(arrayOf(floatArrayOf(0.9f, 0.1f)))
        val stress = Stress(d, m, emptyMap(), Rules(), layer = BeLayer)
        // «з» — односложное, «намі» — два слога: акцентор спрашиваем только про второе
        assertEquals("з н+амі", stress.apply("з намі"))
        assertEquals(listOf("намі"), m.asked)
    }

    @Test fun userDictWinsOverAccentor() {
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), mapOf("дзякуй" to "дз+якуй"), Rules(), layer = BeLayer)
        assertEquals("дз+якуй", stress.apply("дзякуй"))
    }

    @Test fun packCorrectionsApplied() {
        // поправка пака (stress.txt) — до акцентора
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), emptyMap(), Rules(), layer = BeLayer,
            corrections = mapOf("дзякуй" to "дзя+куй"))
        assertEquals("дзя+куй", stress.apply("дзякуй"))
    }

    @Test fun userDictWinsOverPackCorrections() {
        val m = Fake(arrayOf(floatArrayOf(0.1f, 0.9f)))
        val stress = Stress(d, m, mapOf("дзякуй" to "дз+якуй"), Rules(),
            layer = BeLayer, corrections = mapOf("дзякуй" to "дзя+куй", "сталы" to "+сталы"))
        assertEquals("дз+якуй +сталы", stress.apply("дзякуй сталы"))
        assertEquals(emptyList<String>(), m.asked)   // слова из словаря и поправок акцентору не идут
    }

    @Test fun readyPlusKeptWhole() {
        // «тэлегр+ам» из Latin.WORDS, «з+амак» из текста: слово не режется по «+» и второй раз не размечается
        val m = Fake(arrayOf(floatArrayOf(0.9f, 0.1f)))
        val stress = Stress(d, m, emptyMap(), Rules(), layer = BeLayer)
        assertEquals("тэлегр+ам і З+амак", stress.apply("тэлегр+ам і З+амак"))
        assertEquals(emptyList<String>(), m.asked)
    }

    @Test fun hyphenWordWholeThenParts() {
        // целиком из поправок пака — акцентор не спрашиваем; иначе по частям, регистр у каждой свой
        val m = Fake(arrayOf(floatArrayOf(0.9f, 0.1f), floatArrayOf(0.9f, 0.1f)))
        val stress = Stress(d, m, emptyMap(), Rules(), layer = BeLayer, corrections = mapOf("па-беларуску" to "па-белар+уску"))
        assertEquals("Па-белар+уску", stress.apply("Па-беларуску"))
        assertEquals(emptyList<String>(), m.asked)
        assertEquals("Ч+орна-б+елы", stress.apply("Чорна-белы"))
        assertEquals(listOf("чорна", "белы"), m.asked)
    }

    @Test fun modelEndKnowsBelarusianVowels() {
        // «дар+огі» — ударение не на последнем слоге: точка остаётся (end_oxy её снимал, «і» не считалась гласной)
        val stress = Stress(d, Fake(emptyArray()), emptyMap(), Rules(), layer = BeLayer)
        assertEquals("тут дар+огі." to false, stress.modelEnd("тут дар+огі."))
        assertEquals("тут дар+огі." to true, stress.modelEnd("тут дар+огі"))
        assertEquals("+ухо востр+о" to false, stress.modelEnd("+ухо востр+о."))   // ударное на конце — как было
        // короткое слово с «ў»/«і»: последняя гласная сдвоена, как у русского «Да.»
        assertEquals("ўсёё.", stress.stretchShort("ўсё."))
        assertEquals("ціі?", stress.stretchShort("ці?"))
    }

    @Test fun packAccentorWithoutYoOutput() {
        // silero-stress bel/ukr: forward → (logits, None) или один тензор — «ё» пустые строки той же длины
        val t = org.pytorch.Tensor.fromBlob(floatArrayOf(0f, 1f, 2f, 0f), longArrayOf(2, 2))
        for (out in listOf(org.pytorch.IValue.tupleFrom(org.pytorch.IValue.from(t), org.pytorch.IValue.optionalNull()), org.pytorch.IValue.from(t))) {
            val (stress, yo) = ru.kost.ruvoice.SileroModels.accentorOut(out)
            assertEquals(2, stress.size)
            assertTrue(stress[0][1] > stress[0][0] && stress[1][0] > stress[1][1])
            assertEquals(listOf(0, 0), yo.map { it.size })
        }
        // русский акцентор — два тензора, как было
        val (_, yo) = ru.kost.ruvoice.SileroModels.accentorOut(org.pytorch.IValue.tupleFrom(org.pytorch.IValue.from(t), org.pytorch.IValue.from(t)))
        assertEquals(listOf(2, 2), yo.map { it.size })
    }

    @Test fun traceForAnalyze() {
        // «Разбор»: белорусский путь пишет вход и результат
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), emptyMap(), Rules(), layer = BeLayer)
        stress.trace = ArrayList()
        stress.apply("дзякуй")
        assertEquals(listOf("input: дзякуй", "be: дзяк+уй"), stress.trace)
    }
}
