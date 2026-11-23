package mn.diploma.routeplanner.recommendation;

/** Дүрмээр шүүгдсэн нэр дэвшигч газар, шинж чанарууд болон (эрэмбэлсний дараа) онооны хамт. */
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
