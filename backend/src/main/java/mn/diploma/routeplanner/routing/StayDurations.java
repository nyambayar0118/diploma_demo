package mn.diploma.routeplanner.routing;

import java.util.Map;

/** Typical minutes spent at a place of each subcategory; used for arrival times and the time budget. */
final class StayDurations {

    static final int DEFAULT_MINUTES = 45;

    private static final Map<String, Integer> MINUTES = Map.ofEntries(
            Map.entry("cafe", 45), Map.entry("restaurant", 75), Map.entry("fast_food", 30),
            Map.entry("pub_bar", 90), Map.entry("dessert", 30), Map.entry("cinema", 130),
            Map.entry("game_center", 75), Map.entry("karaoke", 120), Map.entry("nightclub", 150),
            Map.entry("theme_park", 120), Map.entry("zoo", 120), Map.entry("museum", 90),
            Map.entry("gallery", 60), Map.entry("theatre", 150), Map.entry("library", 60),
            Map.entry("historic", 30), Map.entry("worship", 30), Map.entry("park", 45),
            Map.entry("scenic", 30), Map.entry("picnic", 90), Map.entry("fitness", 75),
            Map.entry("sports_hall", 90), Map.entry("swimming", 90), Map.entry("ice_rink", 75),
            Map.entry("mall", 90), Map.entry("market", 60), Map.entry("bookshop", 40),
            Map.entry("fashion", 45), Map.entry("gift", 30), Map.entry("spa", 90));

    private StayDurations() {
    }

    static int of(String categoryCode) {
        return MINUTES.getOrDefault(categoryCode, DEFAULT_MINUTES);
    }
}
