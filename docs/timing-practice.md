# Zmanim preferences

Setup asks for location, then groups candle lighting, daytime calculation, and Shabbos ending under Zmanim preferences, one decision per page. It does not infer all timing customs from ethnic identity or prayer nusach. These are independent settings; changing the daytime calculation does not change candle lighting or Havdalah.

## Choices that affect this app

| Choice | Implementation |
| --- | --- |
| Location | Saved coordinates, local time zone, country, and locality. Search accepts cities, postal codes, and city/state or city/country phrases with or without a comma. After an empty result, it tries comma-separated qualifiers without dropping any query words; explicit regions are retained. Otherwise identical result labels include the county. One-time device location uses reverse geocoding and an automatic time-zone lookup; failure offers city search. |
| Candle lighting | Presets of 15, 18, 20, 30, and 40 minutes before sunset, or an explicit 1–90 minute lead, sent as Hebcal `b`. The local default is 18 outside Israel, 20 in Israel, 40 in Jerusalem, and 30 in Haifa or Zikhron Ya'akov. This is a provider preset, not a claim that every community uses it. |
| Daytime calculation | Gra sunrise/sunset; MGA using Hebcal's fixed 72-minute dawn/nightfall day for Shema and Shacharis; or Chabad/Baal Hatanya for Shema, Shacharis, Mincha gedola, and plag. The REST API returns each calculation; the app selects the matching fields, keeping stable clock/Tasker keys. |
| Show both | Show Gra and MGA Shema and Shacharis deadlines. Both calculations have explicit labels. This is a display choice, not a ruling that one method supersedes another. |
| Shabbos end | Nightfall (`M=on`, 8.5° / three small stars) or fixed minutes (`m`, 1–120). The primary fixed choice is 72 minutes; 42, 50, and custom intervals are under Other timing. Fixed 72 minutes is one Rabbeinu Tam calculation, not all variants of that practice. |
| Israel calendar | Inferred from the selected location and passed as `i`. No separate onboarding question: this beta automates Friday–Saturday Shabbos, not adjoining Yom Tov periods or holiday/reading displays. Hebcal forces Israel rules for the Jerusalem time zone even with `i=off`; visitor holiday overrides require a future holiday-aware integration. |

Language/transliteration, Torah reading details, optional holiday categories, and calendar output format do not change this beta's timings, so setup does not ask about them. Elevation adjustment remains off (Hebcal's default); the app does not guess a community's elevation policy from GPS altitude. MGA angle-based day variants and seasonal/angle-based Rabbeinu Tam variants are not implemented by the current REST integration. A community using those variants can supply this week's Shabbos boundary override; prayer deadlines should be checked against its own calendar.

## Community labels

The daytime and Shabbos-ending choices include brief community associations in setup and Settings. These are guidance, not exclusive community presets: Gra and Magen Avraham are both used by Ashkenazim and Sephardim; the earlier Shema deadline is a useful distinction. Some communities use MGA for Shema but Gra for tefillah. Selecting MGA here still applies the existing fixed-72-minute calculation to both deadlines; the help dialog identifies this variant and the possibility of different methods for different deadlines.

Chabad uses Baal Hatanya daytime calculations and 8.5° for Shabbos ending. The Nightfall label also mentions many Ashkenazi communities, reflecting the 8.5° approach described by Rabbi Chaim Jachter; it does not imply that all Ashkenazim, or only Ashkenazim, use it. Rabbeinu Tam is associated with many Sephardim and Chassidim, but the supported 72-minute choice is only one calculation of that practice. The ending help retains that distinction and tells users to follow their community's published times. Other permits a local fixed-minute custom. No calculation, default, or saved preference changes with these labels.

Sources for the labels and their limits:

- [Peninei Halakha: calculating the hours](https://ph.yhb.org.il/en/02-11-10/): the Gra/MGA disagreement and differing dawn/nightfall implementations; Gra is supported by authorities from multiple traditions.
- [Halacha Yomit: the proper time for Shacharit](https://halachayomit.co.il/en/default.aspx?HalachaID=2032): Rav Ovadia Yosef's approach to the Shema and tefillah deadlines illustrates why a single ethnic preset would mislead.
- [Chabad's calculation explanation](https://www.chabad.org/library/article_cdo/aid/3209349/jewish/About-Our-Zmanim-Calculations.htm): Baal Hatanya daytime hours and 8.5° Shabbos ending.
- [Rabbi Chaim Jachter: When Does Shabbat End](https://www.koltorah.org/halachah/when-does-shabbat-end-by-rabbi-chaim-jachter): overlapping community practices, the 8.5° approach, and Rabbeinu Tam variants.
- [Rabbi Anthony Manning: Late Shabbat](https://rabbimanning.com/wp-content/uploads/2018/08/The-Late-Shabbat.pdf), footnote 24: Rabbeinu Tam observance in many Chassidic and Sephardi communities, with several ways to calculate its ending time.

## Data and cache behavior

New preferences are backward-compatible DataStore keys; Room data is retained. Existing installations without the setup-complete key start with their saved location and settings. Setup completion is persisted only after saving and scheduling. No notifications or alarm previews are triggered by setup.

Settings writes and network refreshes share a mutex so an old in-flight response cannot repopulate times for a previous location or practice. Changing practice clears incompatible time events. Changing the candle/Havdalah method also clears the corresponding weekly override; changing only daytime calculations preserves boundary overrides. Each edit cancels/reschedules affected events. The app requests new data. Changing location also clears cached weather. Unchanged settings retain local data through offline refresh failures. If Hebcal omits a weekly boundary, its matching astronomical calculation is used; an unavailable 8.5° nightfall is never replaced with a fixed 50-minute guess. Polar and adjoining-holiday observance are not inferred.

## Sources

- [Hebcal Shabbat API](https://www.hebcal.com/home/197/shabbat-times-rest-api): candle-lighting lead times, independent nightfall/fixed Havdalah options, and explicit date parameters.
- [Hebcal calendar REST API](https://www.hebcal.com/home/195/jewish-calendar-rest-api): Israel/diaspora options. Live API checks also show that location/time-zone defaults can supersede `i=off`.
- [Hebcal calendar options](https://hebcal.github.io/api/core/types/CalOptions.html): regional candle-lighting defaults and Israel/diaspora schedule.
- [Hebcal zmanim API](https://www.hebcal.com/home/1663/zmanim-halachic-times-api): returned Gra, MGA, and Baal Hatanya fields; separate nightfall definitions.
- [Hebcal calculation documentation](https://hebcal.github.io/api/core/classes/Zmanim.html): MGA's fixed 72-minute day and Baal Hatanya definitions.
- [Chabad's calculation explanation](https://www.chabad.org/library/article_cdo/aid/3209349/jewish/About-Our-Zmanim-Calculations.htm): proportional hours, true sunrise/sunset for the Alter Rebbe's approach, local custom, and the precision limits of published times.
- [Hebcal location API](https://www.hebcal.com/home/4912/specifying-a-location-for-jewish-calendar-apis): required coordinates/time zone and optional elevation policy.
- [Open-Meteo geocoding API](https://open-meteo.com/en/docs/geocoding-api): postal-code lookup, comma-separated state/country qualifiers and administrative-area fields. Unqualified multiword city names are tried intact first; qualifier fallbacks are bounded to four additional requests and stop at the first match. Network errors do not trigger alternate queries.

These sources document distinct accepted practices. Setup lets the user follow their community rather than making a universal halachic ruling.
