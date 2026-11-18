package mn.diploma.routeplanner.interaction;

/**
 * What the user did. These are the implicit-feedback signals: they update the interest weights
 * and become the training labels of the ranking model (VISIT 3, OPEN_IN_MAPS / SAVE 2, ACCEPT 1, none 0).
 */
public enum InteractionType {
    VIEW, ACCEPT, REJECT, SAVE, UNSAVE, HIDE, UNHIDE, ROUTE_MODIFY, OPEN_IN_MAPS, VISIT;

    /** Types the client may report directly; the others are logged by the server itself. */
    public boolean reportableByClient() {
        return this == VIEW || this == ACCEPT || this == REJECT || this == OPEN_IN_MAPS;
    }
}
