import AlOpter.AIOpter;
import AlOpter.CommonConstrain;
import AlOpter.OptData;
import AlOpter.OptDataVector;
import AlOpter.OpterProcesser;
import AlOpter.Splineline;

import java.io.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class OptimizationDemoFrame extends Frame {

    public OptimizationDemoFrame() {
        super("轨道优化演示");
        setSize(900, 700);
        setLayout(new BorderLayout());

        Panel toolbar = new Panel(new FlowLayout(FlowLayout.LEFT));
        Button runBtn = new Button("选择文件并运行");
        Button customBtn = new Button("初始化样条曲线");
        Button exitBtn = new Button("关闭");
        toolbar.add(runBtn);
        toolbar.add(customBtn);
        toolbar.add(exitBtn);
        add(toolbar, BorderLayout.NORTH);

        PlotCanvas canvas = new PlotCanvas();
        add(canvas, BorderLayout.CENTER);

        runBtn.addActionListener(e -> new Thread(() -> runOptimization(this, canvas)).start());
        customBtn.addActionListener(e -> onCustomButtonClicked(this, canvas));

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

    // 自定义按钮的响应函数：可自由修改其中逻辑
    private static void onCustomButtonClicked(Frame parent, PlotCanvas canvas) {
       String filePath = openFileDialog(parent);
        if (filePath == null) return;
        OptDataVector list = readCsv(filePath);
        //list.remove(list.size());
        Splineline splineline = new Splineline(list);
        splineline.Init(200);     
        OptDataVector pickPoints=splineline.GetAllPickPts();
         OptDataVector optdata = splineline.GetData();
         OptDataVector resList=new OptDataVector();
            for (int i = 0; i < optdata.size(); i++) {
                resList.add(new OptData(optdata.get(i).getX(), optdata.get(i).getY()+list.get(i).getY(),0,0));
            }
        showLinePlot("样条曲线",list,resList,pickPoints);
        OptData insertData = new OptData((pickPoints.get(2).getX() + pickPoints.get(3).getX()) / 2, 10);
        if(!splineline.InsertPickPoint(insertData))
            return;
        pickPoints=splineline.GetAllPickPts();
            optdata = splineline.GetData();
            resList=new OptDataVector();
            for (int i = 0; i < optdata.size(); i++) {
                resList.add(new OptData(optdata.get(i).getX(), optdata.get(i).getY()+list.get(i).getY(),0,0));
            }
        showLinePlot("样条曲线",list,resList,pickPoints);
    }

    private static void runOptimization(Frame parent, PlotCanvas targetCanvas) {
        String filePath = openFileDialog(parent);
        if (filePath == null) return;

        OptDataVector list = readCsv(filePath);
        list.add(new OptData(0, 0));
        CommonConstrain constrain = new CommonConstrain();
        constrain.setM_ShortWaveLength(20);
        constrain.setIsPlane(true);
        constrain.setM_ShortWaveLimit(3);
        constrain.setM_factor(0.01);        //平滑系数；平滑为主取0.01.线形回归为主取1.0
        constrain.setM_SuperSlope(0.5);
        constrain.setIsFixed(true);
        
        OpterProcesser proceser = new OpterProcesser(list, constrain);
        if (!proceser.StartOptPro())
            return;
        OptDataVector listResult = proceser.GetOptResult();         //调整量
        OptDataVector resVector = new OptDataVector();              //优化后的偏差值
        OptDataVector upVector = new OptDataVector();               //优化前的偏差值
        for (int i = 0; i < listResult.size(); i++) {
            upVector.add(new OptData(list.get(i).getX(), list.get(i).getY()));
        }

        ///计算调整前后平顺性的示例代码，以10m弦为例
        OptDataVector smoothBefore = new OptDataVector();              //优化前的10m弦
        //if(!AIOpter.SmoothCal(list,smoothBefore,10))
          //  return; 
         if(!AIOpter.SmoothCal(upVector, smoothBefore, 10))
            return;     
        
        // 计算抬拨道后的轨道偏差值数据
        for (int i = 0; i < listResult.size(); i++) {
            resVector.add(new OptData(listResult.get(i).getX(), list.get(i).getY() + listResult.get(i).getY(),list.get(i).getY_down(),list.get(i).getY_up()));
        }
        //proceser.SmoothCalOpt(smoothBefore, ABORT)
        // 平顺性（10m弦）
        OptDataVector smoothResult = new OptDataVector();                   //优化后的10m弦
        if(!AIOpter.SmoothCal(resVector, smoothResult,10))
            return; 

    // 更新主窗口绘图
    EventQueue.invokeLater(() -> targetCanvas.setSeries(new OptDataVector[]{upVector, resVector}));

        for (int i=0;i<listResult.size();i++) {
            System.out.println("X:"+list.get(i).getX() +"\t偏差值："+list.get(i).getY()+"\t调整量:"+ listResult.get(i).getY() +
                    "\t调整后偏差值:"+resVector.get(i).getY()+"\t调整前10m弦:"+smoothBefore.get(i).getY()+"\t调整后10m弦:"+smoothResult.get(i).getY());
        }
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
        private final Color[] palette = new Color[]{Color.BLUE, Color.RED, Color.GREEN.darker(), Color.ORANGE, Color.MAGENTA};

        private PlotCanvas(OptDataVector[] series) {
            this.series = series;
        }

        private PlotCanvas() {
            this.series = new OptDataVector[0];
        }

        public void setSeries(OptDataVector[] series) {
            this.series = series != null ? series : new OptDataVector[0];
            repaint();
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

            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);

            g2.setColor(Color.LIGHT_GRAY);
            g2.fillRect(padding, padding, width - padding * 2, height - padding * 2);

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

}