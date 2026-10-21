package ru.kost.ruvoice

import android.content.Context
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.LitePyTorchAndroid
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.executorch.EValue
import ru.kost.ruvoice.bin.StressBin
import ru.kost.ruvoice.text.BelNumbers
import ru.kost.ruvoice.text.Emoji
import ru.kost.ruvoice.text.Morph
import ru.kost.ruvoice.text.Normalizer
import ru.kost.ruvoice.text.HardE
import ru.kost.ruvoice.text.YoDict
import ru.kost.ruvoice.text.StressModels
import ru.kost.ruvoice.text.SymbolNames
import java.io.File
import kotlin.math.exp

class SileroModels(private val context: Context) : StressModels {
    val data: SileroData get() = data(context)
    // tts_mel (текст → мел) и head (голова вокодера, iSTFT) — TorchScript Lite; backbone (ConvNeXt вокодера,
    // 77 % времени forward) — ExecuTorch/XNNPACK, в 1,4 раза быстрее lite на A32 без потери точности.
    // Тройка либо штатная из assets, либо из папки пака (Pack).
    private var mel: Module? = null
    private var head: Module? = null
    private var backbone: org.pytorch.executorch.Module? = null
    private var acc: Module? = null
    private var homo: Module? = null
    /** Чей акцентор загружен: null — штатный русский из assets, иначе папка пака. */
    private var accLoadedDir: String? = null
    /** Папка тройки mel/backbone/head: null — штатная из assets, иначе каталог пака. */
    private var packDir: File? = null
    /** Пак, чей morph.bin открыт в [BelNumbers.morph]. */
    private var morphDir: String? = null
    /** id загруженного пака, null — штатная модель (или ничего не загружено). */
    @Volatile var loadedPack: String? = null; private set
    val isLoaded get() = mel != null && head != null && backbone != null && acc != null

    /** Тройка синтеза — штатная (pack == null) или из папки пака — плюс акцентор и BERT ([ensureStress]). В памяти
     * одна тройка: другой пак или переход на штатную выгружает прежнюю, BERT живёт до release().
     * Акцентор ставим по паку: у пака со своим `accentor.ptl` — его, иначе штатный русский. */
    @Synchronized fun ensureLoaded(pack: Pack? = null) {
        val t = System.currentTimeMillis()
        try {
            if (mel != null && loadedPack != pack?.id) releaseTts()
            if (mel == null) {
                packDir = pack?.dir
                mel = loadLite(pack, "tts_mel.ptl")
                head = loadLite(pack, "head.ptl")
                backbone = loadBackbone()
                loadedPack = pack?.id
                SileroTtsService.note("модель ${pack?.id ?: "v5_5_ru"} загружена за ${System.currentTimeMillis() - t} мс")
            }
            ensureStress(pack)
        } catch (e: Exception) {
            release()
            throw e
        }
    }

    /** Акцентор голоса [pack] (null — штатная модель: русский из assets) и у русских голосов BERT — без загрузки
     * тройки, чтобы не трогать её; «Книга с ударениями» зовёт заранее, пока в окне «загрузка». */
    @Synchronized fun ensureStress(pack: Pack? = null) {
        ensureAccentor(pack?.takeIf { it.accentor }?.dir)
        // морфология языка пака (белорусская из GrammarDB) — нормализатору слоя: род и падеж числа
        pack?.takeIf { it.lang != "ru" && it.dir.path != morphDir }?.let { p ->
            BelNumbers.morph = File(p.dir, Packs.MORPH).takeIf { it.isFile }
                ?.let { f -> runCatching { Morph.open(f) }.onFailure { e -> Log.e(TAG, "morph.bin пака не открылся", e) }.getOrNull() }
            morphDir = p.dir.path
        }
        // омографы BERT — русские: белорусскому голосу не нужны, русскому греем заранее, а не на первой фразе
        if ((pack?.lang ?: "ru") == "ru") ensureHomo()
    }

    /** Акцентор [dir]: null — штатный русский из assets. Модуль один: акцентор другого языка заменяет прежний,
     * иначе белорусская фраза считалась бы русским акцентором и наоборот. */
    @Synchronized private fun ensureAccentor(dir: File?) {
        if (acc != null && accLoadedDir == dir?.path) return
        acc?.destroy(); acc = null
        acc = if (dir == null) LiteModuleLoader.loadModuleFromAsset(context.assets, "silero/accentor.ptl")
            else LiteModuleLoader.load(File(dir, Packs.ACCENTOR).path)
        accLoadedDir = dir?.path
    }

    private fun loadLite(pack: Pack?, name: String): Module =
        if (pack == null) LiteModuleLoader.loadModuleFromAsset(context.assets, "silero/$name") else LiteModuleLoader.load(File(pack.dir, name).path)

    /** ExecuTorch грузит только с пути: ассет копируем в filesDir после каждой установки APK, файл пака берём из его папки.
     * Одной длины мало: у вокодера 1.0 (общий, ru_voices) она та же, что у штатного 17.09, и обновившиеся
     * оставались со старым бэкбоном при новых head и tts_mel. */
    private fun loadBackbone(): org.pytorch.executorch.Module {
        val f = packDir?.let { File(it, "backbone.pte") } ?: File(context.filesDir, "backbone.pte").also { f ->
            val len = context.assets.openFd("silero/backbone.pte").use { it.length }
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            if (f.length() != len || f.lastModified() < installed) context.assets.open("silero/backbone.pte").use { i -> f.outputStream().use { i.copyTo(it) } }
        }
        return org.pytorch.executorch.Module.load(f.absolutePath, if (resident) org.pytorch.executorch.Module.LOAD_MODE_FILE else org.pytorch.executorch.Module.LOAD_MODE_MMAP, threads.takeIf { it > 0 } ?: fastCores)
    }

    /** Потоки forward. Правило fast_cores (вкл. по умолчанию) — по числу быстрых ядер, выключено — все.
     * Redmi Note 13 5G (Dimensity 6080, 2×A76 + 6×A55, sysfs показывает 4 быстрых): forward на 1/2/4/6/8
     * потоках = 416/269/207/257/562 мс при 2,3/2,7/3,7/6,4/17 с процессорного времени на запрос —
     * медленные ядра только вредят. Galaxy A32 (2×A75 + 6×A55): 2 потока 1009 мс против 776 мс на всех,
     * процессорного времени 1,9 с против 4,7 с. Mobile-сборка PyTorch пересоздаёт pthreadpool на каждый
     * setNumThreads, менять можно между запросами; у ExecuTorch потоки задаются при загрузке — перегружаем backbone. */
    var threads = 0
        @Synchronized set(n) {
            if (n == field) return
            // прогрев грузит бэкбон при field = 0, то есть на fastCores: первый запрос с тем же числом не перегружает его
            val was = field.takeIf { it > 0 } ?: fastCores
            field = n; LitePyTorchAndroid.setNumThreads(n); SileroTtsService.note("потоков синтеза: $n")
            if (backbone != null && n != was) { backbone?.destroy(); backbone = loadBackbone() }
        }

    /** Бэкбон целиком в памяти (LOAD_MODE_FILE), а не отображением файла (MMAP): страницы отображения система
     * после простоя выкидывает, и первая фраза снова читает их с флеш-памяти. Дороже на размер бэкбона в памяти,
     * поэтому только пока работает экранный чтец (SileroTtsService.srHold). Смена — перезагрузка бэкбона. */
    var resident = false
        @Synchronized set(v) {
            if (v == field) return
            field = v
            if (backbone != null) { backbone?.destroy(); backbone = loadBackbone(); SileroTtsService.note("бэкбон ${if (v) "в памяти" else "отображением файла"}") }
        }

    /** Загружена ли тройка синтеза (какого голоса — не важно). */
    val loaded: Boolean @Synchronized get() = mel != null

    private fun releaseTts() {
        mel?.destroy(); head?.destroy(); backbone?.destroy()
        mel = null; head = null; backbone = null; packDir = null; loadedPack = null
    }

    @Synchronized fun release() {
        releaseTts()
        acc?.destroy(); homo?.destroy(); acc = null; homo = null; accLoadedDir = null
    }

    /** Пак [id] поставлен заново или удалён: выгружаем, только если из него загружена тройка или акцентор — установка
     * чужого пака не должна заставлять TalkBack грузить модели заново. */
    @Synchronized fun releasePack(id: String) {
        if (loadedPack == id || accLoadedDir?.let { File(it).name } == id) release()
    }

    /** BERT-омографы — только русские (ассет штатной модели). */
    @Synchronized private fun ensureHomo() {
        if (homo == null) homo = LiteModuleLoader.loadModuleFromAsset(context.assets, "silero/homo.ptl")
    }

    // под монитором: release() из другого потока не снимет модуль посреди forward (монитор реентерабельный)
    @Synchronized override fun accentor(words: List<String>): Pair<Array<FloatArray>, Array<FloatArray>> {
        // загруженный не меняем: его поставил ensureLoaded/ensureStress голоса; после release() — штатный русский
        if (acc == null) ensureAccentor(null)
        return accentorOut(acc!!.forward(IValue.listFrom(*words.map { IValue.from(it) }.toTypedArray())))
    }

    @Synchronized override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray): FloatArray {
        ensureHomo()
        if (ids.isEmpty()) return FloatArray(0)
        val b = ids.size; val len = ids.maxOf { it.size }
        val flat = LongArray(b * len) { data.bertPad.toLong() }
        for ((i, row) in ids.withIndex()) row.copyInto(flat, i * len)
        val logits = homo!!.forward(
            IValue.from(Tensor.fromBlob(flat, longArrayOf(b.toLong(), len.toLong()))),
            IValue.from(Tensor.fromBlob(starts, longArrayOf(b.toLong()))),
            IValue.from(Tensor.fromBlob(ends, longArrayOf(b.toLong())))
        ).toTensor().dataAsFloatArray
        return FloatArray(b) { (1.0 / (1.0 + exp(-logits[it].toDouble()))).toFloat() }
    }

    /** durs[i] — число фреймов, занятых i-м входным символом seq (включая sos/eos). */
    data class Synth(val audio: FloatArray, val durs: FloatArray)

    /**
     * rates/pitches — коэффициенты темпа и высоты по символам (durs_rate, pitch_coefs; высота 0 —
     * «робот»), focus — логическое ударение по символам (focus_mask, 0..3), symbDurs — явная
     * длительность символа в кадрах (symb_durs: индекс в seq → кадры по 12.5 мс), паузы модели.
     */
    fun synthesize(seq: LongArray, speakerId: Int, sampleRate: Int, rates: FloatArray, pitches: FloatArray, typeIds: LongArray,
                   focus: LongArray, symbDurs: Map<Long, Long>, types: Boolean = true, cancelled: () -> Boolean = { false }): Synth {
        check(mel != null) { "модели не загружены" }   // ensureLoaded(pack) зовёт сервис — какой пак, знает только он
        val n = seq.size.toLong()
        val t = System.nanoTime()
        // 11 аргументов как у v5_ru; type_ids и focus_mask (12-й и 13-й) есть только у v5_5_ru
        val args = arrayListOf(
            IValue.from(Tensor.fromBlob(seq, longArrayOf(1, n))),
            IValue.from(Tensor.fromBlob(longArrayOf(speakerId.toLong()), longArrayOf(1))),
            IValue.from(sampleRate.toLong()),
            if (symbDurs.isEmpty()) IValue.optionalNull() else IValue.dictLongKeyFrom(symbDurs.mapValues { IValue.from(it.value) }),
            IValue.from(Tensor.fromBlob(rates, longArrayOf(1, n))),
            IValue.from(Tensor.fromBlob(pitches, longArrayOf(1, n))),
            IValue.optionalNull(),
            IValue.optionalNull(),
            IValue.from("cpu"),
            IValue.from(-1L),
            IValue.from(false))
        if (types) {
            args += IValue.from(Tensor.fromBlob(typeIds, longArrayOf(1, n)))
            args += if (focus.all { it == 0L }) IValue.optionalNull() else IValue.from(Tensor.fromBlob(focus, longArrayOf(1, n)))
        }
        val out = mel!!.forward(*args.toTypedArray()).toTuple()
        val melT = out[0].toTensor()
        // forward не прерывается: фразу, которую чтец уже перебил, хотя бы не досчитываем бэкбоном (около половины времени)
        if (cancelled()) throw java.util.concurrent.CancellationException()
        val tb = System.nanoTime()
        val hid = backbone!!.forward(EValue.from(org.pytorch.executorch.Tensor.fromBlob(melT.dataAsFloatArray, melT.shape())))[0].toTensor()
        val th = System.nanoTime()
        val audio = head!!.forward(IValue.from(Tensor.fromBlob(hid.dataAsFloatArray, hid.shape())), IValue.from(sampleRate.toLong()),
            IValue.from(0.0), IValue.from(true)).toTensor().dataAsFloatArray
        backboneMs.addAndGet((th - tb) / 1_000_000)
        liteMs.addAndGet((tb - t + System.nanoTime() - th) / 1_000_000)
        Log.i(TAG, "forward ${(System.nanoTime() - t) / 1_000_000} мс, звук ${audio.size * 1000L / sampleRate} мс, $n симв.")
        return Synth(audio, out[1].toTensor().dataAsFloatArray)
    }

    companion object {
        private fun softmaxRows(t: Tensor): Array<FloatArray> {
            val rows = t.shape()[0].toInt(); val cols = t.shape()[1].toInt(); val d = t.dataAsFloatArray
            return Array(rows) { r ->
                val row = FloatArray(cols) { d[r * cols + it] }
                val max = row.max(); val e = row.map { exp((it - max).toDouble()) }; val s = e.sum()
                FloatArray(cols) { (e[it] / s).toFloat() }
            }
        }

        /** Выход акцентора: (ударение, «ё») по словам. Акцентор пака может отдать один тензор или кортеж с None
         * вторым (silero-stress bel/ukr: `(logits, None)`) — тогда «ё» пустые строки той же длины. */
        fun accentorOut(out: IValue): Pair<Array<FloatArray>, Array<FloatArray>> {
            val parts = if (out.isTuple) out.toTuple() else arrayOf(out)
            val stress = softmaxRows(parts[0].toTensor())
            val yo = parts.getOrNull(1)?.takeIf { it.isTensor }?.let { softmaxRows(it.toTensor()) } ?: Array(stress.size) { FloatArray(0) }
            return stress to yo
        }

        /** Время forward с запуска процесса: mel + head (TorchScript Lite) и бэкбон (ExecuTorch) — для Perf. */
        val liteMs = java.util.concurrent.atomic.AtomicLong()
        val backboneMs = java.util.concurrent.atomic.AtomicLong()
        /** Один экземпляр на процесс: сервис и настройки живут в одном процессе, и «Разбор» берёт те же
         * акцентор и BERT, что синтез, а не вторую копию на 50 МБ. Жизнью управляет сервис (выгрузка по
         * простою, onDestroy); release() лишь снимает модули, следующий ensure* грузит заново. */
        @Volatile private var instance: SileroModels? = null
        fun shared(context: Context): SileroModels =
            instance ?: synchronized(this) { instance ?: SileroModels(context.applicationContext).also { instance = it } }

        /** Ядра быстрее самого медленного кластера (по cpuinfo_max_freq): 2 на A32, 4 на Dimensity 6080.
         * Все ядра, если кластер один или sysfs закрыт. Прибить потоки к ядрам из Java нельзя,
         * планировщик сам сажает тяжёлые потоки на быстрые. */
        val fastCores: Int by lazy {
            val freqs = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
                ?.mapNotNull { runCatching { File(it, "cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull() }
                ?: emptyList()
            val fast = freqs.minOrNull()?.let { m -> freqs.count { it > m } } ?: 0
            if (fast > 0) fast else Runtime.getRuntime().availableProcessors()
        }
        const val TAG = "RuVoice"
        // json (2.5 МБ) разбирается один раз на процесс — не на каждый SileroModels(context).
        @Volatile private var shared: SileroData? = null
        // shared — только когда загружены и словари: кто получил data, сразу читает с «ё» и твёрдым «э».
        fun data(context: Context): SileroData = shared ?: synchronized(this) {
            shared ?: run {
                val ctx = context.applicationContext
                val t = System.nanoTime()
                val d = SileroData(StressBin.File(asset(ctx, "silero/stress.bin")))
                Log.i(TAG, "stress.bin: ${(System.nanoTime() - t) / 1_000_000} мс")
                // Морфология для нормализатора — mmap ассета, один раз на процесс.
                Normalizer.morph = runCatching { Morph.open(ctx) }.onFailure { e -> Log.e(TAG, "morph.bin не открылся", e) }.getOrNull()
                YoDict.shared = YoDict(d.yo)
                HardE.shared = runCatching { HardE.open(ctx) }.onFailure { e -> Log.e(TAG, "hard_e.txt не открылся", e) }.getOrNull()
                Emoji.shared = runCatching { Emoji.open(ctx) }.onFailure { e -> Log.e(TAG, "emoji_ru.tsv не открылся", e) }.getOrNull()
                SymbolNames.cldr = runCatching { SymbolNames.open(ctx) }.onFailure { e -> Log.e(TAG, "symbols_ru.tsv не открылся", e) }.getOrNull()
                // белорусские имена эмодзи и знаков — 170 КБ, читаются за десятки мс; вместе с русскими, чтобы не ждать на первой фразе
                Emoji.be = runCatching { Emoji.open(ctx, "emoji_be.tsv") }.onFailure { e -> Log.e(TAG, "emoji_be.tsv не открылся", e) }.getOrNull()
                SymbolNames.cldrBe = runCatching { SymbolNames.open(ctx, "symbols_be.tsv") }.onFailure { e -> Log.e(TAG, "symbols_be.tsv не открылся", e) }.getOrNull()
                Emoji.uk = runCatching { Emoji.open(ctx, "emoji_uk.tsv") }.onFailure { e -> Log.e(TAG, "emoji_uk.tsv не открылся", e) }.getOrNull()
                SymbolNames.cldrUk = runCatching { SymbolNames.open(ctx, "symbols_uk.tsv") }.onFailure { e -> Log.e(TAG, "symbols_uk.tsv не открылся", e) }.getOrNull()
                d.also { shared = it }
            }
        }

        /** mmap несжатого ассета (build.gradle noCompress «bin»); сжатый — читаем целиком. */
        private fun asset(ctx: Context, name: String): java.nio.ByteBuffer = try {
            ctx.assets.openFd(name).use { fd -> fd.createInputStream().channel.use { it.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length) } }
        } catch (e: java.io.IOException) {
            Log.w(TAG, "$name без mmap, читаем целиком: $e")
            java.nio.ByteBuffer.wrap(ctx.assets.open(name).use { it.readBytes() })
        }

        @Volatile private var speakerNames: Set<String>? = null
        /** Имена штатных голосов из заголовка stress.bin — без data(): система при подключении читалки спрашивает
         * голос по умолчанию и шлёт первую фразу только после ответа, а data() ещё и инициализирует Normalizer (~1,4 с). */
        fun speakers(context: Context): Set<String> = shared?.speakers?.keys ?: speakerNames
            ?: org.json.JSONObject(StressBin.File(asset(context.applicationContext, "silero/stress.bin")).head).getJSONObject("speakers")
                .keys().asSequence().toSet().also { speakerNames = it }
    }
}
