package mn.diploma.routeplanner.recommendation;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/** Өдрийн цагийн хэсгүүд; category_time_priors болон машин сургалтын шинж чанарт ашиглана. */
public enum TimeOfDay {
    MORNING, MIDDAY, AFTERNOON, EVENING, NIGHT;

    public static TimeOfDay of(LocalDateTime t) {
        int h = t.getHour();
        if (h >= 6 && h < 11) return MORNING;
        if (h >= 11 && h < 14) return MIDDAY;
        if (h >= 14 && h < 18) return AFTERNOON;
        if (h >= 18 && h < 22) return EVENING;
        return NIGHT;
    }

    public static String dayType(LocalDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY ? "WEEKEND" : "WEEKDAY";
    }
}
