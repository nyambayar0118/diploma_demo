package mn.diploma.routeplanner.recommendation;

/** A place that passed the rule-based filters, with its features and (after ranking) its score. */
public class Candidate {

    final long placeId;
    final String name;
    final String nameLatin;
    final long categoryId;
    final String category;
    final String categoryName;
    final String mainCategory;
    final double lat;
    final double lng;
    final double distanceM;
    final double interestWeight;
    final double timePrior;
    int travelMinutes;
    double score;
    boolean exploration;

    Candidate(long placeId, String name, String nameLatin, long categoryId, String category, String categoryName,
              String mainCategory, double lat, double lng, double distanceM, double interestWeight, double timePrior) {
        this.placeId = placeId;
        this.name = name;
        this.nameLatin = nameLatin;
        this.categoryId = categoryId;
        this.category = category;
        this.categoryName = categoryName;
        this.mainCategory = mainCategory;
        this.lat = lat;
        this.lng = lng;
        this.distanceM = distanceM;
        this.interestWeight = interestWeight;
        this.timePrior = timePrior;
    }
}
