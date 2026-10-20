package ru.kost.ruvoice

import ru.kost.ruvoice.text.Layers

/** Голос по имени из Prefs или от читалки: штатный («xenia») или из пака («cis_ru/ru_alexandr»).
 * В сервисе класс android.speech.tts.Voice, поэтому не Voice. */
class Speaker(val name: String, val pack: Pack?, val id: Int, val sym: Symbols, val types: Boolean,
              private val builtinBases: Map<String, String> = emptyMap()) {
    /** Штатный голос, от которого темп и интонация: у новых голосов — из bases пака (у встроенной модели — из
     * silero_ru.json), иначе сам голос. */
    val base: String get() = name.substringAfter('/').let { (pack?.bases ?: builtinBases)[it] ?: it }
    /** Язык голоса — язык его пака (штатная модель и старые паки без поля lang — русские). */
    val lang: String get() = pack?.lang ?: "ru"
    companion object {
        const val DEFAULT = "xenia"
        /** Пак штатной модели для сборки lite: голые имена («xenia» из старых prefs) ищем в нём. */
        const val RU_PACK = "ru"
        /** Есть ли модель в APK (сборка full). Тесты подменяют. */
        var builtin = BuildConfig.BUILTIN_MODEL
        private fun packName(pack: Pack, speaker: String) = "${pack.id}/$speaker"
        private fun of(pack: Pack, speaker: String): Speaker? = pack.speakers[speaker]?.let { Speaker(packName(pack, speaker), pack, it, pack.sym, pack.types) }

        fun resolve(name: String?, d: SileroData, packs: List<Pack>): Speaker? {
            if (name == null) return null
            val i = name.indexOf('/')
            if (i < 0) {
                if (builtin) return d.speakers[name]?.let { Speaker(name, null, it, d.sym, true, d.bases) }
                return packs.firstOrNull { it.id == RU_PACK }?.let { of(it, name) }
            }
            val pack = packs.firstOrNull { it.id == name.substring(0, i) } ?: return null
            return of(pack, name.substring(i + 1))
        }

        /** Голос по умолчанию: «xenia» (штатная или из пака ru), иначе первый русский (lite с cis_ru и cis_be: русский
         * текст — русским голосом и слоем), иначе голос по умолчанию пака (поле default: в lite только с cis_be —
         * bel_larisa), иначе первый голос первого пака; null — голосов нет. */
        fun default(d: SileroData, packs: List<Pack>): Speaker? = defaultName(d.speakers.keys, packs)?.let { resolve(it, d, packs) }
        /** Имя [default] без SileroData — по одним именам штатных голосов, как [exists]. */
        fun defaultName(builtinNames: Set<String>, packs: List<Pack>): String? {
            val all = names(builtinNames, packs)
            return DEFAULT.takeIf { exists(it, builtinNames, packs) } ?: all.firstOrNull { lang(it, packs) == "ru" }
                ?: packs.firstNotNullOfOrNull { p -> p.defaultVoice?.let { packName(p, it) } } ?: all.firstOrNull()
        }

        /** Голос, которым читает сервис: [voice] из настроек, если он есть, иначе [defaultName] (пустой или голос удалённого
         * пака). Им же настройки выбирают язык словарей. */
        fun currentName(voice: String?, builtinNames: Set<String>, packs: List<Pack>): String? =
            voice.takeIf { exists(it, builtinNames, packs) } ?: defaultName(builtinNames, packs)

        /** Есть ли хоть один голос: встроенная модель или пак с голосами. Без разбора silero_ru.json. */
        fun hasVoices(packs: List<Pack>) = builtin || packs.any { it.speakers.isNotEmpty() }

        /** Есть ли голос — то же, что resolve(...) != null, но по одним именам штатных голосов (SileroModels.speakers):
         * без разбора всего silero_ru.json. */
        fun exists(name: String?, builtinNames: Set<String>, packs: List<Pack>): Boolean {
            if (name == null) return false
            val i = name.indexOf('/')
            if (i < 0) return if (builtin) name in builtinNames else packs.firstOrNull { it.id == RU_PACK }?.speakers?.containsKey(name) == true
            return packs.firstOrNull { it.id == name.substring(0, i) }?.speakers?.containsKey(name.substring(i + 1)) == true
        }

        /** Все имена: штатные по алфавиту (в full), затем по пакам. */
        fun names(d: SileroData, packs: List<Pack>): List<String> = names(d.speakers.keys, packs)
        fun names(builtinNames: Set<String>, packs: List<Pack>): List<String> =
            (if (builtin) builtinNames.sorted() else emptyList()) + packs.flatMap { p -> p.speakers.keys.sorted().map { packName(p, it) } }

        /** Голоса того же движка, что [main] — для прямой речи: в памяти одна тройка моделей,
         * перегружать 90 МБ на каждую реплику нельзя. */
        fun sameEngine(main: Speaker, d: SileroData, packs: List<Pack>): List<String> =
            main.pack?.let { p -> p.speakers.keys.sorted().map { packName(p, it) } } ?: d.speakers.keys.sorted()

        /** Имя для TTS API читалок: «xenia-ru», «marat-ru-cis», «larisa-be-cis». Без «/» и «_» (AlReaderX такие
         * не опознаёт), код языка — из пака один раз: голос, язык, пак; в lite «ru/xenia» — «xenia-ru», как в full.
         * Из частей имени убираем только свой язык — его код и ISO-3 («bel_larisa» в «cis_be»). */
        fun ttsName(name: String, packs: List<Pack>): String {
            val (pack, speaker) = name.split('/').let { if (it.size == 2) it else listOf("", it[0]) }
            val layer = Layers.of(packs.firstOrNull { it.id == pack }?.lang)
            val own = setOf(layer.code, layer.locale.isO3Language)
            fun parts(s: String) = s.split('_').filter { it.isNotEmpty() && it !in own }
            return (parts(speaker) + layer.code + parts(pack)).joinToString("-")
        }
        /** Язык голоса по имени: у голоса пака — язык пака, остальное (штатная модель) — русский. */
        fun lang(name: String, packs: List<Pack>): String =
            name.indexOf('/').takeIf { it > 0 }?.let { i -> packs.firstOrNull { it.id == name.substring(0, i) }?.lang } ?: "ru"
        /** Переключать ли профиль на голос [asked] запроса: только голос языка выбранного [current]. TalkBack на LocaleSpan
         * просит белорусский голос (defaultFor) — профиль белорусского голоса остался бы в prefs, и русский текст дальше
         * читался бы им: «язык системы» в defaultFor отдаёт выбранный голос. */
        fun followsProfile(asked: String, current: String?, packs: List<Pack>): Boolean =
            current == null || lang(asked, packs) == lang(current, packs)
        /** Обратно из [ttsName]; имя в старой форме «ru-ru-cis_ru/ru_marat» (до 0.15) — тоже. */
        fun fromTtsName(tts: String?, d: SileroData, packs: List<Pack>): String? = fromTtsName(tts, names(d, packs), packs)
        fun fromTtsName(tts: String?, names: List<String>, packs: List<Pack>): String? =
            tts?.let { t -> names.firstOrNull { ttsName(it, packs) == t } ?: t.removePrefix("ru-ru-") }

        /** Голос для языка [lang] (код пака: ru, be) в ответ системе: она кладёт его в KEY_PARAM_VOICE_NAME каждого
         * запроса клиента. Язык выбранного голоса [current] или системы [systemLang] — сам выбранный голос, иначе
         * TalkBack и читалки читали бы не им. Другой язык — голос этого языка: по умолчанию его пака (поле default,
         * у штатных — [DEFAULT]), с родным префиксом ISO-3 («bel_»), первый; нет таких — выбранный. */
        fun defaultFor(lang: String, current: String, systemLang: String?, names: List<String>, packs: List<Pack>): String {
            if (lang == lang(current, packs) || lang == systemLang) return current
            val own = names.filter { lang(it, packs) == lang }
            val prefix = Layers.of(lang).locale.isO3Language + "_"
            val defaults = packs.mapNotNull { p -> p.defaultVoice?.let { packName(p, it) } } + DEFAULT + "$RU_PACK/$DEFAULT"
            return defaults.firstOrNull { it in own } ?: own.firstOrNull { it.substringAfter('/').startsWith(prefix) }
                ?: own.firstOrNull() ?: current
        }

        /** Подпись в списке: «ru_alexandr (cis_ru)», штатные как есть. */
        fun label(name: String): String = name.indexOf('/').let { i -> if (i < 0) name else "${name.substring(i + 1)} (${name.substring(0, i)})" }
    }
}
