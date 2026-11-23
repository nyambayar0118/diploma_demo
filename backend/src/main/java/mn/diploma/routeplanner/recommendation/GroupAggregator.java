package mn.diploma.routeplanner.recommendation;

import java.util.List;

/**
 * Нэг газарт гишүүдийн өгсөн оноог бүлгийн оноо болгон нэгтгэнэ (судалгааны бүлэг 2, Masthoff 2011).
 * app.group.aggregator-оор сонгоно (анхдагч LEAST_MISERY: аль ч гишүүнд таалагдахгүй газар сонгогдохгүй).
 */
public interface GroupAggregator {

    double aggregate(List<Double> memberScores);

    String name();

    enum Strategy implements GroupAggregator {
        /** S_avg(p) = гишүүдийн s_u(p)-ийн дундаж: нийт сэтгэл ханамж хамгийн өндөр. */
        AVERAGE {
            @Override
            public double aggregate(List<Double> s) {
                return s.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            }
        },
        /** S_lm(p) = гишүүдийн s_u(p)-ийн хамгийн бага утга (хамгийн бага сэтгэл ханамж, least misery): нэг гишүүнд огт таалагдахгүй газраас зайлсхийнэ. */
        LEAST_MISERY {
            @Override
            public double aggregate(List<Double> s) {
                return s.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            }
        };
    }
}
