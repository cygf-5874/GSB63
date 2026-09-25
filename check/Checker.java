import roarish.Bitmap;
import roarish.BitmapError;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * roarish 的固定验收程序（勿改）。
 *
 * <p>10 个场景覆盖 README「对外契约」的 10 条：basic 3 / setop 3 / wire 2 / density 2。
 * 逐场景独立判定，失败不早退；判据全部确定性：没有墙钟、没有随机源（随机增删用固定种子的
 * {@link Random}）、内存看 {@code memoryBytes()} 自报 + 源码扫描。
 *
 * <p>用法：java -cp out Checker [-list] [--only &lt;组名&gt;[,&lt;组名&gt;...]]
 */
public final class Checker {

    /** 契约第 7、8 条的内存上限：1 MiB。 */
    private static final long MEM_LIMIT = 1L << 20;

    /** 迭代器产出元素数的硬上限，防止实现不收敛时把固定件挂死。 */
    private static final int ITER_CAP = 200_000;

    private interface Body {
        /** 返回 null 表示 PASS，否则返回可判定的失败原因。 */
        String run() throws Exception;
    }

    private static final class Scenario {
        final String group;
        final String name;
        final Body body;

        Scenario(String name, Body body) {
            this.name = name;
            this.body = body;
            int slash = name.indexOf('/');
            this.group = slash < 0 ? name : name.substring(0, slash);
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> only = new ArrayList<>();
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-list".equals(a)) {
                list = true;
            } else if ("--only".equals(a)) {
                if (i + 1 >= args.length) {
                    System.out.println("--only 需要一个组名");
                    System.exit(2);
                }
                for (String g : args[++i].split(",")) {
                    if (!g.isEmpty()) only.add(g);
                }
            } else {
                System.out.println("未知参数：" + a);
                System.exit(2);
            }
        }

        Path root = Paths.get("").toAbsolutePath().normalize();
        List<Scenario> all = scenarios(root);

        if (list) {
            for (Scenario s : all) {
                System.out.println(s.name);
            }
            return;
        }

        int total = 0;
        int pass = 0;
        for (Scenario s : all) {
            if (!only.isEmpty() && !only.contains(s.group)) continue;
            total++;
            String err;
            try {
                err = s.body.run();
            } catch (Throwable t) {
                err = "检查过程抛异常：" + t;
            }
            if (err == null) {
                pass++;
                System.out.println("PASS " + s.name);
            } else {
                System.out.println("FAIL " + s.name + "  " + err);
            }
        }
        if (total == 0) {
            System.out.println("没有匹配的场景（--only " + only + "）");
            System.exit(2);
        }
        System.out.println("结果：通过 " + pass + "/" + total);
        if (pass != total) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- 场景表

    private static List<Scenario> scenarios(Path root) {
        List<Scenario> out = new ArrayList<>();

        // ---------------- basic ----------------

        out.add(new Scenario("basic/add-remove-contains", () -> {
            Bitmap b = new Bitmap();
            if (!b.isEmpty()) return "期望=新位图 isEmpty()==true 实际=false";
            if (b.cardinality() != 0) return "期望=新位图基数 0 实际=" + b.cardinality();

            b.add(10);
            b.add(10);
            b.add(20);
            if (!b.contains(10) || !b.contains(20)) return "期望=add 后 contains(10) 与 contains(20) 都为 true 实际=false";
            if (b.contains(15)) return "期望=未 add 的 15 不 contains 实际=true";
            if (b.cardinality() != 2) return "期望=重复 add 后基数 2 实际=" + b.cardinality();

            b.remove(10);
            b.remove(10);
            if (b.contains(10)) return "期望=remove 后不 contains(10) 实际=true";
            if (b.cardinality() != 1) return "期望=remove 后基数 1 实际=" + b.cardinality();
            b.remove(999);
            if (b.cardinality() != 1) return "期望=remove 不存在的下标是空操作，基数仍为 1 实际=" + b.cardinality();

            b.clear();
            if (!b.isEmpty() || b.cardinality() != 0) return "期望=clear 后为空且基数 0 实际=isEmpty=" + b.isEmpty() + " 基数=" + b.cardinality();

            String e;
            e = expectError(() -> b.add(-1), "add(-1)");
            if (e != null) return e;
            e = expectError(() -> b.remove(-1), "remove(-1)");
            if (e != null) return e;
            e = expectError(() -> b.contains(-1), "contains(-1)");
            if (e != null) return e;
            e = expectError(() -> b.add(Integer.MIN_VALUE), "add(Integer.MIN_VALUE)");
            if (e != null) return e;
            return null;
        }));

        out.add(new Scenario("basic/iterator-first-last", () -> {
            Bitmap b = new Bitmap();
            int[] seeds = {9, 1, 7, 3, 5};
            for (int v : seeds) {
                b.add(v);
            }
            String e = expectElements("iterator 升序", b, new int[]{1, 3, 5, 7, 9});
            if (e != null) return e;
            if (b.first() != 1) return "期望=first()==1 实际=" + b.first();
            if (b.last() != 9) return "期望=last()==9 实际=" + b.last();

            Bitmap wide = new Bitmap();
            wide.add(0);
            wide.add(Integer.MAX_VALUE);
            wide.add(12345678);
            e = expectElements("跨越整个下标空间的迭代", wide, new int[]{0, 12345678, Integer.MAX_VALUE});
            if (e != null) return e;
            if (wide.first() != 0) return "期望=first()==0 实际=" + wide.first();
            if (wide.last() != Integer.MAX_VALUE) return "期望=last()==Integer.MAX_VALUE 实际=" + wide.last();

            Bitmap empty = new Bitmap();
            e = expectElements("空位图迭代", empty, new int[0]);
            if (e != null) return e;
            e = expectError(empty::first, "空位图 first()");
            if (e != null) return e;
            e = expectError(empty::last, "空位图 last()");
            if (e != null) return e;
            return null;
        }));

        out.add(new Scenario("basic/ten-power-six-random-ops", () -> {
            final int domain = 8192;
            Random rnd = new Random(0x5EEDL);
            Bitmap b = new Bitmap();
            Set<Integer> ref = new HashSet<>();
            for (int i = 0; i < 1_000_000; i++) {
                int idx = rnd.nextInt(domain);
                if (rnd.nextBoolean()) {
                    b.add(idx);
                    ref.add(idx);
                } else {
                    b.remove(idx);
                    ref.remove(idx);
                }
            }
            if (b.cardinality() != ref.size()) {
                return "期望=10^6 次随机增删后基数与 HashSet 一致（" + ref.size() + "） 实际=" + b.cardinality();
            }
            for (int v = 0; v < domain; v++) {
                if (b.contains(v) != ref.contains(v)) {
                    return "期望=下标 " + v + " 的置位状态与 HashSet 一致 实际=不一致（HashSet=" + ref.contains(v) + "）";
                }
            }
            return null;
        }));

        // ---------------- setop ----------------

        out.add(new Scenario("setop/and-or-andnot-xor", () -> {
            Bitmap a = bitmapOf(1, 2, 3, 100, 65536, 65537, 200000);
            Bitmap b = bitmapOf(2, 3, 4, 5, 65536, 999999);

            String e = expectElements("a & b", a.and(b), new int[]{2, 3, 65536});
            if (e != null) return e;
            e = expectElements("a | b", a.or(b), new int[]{1, 2, 3, 4, 5, 100, 65536, 65537, 200000, 999999});
            if (e != null) return e;
            e = expectElements("a \\ b", a.andNot(b), new int[]{1, 100, 65537, 200000});
            if (e != null) return e;
            e = expectElements("b \\ a", b.andNot(a), new int[]{4, 5, 999999});
            if (e != null) return e;
            e = expectElements("a ^ b", a.xor(b), new int[]{1, 4, 5, 100, 65537, 200000, 999999});
            if (e != null) return e;

            if (a.and(b).cardinality() != 3) return "期望=a&b 基数 3 实际=" + a.and(b).cardinality();
            if (a.or(b).cardinality() != 10) return "期望=a|b 基数 10 实际=" + a.or(b).cardinality();
            if (a.xor(b).cardinality() != 7) return "期望=a^b 基数 7 实际=" + a.xor(b).cardinality();
            if (a.andNot(b).cardinality() + b.andNot(a).cardinality() != 7) {
                return "期望=两个差集基数之和等于对称差基数 7 实际="
                        + (a.andNot(b).cardinality() + b.andNot(a).cardinality());
            }

            String n;
            n = expectError(() -> a.and(null), "and(null)");
            if (n != null) return n;
            n = expectError(() -> a.or(null), "or(null)");
            if (n != null) return n;
            n = expectError(() -> a.andNot(null), "andNot(null)");
            if (n != null) return n;
            n = expectError(() -> a.xor(null), "xor(null)");
            if (n != null) return n;
            return null;
        }));

        out.add(new Scenario("setop/inputs-not-modified", () -> {
            Bitmap a = bitmapOf(1, 2, 3, 100, 65536, 65537, 200000);
            Bitmap b = bitmapOf(2, 3, 4, 5, 65536, 999999);
            int[] beforeA = flatten(a);
            int[] beforeB = flatten(b);

            Bitmap and = a.and(b);
            Bitmap or = a.or(b);
            Bitmap diff = a.andNot(b);
            Bitmap x = a.xor(b);

            if (!Arrays.equals(beforeA, flatten(a))) return "期望=进行集合运算后 this 不变 实际=被改动";
            if (!Arrays.equals(beforeB, flatten(b))) return "期望=进行集合运算后入参 other 不变 实际=被改动";
            if (a.cardinality() != beforeA.length || b.cardinality() != beforeB.length) {
                return "期望=集合运算后入参基数不变 实际=a=" + a.cardinality() + " b=" + b.cardinality();
            }

            if (and == a || and == b || or == a || or == b || diff == a || diff == b || x == a || x == b) {
                return "期望=四个集合运算都返回新对象 实际=返回了入参本身";
            }

            or.add(777777);
            and.remove(2);
            diff.add(888888);
            x.remove(3);
            if (a.contains(777777) || b.contains(777777) || a.contains(888888) || b.contains(888888)) {
                return "期望=改动结果不影响任何入参 实际=入参被污染";
            }
            if (!Arrays.equals(beforeA, flatten(a))) return "期望=改动结果后 this 仍不变 实际=被改动";
            if (!Arrays.equals(beforeB, flatten(b))) return "期望=改动结果后入参 other 仍不变 实际=被改动";
            return null;
        }));

        out.add(new Scenario("setop/empty-identity-and-edge", () -> {
            Bitmap empty = new Bitmap();
            Bitmap s = bitmapOf(3, 5, 8);

            String e = expectElements("empty | s", empty.or(s), new int[]{3, 5, 8});
            if (e != null) return e;
            e = expectElements("s | empty", s.or(empty), new int[]{3, 5, 8});
            if (e != null) return e;
            e = expectElements("empty ^ s", empty.xor(s), new int[]{3, 5, 8});
            if (e != null) return e;
            e = expectElements("s ^ empty", s.xor(empty), new int[]{3, 5, 8});
            if (e != null) return e;
            if (!s.and(empty).isEmpty() || !empty.and(s).isEmpty()) return "期望=空位图是 and 的零元 实际=非空";
            e = expectElements("s \\ empty", s.andNot(empty), new int[]{3, 5, 8});
            if (e != null) return e;
            if (!empty.andNot(s).isEmpty()) return "期望=empty \\ s 为空 实际=非空";
            if (!empty.or(empty).isEmpty()) return "期望=empty | empty 为空 实际=非空";
            if (!empty.xor(empty).isEmpty()) return "期望=empty ^ empty 为空 实际=非空";
            if (!empty.and(empty).isEmpty()) return "期望=empty & empty 为空 实际=非空";

            if (!s.xor(s).isEmpty()) return "期望=s ^ s 为空 实际=非空";
            if (!s.andNot(s).isEmpty()) return "期望=s \\ s 为空 实际=非空";
            e = expectElements("s & s", s.and(s), new int[]{3, 5, 8});
            if (e != null) return e;

            Bitmap disjoint = bitmapOf(10, 11);
            if (!s.and(disjoint).isEmpty()) return "期望=不相交集合的 and 为空 实际=非空";
            e = expectElements("s | disjoint", s.or(disjoint), new int[]{3, 5, 8, 10, 11});
            if (e != null) return e;

            Bitmap sub = bitmapOf(3);
            e = expectElements("s & sub", s.and(sub), new int[]{3});
            if (e != null) return e;
            e = expectElements("s \\ sub", s.andNot(sub), new int[]{5, 8});
            if (e != null) return e;
            if (!sub.andNot(s).isEmpty()) return "期望=sub \\ s 为空 实际=非空";

            if (s.first() != 3) return "期望=s.first()==3 实际=" + s.first();
            if (s.last() != 8) return "期望=s.last()==8 实际=" + s.last();
            return null;
        }));

        // ---------------- wire ----------------

        out.add(new Scenario("wire/deterministic-bytes-order-independent", () -> {
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
            Random rnd = new Random(20240925L);
            for (int i = order.length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }
            for (int v : order) {
                shuffled.add(v);
            }

            byte[] bytes = asc.toBytes();
            if (!Arrays.equals(bytes, asc.toBytes())) return "期望=同一集合两次 toBytes() 逐字节相同 实际=不同";
            if (!Arrays.equals(bytes, desc.toBytes())) return "期望=降序插入与升序插入的字节逐字节相同 实际=不同";
            if (!Arrays.equals(bytes, shuffled.toBytes())) return "期望=乱序插入与升序插入的字节逐字节相同 实际=不同";

            String e = expectElements("字节往返", Bitmap.fromBytes(bytes), values);
            if (e != null) return e;

            Bitmap denseAsc = new Bitmap();
            Bitmap denseDesc = new Bitmap();
            for (int i = 0; i < 100_000; i++) {
                denseAsc.add(i);
                denseDesc.add(99_999 - i);
            }
            if (!Arrays.equals(denseAsc.toBytes(), denseDesc.toBytes())) {
                return "期望=稠密集合不同插入顺序的字节逐字节相同 实际=不同";
            }
            if (Bitmap.fromBytes(denseAsc.toBytes()).cardinality() != 100_000) {
                return "期望=稠密集合往返后基数 100000 实际=" + Bitmap.fromBytes(denseAsc.toBytes()).cardinality();
            }

            Bitmap empty = new Bitmap();
            if (!Bitmap.fromBytes(empty.toBytes()).isEmpty()) return "期望=空位图往返后仍为空 实际=非空";

            Bitmap other = bitmapOf(3, 5, 8);
            if (Arrays.equals(bytes, other.toBytes())) return "期望=不同集合的字节不同 实际=相同";
            return null;
        }));

        out.add(new Scenario("wire/malformed-bytes-rejected", () -> {
            Bitmap b = bitmapOf(1, 2, 3, 1000, 50000);
            byte[] good = b.toBytes();

            String e;
            e = expectError(() -> Bitmap.fromBytes(null), "fromBytes(null)");
            if (e != null) return e;
            e = expectError(() -> Bitmap.fromBytes(new byte[0]), "fromBytes(空数组)");
            if (e != null) return e;
            e = expectError(() -> Bitmap.fromBytes(new byte[]{0x01}), "fromBytes(只有版本字节)");
            if (e != null) return e;
            e = expectError(() -> Bitmap.fromBytes(new byte[]{0x01, 0x00, 0x00}), "fromBytes(头部不足)");
            if (e != null) return e;
            e = expectError(() -> Bitmap.fromBytes(new byte[]{0x02, 0x00, 0x00, 0x00, 0x00}), "fromBytes(版本字节 0x02)");
            if (e != null) return e;
            e = expectError(() -> Bitmap.fromBytes(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00}), "fromBytes(版本字节 0x00)");
            if (e != null) return e;
            e = expectError(() -> Bitmap.fromBytes(new byte[]{0x01, 0x00, 0x00, 0x00, 0x09}), "fromBytes(声明长度大于实际)");
            if (e != null) return e;

            byte[] truncated = Arrays.copyOf(good, good.length - 1);
            e = expectError(() -> Bitmap.fromBytes(truncated), "fromBytes(截断 1 字节)");
            if (e != null) return e;
            byte[] half = Arrays.copyOf(good, good.length / 2);
            e = expectError(() -> Bitmap.fromBytes(half), "fromBytes(截断一半)");
            if (e != null) return e;
            byte[] extra = Arrays.copyOf(good, good.length + 1);
            e = expectError(() -> Bitmap.fromBytes(extra), "fromBytes(尾部多 1 字节)");
            if (e != null) return e;

            byte[] badVersion = good.clone();
            badVersion[0] = (byte) 0x7F;
            e = expectError(() -> Bitmap.fromBytes(badVersion), "fromBytes(版本字节 0x7F)");
            if (e != null) return e;

            e = expectElements("合法字节仍可解析", Bitmap.fromBytes(good), new int[]{1, 2, 3, 1000, 50000});
            if (e != null) return e;
            return null;
        }));

        // ---------------- density ----------------

        out.add(new Scenario("density/sparse-memory-bound", () -> {
            String scan = scanNoBitSet(root);
            if (scan != null) return scan;

            Bitmap s = new Bitmap();
            for (int i = 0; i < 10_000; i++) {
                s.add(i * 1000);
            }
            if (s.cardinality() != 10_000) return "期望=稀疏集合基数 10000 实际=" + s.cardinality();
            if (s.first() != 0) return "期望=first()==0 实际=" + s.first();
            if (s.last() != 9_999_000) return "期望=last()==9999000 实际=" + s.last();
            if (!s.contains(0) || !s.contains(9_999_000) || s.contains(1) || s.contains(999)) {
                return "期望=端点置位、相邻空位不置位 实际=不匹配（contains(0)=" + s.contains(0)
                        + " contains(9999000)=" + s.contains(9_999_000) + " contains(1)=" + s.contains(1) + "）";
            }
            String m = checkMem("[0,10^7) 上万个稀疏点", s);
            if (m != null) return m;

            byte[] bytes = s.toBytes();
            Bitmap back = Bitmap.fromBytes(bytes);
            if (back.cardinality() != 10_000) return "期望=稀疏集合往返后基数 10000 实际=" + back.cardinality();
            if (!Arrays.equals(bytes, back.toBytes())) return "期望=稀疏集合往返后字节逐字节相同 实际=不同";
            return null;
        }));

        out.add(new Scenario("density/setop-results-compressed", () -> {
            Bitmap empty = new Bitmap();
            Bitmap sparse = new Bitmap();
            for (int i = 0; i < 10_000; i++) {
                sparse.add(i * 1000);
            }
            Bitmap full = new Bitmap();
            for (int i = 0; i < 10_000_000; i++) {
                full.add(i);
            }
            Bitmap block = new Bitmap();
            for (int i = 200_000; i < 400_000; i++) {
                block.add(i);
            }

            String e = expectElements("empty | sparse", empty.or(sparse), flatten(sparse));
            if (e != null) return e;
            if (!empty.and(sparse).isEmpty() || !sparse.and(empty).isEmpty()) {
                return "期望=空位图是 and 的零元 实际=非空";
            }
            e = expectElements("sparse \\ empty", sparse.andNot(empty), flatten(sparse));
            if (e != null) return e;

            String m = checkMem("入参 full（[0,10^7) 连续）", full);
            if (m != null) return m;
            m = checkMem("入参 sparse（10^4 个离散点）", sparse);
            if (m != null) return m;
            m = checkMem("入参 block（20 万连续）", block);
            if (m != null) return m;

            Bitmap union = sparse.or(full);
            if (union.cardinality() != 10_000_000) return "期望=sparse | full 基数 10000000 实际=" + union.cardinality();
            m = checkMem("sparse | full", union);
            if (m != null) return m;

            Bitmap inter = sparse.and(full);
            if (inter.cardinality() != 10_000) return "期望=sparse & full 基数 10000 实际=" + inter.cardinality();
            if (!Arrays.equals(inter.toBytes(), sparse.toBytes())) return "期望=sparse & full 与 sparse 相等 实际=不等";
            m = checkMem("sparse & full", inter);
            if (m != null) return m;

            Bitmap diff = full.andNot(sparse);
            if (diff.cardinality() != 9_990_000) return "期望=full \\ sparse 基数 9990000 实际=" + diff.cardinality();
            m = checkMem("full \\ sparse", diff);
            if (m != null) return m;

            Bitmap sym = sparse.xor(full);
            if (sym.cardinality() != 9_990_000) return "期望=sparse ^ full 基数 9990000 实际=" + sym.cardinality();
            if (!Arrays.equals(sym.toBytes(), diff.toBytes())) return "期望=sparse ^ full 与 full \\ sparse 相等 实际=不等";
            m = checkMem("sparse ^ full", sym);
            if (m != null) return m;

            if (!sparse.andNot(full).isEmpty()) return "期望=sparse \\ full 为空 实际=非空";

            Bitmap capped = full.and(block);
            if (capped.cardinality() != 200_000) return "期望=full & block 基数 200000 实际=" + capped.cardinality();
            m = checkMem("full & block", capped);
            if (m != null) return m;

            Bitmap cross = block.or(sparse);
            if (cross.cardinality() != 209_800) return "期望=block | sparse 基数 209800 实际=" + cross.cardinality();
            m = checkMem("block | sparse（连续与离散交叉）", cross);
            if (m != null) return m;

            Bitmap rest = cross.andNot(block);
            if (rest.cardinality() != 9_800) return "期望=cross \\ block 基数 9800 实际=" + rest.cardinality();
            m = checkMem("cross \\ block", rest);
            if (m != null) return m;

            Bitmap xored = cross.xor(block);
            if (xored.cardinality() != 9_800) return "期望=cross ^ block 基数 9800 实际=" + xored.cardinality();
            m = checkMem("cross ^ block", xored);
            if (m != null) return m;
            return null;
        }));

        return out;
    }

    // ------------------------------------------------------------- 辅助

    private static Bitmap bitmapOf(int... values) {
        Bitmap b = new Bitmap();
        for (int v : values) {
            b.add(v);
        }
        return b;
    }

    /**
     * 按升序把置位下标收集成数组。超过 {@link #ITER_CAP} 时抛异常（把「迭代不收敛」变成
     * 一个可判定的失败，而不是把固定件挂死）。
     */
    private static int[] flatten(Bitmap b) {
        List<Integer> out = new ArrayList<>();
        for (int v : b) {
            out.add(v);
            if (out.size() > ITER_CAP) {
                throw new IllegalStateException("iterator 产出元素数超过 " + ITER_CAP + "（可能未收敛）");
            }
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    /** 校验元素集合（要求升序迭代）。 */
    private static String expectElements(String what, Bitmap b, int[] expected) {
        int[] got = flatten(b);
        if (got.length != expected.length) {
            return "期望=" + what + " 共 " + expected.length + " 个元素 实际=" + got.length + " 个";
        }
        for (int i = 0; i < got.length; i++) {
            if (got[i] != expected[i]) {
                return "期望=" + what + " 第 " + i + " 个元素为 " + expected[i] + "（升序） 实际=" + got[i];
            }
        }
        return null;
    }

    private static String checkMem(String what, Bitmap b) {
        long used = b.memoryBytes();
        if (used > MEM_LIMIT) {
            return "期望=" + what + " 的 memoryBytes() <= " + MEM_LIMIT + " 实际=" + used;
        }
        if (used <= 0) {
            return "期望=" + what + " 的 memoryBytes() > 0 实际=" + used;
        }
        return null;
    }

    private static String expectError(Runnable body, String what) {
        try {
            body.run();
        } catch (BitmapError expected) {
            return null;
        } catch (Throwable t) {
            return "期望=" + what + " 抛 BitmapError 实际=抛了 " + t.getClass().getName();
        }
        return "期望=" + what + " 抛 BitmapError 实际=没有抛出";
    }

    /** 扫描 src/roarish/*.java，确认内部表示没有用 java.util.BitSet。 */
    private static String scanNoBitSet(Path root) throws Exception {
        Path dir = root.resolve("src/roarish");
        if (!Files.isDirectory(dir)) {
            return "期望=src/roarish 目录存在 实际=缺失（当前目录 " + root + "）";
        }
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        List<String> bad = new ArrayList<>();
        for (Path p : files) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            if (text.contains("BitSet")) {
                bad.add(p.getFileName().toString());
            }
        }
        if (!bad.isEmpty()) {
            return "期望=内部表示不使用 java.util.BitSet 实际=命中 " + bad;
        }
        return null;
    }

    private Checker() {
    }
}
