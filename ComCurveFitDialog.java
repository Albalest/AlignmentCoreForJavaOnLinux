import AlignmentCore.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComCurveFitDialog extends JDialog {

    private final PlotPanel plot;

    private SurveyDatas datas;
    private ComCurveDesigner comCurve;

    private final ComCurveDesignerUndoManager undoManager;
    private final AlignmentFitting fittingData; // optional
    private final AlignmentFittingUndoManager alUndoManager; // optional

    private final JTextField txtLen1 = new JTextField(8);
    private final JTextField txtLen2 = new JTextField(8);
    private final JTextField txtLen3 = new JTextField(8);

    private final JTextField txtStInter = new JTextField(6);
    private final JTextField txtEdInter = new JTextField(6);

    private int editType = 0; // 0-none 1-add 2-del 3-move
    private boolean moveFirstClick = true;
    private AL_Point3d movePrePt;

    private final List<AL_Point3d> keyPts = new ArrayList<>();

    private double[] miles = new double[0];
    private double[] curv = new double[0];

    private double[] marginMiles = new double[0];
    private double[] marginMm = new double[0];

    public ComCurveFitDialog(Frame owner, SurveyDatas surveyDatas) {
        this(owner, surveyDatas, null, null);
    }

    public ComCurveFitDialog(Frame owner, AlignmentFitting fittingData, AlignmentFittingUndoManager alUndoManager) {
        this(owner, fittingData != null ? fittingData.GetSurveyDatas() : null, fittingData, alUndoManager);
    }

    private ComCurveFitDialog(Frame owner, SurveyDatas surveyDatas, AlignmentFitting fittingData, AlignmentFittingUndoManager alUndoManager) {
        super(owner, "复曲线设计", true);

        this.datas = surveyDatas;
        this.fittingData = fittingData;
        this.alUndoManager = alUndoManager;

        this.undoManager = new ComCurveDesignerUndoManager();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 780);
        setLayout(new BorderLayout());

        plot = new PlotPanel();
        add(plot, BorderLayout.CENTER);

        JPanel controls = buildControlsPanel();
        add(controls, BorderLayout.SOUTH);

        setLocationRelativeTo(owner);

        initDesigner();
        refreshOriginCurvature();
        refreshPlot();
    }

    private JPanel buildControlsPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BorderLayout());

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAdd = new JButton("插入关键点");
        JButton btnDel = new JButton("删除关键点");
        JButton btnMove = new JButton("移动关键点");
        JButton btnAuto = new JButton("自动分段");

        JButton btnInit = new JButton("初始化直线和圆");
        JButton btnInitByKey = new JButton("根据分界点初始化");
        JButton btnShowMargin = new JButton("计算并显示偏差值");
        JButton btnRound = new JButton("取整优化");

        JButton btnUndo = new JButton("Undo");
        JButton btnRedo = new JButton("Redo");

        row1.add(btnAdd);
        row1.add(btnDel);
        row1.add(btnMove);
        row1.add(btnAuto);
        row1.add(btnInit);
        row1.add(btnInitByKey);
        row1.add(btnShowMargin);
        row1.add(btnRound);
        row1.add(btnUndo);
        row1.add(btnRedo);

        btnAdd.addActionListener(e -> editType = 1);
        btnDel.addActionListener(e -> editType = 2);
        btnMove.addActionListener(e -> {
            editType = 3;
            moveFirstClick = true;
        });
        btnAuto.addActionListener(e -> onAutoSegment());
        btnInit.addActionListener(e -> onInitKeyPtsOnly());
        btnInitByKey.addActionListener(e -> onInitByKeyPts());
        btnShowMargin.addActionListener(e -> onCalAndShowMarginWithExport());
        btnRound.addActionListener(e -> onRoundOptimize());

        btnUndo.addActionListener(e -> {
            if (undoManager.CanUndo()) {
                undoManager.Undo();
                refreshPlotFromDesigner();
            }
        });

        btnRedo.addActionListener(e -> {
            if (undoManager.CanRedo()) {
                undoManager.Redo();
                refreshPlotFromDesigner();
            }
        });

        JPanel row2 = new JPanel();
        row2.setLayout(new BorderLayout());

        JPanel row2Top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2Top.add(new JLabel("第一缓长长度"));
        row2Top.add(txtLen1);
        row2Top.add(new JLabel("第二缓长长度"));
        row2Top.add(txtLen2);
        row2Top.add(new JLabel("第三缓长长度"));
        row2Top.add(txtLen3);

        JButton btnManualOpt = new JButton("按照约定缓和曲线优化半径");
        row2Top.add(btnManualOpt);
        btnManualOpt.addActionListener(e -> onManualOptimize());

        row2Top.add(new JLabel("前交点序号"));
        row2Top.add(txtStInter);
        row2Top.add(new JLabel("后交点序号"));
        row2Top.add(txtEdInter);

        JPanel row2Bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnFilter = new JButton("按照交点序号筛选测量数据");
        JButton btnMerge = new JButton("拟合结果并入");
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");

        btnFilter.setEnabled(fittingData != null);
        btnMerge.setEnabled(fittingData != null && alUndoManager != null);

        row2Bottom.add(btnFilter);
        row2Bottom.add(btnMerge);
        row2Bottom.add(btnOk);
        row2Bottom.add(btnCancel);

        btnFilter.addActionListener(e -> onFilterByInterIndex());
        btnMerge.addActionListener(e -> onMergeIntoFitting());
        btnOk.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
        btnCancel.addActionListener(e -> {
            setVisible(false);
            dispose();
        });

        row2.add(row2Top, BorderLayout.NORTH);
        row2.add(row2Bottom, BorderLayout.SOUTH);

        root.add(row1, BorderLayout.NORTH);
        root.add(row2, BorderLayout.SOUTH);
        return root;
    }

    private void initDesigner() {
        if (datas == null) {
            JOptionPane.showMessageDialog(this, "没有测量数据（SurveyDatas=null）");
            comCurve = new ComCurveDesigner();
            undoManager.BindDesigner(comCurve);
            return;
        }

        SurveyVector vec = datas.getM_SurveyImfos();
        int n = vec != null ? vec.size() : 0;
        if (n <= 0) {
            JOptionPane.showMessageDialog(this, "测量点数量为 0");
            comCurve = new ComCurveDesigner();
            undoManager.BindDesigner(comCurve);
            return;
        }

        comCurve = new ComCurveDesigner(vec, 0, n - 1);
        undoManager.BindDesigner(comCurve);

        plot.setClickHandler(this::onPlotClick);
    }

    private void refreshOriginCurvature() {
        if (datas == null) {
            miles = new double[0];
            curv = new double[0];
            return;
        }

        SurveyVector vec = datas.getM_SurveyImfos();
        int n = vec != null ? vec.size() : 0;
        if (n <= 0) {
            miles = new double[0];
            curv = new double[0];
            return;
        }

        miles = new double[n];
        curv = new double[n];
        for (int i = 0; i < n; i++) {
            SurveyImfo it = vec.get(i);
            miles[i] = it != null ? it.getM_mileage() : Double.NaN;
            curv[i] = Double.NaN;
        }

        try {
            // 和 C# 默认一致：150,1
            IntDoublePairVector pairs = datas.GetCurvation(150, 1);
            if (pairs != null) {
                for (int i = 0; i < pairs.size(); i++) {
                    IntDoublePair p = pairs.get(i);
                    if (p == null) continue;
                    int idx = p.getFirst();
                    if (idx >= 0 && idx < n) {
                        curv[idx] = p.getSecond();
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("GetCurvation 失败: " + t.getMessage());
        }
    }

    private void refreshPlotFromDesigner() {
        try {
            // 设计器内部数据可能变化（尤其是 margin），重新刷新
            SurveyVector vec = comCurve != null ? comCurve.GetSurveyDatas() : null;
            if (vec != null && datas != null) {
                datas.setM_SurveyImfos(vec);
            }
        } catch (Throwable ignored) {
        }

        refreshOriginCurvature();
        refreshMarginFromDesigner();
        refreshPlot();
    }

    private void refreshMarginFromDesigner() {
        SurveyVector vec = null;
        try {
            vec = comCurve != null ? comCurve.GetSurveyDatas() : null;
        } catch (Throwable ignored) {
        }

        if (vec == null || vec.size() <= 0) {
            marginMiles = new double[0];
            marginMm = new double[0];
            return;
        }

        int n = vec.size();
        marginMiles = new double[n];
        marginMm = new double[n];
        for (int i = 0; i < n; i++) {
            SurveyImfo it = vec.get(i);
            marginMiles[i] = it != null ? it.getM_mileage() : Double.NaN;
            marginMm[i] = it != null ? it.getM_H_margin() * 1000.0 : Double.NaN;
        }
    }

    private void refreshPlot() {
        plot.setSeries(miles, curv, keyPts, marginMiles, marginMm);
    }

    private void onPlotClick(double x, double y) {
        if (editType == 0) return;

        if (editType == 1) {
            editType = 0;
            if (keyPts.size() >= 6) return;

            double yy = interpolateCurvatureAtX(x);
            keyPts.add(new AL_Point3d(x, yy, 0));
            keyPts.sort(Comparator.comparingDouble(AL_Point3d::getX));
            refreshPlot();
            return;
        }

        if (editType == 2) {
            editType = 0;
            if (keyPts.isEmpty()) return;

            int idx = findKeyPtByX(x, 20);
            if (idx >= 0) {
                keyPts.remove(idx);
                refreshPlot();
            }
            return;
        }

        if (editType == 3) {
            if (moveFirstClick) {
                movePrePt = new AL_Point3d(x, y, 0);
                moveFirstClick = false;
                return;
            }

            editType = 0;
            moveFirstClick = true;
            int idx = findKeyPtByX(movePrePt.getX(), 20);
            if (idx >= 0) {
                double yy = interpolateCurvatureAtX(x);
                keyPts.set(idx, new AL_Point3d(x, yy, 0));
                keyPts.sort(Comparator.comparingDouble(AL_Point3d::getX));
                refreshPlot();
            }
        }
    }

    private int findKeyPtByX(double x, double tol) {
        for (int i = 0; i < keyPts.size(); i++) {
            if (Math.abs(keyPts.get(i).getX() - x) < tol) return i;
        }
        return -1;
    }

    private double interpolateCurvatureAtX(double x) {
        if (miles == null || curv == null || miles.length == 0) return 0;

        int n = Math.min(miles.length, curv.length);
        int best = -1;
        double bestDx = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(miles[i]) || !Double.isFinite(curv[i])) continue;
            double dx = Math.abs(miles[i] - x);
            if (dx < bestDx) {
                bestDx = dx;
                best = i;
            }
        }

        if (best >= 0) return curv[best];
        return 0;
    }

    private IntArr getKeyPtIndexs() {
        IntArr indexs = new IntArr();
        if (datas == null) return indexs;

        SurveyVector vec = datas.getM_SurveyImfos();
        if (vec == null || vec.size() <= 1) return indexs;

        int nIndex = 1;
        for (AL_Point3d kp : keyPts) {
            double keyX = kp.getX();
            for (int j = nIndex; j < vec.size(); j++) {
                SurveyImfo a = vec.get(j - 1);
                SurveyImfo b = vec.get(j);
                if (a == null || b == null) continue;
                if (a.getM_mileage() <= keyX && keyX < b.getM_mileage()) {
                    indexs.add(j);
                    nIndex = j;
                    break;
                }
            }
        }
        return indexs;
    }

    //自动进行复曲线分界点的分段
    private void onAutoSegment() {
        if (comCurve == null) return;
        try {
            IntArr keys = comCurve.AutoKeyPts();
            System.out.println("[ComCurveFitDialog] AutoKeyPts keys=" + intArrDebug(keys, 50));
            if (keys == null || keys.size() == 0) return;

            keyPts.clear();
            for (int i = 0; i < keys.size(); i++) {
                int idx = keys.get(i);
                double x = idx >= 0 && idx < miles.length ? miles[idx] : Double.NaN;
                double y = idx >= 0 && idx < curv.length ? curv[idx] : Double.NaN;

                if (!Double.isFinite(x)) {
                    SurveyVector vec = datas != null ? datas.getM_SurveyImfos() : null;
                    if (vec != null && idx >= 0 && idx < vec.size()) {
                        SurveyImfo it = vec.get(idx);
                        x = it != null ? it.getM_mileage() : Double.NaN;
                    }
                }

                if (!Double.isFinite(y)) {
                    y = interpolateCurvatureAtX(x);
                }

                if (Double.isFinite(x)) {
                    keyPts.add(new AL_Point3d(x, y, 0));
                }
            }

            keyPts.sort(Comparator.comparingDouble(AL_Point3d::getX));
            refreshPlot();
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "自动分段失败: " + t.getMessage());
        }
    }

    //自动初始化复曲线的分界点
    private void onInitKeyPtsOnly() {
        if (comCurve == null) return;
        undoManager.SaveSnapshot("初始化复曲线");
        IntArr idx = getKeyPtIndexs();
        if (!comCurve.InitKeyPts(idx)) {
            JOptionPane.showMessageDialog(this, "InitKeyPts 失败");
            return;
        }
        JOptionPane.showMessageDialog(this, "初始化成功!");
    }

    //根据分界点初始化复曲线
    private void onInitByKeyPts() {
        if (comCurve == null) return;
        undoManager.SaveSnapshot("复曲线初始化");

        IntArr idx = getKeyPtIndexs();
        if (!comCurve.StartComCurveFitting(idx)) {
            JOptionPane.showMessageDialog(this, "StartComCurveFitting 失败");
            return;
        }

        txtLen1.setText(String.valueOf(comCurve.GetComCurveLen(0)));
        txtLen2.setText(String.valueOf(comCurve.GetComCurveLen(1)));
        txtLen3.setText(String.valueOf(comCurve.GetComCurveLen(2)));

        refreshPlotFromDesigner();
        JOptionPane.showMessageDialog(this, "复曲线初始化完成!");
    }

    ///对已经计算的复曲线参数进行取整优化
    private void onRoundOptimize() {
        if (comCurve == null) return;
        undoManager.SaveSnapshot("复曲线最佳缓和曲线取整优化");
        if (!comCurve.ComCurveOpting(0, 0, 0, false)) {
            JOptionPane.showMessageDialog(this, "取整优化失败");
            return;
        }
        refreshPlotFromDesigner();
        JOptionPane.showMessageDialog(this, "取整优化完成!");
    }

    //根据输入的缓和曲线长度重新优化复曲线
    private void onManualOptimize() {
        if (comCurve == null) return;
        double d1 = parseDouble(txtLen1.getText());
        double d2 = parseDouble(txtLen2.getText());
        double d3 = parseDouble(txtLen3.getText());

        if (!Double.isFinite(d1) || !Double.isFinite(d2) || !Double.isFinite(d3)) {
            JOptionPane.showMessageDialog(this, "请输入有效的三段长度数字");
            return;
        }

        undoManager.SaveSnapshot("复曲线分段长度调整");
        if (!comCurve.ComCurveOpting(d1, d2, d3, true)) {
            JOptionPane.showMessageDialog(this, "优化失败");
            return;
        }

        refreshPlotFromDesigner();
        exportMarginAndInterPts();
    }


    //计算并显示偏差值
    private void onCalAndShowMarginWithExport() {
        if (comCurve == null) return;
        undoManager.SaveSnapshot("计算并显示偏差值");

        IntArr idx = getKeyPtIndexs();
        if (!comCurve.StartComCurveFitting(idx)) {
            JOptionPane.showMessageDialog(this, "StartComCurveFitting 失败");
            return;
        }

        refreshPlotFromDesigner();
        exportMarginAndInterPts();
    }

    private void exportMarginAndInterPts() {
        try {
            SurveyVector vec = comCurve.GetSurveyDatas();
            if (vec != null) {
                File file = chooseSaveFile("保存偏差值 (mileage,margin_mm)");
                if (file != null) {
                    writeMarginFile(file, vec);
                }
            }

            InterPtArr pts = comCurve.GetInterPts();
            if (pts != null) {
                File file = chooseSaveFile("保存交点信息");
                if (file != null) {
                    writeInterPtsFile(file, pts);
                    JOptionPane.showMessageDialog(this, "文件已成功保存到:\n" + file.getAbsolutePath());
                }
            }
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "导出失败: " + t.getMessage());
        }
    }

    private File chooseSaveFile(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        int res = chooser.showSaveDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return null;
        return chooser.getSelectedFile();
    }

    private void writeMarginFile(File file, SurveyVector vec) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (int i = 0; i < vec.size(); i++) {
                SurveyImfo it = vec.get(i);
                if (it == null) continue;
                w.write(it.getM_mileage() + "," + (it.getM_H_margin() * 1000.0));
                w.newLine();
            }
        }
    }

    private void writeInterPtsFile(File file, InterPtArr pts) throws Exception {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (int i = 0; i < pts.size(); i++) {
                ALIntersectionPoint it = pts.get(i);
                if (it == null) continue;

                AL_Point3d zh = it.getM_ZHpt();
                AL_Point3d hy = it.getM_HYpt();
                AL_Point3d yh = it.getM_YHpt();
                AL_Point3d hz = it.getM_HZpt();

                w.write(
                        safeX(zh) + "," + safeY(zh) + "," +
                        safeX(hy) + "," + safeY(hy) + "," +
                        safeX(yh) + "," + safeY(yh) + "," +
                        safeX(hz) + "," + safeY(hz) + "," +
                        it.getM_ZHml() + "," + it.getM_HYml() + "," + it.getM_YHml() + "," + it.getM_HZml() + "," +
                        it.getM_PreLen() + "," + it.getM_SubLen() + "," + it.getM_Radius()
                );
                w.newLine();
            }
        }
    }

    private static double safeX(AL_Point3d p) {
        return p != null ? p.getX() : Double.NaN;
    }

    private static double safeY(AL_Point3d p) {
        return p != null ? p.getY() : Double.NaN;
    }

    //通过输入的交点序号更新测量数据
    private void onFilterByInterIndex() {
        if (fittingData == null) return;

        int stIndex = parseIntOrDefault(txtStInter.getText(), 0);
        int edIndex = parseIntOrDefault(txtEdInter.getText(), -1);

        try {
            SurveyDatas fitDatas = fittingData.GetSurveyDatas();
            int max = (fitDatas != null && fitDatas.getM_SurveyImfos() != null) ? fitDatas.getM_SurveyImfos().size() - 1 : -1;
            if (edIndex < 0) edIndex = max;

            if (!fittingData.InitCurveDesigner(stIndex, edIndex)) {
                JOptionPane.showMessageDialog(this, "初始化失败!");
                return;
            }

            comCurve = fittingData.GetCurveDesigner();
            undoManager.BindDesigner(comCurve);

            // 用设计器返回的测点更新本窗口 datas
            SurveyVector newVec = comCurve.GetSurveyDatas();
            datas = new SurveyDatas(newVec, SURVEYTYPE.CENTER);

            keyPts.clear();
            marginMiles = new double[0];
            marginMm = new double[0];

            refreshOriginCurvature();
            refreshPlotFromDesigner();
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "筛选失败: " + t.getMessage());
        }
    }

    //将复曲线拟合结果并入整个曲线
    private void onMergeIntoFitting() {
        if (fittingData == null || alUndoManager == null) return;

        alUndoManager.SaveSnapshot("更新水平曲线到拟合数据");
        if (!fittingData.UpdataHoriLineByCurveDesigner()) {
            JOptionPane.showMessageDialog(this, "更新失败!");
            alUndoManager.Undo();
            alUndoManager.RemoveSnapshots(-1, -1);
        } else {
            JOptionPane.showMessageDialog(this, "更新成功!");
            alUndoManager.RemoveSnapshots(0, 0);
        }
    }

    private static double parseDouble(String s) {
        try {
            if (s == null) return Double.NaN;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    private static int parseIntOrDefault(String s, int def) {
        try {
            if (s == null) return def;
            String t = s.trim();
            if (t.isEmpty()) return def;
            return Integer.parseInt(t);
        } catch (Exception e) {
            return def;
        }
    }

    private static String intArrDebug(IntArr arr, int maxItems) {
        if (arr == null) return "null";
        int n;
        try {
            n = arr.size();
        } catch (Throwable t) {
            return "<size error: " + t.getMessage() + ">";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("size=").append(n).append(" [");
        int limit = Math.min(Math.max(maxItems, 0), n);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(',');
            try {
                sb.append(arr.get(i));
            } catch (Throwable t) {
                sb.append("<err>");
                break;
            }
        }
        if (n > limit) sb.append("...");
        sb.append(']');
        return sb.toString();
    }

    private static final class PlotPanel extends JPanel {
        private static final int PAD_L = 70;
        private static final int PAD_R = 30;
        private static final int PAD_T = 30;
        private static final int PAD_B = 55;

        private double[] x;
        private double[] y;
        private double[] marginX;
        private double[] marginY;
        private List<AL_Point3d> keyPts;

        private double xMin, xMax, yMin, yMax;
        private boolean boundsValid;

        private PlotClickHandler clickHandler;

        PlotPanel() {
            setBackground(Color.WHITE);
            this.x = new double[0];
            this.y = new double[0];
            this.marginX = new double[0];
            this.marginY = new double[0];
            this.keyPts = new ArrayList<>();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (clickHandler == null || !boundsValid) return;
                    Point p = e.getPoint();
                    double dx = screenToDataX(p.x);
                    double dy = screenToDataY(p.y);
                    clickHandler.onClick(dx, dy);
                    repaint();
                }
            });
        }

        void setClickHandler(PlotClickHandler handler) {
            this.clickHandler = handler;
        }

        void setSeries(double[] x, double[] y, List<AL_Point3d> keyPts, double[] marginX, double[] marginY) {
            this.x = x != null ? x : new double[0];
            this.y = y != null ? y : new double[0];
            this.keyPts = keyPts != null ? keyPts : new ArrayList<>();
            this.marginX = marginX != null ? marginX : new double[0];
            this.marginY = marginY != null ? marginY : new double[0];

            computeBounds();
            repaint();
        }

        private void computeBounds() {
            boundsValid = false;

            double xmin = Double.POSITIVE_INFINITY;
            double xmax = Double.NEGATIVE_INFINITY;
            double ymin = Double.POSITIVE_INFINITY;
            double ymax = Double.NEGATIVE_INFINITY;

            int n = Math.min(x.length, y.length);
            for (int i = 0; i < n; i++) {
                if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) continue;
                xmin = Math.min(xmin, x[i]);
                xmax = Math.max(xmax, x[i]);
                ymin = Math.min(ymin, y[i]);
                ymax = Math.max(ymax, y[i]);
            }

            // 如果没有曲率有效点，但有偏差点，则用偏差点 bounds
            if (!Double.isFinite(xmin)) {
                int m = Math.min(marginX.length, marginY.length);
                for (int i = 0; i < m; i++) {
                    if (!Double.isFinite(marginX[i]) || !Double.isFinite(marginY[i])) continue;
                    xmin = Math.min(xmin, marginX[i]);
                    xmax = Math.max(xmax, marginX[i]);
                    ymin = Math.min(ymin, marginY[i]);
                    ymax = Math.max(ymax, marginY[i]);
                }
            }

            if (!Double.isFinite(xmin) || !Double.isFinite(ymin)) return;

            if (Math.abs(xmax - xmin) < 1e-9) {
                xmax = xmin + 1;
                xmin = xmin - 1;
            }
            if (Math.abs(ymax - ymin) < 1e-9) {
                ymax = ymin + 1;
                ymin = ymin - 1;
            }

            // 让 keyPts/margin 也纳入 bounds，避免点画在边界外
            for (AL_Point3d p : keyPts) {
                if (p == null) continue;
                if (!Double.isFinite(p.getX()) || !Double.isFinite(p.getY())) continue;
                xmin = Math.min(xmin, p.getX());
                xmax = Math.max(xmax, p.getX());
                ymin = Math.min(ymin, p.getY());
                ymax = Math.max(ymax, p.getY());
            }

            int m = Math.min(marginX.length, marginY.length);
            for (int i = 0; i < m; i++) {
                if (!Double.isFinite(marginX[i]) || !Double.isFinite(marginY[i])) continue;
                xmin = Math.min(xmin, marginX[i]);
                xmax = Math.max(xmax, marginX[i]);
                ymin = Math.min(ymin, marginY[i]);
                ymax = Math.max(ymax, marginY[i]);
            }

            xMin = xmin;
            xMax = xmax;
            yMin = ymin;
            yMax = ymax;
            boundsValid = true;
        }

        private Rectangle plotRect() {
            int w = Math.max(10, getWidth() - PAD_L - PAD_R);
            int h = Math.max(10, getHeight() - PAD_T - PAD_B);
            return new Rectangle(PAD_L, PAD_T, w, h);
        }

        private int dataToScreenX(double dx) {
            Rectangle r = plotRect();
            double t = (dx - xMin) / (xMax - xMin);
            return r.x + (int) Math.round(t * r.width);
        }

        private int dataToScreenY(double dy) {
            Rectangle r = plotRect();
            double t = (dy - yMin) / (yMax - yMin);
            return r.y + r.height - (int) Math.round(t * r.height);
        }

        private double screenToDataX(int sx) {
            Rectangle r = plotRect();
            double t = (sx - r.x) / (double) r.width;
            return xMin + t * (xMax - xMin);
        }

        private double screenToDataY(int sy) {
            Rectangle r = plotRect();
            double t = (r.y + r.height - sy) / (double) r.height;
            return yMin + t * (yMax - yMin);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Rectangle r = plotRect();
            g2.setColor(new Color(245, 245, 245));
            g2.fillRect(r.x, r.y, r.width, r.height);
            g2.setColor(Color.BLACK);
            g2.drawRect(r.x, r.y, r.width, r.height);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g2.drawString("复曲线设计", r.x + 10, r.y - 8 + 20);

            if (!boundsValid) return;

            drawAxes(g2, r);
            drawCurvatureLine(g2);
            drawKeyPts(g2);
            drawMarginPts(g2);
        }

        private void drawAxes(Graphics2D g2, Rectangle r) {
            g2.setColor(new Color(220, 220, 220));
            int ticks = 6;
            for (int i = 0; i <= ticks; i++) {
                double t = i / (double) ticks;
                int px = r.x + (int) Math.round(t * r.width);
                int py = r.y + r.height - (int) Math.round(t * r.height);
                g2.drawLine(px, r.y, px, r.y + r.height);
                g2.drawLine(r.x, py, r.x + r.width, py);
            }

            g2.setColor(Color.DARK_GRAY);
            for (int i = 0; i <= ticks; i++) {
                double t = i / (double) ticks;
                double xv = xMin + t * (xMax - xMin);
                double yv = yMin + t * (yMax - yMin);
                int px = r.x + (int) Math.round(t * r.width);
                int py = r.y + r.height - (int) Math.round(t * r.height);

                String xs = String.format("%.0f", xv);
                String ys = String.format("%.3f", yv);

                g2.drawString(xs, px - 18, r.y + r.height + 22);
                g2.drawString(ys, r.x - 62, py + 5);
            }

            g2.setColor(Color.BLACK);
            g2.drawString("实测里程", r.x + r.width / 2 - 30, r.y + r.height + 45);
            g2.drawString("曲率 / 偏差", 10, r.y + 15);
        }

        private void drawCurvatureLine(Graphics2D g2) {
            int n = Math.min(x.length, y.length);
            if (n < 2) return;

            g2.setColor(new Color(30, 90, 200));
            g2.setStroke(new BasicStroke(2f));

            boolean hasLast = false;
            int lastX = 0;
            int lastY = 0;

            for (int i = 0; i < n; i++) {
                double dx = x[i];
                double dy = y[i];
                if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
                    hasLast = false;
                    continue;
                }

                int px = dataToScreenX(dx);
                int py = dataToScreenY(dy);
                if (hasLast) g2.drawLine(lastX, lastY, px, py);

                lastX = px;
                lastY = py;
                hasLast = true;
            }
        }

        private void drawKeyPts(Graphics2D g2) {
            if (keyPts == null || keyPts.isEmpty()) return;

            g2.setColor(new Color(20, 20, 20));
            for (AL_Point3d p : keyPts) {
                if (p == null) continue;
                if (!Double.isFinite(p.getX()) || !Double.isFinite(p.getY())) continue;
                int px = dataToScreenX(p.getX());
                int py = dataToScreenY(p.getY());
                g2.fillOval(px - 5, py - 5, 10, 10);
            }
        }

        private void drawMarginPts(Graphics2D g2) {
            int n = Math.min(marginX.length, marginY.length);
            if (n <= 0) return;

            g2.setColor(new Color(200, 40, 40));
            for (int i = 0; i < n; i++) {
                double dx = marginX[i];
                double dy = marginY[i];
                if (!Double.isFinite(dx) || !Double.isFinite(dy)) continue;
                int px = dataToScreenX(dx);
                int py = dataToScreenY(dy);
                g2.fillOval(px - 3, py - 3, 6, 6);
            }
        }

        interface PlotClickHandler {
            void onClick(double x, double y);
        }
    }

}
