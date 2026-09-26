package roarish;

/**
 * 区间（run）编解码辅助位：把「升序、互不相邻、互不重叠」的闭区间序列
 * 与字节流互相转换。
 *
 * <p>wire 格式的**版本号**定义在这里（见 README「对外契约」第 5、6 条）。
 *
 * <p>payload 布局（全部大端）：
 * <pre>
 *   [0..3]                run 个数 n（n &gt;= 0）
 *   [4 + 8k .. 4 + 8k+3]  第 k 个 run 的 start（非负）
 *   [4 + 8k+4 .. 4+8k+7]  第 k 个 run 的 endInclusive（&gt;= start）
 * </pre>
 */
public final class RangeCodec {

    /** wire 格式的版本字节，当前为 {@code 0x01}。 */
    public static final byte VERSION = 0x01;

    /** payload 头部（run 计数）字节数。 */
    private static final int HEADER = 4;

    /** 单个 run 的字节数（start + endInclusive）。 */
    private static final int RUN_BYTES = 8;

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
        byte[] out = new byte[HEADER + runs.length * RUN_BYTES];
        writeInt(out, 0, runs.length);
        int offset = HEADER;
        for (int[] run : runs) {
            if (run == null || run.length != 2) {
                throw new BitmapError("run 必须是 {start, endInclusive}");
            }
            writeInt(out, offset, run[0]);
            writeInt(out, offset + 4, run[1]);
            offset += RUN_BYTES;
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
        if (payload.length < HEADER) {
            throw new BitmapError("payload 长度不足以容纳头部: " + payload.length);
        }
        int count = readInt(payload, 0);
        if (count < 0) {
            throw new BitmapError("run 个数为负: " + count);
        }
        long expected = (long) HEADER + (long) count * RUN_BYTES;
        if (expected != payload.length) {
            throw new BitmapError("payload 声明长度与实际不符: 声明=" + expected + " 实际=" + payload.length);
        }
        int[][] runs = new int[count][2];
        int offset = HEADER;
        long prevEnd = -2; // 不存在的前一个 run 的 end
        for (int i = 0; i < count; i++) {
            int start = readInt(payload, offset);
            int end = readInt(payload, offset + 4);
            offset += RUN_BYTES;
            if (start < 0) {
                throw new BitmapError("run 起点为负: " + start);
            }
            if (end < start) {
                throw new BitmapError("run 终点小于起点: [" + start + ", " + end + "]");
            }
            if ((long) start <= prevEnd + 1) {
                throw new BitmapError("run 序列重叠或相邻（未合并）: start=" + start);
            }
            prevEnd = end;
            runs[i][0] = start;
            runs[i][1] = end;
        }
        return runs;
    }

    private static void writeInt(byte[] out, int offset, int value) {
        out[offset] = (byte) (value >>> 24);
        out[offset + 1] = (byte) (value >>> 16);
        out[offset + 2] = (byte) (value >>> 8);
        out[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] in, int offset) {
        return ((in[offset] & 0xFF) << 24)
                | ((in[offset + 1] & 0xFF) << 16)
                | ((in[offset + 2] & 0xFF) << 8)
                | (in[offset + 3] & 0xFF);
    }

    private RangeCodec() {
    }
}
