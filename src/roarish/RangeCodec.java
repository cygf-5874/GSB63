package roarish;

/**
 * 区间（run）编解码辅助位：把「升序、互不相邻、互不重叠」的闭区间序列
 * 与字节流互相转换。
 *
 * <p>payload 布局：4 字节大端区间数，随后每个区间 8 字节
 * （4 字节大端 start，4 字节大端 endInclusive）。
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
        if (runs == null) {
            throw new BitmapError("runs 不能为 null");
        }
        byte[] out = new byte[4 + 8 * runs.length];
        writeInt(out, 0, runs.length);
        long prevEnd = -1L;
        for (int i = 0; i < runs.length; i++) {
            int[] run = runs[i];
            if (run == null || run.length != 2) {
                throw new BitmapError("第 " + i + " 个区间必须是 {start, endInclusive}");
            }
            int start = run[0];
            int end = run[1];
            checkRun(i, start, end, i > 0 ? prevEnd : -2L);
            prevEnd = end;
            writeInt(out, 4 + 8 * i, start);
            writeInt(out, 8 + 8 * i, end);
        }
        return out;
    }

    /**
     * 把 payload 字节解码为闭区间序列。
     *
     * @param payload 由 {@link #encodeRuns} 产出的字节
     * @return 每个元素是 {@code {start, endInclusive}}，按 {@code start} 升序
     * @throws BitmapError payload 语法或长度不合法时抛出
     */
    public static int[][] decodeRuns(byte[] payload) {
        if (payload == null) {
            throw new BitmapError("payload 不能为 null");
        }
        if (payload.length < 4) {
            throw new BitmapError("payload 长度不足，无法容纳区间计数");
        }
        int count = readInt(payload, 0);
        if (count < 0) {
            throw new BitmapError("区间计数为负：" + count);
        }
        if (payload.length != 4 + 8L * count) {
            throw new BitmapError("payload 长度与声明的区间数不符：声明 " + count
                    + " 个区间，实际 " + payload.length + " 字节");
        }
        int[][] runs = new int[count][2];
        long prevEnd = -1L;
        for (int i = 0; i < count; i++) {
            int start = readInt(payload, 4 + 8 * i);
            int end = readInt(payload, 8 + 8 * i);
            checkRun(i, start, end, i > 0 ? prevEnd : -2L);
            prevEnd = end;
            runs[i] = new int[]{start, end};
        }
        return runs;
    }

    private static void checkRun(int i, int start, int end, long prevEnd) {
        if (start < 0) {
            throw new BitmapError("第 " + i + " 个区间起点为负：" + start);
        }
        if (end < start) {
            throw new BitmapError("第 " + i + " 个区间终点小于起点：[" + start + ", " + end + "]");
        }
        if (prevEnd + 1 >= start) {
            throw new BitmapError("第 " + i + " 个区间与前一个区间重叠或相邻：[" + start + ", " + end + "]");
        }
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24)
                | ((b[off + 1] & 0xFF) << 16)
                | ((b[off + 2] & 0xFF) << 8)
                | (b[off + 3] & 0xFF);
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    private RangeCodec() {
    }
}
