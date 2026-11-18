package mn.diploma.routeplanner.routing;

/** Estimated travel minutes between every pair of points (index 0 is usually the start). */
public record TravelTimeMatrix(int[][] minutes) {

    public int between(int from, int to) {
        return minutes[from][to];
    }

    public int size() {
        return minutes.length;
    }
}
