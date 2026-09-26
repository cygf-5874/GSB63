package roarish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * 区间/容器压缩位图。下标空间是 {@code [0, 2^31)}，即所有非负 {@code int}。
 *
 * <p>内部表示：一组升序、互不相邻、互不重叠的闭区间（run），存放在
 * {@code TreeMap<start, endInclusive>} 中。稀疏集合每个孤立点只占一个 run，
 * 成片连续的下标合并成一个 run，驻留内存与压缩后的规模相称。
 */
public class Bitmap implements Iterable<Integer> {

    /** run 表：start -> endInclusive。不变式：升序、互不相邻、互不重叠。 */
    private final TreeMap<Integer, Integer> runs = new TreeMap<>();

    /** 置位元素个数（精确值，随 add/remove 增量维护）。 */
    private int card;

    /** 建一个空位图。空位图是 {@code or} / {@code xor} 的恒等元。 */
    public Bitmap() {
    }

    /**
     * 置位下标 {@code index}。已置位时是幂等的。
     *
     * @throws BitmapError {@code index < 0}（下标必须落在 {@code [0, 2^31)}）
     */
    public void add(int index) {
        checkIndex(index);
        Map.Entry<Integer, Integer> floor = runs.floorEntry(index);
        if (floor != null && floor.getValue() >= index) {
            return; // 已在某个 run 内，幂等空操作
        }
        boolean mergePrev = floor != null && floor.getValue() == index - 1;
        Map.Entry<Integer, Integer> next = runs.higherEntry(index);
        boolean mergeNext = next != null && index != Integer.MAX_VALUE
                && next.getKey() == index + 1;
        if (mergePrev && mergeNext) {
            runs.remove(next.getKey());
            runs.put(floor.getKey(), next.getValue());
        } else if (mergePrev) {
            runs.put(floor.getKey(), index);
        } else if (mergeNext) {
            int end = next.getValue();
            runs.remove(next.getKey());
            runs.put(index, end);
        } else {
            runs.put(index, index);
        }
        card++;
    }

    /**
     * 清掉下标 {@code index} 的位。未置位时是幂等的。
     *
     * @throws BitmapError {@code index < 0}
     */
    public void remove(int index) {
        checkIndex(index);
        Map.Entry<Integer, Integer> floor = runs.floorEntry(index);
        if (floor == null || floor.getValue() < index) {
            return; // 未置位，幂等空操作
        }
        int start = floor.getKey();
        int end = floor.getValue();
        runs.remove(start);
        if (start < index) {
            runs.put(start, index - 1);
        }
        if (index < end) {
            runs.put(index + 1, end);
        }
        card--;
    }

    /**
     * 下标 {@code index} 是否置位。
     *
     * @throws BitmapError {@code index < 0}
     */
    public boolean contains(int index) {
        checkIndex(index);
        Map.Entry<Integer, Integer> floor = runs.floorEntry(index);
        return floor != null && floor.getValue() >= index;
    }

    /** 置位元素个数，必须精确。 */
    public int cardinality() {
        return card;
    }

    /** 是否没有任何置位元素。 */
    public boolean isEmpty() {
        return card == 0;
    }

    /** 清空。 */
    public void clear() {
        runs.clear();
        card = 0;
    }

    /**
     * 最小置位下标。
     *
     * @throws BitmapError 空位图调用时抛出
     */
    public int first() {
        if (runs.isEmpty()) {
            throw new BitmapError("空位图没有 first()");
        }
        return runs.firstKey();
    }

    /**
     * 最大置位下标。
     *
     * @throws BitmapError 空位图调用时抛出
     */
    public int last() {
        if (runs.isEmpty()) {
            throw new BitmapError("空位图没有 last()");
        }
        return runs.lastEntry().getValue();
    }

    /**
     * 交集，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap and(Bitmap other) {
        requireOther(other);
        return fromRuns(andRuns(this.runsArray(), other.runsArray()));
    }

    /**
     * 并集，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap or(Bitmap other) {
        requireOther(other);
        return fromRuns(unionRuns(this.runsArray(), other.runsArray()));
    }

    /**
     * 差集 {@code this \ other}，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap andNot(Bitmap other) {
        requireOther(other);
        return fromRuns(andNotRuns(this.runsArray(), other.runsArray()));
    }

    /**
     * 对称差，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap xor(Bitmap other) {
        requireOther(other);
        int[][] a = this.runsArray();
        int[][] b = other.runsArray();
        return fromRuns(unionRuns(andNotRuns(a, b), andNotRuns(b, a)));
    }

    /** 按**升序**产出置位下标。 */
    @Override
    public Iterator<Integer> iterator() {
        Iterator<Map.Entry<Integer, Integer>> entries = runs.entrySet().iterator();
        return new Iterator<Integer>() {
            private int nextValue;
            private int runEnd;
            private boolean inRun;

            {
                advanceRun();
            }

            private void advanceRun() {
                if (entries.hasNext()) {
                    Map.Entry<Integer, Integer> run = entries.next();
                    nextValue = run.getKey();
                    runEnd = run.getValue();
                    inRun = true;
                } else {
                    inRun = false;
                }
            }

            @Override
            public boolean hasNext() {
                return inRun;
            }

            @Override
            public Integer next() {
                if (!inRun) {
                    throw new NoSuchElementException();
                }
                int result = nextValue;
                if (nextValue == runEnd) {
                    advanceRun();
                } else {
                    nextValue++;
                }
                return result;
            }
        };
    }

    /**
     * 序列化。同一集合产出的字节必须逐字节相同，且与元素插入顺序无关。
     *
     * <p>布局：{@code [0]} 版本字节 {@link RangeCodec#VERSION}；
     * {@code [1..4]} payload 长度（大端）；{@code [5..]} payload（run 编码）。
     *
     * @return wire 字节
     */
    public byte[] toBytes() {
        byte[] payload = RangeCodec.encodeRuns(runsArray());
        byte[] out = new byte[5 + payload.length];
        out[0] = RangeCodec.VERSION;
        int length = payload.length;
        out[1] = (byte) (length >>> 24);
        out[2] = (byte) (length >>> 16);
        out[3] = (byte) (length >>> 8);
        out[4] = (byte) length;
        System.arraycopy(payload, 0, out, 5, payload.length);
        return out;
    }

    /**
     * 反序列化，返回与写入时集合相等的位图。
     *
     * @param bytes {@link #toBytes} 产出的字节
     * @throws BitmapError {@code bytes} 为 {@code null}、截断、版本字节错误或长度不符
     */
    public static Bitmap fromBytes(byte[] bytes) {
        if (bytes == null) {
            throw new BitmapError("字节数组不能为 null");
        }
        if (bytes.length < 5) {
            throw new BitmapError("长度不足以容纳头部: " + bytes.length);
        }
        if (bytes[0] != RangeCodec.VERSION) {
            throw new BitmapError("版本字节错误: " + bytes[0]);
        }
        int length = ((bytes[1] & 0xFF) << 24)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 8)
                | (bytes[4] & 0xFF);
        if (length < 0 || length != bytes.length - 5) {
            throw new BitmapError("声明长度与实际不符: 声明=" + length
                    + " 实际=" + (bytes.length - 5));
        }
        int[][] decoded = RangeCodec.decodeRuns(Arrays.copyOfRange(bytes, 5, bytes.length));
        return fromRuns(decoded);
    }

    /**
     * 本对象驻留内存的字节数估计（不含调用方持有的引用本身）。
     *
     * <p>与「压缩后的规模」相称：正比于 run 个数，而不是元素最大下标。
     */
    public long memoryBytes() {
        // 对象头与字段约 64 字节；每个 TreeMap 节点（含两个装箱 Integer）约 56 字节。
        return 64L + runs.size() * 56L;
    }

    // ---------------------------------------------------------- 内部实现

    private static void checkIndex(int index) {
        if (index < 0) {
            throw new BitmapError("下标越界（必须落在 [0, 2^31)）: " + index);
        }
    }

    private static void requireOther(Bitmap other) {
        if (other == null) {
            throw new BitmapError("入参 other 不能为 null");
        }
    }

    /** 把当前 run 表导出为 {@code {start, endInclusive}} 数组（升序）。 */
    private int[][] runsArray() {
        int[][] out = new int[runs.size()][2];
        int i = 0;
        for (Map.Entry<Integer, Integer> run : runs.entrySet()) {
            out[i][0] = run.getKey();
            out[i][1] = run.getValue();
            i++;
        }
        return out;
    }

    /** 由合法 run 序列（升序、互不相邻、互不重叠）构造位图。 */
    private static Bitmap fromRuns(int[][] decoded) {
        Bitmap bitmap = new Bitmap();
        long total = 0;
        for (int[] run : decoded) {
            bitmap.runs.put(run[0], run[1]);
            total += (long) run[1] - run[0] + 1;
        }
        bitmap.card = (int) total;
        return bitmap;
    }

    /** 并集：归并两个升序 run 序列，重叠或相邻的 run 合并。 */
    private static int[][] unionRuns(int[][] a, int[][] b) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        long curStart = -1;
        long curEnd = -1;
        while (i < a.length || j < b.length) {
            int[] next;
            if (j >= b.length || (i < a.length && a[i][0] <= b[j][0])) {
                next = a[i++];
            } else {
                next = b[j++];
            }
            if (curStart < 0) {
                curStart = next[0];
                curEnd = next[1];
            } else if ((long) next[0] <= curEnd + 1) {
                curEnd = Math.max(curEnd, next[1]);
            } else {
                out.add(new int[]{(int) curStart, (int) curEnd});
                curStart = next[0];
                curEnd = next[1];
            }
        }
        if (curStart >= 0) {
            out.add(new int[]{(int) curStart, (int) curEnd});
        }
        return out.toArray(new int[0][]);
    }

    /** 交集：两个升序 run 序列的重叠部分。 */
    private static int[][] andRuns(int[][] a, int[][] b) {
        List<int[]> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < a.length && j < b.length) {
            int start = Math.max(a[i][0], b[j][0]);
            int end = Math.min(a[i][1], b[j][1]);
            if (start <= end) {
                out.add(new int[]{start, end});
            }
            if (a[i][1] < b[j][1]) {
                i++;
            } else {
                j++;
            }
        }
        return out.toArray(new int[0][]);
    }

    /** 差集：a 的 run 序列减去 b 的 run 序列。 */
    private static int[][] andNotRuns(int[][] a, int[][] b) {
        List<int[]> out = new ArrayList<>();
        int j = 0;
        for (int[] runA : a) {
            long start = runA[0];
            long end = runA[1];
            while (j < b.length && b[j][1] < start) {
                j++;
            }
            int k = j;
            while (k < b.length && b[k][0] <= end) {
                if (b[k][0] > start) {
                    out.add(new int[]{(int) start, b[k][0] - 1});
                }
                start = Math.max(start, (long) b[k][1] + 1);
                if (start > end) {
                    break;
                }
                k++;
            }
            if (start <= end) {
                out.add(new int[]{(int) start, (int) end});
            }
        }
        return out.toArray(new int[0][]);
    }
}
