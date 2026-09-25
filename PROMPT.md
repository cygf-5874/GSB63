我们准备把位图索引接进检索链路，需要从零写一个 Java 17 库 roarish（只用 JDK 标准库，
构建走 scripts/*.sh，不许引入 Maven/Gradle 或任何第三方依赖；目标场景是稀疏集合）。
README「对外契约」一节列了 10 条；src/ 下的方法是空壳，
test/ 里 12 个用例当前全红 —— 能编译，没有语法错。

任务：把这 10 条实现出来。

验收（check/ 是固定验收程序，别改）：
- bash scripts/check.sh 退出码 0，10 个场景全过（basic 3 + setop 3 + wire 2 + density 2）；
- bash scripts/test.sh 全绿。

约束：
1. 不改 check/、不改 test/ 里既有用例的断言；类名、方法签名、异常类型不变。
2. 只用 JDK 标准库；不许用 java.util.BitSet 作为内部表示（判据会扫描源码）。
3. 内存上限与字节确定性由固定件判定：稀疏集合必须压得住，同一集合的字节必须逐字节一致。
4. 入参是否被修改按 README 写死的那一条为准，集合运算不许改入参。
