// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.SpannableString
import android.text.style.LocaleSpan
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import ru.kost.ruvoice.text.Rules
import ru.kost.ruvoice.text.Splitter
import ru.kost.ruvoice.text.UkLatin
import ru.kost.ruvoice.text.UkLayer
import ru.kost.ruvoice.text.UkNormalizer
import ru.kost.ruvoice.text.UkNumbers
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Украинские паки cis_uk и cis_uk_ext на телефоне, через сервис как у читалки: оба zip заранее
 * `adb push dist/ruvoice-pack-cis_uk.zip dist/ruvoice-pack-cis_uk_ext.zip /data/local/tmp/` (без них класс пропускается).
 * Паки ставятся в filesDir приложения один раз на класс; в конце удаляются те, которых не было до теста, если не передан
 * `-e keepPack true`. Prefs «ruvoice» и «ruvoice_profiles» возвращаются как были.
 *
 * Сверка слоя с JVM ([layerSameAsJvm]) — по файлам `/data/local/tmp/uk_layer_in.txt` и `uk_layer_jvm.txt`, без них
 * пропуск. Их пишет JVM-тест `UkLayerReferenceGen` в `app/build/`; после правки слоя пересобрать и отдать заново:
 * `./gradlew :app:testFullDebugUnitTest --tests '*UkLayerReferenceGen*' --rerun`,
 * `adb push app/build/uk_layer_in.txt app/build/uk_layer_jvm.txt /data/local/tmp/`.
 */
@RunWith(AndroidJUnit4::class)
class UkrainianPackDeviceTest {
    companion object {
        private const val BASE = "cis_uk"
        private const val EXT = "cis_uk_ext"
        private val UK = Locale("uk", "UA")
        private val RU = Locale("ru", "RU")
        private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
        private fun live() = ctx.getSharedPreferences("ruvoice", Context.MODE_PRIVATE)
        private fun store() = ctx.getSharedPreferences("ruvoice_profiles", Context.MODE_PRIVATE)
        private fun zip(id: String) = File("/data/local/tmp/ruvoice-pack-$id.zip")
        private val had = HashMap<String, Boolean>()
        private var liveSaved: Map<String, *>? = null
        private var storeSaved: Map<String, *>? = null

        fun install(id: String) {
            zip(id).inputStream().use { Packs.install(it, ctx.filesDir) }
            // как SettingsActivity.releaseModels: прежняя версия пака не остаётся в памяти сервиса
            SileroModels.shared(ctx).releasePack(id)
        }

        @JvmStatic @BeforeClass fun installBoth() {
            for (id in listOf(BASE, EXT)) assumeTrue("нет ${zip(id)}", zip(id).isFile)
            liveSaved = HashMap(live().all); storeSaved = HashMap(store().all)
            for (id in listOf(BASE, EXT)) { had[id] = Packs.find(ctx.filesDir, id) != null; install(id) }
        }

        // @AfterClass идёт и после пропуска в @BeforeClass — тогда снимка нет и трогать нечего
        @JvmStatic @AfterClass fun remove() {
            val l = liveSaved ?: return
            if (InstrumentationRegistry.getArguments().getString("keepPack") != "true") {
                for (id in listOf(BASE, EXT)) if (had[id] == false) { Packs.delete(ctx.filesDir, id); SileroModels.shared(ctx).releasePack(id) }
                if (Packs.installed(ctx.filesDir).none { it.lang == "uk" }) File(ctx.cacheDir, DictCache.diskName("uk")).delete()
            }
            restorePrefs(live(), l); restorePrefs(store(), storeSaved!!)
        }
    }

    private val packs get() = Packs.installed(ctx.filesDir)
    private fun pack(id: String) = packs.single { it.id == id }

    /** Русский голос как выбранный: текущий, если он русский, иначе первый русский (штатный xenia, если есть) ставится в
     * prefs — @AfterClass вернёт голос пользователя. Пропуск — только если русских голосов нет совсем (lite без пака ru). */
    private fun ruCurrent(): String {
        val builtin = SileroModels.speakers(ctx)
        val cur = Speaker.currentName(Prefs(ctx).voice, builtin, packs)
        if (cur != null && Speaker.lang(cur, packs) == "ru") return cur
        val ru = Speaker.names(builtin, packs).filter { Speaker.lang(it, packs) == "ru" }
        assumeTrue("русских голосов нет", ru.isNotEmpty())
        return (ru.firstOrNull { it == "xenia" } ?: ru.first()).also { Prefs(ctx).voice = it }
    }

    @Test fun bothPacksInstalledWithOneList() {
        val base = pack(BASE); val ext = pack(EXT)
        for (p in listOf(base, ext)) {
            assertEquals("uk", p.lang); assertEquals(3, p.format)
            assertTrue("${p.id}: нет ${Packs.ACCENTOR}", p.accentor)
            assertTrue("${p.id}: нет ${Packs.MORPH}", File(p.dir, Packs.MORPH).isFile)
        }
        assertEquals("ukr_igor", base.defaultVoice); assertEquals(setOf("ukr_igor", "ukr_roman"), base.speakers.keys)
        assertEquals("ukr_mykyta", ext.defaultVoice)
        assertEquals(setOf("ukr_kateryna", "ukr_lada", "ukr_mykyta", "ukr_oleksa", "ukr_tetiana"), ext.speakers.keys)
        // «Системный uk» один на оба пака, из stress.txt первого по id
        val list = Dicts.file(ctx.filesDir, Dicts.Kind.STRESS, Dicts.packName("uk"))
        assertTrue("нет ${list.name}", list.isFile)
        assertEquals(File(base.dir, Packs.STRESS).readText(), list.readText())
        assertTrue("${Packs.STRESS} пуст", list.readLines().any { DictLines.parseStress(it) != null })
        val names = Dicts.dir(ctx.filesDir, Dicts.Kind.STRESS).list().orEmpty().filter { it.startsWith(Dicts.packName("uk")) }
        assertEquals("$names", listOf("${Dicts.packName("uk")}.txt"), names)
    }

    /** Глазами клиента: uk заявлен, у семи голосов локаль uk-UA; setLanguage(uk) — голос пака, setLanguage(ru) — снова русский. */
    @Test fun languageAndSevenVoices() {
        val ps = packs
        tts { t ->
            assertTrue(t.isLanguageAvailable(UK) >= TextToSpeech.LANG_AVAILABLE)
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.isLanguageAvailable(UK))
            val voices = t.voices.associateBy { it.name }
            for (n in VOICES) assertEquals(n, UK, voices[n]?.locale)
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.setLanguage(UK))
            assertTrue("${t.voice?.name}", t.voice?.name in VOICES)
            val cur = ruCurrent()
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.setLanguage(RU))
            assertEquals(Speaker.ttsName(cur, ps), t.voice?.name)
        }
    }

    /** Фраза брифа каждым из семи голосов: непустой wav, в модель — украинский слой («мінус», «цельсія», «гривня»). */
    @Test fun sevenVoicesSynthesize() {
        Prefs(ctx).setRule("verbose_log", true)
        tts { t ->
            val voices = t.voices.associateBy { it.name }
            for (n in VOICES) {
                assertEquals(n, TextToSpeech.SUCCESS, t.setVoice(voices.getValue(n)))
                val j = mark()
                val ms = System.currentTimeMillis()
                wavOk(synth(t, PHRASE, n))
                Log.i("RuVoiceTest", "$n: ${System.currentTimeMillis() - ms} мс")
                val log = since(j)
                // запрос пришёл именно этим голосом, а не голосом по умолчанию пака
                val name = Speaker.fromTtsName(n, Speaker.names(SileroModels.speakers(ctx), packs), packs)
                assertTrue("$n: нет «голос $name,» в журнале:\n${log.joinToString("\n")}", log.any { "голос $name," in it })
                val model = log.filter { it.contains("в модель") }.joinToString("\n").replace("+", "")
                assertTrue("$n не украинский слой: $model", model.contains("мінус") && model.contains("цельсія") && model.contains("гривня"))
            }
        }
    }

    /** Первый звук: отдельным прогоном после `am force-stop` (тогда процесс и модели холодные) — голос пака по умолчанию. */
    @Test fun firstSynthCold() {
        Prefs(ctx).setRule("verbose_log", true)
        tts { t ->
            assertEquals(TextToSpeech.SUCCESS, t.setVoice(t.voices.first { it.name == "igor-uk-cis" }))
            val ms = System.currentTimeMillis()
            wavOk(synth(t, "Добрий день! Як справи?", "cold"))
            Log.i("RuVoiceTest", "первый синтез: ${System.currentTimeMillis() - ms} мс")
            SileroTtsService.journal().takeLast(15).forEach { Log.i("RuVoiceTest", it) }
        }
    }

    /** CHECK_TTS_DATA: «ukr-UKR» среди доступных, иначе язык не выбрать в системных настройках синтеза. */
    @Test fun checkTtsDataListsUkrainian() {
        ScreenReaderSettle.await()
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA).setClassName(ctx, CheckVoiceDataActivity::class.java.name)
        ActivityScenario.launchActivityForResult<CheckVoiceDataActivity>(intent).use { s ->
            assertEquals(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, s.result.resultCode)
            val avail = s.result.resultData.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES)
            assertTrue("$avail", avail != null && avail.containsAll(listOf("ukr-UKR", "rus-RUS")) && avail.count { it == "ukr-UKR" } == 1)
        }
    }

    /** Ударение «мʼясо» с морфологией пака: апостроф → «ъ», словарь и акцентор находят слово без него. */
    @Test fun apostropheStressWithPackMorph() {
        val m = SileroModels(ctx)
        try {
            val p = pack(BASE)
            m.ensureStress(p)
            assertNotNull("morph.bin пака не открыт", UkNumbers.morph)
            val stress = UkLayer.stress(m, Prefs(ctx).userDict("uk"), Rules())!!
            for (w in listOf("мʼясо", "м'ясо", "м’ясо")) {
                val prepared = UkLayer.prepare(w, p.sym.allowed, Rules())
                val out = stress(prepared)
                Log.i("RuVoiceTest", "$w → $prepared → $out")
                assertEquals(w, "мъясо", out.replace("+", ""))
                assertEquals(w, 1, out.count { it == '+' })
            }
            // морфология в деле: род и падеж по существительному
            assertEquals("з пъятьма друзями", UkLayer.prepare("з 5 друзями", p.sym.allowed, Rules()))
        } finally { m.release() }
    }

    /** Как TalkBack на LocaleSpan uk: setLanguage(uk) и кусок с LocaleSpan — украинский голос и слой; потом setLanguage(ru)
     * и русский кусок — русский голос пользователя и русский слой; выбранный голос в настройках не меняется. Запросы из
     * своего процесса: ветку профилей (Speaker.followsProfile, только чужой uid) это не проходит, она — в SpeakerTest. */
    @Test fun localeSpanUkThenRussian() {
        val ruName = ruCurrent()
        val voiceBefore = Prefs(ctx).voice
        Prefs(ctx).setRule("verbose_log", true)
        tts { t ->
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.setLanguage(UK))
            val uk = SpannableString("Температура −5 °C, друже.").apply { setSpan(LocaleSpan(UK), 0, length, 0) }
            var j = mark()
            wavOk(synth(t, uk, "span_uk"), 1f)
            val ukLog = since(j).joinToString("\n")
            assertTrue("не украинский голос: $ukLog", Regex("голос cis_uk(_ext)?/ukr_").containsMatchIn(ukLog))
            assertTrue("не украинский слой: $ukLog", ukLog.replace("+", "").contains("мінус"))
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.setLanguage(RU))
            j = mark()
            wavOk(synth(t, "Температура −5 °C, друг.", "span_ru"), 1f)
            val ruLog = since(j).joinToString("\n")
            assertTrue("не русский голос $ruName: $ruLog", ruLog.contains("голос $ruName,"))
            assertTrue("не русский слой: $ruLog", ruLog.replace("+", "").contains("минус"))
        }
        assertEquals(voiceBefore, Prefs(ctx).voice)
    }

    /** Слой на Android ICU = слой на JVM: регистр кириллицы в регулярках, `\d`, просмотр назад. Все Regex-поля слоя
     * (и lazy) компилируются здесь в ICU; примеры из JVM-тестов идут, как на телефоне (Splitter, потом prepare). */
    @Test fun layerSameAsJvm() {
        for (o in listOf<Any>(UkNormalizer, UkNumbers, UkLatin)) for (f in o::class.java.declaredFields) {
            f.isAccessible = true
            when (val v = runCatching { f.get(o) }.getOrNull()) { is Lazy<*> -> v.value; is Regex -> v.pattern }
        }
        val inF = File("/data/local/tmp/uk_layer_in.txt"); val jvmF = File("/data/local/tmp/uk_layer_jvm.txt")
        assumeTrue("нет $inF или $jvmF", inF.isFile && jvmF.isFile)
        val m = SileroModels(ctx)
        try {
            val p = pack(BASE)
            m.ensureStress(p) // морфология пака, как у JVM-генератора
            // русская морфология (Normalizer.punctuation склеивает перенос по ней) и таблицы эмодзи и знаков — как у сервиса
            SileroModels.data(ctx)
            val ins = inF.readLines().map(::unesc); val want = jvmF.readLines().map(::unesc)
            assertEquals(ins.size, want.size)
            val bad = ArrayList<String>()
            for ((i, t) in ins.withIndex()) {
                val got = runCatching {
                    Splitter.sentences(t, Rules(), layer = UkLayer).joinToString(" | ") { UkLayer.prepare(it, p.sym.allowed, Rules()) }
                }.getOrElse { "!${it.javaClass.simpleName}: ${it.message}" }
                if (got != want[i]) bad += "«$t»\n  jvm: ${want[i]}\n  dev: $got"
            }
            Log.i("RuVoiceTest", "слой: ${ins.size} примеров, расхождений ${bad.size}")
            bad.take(40).forEach { Log.i("RuVoiceTest", it) }
            assertTrue("расхождений ${bad.size} из ${ins.size}:\n" + bad.take(20).joinToString("\n"), bad.isEmpty())
        } finally { m.release() }
    }

    /** Удаление cis_uk_ext: cis_uk ставится и читает, «Системный uk» остаётся. Пак ext возвращается в finally. */
    @Test fun removingExtKeepsBase() {
        try {
            Packs.delete(ctx.filesDir, EXT)
            SileroModels.shared(ctx).releasePack(EXT)
            assertNull(Packs.find(ctx.filesDir, EXT))
            assertTrue(Dicts.file(ctx.filesDir, Dicts.Kind.STRESS, Dicts.packName("uk")).isFile)
            tts { t ->
                assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.isLanguageAvailable(UK))
                val voices = t.voices.associateBy { it.name }
                assertNull(voices["mykyta-uk-cis-ext"])
                assertEquals(TextToSpeech.SUCCESS, t.setVoice(voices.getValue("roman-uk-cis")))
                wavOk(synth(t, PHRASE, "base_only"))
            }
        } finally { install(EXT) }
    }

    // ---- помощники ----

    private val PHRASE = "Добрий день! Сьогодні 21.10.2026, температура −5 °C. Скільки коштує 1,20 UAH?"
    private val VOICES = listOf("igor-uk-cis", "roman-uk-cis", "kateryna-uk-cis-ext", "lada-uk-cis-ext", "mykyta-uk-cis-ext",
        "oleksa-uk-cis-ext", "tetiana-uk-cis-ext")

    /** Журнал сервиса держит 200 строк: новое — по счётчику строк, а не по размеру журнала. */
    private fun mark() = SileroTtsService.journalSeq()
    private fun since(mark: Long) = SileroTtsService.journalSince(mark)

    private fun unesc(s: String) = Regex("""\\([n\\])""").replace(s) { if (it.groupValues[1] == "n") "\n" else "\\" }

    private fun tts(body: (TextToSpeech) -> Unit) {
        val ready = CountDownLatch(1); var status = -1
        val t = TextToSpeech(ctx, { s -> status = s; ready.countDown() }, "ru.kost.ruvoice")
        try {
            assertTrue("engine bind timeout", ready.await(60, TimeUnit.SECONDS)); assertEquals(TextToSpeech.SUCCESS, status)
            body(t)
        } finally { t.shutdown() }
    }

    private fun synth(t: TextToSpeech, text: CharSequence, id: String): File {
        val done = CountDownLatch(1); var err = false
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done.countDown() }
            @Deprecated("") override fun onError(id: String?) { err = true; done.countDown() }
            override fun onError(id: String?, code: Int) { err = true; done.countDown() }
        })
        val f = File(ctx.cacheDir, "uk_$id.wav")
        assertEquals(TextToSpeech.SUCCESS, t.synthesizeToFile(text, Bundle(), f, id))
        assertTrue("timeout $id", done.await(180, TimeUnit.SECONDS)); assertFalse("error $id", err)
        return f
    }

    /** 16-битный моно WAV: длиннее [min] с и не тишина. */
    private fun wavOk(f: File, min: Float = 2f) {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val sr = b.getInt(24); b.position(44)
        var peak = 0; var n = 0
        while (b.remaining() >= 2) { peak = maxOf(peak, kotlin.math.abs(b.short.toInt())); n++ }
        val sec = n.toFloat() / sr
        Log.i("RuVoiceTest", "${f.name}: %.2f с, %d Гц, пик %.3f".format(sec, sr, peak / 32768f))
        f.delete()
        assertTrue("${f.name}: $sec с", sec > min)
        assertTrue("${f.name}: тишина, пик $peak", peak > 0.05f * 32768)
    }
}
