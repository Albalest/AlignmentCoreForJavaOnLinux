# B样条拟合与优化接口文档（Java）

## 概述

本模块为铁路线路平顺性优化结果提供 **B样条曲线拟合** 和 **直接B样条优化** 两种能力，使设计人员能够通过编辑少量控制点对调整量曲线进行二次编辑。

---

## 数据结构

### `OptData` — 数据点

| 字段 | 类型 | 说明 |
|------|------|------|
| `x` | `double` | 里程 (m) |
| `y` | `double` | 偏差值 / 调整量 (mm) |
| `y_up` | `double` | 调整上限 (mm) |
| `y_down` | `double` | 调整下限 (mm) |

### `OptDataVector` — 数据点列表

```java
OptDataVector dataList = new OptDataVector();
dataList.add(new OptData(1000.0, 5.2, 8.0, -8.0));
```

### `CommonConstrain` — 优化约束参数

```java
CommonConstrain c = new CommonConstrain();
c.setM_ShortWaveLimit(3.0);       // 短波不平顺限值 (mm)
c.setM_ShortWaveLength(20.0);     // 短波弦长 (m)
c.setM_SuperSlope(0.5);           // 顺坡率 (mm/m)
c.setM_factor(1.0);               // 偏离度权重
c.setIsFixed(true);               // 首尾是否固定
c.setIsPlane(true);                // true=平面(拨道), false=纵断面(抬道)
```

### `BSplineFitInfo` — B样条拟合精度信息

| 方法 | 返回类型 | 说明 |
|------|---------|------|
| `getIsValid()` | `boolean` | 拟合是否有效 |
| `getNumControlPts()` | `int` | 控制点数量 |
| `getRmse()` | `double` | 均方根误差 (mm) |
| `getMaxError()` | `double` | 最大绝对误差 (mm) |

---

## 两种方案对比

| 特性 | 方案一：后处理拟合 | 方案二：直接B样条优化 |
|------|-------------------|---------------------|
| 流程 | 先离散优化，再拟合B样条 | B样条控制点**直接**作为优化变量 |
| 调用方法 | `StartOpt()` + `FitBSplineAdaptive()` | `StartOptBSpline()` |
| 控制点 | 后处理生成 | Ipopt 直接求解 |
| 曲线质量 | 依赖拟合精度 | **天然 C? 连续**，无拟合误差 |
| 场景 | 已有离散优化结果的场景 | 全新优化的场景 |

---

## 方案一：离散优化 + B样条后处理拟合

```java
// ========== 准备输入数据 ==========
OptDataVector measureData = new OptDataVector();
// 添加测点数据（里程, 偏差值, 上限, 下限）
measureData.add(new OptData(1000.0, 5.2, 8.0, -8.0));
measureData.add(new OptData(1005.0, 4.8, 8.0, -8.0));
// ... 更多测点 ...

CommonConstrain constrain = new CommonConstrain();
constrain.setM_ShortWaveLimit(3.0);
constrain.setM_ShortWaveLength(20.0);
constrain.setM_SuperSlope(0.5);
constrain.setIsFixed(true);
constrain.setIsPlane(true);

// ========== 第1步：Ipopt离散点优化 ==========
OpterProcesser opter = new OpterProcesser(measureData, constrain);
opter.StartOpt();                           // ← 返回各测点的最优调整量

// 获取离散优化结果
OptDataVector optResult = opter.GetOptResult();

// ========== 第2步：B样条后处理拟合 ==========

// 方式A：指定控制点数量（均匀分布）
opter.FitBSpline(12);                      // 12个控制点，全线均匀分布

// 方式B（推荐）：Douglas-Peucker自适应拟合
opter.FitBSplineAdaptive(0.3);             // 容差0.3mm，自动确定控制点数量和位置
// 参数说明：
//   tolerance=0.1 → 控制点多，拟合精确
//   tolerance=0.3 → 推荐，精度与简洁性平衡
//   tolerance=0.5 → 控制点少，适合快速编辑

// 查看拟合精度
BSplineFitInfo info = opter.GetBSplineFitInfo();
System.out.println("控制点数量: " + info.getNumControlPts());
System.out.println("RMSE: " + info.getRmse() + " mm");
System.out.println("最大误差: " + info.getMaxError() + " mm");

// ========== 第3步：获取并编辑控制点 ==========
OptDataVector ctrlPts = opter.GetBSplineControlPoints();
for (int i = 0; i < ctrlPts.size(); i++) {
    OptData pt = ctrlPts.get(i);
    System.out.println("控制点" + i + ": 里程=" + pt.getX() + ", 调整量=" + pt.getY());
}

// 设计师编辑：移动第3个控制点
opter.MoveBSplineControlPoint(3, new OptData(1020.0, -2.5, 0, 0));

// 在指定位置插入新的控制点
opter.InsertBSplineControlPoint(new OptData(1030.0, -1.8, 0, 0));

// 删除控制点（首尾不可删除）
opter.RemoveBSplineControlPoint(5);

// ========== 第4步：应用编辑结果 ==========

// 获取编辑后的调整量曲线
OptDataVector adjusted = opter.GetBSplineFitResult();

// 获取编辑后的最终偏差曲线（原始偏差 + B样条调整量）
OptDataVector profile = opter.GetBSplineAdjustedProfile();

// 将B样条结果写回优化结果（使后续不平顺计算等使用最新数据）
opter.ApplyBSplineResult();

// 计算并检查优化后的不平顺
OptDataVector smoothness = new OptDataVector();
opter.SmoothCalOpt(smoothness, 20.0);      // 20m弦不平顺
```

---

## 方案二：直接B样条优化（推荐）

```java
// ========== 准备输入数据 ==========
OptDataVector measureData = new OptDataVector();
// ... 添加测点数据 ...

CommonConstrain constrain = new CommonConstrain();
// ... 设置约束参数 ...

// ========== 一行完成全部优化 ==========
OpterProcesser opter = new OpterProcesser(measureData, constrain);

// 方式A：自动确定控制点（推荐）
opter.StartOptBSpline();                         // DP自动确定控制点数量和位置

// 方式B：指定控制点数量
opter.StartOptBSpline(15);                       // 15个控制点，均匀分布

// 方式C：指定DP容差
opter.StartOptBSpline(0, 0.5);                   // DP容差0.5mm（控制点更少）

// ========== 获取结果 ==========

// 优化后的各测点调整量
OptDataVector result = opter.GetBSplineOptResult();

// B样条控制点（可直接供设计师编辑）
OptDataVector ctrlPts = opter.GetBSplineOptControlPoints();

// 优化后的最终偏差 = 原始偏差 + 调整量
OptDataVector finalProfile = opter.GetBSplineOptProfile();
```

---

## 控制点编辑接口（两种方案通用）

| 方法 | 说明 |
|------|------|
| `GetBSplineControlPoints()` | 获取当前控制点列表 |
| `MoveBSplineControlPoint(index, pt)` | 移动指定控制点 |
| `InsertBSplineControlPoint(pt, index)` | 插入控制点（index=-1 表示末尾） |
| `RemoveBSplineControlPoint(index)` | 删除控制点（首尾不可删） |
| `GetBSplineFitInfo()` | 获取拟合精度 RMSE / 最大误差 |

---

## 完整示例：从优化到编辑到验证

```java
import AlOpter.*;

public class OptDemo {
    public static void main(String[] args) {
        // ---- 构造输入数据 ----
        OptDataVector data = new OptDataVector();
        for (double lc = 1000; lc <= 2000; lc += 5) {
            double dev = 3.0 * Math.sin(lc / 100.0) + 1.0;
            data.add(new OptData(lc, dev, 10.0, -10.0));
        }

        CommonConstrain c = new CommonConstrain();
        c.setM_ShortWaveLimit(3.0);
        c.setM_ShortWaveLength(20.0);
        c.setM_SuperSlope(0.5);
        c.setIsFixed(true);
        c.setIsPlane(true);

        // ---- 方案二：直接B样条优化 ----
        OpterProcesser opter = new OpterProcesser(data, c);
        boolean ok = opter.StartOptBSpline(0, 0.3);
        System.out.println("优化" + (ok ? "成功" : "失败"));

        // ---- 查看控制点 ----
        OptDataVector ctrl = opter.GetBSplineOptControlPoints();
        System.out.println("控制点数量: " + ctrl.size());
        for (int i = 0; i < ctrl.size(); i++) {
            OptData p = ctrl.get(i);
            System.out.printf("  P%d: 里程=%.1f, 调整量=%.2f\n",
                i, p.getX(), p.getY());
        }

        // ---- 编辑控制点 ----
        opter.MoveBSplineControlPoint(3, new OptData(1150.0, -2.0, 0, 0));

        // ---- 获取最终结果 ----
        OptDataVector finalProfile = opter.GetBSplineOptProfile();
        System.out.println("最终偏差点数: " + finalProfile.size());
    }
}
```

---

## 内部原理简述

### 方案一：后处理拟合

```
离散优化结果 (各测点调整量)
    │
    ├─ Douglas-Peucker 提取关键顶点
    │    在曲线变化剧烈处加密，平缓处稀疏
    │
    ├─ 最小二乘 B 样条拟合
    │    min || N·P - Y ||?   →  Cholesky 分解求解
    │
    └─ 设计师编辑控制点 → 重新求值
```

### 方案二：直接B样条优化

```
B样条控制点 P?...P_{m-1} (优化变量)
    │
    ├─ B样条求值: adj(i) = Σ N_j(t_i)·P_j
    │   基函数矩阵 N_j(t_i) 预计算为 double
    │
    ├─ Ipopt 求解:
    │   目标: 平滑度 + 偏离度
    │   约束: 短波不平顺限值
    │
    └─ 输出控制点 → 设计师可直接编辑
```
