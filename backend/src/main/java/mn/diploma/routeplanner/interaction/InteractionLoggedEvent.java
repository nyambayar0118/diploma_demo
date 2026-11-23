package mn.diploma.routeplanner.interaction;

/** Үйлдэл бүртгэгдсэний дараа нийтлэгдэнэ; Сонирхлын профайлын модуль үүнийг асинхроноор сонсоно. */
public record InteractionLoggedEvent(Long userId, Long placeId, InteractionType type) {
}
