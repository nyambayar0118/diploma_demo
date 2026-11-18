package mn.diploma.routeplanner.interaction;

/** Published after an interaction is recorded; the Interest Profile module listens to it asynchronously. */
public record InteractionLoggedEvent(Long userId, Long placeId, InteractionType type) {
}
