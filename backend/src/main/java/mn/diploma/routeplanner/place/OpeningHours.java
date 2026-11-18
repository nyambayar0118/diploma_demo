package mn.diploma.routeplanner.place;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluator for the common subset of the OSM opening_hours syntax:
 * "24/7", "Mo-Fr 09:00-18:00; Sa 10:00-16:00; Su off", "Mo,We 10:00-14:00,15:00-19:00", "10:00-22:00",
 * overnight ranges ("Fr-Sa 18:00-02:00") and public-holiday rules (ignored).
 * Anything more complex (months, week numbers, sunrise, ...) gives {@link Status#UNKNOWN}, and the place
 * is then treated as possibly open rather than wrongly excluded.
 */
public final class OpeningHours {

    public enum Status { OPEN, CLOSED, UNKNOWN }

    private static final String[] DAY_CODES = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
    private static final Pattern RULE = Pattern.compile(
            "^(?:(?<days>(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?"
                    + "(?:,(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:-(?:Mo|Tu|We|Th|Fr|Sa|Su))?)*)\\s+)?"
                    + "(?<times>off|closed|\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}(?:\\s*,\\s*\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2})*)$");
    private static final Pattern TIME_RANGE = Pattern.compile("(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})");

    /** Opening intervals per weekday in minutes since midnight [start, end). */
    private final Map<DayOfWeek, List<int[]>> week;

    private OpeningHours(Map<DayOfWeek, List<int[]>> week) {
        this.week = week;
    }

    /** Parses the value; returns null when the syntax is outside the supported subset. */
    public static OpeningHours parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.equals("24/7")) {
            Map<DayOfWeek, List<int[]>> all = new EnumMap<>(DayOfWeek.class);
            for (DayOfWeek d : DayOfWeek.values()) {
                all.put(d, new ArrayList<>(List.of(new int[] {0, 24 * 60})));
            }
            return new OpeningHours(all);
        }
        Map<DayOfWeek, List<int[]>> week = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, List<int[]>> spill = new EnumMap<>(DayOfWeek.class);
        for (String part : value.split(";")) {
            String rule = part.trim();
            if (rule.isEmpty() || rule.startsWith("PH") || rule.startsWith("SH")) {
                continue; // public / school holidays: no calendar data, ignore
            }
            Matcher m = RULE.matcher(rule);
            if (!m.matches()) {
                return null;
            }
            List<DayOfWeek> days = m.group("days") == null ? List.of(DayOfWeek.values()) : days(m.group("days"));
            String times = m.group("times");
            for (DayOfWeek day : days) {
                // A later rule replaces earlier rules for the days it names (OSM semantics)
                List<int[]> intervals = new ArrayList<>();
                week.put(day, intervals);
                spill.remove(day.plus(1));
                if (times.equals("off") || times.equals("closed")) {
                    continue;
                }
                Matcher t = TIME_RANGE.matcher(times);
                while (t.find()) {
                    int start = Integer.parseInt(t.group(1)) * 60 + Integer.parseInt(t.group(2));
                    int end = Integer.parseInt(t.group(3)) * 60 + Integer.parseInt(t.group(4));
                    if (start > 24 * 60 || end > 48 * 60) {
                        return null;
                    }
                    if (end > start && end <= 24 * 60) {
                        intervals.add(new int[] {start, end});
                    } else {
                        // Past midnight, e.g. 18:00-02:00 or 18:00-26:00
                        int endNextDay = end > 24 * 60 ? end - 24 * 60 : end;
                        intervals.add(new int[] {start, 24 * 60});
                        spill.computeIfAbsent(day.plus(1), d -> new ArrayList<>()).add(new int[] {0, endNextDay});
                    }
                }
            }
        }
        spill.forEach((day, intervals) -> week.computeIfAbsent(day, d -> new ArrayList<>()).addAll(intervals));
        return new OpeningHours(week);
    }

    private static List<DayOfWeek> days(String spec) {
        List<DayOfWeek> result = new ArrayList<>();
        for (String piece : spec.split(",")) {
            String[] range = piece.split("-");
            int from = index(range[0]);
            int to = range.length > 1 ? index(range[1]) : from;
            for (int i = from; ; i = (i + 1) % 7) {
                result.add(DayOfWeek.of(i + 1));
                if (i == to) {
                    break;
                }
            }
        }
        return result;
    }

    private static int index(String code) {
        for (int i = 0; i < DAY_CODES.length; i++) {
            if (DAY_CODES[i].equals(code)) {
                return i;
            }
        }
        throw new IllegalArgumentException(code);
    }

    public boolean isOpenAt(LocalDateTime time) {
        int minute = time.toLocalTime().toSecondOfDay() / 60;
        for (int[] interval : week.getOrDefault(time.getDayOfWeek(), List.of())) {
            if (minute >= interval[0] && minute < interval[1]) {
                return true;
            }
        }
        return false;
    }

    /** OPEN / CLOSED at the given time, UNKNOWN when the value is missing or not supported. */
    public static Status statusAt(String raw, LocalDateTime time) {
        OpeningHours oh = parse(raw);
        if (oh == null) {
            return Status.UNKNOWN;
        }
        return oh.isOpenAt(time) ? Status.OPEN : Status.CLOSED;
    }

    /** True unless we know the place is closed at {@code time} or closes within {@code minutes} after it. */
    public static boolean possiblyOpenFor(String raw, LocalDateTime time, int minutes) {
        OpeningHours oh = parse(raw);
        if (oh == null) {
            return true;
        }
        return oh.isOpenAt(time) && oh.isOpenAt(time.plusMinutes(Math.max(0, minutes - 1)));
    }
}
