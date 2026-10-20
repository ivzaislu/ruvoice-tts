package ru.kost.ruvoice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackTest {
    private val packJson = JSONObject(File(TestData.root(), "app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack")
    private val filesDir = Files.createTempDirectory("ruvoice").toFile()

    private fun zip(json: String?, vararg models: String, extras: Map<String, ByteArray> = emptyMap()): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            if (json != null) { z.putNextEntry(ZipEntry("pack.json")); z.write(json.toByteArray()); z.closeEntry() }
            for (m in models) { z.putNextEntry(ZipEntry(m)); z.write(byteArrayOf(1)); z.closeEntry() }
            for ((n, b) in extras) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return bytes.toByteArray()
    }
    private val full get() = zip(packJson.toString(), "tts_mel.ptl", "backbone.pte", "head.ptl")

    @Test fun parsesPackJson() {
        val p = Pack(packJson.toString(), File("."))
        assertEquals("cis_ru", p.id); assertEquals("MIT", p.license); assertFalse(p.types)
        assertEquals(29, p.speakers.size); assertTrue("ru_alexandr" in p.speakers)
        assertFalse('!' in p.sym.allowed)
    }

    @Test fun rejectsOtherFormat() {
        val e = assertThrows(IllegalArgumentException::class.java) { Pack(packJson.put("format", 1).toString(), File(".")) }
        assertTrue(e.message!!, e.message!!.contains("другой версии"))
    }

    @Test fun langDefaultsToRuAndComesFromJson() {
        // пак без поля lang читается русским — старые паки не ломаются
        assertEquals("ru", Pack(packJson.toString(), File(".")).lang)
        assertEquals("be", Pack(JSONObject(packJson.toString()).put("lang", "be").toString(), File(".")).lang)
        // код языка в нижнем регистре: pack.json пишут люди и скрипты
        assertEquals("be", Pack(JSONObject(packJson.toString()).put("lang", "BE").toString(), File(".")).lang)
    }

    @Test fun accentorIsOptional() {
        val dir = Files.createTempDirectory("packlang").toFile()
        // пак без файлов языка: акцентора нет
        val plain = Pack(packJson.toString(), dir)
        assertFalse(plain.accentor)
        assertEquals(0L, plain.size)
        File(dir, Packs.ACCENTOR).writeText("x")
        File(dir, Packs.STRESS).writeText("дзякуй дзя+куй\n")
        val p = Pack(packJson.toString(), dir)
        assertTrue(p.accentor)
        // место на диске — со всеми файлами пака: акцентор весит мегабайты
        assertEquals(1L + File(dir, Packs.STRESS).length(), p.size)
    }

    @Test fun formatsTwoAndThree() {
        // 3 — паки не на русском: 1.0.1 их отвергает, это приложение принимает оба
        assertEquals("cis_ru", Pack(JSONObject(packJson.toString()).put("format", 3).toString(), File(".")).id)
        assertThrows(IllegalArgumentException::class.java) { Pack(JSONObject(packJson.toString()).put("format", 4).toString(), File(".")) }
    }

    @Test fun defaultVoiceOnlyIfInPack() {
        assertNull(Pack(packJson.toString(), File(".")).defaultVoice)
        assertEquals("ru_marat", Pack(JSONObject(packJson.toString()).put("default", "ru_marat").toString(), File(".")).defaultVoice)
        assertNull(Pack(JSONObject(packJson.toString()).put("default", "nobody").toString(), File(".")).defaultVoice)
    }

    @Test fun rejectsUnknownLanguageAndForeignWithoutAccentor() {
        val models = arrayOf("tts_mel.ptl", "backbone.pte", "head.ptl")
        fun install(lang: String, vararg extra: String) = Packs.install(ByteArrayInputStream(zip(JSONObject(packJson.toString())
            .put("id", "p_$lang").put("format", 3).put("lang", lang).toString(), *models, extras = extra.associateWith { byteArrayOf(1) })), filesDir)
        // языка нет в Layers — читать его нечем
        val unknown = assertThrows(UserError::class.java) { install("uk", Packs.ACCENTOR) }
        assertEquals(R.string.err_pack_lang, unknown.res)
        // не русский без своего акцентора — слова считались бы русским
        assertEquals(R.string.err_pack_accentor, assertThrows(UserError::class.java) { install("be") }.res)
        assertTrue(Packs.installed(filesDir).isEmpty())
        assertTrue((File(filesDir, "packs").listFiles() ?: emptyArray()).none { it.name.startsWith(".tmp") })
        assertEquals("be", install("be", Packs.ACCENTOR).lang)
        // русскому акцентор не нужен
        assertEquals("ru", install("ru").lang)
        Packs.delete(filesDir, "p_be"); Packs.delete(filesDir, "p_ru")
    }

    @Test fun foreignPackNeedsFormatThree() {
        // пак формата 2 не на русском приложения до 1.1 приняли бы и читали русским слоем — не ставим
        val json = JSONObject(packJson.toString()).put("id", "p_be2").put("lang", "be")
        val zip = zip(json.toString(), "tts_mel.ptl", "backbone.pte", "head.ptl", extras = mapOf(Packs.ACCENTOR to byteArrayOf(1)))
        val e = assertThrows(UserError::class.java) { Packs.install(ByteArrayInputStream(zip), filesDir) }
        assertEquals(R.string.err_pack_format, e.res)
        assertTrue(Packs.installed(filesDir).isEmpty())
        assertEquals(2, Pack(json.toString(), File(".")).format)
        // русский пак формата 2 — как раньше
        assertEquals(2, Packs.install(ByteArrayInputStream(full), filesDir).format)
        Packs.delete(filesDir, "cis_ru")
    }

    @Test fun installCopiesLanguageFiles() {
        val extras = mapOf(Packs.ACCENTOR to byteArrayOf(1, 2), Packs.STRESS to "а +а".toByteArray())
        val p = Packs.install(ByteArrayInputStream(zip(packJson.toString(), "tts_mel.ptl", "backbone.pte", "head.ptl", extras = extras)), filesDir)
        assertTrue(File(p.dir, Packs.ACCENTOR).isFile)
        assertEquals("а +а", File(p.dir, Packs.STRESS).readText())
        // русскому паку системный список не заводится
        assertTrue(Dicts.files(filesDir, Dicts.Kind.STRESS).isEmpty())
        Packs.delete(filesDir, p.id)
    }

    @Test fun installAndDeleteSyncPackList() {
        // поставили пак be со stress.txt — появился «Системный be»; удалили — пропал
        val json = JSONObject(packJson.toString()).put("id", "cis_be").put("format", 3).put("lang", "be").toString()
        val extras = mapOf(Packs.ACCENTOR to byteArrayOf(1), Packs.STRESS to "сталы +сталы\n".toByteArray())
        Packs.install(ByteArrayInputStream(zip(json, "tts_mel.ptl", "backbone.pte", "head.ptl", extras = extras)), filesDir)
        val list = Dicts.file(filesDir, Dicts.Kind.STRESS, "Системный be")
        assertEquals("сталы +сталы\n", list.readText())
        Packs.delete(filesDir, "cis_be")
        assertFalse(list.exists())
    }

    @Test fun installsListsDeletes() {
        File(filesDir, "packs/.tmp-old").mkdirs(); File(filesDir, "packs/.tmp-old/x").writeText("x")
        val p = Packs.install(ByteArrayInputStream(full), filesDir)
        assertEquals("cis_ru", p.id)
        assertFalse(File(filesDir, "packs/.tmp-old").exists()) // хвост убитого процесса убран
        assertEquals(listOf("cis_ru"), Packs.installed(filesDir).map { it.id })
        assertTrue(File(filesDir, "packs/cis_ru/head.ptl").isFile)
        assertEquals(3L + File(filesDir, "packs/cis_ru/pack.json").length(), Packs.installed(filesDir).single().size)
        assertNotNull(Packs.find(filesDir, "cis_ru")); assertNull(Packs.find(filesDir, "nope"))
        Packs.delete(filesDir, "cis_ru")
        assertTrue(Packs.installed(filesDir).isEmpty()); assertFalse(File(filesDir, "packs/cis_ru").exists())
    }

    @Test fun rejectsZipWithoutModelFile() {
        val e = assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(zip(packJson.toString(), "tts_mel.ptl", "backbone.pte")), filesDir) }
        assertEquals(Packs.NOT_A_PACK, e.message)
        assertTrue((File(filesDir, "packs").listFiles() ?: emptyArray()).none { it.name.startsWith(".tmp") })
        assertTrue(Packs.installed(filesDir).isEmpty())
    }

    @Test fun deleteRejectsBadId() {
        assertThrows(IllegalArgumentException::class.java) { Packs.delete(filesDir, "..") }
    }

    @Test fun rejectsZipWithoutJsonAndBrokenJson() {
        assertEquals(Packs.NOT_A_PACK, assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(zip(null, "tts_mel.ptl", "backbone.pte", "head.ptl")), filesDir) }.message)
        assertEquals(Packs.NOT_A_PACK, assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(zip("{oops", "tts_mel.ptl", "backbone.pte", "head.ptl")), filesDir) }.message)
    }

    @Test fun rejectsTruncatedZip() {
        val bytes = full
        val truncated = bytes.copyOfRange(0, bytes.size * 6 / 10)
        val e = assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(truncated), filesDir) }
        assertEquals(Packs.NOT_A_PACK, e.message)
        assertTrue((File(filesDir, "packs").listFiles() ?: emptyArray()).none { it.name.startsWith(".tmp") })
    }

    @Test fun reinstallReplaces() {
        Packs.install(ByteArrayInputStream(full), filesDir)
        File(filesDir, "packs/cis_ru/stray.txt").writeText("x")
        Packs.install(ByteArrayInputStream(full), filesDir)
        assertFalse(File(filesDir, "packs/cis_ru/stray.txt").exists())
        assertEquals(1, Packs.installed(filesDir).size)
    }
}
