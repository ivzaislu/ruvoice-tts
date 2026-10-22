package ru.kost.ruvoice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpeakerTest {
    init { Speaker.builtin = true }   // тесты считают сборку full, lite включается через lite {}; в testLite… BuildConfig даёт false
    private val d = TestData.data()
    private val pack = Pack(JSONObject(File(TestData.root(), "app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack").toString(), File("."))
    // пак ru = пять штатных голосов паком: та же таблица символов, что в silero_ru.json (во встроенной full ещё и новые голоса)
    private val ruJson = JSONObject(File(TestData.root(), "app/src/main/assets/silero/silero_ru.json").readText()).let { j ->
        JSONObject().put("format", 2).put("id", "ru").put("title", "Штатные голоса").put("license", "CC BY-NC-SA 4.0").put("source", "x")
            .put("symbols", j.getString("symbols")).put("symbol_to_id", j.getJSONObject("symbol_to_id")).put("sos", j.getString("sos")).put("eos", j.getString("eos"))
            .put("alphabet", j.getString("alphabet")).put("types", true)
            .put("speakers", JSONObject(listOf("aidar", "baya", "kseniya", "eugene", "xenia").associateWith { j.getJSONObject("speakers").getInt(it) }))
    }
    private val ruPack = Pack(ruJson.toString(), File("."))
    /// тот же пак, но белорусский: язык пака решает, какой у голоса слой и как он называется в TTS API
    private val belPack = Pack(JSONObject(File(TestData.root(), "app/src/test/resources/golden_pack.json").readText())
        .getJSONObject("pack").put("id", "cis_be").put("lang", "be").toString(), File("."))
    /** cis_be с настоящими именами родных голосов и парой чужих; [default] — поле default пака. */
    private fun cisBe(default: String? = null) = Pack(JSONObject(File(TestData.root(), "app/src/test/resources/golden_pack.json").readText())
        .getJSONObject("pack").put("id", "cis_be").put("lang", "be").put("format", 3).put("default", default ?: "")
        .put("speakers", JSONObject(listOf("aze_gamat", "bel_anatoliy", "bel_dmitriy", "bel_larisa", "ukr_igor").withIndex().associate { it.value to it.index }))
        .toString(), File("."))

    /** Украинские паки с настоящими id, именами голосов и полем default (dist/ruvoice-pack-cis_uk*.zip, 22.10.2026). */
    private fun uk(id: String, default: String, vararg speakers: String) = Pack(JSONObject(File(TestData.root(), "app/src/test/resources/golden_pack.json").readText())
        .getJSONObject("pack").put("id", id).put("lang", "uk").put("format", 3).put("default", default)
        .put("speakers", JSONObject(speakers.withIndex().associate { it.value to it.index })).toString(), File("."))
    private val cisUk = uk("cis_uk", "ukr_igor", "ukr_igor", "ukr_roman")
    private val cisUkExt = uk("cis_uk_ext", "ukr_mykyta", "ukr_kateryna", "ukr_lada", "ukr_mykyta", "ukr_oleksa", "ukr_tetiana")

    @Test fun ukrainianTwoPacks() {
        val packs = listOf(pack, cisBe("bel_larisa"), cisUk, cisUkExt)
        val names = Speaker.names(d, packs)
        val tts = names.map { Speaker.ttsName(it, packs) }
        // все имена TTS — штатные, cis_ru, cis_be и семь украинских — разные и читаются обратно
        assertEquals(tts.groupBy { it }.filterValues { it.size > 1 }.keys.toString(), names.size, tts.toSet().size)
        for ((n, t) in names.zip(tts)) assertEquals(n, Speaker.fromTtsName(t, names, packs))
        assertEquals(listOf("igor-uk-cis", "roman-uk-cis", "kateryna-uk-cis-ext", "lada-uk-cis-ext", "mykyta-uk-cis-ext",
            "oleksa-uk-cis-ext", "tetiana-uk-cis-ext"), names.filter { Speaker.lang(it, packs) == "uk" }.map { Speaker.ttsName(it, packs) })
        // украинский голос в белорусском паке своё «ukr» сохраняет и с украинскими не совпадает
        assertEquals("ukr-igor-be-cis", Speaker.ttsName("cis_be/ukr_igor", packs))
        // голос для «uk»: при одном паке — его default, при обоих — default первого пака (installed() идёт по id)
        fun forUk(ps: List<Pack>) = Speaker.defaultFor("uk", "kseniya", "ru", Speaker.names(d, ps), ps)
        assertEquals("cis_uk/ukr_igor", forUk(listOf(pack, cisUk)))
        assertEquals("cis_uk_ext/ukr_mykyta", forUk(listOf(pack, cisUkExt)))
        assertEquals("cis_uk/ukr_igor", forUk(packs))
        // выбран голос ext — украинский запрос читает он, а не default другого пака
        assertEquals("cis_uk_ext/ukr_lada", Speaker.defaultFor("uk", "cis_uk_ext/ukr_lada", "ru", names, packs))
        // голоса двух паков одного языка — один профиль, русский — другой
        assertTrue(Speaker.followsProfile("cis_uk_ext/ukr_lada", "cis_uk/ukr_igor", packs))
        assertFalse(Speaker.followsProfile("cis_uk/ukr_igor", "xenia", packs))
        assertFalse(Speaker.followsProfile("cis_uk/ukr_igor", "cis_be/bel_larisa", packs))
        // вкладки «Правил»: русский, белорусский, украинский — язык один раз, хоть паков два
        assertEquals(listOf("ru", "be", "uk"), Speaker.langs(d.speakers.keys, packs))
        lite {
            // lite с cis_ru и cis_uk без пака ru, голос не выбран: русский текст — русским голосом
            assertEquals("cis_ru/ru_aigul", Speaker.currentName("", d.speakers.keys, listOf(cisUk, pack)))
            assertEquals("cis_ru/ru_aigul", Speaker.defaultName(d.speakers.keys, listOf(cisUkExt, cisUk, pack)))
            // только украинские паки — default первого
            assertEquals("cis_uk/ukr_igor", Speaker.default(d, listOf(cisUk, cisUkExt))!!.name)
            assertEquals("cis_uk_ext/ukr_mykyta", Speaker.default(d, listOf(cisUkExt))!!.name)
            // удалили cis_uk — выбранный его голос заменяется голосом оставшегося пака
            assertEquals("cis_uk_ext/ukr_mykyta", Speaker.currentName("cis_uk/ukr_roman", d.speakers.keys, listOf(cisUkExt)))
        }
    }

    @Test fun ttsNamesCarryPackLanguage() {
        assertEquals("marat-ru-cis", Speaker.ttsName("cis_ru/ru_marat", listOf(pack)))
        // из имени убирается только свой язык: русский голос в белорусском паке свой «ru» сохраняет
        assertEquals("ru-alexandr-be-cis", Speaker.ttsName("cis_be/ru_alexandr", listOf(belPack)))
        // пака в списке нет — язык русский, как до появления второго языка
        assertEquals("marat-ru-cis", Speaker.ttsName("cis_ru/ru_marat", emptyList()))
        assertEquals("xenia-ru", Speaker.ttsName("xenia", listOf(belPack)))
    }

    @Test fun ttsNamesUniqueAndReversible() {
        // штатные, ru_voices (имена из пака 10.10.2026), cis_ru и cis_be: имена TTS не пересекаются и читаются обратно
        val voices = JSONObject(ruJson.toString()).put("id", "ru_voices").put("speakers", JSONObject(listOf("aidar", "anyanov", "baya",
            "boris", "chulsky", "dmitri", "eugene", "grinberg", "ilya", "ivan", "kazbek", "kseniya", "oleg", "olga", "pavel", "ponomarenko",
            "ruslan", "solokha", "tovarisch", "wp_gk_tramrunner", "wp_muratov", "xenia").withIndex().associate { it.value to it.index }))
        val packs = listOf(pack, Pack(voices.toString(), File(".")), cisBe())
        val names = Speaker.names(d, packs)
        val tts = names.map { Speaker.ttsName(it, packs) }
        assertEquals(tts.groupBy { it }.filterValues { it.size > 1 }.keys.toString(), names.size, tts.toSet().size)
        for ((n, t) in names.zip(tts)) assertEquals(n, Speaker.fromTtsName(t, names, packs))
        assertEquals(listOf("larisa-be-cis", "anatoliy-be-cis", "dmitriy-be-cis"),
            listOf("bel_larisa", "bel_anatoliy", "bel_dmitriy").map { Speaker.ttsName("cis_be/$it", packs) })
        // русские имена — как до 1.1, байт в байт
        assertEquals("xenia-ru", Speaker.ttsName("xenia", packs)); assertEquals("marat-ru-cis", Speaker.ttsName("cis_ru/ru_marat", packs))
        assertEquals("wp-gk-tramrunner-ru-voices",Speaker.ttsName("ru_voices/wp_gk_tramrunner", packs))
    }

    @Test fun defaultVoiceForLanguage() {
        val be = cisBe()
        val packs = listOf(pack, be)
        val names = Speaker.names(d, packs)
        // язык выбранного голоса — он сам, а не первый русский по алфавиту (aidar)
        assertEquals("kseniya", Speaker.defaultFor("ru", "kseniya", "ru", names, packs))
        assertEquals("cis_ru/ru_marat", Speaker.defaultFor("ru", "cis_ru/ru_marat", "en", names, packs))
        // язык системы — тоже выбранный: белорусский голос при русской системе читает и русский запрос
        assertEquals("cis_be/bel_larisa", Speaker.defaultFor("ru", "cis_be/bel_larisa", "ru", names, packs))
        // другой язык: голос с родным префиксом, а не первый по алфавиту (aze_gamat)
        assertEquals("cis_be/bel_anatoliy", Speaker.defaultFor("be", "kseniya", "ru", names, packs))
        // поле default пака главнее префикса
        val withDefault = listOf(pack, cisBe("bel_larisa"))
        assertEquals("cis_be/bel_larisa", Speaker.defaultFor("be", "kseniya", "ru", Speaker.names(d, withDefault), withDefault))
        // русский по запросу при белорусской системе и голосе — штатный по умолчанию, не aidar
        assertEquals("xenia", Speaker.defaultFor("ru", "cis_be/bel_larisa", "be", names, packs))
        lite { assertEquals("ru/xenia", Speaker.defaultFor("ru", "cis_be/bel_larisa", "be", Speaker.names(d, listOf(ruPack, be)), listOf(ruPack, be))) }
        // голосов языка нет — выбранный
        assertEquals("kseniya", Speaker.defaultFor("be", "kseniya", "ru", Speaker.names(d, listOf(pack)), listOf(pack)))
        // язык системы неизвестен (пустой systemLocales) — правило «язык системы» молчит, язык голоса — работает
        assertEquals("kseniya", Speaker.defaultFor("ru", "kseniya", null, names, packs))
        assertEquals("xenia", Speaker.defaultFor("ru", "cis_be/bel_larisa", null, names, packs))
        assertEquals("cis_be/bel_anatoliy", Speaker.defaultFor("be", "kseniya", null, names, packs))
    }

    @Test fun profileFollowsOnlySameLanguage() {
        val packs = listOf(pack, cisBe())
        // TalkBack на LocaleSpan be попросил белорусский голос — профиль не переключаем, иначе русский текст дальше им
        assertFalse(Speaker.followsProfile("cis_be/bel_larisa", "xenia", packs))
        assertFalse(Speaker.followsProfile("xenia", "cis_be/bel_larisa", packs))
        // читалка сменила голос того же языка — профиль, как раньше
        assertTrue(Speaker.followsProfile("cis_ru/ru_marat", "xenia", packs))
        assertTrue(Speaker.followsProfile("cis_be/bel_anatoliy", "cis_be/bel_larisa", packs))
        assertTrue(Speaker.followsProfile("xenia", null, packs))
    }

    @Test fun defaultTakesPackDefaultBeforeAlphabet() {
        // lite только с cis_be: голос по умолчанию пака, а не первый по алфавиту (aze_gamat)
        lite {
            assertEquals("cis_be/bel_larisa", Speaker.default(d, listOf(cisBe("bel_larisa")))!!.name)
            assertEquals("cis_be/aze_gamat", Speaker.default(d, listOf(cisBe()))!!.name)
            assertEquals("ru/xenia", Speaker.default(d, listOf(ruPack, cisBe("bel_larisa")))!!.name)
            assertEquals("cis_be/bel_larisa", Speaker.defaultName(d.speakers.keys, listOf(cisBe("bel_larisa"))))
            assertEquals("xenia", Speaker.defaultName(d.speakers.keys, listOf(ruPack)))
            // пака ru нет, но есть русский cis_ru: русский голос, а не default белорусского пака (порядок паков не важен)
            assertEquals("cis_ru/ru_aigul", Speaker.default(d, listOf(cisBe("bel_larisa"), pack))!!.name)
            assertEquals("cis_ru/ru_aigul", Speaker.defaultName(d.speakers.keys, listOf(pack, cisBe("bel_larisa"))))
            // голос сервиса и язык словарей настроек: пустой или голос удалённого пака — голос по умолчанию
            assertEquals("cis_be/bel_larisa", Speaker.currentName("", d.speakers.keys, listOf(cisBe("bel_larisa"))))
            assertEquals("cis_be/bel_larisa", Speaker.currentName("xenia", d.speakers.keys, listOf(cisBe("bel_larisa"))))
            assertEquals("cis_ru/ru_aigul", Speaker.currentName("cis_be/bel_larisa", d.speakers.keys, listOf(pack)))
            assertEquals("cis_be/bel_anatoliy", Speaker.currentName("cis_be/bel_anatoliy", d.speakers.keys, listOf(pack, cisBe("bel_larisa"))))
        }
        // full и русские паки без default — как было
        assertEquals("xenia", Speaker.default(d, listOf(pack, cisBe("bel_larisa")))!!.name)
        assertEquals(Speaker.DEFAULT, Speaker.defaultName(d.speakers.keys, listOf(cisBe("bel_larisa"))))
    }

    @Test fun voiceLanguageComesFromPack() {
        assertEquals("ru", Speaker.lang("cis_ru/ru_marat", listOf(pack)))
        assertEquals("be", Speaker.lang("cis_be/ru_alexandr", listOf(belPack)))
        assertEquals("ru", Speaker.lang("xenia", listOf(belPack)))       // встроенный голос — русский
        assertEquals("ru", Speaker.lang("cis_ru/ru_marat", emptyList())) // пак удалён — умолчание
        assertEquals("be", Speaker.resolve("cis_be/ru_alexandr", d, listOf(belPack))!!.lang)
        assertEquals("ru", Speaker.resolve("cis_ru/ru_marat", d, listOf(pack))!!.lang)
        assertEquals("cis_be/ru_alexandr", Speaker.fromTtsName("ru-alexandr-be-cis", Speaker.names(d, listOf(belPack)), listOf(belPack)))
    }

    private fun lite(block: () -> Unit) { Speaker.builtin = false; try { block() } finally { Speaker.builtin = true } }

    @Test fun resolvesBuiltinAndPackVoices() {
        val x = Speaker.resolve("xenia", d, listOf(pack))!!
        assertNull(x.pack); assertTrue(x.types); assertSame(d.sym, x.sym); assertEquals(d.speakers.getValue("xenia"), x.id)
        val a = Speaker.resolve("cis_ru/ru_alexandr", d, listOf(pack))!!
        assertSame(pack, a.pack); assertFalse(a.types); assertSame(pack.sym, a.sym); assertEquals(pack.speakers.getValue("ru_alexandr"), a.id)
    }

    @Test fun baseFromPackBases() {
        val j = JSONObject(ruJson.toString()).put("id", "ru_voices")
        j.getJSONObject("speakers").put("kazbek", 5).put("boris", 6)
        val v = Pack(j.put("bases", JSONObject().put("kazbek", "aidar").put("boris", "eugene")).toString(), File("."))
        assertEquals("aidar", Speaker.resolve("ru_voices/kazbek", d, listOf(v))!!.base)
        assertEquals("eugene", Speaker.resolve("ru_voices/boris", d, listOf(v))!!.base)
        assertEquals("aidar", Speaker.resolve("ru_voices/aidar", d, listOf(v))!!.base)   // штатный в паке — сам себе основа
        assertEquals("aidar", Speaker.resolve("aidar", d, listOf(v))!!.base)
        assertEquals("xenia", Speaker.resolve("ilya", d, emptyList())!!.base)   // встроенная full: bases из silero_ru.json
        assertEquals("aidar", Speaker.resolve("kazbek", d, emptyList())!!.base)
        assertEquals("ru_alexandr", Speaker.resolve("cis_ru/ru_alexandr", d, listOf(pack))!!.base)   // пак без bases
    }

    @Test fun unknownIsNullAndDefaultIsXenia() {
        assertNull(Speaker.resolve("cis_ru/ru_alexandr", d, emptyList()))   // пак удалён
        assertNull(Speaker.resolve("cis_ru/nobody", d, listOf(pack)))
        assertNull(Speaker.resolve(null, d, listOf(pack)))
        assertEquals("xenia", Speaker.default(d, listOf(pack))!!.name)
    }

    @Test fun namesAndSameEngine() {
        val names = Speaker.names(d, listOf(pack))
        val n = d.speakers.size
        assertEquals(d.speakers.keys.sorted(), names.take(n))
        assertEquals(n + 29, names.size); assertEquals("cis_ru/ru_aigul", names[n])
        assertEquals(d.speakers.keys.sorted(), Speaker.sameEngine(Speaker.default(d, listOf(pack))!!, d, listOf(pack)))
        assertEquals(names.drop(n), Speaker.sameEngine(Speaker.resolve("cis_ru/ru_vika", d, listOf(pack))!!, d, listOf(pack)))
    }

    @Test fun labels() {
        assertEquals("xenia", Speaker.label("xenia"))
        assertEquals("ru_alexandr (cis_ru)", Speaker.label("cis_ru/ru_alexandr"))
    }

    @Test fun ttsNamesHaveNoSlashesAndRuOnce() {
        val packs = listOf(pack)
        assertEquals("xenia-ru", Speaker.ttsName("xenia", packs))
        assertEquals("xenia-ru", Speaker.ttsName("ru/xenia", listOf(ruPack)))   // lite: то же имя, что в full
        assertEquals("marat-ru-cis", Speaker.ttsName("cis_ru/ru_marat", packs))
        assertEquals("cis_ru/ru_marat", Speaker.fromTtsName("marat-ru-cis", d, packs))
        assertEquals("cis_ru/ru_marat", Speaker.fromTtsName("ru-ru-cis_ru/ru_marat", d, packs))   // сохранённое читалкой до 0.15
        assertEquals("xenia", Speaker.fromTtsName("xenia-ru", d, packs))
        assertEquals("xenia", Speaker.fromTtsName("ru-ru-xenia", d, packs))
        assertNull(Speaker.fromTtsName(null, d, packs))
        assertNull(Speaker.resolve(Speaker.fromTtsName("nobody-ru", d, packs), d, packs))
        lite { assertEquals("ru/xenia", Speaker.fromTtsName("xenia-ru", d, listOf(ruPack))) }
    }

    @Test fun liteWithoutPacksHasNoVoices() = lite {
        assertTrue(Speaker.names(d, emptyList()).isEmpty())
        assertNull(Speaker.default(d, emptyList()))
        assertNull(Speaker.resolve("xenia", d, emptyList()))
        assertEquals(listOf("cis_ru/ru_aigul"), Speaker.names(d, listOf(pack)).take(1))
        assertEquals("cis_ru/ru_aigul", Speaker.default(d, listOf(pack))!!.name)
    }

    @Test fun liteBareNameResolvesIntoRuPack() = lite {
        val x = Speaker.resolve("xenia", d, listOf(ruPack))!!
        assertEquals("ru/xenia", x.name); assertSame(ruPack, x.pack); assertTrue(x.types); assertEquals(ruPack.speakers.getValue("xenia"), x.id)
        assertEquals("ru/xenia", Speaker.default(d, listOf(pack, ruPack))!!.name)   // xenia важнее первого пака
        assertEquals(listOf("ru/aidar", "ru/baya", "ru/eugene", "ru/kseniya", "ru/xenia"), Speaker.sameEngine(x, d, listOf(pack, ruPack)))
        assertNull(Speaker.resolve("nobody", d, listOf(ruPack)))
    }

    @Test fun hasVoices() {
        assertTrue(Speaker.hasVoices(emptyList()))
        lite {
            assertFalse(Speaker.hasVoices(emptyList()))
            assertTrue(Speaker.hasVoices(listOf(pack)))
        }
    }

    @Test fun fullKeepsBuiltinEvenWithRuPack() {
        val x = Speaker.resolve("xenia", d, listOf(ruPack))!!
        assertNull(x.pack); assertEquals("xenia", x.name)
        assertEquals(d.speakers.size + 5, Speaker.names(d, listOf(ruPack)).size)
    }

    @Test fun existsMatchesResolve() {
        // exists — для вопросов системы о голосах без разбора silero_ru.json
        val names = listOf("xenia", "nobody", "ru/xenia", "cis_ru/ru_alexandr", "cis_ru/nobody", "zz/xenia", "", null)
        for (packs in listOf(emptyList(), listOf(pack), listOf(ruPack), listOf(pack, ruPack))) {
            for (n in names) assertEquals("$n $packs", Speaker.resolve(n, d, packs) != null, Speaker.exists(n, d.speakers.keys, packs))
            lite { for (n in names) assertEquals("lite $n", Speaker.resolve(n, d, packs) != null, Speaker.exists(n, d.speakers.keys, packs)) }
        }
    }
}
