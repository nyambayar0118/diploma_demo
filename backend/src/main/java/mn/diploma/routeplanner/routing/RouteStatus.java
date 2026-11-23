package mn.diploma.routeplanner.routing;

/** Маршрутын амьдралын мөчлөг (шаардлагын бүлэг, "Маршрутын төлөв" төлөвийн диаграм). */
public enum RouteStatus {
    CREATED, OPENED_IN_MAPS, COMPLETED, CANCELLED;

    public boolean isOpen() {
        return this == CREATED || this == OPENED_IN_MAPS;
    }
}
