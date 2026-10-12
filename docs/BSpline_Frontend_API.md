# B 样条人工编辑接口说明（前端替换版）

## 1. 适用范围

本文档用于前端替换 B 样条控制点编辑逻辑。

当前人工编辑接口作用于后处理拟合曲线，调用顺序为：

1. `StartOpt()`：完成离散优化；
2. `FitBSpline(...)` 或 `FitBSplineAdaptive(...)`：生成可编辑的后处理 B 样条；
3. 使用本文档中的控制点接口进行移动、插入、删除；
4. 调用绘制、结果获取或 `ApplyBSplineResult()`。

> `StartOptBSpline()` 是直接 B 样条优化流程。当前 `GetBSplineOptControlPoints()` 返回的是直接优化结果，不能直接使用后处理控制点编辑接口进行修改。前端应区分两种结果。

---

## 2. 基础数据类型

### `OptData`

| 字段 | 类型 | 含义 |
|---|---|---|
| `x` | `double` | 里程，单位 m |
| `y` | `double` | 控制点值或曲线调整量，单位 mm |
| `y_up` | `double` | 前端显示用上限，单位 mm |
| `y_down` | `double` | 前端显示用下限，单位 mm |

人工编辑接口不负责约束合法性校验。前端负责显示上下限并决定是否允许提交。

### SWIG 容器

- `OptDataVector`：`std::vector<OptData>`；
- `DoubleArr`：`std::vector<double>`。

示例：

```java
OptDataVector controlPoints = opter.GetBSplineControlPoints();
DoubleArr mileages = new DoubleArr();
mileages.add(1000.0);
mileages.add(1001.0);
```

---

## 3. 推荐的前端编辑模型

### 3.1 移动控制点：只修改纵坐标

推荐使用：

```java
boolean ok = opter.MoveBSplineControlPointY(int index, double y);
```

说明：

- `index`：控制点下标，从 `0` 开始；
- `y`：新的控制点纵坐标，单位 mm；
- 控制点 `x` 不修改；
- `degree`、`knots` 和里程到参数域的映射保持不变；
- 三次 B 样条只影响该控制点对应的局部支撑区间；
- 成功后重新调用 `GetBSplineFitCurve` 获取绘制曲线。

前端拖拽推荐流程：

```java
int index = selectedControlPointIndex;

opter.MoveBSplineControlPointY(index, draggedY);

OptDataVector curve = new OptDataVector();
opter.GetBSplineFitCurve(plotMileages, curve);
```

兼容旧接口：

```java
boolean ok = opter.MoveBSplineControlPoint(
    index,
    new OptData(originalX, newY, 0.0, 0.0));
```

旧接口当前也只采纳 `pt.y`，传入的 `pt.x` 不会改变样条控制点的里程锚点。新前端代码应优先使用 `MoveBSplineControlPointY`。

---

## 4. 插入控制点

推荐使用：

```java
boolean ok = opter.InsertBSplineKnot(double mileage);
```

说明：

- `mileage`：插入位置，单位 m；
- 插入位置必须位于曲线有效范围内部；
- 不能在首端或末端参数边界插入；
- 插入后原曲线几何形状保持不变；
- 新控制点由 B 样条节点插入算法计算，不由前端指定 `y`；
- 插入成功后，控制点数量增加 1；
- 插入完成后，前端可以继续移动新增控制点的 `y`。

示例：

```java
boolean inserted = opter.InsertBSplineKnot(1030.0);
if (inserted) {
    OptDataVector controlPoints = opter.GetBSplineControlPoints();
    OptDataVector curve = new OptDataVector();
    opter.GetBSplineFitCurve(plotMileages, curve);
}
```

兼容旧接口：

```java
opter.InsertBSplineControlPoint(
    new OptData(1030.0, 0.0, 0.0, 0.0));
```

旧接口现在只使用传入点的 `x` 作为插入里程，传入的 `y` 和 `index` 不作为 B 样条节点插入参数。新前端代码不要依赖旧接口的 `y` 或 `index` 语义。

---

## 5. 删除控制点

推荐使用：

```java
boolean ok = opter.RemoveBSplineKnotForControlPoint(
    int index,
    double tolerance);
```

说明：

- `index`：要删除的内部控制点下标；
- 首尾控制点不能删除；
- 曲线至少保留 4 个控制点；
- `tolerance`：允许的最大曲线变化量，单位 mm；
- 删除前会计算候选曲线；
- 若误差超过 `tolerance`，接口返回 `false`，当前曲线不改变；
- 删除成功后控制点数量减少 1。

示例：

```java
boolean removed = opter.RemoveBSplineKnotForControlPoint(5, 0.001);
if (!removed) {
    // 删除失败，原曲线仍然有效
}
```

兼容旧接口：

```java
boolean removed = opter.RemoveBSplineControlPoint(5, 0.001);
```

旧接口现在也使用带容差的节点删除逻辑。

---

## 6. 获取控制点和曲线

### 获取控制点

```java
OptDataVector controlPoints = opter.GetBSplineControlPoints();
```

返回当前后处理拟合曲线的控制点。每次移动、插入或删除成功后，应重新获取，不要在前端长期缓存旧下标和旧列表。

### 获取原始测点处的曲线值

```java
OptDataVector result = opter.GetBSplineFitResult();
```

返回当前 B 样条在原始测点里程处的值。

### 获取任意里程处的曲线值

```java
OptDataVector curve = new OptDataVector();
boolean ok = opter.GetBSplineFitCurve(mileages, curve);
```

其中 `mileages` 为输入里程数组，`curve` 会被填充为 `(x, y)` 点集。

前端绘图建议使用固定采样间隔生成 `mileages`，例如每 1 m 一个点。

---

## 7. 获取拟合参数

```java
int degree = opter.GetBSplineDegree();
DoubleArr knots = opter.GetBSplineKnots();
```

这些参数属于后处理拟合曲线，用于保存、重建或调用通用求值接口。

B 样条定义由以下三部分共同决定：

- 控制点；
- `degree`；
- `knots`。

不能只保存控制点而丢弃 `degree` 或 `knots`。

---

## 8. 撤销与重做

每次成功的移动、插入或删除操作都会保存快照。

```java
if (opter.CanUndoBSpline()) {
    opter.UndoBSpline();
}

if (opter.CanRedoBSpline()) {
    opter.RedoBSpline();
}
```

完整接口：

| 接口 | 说明 |
|---|---|
| `UndoBSpline()` | 撤销上一次成功编辑 |
| `RedoBSpline()` | 重做上一次撤销 |
| `CanUndoBSpline()` | 是否存在可撤销操作 |
| `CanRedoBSpline()` | 是否存在可重做操作 |
| `ClearBSplineUndoHistory()` | 清空撤销/重做历史 |

一次拖拽建议视为一次编辑操作。不要在鼠标移动的每个像素位置都建立业务层快照；如果界面需要连续刷新，可在拖拽开始前保存一次状态，拖拽结束后保留最终结果。

---

## 9. 获取最终调整结果

获取编辑后的最终偏差：

```java
OptDataVector profile = opter.GetBSplineAdjustedProfile();
```

该结果为：

```text
原始偏差 + 当前 B 样条调整量
```

如果需要将当前 B 样条曲线写回处理器的优化结果：

```java
boolean applied = opter.ApplyBSplineResult();
```

---

## 10. 前端推荐完整流程

```java
// 1. 优化
opter.StartOpt();

// 2. 生成后处理 B 样条
opter.FitBSplineAdaptive(0.3);

// 3. 获取控制点并绘制
OptDataVector controlPoints = opter.GetBSplineControlPoints();
OptDataVector curve = new OptDataVector();
opter.GetBSplineFitCurve(plotMileages, curve);

// 4. 移动控制点：只修改 y
opter.MoveBSplineControlPointY(selectedIndex, newY);

// 5. 插入节点
opter.InsertBSplineKnot(insertMileage);

// 6. 删除节点：带误差容差
opter.RemoveBSplineKnotForControlPoint(removeIndex, 0.001);

// 7. 每次成功编辑后刷新
controlPoints = opter.GetBSplineControlPoints();
curve = new OptDataVector();
opter.GetBSplineFitCurve(plotMileages, curve);

// 8. 用户确认后应用结果
opter.ApplyBSplineResult();
```

---

## 11. 前端替换要点

### 11.1 必须替换的旧接口

前端需要重点替换以下三个控制点编辑接口：

| 旧接口 | 新接口 | 替换原因 |
|---|---|---|
| `MoveBSplineControlPoint(int index, OptData pt)` | `MoveBSplineControlPointY(int index, double y)` | 人工移动只修改纵坐标，避免修改控制点 `x` 导致参数化语义不明确 |
| `InsertBSplineControlPoint(OptData pt, int index)` | `InsertBSplineKnot(double mileage)` | 插入操作应插入 B 样条节点，不能直接插入一个任意控制点并重新生成节点向量 |
| `RemoveBSplineControlPoint(int index)` | `RemoveBSplineKnotForControlPoint(int index, double tolerance)` | 删除操作需要误差容差，不能直接删除控制点后整体重建节点向量 |

#### 旧接口一：移动控制点

旧代码：

```java
opter.MoveBSplineControlPoint(index,
    new OptData(controlPoint.getX(), newY, 0.0, 0.0));
```

替换为：

```java
opter.MoveBSplineControlPointY(index, newY);
```

变化说明：

- 不再创建 `OptData` 作为移动参数；
- 不再从前端传入新的 `x`；
- `x` 是固定的控制点里程锚点；
- 只提交新的 `y` 值；
- `y` 单位仍为 mm。

#### 旧接口二：插入控制点

旧代码：

```java
opter.InsertBSplineControlPoint(
    new OptData(insertMileage, insertY, 0.0, 0.0));
```

替换为：

```java
opter.InsertBSplineKnot(insertMileage);
```

变化说明：

- 前端只提交插入里程；
- 不再提交 `insertY`；
- 不再依赖 `index` 参数；
- 新控制点的 `y` 由节点插入算法自动计算；
- 插入后重新调用 `GetBSplineControlPoints()` 获取新的控制点列表。

#### 旧接口三：删除控制点

旧代码：

```java
opter.RemoveBSplineControlPoint(index);
```

替换为：

```java
double tolerance = 0.001; // 单位：mm
boolean removed = opter.RemoveBSplineKnotForControlPoint(
    index, tolerance);
```

变化说明：

- 删除接口新增 `tolerance` 参数；
- 返回 `false` 表示删除失败，当前曲线不会改变；
- 首尾控制点仍不能删除；
- 删除成功后必须重新获取控制点列表和曲线数据。

### 11.2 不需要替换的接口

以下接口仍可继续使用：

| 接口 | 用途 |
|---|---|
| `GetBSplineControlPoints()` | 获取后处理拟合曲线的控制点 |
| `GetBSplineFitCurve(mileages, outCurve)` | 获取任意里程处的绘制曲线 |
| `GetBSplineFitResult()` | 获取原始测点处的曲线值 |
| `GetBSplineAdjustedProfile()` | 获取原始偏差加当前 B 样条调整量 |
| `GetBSplineFitInfo()` | 获取控制点数量、RMSE 和最大误差 |
| `ApplyBSplineResult()` | 将编辑后的曲线写回优化结果 |
| `UndoBSpline()` / `RedoBSpline()` | 撤销和重做编辑 |
| `CanUndoBSpline()` / `CanRedoBSpline()` | 查询撤销/重做状态 |

### 11.3 前端替换后的标准刷新流程

三类编辑操作统一使用以下流程：

```java
boolean changed;

// 移动
changed = opter.MoveBSplineControlPointY(index, newY);

// 或插入
changed = opter.InsertBSplineKnot(mileage);

// 或删除
changed = opter.RemoveBSplineKnotForControlPoint(index, 0.001);

if (changed) {
    // 编辑成功后重新获取，不能继续使用旧控制点列表
    OptDataVector newControlPoints = opter.GetBSplineControlPoints();

    OptDataVector newCurve = new OptDataVector();
    opter.GetBSplineFitCurve(plotMileages, newCurve);
}
```

### 11.4 迁移注意事项

- 前端不应继续使用旧接口作为新功能的主要调用入口；
- 旧接口可以暂时保留用于兼容旧代码，但新代码应全部使用新接口；
- 插入和删除后控制点数量会变化，前端必须重新绑定控制点列表；
- 重新绑定后，原控制点下标可能发生变化，不要缓存旧下标；
- 拖拽过程中建议调用 `MoveBSplineControlPointY`，拖拽结束后再执行一次完整刷新；
- 前端可以显示 `y_up` 和 `y_down`，但本次人工编辑接口本身不做上下限合法性校验；
- 不要在前端自行生成或修改 `knots`；
- 不要把 `GetBSplineOptControlPoints()` 与 `GetBSplineControlPoints()` 混用。

### 11.5 新旧接口对照速查表

| 旧调用 | 推荐替换 |
|---|---|
| `MoveBSplineControlPoint(index, pt)` | `MoveBSplineControlPointY(index, y)` |
| `InsertBSplineControlPoint(pt, index)` | `InsertBSplineKnot(mileage)` |
| `RemoveBSplineControlPoint(index)` | `RemoveBSplineKnotForControlPoint(index, tolerance)` |
| 手工重新计算整条曲线 | `GetBSplineFitCurve(mileages, outCurve)` |
| 直接修改控制点 `x` | 不修改，控制点里程锚点固定 |
| 仅保存控制点 | 同时保存 `degree` 和 `knots` |

前端不应：

- 修改控制点 `x` 来实现横向拖拽；
- 插入时自行指定新控制点 `y`；
- 删除控制点后自行重新生成 knots；
- 仅保存控制点而不保存 degree 和 knots；
- 将 `GetBSplineOptControlPoints()` 当作后处理拟合控制点进行编辑。
