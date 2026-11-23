package mn.diploma.routeplanner.common;

import org.springframework.http.HttpStatus;

/** Клиентэд өгөгдсөн HTTP төлөвтэйгээр {code, message} хэлбэрээр буцаах бизнес логикийн алдаа. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
