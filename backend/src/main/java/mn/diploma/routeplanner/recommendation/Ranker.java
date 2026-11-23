package mn.diploma.routeplanner.recommendation;

import java.util.List;

/**
 * Санал болголтын 2-р шат: эрэмбэлэгч. Хэрэгжүүлэлт: одоогоор {@link RuleBasedRanker}; машин сургалтын шатанд
 * LightGBM-д суурилсан MlRanker, сервис цагтаа хариулахгүй бол дүрэмд суурилсан эрэмбэлэлт рүү шилжинэ (МГШ06).
 */
public interface Ranker {

    /** Нэр дэвшигч бүрд {@code score} онооно (их байх тусам сайн). */
    void score(List<Candidate> candidates, RecommendationContext ctx);

    String name();

    /** Багцын бүртгэлд загварын хувилбар; дүрэмд суурилсан эрэмбэлэлтэд null. */
    default String modelVersion() {
        return null;
    }
}
