package mn.diploma.routeplanner.auth;

/**
 * Published inside the account-deletion transaction (ХШ08). Every module that keeps personal data
 * removes or anonymises its rows in a synchronous listener, so deletion is all-or-nothing.
 */
public record UserDeletedEvent(long userId) {
}
