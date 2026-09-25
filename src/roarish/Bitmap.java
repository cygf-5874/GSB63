package roarish;

import java.util.Iterator;

/**
 * 区间/容器压缩位图。下标空间是 {@code [0, 2^31)}，即所有非负 {@code int}。
 *
 * <p>本文件当前是**空壳**：每个方法体只抛 {@link UnsupportedOperationException}。
 * 公开类型、方法签名与返回约定已经是最终形态（见 README「对外契约」），不要改动；
 * 内部表示与私有成员可以自由设计。
 */
public class Bitmap implements Iterable<Integer> {

    /** 建一个空位图。空位图是 {@code or} / {@code xor} 的恒等元。 */
    public Bitmap() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 置位下标 {@code index}。已置位时是幂等的。
     *
     * @throws BitmapError {@code index < 0}（下标必须落在 {@code [0, 2^31)}）
     */
    public void add(int index) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 清掉下标 {@code index} 的位。未置位时是幂等的。
     *
     * @throws BitmapError {@code index < 0}
     */
    public void remove(int index) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 下标 {@code index} 是否置位。
     *
     * @throws BitmapError {@code index < 0}
     */
    public boolean contains(int index) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 置位元素个数，必须精确。 */
    public int cardinality() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 是否没有任何置位元素。 */
    public boolean isEmpty() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 清空。 */
    public void clear() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 最小置位下标。
     *
     * @throws BitmapError 空位图调用时抛出
     */
    public int first() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 最大置位下标。
     *
     * @throws BitmapError 空位图调用时抛出
     */
    public int last() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 交集，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap and(Bitmap other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 并集，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap or(Bitmap other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 差集 {@code this \ other}，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap andNot(Bitmap other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 对称差，返回**新**位图，不改动 {@code this} 与 {@code other}。
     *
     * @throws BitmapError {@code other == null}
     */
    public Bitmap xor(Bitmap other) {
        throw new UnsupportedOperationException("not implemented");
    }

    /** 按**升序**产出置位下标。 */
    @Override
    public Iterator<Integer> iterator() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 序列化。同一集合产出的字节必须逐字节相同，且与元素插入顺序无关。
     *
     * @return wire 字节
     */
    public byte[] toBytes() {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 反序列化，返回与写入时集合相等的位图。
     *
     * @param bytes {@link #toBytes} 产出的字节
     * @throws BitmapError {@code bytes} 为 {@code null}、截断、版本字节错误或长度不符
     */
    public static Bitmap fromBytes(byte[] bytes) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 本对象驻留内存的字节数估计（不含调用方持有的引用本身）。
     *
     * <p>必须与「压缩后的规模」相称，而不是与元素最大下标相称：见 README 第 7 条。
     */
    public long memoryBytes() {
        throw new UnsupportedOperationException("not implemented");
    }
}
