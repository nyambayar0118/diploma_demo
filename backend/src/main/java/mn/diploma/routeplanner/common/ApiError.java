package mn.diploma.routeplanner.common;

/** Error body of every failed API request (design 3.1.3: {code, message}). */
public record ApiError(String code, String message) {
}
