package mn.diploma.routeplanner.routing;

/** Цэгийн хос бүрийн хоорондох тооцоолсон зорчих хугацаа (зайн матриц), минутаар (0-р индекс ихэвчлэн эхлэх цэг). */
public record TravelTimeMatrix(int[][] minutes) {

    public int between(int from, int to) {
        return minutes[from][to];
    }

    public int size() {
        return minutes.length;
    }
}
