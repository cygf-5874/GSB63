# roarish

一个**区间/容器压缩位图**小库（Java 17，只用 JDK 标准库，无第三方依赖）。
下标空间是 `[0, 2^31)`，也就是全部非负 `int`；目标场景是**稀疏集合**。

它提供逐位读写、精确基数、四个集合运算（返回新位图）、升序迭代、
确定性的字节序列化，以及一个驻留内存的自报口径 `memoryBytes()`。

```java
Bitmap b = new Bitmap();
b.add(10);
b.add(20);
b.contains(10);                       // true
b.cardinality();                      // 2
Bitmap u = b.or(other);               // 新位图；b / other 都不被改动
byte[] wire = b.toBytes();            // 同一集合的字节与插入顺序无关
Bitmap back = Bitmap.fromBytes(wire); // 集合相等
```

## 目录

```
src/roarish/BitmapError.java      统一异常类型
src/roarish/RangeCodec.java       区间（run）编解码辅助位（当前为空壳）
src/roarish/Bitmap.java           位图（当前为空壳）
test/roarish/BitmapTest.java      既有用例（12 个，手写 runner，无第三方框架）
scripts/build.sh                  编译 src/ 与 test/ 到 out/
scripts/test.sh                   跑既有用例
scripts/check.sh                  固定验收入口（勿改）
check/Checker.java                固定验收程序（勿改）
```

## 语言版本前提

- Java 17（`javac` / `java`）。
- **只用 JDK 标准库**：不引入 Maven / Gradle，不引入任何第三方依赖。
- 构建产物放在 `out/`，已在 `.gitignore` 中忽略。

## 怎么跑

```bash
bash scripts/build.sh      # 编译到 out/
bash scripts/test.sh       # 既有用例
bash scripts/check.sh      # 固定验收；支持 -list 与 --only <组名>
bash scripts/check.sh -list              # 列出全部验收场景
bash scripts/check.sh --only density     # 只跑一组
```

起点状态：`src/` 下的方法是空壳，调用即抛 `UnsupportedOperationException`；
`bash scripts/test.sh` 打印 12 个 `FAIL`（**能编译，没有语法错**）；
`bash scripts/check.sh` 打印 `结果：通过 0/10` 并以退出码 1 结束。

> `check/` 下的 `Checker.java` 是**固定验收程序，勿改**；`test/` 里既有用例的断言、
> `src/roarish/BitmapError.java` 的类名与继承关系，同样都是契约的一部分，勿改。
> 公开的**类型名、方法名、签名与异常类型**已经是最终形态（可以新增内部方法与私有成员），
> 不要改动下面 `API` 一节列出的这些。

## 对外契约

下面 10 条是 `roarish` 的**对外契约**，它们是本题验收点的唯一出处。
**它们是契约，不是「当前行为」的转述**；实现方式不限，但必须让这 10 条同时成立。

1. **逐位读写与越界**。`add(int)` / `remove(int)` / `contains(int)` 的下标范围是
   `[0, 2^31)`，即所有非负 `int`；下标为负时抛 `BitmapError`。
   `add` 重复置同一元素、`remove` 清一个本来就没置位的元素，都是**幂等**的空操作。
2. **基数与空判定**。`cardinality()` 返回置位元素个数且必须**精确**（不是估计）；
   `isEmpty()` 等价于 `cardinality() == 0`；`clear()` 清空。
3. **集合运算返回新位图**。`and` / `or` / `andNot` / `xor` 都返回**新建**的位图：
   返回对象与 `this`、与入参 `other` 都不是同一个对象；运算过程**不得修改** `this` 与 `other`
   （事后改动返回值也不得影响入参）。入参为 `null` 抛 `BitmapError`。
4. **升序迭代与两端**。`iterator()` 按**升序**产出全部置位下标（不重不漏、不依赖内部表示顺序）；
   `Bitmap` 实现 `Iterable<Integer>`。`first()` / `last()` 返回最小 / 最大置位下标，
   **空位图调用时抛 `BitmapError`**。
5. **确定性序列化**。`toBytes()` / `fromBytes()` 往返后集合相等（元素完全一致）。
   **同一个集合必须产生逐字节相同的字节，且与元素的插入顺序无关**（先 `add` 后 `add`、
   倒序 `add`、乱序 `add` 都要得到 `Arrays.equals` 相等的字节）。
   wire 格式的**第一字节是版本号**，当前必须为 `0x01`。
6. **反序列化必须拒绝坏输入**。`fromBytes` 对下列输入一律抛 `BitmapError`，
   **不许越界读**（不许抛 `ArrayIndexOutOfBoundsException` 之类的其他异常）：
   `null`、长度不足以容纳头部、版本字节不是 `0x01`、声明长度与实际长度不符（截断或尾部多余字节）、
   以及 payload 本身语法不合法。合法字节必须能解析出与原集合相等的位图。
7. **内存上限（必须压缩）**。`memoryBytes()` 自报本对象驻留内存的字节数。
   对「在 `[0, 10^7)` 上每隔 1000 取一个、共 `10^4` 个元素」的稀疏集合，
   `memoryBytes() <= 1 MiB`（`1 << 20`）。即驻留内存必须与**压缩后的规模**相称，
   而不是与元素最大下标相称 —— 不许直接开按位数组（也不许用 `java.util.BitSet` 当内部表示）。
8. **空位图恒等元 + 运算结果同样压得住**。空位图是 `or` / `xor` 的恒等元、
   `and` / `andNot` 的零元。对元素分布**稀疏**或**成片连续**的位图，
   四个集合运算的结果以及参与运算的入参，`memoryBytes()` 都必须 `<= 1 MiB`；
   运算不得把结果退化成「按下标上界开满的内存」。
9. **大基数下仍然精确**。经过 `10^6` 次随机 `add` / `remove` 之后，
   `cardinality()` 与逐位 `contains` 都必须与 `java.util.HashSet<Integer>` 对拍的结论一致。
10. **入参修改语义写死**。**只有 `add` / `remove` / `clear` 会修改 `this`**；
   其余所有公开方法（`contains`、`cardinality`、`isEmpty`、`first`、`last`、
   四个集合运算、`iterator`、`toBytes`、`fromBytes`、`memoryBytes`）都**不改动任何入参**。

## API

```
roarish.Bitmap implements Iterable<Integer>
  Bitmap()                    -> 空位图
  #add(int)                   -> void    （只此三者修改自身）
  #remove(int)                -> void
  #clear()                    -> void
  #contains(int)              -> boolean
  #cardinality()              -> int
  #isEmpty()                  -> boolean
  #first()                    -> int     （空位图抛 BitmapError）
  #last()                     -> int     （空位图抛 BitmapError）
  #and(Bitmap)                -> Bitmap  （新对象）
  #or(Bitmap)                 -> Bitmap  （新对象）
  #andNot(Bitmap)             -> Bitmap  （新对象）
  #xor(Bitmap)                -> Bitmap  （新对象）
  #iterator()                 -> Iterator<Integer>（升序）
  #toBytes()                  -> byte[]
  static #fromBytes(byte[])   -> Bitmap  （坏输入抛 BitmapError）
  #memoryBytes()              -> long

roarish.BitmapError extends RuntimeException
roarish.RangeCodec           （区间编解码辅助位；当前为空壳，是否使用不影响验收）
```

## 验收

`check/Checker.java` 是固定验收程序，**不要修改**。它按 4 组共 10 个场景检查上面的契约：

| 组 | 场景 | 对应契约 |
| --- | --- | --- |
| `basic` | `add-remove-contains` | 1、2、10 |
| `basic` | `iterator-first-last` | 2、4 |
| `basic` | `ten-power-six-random-ops` | 9 |
| `setop` | `and-or-andnot-xor` | 3 |
| `setop` | `inputs-not-modified` | 3、10 |
| `setop` | `empty-identity-and-edge` | 8 |
| `wire` | `deterministic-bytes-order-independent` | 5 |
| `wire` | `malformed-bytes-rejected` | 6 |
| `density` | `sparse-memory-bound` | 7 |
| `density` | `setop-results-compressed` | 8 |

共 10 个场景。每个场景独立判定，失败不会遮蔽其余场景。判据全部确定性：
没有墙钟、没有随机源（随机增删用固定种子的 `java.util.Random`）、
内存看 `memoryBytes()` 自报，外加扫描 `src/roarish/*.java` 确认没有用 `java.util.BitSet`。
