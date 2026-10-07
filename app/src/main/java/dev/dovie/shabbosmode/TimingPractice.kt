package dev.dovie.shabbosmode

enum class ZmanTradition(val title: String, val detail: String) {
    GRA("Gra", "Sunrise to sunset"),
    MGA("Magen Avraham", "Dawn to nightfall · fixed 72-minute offsets"),
    CHABAD("Chabad", "Baal Hatanya"),
    BOTH("Show both", "Gra & Magen Avraham")
}

object TimingPractice {
    fun candleLead(settings: AppSettings): Int {
        if (settings.candleMinutes > 0) return settings.candleMinutes
        val city = settings.locality.ifBlank { settings.city.substringBefore(',') }.lowercase()
        val israel = settings.countryCode == "IL" ||
            (settings.countryCode.isBlank() && settings.zoneId == "Asia/Jerusalem")
        return if (!israel) 18 else when (city) {
            "jerusalem", "yerushalayim" -> 40
            "haifa", "zikhron ya‘aqov", "zikhron ya'akov", "zichron yaakov", "zikhron yaakov" -> 30
            else -> 20
        }
    }

    fun shabbatParameters(settings: AppSettings): String =
        "&b=${candleLead(settings)}&i=${if (settings.israelCalendar) "on" else "off"}" +
            if (settings.havdalahMinutes == 0) "&M=on" else "&m=${settings.havdalahMinutes}"

    // Stable event keys keep clock selections and Tasker profiles intact.
    fun zmanSources(tradition: ZmanTradition): Map<String, Pair<String, String>> {
        val shema = when (tradition) {
            ZmanTradition.MGA -> "sofZmanShmaMGA"
            ZmanTradition.CHABAD -> "sofZmanShmaBaalHatanya"
            else -> "sofZmanShma"
        }
        val tfilla = when (tradition) {
            ZmanTradition.MGA -> "sofZmanTfillaMGA"
            ZmanTradition.CHABAD -> "sofZmanTfilaBaalHatanya"
            else -> "sofZmanTfilla"
        }
        val suffix = when (tradition) {
            ZmanTradition.MGA -> "MGA"
            ZmanTradition.CHABAD -> "Baal Hatanya"
            else -> "Gra"
        }
        return linkedMapOf(
            "sunrise" to ("sunrise" to "Sunrise"),
            "sofZmanShma" to (shema to "Shema · $suffix"),
            "sofZmanTfilla" to (tfilla to "Shacharis · $suffix"),
            "chatzot" to ("chatzot" to "Chatzot"),
            "minchaGedola" to ((if (tradition == ZmanTradition.CHABAD) "minchaGedolaBaalHatanya" else "minchaGedola") to "Mincha gedola"),
            "plagHaMincha" to ((if (tradition == ZmanTradition.CHABAD) "plagHaminchaBaalHatanya" else "plagHaMincha") to "Plag hamincha"),
            "sunset" to ("sunset" to "Sunset"),
            "tzeit7083deg" to ("tzeit7083deg" to "Tzeit · 7.1°")
        ).apply {
            if (tradition == ZmanTradition.BOTH) {
                put("sofZmanShmaMGA", "sofZmanShmaMGA" to "Shema · MGA")
                put("sofZmanTfillaMGA", "sofZmanTfillaMGA" to "Shacharis · MGA")
            }
        }
    }
}
