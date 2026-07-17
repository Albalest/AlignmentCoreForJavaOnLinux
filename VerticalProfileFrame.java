import AlignmentCore.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;

/**
 * Vertical profile adjustment window.
 * Shows design grade line + survey points, with interactive GCP editing tools.
 */
public class VerticalProfileFrame extends Frame {

    private final AlignmentFitting fitting;

    // ---- Plot data ----
    private List<ProfilePoint> designLine;
    private List<ProfilePoint> gradeChangePts;
    private List<ProfilePoint> surveyPts;

    // ---- UI state ----
    private int selectedGCPIndex = -1;
    private boolean lockFrontSlope = false;
    private boolean lockBackSlope = false;
    private TextField liftValueField;

    // ---- Interactive mode ----
    private enum InteractionMode { NONE, MOVE_GCP, INSERT_GCP }
    private InteractionMode interactionMode = InteractionMode.NONE;
    private int moveSourceIndex = -1; // for move: source GCP index
    private String statusMessage = "";

    // ---- Plot state (for mouse interaction & painting) ----
    private double plotMinX, plotMaxX, plotMinY, plotMaxY;
    private int plotLeft, plotTop, plotWidth, plotHeight, padding = 65;
    private double xScale, yScale;

    private final ProfileCanvas canvas;

    // ================================================================
    //  Construction
    // ================================================================

    public VerticalProfileFrame(AlignmentFitting fitting) {
        super("\u7EB5\u65AD\u9762\u8C03\u6574\u793A\u610F");
        this.fitting = fitting;

        collectData();

        setSize(1100, 750);
        setLayout(new BorderLayout());

        canvas = new ProfileCanvas();
        add(canvas, BorderLayout.CENTER);
        add(createToolbar(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelInteractionMode();
                dispose();
            }
        });

        setLocationRelativeTo(null);
    }

    // ================================================================
    //  Data collection
    // ================================================================

    private void collectData() {
        designLine = new ArrayList<>();
        gradeChangePts = new ArrayList<>();
        surveyPts = new ArrayList<>();

        if (fitting == null) return;

        SingleAlignmentBase alignment = fitting.GetAlignment();
        if (alignment != null) {
            GradeChangePointArr gcps = alignment.GetGradeChangePoints();
            if (gcps != null && gcps.size() >= 2) {
                for (int i = 0; i < gcps.size(); i++) {
                    ALGradeChangePoint gcp = gcps.get(i);
                    gradeChangePts.add(new ProfilePoint(gcp.getM_RealMilage(), gcp.getM_Height()));
                }

                double startMileage = gcps.get(0).getM_RealMilage() - 50;
                double endMileage = gcps.get(gcps.size() - 1).getM_RealMilage() + 50;

                double alStart = alignment.GetAlignmentStartDM();
                double alEnd = alignment.GetAlignmentEndDM().getFirst();
                if (alStart > 0) startMileage = Math.min(startMileage, alStart);
                if (alEnd > 0) endMileage = Math.max(endMileage, alEnd);

                double step = 1.0;
                for (double rm = startMileage; rm <= endMileage; rm += step) {
                    double h = alignment.GetDesignHeightByRM(rm);
                    if (h < 1e9) {
                        designLine.add(new ProfilePoint(rm, h));
                    }
                }
            }
        }

        SurveyDatas surveyDatas = fitting.GetSurveyDatas();
        if (surveyDatas != null) {
            SurveyVector sv = surveyDatas.getM_SurveyImfos();
            if (sv != null) {
                for (int i = 0; i < sv.size(); i++) {
                    SurveyImfo imfo = sv.get(i);
                    double m = imfo.getM_mileage();
                    double h = imfo.getM_surveyPoint().getZ();
                    surveyPts.add(new ProfilePoint(m, h));
                }
            }
        }
    }

    /** Re-fetch data from native layer and repaint. Call after each modification. */
    private void refreshData() {
        collectData();
        if (selectedGCPIndex >= gradeChangePts.size()) {
            selectedGCPIndex = -1;
        }
        canvas.repaint();
    }

    // ================================================================
    //  Helper: get current GCP count from native
    // ================================================================

    private int getGCPCount() {
        if (fitting == null) return 0;
        SingleAlignmentBase alignment = fitting.GetAlignment();
        if (alignment == null) return 0;
        GradeChangePointArr gcps = alignment.GetGradeChangePoints();
        return (gcps == null) ? 0 : gcps.size();
    }

    // ================================================================
    //  Toolbar creation
    // ================================================================

    private Panel createToolbar() {
        Panel toolbar = new Panel(new BorderLayout());
        toolbar.setBackground(new Color(240, 240, 240));

        // ---- Left side: button groups ----
        Panel btnPanel = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 3));

        // --- Group 1: GCP operations ---
        JPanel g1 = new JPanel(new GridLayout(1, 5, 3, 0));
        g1.setBorder(border("GCP"));
        g1.add(buildButton("\u63D2\u5165\u53D8\u8D2F\u70B9", e -> onInsertGCP()));
        g1.add(buildButton("\u5220\u9664\u53D8\u8D2F\u70B9", e -> onDeleteGCP()));
        g1.add(buildButton("\u79FB\u52A8\u53D8\u8D2F\u70B9", e -> onMoveGCP()));
        g1.add(buildButton("\u7EB5\u65AD\u9762\u4F18\u5316", e -> onOptimizeProfile()));
        g1.add(buildButton("\u9650\u503C\u8BBE\u7F6E", e -> onLimitSettings()));
        btnPanel.add(g1);

        // --- Group 2: Slope / radius ---
        JPanel g2 = new JPanel(new GridLayout(1, 3, 3, 0));
        g2.setBorder(border("\u8C03\u6574"));
        g2.add(buildButton("\u4FEE\u6539\u5761\u957F\u5761\u7387", e -> onModifySlope()));
        g2.add(buildButton("\u4FEE\u6539\u7AD6\u66F2\u7EBF\u534A\u5F84", e -> onModifyRadius()));
        g2.add(buildButton("\u4F18\u5316\u7AD6\u66F2\u7EBF(\u9009\u62E9)", e -> onAutoFitRadius()));
        btnPanel.add(g2);

        // --- Group 3: Lift ---
        JPanel g3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        g3.setBorder(border("\u6574\u4F53\u62AC\u5347"));
        g3.add(buildButton("\u6574\u4F53\u62AC\u5347", e -> onLiftAll()));
        g3.add(new Label(" \u62AC\u5347\u503C:", Label.RIGHT));
        liftValueField = new TextField("0.0", 8);
        g3.add(liftValueField);
        g3.add(new Label(" mm "));
        btnPanel.add(g3);

        // --- Group 4: Lock options ---
        JPanel g4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        g4.setBorder(border("\u9501\u5B9A"));
        Checkbox chkFront = new Checkbox("\u9501\u5B9A\u524D\u5761\u5EA6", lockFrontSlope);
        chkFront.addItemListener(e -> { lockFrontSlope = chkFront.getState(); });
        g4.add(chkFront);

        Checkbox chkBack = new Checkbox("\u9501\u5B9A\u540E\u5761\u5EA6", lockBackSlope);
        chkBack.addItemListener(e -> { lockBackSlope = chkBack.getState(); });
        g4.add(chkBack);

        g4.add(buildButton("\u79FB\u52A8\u53D8\u8D2F\u70B9(\u5761\u7387\u4E0D\u53D8\u9501\u524D)", e -> onMoveGCPLockFront()));
        g4.add(buildButton("\u79FB\u52A8\u53D8\u8D2F\u70B9(\u5761\u7387\u4E0D\u53D8\u9501\u540E)", e -> onMoveGCPLockBack()));
        btnPanel.add(g4);

        toolbar.add(btnPanel, BorderLayout.CENTER);

        // ---- Right side: confirm / cancel ----
        Panel right = new Panel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        right.add(buildButton("\u786E\u8BA4\u4FEE\u6539", e -> onConfirm()));
        right.add(buildButton("\u53D6\u6D88", e -> dispose()));
        toolbar.add(right, BorderLayout.EAST);

        return toolbar;
    }

    private static Border border(String title) {
        return BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), title,
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP,
            new Font("SansSerif", Font.PLAIN, 10));
    }

    private Button buildButton(String label, ActionListener listener) {
        Button btn = new Button(label);
        btn.addActionListener(listener);
        return btn;
    }

    // ================================================================
    //  GCP selection helpers
    // ================================================================

    /** @return the native GCP index selected in the chart, or -1 */
    private int getSelectedIndexOrPrompt() {
        if (selectedGCPIndex < 0 || selectedGCPIndex >= getGCPCount()) {
            JOptionPane.showMessageDialog(null, "\u8BF7\u5148\u5728\u56FE\u4E2D\u70B9\u51FB\u9009\u62E9\u4E00\u4E2A\u53D8\u8D2F\u70B9",
                "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return selectedGCPIndex;
    }

    /** Convert screen x coordinate to mileage */
    private double screenXToMileage(int sx) {
        return plotMinX + (sx - plotLeft) / xScale;
    }

    /** Convert screen y coordinate to elevation */
    private double screenYToHeight(int sy) {
        return plotMinY + (plotTop + plotHeight - sy) / yScale;
    }

    /** Cancel any ongoing interactive mode and reset UI state */
    private void cancelInteractionMode() {
        if (interactionMode != InteractionMode.NONE) {
            interactionMode = InteractionMode.NONE;
            moveSourceIndex = -1;
            statusMessage = "";
            canvas.setCursor(Cursor.getDefaultCursor());
            canvas.repaint();
        }
    }

    // ================================================================
    //  Button actions
    // ================================================================

    // ---- Insert GCP (interactive) ----
    private void onInsertGCP() {
        interactionMode = InteractionMode.INSERT_GCP;
        moveSourceIndex = -1;
        statusMessage = "\u8BF7\u5728\u56FE\u4E2D\u70B9\u51FB\u63D2\u5165\u4F4D\u7F6E";
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        System.out.println("\u63D2\u5165\u6A21\u5F0F: \u8BF7\u5728\u56FE\u4E2D\u70B9\u51FB\u63D2\u5165\u4F4D\u7F6E");
        canvas.repaint();
    }

    // ---- Delete GCP ----
    private void onDeleteGCP() {
        // Cancel any interactive mode
        cancelInteractionMode();
        int idx = getSelectedIndexOrPrompt();
        if (idx < 0) return;

        int count = getGCPCount();
        if (count <= 2) {
            JOptionPane.showMessageDialog(null, "\u81F3\u5C11\u9700\u8981\u4FDD\u75592\u4E2A\u53D8\u8D2F\u70B9\uFF0C\u65E0\u6CD5\u5220\u9664!",
                "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean ok = fitting.DeleteGCP(idx, false, 0);
        System.out.println(ok ? "\u5220\u9664\u53D8\u8D2F\u70B9\u6210\u529F!" : "\u5220\u9664\u53D8\u8D2F\u70B9\u5931\u8D25!");
        selectedGCPIndex = -1;
        refreshData();
    }

    // ---- Move GCP (interactive) ----
    private void onMoveGCP() {
        interactionMode = InteractionMode.MOVE_GCP;
        moveSourceIndex = -1;
        statusMessage = "\u8BF7\u70B9\u51FB\u9009\u62E9\u8981\u79FB\u52A8\u7684\u53D8\u8D2F\u70B9";
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        System.out.println("\u79FB\u52A8\u6A21\u5F0F: \u8BF7\u70B9\u51FB\u9009\u62E9\u8981\u79FB\u52A8\u7684\u53D8\u8D2F\u70B9");
        canvas.repaint();
    }

    // ---- Move GCP (lock front slope rate) ----
    private void onMoveGCPLockFront() {
        cancelInteractionMode();
        int idx = getSelectedIndexOrPrompt();
        if (idx < 0) return;

        SingleAlignmentBase alignment = fitting.GetAlignment();
        ALGradeChangePoint gcp = alignment.GetGradeChangePoints().get(idx);

        String mileStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u771F\u5B9E\u91CC\u7A0B (m):",
            String.format("%.3f", gcp.getM_RealMilage()));
        if (mileStr == null) return;
        double newMileage;
        try {
            newMileage = Double.parseDouble(mileStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u91CC\u7A0B\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String heightStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u9AD8\u7A0B (m):",
            String.format("%.3f", gcp.getM_Height()));
        if (heightStr == null) return;
        double newHeight;
        try {
            newHeight = Double.parseDouble(heightStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u9AD8\u7A0B\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // isFixedFront = true => lock front slope rate
        boolean ok = fitting.MoveGCPByFixed(newMileage, newHeight, idx, true, false, 0);
        System.out.println(ok ? "\u79FB\u52A8\u6210\u529F(\u9501\u524D\u5761\u7387\u4E0D\u53D8)!" : "\u79FB\u52A8\u5931\u8D25!");
        refreshData();
    }

    // ---- Move GCP (lock back slope rate) ----
    private void onMoveGCPLockBack() {
        cancelInteractionMode();
        int idx = getSelectedIndexOrPrompt();
        if (idx < 0) return;

        SingleAlignmentBase alignment = fitting.GetAlignment();
        ALGradeChangePoint gcp = alignment.GetGradeChangePoints().get(idx);

        String mileStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u771F\u5B9E\u91CC\u7A0B (m):",
            String.format("%.3f", gcp.getM_RealMilage()));
        if (mileStr == null) return;
        double newMileage;
        try {
            newMileage = Double.parseDouble(mileStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u91CC\u7A0B\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String heightStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u9AD8\u7A0B (m):",
            String.format("%.3f", gcp.getM_Height()));
        if (heightStr == null) return;
        double newHeight;
        try {
            newHeight = Double.parseDouble(heightStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u9AD8\u7A0B\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // isFixedFront = false => lock back slope rate
        boolean ok = fitting.MoveGCPByFixed(newMileage, newHeight, idx, false, false, 0);
        System.out.println(ok ? "\u79FB\u52A8\u6210\u529F(\u9501\u540E\u5761\u7387\u4E0D\u53D8)!" : "\u79FB\u52A8\u5931\u8D25!");
        refreshData();
    }

    // ---- Modify slope length & rate ----
    private void onModifySlope() {
        cancelInteractionMode();
        int idx = getSelectedIndexOrPrompt();
        if (idx < 0) return;

        SingleAlignmentBase alignment = fitting.GetAlignment();
        ALGradeChangePoint gcp = alignment.GetGradeChangePoints().get(idx);

        String lenStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u5761\u957F (m):",
            String.format("%.3f", gcp.getM_SlopeLength()));
        if (lenStr == null) return;
        double newLen;
        try {
            newLen = Double.parseDouble(lenStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u5761\u957F\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String rateStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u5761\u7387 (\u2030):",
            String.format("%.3f", gcp.getM_Gradient() * 1000));
        if (rateStr == null) return;
        double newRate;
        try {
            newRate = Double.parseDouble(rateStr.trim()) / 1000.0;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u5761\u7387\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean ok = fitting.ModifySlope(newLen, newRate, idx, false, 0);
        System.out.println(ok ? "\u4FEE\u6539\u5761\u957F\u5761\u7387\u6210\u529F!" : "\u4FEE\u6539\u5761\u957F\u5761\u7387\u5931\u8D25!");
        refreshData();
    }

    // ---- Modify vertical curve radius ----
    private void onModifyRadius() {
        cancelInteractionMode();
        int idx = getSelectedIndexOrPrompt();
        if (idx < 0) return;

        SingleAlignmentBase alignment = fitting.GetAlignment();
        ALGradeChangePoint gcp = alignment.GetGradeChangePoints().get(idx);

        String radiusStr = JOptionPane.showInputDialog(null, "\u8F93\u5165\u65B0\u7684\u7AD6\u66F2\u7EBF\u534A\u5F84 (m):",
            String.format("%.0f", gcp.getM_Radius()));
        if (radiusStr == null) return;
        double newRadius;
        try {
            newRadius = Double.parseDouble(radiusStr.trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u65E0\u6548\u7684\u534A\u5F84\u503C!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean ok = fitting.ModifyVertRadius(newRadius, idx, false, 0);
        System.out.println(ok ? "\u4FEE\u6539\u7AD6\u66F2\u7EBF\u534A\u5F84\u6210\u529F!" : "\u4FEE\u6539\u7AD6\u66F2\u7EBF\u534A\u5F84\u5931\u8D25!");
        refreshData();
    }

    // ---- Auto-fit vertical radius at selected GCP ----
    private void onAutoFitRadius() {
        cancelInteractionMode();
        int idx = getSelectedIndexOrPrompt();
        if (idx < 0) return;

        boolean ok = fitting.AutoFitVertRadius(idx);
        System.out.println(ok ? "\u4F18\u5316\u7AD6\u66F2\u7EBF\u534A\u5F84\u6210\u529F!" : "\u4F18\u5316\u7AD6\u66F2\u7EBF\u534A\u5F84\u5931\u8D25!");
        refreshData();
    }

    // ---- Lift all ----
    private void onLiftAll() {
        cancelInteractionMode();
        double dH;
        try {
            dH = Double.parseDouble(liftValueField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u8BF7\u8F93\u5165\u6709\u6548\u7684\u62AC\u5347\u503C(\u6BEB\u7C73)!", "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Convert mm to m
        double dHeightM = dH / 1000.0;

        boolean ok = fitting.UpDownSlopeAll(dHeightM, false, 0);
        System.out.println(ok ? "\u6574\u4F53\u62AC\u5347\u6210\u529F!" : "\u6574\u4F53\u62AC\u5347\u5931\u8D25!");
        refreshData();
    }

    // ---- Confirm modifications (recalculate margins) ----
    private void onConfirm() {
        cancelInteractionMode();
        fitting.CalVertMargin(false, 0);
        refreshData();
        System.out.println("\u786E\u8BA4\u4FEE\u6539\uFF0C\u5DF2\u91CD\u65B0\u8BA1\u7B97\u9AD8\u7A0B\u504F\u5DEE\u91CF!");
    }

    // ---- Optimize profile within index range ----
    private void onOptimizeProfile() {
        cancelInteractionMode();
        int count = getGCPCount();
        if (count < 2) {
            JOptionPane.showMessageDialog(null, "\u53D8\u8D2F\u70B9\u6570\u91CF\u4E0D\u8DB3\uFF0C\u65E0\u6CD5\u4F18\u5316!",
                "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String input = JOptionPane.showInputDialog(null,
            "\u8BF7\u8F93\u5165\u8D77\u59CB\u7D22\u5F15\u548C\u7EC8\u6B62\u7D22\u5F15\uFF0C\u7528\u9017\u53F7\u5206\u9694\n\u53D8\u8D2F\u70B9\u6570\u91CF: " + count + "\n\u7D22\u5F15\u8303\u56F4: 0 ~ " + (count - 1),
            "\u7EB5\u65AD\u9762\u4F18\u5316", JOptionPane.QUESTION_MESSAGE);
        if (input == null || input.trim().isEmpty()) return;

        String[] parts = input.trim().split("[,\\s]+");
        int stIdx, edIdx;
        try {
            stIdx = Integer.parseInt(parts[0].trim());
            edIdx = Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "\u8BF7\u8F93\u5165\u6709\u6548\u7684\u7D22\u5F15\u6570\u5B57! \u683C\u5F0F: \u8D77\u59CB,\u7EC8\u6B62",
                "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (stIdx < 0 || edIdx >= count || stIdx > edIdx) {
            JOptionPane.showMessageDialog(null, "\u7D22\u5F15\u8303\u56F4\u65E0\u6548! \n\u53D8\u8D2F\u70B9\u6570\u91CF: " + count + "\n\u8BF7\u8F93\u5165 0 ~ " + (count - 1) + " \u4E4B\u95F4\u7684\u7D22\u5F15\uFF0C\u4E14\u8D77\u59CB <= \u7EC8\u6B62",
                "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean ok = fitting.AutoSlopeFitting(stIdx, edIdx);
        System.out.println(ok
            ? "\u7EB5\u65AD\u9762\u4F18\u5316\u6210\u529F! \u8303\u56F4: " + stIdx + " ~ " + edIdx
            : "\u7EB5\u65AD\u9762\u4F18\u5316\u5931\u8D25!");
        refreshData();
    }

    // ---- Limit settings for vertical optimization ----
    private void onLimitSettings() {
        cancelInteractionMode();

        // Get current parameters
        SurveyParaVert para = fitting.GetParaVert();
        if (para == null) {
            JOptionPane.showMessageDialog(null, "\u83B7\u53D6\u7EB5\u65AD\u9762\u53C2\u6570\u5931\u8D25!",
                "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
            return;
        }

        double currentUpLimit = para.getM_V_upLimit();
        double currentDownLimit = para.getM_V_downLimit();

        // Build a simple input panel
        JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8));
        panel.add(new JLabel("\u4E0A\u9650\u503C (mm):"));
        JTextField upField = new JTextField(String.format("%.1f", currentUpLimit * 1000), 10);
        panel.add(upField);
        panel.add(new JLabel("\u4E0B\u9650\u503C (mm):"));
        JTextField downField = new JTextField(String.format("%.1f", currentDownLimit * 1000), 10);
        panel.add(downField);

        int result = JOptionPane.showConfirmDialog(null, panel,
            "\u9650\u503C\u8BBE\u7F6E", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        try {
            double newUpLimit = Double.parseDouble(upField.getText().trim()) / 1000.0;
            double newDownLimit = Double.parseDouble(downField.getText().trim()) / 1000.0;

            para.setM_V_upLimit(newUpLimit);
            para.setM_V_downLimit(newDownLimit);
            fitting.SetParaVert(para);

            System.out.println("\u9650\u503C\u8BBE\u7F6E\u5DF2\u66F4\u65B0: \u4E0A\u9650="
                + String.format("%.1f", newUpLimit * 1000) + "mm, \u4E0B\u9650="
                + String.format("%.1f", newDownLimit * 1000) + "mm");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "\u8BF7\u8F93\u5165\u6709\u6548\u7684\u6570\u503C!",
                "\u9519\u8BEF", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ================================================================
    //  Internal data struct
    // ================================================================

    private static final class ProfilePoint {
        final double mileage;
        final double height;
        ProfilePoint(double mileage, double height) {
            this.mileage = mileage;
            this.height = height;
        }
    }

    // ================================================================
    //  Canvas
    // ================================================================

    private final class ProfileCanvas extends Canvas {

        private static final int HIT_RADIUS = 12; // pixels for GCP click detection

        private ProfileCanvas() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    handleCanvasClick(e.getX(), e.getY());
                }
            });
        }

        /** Find nearest GCP within hit radius; update selectedGCPIndex. */
        private void handleCanvasClick(int sx, int sy) {
            // Convert screen coordinates to data coordinates
            double clickedMileage = screenXToMileage(sx);
            double clickedHeight = screenYToHeight(sy);

            // ---- Interactive mode handling ----
            if (interactionMode == InteractionMode.INSERT_GCP) {
                // Insert a new GCP at the clicked location
                double height = fitting.GetAlignment().GetDesignHeightByRM(clickedMileage);
                if (height >= 1e9) height = clickedHeight;

                ALGradeChangePoint gcp = new ALGradeChangePoint(clickedMileage, height, 0);
                boolean ok = fitting.InsertGCP(gcp, false, 0);
                System.out.println(ok
                    ? "\u63D2\u5165\u53D8\u8D2F\u70B9\u6210\u529F! \u4F4D\u7F6E: " + String.format("%.3f", clickedMileage)
                    : "\u63D2\u5165\u53D8\u8D2F\u70B9\u5931\u8D25!");
                if (ok) selectedGCPIndex = getGCPCount() - 1;
                interactionMode = InteractionMode.NONE;
                statusMessage = "";
                setCursor(Cursor.getDefaultCursor());
                refreshData();
                return;
            }

            if (interactionMode == InteractionMode.MOVE_GCP) {
                if (moveSourceIndex < 0) {
                    // First click: select source GCP
                    int hitIdx = findNearestGCP(sx, sy);
                    if (hitIdx >= 0) {
                        moveSourceIndex = hitIdx;
                        selectedGCPIndex = hitIdx;
                        statusMessage = "\u8BF7\u70B9\u51FB\u76EE\u6807\u4F4D\u7F6E";
                        repaint();
                    } else {
                        JOptionPane.showMessageDialog(null,
                            "\u8BF7\u70B9\u51FB\u9009\u62E9\u8981\u79FB\u52A8\u7684\u53D8\u8D2F\u70B9",
                            "\u63D0\u793A", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    // Second click: move GCP to target location
                    boolean ok = fitting.MoveGCP(clickedMileage, clickedHeight, moveSourceIndex, false, 0);
                    System.out.println(ok
                        ? "\u79FB\u52A8\u53D8\u8D2F\u70B9\u6210\u529F! \u65B0\u4F4D\u7F6E: " + String.format("%.3f", clickedMileage)
                        : "\u79FB\u52A8\u53D8\u8D2F\u70B9\u5931\u8D25!");
                    interactionMode = InteractionMode.NONE;
                    moveSourceIndex = -1;
                    statusMessage = "";
                    setCursor(Cursor.getDefaultCursor());
                    refreshData();
                }
                return;
            }

            // ---- Normal mode: select GCP ----
            int bestIdx = findNearestGCP(sx, sy);
            selectedGCPIndex = bestIdx;
            System.out.println(bestIdx >= 0
                ? "\u9009\u4E2D\u53D8\u8D2F\u70B9 #" + bestIdx + " \u91CC\u7A0B=" + gradeChangePts.get(bestIdx).mileage
                : "\u53D6\u6D88\u9009\u62E9");
            repaint();
        }

        /** Find the nearest GCP index within hit radius, or -1 */
        private int findNearestGCP(int sx, int sy) {
            if (gradeChangePts.isEmpty()) return -1;
            double bestDist = HIT_RADIUS;
            int bestIdx = -1;
            for (int i = 0; i < gradeChangePts.size(); i++) {
                ProfilePoint pt = gradeChangePts.get(i);
                int cx = (int) Math.round(plotLeft + (pt.mileage - plotMinX) * xScale);
                int cy = (int) Math.round(plotTop + plotHeight - (pt.height - plotMinY) * yScale);
                double dist = Math.hypot(sx - cx, sy - cy);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }

        @Override
        public void paint(Graphics g) {
            if (designLine.isEmpty() && gradeChangePts.isEmpty() && surveyPts.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Color.RED);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                String msg = "\u65E0\u7EB5\u65AD\u9762\u6570\u636E\u53EF\u663E\u793A\uFF0C\u8BF7\u5148\u5BFC\u5165\u6D4B\u91CF\u6570\u636E\u5E76\u5B8C\u6210\u7EB5\u65AD\u9762\u62DF\u5408";
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = getHeight() / 2;
                g2.drawString(msg, x, y);
                return;
            }

            int width = getWidth();
            int height = getHeight();

            // ---- Compute data bounds ----
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

            for (ProfilePoint pt : designLine) {
                minX = Math.min(minX, pt.mileage); maxX = Math.max(maxX, pt.mileage);
                minY = Math.min(minY, pt.height); maxY = Math.max(maxY, pt.height);
            }
            for (ProfilePoint pt : gradeChangePts) {
                minX = Math.min(minX, pt.mileage); maxX = Math.max(maxX, pt.mileage);
                minY = Math.min(minY, pt.height); maxY = Math.max(maxY, pt.height);
            }
            for (ProfilePoint pt : surveyPts) {
                minX = Math.min(minX, pt.mileage); maxX = Math.max(maxX, pt.mileage);
                minY = Math.min(minY, pt.height); maxY = Math.max(maxY, pt.height);
            }

            double xRange = maxX - minX, yRange = maxY - minY;
            if (xRange < 1) xRange = 1;
            if (yRange < 1) yRange = 1;
            minX -= xRange * 0.02;
            maxX += xRange * 0.02;
            minY -= yRange * 0.05;
            maxY += yRange * 0.05;

            // Store for mouse interaction
            plotMinX = minX; plotMaxX = maxX; plotMinY = minY; plotMaxY = maxY;
            plotLeft = padding; plotTop = padding;
            plotWidth = width - padding * 2; plotHeight = height - padding * 2;
            xScale = plotWidth / (maxX - minX);
            yScale = plotHeight / (maxY - minY);

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Background
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);

            // Plot area
            g2.setColor(new Color(240, 240, 240));
            g2.fillRect(plotLeft, plotTop, plotWidth, plotHeight);
            g2.setColor(Color.BLACK);
            g2.drawRect(plotLeft, plotTop, plotWidth, plotHeight);

            // Grid lines
            int tickCount = 10;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(new Color(200, 200, 200));
            for (int i = 1; i < tickCount; i++) {
                double ratio = i / (double) tickCount;
                int x = (int) Math.round(plotLeft + ratio * plotWidth);
                g2.drawLine(x, plotTop, x, plotTop + plotHeight);
                int y = (int) Math.round(plotTop + plotHeight - ratio * plotHeight);
                g2.drawLine(plotLeft, y, plotLeft + plotWidth, y);
            }

            // Axis labels
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int i = 0; i <= tickCount; i++) {
                double ratio = i / (double) tickCount;
                int x = (int) Math.round(plotLeft + ratio * plotWidth);
                double xVal = minX + ratio * (maxX - minX);
                String xLabel = formatMileage(xVal);
                g2.drawLine(x, plotTop + plotHeight, x, plotTop + plotHeight + 5);
                int xlw = g2.getFontMetrics().stringWidth(xLabel);
                g2.drawString(xLabel, x - xlw / 2, plotTop + plotHeight + 20);

                int y = (int) Math.round(plotTop + plotHeight - ratio * plotHeight);
                double yVal = minY + ratio * (maxY - minY);
                g2.drawLine(plotLeft - 5, y, plotLeft, y);
                String yLabel = String.format("%.2f", yVal);
                g2.drawString(yLabel, plotLeft - g2.getFontMetrics().stringWidth(yLabel) - 8, y + 4);
            }

            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.drawString("\u91CC\u7A0B (m)", width / 2 - 30, height - 8);
            g2.translate(15, height / 2 + 30);
            g2.rotate(-Math.PI / 2);
            g2.drawString("\u9AD8\u7A0B (m)", 0, 0);
            g2.rotate(Math.PI / 2);
            g2.translate(-15, -(height / 2 + 30));

            // ---- Draw design grade line ----
            if (designLine.size() >= 2) {
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(new Color(0, 100, 200));
                int[] xs = new int[designLine.size()];
                int[] ys = new int[designLine.size()];
                for (int i = 0; i < designLine.size(); i++) {
                    ProfilePoint pt = designLine.get(i);
                    xs[i] = (int) Math.round(plotLeft + (pt.mileage - minX) * xScale);
                    ys[i] = (int) Math.round(plotTop + plotHeight - (pt.height - minY) * yScale);
                }
                g2.drawPolyline(xs, ys, designLine.size());
            }

            // ---- Draw grade change points ----
            for (int i = 0; i < gradeChangePts.size(); i++) {
                ProfilePoint pt = gradeChangePts.get(i);
                int cx = (int) Math.round(plotLeft + (pt.mileage - minX) * xScale);
                int cy = (int) Math.round(plotTop + plotHeight - (pt.height - minY) * yScale);

                boolean selected = (i == selectedGCPIndex);
                if (selected) {
                    // Highlight ring
                    g2.setColor(new Color(255, 200, 200, 120));
                    g2.fillOval(cx - 10, cy - 10, 20, 20);
                    g2.setColor(Color.RED);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(cx - 10, cy - 10, 20, 20);
                }

                g2.setStroke(new BasicStroke(2f));
                g2.setColor(selected ? Color.RED : new Color(0, 150, 0));
                int[] xDi = {cx, cx + 6, cx, cx - 6};
                int[] yDi = {cy - 6, cy, cy + 6, cy};
                g2.fillPolygon(xDi, yDi, 4);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(Color.DARK_GRAY);
                String label = formatMileage(pt.mileage);
                if (selected) {
                    label += " #" + i + " H=" + String.format("%.3f", pt.height);
                }
                g2.drawString(label, cx + 8, cy - 4);
            }

            // ---- Draw survey data points ----
            if (!surveyPts.isEmpty()) {
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(220, 50, 50));
                for (ProfilePoint pt : surveyPts) {
                    int cx = (int) Math.round(plotLeft + (pt.mileage - minX) * xScale);
                    int cy = (int) Math.round(plotTop + plotHeight - (pt.height - minY) * yScale);
                    g2.drawOval(cx - 2, cy - 2, 4, 4);
                }
            }

            // ---- Legend ----
            drawLegend(g2, plotLeft + 10, plotTop + 15);

            // ---- Interactive mode status message ----
            if (interactionMode != InteractionMode.NONE && !statusMessage.isEmpty()) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                g2.setColor(new Color(0, 0, 0, 180));
                FontMetrics fm = g2.getFontMetrics();
                int sw = fm.stringWidth(statusMessage) + 20;
                int sh = fm.getHeight() + 10;
                int msgX = (width - sw) / 2;
                int msgY = plotTop + plotHeight - sh - 10;
                g2.setColor(new Color(255, 255, 200, 220));
                g2.fillRect(msgX, msgY, sw, sh);
                g2.setColor(new Color(180, 80, 0));
                g2.drawRect(msgX, msgY, sw, sh);
                g2.setColor(Color.BLACK);
                g2.drawString(statusMessage, msgX + 10, msgY + sh - 6);
            }
        }

        private String formatMileage(double v) {
            if (v >= 100000) return String.format("%.0f", v);
            else if (v >= 10000) return String.format("%.1f", v);
            else return String.format("%.2f", v);
        }

        private void drawLegend(Graphics2D g2, int x, int y) {
            int bw = 170, lh = 18, dr = 4;
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillRect(x, y, bw, lh * 4 + 10);
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(x, y, bw, lh * 4 + 10);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));

            g2.setColor(new Color(0, 100, 200));
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawLine(x + 5, y + 5 + lh / 2, x + 30, y + 5 + lh / 2);
            g2.setColor(Color.BLACK);
            g2.drawString("Grade Line", x + 35, y + 5 + lh);

            g2.setColor(new Color(0, 150, 0));
            g2.fillPolygon(new int[]{x + 5 + dr, x + 5 + dr * 2, x + 5 + dr, x + 5},
                new int[]{y + 5 + lh * 2 + dr, y + 5 + lh * 2 + dr * 2, y + 5 + lh * 2 + dr * 3, y + 5 + lh * 2 + dr * 2}, 4);
            g2.setColor(Color.BLACK);
            g2.drawString("GCP", x + 35, y + 5 + lh * 2);

            g2.setColor(new Color(220, 50, 50));
            g2.drawOval(x + 5 + dr - 2, y + 5 + lh * 3 + dr - 2, 4, 4);
            g2.setColor(Color.BLACK);
            g2.drawString("Survey Pt", x + 35, y + 5 + lh * 3);

            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x + 5 + dr - 4, y + 5 + lh * 4 + dr - 4, 8, 8);
            g2.setColor(Color.BLACK);
            g2.drawString("Selected GCP", x + 35, y + 5 + lh * 4);
        }
    }
}
