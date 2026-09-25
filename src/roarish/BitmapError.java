package roarish;

/**
 * roarish 的统一错误类型：位下标越界、入参非法、字节流无法解析等，
 * 一律抛这个异常（见 README「对外契约」）。
 *
 * <p>类名与继承关系是对外契约的一部分，不要改动。
 */
public class BitmapError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BitmapError(String message) {
        super(message);
    }

    public BitmapError(String message, Throwable cause) {
        super(message, cause);
    }
}
