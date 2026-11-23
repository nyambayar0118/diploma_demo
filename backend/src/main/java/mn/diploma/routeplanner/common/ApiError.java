package mn.diploma.routeplanner.common;

/** Амжилтгүй API хүсэлт бүрийн алдааны хариу (зохиомж 3.1.3: {code, message}). */
public record ApiError(String code, String message) {
}
