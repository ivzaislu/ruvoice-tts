package ru.kost.ruvoice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Белорусский пак cis_be на телефоне, через сервис как у читалки: zip заранее
 * `adb push dist/ruvoice-pack-cis_be.zip /data/local/tmp/` (без файла класс пропускается). Пак ставится в filesDir
 * приложения один раз на класс; в конце удаляется, если его не было до теста и не передан `-e keepPack true`.
 * Prefs «ruvoice» и «ruvoice_profiles» (голос, профили, правило «Подробный журнал») возвращаются как были.
 */
@RunWith(AndroidJUnit4::class)
class BelarusianPackDeviceTest {
    companion object {
        private const val ID = "cis_be"
        private val BE = Locale("be", "BY")
        private val RU = Locale("ru", "RU")
        private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
        private fun live() = ctx.getSharedPreferences("ruvoice", Context.MODE_PRIVATE)
        private fun store() = ctx.getSharedPreferences("ruvoice_profiles", Context.MODE_PRIVATE)
        private var had = false
        private var liveSaved: Map<String, *>? = null
        private var storeSaved: Map<String, *>? = null

        @JvmStatic @BeforeClass fun install() {
            val zip = File("/data/local/tmp/ruvoice-pack-cis_be.zip")
            assumeTrue("нет $zip", zip.isFile)
            liveSaved = HashMap(live().all); storeSaved = HashMap(store().all)
            had = Packs.find(ctx.filesDir, ID) != null
            zip.inputStream().use { Packs.install(it, ctx.filesDir) }
            // как SettingsActivity.releaseModels: прежняя версия пака не остаётся в памяти сервиса
            SileroModels.shared(ctx).releasePack(ID)
        }

        // @AfterClass идёт и после пропуска в @BeforeClass — тогда снимка нет и трогать нечего
        @JvmStatic @AfterClass fun remove() {
            val l = liveSaved ?: return
            if (!had && InstrumentationRegistry.getArguments().getString("keepPack") != "true") {
                Packs.delete(ctx.filesDir, ID)
                SileroModels.shared(ctx).releasePack(ID)
                File(ctx.cacheDir, DictCache.diskName("be")).delete()
            }
            restorePrefs(live(), l); restorePrefs(store(), storeSaved!!)
        }
    }

    private val packs get() = Packs.installed(ctx.filesDir)

    /** Текущий голос пользователя, если он русский; иначе (lite только с cis_be, выбран белорусский) — пропуск. */
    private fun ruCurrent(): String {
        val cur = Speaker.currentName(Prefs(ctx).voice, SileroModels.speakers(ctx), packs)
        assumeTrue("текущий голос $cur не русский", cur != null && Speaker.lang(cur, packs) == "ru")
        return cur!!
    }

    @Test fun packInstalledWithLanguage() {
        val p = packs.single { it.id == ID }
        assertEquals("be", p.lang)
        assertEquals(3, p.format)
        assertTrue("нет ${Packs.ACCENTOR}", p.accentor)
        assertTrue("${Packs.STRESS} пуст", p.corrections.isNotEmpty())
        assertEquals("bel_larisa", p.defaultVoice)
        assertEquals(setOf("bel_larisa", "bel_anatoliy", "bel_dmitriy"), p.speakers.keys)
    }

    /** Глазами клиента: be и ru заявлены, у голосов локаль своего языка, русские имена прежние;
     * setLanguage(be-BY) — голос пака по умолчанию, setLanguage(ru-RU) — снова русский голос пользователя. */
    @Test fun languagesAndVoices() {
        val ps = packs
        val ru = Speaker.names(SileroModels.speakers(ctx), ps).filter { Speaker.lang(it, ps) == "ru" }
        tts { t ->
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.isLanguageAvailable(BE))
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.isLanguageAvailable(RU))
            val voices = t.voices.associateBy { it.name }
            Log.i("RuVoiceTest", "голоса: ${voices.values.joinToString { "${it.name} ${it.locale}" }}")
            for (n in listOf("larisa-be-cis", "anatoliy-be-cis", "dmitriy-be-cis")) assertEquals(n, BE, voices[n]?.locale)
            for (n in ru) oldTtsName(n).let { assertEquals(it, RU, voices[it]?.locale) }
            if (Speaker.builtin) assertEquals(RU, voices["xenia-ru"]?.locale)
            val cur = ruCurrent()
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.setLanguage(BE))
            assertEquals("larisa-be-cis", t.voice?.name)
            assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, t.setLanguage(RU))
            assertEquals(Speaker.ttsName(cur, ps), t.voice?.name)
        }
    }

    /** Белорусская фраза голосом larisa (в модель — белорусским слоем), потом русская русским голосом:
     * смена пака туда и обратно не ломает синтез. */
    @Test fun synthesizesBelarusianThenRussian() {
        val ruName = Speaker.ttsName(ruCurrent(), packs)
        Prefs(ctx).setRule("verbose_log", true)
        tts { t ->
            val voices = t.voices.associateBy { it.name }
            assertEquals(TextToSpeech.SUCCESS, t.setVoice(voices.getValue("larisa-be-cis")))
            val n = SileroTtsService.journal().size
            wavOk(synth(t, "Добры дзень! Сёння 20.10.2026, тэмпература −5 °C. Колькі каштуюць 2 кг яблыкаў? Дзякуй.", "be"))
            val model = SileroTtsService.journal().drop(n).filter { it.contains("в модель") }.joinToString("\n").replace("+", "")
            Log.i("RuVoiceTest", model)
            assertTrue("не белорусский слой: $model", model.contains("мінус") && model.contains("цэльсія"))
            assertEquals(TextToSpeech.SUCCESS, t.setVoice(voices.getValue(ruName)))
            wavOk(synth(t, "Поздним вечером старый смотритель запер тяжёлые ворота. В 1917 году было 3 события.", "ru"))
        }
    }

    /** CHECK_TTS_DATA: язык пака среди доступных, иначе его не выбрать в системных настройках синтеза. */
    @Test fun checkTtsDataListsBelarusian() {
        ScreenReaderSettle.await()
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA).setClassName(ctx, CheckVoiceDataActivity::class.java.name)
        ActivityScenario.launchActivityForResult<CheckVoiceDataActivity>(intent).use { s ->
            assertEquals(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, s.result.resultCode)
            val avail = s.result.resultData.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES)
            assertTrue("$avail", avail != null && avail.containsAll(listOf("bel-BLR", "rus-RUS")))
        }
    }

    /** Один экземпляр моделей: ensureStress(пак be) — акцентор пака (ударение по гласным, «ё» нет),
     * ensureStress(null) — снова штатный русский, у него «ё» есть. */
    @Test fun accentorFollowsPack() {
        val m = SileroModels(ctx)
        try {
            m.ensureStress(packs.single { it.id == ID })
            val (be, beYo) = m.accentor(listOf("дзякуй", "беларусь"))
            assertEquals(2, be.size); assertEquals(2, beYo.size)
            for (row in be) assertEquals(1f, row.sum(), 1e-3f)
            assertEquals("дз+якуй", 0, argmax(be[0], 2))
            assertEquals("белар+усь", 2, argmax(be[1], 3))
            m.ensureStress(null)
            val (ru, ruYo) = m.accentor(listOf("елка", "привет"))
            assertEquals(2, ru.size); assertEquals(2, ruYo.size)
            assertTrue("«ё» русского акцентора пустые", ruYo.all { it.isNotEmpty() })
        } finally { m.release() }
    }

    // ---- помощники ----

    /** Номер самой вероятной из первых [n] гласных. */
    private fun argmax(p: FloatArray, n: Int) = (0 until n).maxByOrNull { p[it] }!!

    /** ttsName до пака be: «ru» один раз — голос, язык, пак. Имена русских голосов не должны были поменяться. */
    private fun oldTtsName(name: String): String {
        val (pack, speaker) = name.split('/').let { if (it.size == 2) it else listOf("", it[0]) }
        fun parts(s: String) = s.split('_').filter { it.isNotEmpty() && it != "ru" }
        return (parts(speaker) + "ru" + parts(pack)).joinToString("-")
    }

    private fun tts(body: (TextToSpeech) -> Unit) {
        val ready = CountDownLatch(1); var status = -1
        val t = TextToSpeech(ctx, { s -> status = s; ready.countDown() }, "ru.kost.ruvoice")
        try {
            assertTrue("engine bind timeout", ready.await(60, TimeUnit.SECONDS)); assertEquals(TextToSpeech.SUCCESS, status)
            body(t)
        } finally { t.shutdown() }
    }

    private fun synth(t: TextToSpeech, text: String, id: String): File {
        val done = CountDownLatch(1); var err = false
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { done.countDown() }
            @Deprecated("") override fun onError(id: String?) { err = true; done.countDown() }
            override fun onError(id: String?, code: Int) { err = true; done.countDown() }
        })
        val f = File(ctx.cacheDir, "be_$id.wav")
        assertEquals(TextToSpeech.SUCCESS, t.synthesizeToFile(text, Bundle(), f, id))
        assertTrue("timeout $id", done.await(180, TimeUnit.SECONDS)); assertFalse("error $id", err)
        return f
    }

    /** 16-битный моно WAV: длиннее 2 с и не тишина. */
    private fun wavOk(f: File) {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val sr = b.getInt(24); b.position(44)
        var peak = 0; var n = 0
        while (b.remaining() >= 2) { peak = maxOf(peak, kotlin.math.abs(b.short.toInt())); n++ }
        val sec = n.toFloat() / sr
        Log.i("RuVoiceTest", "${f.name}: %.2f с, %d Гц, пик %.3f".format(sec, sr, peak / 32768f))
        assertTrue("${f.name}: $sec с", sec > 2f)
        assertTrue("${f.name}: тишина, пик $peak", peak > 0.05f * 32768)
    }
}
