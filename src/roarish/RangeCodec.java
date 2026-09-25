package roarish;

/**
 * 区间（run）编解码辅助位：把「升序、互不相邻、互不重叠」的闭区间序列
 * 与字节流互相转换。当前是空壳，是否使用它实现 {@link Bitmap} 由实现者决定。
 *
 * <p>wire 格式的**版本号**定义在这里（见 README「对外契约」第 5、6 条）。
 */
public final class RangeCodec {

    /** wire 格式的版本字节，当前为 {@code 0x01}。 */
    public static final byte VERSION = 0x01;

    /**
     * 把闭区间序列编码为 payload 字节。
     *
     * @param runs 每个元素是 {@code {start, endInclusive}}，必须按 {@code start} 升序、
     *             互不相邻（相邻区间必须合并）且互不重叠
     * @return payload 字节
     */
    public static byte[] encodeRuns(int[][] runs) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 把 payload 字节解码为闭区间序列。
     *
     * @param payload 由 {@link #encodeRuns} 产出的字节
     * @return 每个元素是 {@code {start, endInclusive}}，按 {@code start} 升序
     * @throws BitmapError payload 语法或长度不合法时抛出
     */
    public static int[][] decodeRuns(byte[] payload) {
        throw new UnsupportedOperationException("not implemented");
    }

    private RangeCodec() {
    }
}
