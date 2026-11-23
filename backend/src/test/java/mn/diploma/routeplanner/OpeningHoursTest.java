package mn.diploma.routeplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDateTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import mn.diploma.routeplanner.place.OpeningHours;
import mn.diploma.routeplanner.place.OpeningHours.Status;

class OpeningHoursTest {

    // 2026-11-16 бол даваа гараг
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mo-Fr 09:00-18:00; Sa 10:00-16:00; Su off | 2026-11-16T10:00 | OPEN",
            "Mo-Fr 09:00-18:00; Sa 10:00-16:00; Su off | 2026-11-16T18:00 | CLOSED",
            "Mo-Fr 09:00-18:00; Sa 10:00-16:00; Su off | 2026-11-22T12:00 | CLOSED",
            "24/7                                      | 2026-11-22T03:00 | OPEN",
            "10:00-22:00                               | 2026-11-22T21:59 | OPEN",
            "Fr-Sa 18:00-02:00                         | 2026-11-21T01:30 | OPEN",
            "Fr-Sa 18:00-02:00                         | 2026-11-20T01:30 | CLOSED",
            "Mo,We 10:00-14:00,15:00-19:00             | 2026-11-18T14:30 | CLOSED",
            "Mo-Su 09:00-21:00; PH off                 | 2026-11-18T09:00 | OPEN",
            "Su-Tu 09:00-10:00                         | 2026-11-17T09:30 | OPEN",
            "Jan-Mar Mo-Fr 09:00-18:00                 | 2026-11-18T10:00 | UNKNOWN",
    })
    void status(String raw, String time, Status expected) {
        assertEquals(expected, OpeningHours.statusAt(raw.trim(), LocalDateTime.parse(time)));
    }

    @Test
    void closesTooSoon() {
        assertFalse(OpeningHours.possiblyOpenFor("Mo-Su 09:00-21:00", LocalDateTime.parse("2026-11-18T20:45"), 30));
    }
}
