package mn.diploma.routeplanner.interaction;

/**
 * Хэрэглэгчийн хийсэн үйлдэл. Эдгээр нь далд үнэлгээ: сонирхлын жинг шинэчилж, эрэмбэлэх загварын
 * сургалтын шошго болно (VISIT 3, OPEN_IN_MAPS / SAVE 2, ACCEPT 1, үйлдэлгүй 0).
 */
public enum InteractionType {
    VIEW, ACCEPT, REJECT, SAVE, UNSAVE, HIDE, UNHIDE, ROUTE_MODIFY, OPEN_IN_MAPS, VISIT;

    /** Клиент шууд мэдээлж болох төрлүүд; бусдыг сервер өөрөө бүртгэнэ. */
    public boolean reportableByClient() {
        return this == VIEW || this == ACCEPT || this == REJECT || this == OPEN_IN_MAPS;
    }
}
