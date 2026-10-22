// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Украинский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Латиница → украинские буквы: тот же разбор, что у [Latin] (словарь, капс по буквам, диграфы, по буквам), со своими
 * таблицами. Без него латиница выпадала фильтром: «USB x» давало пустую строку, «Карл V» — «карл».
 *
 * Буквы — по правопису 2019 (§ 129–131): «h» — «г» (Гемінгвей), «g» — «ґ» (звук [g], правопис допускает «ґ»), «w» — «в»
 * (Вашингтон), «e» в начале — «е». Имена букв в капсе и одной буквой — английские ([UK_LATIN]: «USB» — «ю-ес-бі»). Известные
 * слова — так, как их пишут по-украински. Не проверено носителем.
 */
object UkLatin {
    private val WORDS = mapOf(
        "iphone" to "айф+он",
        "ipad" to "айп+ад",
        "android" to "андр+оїд",
        "google" to "ґ+уґл",
        "youtube" to "ют+уб",
        "telegram" to "телегр+ам",
        "whatsapp" to "вотс+ап",
        "viber" to "в+айбер",
        "facebook" to "фейсб+ук",
        "instagram" to "інстагр+ам",
        "tiktok" to "тікт+ок",
        "twitter" to "тв+іттер",
        "windows" to "в+індоус",
        "linux" to "л+інукс",
        "excel" to "екс+ель",
        "word" to "ворд",
        "wifi" to "вайф+ай",
        "bluetooth" to "блют+уз",
        "ok" to "ок+ей",
        "film" to "фільм",
        "filmbell" to "фільмб+ел",
        "netflix" to "нетфл+ікс",
        "amazon" to "+амазон",
        "microsoft" to "майкрос+офт",
        "email" to "ім+ейл",
        "online" to "онл+айн",
        "internet" to "інтерн+ет",
        "site" to "сайт",
        "web" to "веб",
        "podcast" to "п+одкаст",
        "chrome" to "хром",
        "photoshop" to "фотош+оп",
        "zoom" to "зум",
        "spotify" to "сп+отіфай",
        "github" to "ґітх+аб",
        "chatgpt" to "чатджіпіт+і",
        "ai" to "ей-+ай",
        "blog" to "блоґ",
        "chat" to "чат",
        "link" to "лінк",
        "live" to "лайв",
        "team" to "тім",
        "video" to "в+ідео",
        "audio" to "+аудіо",
        "photo" to "ф+ото",
        "media" to "м+едіа",
        "forum" to "ф+орум",
        "login" to "л+оґін",
        "password" to "пар+оль",
        "download" to "даунл+оуд",
        "covid" to "к+овід",
        "pin" to "пін",
    )
    private val DIGRAPHS = listOf(
        "tch" to "ч", "sch" to "ш", "igh" to "ай", "tion" to "шн", "sion" to "жн",
        "ph" to "ф", "th" to "т", "sh" to "ш", "chr" to "кр", "chl" to "кл", "chn" to "кн",
        "ch" to "ч", "ck" to "к", "qu" to "кв",
        "oo" to "у", "ee" to "і", "ea" to "і", "ai" to "ей", "ay" to "ей", "ey" to "ей",
        "oi" to "ой", "oy" to "ой", "ou" to "ау", "ow" to "оу", "oa" to "оу", "ie" to "і",
        "wh" to "в", "ng" to "нг", "kn" to "н", "wr" to "р",
    )
    private val LETTERS = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф", 'g' to "ґ",
        'h' to "г", 'i' to "і", 'j' to "дж", 'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н",
        'o' to "о", 'p' to "п", 'q' to "к", 'r' to "р", 's' to "с", 't' to "т", 'u' to "у",
        'v' to "в", 'w' to "в", 'x' to "кс", 'y' to "и", 'z' to "з",
    )

    val TABLE = LatinTable(WORDS, DIGRAPHS, LETTERS, UK_LATIN, "аеєиіїоуюя", eStart = "е", yVowel = "и")

    fun translate(text: String, rules: Rules) = Latin.translate(text, rules, TABLE)
}
