package ru.kost.ruvoice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.kost.ruvoice.text.Replacements

class DictsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun migrateMovesOldFilesIntoMainListAndSeedsDefaults() {
        val root = tmp.root
        root.resolve("user_stress.txt").writeText("творог твор+ог\n")
        Dicts.migrate(root, "старый замок = старый з+амок\n")
        assertFalse(root.resolve("user_stress.txt").exists())
        assertEquals("творог твор+ог\n", root.resolve("dicts/stress/Основной.txt").readText())
        // замен не было — предустановки, как раньше при первом запуске
        assertEquals("старый замок = старый з+амок\n", root.resolve("dicts/replace/Основной.txt").readText())
        // повторный вызов ничего не трогает
        root.resolve("dicts/replace/Основной.txt").writeText("")
        Dicts.migrate(root, "старый замок = старый з+амок\n")
        assertEquals("", root.resolve("dicts/replace/Основной.txt").readText())
    }

    @Test fun installSystemWritesAssetAndRefreshesItAfterUpdate() {
        val root = tmp.root
        var asset = "# v1\nтворог твор+ог\n"
        Dicts.installSystem(root) { path -> if (path == "dicts/stress/Системный.txt") asset else null }
        val f = root.resolve("dicts/stress/Системный.txt")
        assertEquals(asset, f.readText())
        assertFalse(root.resolve("dicts/replace/Системный.txt").exists())
        f.writeText("правка пользователя\n")
        Dicts.installSystem(root) { path -> if (path == "dicts/stress/Системный.txt") asset else null }
        assertEquals(asset, f.readText()) // системный список всегда как в assets
        asset = "# v2\nтворог твор+ог\n"
        Dicts.installSystem(root) { path -> if (path == "dicts/stress/Системный.txt") asset else null }
        assertEquals(asset, f.readText())
    }

    /** Отключённая строка системного уходит в «Системный удалённые», переживает обновление (сверка по ключу,
     * даже если вариант в assets поменялся) и возвращается, когда её убирают оттуда. */
    @Test fun removedSystemLinesSurviveUpdateAndComeBack() {
        val root = tmp.root
        var stress = "# v1\nтворог твор+ог\nзамок з+амок\n"
        var replace = "г. = год\n~(\\d+)р = $1 рублей\n"
        fun install() = Dicts.installSystem(root) { path -> when (path) { "dicts/stress/Системный.txt" -> stress; "dicts/replace/Системный.txt" -> replace; else -> null } }
        install()
        val sys = Dicts.file(root, Dicts.Kind.STRESS, Dicts.SYSTEM)
        val sysR = Dicts.file(root, Dicts.Kind.REPLACE, Dicts.SYSTEM)
        Dicts.addRemoved(root, Dicts.Kind.STRESS, "творог твор+ог")
        Dicts.addRemoved(root, Dicts.Kind.REPLACE, "~(\\d+)р = $1 рублей")
        for (k in Dicts.Kind.values()) Dicts.rebuildSystem(root, k)
        assertEquals("# v1\nзамок з+амок\n", sys.readText())
        assertEquals("г. = год\n", sysR.readText())
        // удалённые — сразу за системным; полная копия (Системный.full) в списки не попадает
        Dicts.file(root, Dicts.Kind.STRESS, "Азбука").writeText("")
        assertEquals(listOf(Dicts.SYSTEM, Dicts.REMOVED, "Азбука"), Dicts.files(root, Dicts.Kind.STRESS).map { Dicts.name(it) })
        // обновление: ударение в «твороге» поменялось, слово всё равно остаётся убранным
        stress = "# v2\nтворог тв+орог\nзамок з+амок\nмолоко молок+о\n"
        replace = "г. = года\n~(\\d+)р = $1 руб.\n"
        install()
        assertEquals("# v2\nзамок з+амок\nмолоко молок+о\n", sys.readText())
        assertEquals("г. = года\n", sysR.readText())
        // вернули — в системный приходит вариант из новой версии, на прежнее место
        Dicts.dropRemoved(root, Dicts.Kind.STRESS, "творог твор+ог")
        Dicts.dropRemoved(root, Dicts.Kind.REPLACE, "~(\\d+)р = $1 рублей")
        for (k in Dicts.Kind.values()) Dicts.rebuildSystem(root, k)
        assertEquals(stress, sys.readText())
        assertEquals(replace, sysR.readText())
        assertEquals("", Dicts.file(root, Dicts.Kind.STRESS, Dicts.REMOVED).readText())
    }

    @Test fun scopesTreatRemovedAsSystem() {
        assertTrue(Dicts.Scope.SYSTEM.covers(Dicts.REMOVED))
        assertFalse(Dicts.Scope.USER.covers(Dicts.REMOVED))
        assertTrue(Dicts.Scope.USER.covers(Dicts.MAIN))
    }

    /** Системный список пака «Системный be»: только своему языку; «Системный удалённые» и свои — не пака. */
    @Test fun packListNamesAndLanguages() {
        assertEquals("be", Dicts.packLang("Системный be")); assertEquals("uk", Dicts.packLang("Системный uk"))
        for (n in listOf(Dicts.SYSTEM, Dicts.REMOVED, Dicts.MAIN, "Системный BE", "Системный be (2)", "Мой Системный be")) assertNull(n, Dicts.packLang(n))
        assertTrue(Dicts.isSystem("Системный be")); assertTrue(Dicts.isSystem(Dicts.REMOVED)); assertFalse(Dicts.isSystem("Системный be (2)"))
        assertEquals("ru", Dicts.langOf(Dicts.SYSTEM)); assertEquals("ru", Dicts.langOf(Dicts.REMOVED))
        assertEquals("be", Dicts.langOf("Системный be")); assertNull(Dicts.langOf(Dicts.MAIN))
        assertTrue(Dicts.Scope.SYSTEM.covers("Системный be")); assertFalse(Dicts.Scope.USER.covers("Системный be"))
        val files = listOf(Dicts.SYSTEM, Dicts.REMOVED, "Системный be", "Системный uk", Dicts.MAIN).map { tmp.root.resolve("$it.txt") }
        fun names(lang: String) = Dicts.forLang(files, lang).map { Dicts.name(it) }
        assertEquals(listOf(Dicts.SYSTEM, Dicts.REMOVED, Dicts.MAIN), names("ru"))
        assertEquals(listOf("Системный be", Dicts.MAIN), names("be"))
        assertEquals(listOf("Системный uk", Dicts.MAIN), names("uk"))
    }

    /** Порядок слияния: Системный, удалённые, системные паков, свои — свои перебивают список пака. */
    @Test fun packListsAfterSystemBeforeOwn() {
        val dir = Dicts.dir(tmp.root, Dicts.Kind.STRESS).apply { mkdirs() }
        for (n in listOf("Азбука", "Системный uk", Dicts.MAIN, "Системный be", Dicts.REMOVED, Dicts.SYSTEM)) dir.resolve("$n.txt").writeText("")
        assertEquals(listOf(Dicts.SYSTEM, Dicts.REMOVED, "Системный be", "Системный uk", "Азбука", Dicts.MAIN),
            Dicts.files(tmp.root, Dicts.Kind.STRESS).map { Dicts.name(it) })
    }

    @Test fun packListsNotExported() {
        // данные пака (CC BY-SA) в экспорт настроек не идут; такое имя из импорта — под свободным (Prefs.importJson)
        assertFalse(Dicts.portable("Системный be")); assertFalse(Dicts.portable(" Системный be "))
        assertTrue(Dicts.portable(Dicts.SYSTEM)); assertTrue(Dicts.portable(Dicts.REMOVED)); assertTrue(Dicts.portable(Dicts.MAIN))
    }

    @Test fun syncPacksCopiesForeignStressOnly() {
        val root = tmp.newFolder("root")
        val json = JSONObject(TestData.root().resolve("app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack")
        fun pack(id: String, lang: String, stress: String?) = tmp.newFolder(id).let { d ->
            stress?.let { d.resolve(Packs.STRESS).writeText(it) }
            Pack(JSONObject(json.toString()).put("id", id).put("format", 3).put("lang", lang).toString(), d)
        }
        val be = pack("cis_be", "be", "сталы +сталы\n")
        val be2 = pack("zz_be", "be", "іншае інш+ае\n")   // второй пак языка: берётся первый по id
        val beBare = pack("aa_be", "be", null)            // пак без stress.txt не в счёт
        val ru = pack("cis_ru", "ru", "кот к+от\n")       // русскому паку списка нет
        val own = Dicts.file(root, Dicts.Kind.STRESS, Dicts.MAIN).apply { parentFile!!.mkdirs(); writeText("сталы ст+алы\n") }
        Dicts.syncPacks(root, listOf(ru, be2, beBare, be))
        val list = Dicts.file(root, Dicts.Kind.STRESS, "Системный be")
        assertEquals("сталы +сталы\n", list.readText())
        assertEquals(listOf("Системный be", Dicts.MAIN), Dicts.files(root, Dicts.Kind.STRESS).map { Dicts.name(it) })
        // копия с mtime источника; та же длина и тот же mtime — не переписываем (содержимое не сравниваем)
        val src = be.dir.resolve(Packs.STRESS)
        assertEquals(src.lastModified(), list.lastModified())
        list.writeText("СТАЛЫ +СТАЛЫ\n"); list.setLastModified(src.lastModified())
        Dicts.syncPacks(root, listOf(ru, be2, beBare, be))
        assertEquals("СТАЛЫ +СТАЛЫ\n", list.readText())
        // пак переставили: другой mtime — копируем, даже при той же длине и источнике старше копии; другая длина — тоже
        src.writeText("сталы ст+алы\n"); src.setLastModified(list.lastModified() - 5000)
        Dicts.syncPacks(root, listOf(be))
        assertEquals("сталы ст+алы\n", list.readText())
        assertEquals(src.lastModified(), list.lastModified())
        src.writeText("сталы +сталы\nдзякуй дзя+куй\n"); src.setLastModified(list.lastModified())
        Dicts.syncPacks(root, listOf(be))
        assertEquals(src.readText(), list.readText())
        assertFalse(Dicts.dir(root, Dicts.Kind.STRESS).resolve("Системный be.txt.tmp").exists())
        // пак удалили — список убран, свой на месте
        Dicts.syncPacks(root, listOf(ru))
        assertFalse(list.exists())
        assertEquals("сталы ст+алы\n", own.readText())
        assertEquals(listOf(Dicts.MAIN), Dicts.files(root, Dicts.Kind.STRESS).map { Dicts.name(it) })
    }

    @Test fun syncPacksKeepsUserListsWithPackNames() {
        // «Системный be» и «Системный uk» пользователя (имя было свободно) — не копии пака: ни перезаписи, ни удаления
        val root = tmp.newFolder("root")
        val json = JSONObject(TestData.root().resolve("app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack")
        val be = tmp.newFolder("cis_be").let { d ->
            d.resolve(Packs.STRESS).writeText("сталы +сталы\n")
            Pack(JSONObject(json.toString()).put("id", "cis_be").put("format", 3).put("lang", "be").toString(), d)
        }
        fun f(kind: Dicts.Kind, name: String) = Dicts.file(root, kind, name)
        fun names(kind: Dicts.Kind) = Dicts.files(root, kind).map { Dicts.name(it) }
        f(Dicts.Kind.STRESS, "Системный be").apply { parentFile!!.mkdirs(); writeText("мой м+ой\n") }
        f(Dicts.Kind.STRESS, "Системный uk").writeText("кіт к+іт\n")
        f(Dicts.Kind.REPLACE, "Системный be").apply { parentFile!!.mkdirs(); writeText("кот = к+от\n") }
        Dicts.syncPacks(root, listOf(be))
        assertEquals("сталы +сталы\n", f(Dicts.Kind.STRESS, "Системный be").readText())
        assertEquals("мой м+ой\n", f(Dicts.Kind.STRESS, "Системный be (2)").readText())
        assertEquals("кіт к+іт\n", f(Dicts.Kind.STRESS, "Системный uk (2)").readText())
        assertEquals("кот = к+от\n", f(Dicts.Kind.REPLACE, "Системный be (2)").readText())
        assertEquals(listOf("Системный be", "Системный be (2)", "Системный uk (2)"), names(Dicts.Kind.STRESS))
        assertEquals(listOf("Системный be (2)"), names(Dicts.Kind.REPLACE))
        // пак сняли — убрана только своя копия
        Dicts.syncPacks(root, emptyList())
        assertEquals(listOf("Системный be (2)", "Системный uk (2)"), names(Dicts.Kind.STRESS))
        // копия от сборки без учёта (та же, что в паке) — своя: не переименована, со снятием пака уходит
        val old = tmp.newFolder("old")
        Dicts.file(old, Dicts.Kind.STRESS, "Системный be").apply { parentFile!!.mkdirs(); writeText("сталы +сталы\n") }
        Dicts.syncPacks(old, listOf(be))
        assertEquals(listOf("Системный be"), Dicts.files(old, Dicts.Kind.STRESS).map { Dicts.name(it) })
        Dicts.syncPacks(old, emptyList())
        assertTrue(Dicts.files(old, Dicts.Kind.STRESS).isEmpty())
    }

    /** Свои списки перебивают системный: он первый в порядке слияния, а при совпадении побеждает поздний. */
    @Test fun userListsOverrideSystem() {
        val root = tmp.root
        for (kind in Dicts.Kind.values()) Dicts.dir(root, kind).mkdirs()
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.SYSTEM).writeText("творог твор+ог\n")
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.NAMES).writeText("")
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.MAIN).writeText("творог тв+орог\n")
        Dicts.file(root, Dicts.Kind.REPLACE, Dicts.SYSTEM).writeText("амбарный замок = амбарный зам+ок\n")
        Dicts.file(root, Dicts.Kind.REPLACE, Dicts.MAIN).writeText("амбарный замок = амбарный з+амок\n")
        assertEquals(listOf(Dicts.SYSTEM, Dicts.NAMES, Dicts.MAIN), Dicts.files(root, Dicts.Kind.STRESS).map { Dicts.name(it) })
        assertEquals("тв+орог", DictCache.stress(Dicts.files(root, Dicts.Kind.STRESS))["творог"])
        assertEquals("амбарный з+амок", DictCache.replacements(Dicts.files(root, Dicts.Kind.REPLACE)).apply("амбарный замок"))
    }

    /** Свои списки перебивают и системный другого вида: своё слово в ударениях снимает системную замену с ним
     * (иначе её «+» заставит словарь ударений слово пропустить), своя однословная замена — системную фразу с этим словом. */
    @Test fun userListsOverrideSystemAcrossKinds() {
        val root = tmp.root
        for (kind in Dicts.Kind.values()) Dicts.dir(root, kind).mkdirs()
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.SYSTEM).writeText("")
        Dicts.file(root, Dicts.Kind.STRESS, Dicts.MAIN).writeText("потом пот+ом\nкому-то ком+у-то\n")
        Dicts.file(root, Dicts.Kind.REPLACE, Dicts.SYSTEM).writeText(
            "потом = п+отом\nобливаясь потом = обливаясь п+отом\nстарый замок = старый зам+ок\nворон = в+орон\nкот = к+от\n" +
            "кому-то = к+омуто\nв кому = в к+ому\nгде-то там = гд+ето т+ам\n")
        Dicts.file(root, Dicts.Kind.REPLACE, Dicts.MAIN).writeText("замок = з+амок\nВорон = вор+он\nгде-то = гд+е-то\n")
        val r = DictCache.replacements(Dicts.files(root, Dicts.Kind.REPLACE), stress = Dicts.files(root, Dicts.Kind.STRESS))
        assertEquals("обливаясь потом", r.apply("обливаясь потом"))
        assertEquals("старый з+амок", r.apply("старый замок"))
        assertEquals("вор+он", r.apply("ворон"))
        assertEquals("к+от", r.apply("кот"))
        // слово через дефис целиком: своё «кому-то» снимает системное, «в кому» остаётся
        assertEquals("кому-то", r.apply("кому-то"))
        assertEquals("в к+ому", r.apply("в кому"))
        assertEquals("гд+е-то там", r.apply("где-то там"))
    }

    @Test fun systemDictsFromAssetsParse() {
        // ударения: «слово сл+ово»; замены: «фраза = фраза с ударением», слово с «+» входит в ключ (ё-вариант — «все же = вс+ё же»)
        val stress = TestData.root().resolve("app/src/main/assets/dicts/stress/Системный.txt").readLines()
        val parsed = stress.mapNotNull { DictLines.parseStress(it) }
        assertTrue(parsed.size > 20000)
        // имена с «ё» и через «е»: «федоров ф+ёдоров»; из библиотеки — и твёрдое «э»: «малдер м+алдэр» (словарь подменяет буквы слова)
        fun e(s: String) = s.replace('ё', 'е').replace('э', 'е')
        assertTrue(parsed.all { (w, v) -> e(v.replace("+", "")) == e(w) && v.count { it == '+' } == 1 })
        val stressMap = parsed.toMap()   // выход нормализатора («около 300 рублей», «300-й»): модель читает «тр+ёхсот», норма «трёхс+от»
        assertEquals("м+алдэр", stressMap["малдер"]); assertEquals("трёхк+омнатную", stressMap["трехкомнатную"]); assertEquals("трёхк+омнатную", stressMap["трёхкомнатную"])
        assertEquals("трёхс+от", stressMap["трёхсот"]); assertEquals("четырёхс+отый", stressMap["четырёхсотый"]); assertEquals("трехт+ысячного", stressMap["трехтысячного"])
        val replace = TestData.root().resolve("app/src/main/assets/dicts/replace/Системный.txt").readLines()
        val r = Replacements.parse(replace)
        val phrases = replace.dropWhile { !it.startsWith("# Фразы-подсказки") }   // выше — сложные слова и орфоэпия («гм = гмм»)
        val pairs = phrases.takeWhile { !it.startsWith("# Ударение на предлоге") }.mapNotNull { Replacements.split(it) }
        assertTrue(pairs.size > 5000)
        // «п+отомъ»: «ъ» на конце слова — подсказка модели, в ключе её нет (жалоба 28.10.2026)
        val hardEnd = Regex("ъ(?![а-яё])")
        assertTrue(pairs.filter { '*' !in it.first }.all { (k, v) -> v.replace("+", "").replace('ё', 'е').replace(hardEnd, "") == k.replace('ё', 'е').removePrefix("$").replace(hardEnd, "") && v.count { it == '+' } in 1..2 })
        // ударение на предлоге: предлог слеплен со следующим словом («н+абок»), иначе оба звучат ударно
        val clitic = phrases.dropWhile { !it.startsWith("# Ударение на предлоге") }.mapNotNull { Replacements.split(it) }
        assertTrue(clitic.size > 100)
        assertTrue(clitic.all { (k, v) -> v.replace("+", "").replace(" ", "") == k.replace(" ", "") && v.split(" ").any { it.length > 1 && it.indexOf('+') in 0..3 && ' ' !in it } })
        assertEquals("Он н+ебыл дома и ворочался с б+оку н+абок.", r.apply("Он не был дома и ворочался с боку на бок."))
        assertEquals("Сражаться н+ескем, спросить н+еского.", r.apply("Сражаться не с кем, спросить не с кого."))
        assertEquals("Корабль спустили н+аводу, а он сидел н+огу н+аногу.", r.apply("Корабль спустили на воду, а он сидел ногу на ногу."))   // маска «*» в ключе и замене
        assertEquals("Амбарный зам+ок висел", r.apply("Амбарный замок висел"))
        // eugene в начале предложения глотает «с» у «спокойн…», с тире — нет
        assertEquals("— Спокойное лицо. Он ушёл... — спокойно, спокойно.", r.apply("Спокойное лицо. Он ушёл... спокойно, спокойно."))
        assertEquals("— Спокойный вечер, успокойся.", r.apply("— Спокойный вечер, успокойся."))
        assertEquals("— Спокойной н+очи. — Спокойно н+ачал.", r.apply("Спокойной ночи. Спокойно начал."))   // фраза-подсказка и тире вместе
        assertEquals("в+олны силы из-под земл+и и новые з+емли", r.apply("волны силы из-под земли и новые земли"))
        assertEquals("опыт Толст+ого и толстого кота", r.apply("опыт Толстого и толстого кота")) // «$» — только с заглавной
        assertEquals("тёмно-зеленый и темно", r.apply("темно-зеленый и темно"))
        // дефисные из Balamoote (tools/hyphen_first.txt): первая часть «медико-*» и слово целиком; отдельно «медико» не трогаем
        assertEquals("М+едико-биологический, м+ало-м+альски, всего-н+авсего, медико", r.apply("Медико-биологический, мало-мальски, всего-навсего, медико"))
        assertEquals("водяные пар+ы и позитронные п+ары", r.apply("водяные пары и позитронные пары"))
        assertEquals("ут+ёсный паук возбужд+ённее", r.apply("утесный паук возбужденнее"))
        assertEquals("гмм, тест и тенденции, стоят д+орога", r.apply("гм, тест и тенденции, стоят дорого"))   // [тэ] — в HardE после акцентора
        assertEquals("нар+ошно я+ишницу для Марьи Иль+инишны, ништ+о и ничтожный", r.apply("нарочно яичницу для Марьи Ильиничны, ничто и ничтожный"))   // [шн], где модель читает [чн]
        assertEquals("ков+ото, всев+ото и ев+ото, но много-то и кого", r.apply("Кого-то, всего-то и его-то, но много-то и кого"))   // [в] перед частицей
    }

    @Test fun filesAreSortedByRussianCollation() {
        val dir = tmp.root.resolve("dicts/replace").apply { mkdirs() }
        for (n in listOf("яблоко", "Ёлка", "автор", "note")) dir.resolve("$n.txt").writeText("")
        dir.resolve("мусор.bak").writeText("")
        assertEquals(listOf("note", "автор", "Ёлка", "яблоко"), Dicts.files(tmp.root, Dicts.Kind.REPLACE).map { Dicts.name(it) })
    }

    @Test fun decodeStrictUtf8ThenCp1251() {
        assertEquals("твор+ог", Dicts.decode("твор+ог".toByteArray()))
        assertEquals("твор+ог", Dicts.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "твор+ог".toByteArray()))
        assertEquals("твор+ог", Dicts.decode("твор+ог".toByteArray(charset("windows-1251"))))
    }

    @Test fun freeNameAddsCounterWhenTaken() {
        val dir = tmp.root.resolve("dicts/stress").apply { mkdirs() }
        assertEquals("Книга", Dicts.freeName(tmp.root, Dicts.Kind.STRESS, "Книга"))
        dir.resolve("Книга.txt").writeText("")
        assertEquals("Книга (2)", Dicts.freeName(tmp.root, Dicts.Kind.STRESS, "Книга"))
        dir.resolve("Книга (2).txt").writeText("")
        assertEquals("Книга (3)", Dicts.freeName(tmp.root, Dicts.Kind.STRESS, "Книга"))
        // импорт файла «Системный удалённые.txt», пока своего такого нет, не становится списком удалённых
        assertEquals("${Dicts.REMOVED} (2)", Dicts.freeName(tmp.root, Dicts.Kind.STRESS, Dicts.REMOVED))
    }

    @Test fun validNameRejectsSlashesEmptyAndLong() {
        assertTrue(Dicts.validName("Серия Ведьмак"))
        assertFalse(Dicts.validName("  "))
        assertFalse(Dicts.validName("a/b"))
        assertFalse(Dicts.validName("a\\b"))
        assertFalse(Dicts.validName("x".repeat(61)))
    }

    @Test fun demagogExportDropsRegexLinesAndTightensEquals() {
        val (text, skipped) = Dicts.toDemagog(listOf("старый замок = старый з+амок", "~(\\d+)-(\\d+) = $1 по $2", "# коммент", "малек=малёк"), Dicts.Kind.REPLACE)
        assertEquals("старый замок=старый з+амок\r\nмалек=малёк\r\n", text)
        assertEquals(listOf("~(\\d+)-(\\d+) = $1 по $2"), skipped)
        val (stress, none) = Dicts.toDemagog(listOf("творог твор+ог", "", "замок з+амок"), Dicts.Kind.STRESS)
        assertEquals("творог=твор+ог\r\nзамок=з+амок\r\n", stress)
        assertTrue(none.isEmpty())
    }

    @Test fun stressImportAcceptsDemagogEquals() {
        assertEquals("творог твор+ог\nзамок з+амок\n# note\n", Dicts.importText("творог=твор+ог\r\nзамок з+амок\n# note\n", Dicts.Kind.STRESS))
        // замены — как есть, только переводы строк
        assertEquals("a=b\n", Dicts.importText("a=b\r\n", Dicts.Kind.REPLACE))
    }
}
