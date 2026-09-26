package roarish;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 区间/容器压缩位图。下标空间是 {@code [0, 2^31)}，即所有非负 {@code int}。
 *
 * <p>内部表示：升序、互不重叠且互不相邻的闭区间（run）序列，扁平存放在
 * {@code int[]} 中（{@code runs[2i]} 为起点，{@code runs[2i+1]} 为终点，含端点）。
 * 驻留内存与区间数成正比，与元素最大下标无关；同一集合的区间序列唯一，
 * 因此序列化字节与插入顺序无关。
 */
public class Bitmap implements Iterable<Integer> {

    /** 扁平区间数组：{start0, end0, start1, end1, ...}，仅前 {@code 2 * runCount} 项有效。 */
    private int[] runs;

    /** 区间个数。 */
    private int runCount;

    /** 置位元素个数（精确）。 */
    private long size;

    /** 建一个空位图。空位图是 {@code or} / {@code xor} 的恒等元。 */
    public Bitmap() {
        this.runs = new int[8];
        this.runCount = 0;
        this.size = 0L;
    }

    private Bitmap(int[] runs, int runCount) {
        this.runs = runs;
        this.runCount = runCount;
        long total = 0L;
        for (int i = 0; i < runCount; i++) {
            total += (long) runs[2 * i + 1] - runs[2 * i] + 1L;
        }
        this.size = total;
    }

    /**
     * 置位下标 {@code index}。已置位时是幂等的。
     *
     * @throws BitmapError {@code index < 0}（下标必须落在 {@code [0, 2^31)}）
     */
    public void add(int index) {
        checkIndex(index);
        int found = findRun(index);
        if (found >= 0) {
            return;
        }
        int ins = -found - 1;
        boolean mergePrev = ins > 0 && runs[2 * (ins - 1) + 1] == index - 1;
        boolean mergeNext = ins < runCount && index < Integer.MAX_VALUE && runs[2 * ins] == index + 1;
        if (mergePrev && mergeNext) {
            runs[2 * (ins - 1) + 1] = runs[2 * ins + 1];
            removeRunAt(ins);
        } else if (mergePrev) {
            runs[2 * (ins - 1) + 1] = index;
        } else if (mergeNext) {
            runs[2 * ins] = index;
        } else {
            insertRunAt(ins, index, index);
        }
        size++;
    }

    /**
     * 清掉下标 {@code index} 的位。未置位时是幂等的。
     *
     * @throws BitmapError {@code index < 0}
     */
    public void remove(int index) {
        checkIndex(index);
        int r = findRun(index);
        if (r < 0) {
            return;
        }
        int start = runs[2 * r];
        int end = runs[2 * r + 1];
        if (start == end) {
            removeRunAt(r);
        } else if (index == start) {
            runs[2 * r] = start + 1;
        } else if (index == end) {
            runs[2 * r + 1] = end - 1;
        } else {
            insertRunAt(r + 1, index + 1, end);
            runs[2 * r + 1] = index - 1;
        }
        size--;
    }

    /**
     * 下标 {@code index} 是否置位。
     *
     * @throws BitmapError {@code index < 0}
     */
    public boolean contains(int index) {
        checkIndex(index);
        return findRun(index) >= 0;
    }

    /** 置位元素个数，必须精确。 */
    public int cardinality() {
        return (int) size;
    }

    /** 是否没有任何置位元素。 */
    public boolean isEmpty() {
        return runCount == 0;
    }

    /** 清空。 */
    public void clear() {
        runs = new int[8];
        runCount = 0;
        size = 0L;
    }

    /**
     * 最小置位下标。
     *
     * @throws BitmapError 空位图调用时抛出
     */
    public int first() {
        if (runCount == 0) {
            throw new BitmapError("空位图没有最小下标");
        }
        return runs[0];
    }

    /**
     * 最大置位下标。
     *
     * @throws BitmapError 空位图调用时抛出
     */
    public int last() {
        if (runCount == 0) {
            throw new BitmapError("空位图没有最大下标");
        }
        return runs[2 * runCount - 1];
    }

    /**
     * 交集，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap and(Bitmap other) {
        requireOther(other);
        int[] a = this.runs;
        int[] b = other.runs;
        int an = this.runCount;
        int bn = other.runCount;
        int[] out = new int[2 * (an + bn) + 2];
        int outCount = 0;
        int i = 0;
        int j = 0;
        while (i < an && j < bn) {
            int s = Math.max(a[2 * i], b[2 * j]);
            int e = Math.min(a[2 * i + 1], b[2 * j + 1]);
            if (s <= e) {
                out[2 * outCount] = s;
                out[2 * outCount + 1] = e;
                outCount++;
            }
            if (a[2 * i + 1] < b[2 * j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return new Bitmap(trim(out, outCount), outCount);
    }

    /**
     * 并集，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap or(Bitmap other) {
        requireOther(other);
        int[] a = this.runs;
        int[] b = other.runs;
        int an = this.runCount;
        int bn = other.runCount;
        int[] out = new int[2 * (an + bn) + 2];
        int outCount = 0;
        int i = 0;
        int j = 0;
        while (i < an || j < bn) {
            int s;
            int e;
            if (j >= bn || (i < an && a[2 * i] < b[2 * j])) {
                s = a[2 * i];
                e = a[2 * i + 1];
                i++;
            } else {
                s = b[2 * j];
                e = b[2 * j + 1];
                j++;
            }
            if (outCount > 0 && (long) out[2 * outCount - 1] + 1L >= s) {
                out[2 * outCount - 1] = Math.max(out[2 * outCount - 1], e);
            } else {
                out[2 * outCount] = s;
                out[2 * outCount + 1] = e;
                outCount++;
            }
        }
        return new Bitmap(trim(out, outCount), outCount);
    }

    /**
     * 差集 {@code this \ other}，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap andNot(Bitmap other) {
        requireOther(other);
        int[] a = this.runs;
        int[] b = other.runs;
        int an = this.runCount;
        int bn = other.runCount;
        int[] out = new int[2 * an + 2 * bn + 2];
        int outCount = 0;
        int j = 0;
        for (int i = 0; i < an; i++) {
            long cur = a[2 * i];
            int end = a[2 * i + 1];
            while (j < bn && b[2 * j + 1] < cur) {
                j++;
            }
            int k = j;
            while (k < bn && b[2 * k] <= end) {
                if (b[2 * k] > cur) {
                    out[2 * outCount] = (int) cur;
                    out[2 * outCount + 1] = b[2 * k] - 1;
                    outCount++;
                }
                cur = (long) b[2 * k + 1] + 1L;
                if (cur > end) {
                    break;
                }
                k++;
            }
            if (cur <= end) {
                out[2 * outCount] = (int) cur;
                out[2 * outCount + 1] = end;
                outCount++;
            }
        }
        return new Bitmap(trim(out, outCount), outCount);
    }

    /**
     * 对称差，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap xor(Bitmap other) {
        requireOther(other);
        return this.or(other).andNot(this.and(other));
    }

    /** 按**升序**产出置位下标。 */
    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            private int run = 0;
            private int cur = runCount > 0 ? runs[0] : 0;

            @Override
            public boolean hasNext() {
                return run < runCount;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int v = cur;
                if (cur == runs[2 * run + 1]) {
                    run++;
                    if (run < runCount) {
                        cur = runs[2 * run];
                    }
                } else {
                    cur++;
                }
                return v;
            }
        };
    }

    /**
     * 序列化。同一集合产出的字节必须逐字节相同，且与元素插入顺序无关。
     *
     * <p>布局：1 字节版本号（{@link RangeCodec#VERSION}）+ 4 字节大端 payload
     * 长度 + payload（见 {@link RangeCodec}）。
     *
     * @return wire 字节
     */
    public byte[] toBytes() {
        int[][] rs = new int[runCount][2];
        for (int i = 0; i < runCount; i++) {
            rs[i] = new int[]{runs[2 * i], runs[2 * i + 1]};
        }
        byte[] payload = RangeCodec.encodeRuns(rs);
        byte[] out = new byte[5 + payload.length];
        out[0] = RangeCodec.VERSION;
        out[1] = (byte) (payload.length >>> 24);
        out[2] = (byte) (payload.length >>> 16);
        out[3] = (byte) (payload.length >>> 8);
        out[4] = (byte) payload.length;
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
            throw new BitmapError("字节不能为 null");
        }
        if (bytes.length < 5) {
            throw new BitmapError("长度不足，无法容纳头部（版本 + 长度）");
        }
        if (bytes[0] != RangeCodec.VERSION) {
            throw new BitmapError("版本字节错误：期望 0x01 实际 0x"
                    + Integer.toHexString(bytes[0] & 0xFF));
        }
        int len = ((bytes[1] & 0xFF) << 24)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 8)
                | (bytes[4] & 0xFF);
        if (len < 0 || bytes.length != 5 + (long) len) {
            throw new BitmapError("声明长度与实际长度不符：声明 " + len
                    + " 字节 payload，实际 " + (bytes.length - 5) + " 字节");
        }
        int[][] rs = RangeCodec.decodeRuns(Arrays.copyOfRange(bytes, 5, 5 + len));
        int[] flat = new int[2 * rs.length];
        for (int i = 0; i < rs.length; i++) {
            flat[2 * i] = rs[i][0];
            flat[2 * i + 1] = rs[i][1];
        }
        return new Bitmap(flat.length == 0 ? new int[8] : flat, rs.length);
    }

    /**
     * 本对象驻留内存的字节数估计（不含调用方持有的引用本身）。
     *
     * <p>与区间数成正比：稀疏集合与连续区间都远小于按下标上界开满的位数组。
     */
    public long memoryBytes() {
        return 48L + 4L * runs.length;
    }

    // ------------------------------------------------------------- 内部

    private static void checkIndex(int index) {
        if (index < 0) {
            throw new BitmapError("下标越界：" + index + "（合法范围 [0, 2^31)）");
        }
    }

    private static void requireOther(Bitmap other) {
        if (other == null) {
            throw new BitmapError("集合运算的入参不能为 null");
        }
    }

    /**
     * 二分查找包含 {@code index} 的区间。
     *
     * @return 命中时返回区间下标；否则返回 {@code -(插入点) - 1}，
     *         插入点为第一个起点大于 {@code index} 的区间下标
     */
    private int findRun(int index) {
        int lo = 0;
        int hi = runCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int s = runs[2 * mid];
            int e = runs[2 * mid + 1];
            if (index < s) {
                hi = mid - 1;
            } else if (index > e) {
                lo = mid + 1;
            } else {
                return mid;
            }
        }
        return -lo - 1;
    }

    private void ensureCapacity(int runCapacity) {
        if (2 * runCapacity > runs.length) {
            int newLen = Math.max(runs.length * 2, 2 * runCapacity);
            runs = Arrays.copyOf(runs, newLen);
        }
    }

    private void insertRunAt(int pos, int start, int end) {
        ensureCapacity(runCount + 1);
        if (pos < runCount) {
            System.arraycopy(runs, 2 * pos, runs, 2 * pos + 2, 2 * (runCount - pos));
        }
        runs[2 * pos] = start;
        runs[2 * pos + 1] = end;
        runCount++;
    }

    private void removeRunAt(int pos) {
        if (pos < runCount - 1) {
            System.arraycopy(runs, 2 * pos + 2, runs, 2 * pos, 2 * (runCount - pos - 1));
        }
        runCount--;
    }

    private static int[] trim(int[] flat, int count) {
        if (count == 0) {
            return new int[8];
        }
        return flat.length == 2 * count ? flat : Arrays.copyOf(flat, 2 * count);
    }
}
