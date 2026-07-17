import AlOpter.AIOpter;
import AlOpter.BSplineFitInfo;
import AlOpter.CommonConstrain;
import AlOpter.DoubleArr;
import AlOpter.OptData;
import AlOpter.OptDataVector;
import AlOpter.OpterProcesser;
import AlOpter.Splineline;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import javax.swing.JOptionPane;

public class OptimizationDemoFrame extends Frame {

    // 方案一：离散优化 + B 样条后处理拟合
    private static OpterProcesser s_opter = null;
    private static OptDataVector s_originalData = null;   // 原始偏差（测量值）
    private static OptDataVector s_optResult = null;      // 调整量
    private static OptDataVector s_optDeviation = null;   // 优化后偏差（原始偏差+调整量）
    // 方案二：直接 B 样条优化
    private static OpterProcesser s_bsOptOpter = null;
    private static OptDataVector s_bsOptOriginal = null;  // 原始偏差（测量值）
    private static OptDataVector s_bsOptResult = null;    // 调整量
    private static OptDataVector s_bsOptProfile = null;   // 最终偏差

    // ========== 控制点编辑模式 ==========
    private static final int CTRL_MODE_NONE = 0;
    private static final int CTRL_MODE_MOVE = 1;
    private static final int CTRL_MODE_INSERT = 2;
    private static final int CTRL_MODE_DELETE = 3;
    private static int s_ctrlMode = CTRL_MODE_NONE;

    // 移动控制点时，第一次点击选中的控制点索引（-1 表示未选中）
    private static int s_moveSelectedIndex = -1;

    public OptimizationDemoFrame() {
        super("轨道优化演示");
        setSize(900, 700);
        setLayout(new BorderLayout());

        Panel toolbar = new Panel(new FlowLayout(FlowLayout.LEFT));
        Button runBtn = new Button("选择文件并运行");
        Button bsplineBtn = new Button("B样条后处理拟合");
        Button bsOptBtn = new Button("直接B样条优化");
        Button customBtn = new Button("初始化样条曲线");
        Button moveCtrlBtn = new Button("移动控制点");
        Button insertCtrlBtn = new Button("插入控制点");
        Button deleteCtrlBtn = new Button("删除控制点");
        Button undoBtn = new Button("撤销");
        Button redoBtn = new Button("重做");
        Button exitBtn = new Button("关闭");
        toolbar.add(runBtn);
        toolbar.add(bsplineBtn);
        toolbar.add(bsOptBtn);
        toolbar.add(customBtn);
        toolbar.add(moveCtrlBtn);
        toolbar.add(insertCtrlBtn);
        toolbar.add(deleteCtrlBtn);
        toolbar.add(undoBtn);
        toolbar.add(redoBtn);
        toolbar.add(exitBtn);
        add(toolbar, BorderLayout.NORTH);

        PlotCanvas canvas = new PlotCanvas();
        add(canvas, BorderLayout.CENTER);

        runBtn.addActionListener(e -> new Thread(() -> runOptimization(this, canvas)).start());
        bsplineBtn.addActionListener(e -> new Thread(() -> runBSplineFitting(canvas)).start());
        bsOptBtn.addActionListener(e -> new Thread(() -> runBSplineOpt(canvas)).start());
        customBtn.addActionListener(e -> onCustomButtonClicked(this, canvas));
        moveCtrlBtn.addActionListener(e -> setCtrlMode(CTRL_MODE_MOVE, canvas));
        insertCtrlBtn.addActionListener(e -> setCtrlMode(CTRL_MODE_INSERT, canvas));
        deleteCtrlBtn.addActionListener(e -> setCtrlMode(CTRL_MODE_DELETE, canvas));
        undoBtn.addActionListener(e -> onUndo(canvas));
        redoBtn.addActionListener(e -> onRedo(canvas));

        exitBtn.addActionListener(e -> dispose());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
        
        // 尝试居中
        setLocationRelativeTo(null);
    }

    // 自定义按钮的响应函数：用于样条曲线测试
    private static void onCustomButtonClicked(Frame parent, PlotCanvas canvas) {
        String filePath = openFileDialog(parent);
        if (filePath == null) return;
        
        OptDataVector list = readCsv(filePath);
        if (list.isEmpty()) return;

        // 初始化样条曲线
        Splineline splineline = new Splineline(list);
        splineline.Init(200);     
        
        // 获取样条生成的偏差调整量
        OptDataVector optdata = splineline.GetData();
        OptDataVector resList = new OptDataVector();
        for (int i = 0; i < optdata.size(); i++) {
            // 优化后偏差 = 原始偏差 + 调整量
            resList.add(new OptData(optdata.get(i).getX(), optdata.get(i).getY() + list.get(i).getY(), list.get(i).getY_down(), list.get(i).getY_up()));
        }

        // 1. 先绘制一张偏差量前后对比图（原始偏差 vs 优化后偏差）
        EventQueue.invokeLater(() -> showLinePlot("样条优化前后偏差对比 (蓝色:原始, 红色:优化后)", list, resList));

        // 计算 10m 弦正矢
        OptDataVector smoothBefore = new OptDataVector();
        OptDataVector smoothAfter = new OptDataVector();
        
        if (AIOpter.SmoothCal(list, smoothBefore, 10) && AIOpter.SmoothCal(resList, smoothAfter, 10)) {
            // 2. 再弹窗显示 10m 弦对比图
            EventQueue.invokeLater(() -> showLinePlot("样条优化前后10m弦对比 (蓝色:原始, 红色:优化后)", smoothBefore, smoothAfter));
        }

        // 插入一个测试点并重新计算（保持原有逻辑演示）
        OptDataVector pickPoints = splineline.GetAllPickPts();
        if (pickPoints.size() > 3) {
            OptData insertData = new OptData((pickPoints.get(2).getX() + pickPoints.get(3).getX()) / 2, 10);
            if (splineline.InsertPickPoint(insertData)) {
                optdata = splineline.GetData();
                OptDataVector resListUpdated = new OptDataVector();
                for (int i = 0; i < optdata.size(); i++) {
                    resListUpdated.add(new OptData(optdata.get(i).getX(), optdata.get(i).getY() + list.get(i).getY(), 0, 0));
                }
                
                OptDataVector smoothAfterUpdated = new OptDataVector();
                if (AIOpter.SmoothCal(resListUpdated, smoothAfterUpdated, 10)) {
                    EventQueue.invokeLater(() -> showLinePlot("更新测点后10m弦对比 (蓝色:原始, 红色:更新后)", smoothBefore, smoothAfterUpdated));
                }
            }
        }
    }

    private static void runOptimization(Frame parent, PlotCanvas targetCanvas) {
        String filePath = openFileDialog(parent);
        if (filePath == null) return;

        OptDataVector list = readCsv(filePath);
        if (list.isEmpty()) return;

        CommonConstrain constrain = new CommonConstrain();
        constrain.setM_ShortWaveLength(20);
        constrain.setIsPlane(true);
        constrain.setM_ShortWaveLimit(3);
        constrain.setM_factor(0.01);        // 平滑系数；平滑为主取0.01，线形回归为主取1.0
        constrain.setM_SuperSlope(0.5);
        constrain.setIsFixed(true);
        
        OpterProcesser proceser = new OpterProcesser(list, constrain);
        if (!proceser.StartOptPro())
            return;

        s_opter = proceser;
        s_originalData = list;
        s_optResult = proceser.GetOptResult();                     // 调整量
        OptDataVector listResult = s_optResult;
        OptDataVector resVector = new OptDataVector();              // 优化后的偏差值
        OptDataVector upVector = new OptDataVector();               // 优化前的偏差值
        
        for (int i = 0; i < listResult.size(); i++) {
            upVector.add(new OptData(list.get(i).getX(), list.get(i).getY()));
            resVector.add(new OptData(listResult.get(i).getX(), list.get(i).getY() + listResult.get(i).getY(), 
                                   list.get(i).getY_down(), list.get(i).getY_up()));
        }
        s_optDeviation = resVector;

        // 计算优化前的 10m 弦
        OptDataVector smoothBefore = new OptDataVector();
        if(!AIOpter.SmoothCal(upVector, smoothBefore, 10))
            return;     
        
        // 计算优化后的 10m 弦
        OptDataVector smoothResult = new OptDataVector();
        if(!AIOpter.SmoothCal(resVector, smoothResult, 10))
            return; 

        // 更新主窗口绘图（原始偏差 vs 优化后偏差）
        EventQueue.invokeLater(() -> targetCanvas.setSeries(new OptDataVector[]{upVector, resVector}));

        // 弹窗显示 10m 弦对比图（优化前 vs 优化后）
        EventQueue.invokeLater(() -> showLinePlot("10m弦平顺性对比 (蓝色:优化前, 红色:优化后)", smoothBefore, smoothResult));

        // 打印详细结果
        for (int i = 0; i < listResult.size(); i++) {
            System.out.printf("X: %.3f\t原始偏差: %.3f\t调整量: %.3f\t优化后偏差: %.3f\t优化前10m弦: %.3f\t优化后10m弦: %.3f%n",
                list.get(i).getX(), list.get(i).getY(), listResult.get(i).getY(), 
                resVector.get(i).getY(), smoothBefore.get(i).getY(), smoothResult.get(i).getY());
        }
    }

    // ========== 方案二：直接 B 样条优化 ==========
    private static void runBSplineOpt(PlotCanvas targetCanvas) {
        String filePath = openFileDialog(null);
        if (filePath == null) return;

        OptDataVector list = readCsv(filePath);
        if (list.isEmpty()) return;

        System.out.println("===== 直接 B 样条优化 =====");

        CommonConstrain constrain = new CommonConstrain();
        constrain.setM_ShortWaveLength(50);
        constrain.setIsPlane(true);
        constrain.setM_ShortWaveLimit(3);
        constrain.setM_factor(1.0);
        constrain.setM_SuperSlope(0.5);
        constrain.setIsFixed(true);

        OpterProcesser opter = new OpterProcesser(list, constrain);

        // DP 自动确定控制点数量和位置（推荐方式）
        System.out.println(">> 执行直接 B 样条优化 (DP 自适应)...");
        if (!opter.StartOptBSpline()) {
            System.out.println("直接 B 样条优化失败");
            return;
        }

        // ---- 保存结果 ----
        s_bsOptOpter = opter;
        s_bsOptOriginal = list;
        s_bsOptResult = opter.GetBSplineOptResult();         // 调整量
        s_bsOptProfile = opter.GetBSplineOptProfile();       // 最终偏差 = 原始偏差 + 调整量

        // ---- 查看控制点 ----
        OptDataVector ctrlPts = opter.GetBSplineOptControlPoints();
        System.out.println(">> B 样条控制点 (" + ctrlPts.size() + " 个):");
        for (int i = 0; i < ctrlPts.size(); i++) {
            OptData pt = ctrlPts.get(i);
            System.out.printf("  P%d: 里程=%.3f, 调整量=%.3f%n", i, pt.getX(), pt.getY());
        }

        System.out.println(">> 调整量曲线: " + s_bsOptResult.size() + " 个点");
        System.out.println(">> 最终偏差: " + s_bsOptProfile.size() + " 个点");

        // ---- 计算 10m 弦不平顺 ----
        OptDataVector smoothBefore = new OptDataVector();
        OptDataVector smoothAfter = new OptDataVector();
        if (!AIOpter.SmoothCal(list, smoothBefore, 10) || !AIOpter.SmoothCal(s_bsOptProfile, smoothAfter, 10))
            return;

        // ---- 打印结果 ----
        for (int i = 0; i < s_bsOptResult.size(); i++) {
            System.out.printf("X: %.3f\t原始偏差: %.3f\t调整量: %.3f\t优化后偏差: %.3f\t优化前10m弦: %.3f\t优化后10m弦: %.3f%n",
                list.get(i).getX(), list.get(i).getY(), s_bsOptResult.get(i).getY(),
                s_bsOptProfile.get(i).getY(), smoothBefore.get(i).getY(), smoothAfter.get(i).getY());
        }

        // ---- 绘图展示 ----
        EventQueue.invokeLater(() -> targetCanvas.setSeries(new OptDataVector[]{list, s_bsOptProfile}));

        EventQueue.invokeLater(() -> showLinePlot("直接B样条优化10m弦对比 (蓝色:优化前, 红色:优化后)", smoothBefore, smoothAfter));

        EventQueue.invokeLater(() -> showLinePlot("直接B样条控制点分布", ctrlPts));

        System.out.println("===== 直接 B 样条优化完成 =====");
    }

    // ========== 方案一：B 样条后处理拟合 ==========
    private static void runBSplineFitting(PlotCanvas targetCanvas) {
        if (s_opter == null) {
            System.out.println("请先点击「选择文件并运行」完成离散点优化");
            return;
        }

        System.out.println("===== B 样条后处理拟合 =====");

        // ---- 第2步：自适应 B 样条拟合（输入红色曲线） ----
        String input = JOptionPane.showInputDialog(
            null,
            "请输入拟合容差 (mm)：",
            "B 样条拟合容差",
            JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            System.out.println("用户取消输入");
            return;
        }
        double tolerance;
        try {
            tolerance = Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            System.out.println("容差输入无效，使用默认值 0.3mm");
            tolerance = 0.3;
        }
        if (tolerance <= 0) {
            System.out.println("容差必须为正数，使用默认值 0.3mm");
            tolerance = 0.3;
        }

        System.out.printf(">> 执行自适应 B 样条拟合 (容差 %.3fmm)...%n", tolerance);
        if (!s_opter.FitBSplineAdaptive(s_optDeviation, tolerance)) {
            System.out.println("B 样条拟合失败");
            return;
        }

        // 查看拟合精度
        BSplineFitInfo info = s_opter.GetBSplineFitInfo();
        if (info.getIsValid()) {
            System.out.printf("拟合精度: 控制点数量=%d, RMSE=%.3f mm, 最大误差=%.3f mm%n",
                info.getNumControlPts(), info.getRmse(), info.getMaxError());
        }

        // ---- 获取并打印控制点 ----
        OptDataVector ctrlPts = s_opter.GetBSplineControlPoints();
        System.out.println(">> B 样条控制点 (" + ctrlPts.size() + " 个):");
        for (int i = 0; i < ctrlPts.size(); i++) {
            OptData pt = ctrlPts.get(i);
            System.out.printf("  P%d: 里程=%.3f, 调整量=%.3f%n", i, pt.getX(), pt.getY());
        }
        targetCanvas.setControlPoints(ctrlPts);

        // ---- 获取拟合后的调整量曲线 ----
        OptDataVector fitted = s_opter.GetBSplineFitResult();
        System.out.println(">> 拟合后调整量曲线: " + fitted.size() + " 个点");

        // ---- 获取拟合后的最终偏差曲线 ----
        OptDataVector adjustedProfile = s_opter.GetBSplineAdjustedProfile();
        System.out.println(">> 拟合后最终偏差: " + adjustedProfile.size() + " 个点");
       
        // ---- 将 B 样条结果写回优化结果 ----
        s_opter.ApplyBSplineResult();

        // ---- 计算并检查优化后的不平顺 ----
        OptDataVector smoothness = new OptDataVector();
        if (s_opter.SmoothCalOpt(smoothness, 20.0)) {
            System.out.println(">> 20m 弦不平顺计算完成");
        }

        // ---- 绘图展示（利用 GetBSplineCurvePoints 完整参数版） ----
        int degree = s_opter.GetBSplineDegree();
        DoubleArr knots = s_opter.GetBSplineKnots();
        System.out.println(">> 控制点: " + ctrlPts.size() + ", degree: " + degree + ", knots: " + knots.size());

        // 用红色曲线的 X 值作为求值点，native 填入 Y（B 样条拟合值）
        OptDataVector evalPts = new OptDataVector();
        for (int i = 0; i < s_optDeviation.size(); i++) {
            evalPts.add(new OptData(s_optDeviation.get(i).getX(), 0, 0, 0));
        }
        OpterProcesser.GetBSplineCurvePoints(ctrlPts, knots, degree, evalPts);
        System.out.println(">> GetBSplineCurvePoints: " + evalPts.size() + " 点");

        System.out.println(">> 原始:" + s_originalData.size()
            + " 离散优化后:" + s_optDeviation.size()
            + " B样条:" + evalPts.size());

        // 主窗口：原始偏差(蓝) / 离散优化后(红) / B样条光滑(绿)
        EventQueue.invokeLater(() ->
            targetCanvas.setSeries(new OptDataVector[]{
                s_originalData, s_optDeviation, evalPts}));
        // 弹窗：离散优化后(蓝) vs B样条光滑(红)
        OptDataVector denseDiscrete = densify(s_optDeviation, 1.0);
        EventQueue.invokeLater(() ->
            showLinePlot("B样条偏差量对比 (蓝色:离散优化后偏差, 红色:B样条光滑曲线)",
                denseDiscrete, evalPts));

        System.out.println("===== B 样条后处理拟合完成 =====");
    }

    /** Linear interpolation to densify sparse curve for smooth plotting. */
    private static OptDataVector densify(OptDataVector src, double step) {
        if (src == null || src.size() < 2) return src;
        OptDataVector result = new OptDataVector();
        for (int i = 0; i < src.size() - 1; i++) {
            OptData a = src.get(i), b = src.get(i + 1);
            double x0 = a.getX(), x1 = b.getX();
            double y0 = a.getY(), y1 = b.getY();
            result.add(new OptData(x0, y0, 0, 0));
            for (double x = x0 + step; x < x1 - 1e-6; x += step) {
                double t = (x - x0) / (x1 - x0);
                result.add(new OptData(x, y0 + t * (y1 - y0), 0, 0));
            }
        }
        OptData last = src.get(src.size() - 1);
        result.add(new OptData(last.getX(), last.getY(), 0, 0));
        return result;
    }

    /** Linear interpolation at a single X coordinate. */
    private static double interpolateY(OptDataVector src, double x) {
        if (src == null || src.size() == 0) return 0;
        if (src.size() == 1) return src.get(0).getY();
        // 边界外推
        if (x <= src.get(0).getX()) return src.get(0).getY();
        if (x >= src.get(src.size() - 1).getX()) return src.get(src.size() - 1).getY();
        for (int i = 0; i < src.size() - 1; i++) {
            OptData a = src.get(i), b = src.get(i + 1);
            if (x >= a.getX() && x <= b.getX()) {
                double t = (x - a.getX()) / (b.getX() - a.getX());
                return a.getY() + t * (b.getY() - a.getY());
            }
        }
        return 0;
    }

    //写个Java读取Excel的函数
    public static OptDataVector readCsv(String filePath) {
        System.out.println("开始读取文件: " + filePath);
        OptDataVector reaResult = new OptDataVector();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "GBK"))) { 
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                
                // 替换中文逗号
                line = line.replace("，", ",");
                
                // 以逗号拆分，-1 保留空字符串
                String[] columns = line.split(",", -1);
                
                // 简单的跳过表头逻辑
                try {
                    // 尝试解析第一列或第二列作为数字，如果都失败，则认为是表头
                    Double.parseDouble(columns[0]);
                } catch (NumberFormatException e) {
                    try {
                         if (columns.length > 1) Double.parseDouble(columns[1]);
                         else throw e;
                    } catch (NumberFormatException e2) {
                        System.out.println("跳过第 " + lineNum + " 行 (可能是表头): " + line);
                        continue;
                    }
                }

                try {
                    double x = 0;
                    double y = 0;
                    
                    // 针对不同文件格式的简单适配逻辑
                    // 格式1: 里程, 偏差 (2列)
                    // 格式2: 序号, 里程, 偏差... (沪昆数据)
                    // 格式3: 里程, , X, Y... (测试数据)
                    
                    if (columns.length >= 2) {
                        double col0 = 0;
                        boolean col0IsNum = true;
                        try { col0 = Double.parseDouble(columns[0]); } catch(Exception ex) { col0IsNum = false; }
                        
                        double col1 = 0;
                        boolean col1IsNum = true;
                        try { col1 = Double.parseDouble(columns[1]); } catch(Exception ex) { col1IsNum = false; }

                        if (col0IsNum && col1IsNum) {
                            // 如果第一列数值很小(比如序号)，第二列数值很大(比如里程)，则取第二列为X
                            if (col0 < 1000 && col1 > 10000) {
                                x = col1;
                                // 尝试取第三列作为Y
                                if (columns.length > 2 && !columns[2].trim().isEmpty()) {
                                    y = Double.parseDouble(columns[2]);
                                }
                            } else {
                                // 否则默认第一列是X
                                x = col0;
                                // 如果第二列有值，取第二列
                                if (!columns[1].trim().isEmpty()) {
                                    y = col1;
                                } else if (columns.length > 2 && !columns[2].trim().isEmpty()) {
                                    // 第二列为空，尝试第三列
                                    y = Double.parseDouble(columns[2]);
                                }
                            }
                        } else if (col0IsNum) {
                            x = col0;
                             if (columns.length > 2 && !columns[2].trim().isEmpty()) {
                                y = Double.parseDouble(columns[2]);
                            }
                        }
                    }
                    
                    // 只有当读取到有效数据时才添加
                    reaResult.add(new OptData(x, y, 30, -30));
                    if (lineNum <= 5) {
                        System.out.println("读取第 " + lineNum + " 行: X=" + x + ", Y=" + y);
                    }
                } catch (Exception e) {
                    System.err.println("第 " + lineNum + " 行数据解析失败: " + line + " 错误: " + e.getMessage());
                }
            }
            System.out.println("读取完成，共读取 " + reaResult.size() + " 个点。");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return reaResult;
    }

    public static String openFileDialog(Frame parent) {
        FileDialog fileDialog = new FileDialog(parent, "选择文件", FileDialog.LOAD);
        fileDialog.setVisible(true);
        String dir = fileDialog.getDirectory();
        String file = fileDialog.getFile();
        if (file != null) {
            return new File(dir, file).getAbsolutePath();
        }
        return null;
    }

    private static void showLinePlot(String title, OptDataVector... series) {
        if (series == null || series.length == 0) {
            System.out.println("无曲线数据可展示");
            return;
        }

        Frame plotFrame = new Frame(title);
        plotFrame.setSize(800, 600);
        plotFrame.add(new PlotCanvas(series));
        plotFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                plotFrame.dispose();
            }
        });
        plotFrame.setVisible(true);
    }

    private static final class PlotCanvas extends Canvas {
        private OptDataVector[] series;
        private OptDataVector controlPoints;
        private final Color[] palette = new Color[]{Color.BLUE, Color.RED, Color.GREEN.darker(), Color.ORANGE, Color.MAGENTA};

        // 坐标转换缓存（由 paint 更新，供鼠标点击换算）
        private double m_minX = 0, m_maxX = 1, m_minY = 0, m_maxY = 1;
        private double m_xScale = 1, m_yScale = 1;
        private int m_padding = 50;

        private PlotCanvas(OptDataVector[] series) {
            this.series = series;
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onCanvasClick(e.getX(), e.getY());
                }
            });
        }

        private PlotCanvas() {
            this.series = new OptDataVector[0];
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onCanvasClick(e.getX(), e.getY());
                }
            });
        }

        public void setSeries(OptDataVector[] series) {
            this.series = series != null ? series : new OptDataVector[0];
            repaint();
        }

        public void setControlPoints(OptDataVector pts) {
            this.controlPoints = pts;
            repaint();
        }

        /** 查找与屏幕坐标最近的控制点索引，距离阈值 12 像素 */
        private int findNearestCtrlPt(int sx, int sy) {
            if (controlPoints == null) return -1;
            for (int i = 0; i < controlPoints.size(); i++) {
                OptData pt = controlPoints.get(i);
                int cx = (int) Math.round(m_padding + (pt.getX() - m_minX) * m_xScale);
                int cy = (int) Math.round(getHeight() - m_padding - (pt.getY() - m_minY) * m_yScale);
                double dist = Math.sqrt((cx - sx) * (cx - sx) + (cy - sy) * (cy - sy));
                if (dist < 12) return i;
            }
            return -1;
        }

        /** 画布点击分发：根据当前编辑模式执行对应操作 */
        private void onCanvasClick(int sx, int sy) {
            if (s_ctrlMode == CTRL_MODE_NONE) return;
            if (controlPoints == null || controlPoints.size() < 2) {
                System.out.println("当前没有可编辑的控制点，请先执行 B 样条拟合");
                return;
            }
            // 防止未绘制时坐标换算无效
            if (m_minX >= m_maxX || m_minY >= m_maxY || m_xScale <= 0 || m_yScale <= 0) {
                System.out.println("画布尚未就绪，请稍后再试");
                return;
            }
            handleCanvasEdit(null, this, sx, sy);
        }

        @Override
        public void paint(Graphics g) {
            if (!hasData()) {
                return;
            }

            int width = getWidth();
            int height = getHeight();
            int padding = 50;

            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;

            for (OptDataVector vector : series) {
                if (vector == null || vector.isEmpty()) {
                    continue;
                }
                for (int i = 0; i < vector.size(); i++) {
                    OptData point = vector.get(i);
                    minX = Math.min(minX, point.getX());
                    maxX = Math.max(maxX, point.getX());
                    minY = Math.min(minY, point.getY());
                    maxY = Math.max(maxY, point.getY());
                }
            }

            if (maxX == minX) {
                maxX += 1;
                minX -= 1;
            }
            if (maxY == minY) {
                maxY += 1;
                minY -= 1;
            }

            double xScale = (width - padding * 2.0) / (maxX - minX);
            double yScale = (height - padding * 2.0) / (maxY - minY);

            // 缓存坐标换算参数供鼠标点击使用
            m_minX = minX; m_maxX = maxX; m_minY = minY; m_maxY = maxY;
            m_xScale = xScale; m_yScale = yScale; m_padding = padding;

            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);

            g2.setColor(Color.LIGHT_GRAY);
            g2.fillRect(padding, padding, width - padding * 2, height - padding * 2);

            // 编辑模式提示
            if (s_ctrlMode != CTRL_MODE_NONE) {
                int tipWidth = 260;
                g2.setColor(new Color(255, 200, 200, 200));
                g2.fillRect(padding, 5, tipWidth, 22);
                g2.setColor(Color.RED);
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                String modeTip;
                if (s_ctrlMode == CTRL_MODE_MOVE) {
                    modeTip = (s_moveSelectedIndex >= 0)
                        ? "⚡ 移动 P" + s_moveSelectedIndex + "：点击目标位置"
                        : "⚡ 移动控制点：点击控制点选中";
                } else if (s_ctrlMode == CTRL_MODE_INSERT) {
                    modeTip = "⚡ 插入控制点：点击插入位置";
                } else if (s_ctrlMode == CTRL_MODE_DELETE) {
                    modeTip = "⚡ 删除控制点：点击控制点";
                } else {
                    modeTip = "";
                }
                g2.drawString(modeTip, padding + 5, 22);
            }

            g2.setColor(Color.BLACK);
            int plotWidth = width - padding * 2;
            int plotHeight = height - padding * 2;
            g2.drawRect(padding, padding, plotWidth, plotHeight);

            //绘制刻度
            int tickCount = 10;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            for (int i = 0; i <= tickCount; i++) {
                double ratio = i / (double) tickCount;
                int x = (int) Math.round(padding + ratio * plotWidth);

                double xValue = minX + ratio * (maxX - minX);
                double yValue = minY + ratio * (maxY - minY);

                //X轴刻度
                g2.drawLine(x, height - padding, x, height - padding + 5);
                g2.drawString(String.format("%.2f", xValue), x - 15, height - padding + 20);

                //Y轴刻度
                int yTick = (int) Math.round(height - padding - ratio * plotHeight);
                g2.drawLine(padding - 5, yTick, padding, yTick);
                g2.drawString(String.format("%.2f", yValue), padding - 45, yTick + 5);
            }

            g2.setStroke(new BasicStroke(2f));
            for (int s = 0; s < series.length; s++) {
                OptDataVector vector = series[s];
                if (vector == null || vector.isEmpty()) {
                    continue;
                }

                int pointCount = vector.size();
                if (pointCount < 2) {
                    continue;
                }

                int[] xPoints = new int[pointCount];
                int[] yPoints = new int[pointCount];

                for (int i = 0; i < pointCount; i++) {
                    OptData point = vector.get(i);
                    xPoints[i] = (int) Math.round(padding + (point.getX() - minX) * xScale);
                    yPoints[i] = (int) Math.round(height - padding - (point.getY() - minY) * yScale);
                }

                g2.setColor(palette[s % palette.length]);
                g2.drawPolyline(xPoints, yPoints, pointCount);
            }

            // 绘制控制点（空心圆圈；选中时实心红色）
            if (controlPoints != null && controlPoints.size() >= 2) {
                g2.setStroke(new BasicStroke(1.5f));
                for (int i = 0; i < controlPoints.size(); i++) {
                    OptData pt = controlPoints.get(i);
                    int cx = (int) Math.round(padding + (pt.getX() - minX) * xScale);
                    int cy = (int) Math.round(height - padding - (pt.getY() - minY) * yScale);
                    int r = 5;
                    boolean isSelected = (s_ctrlMode == CTRL_MODE_MOVE && i == s_moveSelectedIndex);
                    if (isSelected) {
                        g2.setColor(Color.RED);
                        g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                    } else {
                        g2.setColor(Color.MAGENTA);
                        g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                    }
                    g2.drawString("P" + i, cx + r + 2, cy + 4);
                }
            }

            g2.setColor(Color.DARK_GRAY);
            g2.drawString(String.format("X: %.2f ~ %.2f", minX, maxX), padding + 10, height - padding + 20);
            g2.drawString(String.format("Y: %.2f ~ %.2f", minY, maxY), padding + 10, height - padding + 40);

            drawLegend(g2, padding);
        }

        private boolean hasData() {
            if (series == null || series.length == 0) {
                return false;
            }
            for (OptDataVector vector : series) {
                if (vector != null && vector.size() >= 2) {
                    return true;
                }
            }
            return false;
        }

        private void drawLegend(Graphics2D g2, int padding) {
            int legendX = padding + 10;
            int legendY = padding + 20;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));

            for (int i = 0; i < series.length; i++) {
                OptDataVector vector = series[i];
                if (vector == null || vector.isEmpty()) {
                    continue;
                }
                g2.setColor(palette[i % palette.length]);
                g2.fillRect(legendX, legendY + i * 18 - 8, 12, 12);
                g2.setColor(Color.BLACK);
                g2.drawString("Series " + (i + 1), legendX + 20, legendY + i * 18);
            }
        }
    }

    // =======================================================================
    // 控制点编辑辅助方法
    // =======================================================================

    /** 切换编辑模式（点击同一按钮取消） */
    private static void setCtrlMode(int mode, PlotCanvas canvas) {
        if (s_ctrlMode == mode) {
            s_ctrlMode = CTRL_MODE_NONE;
            s_moveSelectedIndex = -1;
            System.out.println("控制点编辑模式已关闭");
        } else {
            s_ctrlMode = mode;
            s_moveSelectedIndex = -1;
            String[] names = {"", "移动控制点", "插入控制点", "删除控制点"};
            System.out.println("进入「" + names[mode] + "」模式");
            if (mode == CTRL_MODE_MOVE) {
                System.out.println("  第1步：点击画布上的控制点圆圈选中它");
            } else if (mode == CTRL_MODE_INSERT) {
                System.out.println("  点击画布上要插入控制点的位置");
            } else if (mode == CTRL_MODE_DELETE) {
                System.out.println("  点击要删除的控制点圆圈");
            }
        }
        canvas.repaint();
    }

    /** 撤销上一次 B 样条控制点操作 */
    private static void onUndo(PlotCanvas canvas) {
        OpterProcesser opter = getActiveOpter();
        if (opter == null) {
            System.out.println("没有可撤销的 B 样条操作");
            return;
        }
        if (!opter.CanUndoBSpline()) {
            System.out.println("已没有可撤销的操作");
            return;
        }
        if (opter.UndoBSpline()) {
            System.out.println("✓ 撤销成功");
            // 退出编辑模式避免状态混乱
            s_ctrlMode = CTRL_MODE_NONE;
            s_moveSelectedIndex = -1;
            updateCanvasAfterEdit(canvas, opter);
        } else {
            System.out.println("✗ 撤销失败");
        }
    }

    /** 重做上一次撤销的 B 样条控制点操作 */
    private static void onRedo(PlotCanvas canvas) {
        OpterProcesser opter = getActiveOpter();
        if (opter == null) {
            System.out.println("没有可重做的 B 样条操作");
            return;
        }
        if (!opter.CanRedoBSpline()) {
            System.out.println("已没有可重做的操作");
            return;
        }
        if (opter.RedoBSpline()) {
            System.out.println("✓ 重做成功");
            s_ctrlMode = CTRL_MODE_NONE;
            s_moveSelectedIndex = -1;
            updateCanvasAfterEdit(canvas, opter);
        } else {
            System.out.println("✗ 重做失败");
        }
    }

    /** 获取当前拥有控制点的 OpterProcesser */
    private static OpterProcesser getActiveOpter() {
        if (s_opter != null) {
            try {
                OptDataVector cpts = s_opter.GetBSplineControlPoints();
                if (cpts != null && cpts.size() > 0) return s_opter;
            } catch (Exception ignored) { }
        }
        if (s_bsOptOpter != null) {
            try {
                OptDataVector cpts = s_bsOptOpter.GetBSplineOptControlPoints();
                if (cpts != null && cpts.size() > 0) return s_bsOptOpter;
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** 从 opter 获取控制点（适配两种方案） */
    private static OptDataVector getOpterCtrlPts(OpterProcesser opter) {
        if (opter == null) return null;
        if (opter == s_opter) return opter.GetBSplineControlPoints();
        if (opter == s_bsOptOpter) return opter.GetBSplineOptControlPoints();
        return null;
    }

    /** 编辑后刷新画布：更新控制点 + 重新计算 B 样条曲线 */
    private static void updateCanvasAfterEdit(PlotCanvas canvas, OpterProcesser opter) {
        if (opter == null || canvas == null) return;

        OptDataVector newCtrlPts = getOpterCtrlPts(opter);
        if (newCtrlPts == null || newCtrlPts.size() < 2) return;

        canvas.setControlPoints(newCtrlPts);

        // 重新计算 B 样条曲线
        int degree = opter.GetBSplineDegree();
        DoubleArr knots = opter.GetBSplineKnots();

        // 取第一个非空系列的 X 作为求值点
        OptDataVector ref = null;
        for (OptDataVector s : canvas.series) {
            if (s != null && s.size() > 0) { ref = s; break; }
        }
        if (ref == null) return;

        OptDataVector evalPts = new OptDataVector();
        for (int i = 0; i < ref.size(); i++) {
            evalPts.add(new OptData(ref.get(i).getX(), 0, 0, 0));
        }
        OpterProcesser.GetBSplineCurvePoints(newCtrlPts, knots, degree, evalPts);

        // 构建新系列：保留前两个不动，第三个替换为新的 B 样条曲线
        int newLen = Math.max(canvas.series.length, 3);
        OptDataVector[] newSeries = new OptDataVector[newLen];
        for (int i = 0; i < canvas.series.length && i < 3; i++) {
            newSeries[i] = canvas.series[i];
        }
        newSeries[2] = evalPts;
        canvas.setSeries(newSeries);

        System.out.println("画布已刷新，当前控制点 " + newCtrlPts.size() + " 个");
    }

    /** 画布点击主入口：由 PlotCanvas.onCanvasClick 调用 */
    private static void handleCanvasEdit(Frame parent, PlotCanvas canvas, int sx, int sy) {
        OpterProcesser opter = getActiveOpter();
        if (opter == null) {
            JOptionPane.showMessageDialog(parent,
                "没有可编辑的 B 样条控制点。\n请先执行「B样条后处理拟合」或「直接B样条优化」。");
            s_ctrlMode = CTRL_MODE_NONE;
            canvas.repaint();
            return;
        }

        OptDataVector ctrlPts = getOpterCtrlPts(opter);
        if (ctrlPts == null || ctrlPts.size() < 2) {
            JOptionPane.showMessageDialog(parent, "控制点数量不足，无法编辑。");
            s_ctrlMode = CTRL_MODE_NONE;
            canvas.repaint();
            return;
        }

        switch (s_ctrlMode) {
            case CTRL_MODE_MOVE:
                handleMoveCtrlPt(parent, canvas, opter, ctrlPts, sx, sy);
                break;
            case CTRL_MODE_INSERT:
                handleInsertCtrlPt(parent, canvas, opter, ctrlPts, sx, sy);
                break;
            case CTRL_MODE_DELETE:
                handleDeleteCtrlPt(parent, canvas, opter, ctrlPts, sx, sy);
                break;
        }
    }

    private static void handleMoveCtrlPt(Frame parent, PlotCanvas canvas, OpterProcesser opter,
                                          OptDataVector ctrlPts, int sx, int sy) {
        if (s_moveSelectedIndex < 0) {
            // ——— 第 1 步：点击控制点选中 ———
            int idx = canvas.findNearestCtrlPt(sx, sy);
            if (idx < 0) {
                System.out.println("未选中控制点，请点击控制点圆圈位置");
                return;
            }
            s_moveSelectedIndex = idx;
            OptData pt = ctrlPts.get(idx);
            System.out.println("已选中 P" + idx + " (里程=" + String.format("%.3f", pt.getX())
                + ", Y=" + String.format("%.3f", pt.getY()) + ")，请点击目标位置");
            canvas.repaint();
        } else {
            // ——— 第 2 步：点击目标位置，移动控制点 ———
            double targetX = canvas.m_minX + (sx - canvas.m_padding) / canvas.m_xScale;
            double targetY = canvas.m_minY + (canvas.getHeight() - canvas.m_padding - sy) / canvas.m_yScale;
            int idx = s_moveSelectedIndex;
            OptData oldPt = ctrlPts.get(idx);
            OptData newPt = new OptData(targetX, targetY, 0, 0);
            if (opter.MoveBSplineControlPoint(idx, newPt)) {
                System.out.println("✓ 控制点 P" + idx + " 已移动: ("
                    + String.format("%.3f", oldPt.getX()) + ", " + String.format("%.3f", oldPt.getY()) + ")"
                    + " → (" + String.format("%.3f", targetX) + ", " + String.format("%.3f", targetY) + ")");
                s_moveSelectedIndex = -1;
                updateCanvasAfterEdit(canvas, opter);
            } else {
                System.out.println("✗ 移动控制点失败");
            }
        }
    }

    private static void handleInsertCtrlPt(Frame parent, PlotCanvas canvas, OpterProcesser opter,
                                            OptDataVector ctrlPts, int sx, int sy) {
        // 屏幕坐标 → 数据坐标
        double x = canvas.m_minX + (sx - canvas.m_padding) / canvas.m_xScale;
        double y = canvas.m_minY + (canvas.getHeight() - canvas.m_padding - sy) / canvas.m_yScale;

        // 根据里程 X 找到插入位置（保持控制点按里程有序）
        int insertIdx = ctrlPts.size(); // 默认插到最后
        for (int i = 0; i < ctrlPts.size(); i++) {
            if (x < ctrlPts.get(i).getX()) {
                insertIdx = i;
                break;
            }
        }

        OptData newPt = new OptData(x, y, 0, 0);
        if (opter.InsertBSplineControlPoint(newPt, insertIdx)) {
            System.out.println("✓ 已在里程 " + String.format("%.3f", x)
                + " 处插入控制点 P" + insertIdx + "，Y=" + String.format("%.3f", y));
            updateCanvasAfterEdit(canvas, opter);
        } else {
            System.out.println("✗ 插入控制点失败");
        }
    }

    private static void handleDeleteCtrlPt(Frame parent, PlotCanvas canvas, OpterProcesser opter,
                                            OptDataVector ctrlPts, int sx, int sy) {
        int idx = canvas.findNearestCtrlPt(sx, sy);
        if (idx < 0) {
            System.out.println("未选中控制点，请点击控制点圆圈位置");
            return;
        }
        OptData pt = ctrlPts.get(idx);
        int confirm = JOptionPane.showConfirmDialog(parent,
            "确认删除控制点 P" + idx + "?\n"
            + "  里程: " + String.format("%.3f", pt.getX())
            + "  调整量: " + String.format("%.3f", pt.getY()),
            "删除控制点",
            JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (opter.RemoveBSplineControlPoint(idx)) {
                System.out.println("✓ 控制点 P" + idx + " 已删除");
                updateCanvasAfterEdit(canvas, opter);
            } else {
                System.out.println("✗ 删除控制点失败");
            }
        }
    }

}