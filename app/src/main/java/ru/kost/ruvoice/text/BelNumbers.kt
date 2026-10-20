// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Числа в реальной жизни: даты, время, единицы, валюты.
 *
 * Зачем отдельно от [BelNormalizer]: там числа просто читаются словами, а «12.05.2026», «21:30»
 * и «3 км» так читать нельзя — получается «дванаццаць пяць дзве тысячы…». Здесь они раскрываются
 * по-белорусски: «дванаццатага мая дзве тысячы дваццаць шостага года», «дзевятая гадзіна трыццаць
 * хвілін», «тры кіламетры».
 *
 * Порядок важен: сначала даты и время (у них внутри цифры со знаками), потом единицы, и только
 * потом BelNormalizer читает оставшиеся числа как количественные.
 */
object BelNumbers {

    private val MONTHS = arrayOf(
        "студзеня", "лютага", "сакавіка", "красавіка", "мая", "чэрвеня",
        "ліпеня", "жніўня", "верасня", "кастрычніка", "лістапада", "снежня")

    /** Женский род: гадзіна, хвіліна, тысяча. */
    private val FEM = arrayOf(
        "нуль", "адна", "дзве", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")

    private val TENS = arrayOf(
        "", "", "дваццаць", "трыццаць", "сорак", "пяцьдзясят", "шэсцьдзясят", "семдзесят",
        "восемдзесят", "дзевяноста")

    /** Формы основ единиц: род (M, F, N; I — нескланяльнае, м. р.), назоўны…месны адз., назоўны…месны мн. Из GrammarDB (правапіс
     * 2008); чего там нет — по образцу соседних: паскаль и бекерэль как шэкель, фарад и зіверт как кулон, грэй, моль (адзінка),
     * экземпляр, радыян мн., тэсла — не проверено носителем. «Чалавек» после числа — «чалавекі», «чалавек». */
    private val UNIT_FORMS: Map<String, Pair<Gender, List<String>>> = """
        метр M метр метра метру метр метрам метры метры метраў метрам метры метрамі метрах
        гектар M гектар гектара гектару гектар гектарам гектары гектары гектараў гектарам гектары гектарамі гектарах
        літр M літр літра літру літр літрам літры літры літраў літрам літры літрамі літрах
        фут M фут фута футу фут футам фуце футы футаў футам футы футамі футах
        міля F міля мілі мілі мілю міляй мілі мілі міль мілям мілі мілямі мілях
        цаля F цаля цалі цалі цалю цаляй цалі цалі цаляў цалям цалі цалямі цалях
        грам M грам грама граму грам грамам граме грамы грамаў грамам грамы грамамі грамах
        тона F тона тоны тоне тону тонай тоне тоны тон тонам тоны тонамі тонах
        цэнтнер M цэнтнер цэнтнера цэнтнеру цэнтнер цэнтнерам цэнтнеры цэнтнеры цэнтнераў цэнтнерам цэнтнеры цэнтнерамі цэнтнерах
        унцыя F унцыя унцыі унцыі унцыю унцыяй унцыі унцыі унцый унцыям унцыі унцыямі унцыях
        гадзіна F гадзіна гадзіны гадзіне гадзіну гадзінай гадзіне гадзіны гадзін гадзінам гадзіны гадзінамі гадзінах
        хвіліна F хвіліна хвіліны хвіліне хвіліну хвілінай хвіліне хвіліны хвілін хвілінам хвіліны хвілінамі хвілінах
        секунда F секунда секунды секундзе секунду секундай секундзе секунды секунд секундам секунды секундамі секундах
        штука F штука штукі штуцы штуку штукай штуцы штукі штук штукам штукі штукамі штуках
        чалавек M чалавек чалавека чалавеку чалавек чалавекам чалавеку чалавекі чалавек чалавекам чалавекі чалавекамі чалавеках
        экземпляр M экземпляр экземпляра экземпляру экземпляр экземплярам экземпляры экземпляры экземпляраў экземплярам экземпляры экземплярамі экземплярах
        вольт M вольт вольта вольту вольт вольтам вольце вольты вольтаў вольтам вольты вольтамі вольтах
        ампер M ампер ампера амперу ампер амперам амперы амперы ампераў амперам амперы амперамі амперах
        ват M ват вата вату ват ватам ваце ваты ватаў ватам ваты ватамі ватах
        герц M герц герца герцу герц герцам герцы герцы герцаў герцам герцы герцамі герцах
        джоўль M джоўль джоўля джоўлю джоўль джоўлем джоўлі джоўлі джоўляў джоўлям джоўлі джоўлямі джоўлях
        паскаль M паскаль паскаля паскалю паскаль паскалем паскалі паскалі паскаляў паскалям паскалі паскалямі паскалях
        фарад M фарад фарада фараду фарад фарадам фарадзе фарады фарадаў фарадам фарады фарадамі фарадах
        тэсла I тэсла тэсла тэсла тэсла тэсла тэсла тэсла тэсла тэсла тэсла тэсла тэсла
        люмен M люмен люмена люмену люмен люменам люмене люмены люменаў люменам люмены люменамі люменах
        люкс M люкс люкса люксу люкс люксам люксе люксы люксаў люксам люксы люксамі люксах
        бекерэль M бекерэль бекерэля бекерэлю бекерэль бекерэлем бекерэлі бекерэлі бекерэляў бекерэлям бекерэлі бекерэлямі бекерэлях
        зіверт M зіверт зіверта зіверту зіверт зівертам зіверце зіверты зівертаў зівертам зіверты зівертамі зівертах
        дэцыбел M дэцыбел дэцыбела дэцыбелу дэцыбел дэцыбелам дэцыбеле дэцыбелы дэцыбелаў дэцыбелам дэцыбелы дэцыбеламі дэцыбелах
        калорыя F калорыя калорыі калорыі калорыю калорыяй калорыі калорыі калорый калорыям калорыі калорыямі калорыях
        атмасфера F атмасфера атмасферы атмасферы атмасферу атмасферай атмасферы атмасферы атмасфер атмасферам атмасферы атмасферамі атмасферах
        бар M бар бара бару бар барам бары бары бараў барам бары барамі барах
        моль M моль моля молю моль молем молі молі моляў молям молі молямі молях
        біт M біт біта біту біт бітам біце біты бітаў бітам біты бітамі бітах
        байт M байт байта байту байт байтам байце байты байтаў байтам байты байтамі байтах
        абарот M абарот абароту абароту абарот абаротам абароце абароты абаротаў абаротам абароты абаротамі абаротах
        удар M удар удару удару удар ударам удары удары удараў ударам удары ударамі ударах
        градус M градус градуса градусу градус градусам градусе градусы градусаў градусам градусы градусамі градусах
        працэнт M працэнт працэнта працэнту працэнт працэнтам працэнце працэнты працэнтаў працэнтам працэнты працэнтамі працэнтах
        пункт M пункт пункта пункту пункт пунктам пункце пункты пунктаў пунктам пункты пунктамі пунктах
        сіла F сіла сілы сіле сілу сілай сіле сілы сіл сілам сілы сіламі сілах
        рубель M рубель рубля рублю рубель рублём рублі рублі рублёў рублям рублі рублямі рублях
        капейка F капейка капейкі капейцы капейку капейкай капейцы капейкі капеек капейкам капейкі капейкамі капейках
        долар M долар долара долару долар доларам долары долары долараў доларам долары доларамі доларах
        цэнт M цэнт цэнта цэнту цэнт цэнтам цэнце цэнты цэнтаў цэнтам цэнты цэнтамі цэнтах
        пенс M пенс пенса пенсу пенс пенсам пенсе пенсы пенсаў пенсам пенсы пенсамі пенсах
        еўра N еўра еўра еўра еўра еўра еўра еўра еўра еўра еўра еўра еўра
        фунт M фунт фунта фунту фунт фунтам фунце фунты фунтаў фунтам фунты фунтамі фунтах
        грыўня F грыўня грыўні грыўні грыўню грыўняй грыўні грыўні грыўняў грыўням грыўні грыўнямі грыўнях
        тэнге I тэнге тэнге тэнге тэнге тэнге тэнге тэнге тэнге тэнге тэнге тэнге тэнге
        іена F іена іены іене іену іенай іене іены іен іенам іены іенамі іенах
        ліра F ліра ліры ліры ліру лірай ліры ліры лір лірам ліры лірамі лірах
        вона F вона воны воне вону вонай воне воны вон вонам воны вонамі вонах
        шэкель M шэкель шэкеля шэкелю шэкель шэкелем шэкелі шэкелі шэкеляў шэкелям шэкелі шэкелямі шэкелях
        рупія F рупія рупіі рупіі рупію рупіяй рупіі рупіі рупій рупіям рупіі рупіямі рупіях
        тысяча F тысяча тысячы тысячы тысячу тысячай тысячы тысячы тысяч тысячам тысячы тысячамі тысячах
        мільён M мільён мільёна мільёну мільён мільёнам мільёне мільёны мільёнаў мільёнам мільёны мільёнамі мільёнах
        мільярд M мільярд мільярда мільярду мільярд мільярдам мільярдзе мільярды мільярдаў мільярдам мільярды мільярдамі мільярдах
        трыльён M трыльён трыльёна трыльёну трыльён трыльёнам трыльёне трыльёны трыльёнаў трыльёнам трыльёны трыльёнамі трыльёнах
    """.trim().lines().associate { l -> l.trim().split(' ').let { p -> p[0] to ((if (p[1] == "I") Gender.M else Gender.valueOf(p[1])) to p.drop(2)) } }

    /** Единица: формы ([UNIT_FORMS], с приставкой «кіла», «мега»…), прилагательное перед ней («квадратны»), хвост («у гадзіну») и
     * правило «Правил»: деньги — currency, градусы и цалі — degrees, «тыс.», «млн» — abbrev, «%» — numbers, прочее — units. */
    class Measure(lemma: String, prefix: String = "", val adj: String? = null, val suffix: String = "", val rule: String = "units") {
        val gender: Gender = UNIT_FORMS.getValue(lemma).first
        val forms: List<String> = UNIT_FORMS.getValue(lemma).second.map { prefix + it }
    }

    /** Ключ — то, что стоит за числом (строчными: текст уже опущен в регистре). Регистр, который различал бы «МВт» и «мВт», здесь
     * потерян — берём частое (мегават, мілівольт, мегапаскаль); «А» (ампер) BelNormalizer заранее делает «амп», латиницу («GB»,
     * «kWh») — кириллицей. Те же единицы, что у русского unitTable, кроме опасных одной буквой и словом («па», «ком», «кл»). */
    val UNITS: Map<String, Measure> = buildMap {
        fun add(vararg keys: String, u: Measure) = keys.forEach { put(it, u) }
        val sq = "квадратн"; val cu = "кубічн"
        add("км", u = Measure("метр", "кіла")); add("м", u = Measure("метр")); add("дм", u = Measure("метр", "дэцы")); add("см", u = Measure("метр", "санты"))
        add("мм", u = Measure("метр", "мілі")); add("мкм", u = Measure("метр", "мікра")); add("нм", u = Measure("метр", "нана")); add("га", u = Measure("гектар"))
        add("м2", "м²", "кв. м", "кв.м", u = Measure("метр", adj = sq)); add("км2", "км²", "кв. км", "кв.км", u = Measure("метр", "кіла", adj = sq))
        add("см2", "см²", u = Measure("метр", "санты", adj = sq)); add("мм2", "мм²", u = Measure("метр", "мілі", adj = sq))
        add("м3", "м³", "куб. м", "куб.м", u = Measure("метр", adj = cu)); add("см3", "см³", u = Measure("метр", "санты", adj = cu))
        add("мм3", "мм³", u = Measure("метр", "мілі", adj = cu))
        add("л", u = Measure("літр")); add("мл", u = Measure("літр", "мілі")); add("дл", u = Measure("літр", "дэцы"))
        add("км/г", "км/гадз", "км/ч", u = Measure("метр", "кіла", suffix = " у гадзіну")); add("м/с", u = Measure("метр", suffix = " у секунду"))
        add("км/с", u = Measure("метр", "кіла", suffix = " у секунду")); add("м/с2", "м/с²", u = Measure("метр", suffix = " у секунду ў квадраце"))
        add("мм рт. ст", "мм рт.ст", "мм рт ст", u = Measure("метр", "мілі", suffix = " ртутнага слупа"))
        add("кг", u = Measure("грам", "кіла")); add("г", "гр", u = Measure("грам")); add("мг", u = Measure("грам", "мілі")); add("мкг", u = Measure("грам", "мікра"))
        add("т", u = Measure("тона")); add("унц", u = Measure("унцыя"))
        add("гадз", "ч", u = Measure("гадзіна")); add("мін", u = Measure("хвіліна")); add("сек", u = Measure("секунда"))
        add("мс", u = Measure("секунда", "мілі")); add("мкс", u = Measure("секунда", "мікра")); add("нс", u = Measure("секунда", "нана"))
        add("шт", u = Measure("штука")); add("чал", u = Measure("чалавек")); add("экз", u = Measure("экземпляр"))
        add("в", u = Measure("вольт")); add("мв", u = Measure("вольт", "мілі"))
        add("амп", u = Measure("ампер")); add("ма", u = Measure("ампер", "мілі")); add("мка", u = Measure("ампер", "мікра"))
        add("вт", u = Measure("ват")); add("квт", u = Measure("ват", "кіла")); add("мвт", u = Measure("ват", "мега")); add("гвт", u = Measure("ват", "гіга"))
        add("квт·г", "квт⋅г", "квт-г", "квт*г", "квтг", "квт·гадз", "квт·ч", u = Measure("гадзіна", "кілават-"))
        add("мвт·г", u = Measure("гадзіна", "мегават-")); add("вт·г", u = Measure("гадзіна", "ват-"))
        add("ма·г", "мачг", "ма-г", "маг", u = Measure("гадзіна", "міліампер-")); add("а·г", u = Measure("гадзіна", "ампер-"))
        add("гц", u = Measure("герц")); add("кгц", u = Measure("герц", "кіла")); add("мгц", u = Measure("герц", "мега")); add("ггц", u = Measure("герц", "гіга"))
        add("дж", u = Measure("джоўль")); add("кдж", u = Measure("джоўль", "кіла")); add("мдж", u = Measure("джоўль", "мега"))
        add("кпа", u = Measure("паскаль", "кіла")); add("мпа", u = Measure("паскаль", "мега")); add("гпа", u = Measure("паскаль", "гекта"))
        add("мкф", u = Measure("фарад", "мікра")); add("нф", u = Measure("фарад", "нана")); add("пф", u = Measure("фарад", "піка"))
        add("тл", u = Measure("тэсла")); add("лм", u = Measure("люмен")); add("лк", u = Measure("люкс")); add("бк", u = Measure("бекерэль"))
        add("зв", u = Measure("зіверт")); add("мзв", u = Measure("зіверт", "мілі")); add("мкзв", u = Measure("зіверт", "мікра")); add("дб", u = Measure("дэцыбел"))
        add("кал", u = Measure("калорыя")); add("ккал", u = Measure("калорыя", "кіла")); add("атм", u = Measure("атмасфера")); add("бар", u = Measure("бар"))
        add("моль", u = Measure("моль")); add("ммоль", u = Measure("моль", "мілі")); add("ммоль/л", u = Measure("моль", "мілі", suffix = " на літр"))
        add("біт", u = Measure("біт")); add("кбіт", u = Measure("біт", "кіла")); add("мбіт", u = Measure("біт", "мега")); add("гбіт", u = Measure("біт", "гіга"))
        add("байт", u = Measure("байт")); add("кб", u = Measure("байт", "кіла")); add("мб", u = Measure("байт", "мега")); add("гб", u = Measure("байт", "гіга"))
        add("тб", u = Measure("байт", "тэра"))
        add("кбіт/с", u = Measure("біт", "кіла", suffix = " у секунду")); add("мбіт/с", u = Measure("біт", "мега", suffix = " у секунду"))
        add("гбіт/с", u = Measure("біт", "гіга", suffix = " у секунду")); add("кб/с", u = Measure("байт", "кіла", suffix = " у секунду"))
        add("мб/с", u = Measure("байт", "мега", suffix = " у секунду")); add("гб/с", u = Measure("байт", "гіга", suffix = " у секунду"))
        add("аб/мін", u = Measure("абарот", suffix = " у хвіліну")); add("уд/мін", u = Measure("удар", suffix = " у хвіліну"))
        add("п.п", "п. п", u = Measure("пункт", adj = "працэнтн")); add("л. с", "л.с", u = Measure("сіла", adj = "конск"))
        add("°с", u = Measure("градус", suffix = " цэльсія", rule = "degrees")); add("°ф", u = Measure("градус", suffix = " па фарэнгейце", rule = "degrees"))
        add("°", u = Measure("градус", rule = "degrees")); add("″", u = Measure("цаля", rule = "degrees"))
        add("%", u = Measure("працэнт", rule = "numbers"))
        add("бел. руб", "бел.руб", u = Measure("рубель", adj = "беларуск", rule = "currency"))
        add("руб", "₽", u = Measure("рубель", rule = "currency")); add("кап", u = Measure("капейка", rule = "currency"))
        add("$", u = Measure("долар", rule = "currency")); add("€", u = Measure("еўра", rule = "currency")); add("£", u = Measure("фунт", rule = "currency"))
        add("₴", "грн", u = Measure("грыўня", rule = "currency")); add("₸", u = Measure("тэнге", rule = "currency")); add("¥", u = Measure("іена", rule = "currency"))
        add("₺", u = Measure("ліра", rule = "currency")); add("₩", u = Measure("вона", rule = "currency")); add("₪", u = Measure("шэкель", rule = "currency"))
        add("₹", u = Measure("рупія", rule = "currency")); add("¢", u = Measure("цэнт", rule = "currency"))
        add("тыс", u = Measure("тысяча", rule = "abbrev")); add("млн", u = Measure("мільён", rule = "abbrev")); add("млрд", u = Measure("мільярд", rule = "abbrev"))
        add("трлн", u = Measure("трыльён", rule = "abbrev"))
    }
    /** Монета валюты: две цифры после запятой — капейкі, цэнты, пенсы ([units]). */
    private val MINOR = mapOf("руб" to "кап", "₽" to "кап", "$" to "¢", "€" to "¢", "£" to "пенс", "₴" to "кап", "грн" to "кап")
    private val PENCE = Measure("пенс", rule = "currency")

    /** Единица за числом ([UNITS]) для регулярок: длинные ключи раньше коротких. Точку съедаем у сокращения на букву («км.»),
     * у знака («5 %.», «$65 000.») и у «°с», «°ф» она — конец предложения. */
    private val UNIT = "(" + UNITS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")" +
        """(?:(?<=\p{L})(?<!°с|°ф)\.)?(?![\p{L}])"""

    /** Белорусское склонение после числа: 1 — адзін, 2–4 — два, 5 — пяць. */
    fun plural(n: Long, one: String, few: String, many: String): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..14 -> many
            n10 == 1L -> one
            n10 in 2..4 -> few
            else -> many
        }
    }

    /** Женское количественное: 2 → «дзве», 21 → «дваццаць адна». */
    fun femCardinal(n: Long): String {
        if (n < 20) return FEM[n.toInt()]
        val t = TENS[(n / 10 % 10).toInt()]
        val u = n % 10
        return if (u == 0L) t else "$t ${FEM[u.toInt()]}"
    }

    // ---- склонение ----

    /** Количественное в падеже [case]: склоняется каждое слово, как в GrammarDB: «1250» в родном — «адной тысячы двухсот
     * пяцідзесяці», «25» в творном — «дваццаццю пяццю». [fem] — счётное слово женского рода («дзвюх», «адной»).
     * Винительный — неодушевлённый (как назоўны; «адну» у женского). От миллиарда — по цифрам, как [BelNormalizer.cardinal]. */
    fun cardinal(n: Long, case: Case, fem: Boolean = false): String {
        if (case == Case.NOM || case == Case.ACC || n >= 1_000_000_000L) {
            val s = if (fem) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)
            return if (case == Case.ACC && s.endsWith("адна")) s.dropLast(4) + "адну" else s
        }
        if (n < 0) return "мінус " + cardinal(-n, case, fem)
        if (n == 0L) return ZERO[case.ordinal]
        val c = case.ordinal - 1 - (if (case.ordinal > Case.ACC.ordinal) 1 else 0)   // GEN DAT INS PRE → 0..3
        val parts = ArrayList<String>()
        fun group(k: Int, noun: Array<String>, f: Boolean) {
            if (k == 0) return
            parts.add(under1000(k, c, f) + " " + noun[if (k % 10 == 1 && k % 100 != 11) c else c + 4])
        }
        group((n / 1_000_000).toInt(), MILLION, false)
        group((n / 1000 % 1000).toInt(), THOUSAND, true)
        (n % 1000).toInt().let { if (it > 0) parts.add(under1000(it, c, fem)) }
        return parts.joinToString(" ")
    }

    /** 1..999 в косвенном падеже [c] (0 родны, 1 давальны, 2 творны, 3 месны). */
    private fun under1000(n: Int, c: Int, fem: Boolean): String {
        val r = n % 100
        val words = listOfNotNull(
            HUNDREDS_CASES.getOrNull(n / 100 - 1)?.get(c),
            if (r >= 20) TENS_CASES[r / 10 - 2][c] else null,
            (if (r >= 20) r % 10 else r).takeIf { it > 0 }?.let { u -> (if (fem && u <= 2) UNITS_FEM_CASES[u - 1] else UNITS_CASES[u - 1])[c] })
        return words.joinToString(" ")
    }

    // формы GrammarDB (правапіс 2008): родны, давальны, творны, месны
    private fun table(s: String) = s.trim().lines().map { it.trim().split(' ').toTypedArray() }
    private val UNITS_CASES = table("""
        аднаго аднаму адным адным
        двух двум двума двух
        трох тром трыма трох
        чатырох чатыром чатырма чатырох
        пяці пяці пяццю пяці
        шасці шасці шасцю шасці
        сямі сямі сямю сямі
        васьмі васьмі васьмю васьмі
        дзевяці дзевяці дзевяццю дзевяці
        дзесяці дзесяці дзесяццю дзесяці
        адзінаццаці адзінаццаці адзінаццаццю адзінаццаці
        дванаццаці дванаццаці дванаццаццю дванаццаці
        трынаццаці трынаццаці трынаццаццю трынаццаці
        чатырнаццаці чатырнаццаці чатырнаццаццю чатырнаццаці
        пятнаццаці пятнаццаці пятнаццаццю пятнаццаці
        шаснаццаці шаснаццаці шаснаццаццю шаснаццаці
        сямнаццаці сямнаццаці сямнаццаццю сямнаццаці
        васямнаццаці васямнаццаці васямнаццаццю васямнаццаці
        дзевятнаццаці дзевятнаццаці дзевятнаццаццю дзевятнаццаці""")
    private val UNITS_FEM_CASES = table("""
        адной адной адной адной
        дзвюх дзвюм дзвюма дзвюх""")
    private val TENS_CASES = table("""
        дваццаці дваццаці дваццаццю дваццаці
        трыццаці трыццаці трыццаццю трыццаці
        сарака сарака сарака сарака
        пяцідзесяці пяцідзесяці пяццюдзесяццю пяцідзесяці
        шасцідзесяці шасцідзесяці шасцюдзесяццю шасцідзесяці
        сямідзесяці сямідзесяці сямюдзесяццю сямідзесяці
        васьмідзесяці васьмідзесяці васьмюдзесяццю васьмідзесяці
        дзевяноста дзевяноста дзевяноста дзевяноста""")
    private val HUNDREDS_CASES = table("""
        ста ста ста ста
        двухсот двумстам двумастамі двухстах
        трохсот тромстам трымастамі трохстах
        чатырохсот чатыромстам чатырмастамі чатырохстах
        пяцісот пяцістам пяццюстамі пяцістах
        шасцісот шасцістам шасцюстамі шасцістах
        сямісот сямістам сямюстамі сямістах
        васьмісот васьмістам васьмюстамі васьмістах
        дзевяцісот дзевяцістам дзевяцістамі дзевяцістах""")
    /** «тысяча», «мільён» после числа в косвенном падеже: адз. (родны, давальны, творны, месны), потом мн. */
    private val THOUSAND = arrayOf("тысячы", "тысячы", "тысячай", "тысячы", "тысяч", "тысячам", "тысячамі", "тысячах")
    private val MILLION = arrayOf("мільёна", "мільёну", "мільёнам", "мільёне", "мільёнаў", "мільёнам", "мільёнамі", "мільёнах")
    private val ZERO = arrayOf("нуль", "нуля", "нулю", "нуль", "нулём", "нулі")

    /** Порядковое любого рода, числа и падежа: окончание последнего слова [BelNormalizer.ordinal] по таблице GrammarDB.
     * Ударное окончание («другі», «саракавы», «нулявы») — «-ога», «-ой», «-ое»; «трэці» — мягкое («трэцяя»). */
    fun ordinal(n: Long, case: Case, g: Gender = Gender.M, plural: Boolean = false): String {
        val o = BelNormalizer.ordinal(n)
        val last = o.substringAfterLast(' ')
        val ends = when {
            last == "трэці" -> ORD_SOFT
            last.endsWith("і") -> ORD_I
            last == "саракавы" || last == "нулявы" -> ORD_STRESSED
            last.endsWith("ы") -> ORD_HARD
            else -> return o   // от миллиарда — количественное по цифрам
        }
        return o.dropLast(last.length) + last.dropLast(1) + ends[if (plural) 3 else g.ordinal][case.ordinal]
    }
    // м., ж., н., мн. × назоўны, родны, давальны, вінавальны (неадушаўлёны), творны, месны
    private val ORD_HARD = table("""
        ы ага аму ы ым ым
        ая ай ай ую ай ай
        ае ага аму ае ым ым
        ыя ых ым ыя ымі ых""")
    private val ORD_STRESSED = table("""
        ы ога ому ы ым ым
        ая ой ой ую ой ой
        ое ога ому ое ым ым
        ыя ых ым ыя ымі ых""")
    private val ORD_I = table("""
        і ога ому і ім ім
        ая ой ой ую ой ой
        ое ога ому ое ім ім
        ія іх ім ія імі іх""")
    private val ORD_SOFT = table("""
        і яга яму і ім ім
        яя яй яй юю яй яй
        яе яга яму яе ім ім
        ія іх ім ія імі іх""")

    // ---- дроби ----

    /** «3/4» → «тры чацвёртыя»: числитель — количественное ж. р., знаменатель — порядковое ж. р.: при 1 — адз. («адна
     * другая»), при 2–4 — назоўны мн. («дзве трэція»), иначе родны мн. («пяць шостых»); в косвенном падеже [case] — тот же
     * падеж, при 1 — адз. («адной другой»), иначе мн. («двух трэціх»). */
    fun fraction(a: Long, b: Long, case: Case = Case.NOM): String = agreed(a, case) { c, pl -> ordinal(b, c, Gender.F, pl) }

    /** Количественное ж. р. и слово при нём ([word] по падежу и числу) — как у дроби и «цэлая»: назоўны и вінавальны — 1 адз.,
     * 2–4 назоўны мн., 5+ родны мн.; косвенный — тот же падеж, 1 адз., иначе мн. */
    private fun agreed(n: Long, case: Case, word: (Case, Boolean) -> String): String {
        val one = n % 10 == 1L && n % 100 != 11L
        val few = n % 10 in 2..4 && n % 100 !in 12..14
        val w = when {
            case != Case.NOM && case != Case.ACC -> word(case, !one)
            one -> word(case, false)
            few -> word(Case.NOM, true)
            else -> word(Case.GEN, true)
        }
        return (if (case == Case.NOM) BelNormalizer.cardinalFem(n) else cardinal(n, case, fem = true)) + " " + w
    }

    /** Дроби словами (правило fractions). Знак-дробь один — словом: «½» → «палова», «¾» → «тры чвэрці»; при целом —
     * смешанное число. Смешанное: половина — «паўтара» (ж. р. «паўтары») и «два з паловай», прочее — «адна цэлая адна
     * трэцяя». Через слэш — когда знаменатель 2–20 и больше числителя или круглый (2, 10, 100…): «3/4», «7/2»; прочее —
     * «дваццаць чатыры дроб сем». Единица следом — родны адз.: «1/2 кг» → «адна другая кілаграма»; после половины — по
     * целой части: «2 1/2 кг» → «два з паловай кілаграмы». Не проверено носителем. */
    fun fractions(text: String, rules: Rules = Rules()): String {
        if (!rules.on("fractions")) return text
        fun unitOf(u: String) = u.lowercase().takeIf { k -> UNITS[k]?.let { rules.on(it.rule) } == true }
        var s = VULGAR_RE.replace(text) { m ->
            val c = m.groupValues[2][0]
            val (a, b) = VULGAR.getValue(c)
            if (m.groupValues[1].isNotEmpty()) "${m.groupValues[1]} $a/$b"
            else VULGAR_WORDS[c]?.let { (if (m.range.first > 0 && text[m.range.first - 1].isLetterOrDigit()) " " else "") + it +
                (if (text.getOrNull(m.range.last + 1)?.isLetter() == true) " " else "") } ?: "$a/$b"
        }
        // слово-дробь перед единицей: «палова кілаграма»
        s = VULGAR_UNIT_RE.replace(s) { m -> unitOf(m.groupValues[2])?.let { "${m.groupValues[1]} ${unitGen(it)}" } ?: m.value }
        s = MIXED_RE.replace(s) { m ->
            val n = m.groupValues[1].toLong(); val a = m.groupValues[2].toLong(); val b = m.groupValues[3].toLong()
            if (a >= b || b < 2) return@replace m.value
            val u = unitOf(m.groupValues[5])
            val fem = u?.let { UNITS.getValue(it).gender == Gender.F } == true
            val gen = u?.let { " " + unitGen(it) } ?: m.groupValues[4]
            if (a * 2 == b) {
                if (n == 1L) (if (fem) "паўтары" else "паўтара") + gen
                else (if (fem) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)) + " з паловай" +
                    (u?.let { " " + unitWord(it, n) } ?: m.groupValues[4])
            } else BelNormalizer.cardinalFem(n) + " " + plural(n, "цэлая", "цэлыя", "цэлых") + " " + fraction(a, b) + gen
        }
        return SLASH_RE.replace(s) { m ->
            val a = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val b = m.groupValues[2].toLongOrNull() ?: return@replace m.value
            val u = unitOf(m.groupValues[4])
            if (a >= 1 && (b in 2..20 && a < b || b in ROUND_DENOMS)) fraction(a, b) + (u?.let { " " + unitGen(it) } ?: m.groupValues[3])
            else BelNormalizer.cardinal(a) + " дроб " + BelNormalizer.cardinal(b) + m.groupValues[3]
        }
    }
    private val ROUND_DENOMS = setOf(2L, 10L, 100L, 1000L, 10000L)
    private val VULGAR = mapOf('½' to (1 to 2), '¼' to (1 to 4), '¾' to (3 to 4), '⅓' to (1 to 3), '⅔' to (2 to 3),
        '⅕' to (1 to 5), '⅛' to (1 to 8), '⅜' to (3 to 8), '⅝' to (5 to 8), '⅞' to (7 to 8))
    private val VULGAR_WORDS = mapOf('½' to "палова", '¼' to "чвэрць", '¾' to "тры чвэрці", '⅓' to "трэць", '⅔' to "дзве трэці")
    private val VULGAR_RE = Regex("""(?:(\d+)\s?)?([½¼¾⅓⅔⅕⅛⅜⅝⅞])""")
    private val VULGAR_UNIT_RE = Regex("""(?<!\p{L})(палова|чвэрць|чвэрці|трэць|трэці)\s+$UNIT""", RegexOption.IGNORE_CASE)
    /** «2 1/2 кг»: целое, пробел, дробь с однозначным числителем; четвёртая группа — единица с пробелом перед ней, пятая —
     * сама единица (обе могут быть пустыми). */
    private val MIXED_RE = Regex("""(?<![\d/.,])(\d{1,4})\s+(\d)/(\d{1,2})(?![\d/])(\s*$UNIT)?""", RegexOption.IGNORE_CASE)
    /** «3/4», «1/2 кг»: не кусок даты «12/05/2026» и не «1/2/3»; третья группа — единица с пробелом, четвёртая — единица. */
    private val SLASH_RE = Regex("""(?<![\d/.,])(\d{1,6})/(\d{1,6})(?![\d/]|[.,]\d)(\s*$UNIT)?""", RegexOption.IGNORE_CASE)

    // ---- род и падеж числа (правило cases) ----

    /** Морфология языка из пака (GrammarDB, `morph.bin`, tools/gdb_morph.py): род и падеж существительного за числом.
     * null — у пака её нет: падеж только по предлогу. */
    @Volatile var morph: Morph? = null

    /** Число словами в роде и падеже, как у русского cases: диапазоны «паміж 5 і 10» (творны), «з 5 да 10», «ад 5 да 10»
     * (родны), «з 5 па 10» (первое — родны); предлог с родным («да», «каля», «пасля», «без»…), месным («аб», «пры») или творным
     * («паміж», «над», «пад», «перад») — «каля 3 гадзін» → «каля трох». Род — по существительному следом. Потом по таблице
     * [morph] — по самому существительному: «1 кніга» → «адна», «2 кнігі» → «дзве» (после 2–4 — назоўны мн.), «1 акно» →
     * «адно», «з 5 сябрамі» → «з пяццю», «у 2 кнігах» → «у дзвюх», «1 кнігу» → «адну»; прилагательное между ними —
     * вместе с существительным: «2 новыя кнігі» → «дзве». Номер («пункт 2», «нумар 5») не склоняем. Не проверено носителем. */
    fun cases(text: String): String {
        var s = RANGE_BETWEEN.replace(text) { m -> m.chain(1) { cardinal(it, Case.INS) }?.let { a -> m.chain(2) { cardinal(it, Case.INS) }?.let { b -> m.value.replaceGroups(m, 1 to a, 2 to b) } } ?: m.value }
        s = RANGE_FROM_TO.replace(s) { m ->
            // «з 1941 па 1945» — годы ([abbreviations] читает их с «г.»), не количество; «з 1000 да 2000 рублёў» — количество
            if (listOf(2, 4).all { m.groupValues[it].length == 4 && m.groupValues[it].toInt() in 1000..2199 } &&
                !COUNTED_AFTER.containsMatchIn(s.substring(m.range.last + 1))) return@replace m.value
            val upTo = m.groupValues[3].equals("да", true)
            val a = cardinal(m.groupValues[2].toLong(), Case.GEN)
            if (upTo) m.value.replaceGroups(m, 2 to a, 4 to cardinal(m.groupValues[4].toLong(), Case.GEN)) else m.value.replaceGroups(m, 2 to a)
        }
        for ((re, case) in TRIGGERS) s = re.replace(s) { m ->
            val fem = nextNoun(s, m.range.last + 1)?.let { Morph.gender(it) == Gender.F } == true
            m.chain(1) { n -> cardinal(n, case, fem && n % 10 in 1..2 && n % 100 !in 11..12) }?.let { m.value.replaceGroups(m, 1 to it) } ?: m.value
        }
        val morph = morph ?: return s
        return NOUN_AFTER.replace(s) { m ->
            if (LABEL_BEHIND.containsMatchIn(s.substring(maxOf(0, m.range.first - 30), m.range.first))) return@replace m.value
            var t = morph.tags(m.groupValues[2].lowercase())
            // прилагательное — падеж и число вместе с существительным за ним: «2 новыя кнігі», «з 5 новымі кнігамі»
            if (Morph.isAdjective(t) && !Morph.isNoun(t)) {
                val tn = morph.tags(m.groupValues[3].lowercase())
                if (!Morph.isNoun(tn) || Morph.isAdjective(tn)) return@replace m.value
                t = agree(t, tn)
            }
            if (!Morph.isNoun(t) || m.groupValues[2].lowercase() in NOUN_STOP) return@replace m.value
            // месны — только после предлога: «аўтобус 21 едзе» («едзе» в таблице — месны ад «ед»), а не «дваццаці адным»
            if (!PREP_BEHIND.containsMatchIn(s.substring(maxOf(0, m.range.first - 40), m.range.first)))
                t = t and (1 shl (8 + Case.PRE.ordinal)).inv() and (1 shl (14 + Case.PRE.ordinal)).inv()
            m.chain(1) { n -> byNoun(n, t) }?.let { m.value.replaceGroups(m, 1 to it) } ?: m.value
        }
    }

    /** Форма числа перед существительным с тегами [t]: из каждого разбора — своя, одна на все — она, иначе null. После 1 —
     * адз. в падеже существительного (вінавальны: м. р. одушевлённый — «аднаго», ср. — «адно», ж. — «адну»); после 2–4 —
     * назоўны/вінавальны мн. («дзве кнігі»), родны мн. — родны («двух сяброў»); после 5+ родны мн. — назоўны («пяць кніг»);
     * косвенные мн. — тот же падеж. Не 1, а существительное только в косвенном адз. — номер, порядковое: «на 2 паверсе» →
     * «на другім», «а 7 гадзіне» → «а сёмай», «вучань 2 класа» → «другога» (счёт был бы «два паверхі»). */
    private fun byNoun(n: Long, t: Int): String? {
        val g = Morph.gender(t)
        val fem = g == Gender.F
        val one = n % 10 == 1L && n % 100 != 11L
        val few = n % 10 in 2..4 && n % 100 !in 12..14
        fun nom() = when (g) {
            Gender.F -> BelNormalizer.cardinalFem(n)
            Gender.N -> BelNormalizer.cardinal(n).let { if (it.endsWith("адзін")) it.dropLast(5) + "адно" else it }
            else -> BelNormalizer.cardinal(n)
        }
        val out = HashSet<String>()
        val sg = Morph.nounCases(t, plural = false)
        val pl = Morph.nounCases(t, plural = true)
        if (one) {
            if (g == null) return null
            if (Case.NOM in sg) return nom()
            for (c in sg) out += when {
                c == Case.ACC && g == Gender.N -> nom()
                c == Case.ACC && g == Gender.M && Morph.animate(t) -> cardinal(n, Case.GEN)
                else -> cardinal(n, c, fem)
            }
        } else if (pl.isEmpty() && g != null && sg.isNotEmpty() && sg.none { it == Case.NOM || it == Case.ACC }) {
            for (c in sg) out += ordinal(n, c, g)
        } else for (c in pl) when (c) {
            Case.NOM, Case.ACC -> if (few) out += nom()
            Case.GEN -> out += if (few) cardinal(n, Case.GEN, fem) else BelNormalizer.cardinal(n)
            else -> out += cardinal(n, c, fem)
        }
        return out.singleOrNull()
    }

    /** Теги существительного [tn], где клетки — только общие с прилагательным [ta] перед ним (одушевлённому вінавальны =
     * родны). */
    private fun agree(ta: Int, tn: Int): Int {
        var sg = ta shr when (Morph.gender(tn)) { Gender.F -> 20; Gender.N -> 26; else -> 8 } and 63
        var pl = ta shr 14 and 63
        if (Morph.animate(tn)) {
            if (sg shr Case.GEN.ordinal and 1 != 0) sg = sg or (1 shl Case.ACC.ordinal)
            if (pl shr Case.GEN.ordinal and 1 != 0) pl = pl or (1 shl Case.ACC.ordinal)
        }
        return (tn and 0xFF) or ((tn shr 8 and 63 and sg) shl 8) or ((tn shr 14 and 63 and pl) shl 14)
    }

    /** Существительное за числом (прилагательное перед ним пропускаем) — теги или null. */
    private fun nextNoun(s: String, from: Int): Int? {
        val morph = morph ?: return null
        val w = NEXT_WORDS.find(s.substring(from, minOf(s.length, from + 80))) ?: return null
        return w.groupValues.drop(1).filter { it.isNotEmpty() }.map { morph.tags(it.lowercase()) }
            .firstOrNull { Morph.isNoun(it) && !Morph.isAdjective(it) }
    }

    /** Числа цепочки в группе [g] («2-3», «3 і 5») — каждое через [f]; null — хоть одно не вышло. */
    private fun MatchResult.chain(g: Int, f: (Long) -> String?): String? {
        var bad = false
        val out = Regex("\\d+").replace(groupValues[g]) { d -> d.value.toLongOrNull()?.let(f) ?: run { bad = true; d.value } }
        return if (bad) null else out
    }
    private fun String.replaceGroups(m: MatchResult, vararg r: Pair<Int, String>): String {
        var res = this
        for ((g, v) in r.sortedByDescending { m.groups[it.first]!!.range.first }) {
            val gr = m.groups[g]!!.range
            res = res.substring(0, gr.first - m.range.first) + v + res.substring(gr.last + 1 - m.range.first)
        }
        return res
    }

    /** Цепочка чисел: «2-3», «3, 5 і 7»; запятая — только с пробелом («1,1» — дробь). */
    private const val NUM_CHAIN = """\d+(?:(?:,\s+|\s*[-–—]\s*|\s+(?:і|ці|або)\s+)\d+){0,6}"""
    /** Не часть дроби, времени, порядкового («5-га»), процента; не год («1990 года») и не день перед месяцем. */
    private val NUM_TAIL = """(?![:/\d]|[.,]\d)(?!-\p{L})(?!\s?%)(?!(?<=\d{3})\s*(?:год|года|годзе|гады|гадах|г\.)(?!\p{L}))""" +
        """(?!\s+(?:${MONTHS.joinToString("|")})(?!\p{L}))"""
    private const val AMPLIFIERS = """(?:прыблізна|амаль|усяго|толькі|ужо|яшчэ|плюс|мінус|нейкіх|цэлых)"""
    private fun trigger(alt: String) = Regex("""(?<![\p{L}-])(?:$alt)(?![\p{L}])(?:\s+$AMPLIFIERS(?![\p{L}]))*\s+($NUM_CHAIN)$NUM_TAIL""",
        RegexOption.IGNORE_CASE)
    private val TRIGGERS = listOf(
        trigger("""да|ад|каля|ля|без|для|акрамя|апроч|пасля|замест|сярод|звыш|з-за|з-пад|больш|менш|старэй|маладзей""") to Case.GEN,
        trigger("""аб|пры""") to Case.PRE,
        trigger("""паміж|між|над|пад|перад""") to Case.INS,
        trigger("""дзякуючы|насуперак|насустрач""") to Case.DAT)
    private val RANGE_BETWEEN = Regex("""(?<![\p{L}])(?:паміж|між)\s+(\d+)$NUM_TAIL\s+і\s+(\d+)$NUM_TAIL""", RegexOption.IGNORE_CASE)
    /** «з 5 да 10», «ад 5 да 10», «з 5 па 10»; между числами — одно слово («з 10 000 рублёў да 1000»). */
    private val RANGE_FROM_TO = Regex("""(?<![\p{L}])(з|са|ад)\s+(\d+)$NUM_TAIL(?:\s+\p{L}+)?\s+(да|па)\s+(\d+)$NUM_TAIL""", RegexOption.IGNORE_CASE)
    /** Слово за диапазоном — не «год», «гады», «г.»: тогда числа — количество. */
    private val COUNTED_AFTER = Regex("""^\s+(?![гГ])\p{L}""")
    private val NOUN_AFTER = Regex("""(?<![\p{L}\d,./–-])($NUM_CHAIN)$NUM_TAIL\s+(\p{L}{2,})(?![\p{L}-])(?=(?:\s+(\p{L}{2,}))?)""")
    private val NEXT_WORDS = Regex("""^\s+(\p{L}+)(?:\s+(\p{L}+))?""")
    /** Номер, а не количество: «пункт 22 правіл», «нумар 5 аўтобуса», «п. 3 артыкула». */
    private val LABEL_BEHIND = Regex("""(?<![\p{L}])(?:(?:пункт\p{L}{0,2}|артыкул\p{L}{0,2}|глав\p{L}{0,2}|нумар\p{L}{0,2}|том\p{L}{0,2}|""" +
        """раздзел\p{L}{0,2}|частк\p{L}{0,2}|параграф\p{L}{0,2}|старонк\p{L}{0,2}|кватэр\p{L}{0,2}|дом|дома|доме|корпус\p{L}{0,2})\s+|""" +
        """(?:п|пп|ст|арт|гл|ч|рыс|табл|с|д|кв)\.\s*)$""", RegexOption.IGNORE_CASE)
    /** Расхождения таблицы с узусом: «еўра» — и м., и н. р. (у нас «адно», [counted]); русизмы счёта «2 года», «3 раза». */
    private val NOUN_STOP = setOf("еўра", "года", "раза")
    /** Предлог месного (и «а» времени: «а 7 гадзіне») перед числом. */
    private val PREP_BEHIND = Regex("""(?<![\p{L}])(?:у|ў|ва|на|аб|пры|па|а|о)\s+(?:$AMPLIFIERS\s+)*$""", RegexOption.IGNORE_CASE)

    // ---- арифметика ----

    /** Знаки между числами словами (правило arith), как у русского arithmetic: «2 + 3 = 5» → «два плюс тры роўна пяць»,
     * «2×3» — «памножыць на», «÷» — «падзяліць на»; «-» — мінус только в выражении с «=» (иначе диапазон «10-15»).
     * Размер буквой: «5х10 м» → «пяць на дзесяць метраў»; «×» перед единицей — тоже размер. Сравнение: «x > 5» — «больш
     * за», «≥» — «больш або роўна». «√2» — «корань з двух», ступень: «10²» — «у квадраце», «10⁻³» — «у мінус трэцяй
     * ступені». «Ціск 120/80» — «сто дваццаць на восемдзесят», «у прапорцыі 1:4» — «адзін да чатырох». Не проверено
     * носителем. */
    fun arith(text: String): String {
        var s = DIMENSION_RE.replace(text, " на ")
        s = TIMES_SIZE_RE.replace(s, " на ")
        s = COMPARE_RE.replace(s) { m -> " " + COMPARE_WORDS.getValue(m.groupValues[1].ifEmpty { m.groupValues[2] }) + " " }
        s = SQRT_RE.replace(s) { m ->
            val n = m.groupValues[2].toLongOrNull() ?: return@replace m.value
            "корань з " + (if (m.groupValues[1].isNotEmpty()) "мінус " else "") + cardinal(n, Case.GEN)
        }
        s = PRESSURE_RE.replace(s) { m ->
            val before = s.substring(0, m.range.first)
            if (!PRESSURE_CONTEXT_RE.containsMatchIn(before) && !s.substring(m.range.last + 1).trimStart().startsWith("мм рт")) m.value
            else BelNormalizer.cardinal(m.groupValues[1].toLong()) + " на " + m.groupValues[2]
        }
        s = RATIO_RE.replace(s) { m ->
            val a = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val b = m.groupValues[2].replace(" ", "").toLongOrNull() ?: return@replace m.value
            BelNormalizer.cardinal(a) + " да " + cardinal(b, Case.GEN)
        }
        s = MINUS_BEFORE_EQUALS_RE.replace(EQUALS_MINUS_RE.replace(s, " роўна мінус "), " мінус ")
        s = ARITH_RE.replace(s) { m -> " ${ARITH_OPS.getValue(m.groupValues[1])} " }
        s = EQUALS_RE.replace(s, " роўна ")
        // степень — с основанием сразу словами: «у» после гласной — «ў» («два ў квадраце»)
        return POWER_RE.replace(s) { m ->
            val base = BelNormalizer.cardinal(m.groupValues[1].toLongOrNull() ?: return@replace m.value)
            val e = m.groupValues[3].map { SUP_DIGITS.indexOf(it) }.joinToString("").toLong()
            val minus = m.groupValues[2] == "⁻"
            base + " " + (if (base.last() in "аеёіоуыэюя") "ў" else "у") + " " + when {
                !minus && e == 2L -> "квадраце"
                !minus && e == 3L -> "кубе"
                else -> (if (minus) "мінус " else "") + ordinal(e, Case.PRE, Gender.F) + " ступені"
            }
        }
    }
    private val ARITH_OPS = mapOf("+" to "плюс", "×" to "памножыць на", "*" to "памножыць на", "÷" to "падзяліць на",
        "=" to "роўна", "^" to "у ступені", "−" to "мінус")
    private val ARITH_RE = Regex("""(?<=[\d)])\s*([+×*÷^−])\s*(?=[-−]?[\d(])""")
    /** «=» словом только рядом с числом: «x = y» остаётся как есть. */
    private val EQUALS_RE = Regex("""(?<=\d)\s*=\s*(?=[-−]?[\p{L}\d(])|(?<=[\p{L}\d)])\s*=\s*(?=[-−]?[\d(])""")
    private val MINUS_BEFORE_EQUALS_RE = Regex("""(?<=\d)\s*[-–]\s*(?=\d+(?:[.,]\d+)?\s*=)""")
    private val EQUALS_MINUS_RE = Regex("""\s*=\s*[-−–—]\s*(?=\d)""")
    /** «5х10», «3x4x5» — размер; не «0x1F» и не латиница за числом («5800X3D»). */
    private val DIMENSION_RE = Regex("""(?<=\d)(?<!(?<![\d.,])0)\s*[xXхХ]\s*(?=\d++(?:[.,]\d++)?+(?![A-Za-z](?!\d)))""")
    private val TIMES_SIZE_RE = Regex("""(?<=\d)\s*×\s*(?=\d+(?:[.,]\d+)?\s*$UNIT)""", RegexOption.IGNORE_CASE)
    /** «<» и «>» — только в пробелах: «<3» — сердце, «->» — стрелка. */
    private val COMPARE_RE = Regex("""\s*([≥≤≠])\s*(?=[-−]?\d)|(?<=[\p{L}\d)])\s+([<>])\s+(?=[-−]?\d)""")
    private val COMPARE_WORDS = mapOf("≥" to "больш або роўна", "≤" to "менш або роўна", "≠" to "не роўна", "<" to "менш за",
        ">" to "больш за")
    private val SQRT_RE = Regex("""√\s*([-−–]?)\s*(\d+)(?![\d.,])""")
    private val PRESSURE_RE = Regex("""(?<![\d/])(\d{2,3})/(\d{2,3})(?![\d/])""")
    private val PRESSURE_CONTEXT_RE = Regex("""ціск\p{L}*[^.!?]{0,40}$""", RegexOption.IGNORE_CASE)
    private val RATIO_RE = Regex("""(?<=(?:прапорцы|маштаб|суадносін|адносін)\p{L}{0,3}\s)(\d+)\s?:\s?(\d{1,3}(?: \d{3})*|\d+)(?![\d:])""",
        RegexOption.IGNORE_CASE)
    private const val SUP_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val POWER_RE = Regex("""(?<![\d.,])(\d+)([⁻⁺]?)([⁰¹²³⁴⁵⁶⁷⁸⁹]+)(?![⁰-⁹])""")

    // ---- знакі нумара і раздзелы ----

    /** «№ 5», «§ 3» — словом в падеже предлога (правило numbers, как у русского numberSigns): «пад № 7» → «пад нумарам»,
     * «у § 4» → «у параграфе»; «№№» — мн. Второй знак через «і», «або», запятую — в падеже первого. Не проверено носителем. */
    fun signs(text: String): String {
        var lastCase: Case? = null; var lastEnd = -100
        return SIGN_RE.replace(text) { m ->
            val prep = m.groupValues[1]
            val sign = m.groupValues[2]
            val chained = prep.isEmpty() && m.range.first - lastEnd < 30 && SIGN_AFTER_AND_RE.containsMatchIn(text.substring(0, m.range.first))
            val case = SIGN_PREP[prep.lowercase()] ?: (if (chained) lastCase else null) ?: Case.NOM
            lastCase = case; lastEnd = m.range.last
            val forms = SIGN_FORMS.getValue(sign[0])
            (if (prep.isEmpty()) "" else "$prep ") + forms[case.ordinal + if (sign.length == 2) 6 else 0] + " "
        }
    }
    private val SIGN_FORMS = mapOf(
        '№' to "нумар нумара нумару нумар нумарам нумары нумары нумароў нумарам нумары нумарамі нумарах".split(' '),
        '§' to "параграф параграфа параграфу параграф параграфам параграфе параграфы параграфаў параграфам параграфы параграфамі параграфах".split(' '))
    /** Предлог → падеж. «у», «на» — месны (не вінавальны), «з», «за» — творны («з нумарам»), как у русского. */
    internal val SIGN_PREP: Map<String, Case> =
        listOf("да", "ад", "без", "для", "каля", "ля", "акрамя", "апроч", "пасля", "замест", "сярод", "уздоўж", "вакол",
            "супраць", "з-пад", "з-за").associateWith { Case.GEN } +
        listOf("дзякуючы", "насустрач", "насуперак").associateWith { Case.DAT } +
        listOf("праз", "пра").associateWith { Case.ACC } +
        listOf("з", "са", "пад", "над", "перад", "за", "паміж", "між").associateWith { Case.INS } +
        listOf("у", "ў", "на", "пры", "аб", "па").associateWith { Case.PRE }
    private val SIGN_RE = Regex("""(?:(?<![\p{L}\d-])(${SIGN_PREP.keys.sortedByDescending { it.length }.joinToString("|")})\s+)?(№№|§§|№|§)\s*(?=\d)""",
        RegexOption.IGNORE_CASE)
    private val SIGN_AFTER_AND_RE = Regex("""(?:\s+(?:і|й|або|ці)|,)\s*$""")

    /** Номер раздела через точку (правило sections): после метки («п. 4.2», «арт. 20.1», «§ 4.2», «табл. 1.5», «СТБ 1.5»)
     * точку прячем меткой до дат — иначе «4.12» прочлось бы датой; [sections] после дат делает её словом «кропка», как и
     * в цепочке из трёх и больше чисел: «2.10.3» → «два кропка дзесяць кропка тры», «192.168.1.10». */
    fun sectionLabels(text: String): String = SECTION_LABEL_RE.replace(text) { m -> m.value.replace('.', SEC_DOT) }
    fun sections(text: String): String = SECTION_RE.replace(text) { m ->
        (if (m.range.first > 0 && text[m.range.first - 1].isLetter()) " " else "") +
            m.value.split('.', SEC_DOT).joinToString(" кропка ") { p -> if (p.length > 1 && p[0] == '0') BelNormalizer.digits(p) else BelNormalizer.cardinal(p.toLong()) }
    }
    /** Метка точки номера раздела ([sectionLabels]); [BelNormalizer] символов частной области в тексте не оставляет. */
    private const val SEC_DOT = ''
    private const val SECTION_LABEL = """(?:п|пп|падп|арт|ч|рыс|мал|табл|гл|разд|дад|заўв|§|№|пункт[аеуы]?|пунктам|падпункт[аеуы]?|""" +
        """артыкул[аеуы]?|артыкулам|частк[аіуі]|часткай|раздзел[аеуы]?|раздзелам|малюн(?:ак|ка|ку|кам)|рысун(?:ак|ка|ку|кам)|""" +
        """табліц[аыеу]|табліцай|глав[аыеу]|главой|параграф[аеуы]?|параграфам|дадатак|дадатк[аеуу]|СТБ|ДАСТ|ГОСТ|ТКП)"""
    private val SECTION_LABEL_RE = Regex("""(?<=(?<![\p{L}\d])$SECTION_LABEL\.?\s{0,2})\d+(?:\.\d+)+(?!\.?\d|[.,]\d)""",
        RegexOption.IGNORE_CASE)
    private val SECTION_RE = Regex("""(?<![\d.])\d+(?:\d+)+|(?<![\d.])\d+(?:\.\d+){2,}(?!\.?\d)""")

    // ---- даты ----

    /** 12.05.2026 → «дванаццатага мая дзве тысячы дваццаць шостага года»; 3.09 → «трэцяга верасня».
     * Без года — только «дд.мм» через точку: «1.5 кг», «1/2 шклянкі», «24/7» — не даты, их читают числа.
     * Первая группа у всех трёх — предлог перед датой ([DATE_PREP]), падеж дня по нему, как у русского: «на/па/пра/праз/за» —
     * вінавальны ніякага роду («на 1 верасня» → «на першае верасня», «з 1 па 5 мая» → «з першага па пятае мая»), «перад» —
     * творны («перад першым верасня»), иначе родны. Не проверено носителем. Цифровые даты — правило dates, месяц словом и
     * диапазон дней — day_month. */
    fun dates(text: String, rules: Rules = Rules()): String {
        fun date(m: MatchResult, dRaw: String, moRaw: String, yearRaw: String): String {
            val d = dRaw.toInt(); val mo = moRaw.toInt()
            if (d !in 1..31 || mo !in 1..12) return m.value
            val prep = m.groupValues[1]
            val day = when (prep.trim().lowercase()) {
                "" -> BelNormalizer.ordinal(d.toLong(), BelNormalizer.Form.GEN)
                "перад" -> BelNormalizer.ordinal(d.toLong(), BelNormalizer.Form.LOC)   // творны м./н. совпадает с месным
                else -> neuter(d.toLong())
            }
            val sb = StringBuilder(prep).append(day)
            sb.append(' ').append(MONTHS[mo - 1])
            if (yearRaw.isNotEmpty()) {
                val y0 = yearRaw.toLong()
                // двузначный год: «99» — 1999, «20» — 2020
                val y = if (yearRaw.length <= 2) (if (y0 > 30) 1900 else 2000) + y0 else y0
                sb.append(' ').append(BelNormalizer.ordinal(y, BelNormalizer.Form.GEN)).append(" года")
            }
            return sb.toString()
        }
        var s = text
        if (rules.on("dates")) {
            s = DATE.replace(s) { m -> date(m, m.groupValues[2], m.groupValues[4], m.groupValues[5]) }
            s = ISO_DATE.replace(s) { m -> date(m, m.groupValues[4], m.groupValues[3], m.groupValues[2]) }
            s = DAY_MONTH.replace(s) { m -> date(m, m.groupValues[2], m.groupValues[3], "") }
        }
        if (!rules.on("day_month")) return s
        // первый день диапазона — тем же родительным: «15-16 мая» → «пятнаццатага — шаснаццатага мая», «з 1 па 5 мая»
        s = DAY_RANGE.replace(s) { m ->
            val d = m.groupValues[1].toLong()
            if (d !in 1..31) m.value
            else BelNormalizer.ordinal(d, BelNormalizer.Form.GEN) + m.groupValues[2].ifEmpty { " — " }
        }
        // месяц словом: «3 мая 2026 г.» → «трэцяга мая … шостага года», как у цифровой даты
        return DAY_MONTH_WORD.replace(s) { m ->
            date(m, m.groupValues[2], (MONTHS.indexOf(m.groupValues[3].lowercase()) + 1).toString(), m.groupValues[4])
        }
    }

    // ---- время ----

    /** 21:30 → «дваццаць адна гадзіна трыццаць хвілін» (часы словами, потом минуты; 9:00 → «дзевяць гадзін роўна»).
     * Через точку — только после предлога ([TIME_DOT]): «а 19.00», «у 7.30»; без него «10.05» — дата, «1.5» — дробь. */
    fun times(text: String): String {
        // через точку — раньше: «па» смотрит на цифры времени перед ним («з 9:00 па 18.00»)
        val s = TIME_DOT.replace(text) { m ->
            val p = m.groupValues[1].lowercase()
            // «з/да…» перед «дд.мм» с месяцем 01–12 — скорее дата («з 12.05 па 15.05»); «а», «у», «ў» — всегда время
            if (p != "а" && p != "у" && p != "ў" && m.groupValues[4].toInt() in 1..12) return@replace m.value
            time(m.groupValues[3], m.groupValues[4])?.let { m.groupValues[1] + m.groupValues[2] + it } ?: m.value
        }
        // «21:30:15» — с секундами, раньше «21:30»: иначе оставалось «…хвілін:пятнаццаць»
        return TIME.replace(TIME_SEC.replace(s) { m ->
            val sec = m.groupValues[3].toLong()
            time(m.groupValues[1], m.groupValues[2])?.let { t ->
                if (sec == 0L) t else t.removeSuffix(" роўна") + " " + femCardinal(sec) + " " + plural(sec, "секунда", "секунды", "секунд")
            }?.takeIf { sec < 60 } ?: m.value
        }) { m -> time(m.groupValues[1], m.groupValues[2]) ?: m.value }
    }

    private fun time(hRaw: String, miRaw: String): String? {
        val h = hRaw.toIntOrNull() ?: return null
        val mi = miRaw.toIntOrNull() ?: return null
        if (h !in 0..23 || mi !in 0..59) return null
        val hb = StringBuilder(femCardinal(h.toLong()))
        hb.append(' ').append(plural(h.toLong(), "гадзіна", "гадзіны", "гадзін"))
        if (mi > 0) {
            hb.append(' ').append(femCardinal(mi.toLong()))
            hb.append(' ').append(plural(mi.toLong(), "хвіліна", "хвіліны", "хвілін"))
        } else {
            hb.append(' ').append("роўна")
        }
        return hb.toString()
    }

    // ---- единицы и валюты ----

    /** Правило «Правил» для единицы ([UNITS]); те же ключи, что у русского нормализатора. */
    fun unitKey(u: String) = UNITS[u]?.rule ?: "units"
    private fun unit(key: String) = UNITS[key] ?: PENCE.takeIf { key == "пенс" } ?: UNITS.getValue(key)

    /** Единица словами в падеже [c], в числе [pl]: прилагательное, слово, хвост («у» после гласной — «ў»). */
    private fun unitPhrase(u: Measure, c: Case, pl: Boolean): String {
        val noun = u.forms[c.ordinal + if (pl) 6 else 0]
        val head = (u.adj?.let { adjForm(it, c, u.gender, pl) + " " } ?: "") + noun
        return head + if (u.suffix.startsWith(" у ") && head.last() in "аеёіоуыэюя") " ў" + u.suffix.drop(2) else u.suffix
    }
    /** Прилагательное по основе («квадратн», «конск») — окончания как у порядковых ([ORD_HARD]; «к» — мягкое «і»). */
    private fun adjForm(stem: String, c: Case, g: Gender, pl: Boolean) =
        stem + (if (stem.endsWith("к")) ADJ_VELAR else ORD_HARD)[if (pl) 3 else g.ordinal][c.ordinal]
    private val ADJ_VELAR = table("""
        і ага аму і ім ім
        ая ай ай ую ай ай
        ае ага аму ае ім ім
        ія іх ім ія імі іх""")
    /** Единица после дроби — родны адз.: «1,5 кг» → «… кілаграма», «1/2 кг», «палова кілаграма». */
    private fun unitGen(key: String) = unitPhrase(unit(key), Case.GEN, false)
    /** Единица после целого [n] без числа: «паўтара», «два з паловай кілаграмы». */
    private fun unitWord(key: String, n: Long) = unit(key).let { u ->
        val one = n % 10 == 1L && n % 100 != 11L
        val few = n % 10 in 2..4 && n % 100 !in 12..14
        unitPhrase(u, if (one || few) Case.NOM else Case.GEN, !one)
    }

    /** Число и единица в падеже [case] (null — назоўны: 1 — адз., 2–4 — назоўны мн., 5+ — родны мн.; косвенный: 1 — адз., иначе
     * мн. того же падежа): «дзве тоны» (ж. р.), «адно еўра» (н. р.), «да пяці кіламетраў», «пры дваццаці градусах». */
    private fun counted(n: Long, key: String, case: Case? = null): String {
        val u = unit(key)
        val c = case ?: Case.NOM
        val fem = u.gender == Gender.F
        val num = if (c == Case.NOM || c == Case.ACC) (if (fem) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)).let {
            when {
                u.gender == Gender.N && it.endsWith("адзін") -> it.dropLast(5) + "адно"
                c == Case.ACC && it.endsWith("адна") -> it.dropLast(4) + "адну"
                else -> it
            }
        } else cardinal(n, c, fem)
        val one = n % 10 == 1L && n % 100 != 11L
        val word = if (c == Case.NOM || c == Case.ACC) unitWord(key, n).takeUnless { one && c == Case.ACC } ?: unitPhrase(u, Case.ACC, false)
            else unitPhrase(u, c, !one)
        return "$num $word"
    }

    /** «3 км» → «тры кіламетры», «5 %» → «пяць працэнтаў», «2 руб.» → «два рублі», «2 т» → «дзве тоны»;
     * «1,5 кг» → «адзін коска пяць кілаграма» ([decimal]). Валюта перед числом — как после: «$65 000» → «… долараў».
     * Деньги с двумя цифрами после запятой — с монетой, как у русского: «1,20 руб.» → «адзін рубель дваццаць капеек»,
     * «$1.50» → «адзін долар пяцьдзясят цэнтаў»; нуль монет не читаем, одна цифра («2,5 €», «1,5 млн руб.») — дробь.
     * Единица после «тыс.», «млн», «млрд» — родны мн.: «1,2 тыс. км» → «… тысячы кіламетраў». Предлог перед числом задаёт
     * падеж, как у русского units: «да 5 км» → «да пяці кіламетраў», «пры 20 °C» → «пры дваццаці градусах цэльсія», «у 5 км» →
     * «у пяці кіламетрах» (у времени «у» — вінавальны: «у 2 гадз.» как было). Каждая единица — под своим правилом ([unitKey]),
     * перенос валюты вперёд — под currency. Угол с минутами и секундами — правило degrees: «55°45′» → «пяцьдзясят пяць
     * градусаў сорак пяць мінут» (мінута вугла, не хвіліна). */
    fun units(text: String, rules: Rules = Rules()): String {
        val src = (if (rules.on("currency")) currencyAfter(text) else text).let { if (rules.on("degrees")) angles(it) else it }
        return MULT_UNIT.replace(NUMBER_UNIT.replace(src) { m ->
            val raw = m.groupValues[1].replace(" ", "").replace(" ", "")
            val n = raw.toLongOrNull() ?: return@replace m.value
            val key = m.groupValues[3].lowercase()
            if (UNITS[key]?.takeIf { rules.on(it.rule) } == null) return@replace m.value
            val frac = m.groupValues[2]
            val case = UNIT_PREP.find(src.substring(maxOf(0, m.range.first - 40), m.range.first))?.groupValues?.get(1)?.lowercase()
                ?.let { p -> if ((p == "у" || p == "ў") && key in TIME_UNITS) null else UNIT_PREP_CASE[p] }
            val minor = MINOR[key]?.takeIf { frac.length == 2 }
            if (minor != null) return@replace counted(n, key, case) + frac.toLong().let { if (it == 0L) "" else " " + counted(it, minor, case) }
            if (frac == "5") half(n, key, case)?.let { return@replace it }
            if (frac.isNotEmpty()) return@replace "${decimal(raw, frac, case)} ${unitGen(key)}"
            counted(n, key, case)
        }) { m ->
            val k = m.groupValues[2].lowercase()
            UNITS[k]?.takeIf { rules.on(it.rule) }?.let { "${m.groupValues[1]} ${unitPhrase(it, Case.GEN, true)}" } ?: m.value
        }
    }
    private val TIME_UNITS = setOf("гадз", "ч", "мін", "сек", "мс", "мкс", "нс")
    /** Предлог перед числом с единицей → падеж ([units]). «з» — нет: «з 5 кг» и родны, и творны. */
    private val UNIT_PREP_CASE: Map<String, Case> =
        listOf("да", "ад", "каля", "ля", "без", "для", "акрамя", "апроч", "пасля", "замест", "сярод", "звыш").associateWith { Case.GEN } +
        listOf("у", "ў", "аб", "пры").associateWith { Case.PRE } +
        listOf("паміж", "над", "пад", "перад").associateWith { Case.INS } +
        listOf("дзякуючы", "насуперак").associateWith { Case.DAT }
    private val UNIT_PREP = Regex("""(?<![\p{L}-])(${UNIT_PREP_CASE.keys.joinToString("|")})\s+(?:$AMPLIFIERS\s+)*(?:мінус\s+|плюс\s+)?$""",
        RegexOption.IGNORE_CASE)

    private fun angles(text: String) = ANGLE.replace(text) { m ->
        val (deg, min, sec) = m.destructured
        counted(deg.toLong(), "°") + " " + femCardinal(min.toLong()) + " " + plural(min.toLong(), "мінута", "мінуты", "мінут") +
            (if (sec.isEmpty()) "" else " " + femCardinal(sec.toLong()) + " " + plural(sec.toLong(), "секунда", "секунды", "секунд"))
    }
    /** «55°45′», «45°30′15″», «42°30'»: ASCII-кавычка секунд — только после минут. */
    private val ANGLE = Regex("""(?<![\d.,])(\d{1,3})\s*°\s*(\d{1,2})\s*[′'](?:\s*(\d{1,2})\s*[″"])?""")

    /** «$5» → «5 $» ([CURRENCY_BEFORE]) с пробелом впереди: «US$5» — не «у-эспяць», «$1,$2» — число не за запятой
     * ([NUMBER_UNIT]). После дефиса или тире за цифрой — без пробела: «$5-$10» — не «долараў- дзесяць». */
    private fun currencyAfter(text: String) = CURRENCY_BEFORE.replace(text) { m ->
        val i = m.range.first
        (if (i > 1 && text[i - 1] in "-–—" && text[i - 2].isDigit()) "" else " ") + m.groupValues[2] + " " + m.groupValues[1]
    }

    /** Десятичная дробь, как у русского: «3,14» → «тры цэлыя чатырнаццаць сотых», «1,05» → «адна цэлая пяць сотых», «0,5» →
     * «нуль цэлых пяць дзясятых»; знаменатель — по числу знаков («дзясятых», «сотых», «тысячных», «стотысячных»). В падеже
     * [case]: «каля 1,5» → «каля адной цэлай пяці дзясятых». Знаков больше восьми — по цифрам через «коска». Не проверено
     * носителем. */
    fun decimal(intRaw: String, fracRaw: String, case: Case? = null): String {
        val n = intRaw.toLongOrNull()
        val a = fracRaw.toLongOrNull()
        if (n == null || a == null || fracRaw.length > 8)
            return (n?.let { BelNormalizer.cardinal(it) } ?: BelNormalizer.digits(intRaw)) + " коска " + BelNormalizer.group(fracRaw)
        val c = case ?: Case.NOM
        var b = 1L
        repeat(fracRaw.length) { b *= 10 }
        return agreed(n, c) { cc, pl -> adjForm("цэл", cc, Gender.F, pl) } + " " + fraction(a, b, c)
    }
    /** Падеж дроби без единицы по предлогу перед ней (как у единиц, [UNIT_PREP]); «у», «ў», «на», «праз», «за» — вінавальны:
     * «у 1,5 раза», «на 2,5 працэнта». */
    internal fun decimalCase(text: String, start: Int): Case? =
        DECIMAL_PREP.find(text.substring(maxOf(0, start - 40), start))?.groupValues?.get(1)?.lowercase()
            ?.let { UNIT_PREP_CASE[it]?.takeIf { _ -> it != "у" && it != "ў" } ?: Case.ACC }
    private val DECIMAL_PREP = Regex("""(?<![\p{L}-])(${(UNIT_PREP_CASE.keys + listOf("на", "праз", "за")).joinToString("|")})\s+(?:$AMPLIFIERS\s+)*(?:мінус\s+|плюс\s+)?$""",
        RegexOption.IGNORE_CASE)

    /** «1,5 млн» → «паўтара мільёна», «2,5 тыс.» → «дзве з паловай тысячы», «каля 2,5 тыс.» → «каля дзвюх з паловай тысяч» —
     * у разрядов, как у русского halfScales; «21,5 млн» — дробью. Падежи после «паўтара» не проверены носителем. */
    private fun half(n: Long, key: String, case: Case?): String? {
        if (key !in HALF_SCALES || n == 0L || n % 10 == 1L && n != 1L && n % 100 != 11L) return null
        val u = unit(key)
        val fem = u.gender == Gender.F
        val c = case ?: Case.NOM
        val nom = c == Case.NOM || c == Case.ACC
        if (n == 1L) return (if (fem) "паўтары" else "паўтара") + " " + unitPhrase(u, if (nom) Case.GEN else c, !nom)
        return (if (nom) (if (fem) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)) else cardinal(n, c, fem)) + " з паловай " +
            if (nom) unitWord(key, n) else unitPhrase(u, c, true)
    }
    private val HALF_SCALES = setOf("тыс", "млн", "млрд", "трлн")

    // ---- порядковые ----

    /** «1-га верасня» → «першага верасня», «5-ы клас» → «пяты клас», «у 5-м класе» → «у пятым класе», «1990-я» →
     * «тысяча дзевяцьсот дзевяностыя». Форма — по концу суффикса: «-га» родны (м./н.), «-ы/-і» (-гі, -ці, -шы) назоўны
     * м., «-м» месны/творны м.; у десятилетия (10–90 и четырёхзначное на 0) «-я» — назоўны мн. («васьмідзясятыя»), «-х» —
     * родны/месны мн. («у 1990-х» → «у тысяча дзевяцьсот дзевяностых»). С «-я» десятилетие без «гады»/«гг.» за ним —
     * только четырёхзначное, кроме 1000: «10-я кніга» не трогаем, «у 2000-я» — «двухтысячныя», 1000 — ж. р. («1000-я
     * старонка» → «тысячная»). «2-х»…«4-х» — количественное: «двух», «трох», «чатырох» (род не знаем, «дзвюх» не
     * бывает). Падежные суффиксы количественного, как русское «5-ти»: «-ці», «-мі» — родны (он же давальны и месны у 5 и
     * больше: «да 5-ці» → «да пяці», «7-мі» → «сямі»), «-цю», «-мю», «-ма» — творны («5-цю» → «пяццю», «2-ма» → «двума»);
     * «3-ці» — порядковое «трэці», «1990-мі» — десятилетие («дзевяностымі»). «-й» — русский суффикс порядкового:
     * назоўны м. («2-й» → «другі»). Прочее («5-я» — «пятая»?, «5-х») не трогаем. Не проверено носителем. */
    fun ordinals(text: String, rules: Rules = Rules()): String = ORD_SUFFIX.replace(text) { m ->
        val raw = m.groupValues[1]
        val n = raw.toLongOrNull() ?: return@replace m.value
        val sfx = m.groupValues[2].lowercase()
        val decade = n % 10 == 0L && (raw.length == 2 || raw.length == 4)
        val case = when {
            sfx == "х" && n in 2..4 || sfx == "мі" && !(decade && n >= 20) -> Case.GEN
            sfx == "ці" && !BelNormalizer.ordinal(n).endsWith("ці") -> Case.GEN
            sfx == "цю" || sfx == "мю" || sfx == "ма" -> Case.INS
            else -> null
        }
        // падеж количественного — правило gen_suffix, остальные суффиксы — numbers
        if (!rules.on(if (case != null) "gen_suffix" else "numbers")) return@replace m.value
        if (case != null) return@replace cardinal(n, case)
        val thousand = n == 1000L
        // «-й» перед существительным ж. р. — косвенный ж. р.: «на 5-й старонцы» → «пятай» (без морфологии — м. р., «2-й» → «другі»)
        // «а 7-й» — время: «а сёмай» (гадзіне)
        val femNext = sfx == "й" && (nextNoun(text, m.range.last + 1)?.let { Morph.gender(it) == Gender.F } == true ||
            AT_TIME_BEHIND.containsMatchIn(text.substring(maxOf(0, m.range.first - 4), m.range.first)))
        // «у 90-я» — десятилетие и без «гады», если за ним не слово: «90-я старонка» — «дзевяностая»
        val decadeAlone = raw.length == 2 && n >= 20 && !WORD_AHEAD.containsMatchIn(text.substring(m.range.last + 1))
        when {
            sfx.endsWith("га") -> BelNormalizer.ordinal(n, BelNormalizer.Form.GEN)
            sfx in setOf("му", "аму", "ому", "яму") -> ordinal(n, Case.DAT)
            sfx.endsWith("м") -> BelNormalizer.ordinal(n, BelNormalizer.Form.LOC)
            sfx == "мі" -> gender(n, "ымі", emptyMap())   // десятилетие; до «-і»: иначе «дзевяносты»
            femNext || sfx in setOf("ай", "ой", "яй") -> ordinal(n, Case.GEN, Gender.F)   // родны, давальны, творны, месны ж. р. — одна форма
            sfx.endsWith("ы") || sfx.endsWith("і") || sfx == "й" -> BelNormalizer.ordinal(n)
            sfx == "я" && decade && (raw.length == 4 && !thousand || m.groupValues[3].isNotEmpty() || decadeAlone) -> ordinal(n, Case.NOM, plural = true)
            sfx == "я" -> ordinal(n, Case.NOM, Gender.F)   // «5-я кніга» → «пятая», «1000-я» → «тысячная»
            sfx in setOf("е", "ае", "яе") -> ordinal(n, Case.NOM, Gender.N)
            sfx in setOf("ю", "ую", "юю") -> ordinal(n, Case.ACC, Gender.F)
            sfx == "х" && decade -> gender(n, "ых", emptyMap())
            sfx in setOf("х", "ых", "іх") -> ordinal(n, Case.GEN, plural = true)   // «у 5-х класах» → «пятых»
            else -> m.value
        }
    }
    private val WORD_AHEAD = Regex("""^\s+\p{L}""")
    private val AT_TIME_BEHIND = Regex("""(?<![\p{L}])а\s+$""", RegexOption.IGNORE_CASE)

    /** Сложное прилагательное с числом (правило numbers, как у русского compounds): «5-гадовы» → «пяцігадовы», «100-гадовы»
     * → «стагадовы», «1-пакаёвая» → «аднапакаёвая», «2–3-пакаёвая» → «двух-трохпакаёвая». Основа — родны склон, как в GrammarDB
     * («пяцідзесяцігадовы», «двухсотгадовы», «стапавярховы»). Слово — от пяти букв: «5-тым», «2-гім» — суффиксы порядкового. */
    fun compounds(text: String): String = COMPOUND_RE.replace(text) { m ->
        fun stem(n: Int) = BelNormalizer.compoundStem(n, hundred = "ста", one = "адна")
        val a = m.groupValues[1].toInt()
        (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { stem(a) + "-" + stem(it.toInt()) } ?: stem(a)) + m.groupValues[3]
    }
    private val COMPOUND_RE = Regex("""(?<![\d.,-])(\d{1,3})(?:\s*[-–—]\s*(\d{1,3}))?-([а-яёіў]{2,}(?:ы|і|ая|яя|ае|яе|ыя|ія|ага|яга|аму|яму|ым|ім|ай|яй|ую|юю|ых|іх|ымі|імі))(?!\p{L})""",
        RegexOption.IGNORE_CASE)

    /** Номер главы, часткі, кнігі, раздзела — порядковое в роде и падеже слова (правило numbers, как у русского chapterOrdinals):
     * «Глава 5» → «глава пятая», «у главе 10» → «у дзясятай», «раздзел 3» → «раздзел трэці». За номером счётное слово в
     * родным мн. («кнігі 5 аўтараў») — счёт, не трогаем. */
    fun chapters(text: String) = CHAPTER_RE.replace(text) { m ->
        val (g, case) = CHAPTER_FORMS[m.groupValues[1].lowercase()] ?: return@replace m.value
        val next = NEXT_WORDS.find(text.substring(m.range.last + 1, minOf(text.length, m.range.last + 40)))?.groupValues?.get(1)
        if (next != null && morph?.tags(next.lowercase())?.let { Morph.isNoun(it) && Case.GEN in Morph.nounCases(it, plural = true) } == true)
            return@replace m.value
        m.groupValues[1] + " " + ordinal(m.groupValues[2].toLong(), case, g)
    }
    private val CHAPTER_FORMS: Map<String, Pair<Gender, Case>> = buildMap {
        fun add(g: Gender, vararg forms: String) = forms.forEachIndexed { i, f -> putIfAbsent(f, g to Case.values()[i]) }
        add(Gender.F, "глава", "главы", "главе", "главу", "главой", "главе")
        add(Gender.F, "частка", "часткі", "частцы", "частку", "часткай", "частцы")
        add(Gender.F, "кніга", "кнігі", "кнізе", "кнігу", "кнігай", "кнізе")
        add(Gender.M, "раздзел", "раздзела", "раздзелу", "раздзел", "раздзелам", "раздзеле")
    }
    private val CHAPTER_RE = Regex("""(?<![\p{L}])(${CHAPTER_FORMS.keys.sortedByDescending { it.length }.joinToString("|")})\s+(\d{1,3})""" +
        """(?![\d.,:/]|\s*[-–—]\s*\d|-\p{L})""", RegexOption.IGNORE_CASE)

    /** Пункты списка после двоеточия (правило numbers, как у русского listsAndOrdinals): «Прычыны: 1) голад; 2) холад» →
     * «першае, голад; другое, холад». Скобка у номера без двоеточия во фразе — не пункт. */
    fun lists(text: String) = LIST_ITEM_RE.replace(text) { m ->
        if (':' in text.substring(0, m.range.first).split('.', '!', '?', '…').last()) ordinal(m.groupValues[1].toLong(), Case.NOM, Gender.N) + ","
        else m.value
    }
    private val LIST_ITEM_RE = Regex("""(?<=:\s?|[;,]\s|\sі\s)(\d{1,2})\)(?=\s)""")
    /** Родны 1..19 и десятков — основы сложных слов ([BelNormalizer] compoundStem: «двухтысячны», «дваццаціпяці…»). */
    internal val CARD_GEN = UNITS_CASES.map { it[0] } to TENS_CASES.map { it[0] }

    /** Римское число I–XXX перед «стагоддзе», «век» — порядковым в падеже существительного: «XX стагоддзе» →
     * «дваццатае стагоддзе», «XX стагоддзя» → «дваццатага», «у XX стагоддзі» → «у дваццатым». Кириллические «Х», «І»
     * тоже: так часто набирают. «ст.», «стст.» — тоже стагоддзе, падеж по предлогу, как у года: «у XX ст.» → «у
     * дваццатым стагоддзі», «XIX–XX стст.» → «дзевятнаццатае — дваццатае стагоддзі». После имени с заглавной — порядковое
     * имени: «Пётр I» → «пётр першы», «Кацярына II» → «… другая». Остальную латиницу не трогаем. Средний род, «ст.»,
     * род по имени — не проверено носителем. Век — правило roman, после имени — roman_name. */
    fun roman(text: String, rules: Rules = Rules()): String {
        var s = ROMAN.replace(text) { m ->
            // кириллическое «І» одно — союз: «І веку не хапіла»
            if (m.groupValues[1] == "І" || !rules.on("roman")) return@replace m.value
            val n = romanValue(m.groupValues[1])
            val noun = m.groupValues[2]
            val word = when (noun.lowercase()) {
                "стагоддзе" -> neuter(n)
                "век" -> BelNormalizer.ordinal(n)
                "стагоддзя", "века" -> BelNormalizer.ordinal(n, BelNormalizer.Form.GEN)
                else -> BelNormalizer.ordinal(n, BelNormalizer.Form.LOC)   // стагоддзі, стагоддзем, веку, веце, векам
            }
            "$word $noun"
        }
        val st = s
        s = ROMAN_ST.replace(st) { m ->
            val nums = listOf(m.groupValues[2], m.groupValues[4]).filter { it.isNotEmpty() }
            if (nums == listOf("І") || !rules.on("roman")) return@replace m.value
            // без предлога I–IV — скорее ступень («ордэн … II ст.», «Дыплом I ст.»): не трогаем; с «н. э.» за ним — век
            // («Курган III ст. да н. э.»)
            if (m.groupValues[1].isEmpty() && m.groupValues[7].isEmpty() && nums.all { romanValue(it) <= 4 }) return@replace m.value
            // «ад XV да XVII ст.» — оба родным, «стагоддзя» адно
            val upTo = m.groupValues[3].trim().lowercase() == "да"
            val form = if (upTo) BelNormalizer.Form.GEN else when (m.groupValues[1].trim().lowercase()) {
                "у", "ў" -> BelNormalizer.Form.LOC
                "з", "да", "ад" -> BelNormalizer.Form.GEN
                else -> null
            }
            val many = nums.size > 1 && !upTo || m.groupValues[5].length > 2
            val noun = when (form) {
                BelNormalizer.Form.LOC -> if (many) "стагоддзях" else "стагоддзі"
                BelNormalizer.Form.GEN -> if (many) "стагоддзяў" else "стагоддзя"
                else -> if (many) "стагоддзі" else "стагоддзе"
            }
            // точка сокращения в конце предложения остаётся, как у [BelNormalizer] ABBR_END_RE; перед заглавной после
            // одного предлога в начале фразы («У XX ст. Беларусь…») — середина, как «У 1941 г. Германія»
            val mid = m.groupValues[1].isNotEmpty() && m.groupValues[6].isNotBlank() &&
                st.substring(0, m.range.first).none(Char::isLetter)
            m.groupValues[1] + nums.joinToString(if (upTo) " да " else " — ") { r ->
                romanValue(r).let { if (form == null) neuter(it) else BelNormalizer.ordinal(it, form) }
            } + " " + noun + (if (m.groups[6] != null && !mid) " ." else "")
        }
        // ponytail: род по имени: ж. р. — только [FEM_NAMES] (и латиница на «a»), прочее на -а/-я — родны м. р. («Указ
        // Пятра I» — «першага»), согласная — назоўны м. р. («Карл II» — «другі»), прочее не трогаем. Предлог в начале
        // фразы («Пасля II сусветнай») — не имя. Одна буква — только I/І после кириллического имени: «Дзень Х», «Літара V»,
        // «Then I», «Model X» не трогаем. Кириллическая «І» перед точкой — инициал («Шамякін І. П.»), перед словом с
        // заглавной — союз («Мінск І Гомель»), перед строчной — римское («Пётр І заснаваў»); латинская «I» перед точкой —
        // римское («Пётр I.»)
        return ROMAN_NAME.replace(s) { m ->
            val name = m.groupValues[1]
            val r = m.groupValues[2]
            val next = m.groupValues[3]
            if (name in NOT_NAMES || !rules.on("roman_name")) return@replace m.value
            if (r.length == 1 && (r[0] !in "IІ" || name.last().code < 128 ||
                    r == "І" && (next == "." || next.lastOrNull()?.isUpperCase() == true)))
                return@replace m.value
            val last = name.last().lowercaseChar()
            val word = when {
                name.replace(BelNormalizer.APOSTROPHE, "'") in FEM_NAMES || last == 'a' -> gender(romanValue(r), "ая", FEMININE)
                last == 'а' || last == 'я' -> BelNormalizer.ordinal(romanValue(r), BelNormalizer.Form.GEN)
                last in "еёіоуыэюeiouy" -> return@replace m.value
                else -> BelNormalizer.ordinal(romanValue(r))
            }
            "$name $word"
        }
    }
    /** Женские имена (назоўны) перед римским; «Частка», «Кніга», «Фаза», «Ліга»… — не имена, но так нумеруют. Список и
     * формы не проверены носителем. */
    private val FEM_NAMES = setOf("Кацярына", "Лізавета", "Елізавета", "Ганна", "Марыя", "Ядвіга", "Бона", "Софія", "Соф'я",
        "Ізабела", "Ізабэла", "Жанна", "Вікторыя", "Іаана", "Мар'я", "Крысціна", "Маргарыта", "Тэрэза", "Уршуля", "Барбара",
        "Клара", "Рагнеда", "Elizabeth",
        "Частка", "Кніга", "Глава", "Фаза", "Серыя", "Група", "Катэгорыя", "Зона", "Ліга")
    /** Предлоги с заглавной в начале фразы — не имя перед римским; однобуквенные «У», «З» [ROMAN_NAME] и так не ловит.
     * «Літара І ў…» — сама буква. */
    private val NOT_NAMES = setOf("Пасля", "Да", "Ад", "Падчас", "На", "За", "Пры", "Пра", "Па", "Каля", "Для", "Без", "Праз",
        "Літара")

    internal fun romanValue(r: String): Long {
        var n = 0; var prev = 0
        for (c in r.reversed()) {
            val v = when (c) { 'I', 'І' -> 1; 'V' -> 5; else -> 10 }
            if (v < prev) n -= v else { n += v; prev = v }
        }
        return n.toLong()
    }

    /** Порядковое другого рода или числа из мужского: меняется окончание последнего слова («дваццаць першае»); «другі»,
     * «трэці» на «-і» заменой окончания не выводятся — [special]. */
    private fun gender(n: Long, end: String, special: Map<String, String>): String = BelNormalizer.ordinal(n).let { o ->
        val last = o.substringAfterLast(' ')
        o.dropLast(last.length) + (special[last] ?: (last.removeSuffix("ы") + end))
    }
    private fun neuter(n: Long) = gender(n, "ае", NEUTER)
    private val NEUTER = mapOf("другі" to "другое", "трэці" to "трэцяе")
    private val FEMININE = mapOf("другі" to "другая", "трэці" to "трэцяя")

    // ---- сокращения ----

    /** «1999 г.» → «1999 год», «у 1812 г.» → «у 1812 годзе», «90-я гг.» → «90-я гады», «5 тыс.» уже разобрано единицами.
     * «5 г.», «500 г. мукі» — граммы (units): год — 1000–2199 или три цифры после «у/ў» ([YEAR_G]). */
    fun abbreviations(text: String): String {
        var s = text
        // «з 1941 па 1945 г.» — оба года порядковым, как у русского «с 1941-го по 1945-й год»: первый родным, «па …» —
        // вінавальны с «год», «да …» — родны с «года» (как «з першага па пятае мая»). Без «г.»/«года» — не трогаем: «з 1000
        // да 2000 рублёў». Не проверено носителем
        s = YEAR_FROM_TO.replace(s) { m ->
            val (prep, y1, g1, mid, y2, g2) = m.destructured
            if (g1.isEmpty() && g2.isEmpty() || listOf(y1, y2).any { it.toInt() !in 1000..2199 }) return@replace m.value
            val upTo = mid.lowercase() == "да"
            "$prep ${BelNormalizer.ordinal(y1.toLong(), BelNormalizer.Form.GEN)} $mid " +
                (if (upTo) BelNormalizer.ordinal(y2.toLong(), BelNormalizer.Form.GEN) + " года"
                else BelNormalizer.ordinal(y2.toLong()) + " год")
        }
        s = YEAR_G.replace(s) { m ->
            val prep = m.groupValues[1]
            val raw = m.groupValues[2]
            val p = prep.trim().lowercase()
            // год — 1000–2199 или три цифры после «у/ў» («у 988 г.»); «з 500 г.», «па 500 г. мукі» — граммы
            if (if (raw.length < 4) p != "у" && p != "ў" else raw.toInt() !in 1000..2199) return@replace m.value
            // падеж по предлогу: у/ў — месны, з/да/ад — родны, па — вінавальны; не проверено носителем
            val word = when (p) { "у", "ў" -> "годзе"; "з", "да", "ад" -> "года"; else -> "год" }
            "$prep$raw $word"
        }
        // диапазон лет: оба года порядковым, падеж по слову, у «гг.» — по предлогу, как у одного года: «у 1941–1945 гг.» →
        // «у тысяча дзевяцьсот сорак першым — … пятым гадах». Не проверено носителем
        s = YEARS_RANGE.replace(s) { m ->
            val (y1, y2) = listOf(m.groupValues[2].toLong(), m.groupValues[3].toLong())
            if (y1 !in 1000..2199 || y2 !in 1000..2199) return@replace m.value
            val noun = m.groupValues[4].lowercase()
            val p = m.groupValues[1].trim().lowercase()
            val form = when {
                noun == "гадах" || noun.startsWith("гг") && (p == "у" || p == "ў") -> BelNormalizer.Form.LOC
                noun.startsWith("гг") && (p == "з" || p == "да" || p == "ад") -> BelNormalizer.Form.GEN
                else -> BelNormalizer.Form.NOM
            }
            val word = if (!noun.startsWith("гг")) noun else when (form) {
                BelNormalizer.Form.LOC -> "гадах"; BelNormalizer.Form.GEN -> "гадоў"; else -> "гады"
            }
            "${m.groupValues[1]}${BelNormalizer.ordinal(y1, form)} — ${BelNormalizer.ordinal(y2, form)} $word"
        }
        s = GG_LOC.replace(s, "$1гадах")
        s = GG_GEN.replace(s, "$1гадоў")
        s = GG.replace(s, "гады")
        return s
    }
    /** «у 1990-х гг.» — [ordinals] уже дали «у … дзевяностых»: месны, «гадах»; не проверено носителем. */
    private val GG_LOC = Regex("""(?<!\p{L})([уў]\s+(?:\p{L}+\s+){0,3}\p{L}+ых\s+)гг\.?""", RegexOption.IGNORE_CASE)
    /** «-х» без «у/ў» ([GG_LOC] уже прошёл) — родны: «пачатак 1990-х гг.» → «… дзевяностых гадоў»; не проверено носителем. */
    private val GG_GEN = Regex("""(?<!\p{L})(\p{L}+ых\s+)гг\.?""", RegexOption.IGNORE_CASE)

    /** Сокращение без числа: «руб.» после «5 тыс.» или «г.» перед названием города. Только отдельным словом:
     * «капуста», «рубель» и уже развёрнутое единицами «два рублі» не трогаем. «г.» — правило abbrev, единицы — правилом
     * единицы ([unitKey]): иначе с выключенной валютой «5 руб.» читалось бы здесь «пяць рублёў». */
    fun standalone(text: String, rules: Rules = Rules()): String =
        STANDALONE.fold(text) { s, (re, rep, key) -> if (rules.on(key)) re.replace(s, rep) else s }.let { s ->
            // «тыс. км» без числа — единица за развёрнутым «тысяч», как в [units]
            MULT_UNIT.replace(s) { m ->
                UNITS[m.groupValues[2].lowercase()]?.takeIf { rules.on(it.rule) }?.let { "${m.groupValues[1]} ${unitPhrase(it, Case.GEN, true)}" } ?: m.value
            }
        }

    private val STANDALONE = listOf(
        // за числом «г.» — граммы или год, не горад: «100 г.» с выключенными единицами остаётся как есть
        Triple(Regex("""(?<!\p{L})(?<!\d\s?)г\.(?=\s)"""), "горад ", "abbrev"),
        // «цана ў руб.» — месны мн.; не проверено носителем
        Triple(Regex("""(?<!\p{L})([уў])\s+руб\.?(?!\p{L})"""), "$1 рублях", unitKey("руб")),
        Triple(Regex("""(?<![\p{L}\d])руб\.?(?!\p{L})"""), "рублёў", unitKey("руб")),
        Triple(Regex("""(?<![\p{L}\d])кап\.?(?!\p{L})"""), "капеек", unitKey("кап")),
        Triple(Regex("""(?<![\p{L}\d])тыс\.(?!\p{L})"""), "тысяч", unitKey("тыс")),
        Triple(Regex("""(?<![\p{L}\d])млрд(?!\p{L})"""), "мільярдаў", unitKey("млрд")),
        Triple(Regex("""(?<![\p{L}\d])млн(?!\p{L})"""), "мільёнаў", unitKey("млн")),
        Triple(Regex("""(?<![\p{L}\d])шт\.(?!\p{L})"""), "штук", unitKey("шт")))

    /** С годом: «12.05.2026», «12/05/26»; не после цифры и знака — иначе кусок большого числа. Хвост «г.» съедаем:
     * «года» дата уже говорит, иначе «…года горад». */
    private val DATE = Regex("""(?<![\d.,])$DATE_PREP(\d{1,2})([./])(\d{1,2})\3(\d{4}|\d{2})(?!\d)(?:\s*г\.)?""")
    /** ISO «2026-05-12»: год, месяц, день. */
    private val ISO_DATE = Regex("""(?<![\d.,-])$DATE_PREP(\d{4})-(\d{2})-(\d{2})(?![\d-])""")
    /** Предлог, меняющий падеж дня ([dates]), — первая группа у [DATE], [DAY_MONTH], [DAY_MONTH_WORD]. «у» не берём: с датой
     * по-белорусски не ставится, падеж не ясен. */
    private const val DATE_PREP = """(?:(?<!\p{L})((?:па|на|праз?|за|перад)\s+))?"""
    /** «3 мая», «3 мая 2026 г.», «3 мая 2026 года»: год съедаем вместе с «г.»/«года» — date() скажет «года» сам. */
    private val DAY_MONTH_WORD = Regex("""(?<![\d.,])$DATE_PREP(\d{1,2})\s+(${MONTHS.joinToString("|")})(?!\p{L})(?:\s+(\d{4})(?!\d)(?:\s*г\.|\s+года(?!\p{L}))?)?""",
        RegexOption.IGNORE_CASE)
    /** Первый день диапазона перед «д месяц»: «15-16 мая», «з 1 па 5 мая», «ад 5 да 10 мая». */
    private val DAY_RANGE = Regex("""(?<![\d.,])(\d{1,2})(?:\s*[-–—]\s*|(\s+(?:па|да)\s+))(?=\d{1,2}\s+(?:${MONTHS.joinToString("|")})(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    /** Число с суффиксом порядкового ([ordinals]); не кусок «8-029» и не «1.5-га». Третья группа — «гады»/«гг.» за ним.
     * До девяти цифр: «у 10000-м годзе» — «у дзесяцітысячным», а не «дзесяць тысяч-м». */
    private val ORD_SUFFIX = Regex("""(?<![\d.,-])(\d{1,9})-(га|ы|і|гі|ці|шы|ты|мы|м|ым|ім|я|х|ых|іх|й|ай|ой|яй|мі|цю|мю|ма|му|аму|ому|яму|е|ае|яе|ю|ую|юю)(?!\p{L})(?=(\s+(?:гады|гг)(?!\p{L}))?)""",
        RegexOption.IGNORE_CASE)
    /** I–XXX латиницей или кириллицей: непустое. */
    private const val ROMAN_NUM = """(?=[IVXІХ])[XХ]{0,3}(?:[IІ][XХ]|[IІ]V|V?[IІ]{0,3})"""
    /** Римское перед «стагоддзе», «век» ([roman]), не часть слова. */
    private val ROMAN = Regex("""(?<![\p{L}\d])($ROMAN_NUM)\s+([Сс]тагоддз(?:е|я|і|ем)|[Вв](?:ек|ека|еку|еце|екам))(?!\p{L})""")
    /** «у XX ст.», «XIX–XX стст.», «ад XV да XVII ст.»: предлог, одно или два римских (третья группа — тире или «да»),
     * «ст»/«стст»; шестая группа — за точкой конец предложения или заглавная, седьмая — «н. э.» или «да н. э.». */
    private val ROMAN_ST = Regex("""(?:(?<!\p{L})((?:[Уу]|[Ўў]|[Зз]|[Дд]а|[Аа]д)\s+))?(?<![\p{L}\d])($ROMAN_NUM)(?:(\s*[–—-]\s*|\s+[Дд]а\s+)($ROMAN_NUM))?""" +
        """\s+(стст|ст)\.(?=(\s*$|\s+\p{Lu})?)(?=(\s+(?:да\s+)?н\.\s*э\.)?)""")
    /** Римское после слова с заглавной: «Пётр I», «Кацярына II», «Карл XII». Слово — заглавная и строчные (и апостроф:
     * «Соф'я»): «ЯНКА І» не имя. Не перед «ст.» и диапазоном с «ст.» («Дыплом I ст.», «Гэта I–IV ст.» — [ROMAN_ST]) и не
     * перед вторым инициалом («Шамякін I. П.»); третья группа — точка или слово за ним. */
    private val ROMAN_NAME = Regex("""(?<![\p{L}\d])(\p{Lu}\p{Ll}+(?:['’]\p{Ll}+)?)\s+($ROMAN_NUM)""" +
        """(?![\p{L}\d]|(?:\s*[–—-]\s*$ROMAN_NUM)?\s+(?:стст|ст)\.|\.\s*\p{Lu}\.)(?=(\.|\s+\p{L})?)""")
    /** Без года: «3.09» — месяц двумя цифрами, не дробь «1.5», не кусок «1.10.2» и не число с единицей «1.05 кг».
     * «г.» после «дд.мм» — горад, а не граммы: «15.06 г. Гомель». */
    private val DAY_MONTH = Regex("""(?<![\d.,])$DATE_PREP(\d{1,2})\.(\d{2})(?!\d|[.,]\d)(?!\s*(?!г\.)$UNIT)""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")
    private val TIME_SEC = Regex("""(?<![\d:])(\d{1,2}):(\d{2}):(\d{2})(?![\d:])""")
    /** «чч.мм» после предлога ([times]); не кусок даты «у 12.05.2026» и не число с единицей «1.50 руб.», «ў 1.25 раза»,
     * «каля 1.30 мільёна». «па» с числом — чаще цена («па 2.50 за кілаграм»), время — только сразу за другим
     * («з 15.30 па 16.30», «з 9:00 па 18.00»: в [times] идёт раньше [TIME]). Слово-единица — целым словом: «а 18.30 разам»,
     * «метро», «грамадскі» — время. */
    private val TIME_DOT = Regex("""(?<!\p{L})(а|у|ў|з|да|ад|каля|пасля|перад|(?<=\d[.:]\d\d\s{1,3})па)(\s+)(\d{1,2})\.(\d{2})""" +
        """(?!\d|[.,]\d)(?!\s*(?!г\.)$UNIT)(?!\s*(?:(?:раз|тон|штук)[аыі]?|$MULT_WORD|(?:санты|мілі)?(?:метр|грам|літр)(?:а|ы|аў)?|кіла\p{L}+|""" +
        """(?:долар|працэнт|градус|гектар)(?:а|ы|аў)?|рубл(?:я|і|ёў)|капе(?:йк[аі]|ек)|еўра)(?!\p{L}))""",
        RegexOption.IGNORE_CASE)
    /** «з 1941 па 1945 г.», «ад 1941 г. да 1945 гг.», «з 1941 да 1945 года» ([abbreviations]): предлог, год, «г.» при нём,
     * «па»/«да», год, «г.»/«гг.»/«год(а)». */
    private val YEAR_FROM_TO = Regex("""(?<!\p{L})(з|са|ад)\s+(\d{4})(?!\d)(\s*г\.)?\s+(па|да)\s+(\d{4})(?!\d)(\s*(?:гг?\.|года?(?!\p{L})))?""",
        RegexOption.IGNORE_CASE)
    private val YEAR_G = Regex("""(?:(?<!\p{L})((?:у|ў|з|да|па|ад)\s+))?(?<!\d)(\d{3,4})\s*г\.""", RegexOption.IGNORE_CASE)
    /** «у 1941–1945 гг.», «1941-1945 гадах» ([abbreviations]). «гадоў» нет: «1000–2000 гадоў» — количество. */
    private val YEARS_RANGE = Regex("""(?:(?<!\p{L})((?:у|ў|з|да|ад)\s+))?(?<![\d.,])(\d{4})\s*[–—-]\s*(\d{4})\s*(гг\.?|гадах|гады)(?!\p{L})""",
        RegexOption.IGNORE_CASE)
    /** Множитель словом: «тысяча», «мільёнаў», «трыльёны». */
    private const val MULT_WORD = """тысяч[аы]?|мільён(?:а|ы|аў)?|мільярд(?:а|ы|аў)?|трыльён(?:а|ы|аў)?"""
    /** Единица за развёрнутым «тыс.», «млн», «млрд», «трлн» ([units]): «тысячы км.» — точку сокращения съедает, как у числа. */
    private val MULT_UNIT = Regex("""(?<!\p{L})($MULT_WORD)\s+$UNIT""", RegexOption.IGNORE_CASE)
    /** Валюта перед числом: «$65 000», «€ 50» → «65 000 $» для [units]; с множителем (и словом) — за ним: «$75,3 млрд.» →
     * «75,3 млрд. $», «$70 мільярдаў» → «70 мільярдаў $»; у диапазона без пробелов — в конце: «$17-18 млрд» → «17-18 млрд $»;
     * не «10 $ 20». С пробелами тире — не диапазон: «$5 — 10 штук». */
    private val CURRENCY_BEFORE = Regex("""(?<!\d\s?)([€₽\$£₴₸¥₺₩₪₹])\s*($CUR_NUM(?:[-–—]$CUR_NUM)?""" +
        """(?:\s*(?:тыс|млн|млрд|трлн)\.?(?!\p{L})|\s+(?:$MULT_WORD)(?!\p{L}))?)(?![\d.,]?\d)""")
    private const val CUR_NUM = """\d{1,9}(?:[  ]\d{3})*(?:[.,]\d+)?"""
    private val GG = Regex("""(?<!\p{L})гг\.?""", RegexOption.IGNORE_CASE)
    /** Число, дробная часть через точку или запятую ([decimal]), единица [UNIT]. */
    private val NUMBER_UNIT = Regex(
        """(?<![\d.,])(\d{1,9}(?:[ \u00A0]\d{3})*)(?:[.,](\d+))?\s*$UNIT""",
        RegexOption.IGNORE_CASE)
}
