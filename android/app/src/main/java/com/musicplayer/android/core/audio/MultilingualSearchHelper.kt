package com.musicplayer.android.core.audio

/**
 * Pure-Kotlin helper that improves cross-language and typo-tolerant music search
 * without changing local Room database schemas or breaking native Ukrainian queries.
 *
 * Responsibilities:
 * 1. Detect accidental Cyrillic QWERTY typing for English queries (e.g. "ьуефддшсф" -> "metallica").
 * 2. Expand Ukrainian music moods, genres, instruments, languages, and common foreign artist
 *    names into English search variants for sparse-result online fallbacks and local scoring.
 * 3. Provide clean Cyrillic-to-Latin phonetic transliteration for fallback queries.
 */
object MultilingualSearchHelper {

    private val cyrillicRegex = Regex("[\\p{InCyrillic}]")
    private val uaVowels = setOf('а', 'е', 'є', 'и', 'і', 'ї', 'о', 'у', 'ю', 'я', 'ы', 'э', 'ё')
    private val enVowelsStrict = setOf('a', 'e', 'i', 'o', 'u')
    private val enVowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
    private val enConsonantClusterRegex = Regex("[bcdfghjklmnpqrstvwxz]{5,}")
    private val cyrillicConsonantClusterRegex = Regex("[бвгґджзйклмнпрстфхцчшщ]{3,}")
    private val suspiciousOnsets = listOf("щс", "фс", "фд", "ь", "ъ", "ы")
    private val layoutMarkerLetters = setOf('ф', 'ш', 'щ', 'ь', 'г')

    private val qwertyCyrillicToLatin = mapOf(
        'й' to 'q', 'ц' to 'w', 'у' to 'e', 'к' to 'r', 'е' to 't',
        'н' to 'y', 'г' to 'u', 'ш' to 'i', 'щ' to 'o', 'з' to 'p',
        'ф' to 'a', 'і' to 's', 'ы' to 's', 'в' to 'd', 'а' to 'f',
        'п' to 'g', 'р' to 'h', 'о' to 'j', 'л' to 'k', 'д' to 'l',
        'я' to 'z', 'ч' to 'x', 'с' to 'c', 'м' to 'v', 'и' to 'b',
        'т' to 'n', 'ь' to 'm', 'ґ' to 'u',
        // Punctuation keys sometimes hit inside mixed layout phrases
        'х' to '[', 'ї' to ']', 'ъ' to ']', 'ж' to ';', 'є' to '\'', 'э' to '\'',
        'б' to ',', 'ю' to '.'
    )

    private val phoneticCyrillicToLatin = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "h", 'ґ' to "g",
        'д' to "d", 'е' to "e", 'є' to "ye", 'ж' to "zh", 'з' to "z",
        'и' to "y", 'і' to "i", 'ї' to "yi", 'й' to "i", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ь' to "", 'ю' to "yu", 'я' to "ya", 'ы' to "y", 'э' to "e",
        'ё' to "yo", 'ъ' to "", '\'' to "", '’' to ""
    )

    private val fillerStopWords = setOf(
        "для", "під", "про", "без", "на", "у", "в", "і", "й", "та", "або", "з", "із", "зі", "до"
    )

    // Multi-word and single-word artist phonetic mappings (longest match first)
    private val artistPhoneticMap = linkedMapOf(
        "імеджин драгонс" to "imagine dragons",
        "імеджін драгонс" to "imagine dragons",
        "майкл джексон" to "michael jackson",
        "фредді меркюрі" to "freddie mercury",
        "фредді мерк'юрі" to "freddie mercury",
        "фредді мерк’юрі" to "freddie mercury",
        "чаппелл роан" to "chappell roan",
        "сабріна карпентер" to "sabrina carpenter",
        "олівія родріго" to "olivia rodrigo",
        "людовіко ейнауді" to "ludovico einaudi",
        "біллі айліш" to "billie eilish",
        "билли айлиш" to "billie eilish",
        "лана дель рей" to "lana del rey",
        "тейлор свіфт" to "taylor swift",
        "арктік манкіз" to "arctic monkeys",
        "лінкін парк" to "linkin park",
        "лінкен парк" to "linkin park",
        "депеш мод" to "depeche mode",
        "пінк флойд" to "pink floyd",
        "діп перпл" to "deep purple",
        "лед зеппелін" to "led zeppelin",
        "аріана гранде" to "ariana grande",
        "джастін бібер" to "justin bieber",
        "пост мелоун" to "post malone",
        "тревіс скотт" to "travis scott",
        "кендрік ламар" to "kendrick lamar",
        "каньє вест" to "kanye west",
        "бруно марс" to "bruno mars",
        "леді гага" to "lady gaga",
        "леді ґаґа" to "lady gaga",
        "гаррі стайлз" to "harry styles",
        "дуа ліпа" to "dua lipa",
        "ед ширан" to "ed sheeran",
        "ед шіран" to "ed sheeran",
        "дафт панк" to "daft punk",
        "джус ворлд" to "juice wrld",
        "стрей кідз" to "stray kids",
        "стрей кідс" to "stray kids",
        "джо хісаїсі" to "joe hisaishi",
        "ганс ціммер" to "hans zimmer",
        "ханс ціммер" to "hans zimmer",
        "келвін гарріс" to "calvin harris",
        "кальвін харріс" to "calvin harris",
        "алан вокер" to "alan walker",
        "кріс айзек" to "chris isaak",
        "блек пінк" to "blackpink",
        "блекпінк" to "blackpink",
        "ейсі дісі" to "ac dc",
        "зе вікенд" to "the weeknd",
        "марун файв" to "maroon 5",
        "марун 5" to "maroon 5",
        "ліл піп" to "lil peep",
        "вікенд" to "the weeknd",
        "викенд" to "the weeknd",
        "металіка" to "metallica",
        "металліка" to "metallica",
        "рамштайн" to "rammstein",
        "раммштайн" to "rammstein",
        "емінем" to "eminem",
        "квін" to "queen",
        "нірвана" to "nirvana",
        "редіохед" to "radiohead",
        "редіогед" to "radiohead",
        "колдплей" to "coldplay",
        "бейонсе" to "beyonce",
        "ріанна" to "rihanna",
        "адель" to "adele",
        "шакіра" to "shakira",
        "дрейк" to "drake",
        "авічі" to "avicii",
        "евічі" to "avicii",
        "скріллекс" to "skrillex",
        "бітіес" to "bts",
        "бтс" to "bts",
        "міядзакі" to "miyazaki",
        "шопен" to "chopin",
        "моцарт" to "mozart",
        "бетховен" to "beethoven",
        "вівальді" to "vivaldi",
        "дебюссі" to "debussy",
        "сіа" to "sia"
    )

    // Multi-word music phrase overrides before token-level stems
    private val phraseOverrides = linkedMapOf(
        "драм н бейс" to "drum and bass",
        "драм енд бейс" to "drum and bass",
        "лоу фай" to "lofi",
        "ло фай" to "lofi",
        "хіп хоп" to "hip hop",
        "кей поп" to "kpop",
        "поп музика" to "pop music",
        "рок музика" to "rock music"
    )

    // Exact token dictionary (safe for short <= 4-char roots so "року" != "rock")
    private val exactMusicTokens = mapOf(
        "рок" to "rock",
        "роком" to "rock",
        "поп" to "pop",
        "попса" to "pop",
        "реп" to "rap",
        "репу" to "rap",
        "репуха" to "rap",
        "к-поп" to "kpop",
        "кпоп" to "kpop",
        "кей-поп" to "kpop",
        "кейпоп" to "kpop",
        "хіп-хоп" to "hip hop",
        "хіпхоп" to "hip hop",
        "драм-н-бейс" to "drum and bass",
        "драмнбейс" to "drum and bass",
        "лоу-фай" to "lofi",
        "лоуфай" to "lofi",
        "лофай" to "lofi",
        "регі" to "reggae",
        "хаус" to "house",
        "хаусу" to "house",
        "техно" to "techno",
        "транс" to "trance",
        "трансу" to "trance",
        "інді" to "indie",
        "чіл" to "chill",
        "чил" to "chill",
        "ніч" to "night",
        "вночі" to "night",
        "сні" to "sleep",
        "сну" to "sleep",
        "сон" to "sleep",
        "спати" to "sleep",
        "зал" to "gym",
        "залу" to "gym",
        "залі" to "gym",
        "біг" to "running",
        "бігу" to "running",
        "бігом" to "running",
        "бігти" to "running",
        "дощ" to "rain",
        "дощу" to "rain",
        "дощем" to "rain",
        "літо" to "summer",
        "влітку" to "summer",
        "зима" to "winter",
        "взимку" to "winter",
        "осінь" to "autumn",
        "восени" to "autumn",
        "весна" to "spring",
        "навесні" to "spring",
        "кач" to "banger",
        "живий" to "live",
        "жива" to "live",
        "живе" to "live",
        "живі" to "live",
        "наживо" to "live",
        "вживу" to "live",
        "лайв" to "live",
        "нові" to "new",
        "нове" to "new",
        "нова" to "new",
        "новий" to "new",
        "топ" to "top",
        "бас" to "bass",
        "баси" to "bass",
        "басів" to "bass",
        "біт" to "beats",
        "біти" to "beats",
        "бітів" to "beats",
        "мікс" to "mix",
        "мікси" to "mix",
        "міксів" to "mix",
        "хіт" to "hits",
        "хіти" to "hits",
        "хітів" to "hits",
        "трек" to "tracks",
        "треки" to "tracks",
        "треків" to "tracks",
        "душі" to "soul"
    )

    // Stem prefixes (>= 5 chars or unambiguous 4-char roots) ordered longest-first
    private val stemMusicMap = listOf(
        "інструментал" to "instrumental",
        "саундтрек" to "soundtrack",
        "фортепіано" to "piano",
        "електрон" to "electronic",
        "концентрац" to "focus",
        "альтернатив" to "alternative",
        "атмосфер" to "atmospheric",
        "бразильськ" to "brazilian",
        "американськ" to "american",
        "французьк" to "french",
        "українськ" to "ukrainian",
        "італійськ" to "italian",
        "британськ" to "british",
        "меланхол" to "melancholic",
        "агресив" to "aggressive",
        "віолончел" to "cello",
        "саксофон" to "saxophone",
        "медитац" to "meditation",
        "найкращ" to "best",
        "розслабл" to "relax",
        "тренуван" to "workout",
        "навчання" to "study",
        "синтвейв" to "synthwave",
        "сінтвейв" to "synthwave",
        "найткор" to "nightcore",
        "сповільн" to "slowed",
        "іспанськ" to "spanish",
        "японськ" to "japanese",
        "корейськ" to "korean",
        "німецьк" to "german",
        "польськ" to "polish",
        "арабськ" to "arabic",
        "турецьк" to "turkish",
        "спокійн" to "chill",
        "романт" to "romantic",
        "вечірн" to "evening",
        "ранков" to "morning",
        "плейлист" to "playlist",
        "металкор" to "metalcore",
        "акустич" to "acoustic",
        "піаніно" to "piano",
        "барабан" to "drums",
        "скрипк" to "violin",
        "гітар" to "guitar",
        "ембієнт" to "ambient",
        "ембіент" to "ambient",
        "дабстеп" to "dubstep",
        "класич" to "classical",
        "класик" to "classical",
        "опенінг" to "opening",
        "ендінг" to "ending",
        "геймінг" to "gaming",
        "фітнес" to "workout",
        "релакс" to "relax",
        "вечірк" to "party",
        "депрес" to "sad",
        "лірич" to "lyrical",
        "весел" to "happy",
        "радісн" to "happy",
        "енерг" to "energetic",
        "кантрі" to "country",
        "диско" to "disco",
        "метал" to "metal",
        "гранж" to "grunge",
        "латин" to "latin",
        "спорт" to "workout",
        "фокус" to "focus",
        "машин" to "car",
        "дорог" to "road",
        "музик" to "music",
        "музич" to "music",
        "реверб" to "reverb",
        "ремікс" to "remix",
        "аніме" to "anime",
        "драйв" to "drive",
        "фонк" to "phonk",
        "джаз" to "jazz",
        "блюз" to "blues",
        "соул" to "soul",
        "фанк" to "funk",
        "панк" to "punk",
        "фолк" to "folk",
        "кавер" to "cover",
        "пісн" to "songs",
        "пісен" to "songs",
        "сумно" to "sad",
        "сумн" to "sad",
        "нічн" to "night",
        "літн" to "summer",
        "зимов" to "winter",
        "осінн" to "autumn",
        "веснян" to "spring",
        "роков" to "rock",
        "кращ" to "best",
        "косм" to "space",
        "клуб" to "club",
        "навчан" to "study",
        "пробіж" to "running"
    )

    fun hasCyrillic(text: String): Boolean = cyrillicRegex.containsMatchIn(text)

    /**
     * Converts Cyrillic text to clean Latin phonetics suitable for cross-script search fallback.
     */
    fun transliterateToLatinPhonetic(text: String): String {
        val sb = StringBuilder(text.length * 2)
        for (ch in text.lowercase()) {
            sb.append(phoneticCyrillicToLatin[ch] ?: ch)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /**
     * If the query appears to be an English phrase typed accidentally with Cyrillic QWERTY active
     * (e.g. "ьуефддшсф" -> "metallica", "щсуфт" -> "ocean"), returns the flipped ASCII query;
     * otherwise returns null so genuine Ukrainian queries are never mangled.
     */
    fun detectAndFlipWrongLayout(query: String): String? {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty() || !hasCyrillic(trimmed)) return null

        val rawTokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (rawTokens.isEmpty()) return null

        val flippedTokens = ArrayList<String>(rawTokens.size)
        var detectedWrongToken = false

        for (token in rawTokens) {
            if (!hasCyrillic(token)) {
                flippedTokens += token
                continue
            }
            val mapped = flipTokenToAscii(token) ?: return null
            if (!isTokenMistypedEnglish(token, mapped)) return null
            detectedWrongToken = true
            flippedTokens += mapped
        }

        if (!detectedWrongToken) return null
        val joined = flippedTokens.joinToString(" ").trim()
        return joined.takeIf { it.isNotBlank() && it != trimmed }
    }

    /**
     * Translates recognized Ukrainian artist names and music mood/genre/instrument stems
     * into an English query string. Returns null if zero artist or music entries matched
     * (prevents altering native Ukrainian song/artist titles).
     */
    fun translateMusicKeywordsToEnglish(query: String): String? {
        val normalized = query.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty() || !hasCyrillic(normalized)) return null

        var working = " $normalized "
        var matchedAny = false

        for ((cyrPhrase, enPhrase) in artistPhoneticMap) {
            val tokenBoundaryPattern = Regex("(?<=\\s)${Regex.escape(cyrPhrase)}(?=\\s|[.,!?-])")
            if (tokenBoundaryPattern.containsMatchIn(working)) {
                working = working.replace(tokenBoundaryPattern, enPhrase)
                matchedAny = true
            }
        }

        for ((cyrPhrase, enPhrase) in phraseOverrides) {
            val tokenBoundaryPattern = Regex("(?<=\\s)${Regex.escape(cyrPhrase)}(?=\\s|[.,!?-])")
            if (tokenBoundaryPattern.containsMatchIn(working)) {
                working = working.replace(tokenBoundaryPattern, enPhrase)
                matchedAny = true
            }
        }

        val tokens = working.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val resultTokens = ArrayList<String>(tokens.size)

        for (rawToken in tokens) {
            val clean = rawToken.trim('.', ',', '!', '?', ';', ':')
            if (clean.isEmpty()) continue
            if (!hasCyrillic(clean)) {
                resultTokens += clean
                continue
            }
            if (clean in fillerStopWords) {
                continue
            }

            val exact = exactMusicTokens[clean]
            if (exact != null) {
                matchedAny = true
                resultTokens += exact
                continue
            }

            val stemMatch = stemMusicMap.firstOrNull { (stem, _) -> clean.startsWith(stem) }?.second
            if (stemMatch != null) {
                matchedAny = true
                resultTokens += stemMatch
                continue
            }

            // Unknown Cyrillic word kept as phonetic translit when paired with a known match
            val translit = transliterateToLatinPhonetic(clean)
            if (translit.isNotBlank()) {
                resultTokens += translit
            }
        }

        if (!matchedAny) return null
        val deduplicated = deduplicateConsecutiveWords(resultTokens.joinToString(" "))
        return deduplicated.takeIf { it.isNotBlank() && it != normalized }
    }

    /**
     * Ordered list (max 2) of non-empty fallback queries to use ONLY when a primary online search
     * returns sparse results, or to enrich sparse autocomplete suggestions.
     */
    fun buildOnlineFallbackQueries(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !hasCyrillic(trimmed)) return emptyList()

        val lowered = trimmed.lowercase()
        val seen = LinkedHashSet<String>()
        seen += lowered

        val out = ArrayList<String>(2)

        detectAndFlipWrongLayout(trimmed)?.let { flipped ->
            if (seen.add(flipped.lowercase())) out += flipped
        }

        if (out.size < 2) {
            translateMusicKeywordsToEnglish(trimmed)?.let { translated ->
                if (seen.add(translated.lowercase())) out += translated
            }
        }

        if (out.isEmpty()) {
            val translit = transliterateToLatinPhonetic(trimmed)
            if (translit.length >= 2 && seen.add(translit.lowercase())) {
                out += translit
            }
        }

        return out.take(2)
    }

    /**
     * Returns semantic/layout/phonetic English variants for scoring local device tracks.
     * Always excludes the original query itself.
     */
    fun expandLocalQueryVariants(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || !hasCyrillic(trimmed)) return emptyList()

        val seen = linkedSetOf(trimmed.lowercase())
        val variants = ArrayList<String>(2)

        detectAndFlipWrongLayout(trimmed)?.let { flipped ->
            if (seen.add(flipped.lowercase())) variants += flipped
        }

        translateMusicKeywordsToEnglish(trimmed)?.let { translated ->
            if (seen.add(translated.lowercase())) variants += translated
        }

        return variants
    }

    private fun flipTokenToAscii(token: String): String? {
        val sb = StringBuilder(token.length)
        for (ch in token) {
            if (ch in 'a'..'z' || ch in '0'..'9') {
                sb.append(ch)
            } else {
                val mapped = qwertyCyrillicToLatin[ch] ?: return null
                sb.append(mapped)
            }
        }
        return sb.toString()
    }

    private fun isTokenMistypedEnglish(cyrillicToken: String, flipped: String): Boolean {
        if (cyrillicToken.length < 3 || flipped.any { it !in 'a'..'z' }) return false
        if (!flipped.any { it in enVowels }) return false
        if (enConsonantClusterRegex.containsMatchIn(flipped)) return false
        if (flipped.endsWith("q") || Regex("q[^u]").containsMatchIn(flipped)) return false

        val firstChar = cyrillicToken.first()
        if (firstChar == 'ь' || firstChar == 'ъ' || firstChar == 'ы') return true

        val uaVowelCount = cyrillicToken.count { it in uaVowels }
        if (uaVowelCount == 0) return true

        if (suspiciousOnsets.any { cyrillicToken.startsWith(it) }) return true
        if (cyrillicConsonantClusterRegex.containsMatchIn(cyrillicToken)) return true

        val enStrictVowelCount = flipped.count { it in enVowelsStrict }
        return enStrictVowelCount > uaVowelCount && cyrillicToken.any { it in layoutMarkerLetters }
    }

    private fun deduplicateConsecutiveWords(text: String): String {
        val parts = text.trim().split(Regex("\\s+"))
        val out = ArrayList<String>(parts.size)
        for (part in parts) {
            if (part.isNotBlank() && (out.isEmpty() || out.last() != part)) {
                out += part
            }
        }
        return out.joinToString(" ")
    }
}
