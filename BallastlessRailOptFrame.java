import AlBallastlessRailOpter.*;
import AlignmentCore.ALChain;
import AlignmentCore.ChainArr;
import AlignmentCore.DoubleDoublePairVector;
import AlignmentCore.TQICal;
import AlignmentCore.TQIValue;
import AlignmentCore.TQIValueVector;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;

public class BallastlessRailOptFrame extends JFrame {

    // ==================== 数据 ====================
    private RtSurveyPtArr surveyData;
    private OptConstrain  constrainH;      // 平面调整量优化参数
    private OptConstrain  constrainV;      // 高程调整量优化参数

    private RailAPI       horiApi;
    private RailAPI       vertApi;

    private OptValArr     marginOpt;       // 平面调整量
    private OptValArr     heightOpt;       // 高程调整量

    // 原始偏差数据（用于绘图）
    private DoubleArr     originL_H, originR_H;   // 原始平面偏差
    private DoubleArr     originL_V, originR_V;   // 原始高程偏差
    private DoubleArr     afterL_H,  afterR_H;    // 优化后平面偏差（原始+调整量）
    private DoubleArr     afterL_V,  afterR_V;    // 优化后高程偏差

    // 弦测值
    private DoubleArr chord10L_H, chord10R_H, chord10L_V, chord10R_V;
    private DoubleArr chord30L_H, chord30R_H, chord30L_V, chord30R_V;
    private DoubleArr chord300L_H,chord300R_H,chord300L_V,chord300R_V;
    private DoubleArr chord10L_H_opt, chord10R_H_opt, chord10L_V_opt, chord10R_V_opt;
    private DoubleArr chord30L_H_opt, chord30R_H_opt, chord30L_V_opt, chord30R_V_opt;
    private DoubleArr chord300L_H_opt,chord300R_H_opt,chord300L_V_opt,chord300R_V_opt;

    // ==================== UI ====================
    private JTextArea     logArea;
    private JLabel        statusLabel;
    private DeviationChartPanel chartPanel;

    private JButton btnLoad, btnOptHori, btnOptVert;
    private JButton btnOptHoriLong, btnOptVertLong;
    private JButton btnExport, btnExportWave;
    private JButton btnChordH, btnChordV;
    private JButton btnTQI;

    // --- 约束参数输入 (H / V 共用一部分) ---
    private JTextField tfSW10, tfSWLength, tfLW30, tfLW300;
    private JTextField tfGaugeL, tfGaugeRate;
    private JTextField tfMarginL, tfTwistL;
    private JTextField tfRegFactor, tfMinFactor;
    // 平面专用
    private JTextField tfH_UnitL_l, tfH_UnitL_u, tfH_UnitR_l, tfH_UnitR_u;
    private JCheckBox  cbH_IsDifferential, cbH_LongWave, cbH_IsFixed;
    private JTextField tfH_ClassVal;
    // 高程专用
    private JTextField tfV_UnitL_l, tfV_UnitL_u, tfV_UnitR_l, tfV_UnitR_u;
    private JCheckBox  cbV_IsDifferential, cbV_LongWave, cbV_IsFixed;
    private JTextField tfV_ClassVal;

    // --- 图表控制 ---
    private JComboBox<String> chartModeCombo;
    private JCheckBox  cbShowBefore, cbShowAfter, cbShowLeft, cbShowRight;

    // ==================== 构造 ====================
    public BallastlessRailOptFrame() {
        super("\u65E0\u782F\u8F68\u7CBE\u8C03\u6D4B\u8BD5\u6A21\u5757");
        initUI();
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    // ==================== UI 初始化 ====================
    private void initUI() {
        setLayout(new BorderLayout(4, 4));

        // 先初始化约束变量（含默认值）
        initDefaultConstrain();

        // ---- 工具栏 ----
        add(createToolbar(), BorderLayout.NORTH);

        // ---- 左侧约束面板 ----
        add(createConstrainPanel(), BorderLayout.WEST);

        // 创建完 UI 控件后，从变量提取数值填充到各字段
        loadConstrainToUI();

        // ---- 中央图表 ----
        chartPanel = new DeviationChartPanel();
        add(chartPanel, BorderLayout.CENTER);

        // ---- 底部日志 ----
        JPanel bottom = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(logArea);
        sp.setPreferredSize(new Dimension(0, 150));
        bottom.add(sp, BorderLayout.CENTER);
        statusLabel = new JLabel(" ");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bottom.add(statusLabel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        setOptButtonsEnabled(false);
    }

    /** 初始化约束变量默认值 */
    private void initDefaultConstrain() {
        constrainH = new OptConstrain();
        constrainH.setSW_10(1.0);
        constrainH.setSW_Length(10.0);
        constrainH.setLW_30(2.0);
        constrainH.setLW_300(5.0);
        constrainH.setGauge_Up(WIDTH);
        constrainH.setGauge_Down(-0.5);
        constrainH.setMarginL(0.5);
        constrainH.setTwistL(1.0);
        constrainH.setRegressionFactor(1e-12);
        constrainH.setMinFactor(1e-6);
        constrainH.setUnitL_l(-4.0);
        constrainH.setUnitL_u(4.0);
        constrainH.setUnitR_l(-4.0);
        constrainH.setUnitR_u(4.0);
        constrainH.setIsDifferential(true);
        constrainH.setBResultLongWave(true);
        constrainH.setIsFixed(true);

        constrainV = new OptConstrain();
        constrainV.setSW_10(1.0);
        constrainV.setSW_Length(10.0);
        constrainV.setLW_30(2.0);
        constrainV.setLW_300(5.0);
        constrainV.setGauge_Up(0.25);
        constrainV.setGauge_Down(0);
        constrainV.setMarginL(0.25);
        constrainV.setTwistL(1.0);
        constrainV.setRegressionFactor(1e-12);
        constrainV.setMinFactor(1e-6);
        constrainV.setUnitL_l(-2.0);
        constrainV.setUnitL_u(8.0);
        constrainV.setUnitR_l(-2.0);
        constrainV.setUnitR_u(8.0);
        constrainV.setIsDifferential(true);
        constrainV.setBResultLongWave(true);
        constrainV.setIsFixed(true);
    }

    private JPanel createToolbar() {
        JPanel tb = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 3));

        btnLoad = new JButton("1. \u52A0\u8F7D\u6570\u636E");
        btnOptHori = new JButton("2. \u5E73\u9762\u4F18\u5316");
        btnOptVert = new JButton("3. \u9AD8\u7A0B\u4F18\u5316");
        btnOptHoriLong = new JButton("\u5E73\u9762\u957F\u6CE2");
        btnOptVertLong = new JButton("\u9AD8\u7A0B\u957F\u6CE2");
        btnExport = new JButton("\u5BFC\u51FA\u8C03\u6574\u91CF");
        btnExportWave = new JButton("\u5BFC\u51FA\u6CE2\u6570\u636E");

        tb.add(btnLoad);
        tb.add(btnOptHori);
        tb.add(btnOptVert);
        tb.add(btnOptHoriLong);
        tb.add(btnOptVertLong);
        tb.add(btnExport);
        tb.add(btnExportWave);

        btnChordH = new JButton("\u5F26\u6D4B\u503C(H)");
        btnChordV = new JButton("\u5F26\u6D4B\u503C(V)");
        tb.add(btnChordH);
        tb.add(btnChordV);

        btnTQI = new JButton("\u8BA1\u7B97TQI");
        tb.add(btnTQI);

        // --- 图表模式切换 ---
        tb.add(new JLabel("  \u56FE\u8868:"));
        chartModeCombo = new JComboBox<>(new String[]{
            "\u5E73\u9762\u504F\u5DEE", "\u9AD8\u7A0B\u504F\u5DEE",
            "10m\u5F26(\u5E73\u9762)", "30m\u5F26(\u5E73\u9762)", "300m\u5F26(\u5E73\u9762)",
            "10m\u5F26(\u9AD8\u7A0B)", "30m\u5F26(\u9AD8\u7A0B)", "300m\u5F26(\u9AD8\u7A0B)"
        });
        chartModeCombo.addActionListener(e -> updateChart());
        tb.add(chartModeCombo);

        cbShowBefore = new JCheckBox("\u4F18\u5316\u524D", true);
        cbShowAfter  = new JCheckBox("\u4F18\u5316\u540E", true);
        cbShowLeft   = new JCheckBox("\u5DE6\u8F68", true);
        cbShowRight  = new JCheckBox("\u53F3\u8F68", true);
        ActionListener refresh = e -> updateChart();
        cbShowBefore.addActionListener(refresh);
        cbShowAfter.addActionListener(refresh);
        cbShowLeft.addActionListener(refresh);
        cbShowRight.addActionListener(refresh);
        tb.add(cbShowBefore);
        tb.add(cbShowAfter);
        tb.add(cbShowLeft);
        tb.add(cbShowRight);

        btnLoad.addActionListener(e -> handleLoadData());
        btnOptHori.addActionListener(e -> handleOptHori());
        btnOptVert.addActionListener(e -> handleOptVert());
        btnOptHoriLong.addActionListener(e -> handleOptHoriLong());
        btnOptVertLong.addActionListener(e -> handleOptVertLong());
        btnExport.addActionListener(e -> handleExport());
        btnExportWave.addActionListener(e -> handleExportWave());
        btnChordH.addActionListener(e -> handleChordCalc(true));
        btnChordV.addActionListener(e -> handleChordCalc(false));
        btnTQI.addActionListener(e -> handleTQICalc());

        return tb;
    }

    private JPanel createConstrainPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("\u7EA6\u675F\u53C2\u6570"));
        panel.setPreferredSize(new Dimension(280, 0));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("\u5171\u7528\u53C2\u6570", createCommonParamPanel());
        tabs.addTab("\u5E73\u9762(H)", createHParamPanel());
        tabs.addTab("\u9AD8\u7A0B(V)", createVParamPanel());
        panel.add(tabs, BorderLayout.CENTER);

        // 应用参数按钮
        JButton btnApply = new JButton("\u5E94\u7528\u53C2\u6570");
        btnApply.addActionListener(e -> applyConstrainFromUI());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(btnApply);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ---- 通用参数 ----
    private JPanel createCommonParamPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 4, 2, 4);
        c.gridwidth = 1;

        tfSW10     = addField(p, c, 0, "\u77ED\u6CE2 10m:");
        tfSWLength = addField(p, c, 1, "\u77ED\u6CE2\u57FA\u957F:");
        tfLW30     = addField(p, c, 2, "\u957F\u6CE2 30m:");
        tfLW300    = addField(p, c, 3, "\u957F\u6CE2 300m:");
        tfGaugeL   = addField(p, c, 4, "\u8F68\u8DDD\u4E0B\u9650:");
        tfGaugeRate = addField(p, c, 5, "\u8F68\u8DDD\u53D8\u5316\u7387:");
        tfMarginL  = addField(p, c, 6, "\u6C34\u5E73\u504F\u5DEE:");
        tfTwistL   = addField(p, c, 7, "\u626D\u66F2:");
        tfRegFactor = addField(p, c, 8, "\u56DE\u5F52\u56E0\u5B50:");
        tfMinFactor = addField(p, c, 9, "\u6700\u5C0F\u56E0\u5B50:");

        return p;
    }

    // ---- 平面(H) 专用参数 ----
    private JPanel createHParamPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 4, 2, 4);
        c.gridwidth = 1;

        tfH_UnitL_l = addField(p, c, 0, "\u5DE6\u8F68\u4E0B\u9650:");
        tfH_UnitL_u = addField(p, c, 1, "\u5DE6\u8F68\u4E0A\u9650:");
        tfH_UnitR_l = addField(p, c, 2, "\u53F3\u8F68\u4E0B\u9650:");
        tfH_UnitR_u = addField(p, c, 3, "\u53F3\u8F68\u4E0A\u9650:");
        cbH_IsDifferential = addCheck(p, c, 4, "\u5206\u7EA7\u53D6\u6574");
        cbH_LongWave = addCheck(p, c, 5, "\u8003\u8651\u957F\u6CE2");
        cbH_IsFixed  = addCheck(p, c, 6, "\u56FA\u5B9A\u8FB9\u754C");
        tfH_ClassVal = addField(p, c, 7, "\u7EA7\u5DEE:");
        return p;
    }

    // ---- 高程(V) 专用参数 ----
    private JPanel createVParamPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 4, 2, 4);
        c.gridwidth = 1;

        tfV_UnitL_l = addField(p, c, 0, "\u5DE6\u8F68\u4E0B\u9650:");
        tfV_UnitL_u = addField(p, c, 1, "\u5DE6\u8F68\u4E0A\u9650:");
        tfV_UnitR_l = addField(p, c, 2, "\u53F3\u8F68\u4E0B\u9650:");
        tfV_UnitR_u = addField(p, c, 3, "\u53F3\u8F68\u4E0A\u9650:");
        cbV_IsDifferential = addCheck(p, c, 4, "\u5206\u7EA7\u53D6\u6574");
        cbV_LongWave = addCheck(p, c, 5, "\u8003\u8651\u957F\u6CE2");
        cbV_IsFixed  = addCheck(p, c, 6, "\u56FA\u5B9A\u8FB9\u754C");
        tfV_ClassVal = addField(p, c, 7, "\u7EA7\u5DEE:");
        return p;
    }

    // ---- 工具方法：添加字段（不再硬编码默认值，由 loadConstrainToUI 统一填充） ----
    private JTextField addField(JPanel panel, GridBagConstraints c,
                                 int row, String label) {
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 1;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        panel.add(lbl, c);
        c.gridx = 1;
        JTextField tf = new JTextField(8);
        tf.setHorizontalAlignment(JTextField.RIGHT);
        panel.add(tf, c);
        return tf;
    }

    private JCheckBox addCheck(JPanel panel, GridBagConstraints c,
                                int row, String label) {
        c.gridy = row;
        c.gridx = 0;
        c.gridwidth = 2;
        JCheckBox cb = new JCheckBox(label);
        cb.setFont(new Font("SansSerif", Font.PLAIN, 11));
        panel.add(cb, c);
        return cb;
    }

    // ==================== 变量 <-> UI 双向同步 ====================

    /** 从变量 constrainH / constrainV 提取数值，填充到 UI 各字段 */
    private void loadConstrainToUI() {
        // 通用参数（从 constrainH 提取，二者共用同一套默认值）
        tfSW10.setText(String.valueOf(constrainH.getSW_10()));
        tfSWLength.setText(String.valueOf(constrainH.getSW_Length()));
        tfLW30.setText(String.valueOf(constrainH.getLW_30()));
        tfLW300.setText(String.valueOf(constrainH.getLW_300()));
        tfGaugeL.setText(String.valueOf(constrainH.getGauge_Up()));
        tfGaugeRate.setText(String.valueOf(constrainH.getGauge_Down()));
        tfMarginL.setText(String.valueOf(constrainH.getMarginL()));
        tfTwistL.setText(String.valueOf(constrainH.getTwistL()));
        tfRegFactor.setText(String.valueOf(constrainH.getRegressionFactor()));
        tfMinFactor.setText(String.valueOf(constrainH.getMinFactor()));

        // 平面(H) 专用
        tfH_UnitL_l.setText(String.valueOf(constrainH.getUnitL_l()));
        tfH_UnitL_u.setText(String.valueOf(constrainH.getUnitL_u()));
        tfH_UnitR_l.setText(String.valueOf(constrainH.getUnitR_l()));
        tfH_UnitR_u.setText(String.valueOf(constrainH.getUnitR_u()));
        cbH_IsDifferential.setSelected(constrainH.getIsDifferential());
        cbH_LongWave.setSelected(constrainH.getBResultLongWave());
        cbH_IsFixed.setSelected(constrainH.getIsFixed());
        tfH_ClassVal.setText("0.5");  // 级差不在 OptConstrain 中，保留固定

        // 高程(V) 专用
        tfV_UnitL_l.setText(String.valueOf(constrainV.getUnitL_l()));
        tfV_UnitL_u.setText(String.valueOf(constrainV.getUnitL_u()));
        tfV_UnitR_l.setText(String.valueOf(constrainV.getUnitR_l()));
        tfV_UnitR_u.setText(String.valueOf(constrainV.getUnitR_u()));
        cbV_IsDifferential.setSelected(constrainV.getIsDifferential());
        cbV_LongWave.setSelected(constrainV.getBResultLongWave());
        cbV_IsFixed.setSelected(constrainV.getIsFixed());
        tfV_ClassVal.setText("0.5");  // 级差不在 OptConstrain 中，保留固定
    }

    /** 从 UI 各字段读取数值，写回变量 constrainH / constrainV */
    private void applyConstrainFromUI() {
        writeConstrainToVar(true);
        writeConstrainToVar(false);
        log(">>> \u53C2\u6570\u5DF2\u5E94\u7528");
    }

    private void writeConstrainToVar(boolean isH) {
        OptConstrain c = isH ? constrainH : constrainV;
        c.setSW_10(parseDouble(tfSW10.getText(), c.getSW_10()));
        c.setSW_Length(parseDouble(tfSWLength.getText(), c.getSW_Length()));
        c.setLW_30(parseDouble(tfLW30.getText(), c.getLW_30()));
        c.setLW_300(parseDouble(tfLW300.getText(), c.getLW_300()));
        c.setGauge_Up(parseDouble(tfGaugeL.getText(), c.getGauge_Up()));
        c.setGauge_Down(parseDouble(tfGaugeRate.getText(), c.getGauge_Down()));
        c.setMarginL(parseDouble(tfMarginL.getText(), c.getMarginL()));
        c.setTwistL(parseDouble(tfTwistL.getText(), c.getTwistL()));
        c.setRegressionFactor(parseDouble(tfRegFactor.getText(), c.getRegressionFactor()));
        c.setMinFactor(parseDouble(tfMinFactor.getText(), c.getMinFactor()));

        if (isH) {
            c.setUnitL_l(parseDouble(tfH_UnitL_l.getText(), c.getUnitL_l()));
            c.setUnitL_u(parseDouble(tfH_UnitL_u.getText(), c.getUnitL_u()));
            c.setUnitR_l(parseDouble(tfH_UnitR_l.getText(), c.getUnitR_l()));
            c.setUnitR_u(parseDouble(tfH_UnitR_u.getText(), c.getUnitR_u()));
            c.setIsDifferential(cbH_IsDifferential.isSelected());
            c.setBResultLongWave(cbH_LongWave.isSelected());
            c.setIsFixed(cbH_IsFixed.isSelected());
        } else {
            c.setUnitL_l(parseDouble(tfV_UnitL_l.getText(), c.getUnitL_l()));
            c.setUnitL_u(parseDouble(tfV_UnitL_u.getText(), c.getUnitL_u()));
            c.setUnitR_l(parseDouble(tfV_UnitR_l.getText(), c.getUnitR_l()));
            c.setUnitR_u(parseDouble(tfV_UnitR_u.getText(), c.getUnitR_u()));
            c.setIsDifferential(cbV_IsDifferential.isSelected());
            c.setBResultLongWave(cbV_LongWave.isSelected());
            c.setIsFixed(cbV_IsFixed.isSelected());
        }
    }

    private double parseDouble(String text, double def) {
        try { return Double.parseDouble(text.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    // ==================== 数据加载 ====================
    private void handleLoadData() {
        String filePath = openFileDialog();
        if (filePath == null) return;

        log(">>> \u52A0\u8F7D\u6570\u636E: " + filePath);
        try {
            ArrayList<RailTrackSurveyPoint> points = readCsv(filePath);
            if (points.isEmpty()) {
                log("!!! \u672A\u8BFB\u53D6\u5230\u6709\u6548\u6570\u636E");
                JOptionPane.showMessageDialog(this, "\u672A\u8BFB\u53D6\u5230\u6709\u6548\u6570\u636E",
                    "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
                return;
            }

            surveyData = new RtSurveyPtArr(points);
            log(">>> \u6210\u529F\u52A0\u8F7D " + surveyData.size() + " \u4E2A\u6D4B\u70B9");

            // 保存原始偏差
            originL_H = new DoubleArr();
            originR_H = new DoubleArr();
            originL_V = new DoubleArr();
            originR_V = new DoubleArr();
            for (int i = 0; i < surveyData.size(); i++) {
                RailTrackSurveyPoint pt = surveyData.get(i);
                originL_H.add(pt.getX_L());
                originR_H.add(pt.getX_R());
                originL_V.add(pt.getY_L());
                originR_V.add(pt.getY_R());
            }

            // 应用参数
            applyConstrainFromUI();
            log(">>> \u7EA6\u675F\u53C2\u6570\u5DF2\u5E94\u7528");

            setOptButtonsEnabled(true);
            statusLabel.setText("\u5DF2\u52A0\u8F7D " + surveyData.size() + " \u4E2A\u6D4B\u70B9");
            updateChart();
        } catch (Exception ex) {
            log("!!! \u52A0\u8F7D\u5931\u8D25: " + ex.getMessage());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "\u52A0\u8F7D\u5931\u8D25: " + ex.getMessage(),
                "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==================== 平面优化 ====================
    private void handleOptHori() {
        if (surveyData == null) return;
        log(">>> \u5E73\u9762\u4F18\u5316...");

        // 设置每点约束
        for (int i = 0; i < surveyData.size(); i++) {
            surveyData.get(i).setX_L_Con_l(constrainH.getUnitL_l());
            surveyData.get(i).setX_L_Con_u(constrainH.getUnitL_u());
            surveyData.get(i).setX_R_Con_l(constrainH.getUnitR_l());
            surveyData.get(i).setX_R_Con_u(constrainH.getUnitR_u());
        }

        horiApi = new RailAPI(surveyData, constrainH);
        boolean ok;
        if (surveyData.size() > 11000 && constrainH.getBResultLongWave())
            ok = horiApi.StartOptHoriForLong();
        else
            ok = horiApi.StartOptHori();

        if (!ok) {
            log("!!! \u5E73\u9762\u4F18\u5316\u5931\u8D25");
            JOptionPane.showMessageDialog(this, "\u5E73\u9762\u4F18\u5316\u5931\u8D25",
                "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        marginOpt = horiApi.GetResult();

        // 分级取整
        double classVal = parseDouble(tfH_ClassVal.getText(), 0.5);
        if (constrainH.getIsDifferential()) {
            RailAPI._RoundForResultHCompare(originL_H, originR_H, marginOpt,
                constrainH.getGauge_Up(), constrainH.getGauge_Down(), classVal);
        }

        // 计算优化后偏差 = 原始 + 调整量
        afterL_H = new DoubleArr();
        afterR_H = new DoubleArr();
        for (int i = 0; i < surveyData.size(); i++) {
            afterL_H.add(surveyData.get(i).getX_L() + marginOpt.get(i).getXOpt_L());
            afterR_H.add(surveyData.get(i).getX_R() + marginOpt.get(i).getXOpt_R());
        }

        // 计算弦测值
        computeAllChords(true);
        log(">>> \u5E73\u9762\u4F18\u5316\u5B8C\u6210!");
        statusLabel.setText("\u5E73\u9762\u4F18\u5316\u5B8C\u6210");
        updateChart();
    }

    // ==================== 高程优化 ====================
    private void handleOptVert() {
        if (surveyData == null) return;
        log(">>> \u9AD8\u7A0B\u4F18\u5316...");

        for (int i = 0; i < surveyData.size(); i++) {
            surveyData.get(i).setY_L_Con_l(constrainV.getUnitL_l());
            surveyData.get(i).setY_L_Con_u(constrainV.getUnitL_u());
            surveyData.get(i).setY_R_Con_l(constrainV.getUnitR_l());
            surveyData.get(i).setY_R_Con_u(constrainV.getUnitR_u());
        }

        vertApi = new RailAPI(surveyData, constrainV);
        boolean ok;
        if (surveyData.size() > 11000 && constrainV.getBResultLongWave())
            ok = vertApi.StartOptVertForLong();
        else
            ok = vertApi.StartOptVert();

        if (!ok) {
            log("!!! \u9AD8\u7A0B\u4F18\u5316\u5931\u8D25");
            JOptionPane.showMessageDialog(this, "\u9AD8\u7A0B\u4F18\u5316\u5931\u8D25",
                "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        heightOpt = vertApi.GetResult();

        double classVal = parseDouble(tfV_ClassVal.getText(), 0.5);
        if (constrainV.getIsDifferential()) {
            RailAPI._RoundForResultVCompare(originL_V, originR_V, heightOpt,
                constrainV.getMarginL(), -constrainV.getMarginL(), classVal);
        }

        afterL_V = new DoubleArr();
        afterR_V = new DoubleArr();
        for (int i = 0; i < surveyData.size(); i++) {
            afterL_V.add(surveyData.get(i).getY_L() + heightOpt.get(i).getYOpt_L());
            afterR_V.add(surveyData.get(i).getY_R() + heightOpt.get(i).getYOpt_R());
        }

        computeAllChords(false);
        log(">>> \u9AD8\u7A0B\u4F18\u5316\u5B8C\u6210!");
        statusLabel.setText("\u9AD8\u7A0B\u4F18\u5316\u5B8C\u6210");
        updateChart();
    }

    private void handleOptHoriLong() {
        constrainH.setBResultLongWave(true);
        handleOptHori();
        constrainH.setBResultLongWave(cbH_LongWave.isSelected());
    }

    private void handleOptVertLong() {
        constrainV.setBResultLongWave(true);
        handleOptVert();
        constrainV.setBResultLongWave(cbV_LongWave.isSelected());
    }

    // ==================== 弦测值计算 ====================
    private void computeAllChords(boolean isH) {
        DoubleArr beforeL = isH ? originL_H : originL_V;
        DoubleArr beforeR = isH ? originR_H : originR_V;
        DoubleArr afterL  = isH ? afterL_H  : afterL_V;
        DoubleArr afterR  = isH ? afterR_H  : afterR_V;

        if (isH) {
            chord10L_H  = new DoubleArr(); chord10R_H  = new DoubleArr();
            chord30L_H  = new DoubleArr(); chord30R_H  = new DoubleArr();
            chord300L_H = new DoubleArr(); chord300R_H = new DoubleArr();
            chord10L_H_opt = new DoubleArr(); chord10R_H_opt = new DoubleArr();
            chord30L_H_opt = new DoubleArr(); chord30R_H_opt = new DoubleArr();
            chord300L_H_opt = new DoubleArr(); chord300R_H_opt = new DoubleArr();

            RailAPI.ChordLineFor10(beforeL, chord10L_H);
            RailAPI.ChordLineFor10(beforeR, chord10R_H);
            RailAPI.ChordLineFor30(beforeL, chord30L_H);
            RailAPI.ChordLineFor30(beforeR, chord30R_H);
            RailAPI.ChordLineFor300(beforeL, chord300L_H);
            RailAPI.ChordLineFor300(beforeR, chord300R_H);

            if (afterL != null && afterL.size() > 0) {
                RailAPI.ChordLineFor10(afterL, chord10L_H_opt);
                RailAPI.ChordLineFor10(afterR, chord10R_H_opt);
                RailAPI.ChordLineFor30(afterL, chord30L_H_opt);
                RailAPI.ChordLineFor30(afterR, chord30R_H_opt);
                RailAPI.ChordLineFor300(afterL, chord300L_H_opt);
                RailAPI.ChordLineFor300(afterR, chord300R_H_opt);
            }
        } else {
            chord10L_V  = new DoubleArr(); chord10R_V  = new DoubleArr();
            chord30L_V  = new DoubleArr(); chord30R_V  = new DoubleArr();
            chord300L_V = new DoubleArr(); chord300R_V = new DoubleArr();
            chord10L_V_opt = new DoubleArr(); chord10R_V_opt = new DoubleArr();
            chord30L_V_opt = new DoubleArr(); chord30R_V_opt = new DoubleArr();
            chord300L_V_opt = new DoubleArr(); chord300R_V_opt = new DoubleArr();

            RailAPI.ChordLineFor10(beforeL, chord10L_V);
            RailAPI.ChordLineFor10(beforeR, chord10R_V);
            RailAPI.ChordLineFor30(beforeL, chord30L_V);
            RailAPI.ChordLineFor30(beforeR, chord30R_V);
            RailAPI.ChordLineFor300(beforeL, chord300L_V);
            RailAPI.ChordLineFor300(beforeR, chord300R_V);

            if (afterL != null && afterL.size() > 0) {
                RailAPI.ChordLineFor10(afterL, chord10L_V_opt);
                RailAPI.ChordLineFor10(afterR, chord10R_V_opt);
                RailAPI.ChordLineFor30(afterL, chord30L_V_opt);
                RailAPI.ChordLineFor30(afterR, chord30R_V_opt);
                RailAPI.ChordLineFor300(afterL, chord300L_V_opt);
                RailAPI.ChordLineFor300(afterR, chord300R_V_opt);
            }
        }
    }

    // ==================== 弦测值显式计算与打印 ====================

    /** 按钮「弦测值(H)」/「弦测值(V)」的响应 — 计算并打印优化前后弦测值对比 */
    private void handleChordCalc(boolean isH) {
        String label = isH ? "\u5E73\u9762" : "\u9AD8\u7A0B";
        DoubleArr beforeL = isH ? originL_H : originL_V;
        DoubleArr beforeR = isH ? originR_H : originR_V;
        DoubleArr afterL  = isH ? afterL_H  : afterL_V;
        DoubleArr afterR  = isH ? afterR_H  : afterR_V;

        if (beforeL == null || beforeL.size() == 0) {
            log("!!! \u8BF7\u5148\u52A0\u8F7D\u6570\u636E");
            return;
        }
        if (afterL == null || afterL.size() == 0) {
            log("!!! \u8BF7\u5148\u6267\u884C\u4F18\u5316");
            return;
        }

        // 每次点击都重新计算，确保优化后弦测值与原始弦测值一一对应
        log(">>> \u8BA1\u7B97" + label + "\u5F26\u6D4B\u503C...");
        computeAllChords(isH);

        // 选择对应的弦测值数组
        DoubleArr c10L_bef, c10R_bef, c30L_bef, c30R_bef, c300L_bef, c300R_bef;
        DoubleArr c10L_aft, c10R_aft, c30L_aft, c30R_aft, c300L_aft, c300R_aft;
        if (isH) {
            c10L_bef=chord10L_H;  c10R_bef=chord10R_H;
            c30L_bef=chord30L_H;  c30R_bef=chord30R_H;
            c300L_bef=chord300L_H; c300R_bef=chord300R_H;
            c10L_aft=chord10L_H_opt; c10R_aft=chord10R_H_opt;
            c30L_aft=chord30L_H_opt; c30R_aft=chord30R_H_opt;
            c300L_aft=chord300L_H_opt; c300R_aft=chord300R_H_opt;
        } else {
            c10L_bef=chord10L_V;  c10R_bef=chord10R_V;
            c30L_bef=chord30L_V;  c30R_bef=chord30R_V;
            c300L_bef=chord300L_V; c300R_bef=chord300R_V;
            c10L_aft=chord10L_V_opt; c10R_aft=chord10R_V_opt;
            c30L_aft=chord30L_V_opt; c30R_aft=chord30R_V_opt;
            c300L_aft=chord300L_V_opt; c300R_aft=chord300R_V_opt;
        }

        int n = c10L_bef.size();
        log("");
        log("==============================================");
        log("  " + label + " \u5F26\u6D4B\u503C\u5BF9\u6BD4 (\u4F18\u5316\u524D \u2192 \u4F18\u5316\u540E)");
        log("==============================================");

        // 10m 弦
        printChordCompare("10m \u5F26", n, c10L_bef, c10R_bef, c10L_aft, c10R_aft);
        // 30m 弦
        printChordCompare("30m \u5F26", n, c30L_bef, c30R_bef, c30L_aft, c30R_aft);
        // 300m 弦
        printChordCompare("300m \u5F26", n, c300L_bef, c300R_bef, c300L_aft, c300R_aft);

        // 切换到对应的图表模式（setSelectedIndex 会触发 ActionListener 自动调用 updateChart）
        int modeIdx = -1;
        if (isH) {
            if (chartModeCombo.getItemAt(2).contains("10m")) modeIdx = 2;
        } else {
            if (chartModeCombo.getItemAt(5).contains("10m")) modeIdx = 5;
        }
        if (modeIdx >= 0) {
            chartModeCombo.setSelectedIndex(modeIdx);
            // 不重复调用 updateChart()，由 combo 的 listener 负责
        } else {
            updateChart();
        }

        log(">>> " + label + " \u5F26\u6D4B\u503C\u8BA1\u7B97\u5B8C\u6210");
        statusLabel.setText(label + " \u5F26\u6D4B\u503C\u5DF2\u663E\u793A");
    }

    /** 打印一种弦长的优化前后对比 */
    private void printChordCompare(String name, int n,
                                    DoubleArr befL, DoubleArr befR,
                                    DoubleArr aftL, DoubleArr aftR) {
        log("");
        log("--- " + name + " ---");
        log(String.format("%-12s %-12s %-12s %-12s %-12s",
            "\u91CC\u7A0B", "\u524D-\u5DE6", "\u524D-\u53F3", "\u540E-\u5DE6", "\u540E-\u53F3"));
        log("------------------------------------------------------------------------");
        int step = Math.max(1, n / 20);  // 最多打 20 行
        double maxBefL = 0, maxBefR = 0, maxAftL = 0, maxAftR = 0;
        for (int i = 0; i < n; i++) {
            double bl = Math.abs(befL.get(i)), br = Math.abs(befR.get(i));
            double al = Math.abs(aftL.get(i)), ar = Math.abs(aftR.get(i));
            if (bl > maxBefL) maxBefL = bl;
            if (br > maxBefR) maxBefR = br;
            if (al > maxAftL) maxAftL = al;
            if (ar > maxAftR) maxAftR = ar;
            if (i % step == 0 || i == n-1) {
                log(String.format("%-12.3f %-12.4f %-12.4f %-12.4f %-12.4f",
                    surveyData.get(i).getMileage(),
                    befL.get(i), befR.get(i), aftL.get(i), aftR.get(i)));
            }
        }
        log(String.format("  \u6700\u5927\u503C:  \u524D\u5DE6=%.4f  \u524D\u53F3=%.4f  \u540E\u5DE6=%.4f  \u540E\u53F3=%.4f",
            maxBefL, maxBefR, maxAftL, maxAftR));
        log(String.format("  \u6539\u5584\u7387:  \u5DE6\u8F68=%.1f%%  \u53F3\u8F68=%.1f%%",
            (maxBefL > 0 ? (1 - maxAftL/maxBefL)*100 : 0),
            (maxBefR > 0 ? (1 - maxAftR/maxBefR)*100 : 0)));
    }

    // ==================== TQI 计算（调用 TQICal 原生 API）====================

    /** 准备 6 项数据 → 构建 TQIValueVector → 调用 TQICal 计算 → 显示结果 */
    private void handleTQICalc() {
        if (surveyData == null) { log("!!! 请先加载数据"); return; }
        log(">>> 计算 TQI（每 200m 段）...");

        // 确保 10m 弦已计算（轨向=10m弦H、高低=10m弦V）
        if ((chord10L_H == null || chord10L_H.size() == 0) && originL_H != null)
            computeAllChords(true);
        if ((chord10L_V == null || chord10L_V.size() == 0) && originL_V != null)
            computeAllChords(false);

        // 同步（直接）计算
        ChainArr chains = new ChainArr();
        chains.add(new ALChain(0, 0, " "));

        java.util.List<String[]> beforeRows = computeTQIWithDetails(false, chains);
        java.util.List<String[]> afterRows  = computeTQIWithDetails(true, chains);

        if (beforeRows == null || beforeRows.isEmpty()) {
            log("!!! TQI 计算失败");
            return;
        }

        // 合并：将优化后数据追加到优化前行的右侧
        java.util.List<String[]> merged = new java.util.ArrayList<>();
        for (int i = 0; i < beforeRows.size(); i++) {
            String[] bRow = beforeRows.get(i);
            if (afterRows != null && i < afterRows.size()) {
                String[] aRow = afterRows.get(i);
                String[] mRow = new String[bRow.length + 9];
                System.arraycopy(bRow, 0, mRow, 0, bRow.length);
                System.arraycopy(aRow, 2, mRow, bRow.length, 7);
                mRow[bRow.length+7] = aRow[9];
                double befTQI = Double.parseDouble(bRow[9]);
                double aftTQI = Double.parseDouble(aRow[9]);
                double imprv = befTQI > 0 ? (1 - aftTQI/befTQI)*100 : 0;
                mRow[bRow.length+8] = String.format("%.1f", imprv);
                merged.add(mRow);
            } else {
                merged.add(bRow);
            }
        }

        // 列名
        String[] baseCols = {
            "起始里程","终止里程",
            "前左轨向","前右轨向","前左高低","前右高低",
            "前轨距","前水平","前三角坑","前TQI"
        };
        String[] colNames = baseCols;
        if (afterRows != null && !afterRows.isEmpty()) {
            colNames = new String[baseCols.length + 9];
            System.arraycopy(baseCols, 0, colNames, 0, baseCols.length);
            colNames[baseCols.length]="后左轨向"; colNames[baseCols.length+1]="后右轨向";
            colNames[baseCols.length+2]="后左高低"; colNames[baseCols.length+3]="后右高低";
            colNames[baseCols.length+4]="后轨距"; colNames[baseCols.length+5]="后水平";
            colNames[baseCols.length+6]="后三角坑"; colNames[baseCols.length+7]="后TQI";
            colNames[baseCols.length+8]="改善率%";
        }

        showTQITable(colNames, merged);
        log(">>> TQI 计算完成，共 " + beforeRows.size() + " 个段");
        statusLabel.setText("TQI 已计算 (" + beforeRows.size() + " 段)");
    }

    /**
     * 构建 TQIValueVector → 调用 TQICal 计算 → 返回每行10列的段数据：
     * [0]起始里程 [1]终止里程 [2]左轨向 [3]右轨向 [4]左高低 [5]右高低
     * [6]轨距 [7]水平 [8]三角坑 [9]TQI
     *
     * 说明：TQIValue 的 Java wrapper 没有暴露单项 getter，因此 7 项指标
     * 从输入数据反算标准差填入；综合 TQI 直接从 CalTQI() 获取。
     */
    private java.util.List<String[]> computeTQIWithDetails(boolean useOpt, ChainArr chains) {
        // 1. 准备 6 项输入数据
        int n = surveyData.size();

        // 轨向 = H 的 10m 弦
        DoubleArr chH_L = useOpt ? chord10L_H_opt : chord10L_H;
        DoubleArr chH_R = useOpt ? chord10R_H_opt : chord10R_H;
        DoubleArr chV_L = useOpt ? chord10L_V_opt : chord10L_V;
        DoubleArr chV_R = useOpt ? chord10R_V_opt : chord10R_V;
        DoubleArr rX_L  = useOpt ? afterL_H : originL_H;
        DoubleArr rX_R  = useOpt ? afterR_H : originR_H;
        DoubleArr rY_L  = useOpt ? afterL_V : originL_V;
        DoubleArr rY_R  = useOpt ? afterR_V : originR_V;

        if (useOpt) {
            if (chH_L == null || chH_L.size()==0 || rX_L==null || rX_L.size()==0)
                return null;
        }
        // 回退
        if (chH_L==null||chH_L.size()==0) chH_L=chord10L_H;
        if (chH_R==null||chH_R.size()==0) chH_R=chord10R_H;
        if (chV_L==null||chV_L.size()==0) chV_L=chord10L_V;
        if (chV_R==null||chV_R.size()==0) chV_R=chord10R_V;
        if (rX_L==null||rX_L.size()==0) rX_L=originL_H;
        if (rX_R==null||rX_R.size()==0) rX_R=originR_H;
        if (rY_L==null||rY_L.size()==0) rY_L=originL_V;
        if (rY_R==null||rY_R.size()==0) rY_R=originR_V;

        // 同时准备 7 项逐点值（用于手动算标准差展示）
        double[][] pt = new double[7][n];
        TQIValueVector input = new TQIValueVector();
        for (int i = 0; i < n; i++) {
            double hL = i<chH_L.size()?chH_L.get(i):0;
            double hR = i<chH_R.size()?chH_R.get(i):0;
            double vL = i<chV_L.size()?chV_L.get(i):0;
            double vR = i<chV_R.size()?chV_R.get(i):0;
            double xL = i<rX_L.size()?rX_L.get(i):0;
            double xR = i<rX_R.size()?rX_R.get(i):0;
            double yL = i<rY_L.size()?rY_L.get(i):0;
            double yR = i<rY_R.size()?rY_R.get(i):0;
            double gauge = xR - xL;
            double level = yR - yL;

            // 7 项逐点值
            pt[0][i] = hL;      // 左轨向
            pt[1][i] = hR;      // 右轨向
            pt[2][i] = vL;      // 左高低
            pt[3][i] = vR;      // 右高低
            pt[4][i] = gauge;   // 轨距
            pt[5][i] = level;   // 水平
            // 三角坑先填 0，下面统一算
            pt[6][i] = 0;

            // 构建 TQIValue（三角坑填 0，CalPit 自动算）
            input.add(new TQIValue(surveyData.get(i).getMileage(), i, hL, hR, vL, vR, gauge, level, 0));
        }
        // 手动算三角坑（用于展示）：水平的差分
        for (int i = n-1; i > 0; i--) pt[6][i] = pt[5][i] - pt[5][i-1];
        pt[6][0] = pt[6][1];

        // 2. 调用 TQICal
        try {
            double stM = surveyData.get(0).getMileage();
            chains.get(0).setM_SubMileage(stM);
            TQICal cal = new TQICal(input, chains);
            cal.CalRealMileage();
            cal.CalPit();  // 自动计算三角坑

            
            double edM = surveyData.get(n-1).getMileage();

            TQIValueVector resultVals = new TQIValueVector();
            DoubleDoublePairVector splitRanges = new DoubleDoublePairVector();

            if (!cal.GetTQIVal(resultVals, splitRanges, stM, 0, edM, n-1))
                return null;

            int nSeg = Math.min(resultVals.size(), splitRanges.size());
            java.util.List<String[]> rows = new java.util.ArrayList<>();

            for (int s = 0; s < nSeg; s++) {
                double segStart = splitRanges.get(s).getFirst();
                double segEnd   = splitRanges.get(s).getSecond();
                double tqi = resultVals.get(s).CalTQI();  // 来自 TQICal 的综合 TQI

                // 找该段对应的点范围
                int i0=0, i1=n;
                for (int i=0; i<n; i++) {
                    if (surveyData.get(i).getMileage() < segStart) i0=i+1;
                    if (surveyData.get(i).getMileage() <= segEnd) i1=i+1;
                }
                i1 = Math.min(i1, n);
                int cnt = i1 - i0;
                if (cnt <= 0) continue;

                // 手动算 7 项标准差
                String[] row = new String[10];
                row[0] = String.format("%.3f", segStart);
                row[1] = String.format("%.3f", segEnd);
                for (int k = 0; k < 7; k++) {
                    double sum=0;
                    for (int i=i0; i<i1; i++) sum += pt[k][i];
                    double mean = sum/cnt;
                    double var=0;
                    for (int i=i0; i<i1; i++) var += (pt[k][i]-mean)*(pt[k][i]-mean);
                    row[2+k] = String.format("%.3f", Math.sqrt(var/cnt));
                }
                row[9] = String.format("%.3f", tqi);
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            log("!!! TQICal 异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** 弹出 TQI 结果表格对话框 */
    private void showTQITable(String[] colNames, java.util.List<String[]> rows) {
        JDialog dialog = new JDialog(this, "TQI 计算结果 (每200m段)", true);
        dialog.setSize(1100, 500);
        dialog.setLocationRelativeTo(this);

        String[][] data = rows.toArray(new String[0][]);
        JTable table = new JTable(new AbstractTableModel() {
            public int getRowCount() { return data.length; }
            public int getColumnCount() { return colNames.length; }
            public String getColumnName(int col) { return colNames[col]; }
            public Object getValueAt(int r, int c) { return data[r][c]; }
        });
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFont(new Font("Monospaced", Font.PLAIN, 11));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        for (int c = 0; c < colNames.length; c++) {
            int maxW = table.getFontMetrics(table.getFont()).stringWidth(colNames[c]) + 12;
            for (int r = 0; r < Math.min(data.length, 30); r++) {
                int w = table.getFontMetrics(table.getFont()).stringWidth(data[r][c]) + 12;
                if (w > maxW) maxW = w;
            }
            table.getColumnModel().getColumn(c).setPreferredWidth(Math.max(maxW, 65));
        }
        dialog.add(new JScrollPane(table));
        dialog.setVisible(true);
    }

    // ==================== 导出 ====================
    private void handleExport() {
        if (surveyData == null || marginOpt == null || heightOpt == null) {
            JOptionPane.showMessageDialog(this, "\u8BF7\u5148\u52A0\u8F7D\u6570\u636E\u5E76\u6267\u884C\u4F18\u5316",
                "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String path = saveFileDialog();
        if (path == null) return;
        try (PrintWriter w = new PrintWriter(path, "GBK")) {
            w.printf("\u91CC\u7A0B,\u5DE6\u8F68\u5E73\u9762\u504F\u5DEE,\u53F3\u8F68\u5E73\u9762\u504F\u5DEE,");
            w.printf("\u5DE6\u8F68\u9AD8\u7A0B\u504F\u5DEE,\u53F3\u8F68\u9AD8\u7A0B\u504F\u5DEE,");
            w.printf("\u5DE6\u8F68\u5E73\u9762\u8C03\u6574\u91CF,\u53F3\u8F68\u5E73\u9762\u8C03\u6574\u91CF,");
            w.printf("\u5DE6\u8F68\u9AD8\u7A0B\u8C03\u6574\u91CF,\u53F3\u8F68\u9AD8\u7A0B\u8C03\u6574\u91CF%n");
            for (int i = 0; i < surveyData.size(); i++) {
                RailTrackSurveyPoint pt = surveyData.get(i);
                OptValueData m = marginOpt.get(i);
                OptValueData h = heightOpt.get(i);
                w.printf("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    pt.getMileage(),
                    pt.getX_L(), pt.getX_R(),
                    pt.getY_L(), pt.getY_R(),
                    m.getXOpt_L(), m.getXOpt_R(),
                    h.getYOpt_L(), h.getYOpt_R());
            }
            log(">>> \u8C03\u6574\u91CF\u5DF2\u5BFC\u51FA: " + path);
        } catch (IOException e) {
            log("!!! \u5BFC\u51FA\u5931\u8D25: " + e.getMessage());
        }
    }

    private void handleExportWave() {
        if (surveyData == null || marginOpt == null || heightOpt == null) {
            JOptionPane.showMessageDialog(this, "\u8BF7\u5148\u52A0\u8F7D\u6570\u636E\u5E76\u6267\u884C\u4F18\u5316",
                "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String path = saveFileDialog();
        if (path == null) return;
        try (PrintWriter w = new PrintWriter(path, "GBK")) {
            w.println("\u91CC\u7A0B," +
                "LH\u957F\u6CE2,LH\u957F\u6CE2O,RH\u957F\u6CE2,RH\u957F\u6CE2O," +
                "LH\u4E2D\u6CE2,LH\u4E2D\u6CE2O,RH\u4E2D\u6CE2,RH\u4E2D\u6CE2O," +
                "LH\u77ED\u6CE2,LH\u77ED\u6CE2O,RH\u77ED\u6CE2,RH\u77ED\u6CE2O," +
                "LV\u957F\u6CE2,LV\u957F\u6CE2O,RV\u957F\u6CE2,RV\u957F\u6CE2O," +
                "LV\u4E2D\u6CE2,LV\u4E2D\u6CE2O,RV\u4E2D\u6CE2,RV\u4E2D\u6CE2O," +
                "LV\u77ED\u6CE2,LV\u77ED\u6CE2O,RV\u77ED\u6CE2,RV\u77ED\u6CE2O");
            for (int i = 0; i < surveyData.size(); i++) {
                double mil = surveyData.get(i).getMileage();
                w.printf("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f," +
                        "%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    mil,
                    getSafe(chord300L_H, i), getSafe(chord300L_H_opt, i),
                    getSafe(chord300R_H, i), getSafe(chord300R_H_opt, i),
                    getSafe(chord30L_H, i),  getSafe(chord30L_H_opt, i),
                    getSafe(chord30R_H, i),  getSafe(chord30R_H_opt, i),
                    getSafe(chord10L_H, i),  getSafe(chord10L_H_opt, i),
                    getSafe(chord10R_H, i),  getSafe(chord10R_H_opt, i),
                    getSafe(chord300L_V, i), getSafe(chord300L_V_opt, i),
                    getSafe(chord300R_V, i), getSafe(chord300R_V_opt, i),
                    getSafe(chord30L_V, i),  getSafe(chord30L_V_opt, i),
                    getSafe(chord30R_V, i),  getSafe(chord30R_V_opt, i),
                    getSafe(chord10L_V, i),  getSafe(chord10L_V_opt, i),
                    getSafe(chord10R_V, i),  getSafe(chord10R_V_opt, i));
            }
            log(">>> \u6CE2\u6570\u636E\u5DF2\u5BFC\u51FA: " + path);
        } catch (IOException e) {
            log("!!! \u5BFC\u51FA\u5931\u8D25: " + e.getMessage());
        }
    }

    private double getSafe(DoubleArr arr, int i) {
        if (arr == null || i >= arr.size()) return 0;
        return arr.get(i);
    }

    // ==================== 图表 ====================
    private void updateChart() {
        int mode = chartModeCombo.getSelectedIndex();
        String title = (String) chartModeCombo.getSelectedItem();

        java.util.List<double[]> seriesList = new java.util.ArrayList<>();
        java.util.List<String>   nameList   = new java.util.ArrayList<>();
        java.util.List<Color>    colorList  = new java.util.ArrayList<>();

        // 获取要显示的数据
        DoubleArr beforeL = null, beforeR = null, afterL = null, afterR = null;
        boolean isH = mode <= 2; // 0/1 = deviation, 2/3/4 = chord(H), 5/6/7 = chord(V)

        switch (mode) {
            case 0: // 平面偏差
                beforeL = originL_H; beforeR = originR_H; afterL = afterL_H; afterR = afterR_H; break;
            case 1: // 高程偏差
                beforeL = originL_V; beforeR = originR_V; afterL = afterL_V; afterR = afterR_V; break;
            case 2: beforeL = chord10L_H;  beforeR = chord10R_H;  afterL = chord10L_H_opt;  afterR = chord10R_H_opt;  break;
            case 3: beforeL = chord30L_H;  beforeR = chord30R_H;  afterL = chord30L_H_opt;  afterR = chord30R_H_opt;  break;
            case 4: beforeL = chord300L_H; beforeR = chord300R_H; afterL = chord300L_H_opt; afterR = chord300R_H_opt; break;
            case 5: beforeL = chord10L_V;  beforeR = chord10R_V;  afterL = chord10L_V_opt;  afterR = chord10R_V_opt;  break;
            case 6: beforeL = chord30L_V;  beforeR = chord30R_V;  afterL = chord30L_V_opt;  afterR = chord30R_V_opt;  break;
            case 7: beforeL = chord300L_V; beforeR = chord300R_V; afterL = chord300L_V_opt; afterR = chord300R_V_opt; break;
        }

        // 构建系列
        if (cbShowBefore.isSelected() && beforeL != null) {
            if (cbShowLeft.isSelected())  { seriesList.add(toArray(beforeL)); nameList.add("\u4F18\u5316\u524D-\u5DE6"); colorList.add(new Color(0, 102, 204)); }
            if (cbShowRight.isSelected()) { seriesList.add(toArray(beforeR)); nameList.add("\u4F18\u5316\u524D-\u53F3"); colorList.add(new Color(0, 153, 51));  }
        }
        if (cbShowAfter.isSelected() && afterL != null) {
            if (cbShowLeft.isSelected())  { seriesList.add(toArray(afterL)); nameList.add("\u4F18\u5316\u540E-\u5DE6"); colorList.add(new Color(204, 0, 0));   }
            if (cbShowRight.isSelected()) { seriesList.add(toArray(afterR)); nameList.add("\u4F18\u5316\u540E-\u53F3"); colorList.add(new Color(204, 102, 0)); }
        }

        // 里程序列
        double[] mileage = null;
        if (surveyData != null) {
            mileage = new double[surveyData.size()];
            for (int i = 0; i < surveyData.size(); i++)
                mileage[i] = surveyData.get(i).getMileage();
        }

        chartPanel.setData(mileage, seriesList, nameList, colorList, title);
    }

    private double[] toArray(DoubleArr arr) {
        if (arr == null) return new double[0];
        double[] r = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) r[i] = arr.get(i);
        return r;
    }

    // ==================== 内部类：图表面板 ====================
    private static class DeviationChartPanel extends JPanel {
        private double[] xData;
        private java.util.List<double[]> ySeries = new java.util.ArrayList<>();
        private java.util.List<String>   names   = new java.util.ArrayList<>();
        private java.util.List<Color>    colors  = new java.util.ArrayList<>();
        private String title = "";

        private static final int MARGIN_L = 70, MARGIN_R = 30, MARGIN_T = 40, MARGIN_B = 60;

        public void setData(double[] x, java.util.List<double[]> ys,
                            java.util.List<String> ns, java.util.List<Color> cs, String t) {
            xData = x;
            ySeries = ys;
            names = ns;
            colors = cs;
            title = t;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int plotX = MARGIN_L, plotY = MARGIN_T;
            int plotW = w - MARGIN_L - MARGIN_R;
            int plotH = h - MARGIN_T - MARGIN_B;

            if (plotW < 50 || plotH < 50) return;
            if (xData == null || xData.length == 0) {
                g2.setColor(Color.GRAY);
                g2.drawString("\u8BF7\u52A0\u8F7D\u6570\u636E\u5E76\u6267\u884C\u4F18\u5316", w/2-80, h/2);
                return;
            }

            // 计算范围
            double xMin = xData[0], xMax = xData[xData.length-1];
            double yMin = Double.POSITIVE_INFINITY, yMax = Double.NEGATIVE_INFINITY;
            for (double[] s : ySeries) {
                for (double v : s) {
                    if (Double.isFinite(v)) {
                        if (v < yMin) yMin = v;
                        if (v > yMax) yMax = v;
                    }
                }
            }
            if (!Double.isFinite(yMin) || !Double.isFinite(yMax)) { yMin = -1; yMax = 1; }
            double yRange = yMax - yMin;
            if (yRange < 0.001) { yMin -= 0.5; yMax += 0.5; yRange = 1; }
            double yPad = yRange * 0.1;
            yMin -= yPad; yMax += yPad;

            // 绘背景
            g2.setColor(Color.WHITE);
            g2.fillRect(plotX, plotY, plotW, plotH);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRect(plotX, plotY, plotW, plotH);

            // 网格
            g2.setColor(new Color(220, 220, 220));
            int nGridY = 8;
            for (int i = 0; i <= nGridY; i++) {
                int yy = plotY + plotH * i / nGridY;
                g2.drawLine(plotX, yy, plotX + plotW, yy);
            }
            int nGridX = Math.min(10, xData.length);
            for (int i = 0; i <= nGridX; i++) {
                int xx = plotX + plotW * i / nGridX;
                g2.drawLine(xx, plotY, xx, plotY + plotH);
            }

            // Y轴标签
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(Color.DARK_GRAY);
            for (int i = 0; i <= nGridY; i++) {
                double val = yMax - (yMax - yMin) * i / nGridY;
                int yy = plotY + plotH * i / nGridY;
                String lbl = String.format("%.1f", val);
                int tw = g2.getFontMetrics().stringWidth(lbl);
                g2.drawString(lbl, plotX - tw - 5, yy + 4);
            }

            // X轴标签（取几个关键点）
            int nXTicks = Math.min(10, xData.length);
            for (int i = 0; i <= nXTicks; i++) {
                int idx = i * (xData.length - 1) / nXTicks;
                double val = xData[idx];
                int xx = plotX + (int)((val - xMin) / (xMax - xMin) * plotW);
                String lbl = String.format("%.0f", val);
                int tw = g2.getFontMetrics().stringWidth(lbl);
                g2.drawString(lbl, xx - tw/2, plotY + plotH + 15);
            }
            g2.drawString("\u91CC\u7A0B (m)", plotX + plotW/2 - 20, plotY + plotH + 35);

            // 标题
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(Color.BLACK);
            int tw = g2.getFontMetrics().stringWidth(title);
            g2.drawString(title, plotX + plotW/2 - tw/2, plotY - 8);

            // 系列
            int idx = 0;
            for (double[] s : ySeries) {
                if (s.length == 0) continue;
                g2.setColor(colors.get(idx));
                g2.setStroke(new BasicStroke(1.5f));

                int prevX = -1, prevY = -1;
                for (int i = 0; i < Math.min(xData.length, s.length); i++) {
                    double xv = xData[i];
                    double yv = s[i];
                    if (!Double.isFinite(yv)) continue;
                    int px = plotX + (int)((xv - xMin) / (xMax - xMin) * plotW);
                    int py = plotY + (int)((yMax - yv) / (yMax - yMin) * plotH);
                    if (prevX >= 0)
                        g2.drawLine(prevX, prevY, px, py);
                    // 画点
                    g2.fillOval(px-2, py-2, 4, 4);
                    prevX = px; prevY = py;
                }
                idx++;
            }

            // 图例
            int legX = plotX + 8, legY = plotY + 8;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            idx = 0;
            for (String n : names) {
                Color c = colors.get(idx);
                g2.setColor(c);
                g2.fillRect(legX, legY + idx*18, 14, 10);
                g2.setColor(Color.BLACK);
                g2.drawString(n, legX + 18, legY + idx*18 + 10);
                idx++;
            }

            // Y轴标题
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            java.awt.geom.AffineTransform oldXform = g2.getTransform();
            g2.rotate(-Math.PI/2);
            g2.drawString("\u504F\u5DEE\u91CF (mm)", -plotY - plotH/2 - 20, 18);
            g2.setTransform(oldXform);
        }
    }

    // ==================== 工具 ====================
    private ArrayList<RailTrackSurveyPoint> readCsv(String filePath) throws IOException {
        ArrayList<RailTrackSurveyPoint> points = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), "GBK"))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] cols = line.split(",", -1);
                if (cols.length < 6) {
                    if (lineNum == 1) continue;
                    log("  \u8DF3\u8FC7\u7B2C " + lineNum + " \u884C");
                    continue;
                }
                try {
                    double mileage = Double.parseDouble(cols[1].trim());
                    double xL = parseDouble(cols[2].trim(), 0);
                    double xR = parseDouble(cols[3].trim(), 0);
                    double yL = parseDouble(cols[4].trim(), 0);
                    double yR = parseDouble(cols[5].trim(), 0);
                    RailTrackSurveyPoint pt = new RailTrackSurveyPoint();
                    pt.setMileage(mileage);
                    pt.setX_L(xL); pt.setX_R(xR);
                    pt.setY_L(yL); pt.setY_R(yR);
                    pt.setGauge(1435.0);
                    points.add(pt);
                } catch (NumberFormatException e) {
                    if (lineNum <= 3) continue;
                }
            }
        }
        return points;
    }

    private String openFileDialog() {
        FileDialog fd = new FileDialog(this, "\u9009\u62E9\u8F68\u9053\u6D4B\u91CF\u6570\u636E", FileDialog.LOAD);
        fd.setVisible(true);
        if (fd.getFile() != null) return new File(fd.getDirectory(), fd.getFile()).getAbsolutePath();
        return null;
    }

    private String saveFileDialog() {
        FileDialog fd = new FileDialog(this, "\u4FDD\u5B58\u6587\u4EF6", FileDialog.SAVE);
        fd.setVisible(true);
        if (fd.getFile() != null) return new File(fd.getDirectory(), fd.getFile()).getAbsolutePath();
        return null;
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
        System.out.println(msg);
    }

    private void setOptButtonsEnabled(boolean en) {
        btnOptHori.setEnabled(en);
        btnOptVert.setEnabled(en);
        btnOptHoriLong.setEnabled(en);
        btnOptVertLong.setEnabled(en);
        btnExport.setEnabled(en);
        btnExportWave.setEnabled(en);
    }
}
