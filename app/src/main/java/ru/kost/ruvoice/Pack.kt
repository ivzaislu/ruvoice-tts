package ru.kost.ruvoice

import android.util.Log
import org.json.JSONObject
import ru.kost.ruvoice.text.Layers
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Пак голосов: `filesDir/packs/<id>/{pack.json, tts_mel.ptl, backbone.pte, head.ptl}` — та же нарезка,
 * что у штатной модели в assets. Формат — спека 2026-09-18; сверху (по желанию) два файла языка:
 * `accentor.ptl` — акцентор этого языка и `stress.txt` — поправки ударений (слово → слово с «+»; только языкам
 * не ru: копия в списке «Системный <lang>», см. Dicts.syncPacks). */
class Pack(json: String, val dir: File) {
    val id: String
    val title: String
    val license: String
    val source: String
    val sym: Symbols
    /** Имя голоса в модели (ru_alexandr) → speaker id. */
    val speakers: Map<String, Int>
    /** Новый голос → штатный, от которого у него темп и интонация (pack.py voicekit); у штатных записи нет. */
    val bases: Map<String, String>
    /** forward с type_ids и focus_mask (13 аргументов, v5_5_ru); false — 11, как у v5_ru и cis. */
    val types: Boolean
    /** Язык текста пака: `ru` (умолчание — у старых паков поля нет), `be` и другие коды CIS-модели. */
    val lang: String
    /** Поле `format` (Packs.FORMATS): пак не на русском ставится только с 3. */
    val format: Int
    /** Голос пака по умолчанию (поле `default`): его система получает, когда просит язык пака. null — поля нет. */
    val defaultVoice: String?
    /** Акцентор лежит в самом паке: тогда язык читается своим акцентором, а не штатным русским. */
    val accentor: Boolean get() = File(dir, Packs.ACCENTOR).isFile
    /** Место на диске: все файлы пака, с акцентором и поправками. */
    val size: Long get() = dir.listFiles()?.sumOf { it.length() } ?: 0L

    init {
        val o = JSONObject(json)
        format = o.optInt("format")
        if (format !in Packs.FORMATS) o.optString("format").let { throw UserError("Пак собран для другой версии приложения (формат $it)", R.string.err_pack_format, it) }
        id = o.getString("id")
        require(Packs.ID_RE.matches(id)) { "Недопустимый id пака: $id" }
        title = o.optString("title", id); license = o.optString("license", ""); source = o.optString("source", "")
        sym = Symbols.fromJson(o)
        speakers = o.getJSONObject("speakers").let { j -> j.keys().asSequence().associateWith { j.getInt(it) } }
        if (speakers.isEmpty()) throw UserError("В паке нет голосов", R.string.err_pack_no_voices)
        types = o.optBoolean("types", false)
        lang = o.optString("lang", "ru").lowercase()
        defaultVoice = o.optString("default").takeIf { it in speakers }
        bases = o.optJSONObject("bases")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } } ?: emptyMap()
    }
}

object Packs {
    /** 3 — пак не на русском (поле lang): приложения до него такой пак не примут и не станут читать его по-русски. */
    val FORMATS = 2..3
    val MODEL_FILES = listOf("tts_mel.ptl", "backbone.pte", "head.ptl")
    /** Файлы языка в паке: нужны не всем, поэтому необязательные. */
    const val ACCENTOR = "accentor.ptl"
    const val STRESS = "stress.txt"
    /** Необязательные файлы пака: копируем, если пришли; обязательными не считаем. */
    private val EXTRA_FILES = listOf(ACCENTOR, STRESS)
    val ID_RE = Regex("[a-z0-9_]{1,40}")
    const val NOT_A_PACK = "Это не пак RuVoice"
    private fun notAPack(cause: Throwable? = null) = UserError(NOT_A_PACK, R.string.err_not_pack, cause = cause)
    private const val TAG = SileroModels.TAG

    fun dir(filesDir: File) = File(filesDir, "packs")

    // Снимок на процесс: сервис и настройки в одном процессе; сигнатура — каталоги и mtime их pack.json.
    private class Snap(val sig: List<String>, val packs: List<Pack>)
    @Volatile private var snap: Snap? = null

    private fun dirs(filesDir: File) = (dir(filesDir).listFiles() ?: emptyArray()).filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name }
    private fun sig(filesDir: File) = dirs(filesDir).map { "${it.name}|${File(it, "pack.json").lastModified()}" }

    @Synchronized fun installed(filesDir: File): List<Pack> {
        val s = sig(filesDir)
        snap?.takeIf { it.sig == s }?.let { return it.packs }
        val packs = dirs(filesDir).mapNotNull { d ->
            val json = File(d, "pack.json")
            if (!json.isFile || MODEL_FILES.any { !File(d, it).isFile }) return@mapNotNull null
            try { Pack(json.readText(), d).takeIf { it.id == d.name } } catch (e: Exception) { Log.w(TAG, "пак ${d.name} не разобран", e); null }
        }
        return packs.also { snap = Snap(s, it) }
    }

    fun find(filesDir: File, id: String): Pack? = installed(filesDir).firstOrNull { it.id == id }

    /**
     * Распаковывает zip во временный каталог `packs/.tmp-<n>`, проверяет, переносит в `packs/<id>`.
     * Временный каталог убирается при любом отказе. Битый или обрезанный архив и ошибки формата —
     * IllegalArgumentException с текстом для Snackbar, ошибка записи на диск (ENOSPC) — IOException
     * со своим текстом.
     */
    fun install(zip: InputStream, filesDir: File): Pack {
        val packs = dir(filesDir).also { it.mkdirs() }
        // хвосты .tmp-* от убитого посреди распаковки процесса
        packs.listFiles()?.filter { it.name.startsWith(".tmp-") }?.forEach { it.deleteRecursively() }
        val tmp = File(packs, ".tmp-${System.nanoTime()}").also { it.mkdirs() }
        try {
            var json: String? = null
            try {
                ZipInputStream(zip).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        when (e.name) {
                            "pack.json" -> json = z.readBytes().decodeToString()
                            in MODEL_FILES, in EXTRA_FILES -> File(tmp, e.name).outputStream().use { z.copyTo(it) }
                        }
                        z.closeEntry()
                    }
                }
            } catch (e: ZipException) {
                throw notAPack(e)
            } catch (e: EOFException) {
                throw notAPack(e)
            }
            val text = json ?: throw notAPack()
            if (MODEL_FILES.any { !File(tmp, it).isFile }) throw notAPack()
            val pack = try { Pack(text, tmp) } catch (e: IllegalArgumentException) { throw e } catch (e: Exception) { throw notAPack() }
            // язык без своего слоя читался бы русским нормализатором, а без своего акцентора — русским акцентором
            if (!Layers.has(pack.lang)) throw UserError("Язык пака «${pack.lang}» приложение не знает", R.string.err_pack_lang, pack.lang)
            // формат 2 приложения до 1.1 принимают и читали бы такой пак русским слоем: не на русском — только 3
            if (pack.lang != "ru" && pack.format < 3) throw UserError("Пак собран для другой версии приложения (формат ${pack.format})", R.string.err_pack_format, pack.format.toString())
            if (pack.lang != "ru" && !pack.accentor) throw UserError("В паке на языке «${pack.lang}» нет акцентора $ACCENTOR", R.string.err_pack_accentor, pack.lang)
            File(tmp, "pack.json").writeText(text)
            val dest = File(packs, pack.id)
            if (dest.exists()) dest.deleteRecursively()
            if (!tmp.renameTo(dest)) throw UserError("Не удалось переместить пак в ${dest.name}", R.string.err_pack_move)
            synchronized(this) { snap = null }
            Dicts.syncPacks(filesDir, installed(filesDir))
            return Pack(text, dest)
        } catch (e: Exception) {
            tmp.deleteRecursively()
            throw e
        }
    }

    fun delete(filesDir: File, id: String) {
        require(ID_RE.matches(id)) { "Недопустимый id пака: $id" }
        File(dir(filesDir), id).deleteRecursively()
        synchronized(this) { snap = null }
        Dicts.syncPacks(filesDir, installed(filesDir))
    }
}
