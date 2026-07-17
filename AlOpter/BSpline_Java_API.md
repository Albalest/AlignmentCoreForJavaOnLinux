# B样条拟合与优化接口文档（Java）

## 概述

本模块提供 **后处理B样条拟合** 和 **直接B样条优化** 两种能力，使设计人员能够通过编辑少量控制点对调整量曲线进行二次编辑。

B样条曲线由 **控制点 + 次数(Degree) + 节点向量(Knots)** 三者共同定义，三者缺一不可。

---

## 数据结构

### `OptData` — 数据点

| 字段 | 类型 | 说明 |
|------|------|------|
| `x` | `double` | 里程 (m) |
| `y` | `double` | 偏差值 / 调整量 (mm) |
| `y_up` | `double` | 调整上限 (mm) |
| `y_down` | `double` | 调整下限 (mm) |

### `OptDataVector` — 数据点列表（`std::vector<OptData>` 的 SWIG 包装）

```java
OptDataVector dataList = new OptDataVector();
dataList.add(new OptData(1000.0, 5.2, 8.0, -8.0));
```

### `DoubleArr` — 双精度数组（`std::vector<double>` 的 SWIG 包装）

```java
DoubleArr mileages = new DoubleArr();
mileages.add(1000.0);
mileages.add(1001.0);
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
| 流程 | 先离散优化，再拟合B样条 | B样条控制点**直接**作为Ipopt优化变量 |
| 调用方法 | `StartOpt()` + `FitBSplineAdaptive()` | `StartOptBSpline()` |
| 控制点生成 | 后处理 Douglas-Peucker 自适应确定 | Ipopt 直接求解 |
| 曲线质量 | 依赖拟合精度 | **天然 C? 连续**，无拟合误差 |
| 控制点编辑 | 支持（移动/插入/删除） | 支持（需先获取控制点） |
| 场景 | 已有离散优化结果的场景 | 全新优化的场景 |

---

## 方案一：离散优化 + B样条后处理拟合

```java
// ========== 准备输入数据 ==========
OptDataVector measureData = new OptDataVector();
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
opter.StartOpt();

// ========== 第2步：B样条后处理拟合 ==========

// 方式A：指定控制点数量（均匀分布）
opter.FitBSpline(12);                      // 12个控制点，全线均匀分布

// 方式B（推荐）：Douglas-Peucker自适应拟合
opter.FitBSplineAdaptive(0.3);             // 容差0.3mm，自动确定控制点数量和位置

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

// 移动第3个控制点
opter.MoveBSplineControlPoint(3, new OptData(1020.0, -2.5, 0, 0));

// 在指定位置插入新的控制点
opter.InsertBSplineControlPoint(new OptData(1030.0, -1.8, 0, 0));

// 删除控制点（首尾不可删除）
opter.RemoveBSplineControlPoint(5);

// ========== 第4步：应用编辑结果 ==========

// 获取编辑后的调整量曲线（在原始测点里程处求值）
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

## B样条参数获取接口

拟合/优化完成后，可获取完整的 B 样条参数，用于重建曲线或传给 `GetBSplineCurvePoints`：

```java
// 获取B样条次数（degree=3 表示三次B样条）
int degree = opter.GetBSplineDegree();

// 获取节点向量（knot vector）
DoubleArr knots = opter.GetBSplineKnots();
```

> **注意**：`GetBSplineKnots()` 返回的是 **后处理拟合**（`FitBSpline`/`FitBSplineAdaptive`）生成的节点向量。
> 直接优化（`StartOptBSpline`）内部的节点向量不保留，如需对优化结果重建曲线，请使用 `GetBSplineOptCurve`。

---

## 控制点编辑接口（两种方案通用）

| 方法 | 说明 |
|------|------|
| `GetBSplineControlPoints()` | 获取后处理拟合的当前控制点列表 |
| `MoveBSplineControlPoint(index, pt)` | 移动指定控制点 |
| `InsertBSplineControlPoint(pt, index)` | 插入控制点（index=-1 表示末尾） |
| `RemoveBSplineControlPoint(index)` | 删除控制点（首尾不可删） |
| `GetBSplineFitInfo()` | 获取拟合精度（RMSE / 最大误差） |
| `GetBSplineDegree()` | 获取B样条次数 |
| `GetBSplineKnots()` | 获取节点向量 |

### Undo / Redo

每次移动/插入/删除控制点时，BSplineFit 会自动保存前一个状态。

| 方法 | 说明 |
|------|------|
| `UndoBSpline()` | 撤销上一步操作 |
| `RedoBSpline()` | 重做上一步撤销 |
| `CanUndoBSpline()` | 是否可以撤销 |
| `CanRedoBSpline()` | 是否可以重做 |
| `ClearBSplineUndoHistory()` | 清空历史（例如拟合新曲线后自动调用） |

```java
// 编辑控制点...
opter.MoveBSplineControlPoint(2, new OptData(1050.0, -3.0, 0, 0));
opter.RemoveBSplineControlPoint(4);
opter.InsertBSplineControlPoint(new OptData(1100.0, 1.5, 0, 0));

// 撤销：依次回退 插入 → 删除 → 移动
if (opter.CanUndoBSpline()) opter.UndoBSpline();  // 撤销插入
if (opter.CanUndoBSpline()) opter.UndoBSpline();  // 撤销删除
if (opter.CanUndoBSpline()) opter.UndoBSpline();  // 撤销移动

// 重做
if (opter.CanRedoBSpline()) opter.RedoBSpline();
```

---

## 曲线绘制接口

用于在任意指定里程点对 B 样条曲线求值，生成密集点用于前端绘制。

### 后处理拟合曲线 — `GetBSplineFitCurve`

```java
// 构造密集里程数组
DoubleArr mileages = new DoubleArr();
for (double lc = 1000; lc <= 2000; lc += 1.0) {
    mileages.add(lc);
}

// 在指定里程点求后处理拟合曲线值
OptDataVector denseCurve = new OptDataVector();
opter.GetBSplineFitCurve(mileages, denseCurve);
// denseCurve 现在包含 (里程, 调整量) 的密集点集，可直接绘图
```

### 直接优化曲线 — `GetBSplineOptCurve`

```java
// 在指定里程点求直接优化B样条曲线值
OptDataVector optCurve = new OptDataVector();
opter.GetBSplineOptCurve(mileages, optCurve);
```

### 通用静态接口 — `GetBSplineCurvePoints`

已知任意控制点 + degree + knots 时，直接用静态方法构造曲线并求值：

```java
// 从拟合结果获取完整 B 样条参数
int      degree  = opter.GetBSplineDegree();
DoubleArr knots  = opter.GetBSplineKnots();
OptDataVector ctrlPts = opter.GetBSplineControlPoints();

// 构造待求值点
OptDataVector curvePoints = new OptDataVector();
for (double lc = 1000; lc <= 2000; lc += 1.0) {
    curvePoints.add(new OptData(lc, 0));
}

// 通用静态接口：传入完整参数 (控制点 + 节点向量 + 次数)
OpterProcesser.GetBSplineCurvePoints(ctrlPts, knots, degree, curvePoints);
// curvePoints 现在包含 (里程, B样条曲线Y值)
```

---

## 典型绘制流程

```java
// 1. 运行优化（任选一种）
opter.StartOpt();  opter.FitBSplineAdaptive(0.5);    // 方案一
// 或
opter.StartOptBSpline(0, 0.5);                        // 方案二

// 2. 生成绘制点（按 1m 间距）
DoubleArr plotX = new DoubleArr();
for (double lc = 1000; lc <= 2000; lc += 1.0) {
    plotX.add(lc);
}

// 3. 获取B样条曲线绘制点
OptDataVector plotCurve = new OptDataVector();
opter.GetBSplineFitCurve(plotX, plotCurve);   // 后处理拟合
// 或
opter.GetBSplineOptCurve(plotX, plotCurve);   // 直接优化

// 4. 获取控制点用于显示
OptDataVector ctrlPts = opter.GetBSplineControlPoints();  // 或 GetBSplineOptControlPoints

// 5. 传给前端绘图：
//    - 控制点用  ● 标记（可拖拽）
//    - 曲线点用  ─ 连线
```

---

## 完整示例：从优化到编辑到验证

```java
import AlOpter.*;

public class BsplineDemo {
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

        // ---- 获取B样条参数 ----
        int degree = opter.GetBSplineDegree();
        System.out.println("B样条次数: " + degree);

        // ---- 查看控制点 ----
        OptDataVector ctrl = opter.GetBSplineOptControlPoints();
        System.out.println("控制点数量: " + ctrl.size());
        for (int i = 0; i < ctrl.size(); i++) {
            OptData p = ctrl.get(i);
            System.out.printf("  P%d: 里程=%.1f, 调整量=%.2f%n",
                i, p.getX(), p.getY());
        }

        // ---- 编辑控制点 ----
        opter.MoveBSplineControlPoint(3, new OptData(1150.0, -2.0, 0, 0));

        // ---- 生成绘制曲线 ----
        DoubleArr mileages = new DoubleArr();
        for (double lc = 1000; lc <= 2000; lc += 1.0) {
            mileages.add(lc);
        }
        OptDataVector curve = new OptDataVector();
        opter.GetBSplineOptCurve(mileages, curve);
        System.out.println("曲线点数: " + curve.size());

        // ---- 获取最终结果 ----
        OptDataVector finalProfile = opter.GetBSplineOptProfile();
        System.out.println("最终偏差点数: " + finalProfile.size());
    }
}
```

---

## 接口速查表

| 分类 | 方法 | 说明 |
|------|------|------|
| **拟合** | `FitBSpline(numCtrlPts)` | 指定控制点数量进行B样条拟合 |
| | `FitBSplineAdaptive(data, tol, min, max)` | Douglas-Peucker自适应拟合 |
| **优化** | `StartOptBSpline(numCtrl, dpTol)` | 直接B样条优化 |
| **控制点** | `GetBSplineControlPoints()` | 获取后处理拟合控制点 |
| | `GetBSplineOptControlPoints()` | 获取直接优化控制点 |
| | `MoveBSplineControlPoint(idx, pt)` | 移动控制点 |
| | `InsertBSplineControlPoint(pt, idx)` | 插入控制点 |
| | `RemoveBSplineControlPoint(idx)` | 删除控制点 |
| **参数** | `GetBSplineDegree()` | 获取B样条次数 |
| | `GetBSplineKnots()` | 获取节点向量 |
| **求值** | `GetBSplineFitResult()` | 拟合曲线在原始测点的值 |
| | `GetBSplineFitCurve(mileages, out)` | 拟合曲线在任意里程的值 |
| | `GetBSplineOptCurve(mileages, out)` | 优化曲线在任意里程的值 |
| | `GetBSplineCurvePoints(ctrl, knots, deg, out)` (static) | 通用静态方法：任意参数求值 |
| **结果** | `GetBSplineFitInfo()` | 拟合精度信息 |
| | `GetBSplineAdjustedProfile()` | 编辑后的最终偏差 |
| | `GetBSplineOptResult()` | 优化结果调整量 |
| | `GetBSplineOptProfile()` | 优化后最终偏差 |
| | `ApplyBSplineResult()` | 将拟合结果写回优化结果 |
| **撤销/重做** | `UndoBSpline()` | 撤销上一次控制点编辑 |
| | `RedoBSpline()` | 重做上一次撤销 |
| | `CanUndoBSpline()` / `CanRedoBSpline()` | 查询可否撤销/重做 |
| | `ClearBSplineUndoHistory()` | 清空历史 |
