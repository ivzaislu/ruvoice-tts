package ru.kost.ruvoice

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.kost.ruvoice.bin.StressBin
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

class DictCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(name: String, text: String): File = tmp.newFile(name).apply { writeText(text) }

    @Test fun stressMergesFilesLaterWins() {
        val a = file("a.txt", "творог твор+ог\nзамок з+амок\n")
        val b = file("b.txt", "замок зам+ок\n")
        val m = DictCache.stress(listOf(a, b))
        assertEquals("твор+ог", m["творог"])
        assertEquals("зам+ок", m["замок"])
    }

    @Test fun replacementsConcatenateAllFiles() {
        val a = file("a.txt", "кот = к+от\n")
        val b = file("b.txt", "пёс = п+ёс\n")
        assertEquals("к+от и п+ёс", DictCache.replacements(listOf(a, b)).apply("кот и пёс"))
    }

    @Test fun snapshotReusedUntilFileChanges() {
        val a = file("a.txt", "кот = к+от\n")
        val r1 = DictCache.replacements(listOf(a))
        assertSame(r1, DictCache.replacements(listOf(a)))
        a.writeText("кот = к+от\nпёс = п+ёс\n") // длина другая — mtime может совпасть в ту же мс
        val r2 = DictCache.replacements(listOf(a))
        assertNotSame(r1, r2)
        assertEquals("п+ёс", r2.apply("пёс"))
        // другой набор файлов — другой снимок
        assertNotSame(r2, DictCache.replacements(emptyList()))
    }

    @Test fun snapshotPerLanguage() {
        // голоса двух языков вперемешку не пересобирают снимки друг друга
        val ru = file("ru.txt", "кот = к+от\n"); val be = file("be.txt", "кот = к+от\n")
        val r1 = DictCache.replacements(listOf(ru)); val b1 = DictCache.replacements(listOf(be), lang = "be")
        assertSame(r1, DictCache.replacements(listOf(ru)))
        assertSame(b1, DictCache.replacements(listOf(be), lang = "be"))
        val s1 = DictCache.stress(listOf(ru)); DictCache.stress(listOf(be), lang = "be")
        assertSame(s1, DictCache.stress(listOf(ru)))
    }

    @Test fun ruDiskCacheAsBefore() {
        // у русского имя файла и подпись в нём — как в 1.0.1: кэш после обновления не пересобирается
        assertEquals("userdict.bin", DictCache.diskName("ru"))
        assertEquals("userdict-be.bin", DictCache.diskName("be"))
        val a = file("disk_a.txt", "творог твор+ог\n"); val disk = File(tmp.root, "userdict.bin")
        DictCache.stress(listOf(a), disk = disk)
        val tag = RandomAccessFile(disk, "r").channel.use { StressBin.strings(it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())).first }
        assertEquals("${a.path}|${a.lastModified()}|${a.length()}", tag)
    }

    @Test fun systemListsOnlyForRu() {
        val files = listOf(Dicts.SYSTEM, Dicts.REMOVED, Dicts.MAIN, Dicts.NAMES).map { File(tmp.root, "$it.txt") }
        assertEquals(files, Dicts.forLang(files, "ru"))
        assertEquals(listOf(Dicts.MAIN, Dicts.NAMES), Dicts.forLang(files, "be").map { Dicts.name(it) })
    }

    @Test fun warmBuildsSnapshotOfItsLanguage() {
        // прогрев после правки у белорусского голоса пересобирает его снимок, а русский не трогает
        val ru = file("warm_ru.txt", "кот = к+от\n"); val be = file("warm_be.txt", "кот = к+от\n")
        val r0 = DictCache.replacements(listOf(ru))
        DictCache.warm(listOf(be), Dicts.Kind.REPLACE, {}, {}, lang = "be").join()
        assertSame(r0, DictCache.replacements(listOf(ru)))
        // снимок be собран прогревом: запрос берёт его, не разбирая заново (прогресс зовётся только при разборе)
        var rebuilt = false
        assertEquals("к+от", DictCache.replacements(listOf(be), { rebuilt = true }, lang = "be").apply("кот"))
        assertFalse(rebuilt)
        val ruS = file("warm_ru_s.txt", "кот к+от\n"); val beS = file("warm_be_s.txt", "кот к+от\n")
        val s0 = DictCache.stress(listOf(ruS))
        DictCache.warm(listOf(beS), Dicts.Kind.STRESS, {}, {}, lang = "be").join()
        assertSame(s0, DictCache.stress(listOf(ruS)))
        assertEquals("к+от", DictCache.stress(listOf(beS), { rebuilt = true }, lang = "be")["кот"])
        assertFalse(rebuilt)
    }

    @Test fun warmReportsProgressAndDone() {
        val a = file("a.txt", (1..5000).joinToString("\n") { "слово$it = сл+ово$it" })
        val seen = ArrayList<Int>()
        var done = false
        DictCache.warm(listOf(a), Dicts.Kind.REPLACE, { seen += it }, { done = true }).join()
        assertTrue(done)
        assertTrue(seen.toString(), seen.isNotEmpty() && seen.last() == 100 && seen == seen.sorted())
        assertEquals("сл+ово7", DictCache.replacements(listOf(a)).apply("слово7"))
    }
}
