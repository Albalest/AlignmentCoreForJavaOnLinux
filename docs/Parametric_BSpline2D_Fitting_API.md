# 二维参数 B 样条曲线拟合接口说明

## 1. 概述

本文档说明新的二维参数 B 样条曲线及其约束拟合接口：

- `ParametricBSpline2D`
- `ConstrainedParametricBSplineOptimizer`

相关源码位于：

- `source/COptimizer/ParametricBSpline2D.h/.cpp`
- `source/COptimizer/ConstrainedParametricBSplineOptimizer.h/.cpp`

该接口与旧的 `BSplineFit`、`BSplineOpt` 独立，**不会修改或替换旧接口**。

新的曲线模型为：

$$
C(u) = (X(u), Y(u)) = \sum_{i=0}^{n-1} N_{i,p}(u) P_i
$$

其中：

- $u$：样条参数；
- $N_{i,p}(u)$：固定 knot vector 和 degree 对应的 B 样条基函数；
- $P_i=(x_i,y_i)$：二维控制点；
- $X(u)$：物理里程；
- $Y(u)$：高程、偏差或其他纵向量。

当 $X(u)$ 在参数域内严格递增时，可以将曲线解释为物理里程到高程的关系：

$$
y(m) = Y(X^{-1}(m))
$$

因此，新模型允许同时优化控制点的 X、Y 两个分量，而不是固定里程后只优化 Y。

---

## 2. 与旧模型的主要区别

| 项目 | 旧模型 | 新模型 |
|---|---|---|
| 曲线表达 | 通常固定里程或固定采样基函数 | $C(u)=(X(u),Y(u))$ 二维参数曲线 |
| 优化变量 | 主要为 Y 控制值 | X/Y 控制点均可优化 |
| 里程映射 | 通常由外部数据固定 | 由 $X(u)$ 定义，必要时反算 $u$ |
| knot vector | 由旧流程管理 | 构造后保持不变 |
| 局部性 | 由固定基函数决定 | 移动控制点仍只影响其基函数支撑区间 |
| 约束域 | 主要为固定采样点 | 参数域或物理里程域 |
| 求解方式 | 旧优化流程 | 参数域 QP，里程域逐次 QP/SQP 近似 |
| 旧接口兼容 | 原有接口 | 新接口不改变旧文件和旧语义 |

注意：新接口不是旧 `BSplineOpt` 的简单重命名，也不承诺完整复刻旧优化器中的全部业务语义。

---

## 3. `ParametricBSpline2D`

### 3.1 基础数据结构

```cpp
ParametricBSpline2D::Point point;
point.x = mileage;
point.y = elevation;
```

`Point` 字段含义：

| 字段 | 类型 | 含义 |
|---|---|---|
| `x` | `double` | 里程或横坐标 |
| `y` | `double` | 高程、偏差或纵坐标 |

`Evaluation` 同时返回位置、一阶导数和二阶导数：

```cpp
ParametricBSpline2D::Evaluation evaluation = curve.Evaluate(u);
```

---

### 3.2 构造二维 B 样条

```cpp
bool Build(
    const std::vector<Point>& controlPoints,
    int degree,
    const std::vector<double>& knots);
```

要求：

1. `controlPoints` 至少包含 `degree + 1` 个控制点；
2. `knots.size() == controlPoints.size() + degree + 1`；
3. knot vector 非递减；
4. knot vector 和控制点必须是有限数；
5. 控制点 X 默认需要满足严格递增条件；
6. 参数域为：

   ```cpp
   [knots[degree], knots[controlPoints.size()]]
   ```

示例：

```cpp
#include "ParametricBSpline2D.h"

std::vector<ParametricBSpline2D::Point> controlPoints;
controlPoints.push_back({1000.0, 10.0});
controlPoints.push_back({1020.0, 10.5});
controlPoints.push_back({1040.0, 11.0});
controlPoints.push_back({1060.0, 11.2});

std::vector<double> knots;
knots.push_back(0.0);
knots.push_back(0.0);
knots.push_back(0.0);
knots.push_back(0.0);
knots.push_back(1.0);
knots.push_back(1.0);
knots.push_back(1.0);
knots.push_back(1.0);

ParametricBSpline2D curve;
bool ok = curve.Build(controlPoints, 3, knots);
```

建议优先使用开放均匀 knot vector，首尾 knot 的重复次数通常为 `degree + 1`。

---

### 3.3 查询曲线信息

```cpp
bool valid = curve.IsValid();
int degree = curve.GetDegree();
std::size_t count = curve.GetControlPointCount();
std::vector<ParametricBSpline2D::Point> points = curve.GetControlPoints();
std::vector<double> knots = curve.GetKnots();
double uMin = curve.GetParamMin();
double uMax = curve.GetParamMax();
```

曲线定义由以下三部分共同决定：

- 控制点；
- degree；
- knot vector。

保存或跨进程传输曲线时，不能只保存控制点而丢失 degree 或 knot vector。

---

### 3.4 求值和导数

```cpp
ParametricBSpline2D::Point position = curve.EvaluatePosition(u);
ParametricBSpline2D::Point first = curve.EvaluateFirstDerivative(u);
ParametricBSpline2D::Point second = curve.EvaluateSecondDerivative(u);

ParametricBSpline2D::Evaluation all = curve.Evaluate(u);
```

返回值含义：

- `position.x = X(u)`；
- `position.y = Y(u)`；
- `first.x = X'(u)`；
- `first.y = Y'(u)`；
- `second.x = X''(u)`；
- `second.y = Y''(u)`。

参数必须位于有效参数域内。对于端点求值，建议使用 `GetParamMin()` 和 `GetParamMax()`，不要自行推断参数范围。

---

### 3.5 查询基函数

```cpp
std::vector<double> values;
bool ok = curve.GetBasisValues(u, 0, values);
```

`derivativeOrder` 支持：

| 值 | 返回内容 |
|---:|---|
| `0` | $N_{i,p}(u)$ |
| `1` | 一阶基函数导数 |
| `2` | 二阶基函数导数 |

返回数组长度等于控制点数量。基函数具有局部支撑，因此在某个参数 $u$ 处通常只有少量元素非零。

---

### 3.6 里程反算

当 $X(u)$ 严格递增时，可以将物理里程反算为参数：

```cpp
double u = 0.0;
bool ok = curve.MileageToParam(
    mileage,
    u,
    1e-9,   // 里程反算容差
    80      // 最大迭代次数
);
```

也可以直接获取指定里程处的曲线点：

```cpp
ParametricBSpline2D::Point point;
bool ok = curve.EvaluateAtMileage(mileage, point);
```

反算失败的常见原因：

- 曲线无效；
- `X(u)` 不是严格递增；
- 里程超出曲线的 X 范围；
- 迭代次数不足；
- 曲线导数或控制点包含无效数值。

前端绘图或业务查询应优先使用 `EvaluateAtMileage()`，避免自行实现参数反算。

---

### 3.7 控制点编辑

#### 移动 X/Y

```cpp
bool ok = curve.MoveControlPointXY(
    index,
    newX,
    newY,
    true,   // 要求 X(u) 严格递增
    1e-9,   // 最小导数
    true    // 提交修改
);
```

说明：

- 不会重建 knot vector；
- 不会重新参数化；
- 只有控制点坐标发生变化；
- 如果开启严格递增检查，修改失败时原曲线不变；
- 修改一个控制点只影响其 B 样条基函数的局部支撑区间。

如果只需要试算而不提交，可以传入 `commit = false`。

#### 批量设置控制点

```cpp
bool ok = curve.SetControlPoints(
    newControlPoints,
    true,
    1e-9
);
```

`newControlPoints` 数量必须与当前控制点数量相同。此接口不会改变 degree 和 knot vector。

#### 严格递增检查

```cpp
bool monotonic = curve.IsXStrictlyIncreasing(1e-9);
```

实现采用导数控制值进行充分条件检查。对于 degree 为 $p$ 的 B 样条，要求有效导数控制值满足：

$$
\frac{p(x_{i+1}-x_i)}{U_{i+p+1}-U_{i+1}}
\geq d_{min}
$$

该条件可保证 $X'(u)$ 在有效参数域内保持正值或不低于设定下限。

---

### 3.8 节点插入和控制点删除

#### 插入节点

```cpp
bool ok = curve.InsertKnot(u, 1);
```

节点插入会同步更新 X/Y 控制点。理论上，标准 knot insertion 不改变原曲线几何形状，但会改变控制点数量和 knot vector。

如果前端输入的是物理里程，需要先反算参数：

```cpp
double u = 0.0;
if (curve.MileageToParam(insertMileage, u))
    curve.InsertKnot(u);
```

#### 删除控制点

```cpp
bool ok = curve.RemoveControlPoint(
    index,
    1e-3,  // 允许的最大曲线变化量
    true   // 删除后仍要求 X 严格递增
);
```

删除是带容差的近似操作，不应理解为任意情况下的精确 knot removal。以下情况可能失败：

- 删除首尾控制点；
- 控制点数量过少；
- 删除后曲线误差超过 `tolerance`；
- 删除后 X 不再严格递增；
- 候选 knot vector 或控制点无效。

失败时当前曲线保持不变。

---

### 3.9 撤销和重做

```cpp
if (curve.CanUndo())
    curve.Undo();

if (curve.CanRedo())
    curve.Redo();
```

可用接口：

| 接口 | 说明 |
|---|---|
| `SaveSnapshot()` | 保存当前曲线快照 |
| `Undo()` | 撤销一次编辑 |
| `Redo()` | 重做一次编辑 |
| `CanUndo()` | 是否存在可撤销状态 |
| `CanRedo()` | 是否存在可重做状态 |
| `ClearHistory()` | 清空历史 |
| `SetMaxHistory(size)` | 设置最大历史数量 |

一次完整的拖拽建议只形成一次业务快照，不建议为鼠标移动的每一个像素都创建快照。

---

## 4. `ConstrainedParametricBSplineOptimizer`

该类用于根据物理里程-高程测量数据拟合二维参数 B 样条，并对控制点、曲线单调性和高程约束进行优化。

优化变量排列为：

```text
[X0, X1, ..., X(n-1), Y0, Y1, ..., Y(n-1)]
```

可以通过以下接口查询布局：

```cpp
std::size_t controlPointCount = 0;
std::size_t xOffset = 0;
std::size_t yOffset = 0;
optimizer.GetVariableLayout(
    controlPointCount,
    xOffset,
    yOffset
);
```

---

### 4.1 基本数据类型

#### `DataPoint`

```cpp
ConstrainedParametricBSplineOptimizer::DataPoint dataPoint;
dataPoint.mileage = 1000.0;
dataPoint.elevation = 12.3;
dataPoint.weight = 1.0;
```

| 字段 | 含义 |
|---|---|
| `mileage` | 测量点物理里程，必须严格递增 |
| `elevation` | 测量高程或纵向观测值 |
| `weight` | 测量权重，必须大于 0 |
| `hasElevationBounds` | 是否启用该测点的高程上下界 |
| `elevationLower` | 高程下界 |
| `elevationUpper` | 高程上界 |

至少需要两个数据点。里程必须严格递增。

#### `ParametricConstraint`

```cpp
ConstrainedParametricBSplineOptimizer::ParametricConstraint constraint;
constraint.u = 0.5;
constraint.hasElevationBounds = true;
constraint.elevationLower = 10.0;
constraint.elevationUpper = 15.0;
```

该结构中的 `u` 始终表示**参数域位置**。即使优化器设置为 `Mileage` 域，`ParametricConstraint` 仍按固定参数 $u$ 约束处理。

---

### 4.2 `Options`

| 字段 | 默认值 | 说明 |
|---|---:|---|
| `degree` | `3` | B 样条阶次，默认三次 |
| `xStrictTolerance` | `1e-6` | 控制点 X 顺序的最小间距 |
| `xDerivativeMinimum` | `1e-9` | X 导数下限 |
| `derivativeSamplesPerSpan` | `8` | 平滑项采样数 |
| `fitWeight` | `1.0` | 数据拟合权重 |
| `xSmoothWeight` | `0.0` | X 二阶导平滑权重 |
| `ySmoothWeight` | `1.0` | Y 二阶导平滑权重 |
| `movementWeight` | `0.0` | 控制点偏离初始位置的惩罚权重 |
| `autoControlPointTolerance` | `0.003` | 自动选点与最终拟合的最大高程误差；单位与 `elevation` 相同；正值会将每个输入点限制在 `elevation ± tolerance`；当前工程单位下默认值表示 3 mm；设为 `0` 时按数据自适应选点且仅使用显式数据边界 |
| `autoControlPointSelectionTolerance` | `0.0` | 仅用于自动选择控制点数量的纵向弦偏差阈值，不改变最终高程硬约束；正值直接采用该阈值；为 `0` 时，若拟合容差为正则自动取其一半，否则按数据自适应估计 |
| `maxIterations` | `10000` | 内部 QP 最大迭代次数；毫米级逐点硬约束通常需要较多 ADMM 迭代 |
| `maxSqpIterations` | `15` | Mileage 模式最大外层迭代次数 |
| `optimalityTolerance` | `1e-7` | 优化收敛容差 |
| `constraintTolerance` | `1e-7` | 约束残差容差 |
| `xTrustRegion` | `10.0` | Mileage 模式下 X 控制点信赖域 |
| `yTrustRegion` | `100.0` | Mileage 模式下 Y 线性化信赖域 |
| `constraintDomain` | `Parameter` | 约束域：参数域或物理里程域 |
| `enforceControlPointXOrder` | `true` | 是否约束控制点 X 递增 |
| `enforceDerivativePositive` | `true` | 是否约束 $X'(u)$ 为正 |

控制点数量不再由调用方设置。初始化时，程序根据测量数据的纵向形状自动估算控制点数量：以首尾测点为初始分段，反复寻找各里程区间相对端点弦线的最大纵向偏差，并在偏差最大的测点处增加分段。`autoControlPointSelectionTolerance` 专门控制此过程；为 `0` 时，正的 `autoControlPointTolerance` 会自动使用其一半作为选点阈值，为三次样条在局部尖峰附近保留必要自由度。控制点数量由选点容差决定，最小数量为 `degree + 1`；最多不会超过输入测点数。

`autoControlPointTolerance` 为正数时，同时是每个输入测点的高程硬约束：

$$
elevation_i - tolerance \le Y(u_i) \le elevation_i + tolerance
$$

因此在 `Parameter` 域且约束可行时，最终曲线会满足每个输入测点的该误差界。调用方通过 `DataPoint::hasElevationBounds` 设置的边界会和此误差带取交集；交集为空时问题不可行。`Mileage` 域采用 SQP 线性化，最终仍会重算并校验该误差界。`ySmoothWeight` 仍用于在该误差带内选择更平滑的曲线；容差过小会让曲线追随噪声，并可能使二次规划规模增大或不可行。

调用方只需要设置 `degree`、拟合权重、平滑权重和约束相关选项。

---

## 5. 推荐调用流程

### 5.1 使用测量数据自动初始化

```cpp
#include "ConstrainedParametricBSplineOptimizer.h"

ConstrainedParametricBSplineOptimizer optimizer;

std::vector<ConstrainedParametricBSplineOptimizer::DataPoint> data;
data.push_back({1000.0, 10.0, 1.0});
data.push_back({1010.0, 10.4, 1.0});
data.push_back({1020.0, 10.8, 1.0});
data.push_back({1030.0, 11.1, 1.0});
data.push_back({1040.0, 11.3, 1.0});

if (!optimizer.SetData(data)) {
    // 数据无效
}

ConstrainedParametricBSplineOptimizer::Options options;
options.degree = 3;
options.constraintDomain =
    ConstrainedParametricBSplineOptimizer::ConstraintDomain::Parameter;
options.autoControlPointTolerance = 0.003; // 与 elevation 使用相同单位，当前工程为 3 mm
options.autoControlPointSelectionTolerance = 0.0015; // 可选：仅控制自动控制点数量
options.ySmoothWeight = 0.001;
options.enforceControlPointXOrder = true;
options.enforceDerivativePositive = true;

if (!optimizer.Initialize(options)) {
    // 初始化失败
}

ConstrainedParametricBSplineOptimizer::Solution result = optimizer.Solve();
if (!result.success) {
    // 查看 result.diagnostics
}
```

Java 调用方式相同：

```java
options.setConstraintDomain(
    ConstrainedParametricBSplineOptimizer.ConstraintDomain.Parameter);
options.setAutoControlPointTolerance(0.003); // 与 point.getY() 使用相同单位，当前工程为 3 mm
options.setAutoControlPointSelectionTolerance(0.0015); // 可选：仅控制自动控制点数量
options.setYSmoothWeight(0.001);
```

当前工程的高程单位为米时，默认值 `0.003` 表示自动选点阶段尽量将分段弦偏差控制到约 3 mm。应按业务可接受偏差设置，不能跨单位直接照抄。

推荐的标准顺序为：

1. 创建优化器；
2. 调用 `SetData()`；
3. 设置 `Options`；
4. 调用 `Initialize(options)`；
5. 可选：设置参数域约束或公共约束；
6. 调用 `Solve()`；
7. 检查 `Solution.success`；
8. 使用 `Solution.curve` 查询和绘制结果。

---

### 5.2 使用已有二维曲线作为初值

如果已有固定 degree、knot vector 和控制点，可以跳过自动初始化：

```cpp
ParametricBSpline2D initialCurve;
// initialCurve.Build(...);

optimizer.SetData(data);
optimizer.SetOptions(options);

if (!optimizer.SetInitialCurve(initialCurve)) {
    // 初始曲线无效或不能满足 X 严格递增要求
}

ConstrainedParametricBSplineOptimizer::Solution result = optimizer.Solve();
```

已有曲线必须满足：

- 曲线有效；
- degree 与 `Options.degree` 一致；
- `X(u)` 严格递增；
- 能覆盖所有输入数据的物理里程；
- 参数域约束中的 `u` 位于曲线有效参数域内。

求解过程中不会自动改变初始曲线的 degree 和 knot vector。

---

## 6. 约束模型

### 6.1 参数域模式

```cpp
options.constraintDomain =
    ConstrainedParametricBSplineOptimizer::ConstraintDomain::Parameter;
```

参数域模式下：

- 测量数据先映射到固定参数值；
- 曲线位置对控制点是线性的；
- 约束可以直接写成 B 样条基函数的线性组合；
- 在没有物理波长约束时，可作为凸二次规划处理；
- `Solution.isConvexQuadraticModel` 通常为 `true`。

该模式适合：

- 参数域固定且稳定；
- 主要需要高效线性约束拟合；
- 不要求每轮重新执行物理里程反算。

### 6.2 物理里程域模式

```cpp
options.constraintDomain =
    ConstrainedParametricBSplineOptimizer::ConstraintDomain::Mileage;
```

里程域模式下，输入数据以物理里程为位置：

$$
y(m) = Y(u(m)), \quad X(u(m))=m
$$

控制点变化会同时改变 $X(u)$ 和反算参数 $u(m)$，因此问题通常不再是单个固定 QP。当前实现采用逐次线性化的 QP/SQP 近似。

高程对控制点的一阶灵敏度近似为：

$$
\frac{\partial y(m)}{\partial x_i}
= -\frac{Y'(u)}{X'(u)}N_i(u)
$$

$$
\frac{\partial y(m)}{\partial y_i}=N_i(u)
$$

该模式适合：

- 物理里程位置必须保持语义准确；
- X 控制点需要参与拟合；
- 约束按实际里程定义。

需要注意：当前实现是近似 SQP，不是完整非线性规划器。应在业务侧检查迭代是否收敛、最终物理残差是否满足要求。

---

### 6.3 参数域约束示例

```cpp
std::vector<ConstrainedParametricBSplineOptimizer::ParametricConstraint> constraints;

ConstrainedParametricBSplineOptimizer::ParametricConstraint c;
c.u = 0.5;
c.hasElevationBounds = true;
c.elevationLower = 10.0;
c.elevationUpper = 12.0;
c.hasXBounds = true;
c.mileageLower = 1020.0;
c.mileageUpper = 1030.0;
constraints.push_back(c);

if (!optimizer.SetParameterConstraints(constraints)) {
    // 参数域约束无效
}
```

设置约束前应确保：

- `u` 是参数值，不是里程值；
- `u` 位于曲线参数域；
- 上下界均为有限值；
- 下界不大于上界。

---

### 6.4 控制点 X 顺序约束

```cpp
optimizer.AddControlPointXOrderConstraint(0.001);
```

该接口追加控制点 X 的最小相邻间距。实际求解时会与 `xStrictTolerance` 和导数正值约束共同作用，最终使用更严格的最小间距。

---

### 6.5 `CommonConstrain` 的支持范围

可以通过以下接口传入既有公共约束：

```cpp
CommonConstrain commonConstraints;
// 设置公共约束字段
optimizer.SetCommonConstraints(commonConstraints);
```

当前新模型已处理或部分处理的内容包括：

- `m_factor`：用于高程拟合权重；
- `m_ShortWaveLimit` / `m_ShortWaveLength`：短波物理里程约束；
- `m_LongWaveLimit` / `m_LongWaveLength`：长波物理里程约束；
- `m_SuperSlope`：斜率约束；
- `isFixed`：固定首尾控制点；
- `isPlane`：保留为语义标签，但新模型统一按通用 Y 纵坐标处理。

以下旧流程语义目前没有完整等价实现：

- 波长约束的旧索引边距；
- `m_CtrlCurve` 曲率约束；
- `isByVert` 相关旧流程耦合行为。

因此，使用 `CommonConstrain` 时必须检查 `Solution.diagnostics`，不能默认认为结果完整复刻旧优化器。

---

## 7. 求解结果

`Solve()` 返回 `Solution`：

| 字段 | 说明 |
|---|---|
| `success` | 是否成功完成求解并通过最终检查 |
| `isConvexQuadraticModel` | 是否可按凸 QP 模型处理 |
| `usedNonlinearMileageMapping` | 是否使用了里程反算和 SQP 线性化 |
| `iterations` | 内部 QP/SQP 总迭代次数 |
| `objectiveValue` | 最终目标函数值 |
| `curve` | 求解后的二维 B 样条曲线 |
| `constraintResiduals` | 最后一轮线性化约束的残差 |
| `physicalMileageConstraintResiduals` | 最终曲线按实际曲线/物理里程重算的残差 |
| `diagnostics` | 诊断信息和能力限制说明 |

### 残差字段的区别

`constraintResiduals` 记录的是最后一次 QP 线性约束系统的残差，可能包含：

- 控制点 X 范围约束；
- X 严格递增约束；
- 信赖域约束；
- 最后一轮线性化后的物理约束。

`physicalMileageConstraintResiduals` 是对最终曲线重新求值后的残差，更适合用于业务验收。只要该数组中存在大于 `constraintTolerance` 的残差，当前实现会将求解标记为失败。

失败时应优先检查：

```cpp
for (const std::string& message : result.diagnostics) {
    // 记录或显示 message
}
```

---

## 8. 控制点人工编辑后再次拟合

可以先通过 `ParametricBSpline2D` 编辑曲线，再将其作为优化器初始曲线：

```cpp
ParametricBSpline2D editedCurve = result.curve;

bool moved = editedCurve.MoveControlPointXY(
    selectedIndex,
    newX,
    newY,
    true,
    options.xDerivativeMinimum
);

if (moved && optimizer.SetInitialCurve(editedCurve)) {
    ConstrainedParametricBSplineOptimizer::Solution refined =
        optimizer.Solve();
}
```

也可以使用优化器提供的快捷入口：

```cpp
optimizer.MoveControlPointXY(
    selectedIndex,
    newX,
    newY,
    true
);
```

该接口修改的是优化器当前保存的初始曲线，并在修改后重新建立测量里程到参数域的映射。

前端编辑流程建议：

1. 显示 `Solution.curve` 的控制点和采样曲线；
2. 拖动控制点时调用 `MoveControlPointXY()`；
3. 修改后重新调用 `EvaluateAtMileage()` 生成绘图数据；
4. 用户确认后，再将修改后的曲线作为下一轮拟合的初始曲线；
5. 每次成功编辑后重新获取控制点，不要长期缓存旧控制点下标。

---

## 9. 建议的业务验收检查

求解完成后建议至少检查以下内容：

```cpp
const auto& result = optimizer.Solve();

if (!result.success) {
    // 失败时读取 diagnostics
}

if (result.success && !result.curve.IsXStrictlyIncreasing(
        options.xDerivativeMinimum)) {
    // 不应接受不可逆的里程映射
}

ParametricBSpline2D::Point point;
if (!result.curve.EvaluateAtMileage(queryMileage, point)) {
    // 查询里程不在有效范围或曲线不可反算
}
```

建议额外记录：

- 输入数据范围；
- 控制点数量、degree、knot vector；
- 是否使用参数域或里程域；
- `iterations`；
- `objectiveValue`；
- 最大物理残差；
- `diagnostics` 中的能力限制说明。

---

## 10. 当前实现限制

1. 新接口保持 C++ API，是否通过 SWIG 暴露给 Java 或其他语言，需要额外配置对应的接口文件。
2. degree 和 knot vector 在一次曲线编辑/优化过程中保持不变；如需改变，应重新 `Build()` 或重新 `Initialize()`。
3. `X(u)` 必须保持严格递增，才能稳定执行里程反算。
4. Mileage 域使用逐次 QP/SQP 近似，不是完整 NLP 求解器。
5. 删除控制点是带容差的近似删除，不能保证任意曲线都能精确删除节点。
6. `CommonConstrain` 尚未完整覆盖旧优化流程的所有字段语义，尤其是曲率约束和旧索引边距逻辑。
7. 内部 QP 求解器为轻量 ADMM 实现；对于强约束、病态或高度非线性的模型，应重点检查收敛状态和最终物理残差。
8. `constraintResiduals` 只表示最后一轮线性化约束残差；业务验收应优先使用最终曲线重算的 `physicalMileageConstraintResiduals`。

---

## 11. 最小完整示例

```cpp
#include "ConstrainedParametricBSplineOptimizer.h"

void FitCurve()
{
    ConstrainedParametricBSplineOptimizer optimizer;

    std::vector<ConstrainedParametricBSplineOptimizer::DataPoint> data;
    data.push_back({1000.0, 10.00, 1.0});
    data.push_back({1010.0, 10.20, 1.0});
    data.push_back({1020.0, 10.55, 1.0});
    data.push_back({1030.0, 10.80, 1.0});
    data.push_back({1040.0, 11.00, 1.0});

    if (!optimizer.SetData(data))
        return;

    ConstrainedParametricBSplineOptimizer::Options options;
    options.degree = 3;
    options.constraintDomain =
        ConstrainedParametricBSplineOptimizer::ConstraintDomain::Mileage;
    options.fitWeight = 1.0;
    options.ySmoothWeight = 0.5;
    options.enforceControlPointXOrder = true;
    options.enforceDerivativePositive = true;

    if (!optimizer.Initialize(options))
        return;

    ConstrainedParametricBSplineOptimizer::Solution result = optimizer.Solve();
    if (!result.success)
    {
        for (std::size_t i = 0; i < result.diagnostics.size(); ++i)
        {
            // 记录诊断信息
        }
        return;
    }

    ParametricBSpline2D::Point fitted;
    if (result.curve.EvaluateAtMileage(1025.0, fitted))
    {
        // fitted.x == 1025 附近
        // fitted.y 为拟合高程
    }
}
```

该示例展示的是最小调用流程。生产代码仍应根据实际业务设置数据边界、权重、约束、异常处理和结果验收规则。
