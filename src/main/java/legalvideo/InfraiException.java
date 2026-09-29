package legalvideo;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;

    InfraiException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int clientStatus() { return status >= 400 && status < 500 ? status : 502; }
}
