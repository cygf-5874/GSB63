package roarish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * roarish 既有用例。自带极简 runner（不引入任何第三方测试框架）。
 *
 * <p>覆盖范围只包括**内存与性能无关**的那部分语义：基本读写、越界、迭代顺序、
 * 集合运算的取值与不可变性、空位图恒等元、以及字节往返。内存上限与
 * 10^6 次随机增删的精确性由固定件判定。
 *
 * <p>本文件当前全部失败（实现是空壳），能编译，没有语法错。
 */
public final class BitmapTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        run("emptyBitmapBasics", BitmapTest::emptyBitmapBasics);
        run("addContainsRemoveIdempotent", BitmapTest::addContainsRemoveIdempotent);
        run("negativeIndexRejected", BitmapTest::negativeIndexRejected);
        run("iteratorAscending", BitmapTest::iteratorAscending);
        run("firstAndLast", BitmapTest::firstAndLast);
        run("andOrAndNotExact", BitmapTest::andOrAndNotExact);
        run("xorExact", BitmapTest::xorExact);
        run("setOpsDoNotModifyInputs", BitmapTest::setOpsDoNotModifyInputs);
        run("emptyIsIdentityAndZero", BitmapTest::emptyIsIdentityAndZero);
        run("bytesRoundTrip", BitmapTest::bytesRoundTrip);
        run("bytesOrderIndependent", BitmapTest::bytesOrderIndependent);
        run("fromBytesRejectsMalformed", BitmapTest::fromBytesRejectsMalformed);

        System.out.println("通过 " + passed + "/" + (passed + failed));
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- runner

    private interface Case {
        void run() throws Exception;
    }

    private static void run(String name, Case c) {
        try {
            c.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL " + name + "  " + t);
        }
    }

    private static void check(boolean cond, String message) {
        if (!cond) {
            throw new AssertionError(message);
        }
    }

    private static void eq(Object expected, Object actual, String message) {
        boolean same = (expected == null) ? (actual == null) : expected.equals(actual);
        if (!same) {
            throw new AssertionError(message + " 期望=" + expected + " 实际=" + actual);
        }
    }

    private static void expectBitmapError(Runnable body, String message) {
        try {
            body.run();
        } catch (BitmapError e) {
            return;
        } catch (Throwable t) {
            throw new AssertionError(message + " 期望=BitmapError 实际=抛了 " + t.getClass().getName());
        }
        throw new AssertionError(message + " 期望=BitmapError 实际=没有抛出");
    }

    /** 按升序把置位下标收集成 List。 */
    private static List<Integer> collect(Bitmap b) {
        List<Integer> out = new ArrayList<>();
        for (int v : b) {
            out.add(v);
        }
        return out;
    }

    private static void addAll(Bitmap b, int... values) {
        for (int v : values) {
            b.add(v);
        }
    }

    // ----------------------------------------------------------- 既有用例

    private static void emptyBitmapBasics() {
        Bitmap b = new Bitmap();
        check(b.isEmpty(), "新位图应为空");
        eq(0, b.cardinality(), "空位图基数应为 0");
        eq(List.of(), collect(b), "空位图不应产出任何元素");
        check(!b.contains(0), "空位图 contains(0) 应为 false");
        b.clear();
        check(b.isEmpty(), "对空位图 clear 后仍应为空");
    }

    private static void addContainsRemoveIdempotent() {
        Bitmap b = new Bitmap();
        b.add(10);
        b.add(10);
        b.add(20);
        check(b.contains(10), "add 后应 contains");
        check(b.contains(20), "add 后应 contains");
        check(!b.contains(15), "未 add 的下标不应 contains");
        eq(2, b.cardinality(), "重复 add 同一元素不应增长");

        b.remove(10);
        b.remove(10);
        check(!b.contains(10), "remove 后不应 contains");
        eq(1, b.cardinality(), "重复 remove 不应继续减少");
        b.remove(999);
        eq(1, b.cardinality(), "remove 未置位的下标应是空操作");
    }

    private static void negativeIndexRejected() {
        Bitmap b = new Bitmap();
        expectBitmapError(() -> b.add(-1), "add(-1)");
        expectBitmapError(() -> b.remove(-1), "remove(-1)");
        expectBitmapError(() -> b.contains(-1), "contains(-1)");
        expectBitmapError(() -> b.add(Integer.MIN_VALUE), "add(Integer.MIN_VALUE)");
    }

    private static void iteratorAscending() {
        Bitmap b = new Bitmap();
        addAll(b, 9, 1, 7, 3, 5, 1);
        eq(List.of(1, 3, 5, 7, 9), collect(b), "iterator 应按升序且去重");

        Bitmap c = new Bitmap();
        addAll(c, 0, Integer.MAX_VALUE, 1_000_000);
        eq(List.of(0, 1_000_000, Integer.MAX_VALUE), collect(c), "iterator 对下边界与上边界应正确");
    }

    private static void firstAndLast() {
        Bitmap b = new Bitmap();
        addAll(b, 42, 7, 1234);
        eq(7, b.first(), "first 应为最小置位下标");
        eq(1234, b.last(), "last 应为最大置位下标");

        Bitmap one = new Bitmap();
        one.add(5);
        eq(5, one.first(), "单元素位图 first");
        eq(5, one.last(), "单元素位图 last");

        Bitmap empty = new Bitmap();
        expectBitmapError(empty::first, "空位图 first()");
        expectBitmapError(empty::last, "空位图 last()");
    }

    private static void andOrAndNotExact() {
        Bitmap a = new Bitmap();
        addAll(a, 1, 2, 3, 100);
        Bitmap b = new Bitmap();
        addAll(b, 2, 3, 4, 5);

        Bitmap and = a.and(b);
        eq(List.of(2, 3), collect(and), "交集应精确");
        eq(2, and.cardinality(), "交集基数应精确");

        Bitmap or = a.or(b);
        eq(List.of(1, 2, 3, 4, 5, 100), collect(or), "并集应精确");
        eq(6, or.cardinality(), "并集基数应精确");

        Bitmap diff = a.andNot(b);
        eq(List.of(1, 100), collect(diff), "差集应精确");
        eq(2, diff.cardinality(), "差集基数应精确");

        Bitmap disjoint = new Bitmap();
        addAll(disjoint, 7, 8);
        check(a.and(disjoint).isEmpty(), "不相交集合的 and 应为空");
        check(a.andNot(b).contains(1), "差集应保留只属于 a 的元素");
    }

    private static void xorExact() {
        Bitmap a = new Bitmap();
        addAll(a, 1, 2, 3, 100);
        Bitmap b = new Bitmap();
        addAll(b, 2, 3, 4, 5);

        Bitmap x = a.xor(b);
        eq(List.of(1, 4, 5, 100), collect(x), "对称差应精确");
        eq(4, x.cardinality(), "对称差基数应精确");

        Bitmap again = new Bitmap();
        addAll(again, 1, 2, 3, 100);
        check(again.xor(a).isEmpty(), "集合与自身的对称差应为空");
    }

    private static void setOpsDoNotModifyInputs() {
        Bitmap a = new Bitmap();
        addAll(a, 1, 2, 3, 100);
        Bitmap b = new Bitmap();
        addAll(b, 2, 3, 4, 5);
        List<Integer> beforeA = collect(a);
        List<Integer> beforeB = collect(b);

        Bitmap and = a.and(b);
        Bitmap or = a.or(b);
        Bitmap diff = a.andNot(b);
        Bitmap x = a.xor(b);

        eq(beforeA, collect(a), "集合运算不得改动 this");
        eq(beforeB, collect(b), "集合运算不得改动入参 other");

        check(and != a && and != b, "and 必须返回新对象");
        check(or != a && or != b, "or 必须返回新对象");
        check(diff != a && diff != b, "andNot 必须返回新对象");
        check(x != a && x != b, "xor 必须返回新对象");

        or.add(777);
        and.remove(2);
        check(!a.contains(777) && !b.contains(777), "改结果不能影响入参");
        eq(beforeA, collect(a), "改结果后 this 仍应不变");
        eq(beforeB, collect(b), "改结果后 other 仍应不变");
    }

    private static void emptyIsIdentityAndZero() {
        Bitmap empty = new Bitmap();
        Bitmap s = new Bitmap();
        addAll(s, 3, 5, 8);

        eq(collect(s), collect(empty.or(s)), "空位图应是 or 的恒等元");
        eq(collect(s), collect(s.or(empty)), "空位图应是 or 的恒等元（右）");
        eq(collect(s), collect(s.xor(empty)), "空位图应是 xor 的恒等元");
        eq(collect(s), collect(empty.xor(s)), "空位图应是 xor 的恒等元（左）");

        check(s.and(empty).isEmpty(), "空位图应是 and 的零元");
        check(empty.and(s).isEmpty(), "空位图应是 and 的零元（左）");
        eq(collect(s), collect(s.andNot(empty)), "andNot 空位图应得到自身");
        check(empty.andNot(s).isEmpty(), "空位图 andNot 任何集合都应为空");
        check(empty.or(empty).isEmpty(), "空并空仍为空");
    }

    private static void bytesRoundTrip() {
        Bitmap sparse = new Bitmap();
        for (int i = 0; i < 2000; i++) {
            sparse.add(i * 500);
        }
        Bitmap back = Bitmap.fromBytes(sparse.toBytes());
        eq(collect(sparse), collect(back), "稀疏集合往返后应相等");
        eq(sparse.cardinality(), back.cardinality(), "稀疏集合往返后基数应相等");

        Bitmap dense = new Bitmap();
        for (int i = 0; i < 5000; i++) {
            dense.add(i);
        }
        eq(collect(dense), collect(Bitmap.fromBytes(dense.toBytes())), "稠密集合往返后应相等");

        Bitmap empty = new Bitmap();
        check(Bitmap.fromBytes(empty.toBytes()).isEmpty(), "空位图往返后仍应为空");

        Bitmap single = new Bitmap();
        single.add(Integer.MAX_VALUE);
        Bitmap singleBack = Bitmap.fromBytes(single.toBytes());
        eq(List.of(Integer.MAX_VALUE), collect(singleBack), "最大下标应能往返");
    }

    private static void bytesOrderIndependent() {
        int[] values = new int[3000];
        for (int i = 0; i < values.length; i++) {
            values[i] = i * 333 + 7;
        }

        Bitmap asc = new Bitmap();
        for (int v : values) {
            asc.add(v);
        }

        Bitmap desc = new Bitmap();
        for (int i = values.length - 1; i >= 0; i--) {
            desc.add(values[i]);
        }

        Bitmap shuffled = new Bitmap();
        int[] order = values.clone();
        java.util.Random rnd = new java.util.Random(20240925L);
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        for (int v : order) {
            shuffled.add(v);
        }

        check(Arrays.equals(asc.toBytes(), desc.toBytes()),
                "降序插入与升序插入的字节应逐字节相同");
        check(Arrays.equals(asc.toBytes(), shuffled.toBytes()),
                "乱序插入与升序插入的字节应逐字节相同");

        Bitmap other = new Bitmap();
        addAll(other, 3, 5, 8);
        check(!Arrays.equals(asc.toBytes(), other.toBytes()), "不同集合的字节不应相同");
    }

    private static void fromBytesRejectsMalformed() {
        Bitmap b = new Bitmap();
        addAll(b, 1, 2, 3, 1000);
        byte[] good = b.toBytes();

        expectBitmapError(() -> Bitmap.fromBytes(null), "fromBytes(null)");
        expectBitmapError(() -> Bitmap.fromBytes(new byte[0]), "fromBytes 空数组");
        expectBitmapError(() -> Bitmap.fromBytes(new byte[]{0x01}), "fromBytes 只有版本字节");

        byte[] truncated = Arrays.copyOf(good, good.length - 1);
        expectBitmapError(() -> Bitmap.fromBytes(truncated), "fromBytes 截断字节");

        byte[] extra = Arrays.copyOf(good, good.length + 1);
        expectBitmapError(() -> Bitmap.fromBytes(extra), "fromBytes 尾部多余字节");

        byte[] badVersion = good.clone();
        badVersion[0] = (byte) 0x7F;
        expectBitmapError(() -> Bitmap.fromBytes(badVersion), "fromBytes 版本字节错误");

        check(collect(b).equals(collect(Bitmap.fromBytes(good))), "合法字节应仍能解析");
    }

    private BitmapTest() {
    }
}
