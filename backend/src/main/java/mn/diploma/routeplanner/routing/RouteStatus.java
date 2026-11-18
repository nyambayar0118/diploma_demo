package mn.diploma.routeplanner.routing;

/** Route life cycle (requirements, state diagram "Маршрутын төлөв"). */
public enum RouteStatus {
    CREATED, OPENED_IN_MAPS, COMPLETED, CANCELLED;

    public boolean isOpen() {
        return this == CREATED || this == OPENED_IN_MAPS;
    }
}
