import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;

import AlignmentCore.*;


public class DemoFrame extends JFrame {
    public SurveyDatas mySurveyDatas;
    public SingleAlignmentBase mySingleAlignment;
    public  AlignmentFitting myAlignmentFitting;
    private AlignmentFittingUndoManager undoManager;

    public DemoFrame() {
        JButton btn1 = new JButton("导入测量数据");
        btn1.addActionListener(e -> handleBtn1Click());

        JButton btn2 = new JButton("夹直线拟合");
        btn2.addActionListener(e -> handleBtn2Click());

        JButton btn3 = new JButton("曲线参数匹配");
        btn3.addActionListener(e -> handleBtn3Click());

        JButton btnComCurve = new JButton("复曲线拟合");
        btnComCurve.addActionListener(e -> handleBtnComCurveClick());

        JButton btn4 = new JButton("输出平面交点");
        btn4.addActionListener(e -> handleBtn4Click());

        JButton btn5 = new JButton("输出平面偏差量");
        btn5.addActionListener(e -> handleBtn5Click());

        JButton btn6 = new JButton("纵断面拟合");
        btn6.addActionListener(e -> handleBtn6Click());

        JButton btn7 = new JButton("输出坡长坡率");
        btn7.addActionListener(e -> handleBtn7Click());

        JButton btn8 = new JButton("输出高程偏差量");
        btn8.addActionListener(e -> handleBtn8Click());

        JButton btn9 = new JButton("导出线形文件");
        btn9.addActionListener(e -> handleBtn9Click());

        JButton btn10 = new JButton("导入线形并计算偏差量测试");
        btn10.addActionListener(e -> handleBtn10Click());

        JButton btnOpt = new JButton("轨道优化演示");
        btnOpt.addActionListener(e -> new OptimizationDemoFrame().setVisible(true));

        JButton btnUndo = new JButton("Undo");
        btnUndo.addActionListener(e -> handleUndoClick());

        JButton btnRedo = new JButton("Redo");
        btnRedo.addActionListener(e -> handleRedoClick());

        JPanel panel = new JPanel();
        panel.add(btn1);
        panel.add(btn2);
        panel.add(btn3);
        panel.add(btnComCurve);
        panel.add(btn4);
        panel.add(btn5);
        panel.add(btn6);
        panel.add(btn7);
        panel.add(btn8);
        panel.add(btn9);
        panel.add(btn10);
        panel.add(btnOpt);
        panel.add(btnUndo);
        panel.add(btnRedo);
        

        add(panel);
        setSize(420, 260);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        /// 初始化数据
        mySurveyDatas = new SurveyDatas();
        mySingleAlignment = new SingleAlignmentBase();
        myAlignmentFitting=new AlignmentFitting(mySingleAlignment,mySurveyDatas);

        undoManager = new AlignmentFittingUndoManager();
        undoManager.BindFitting(myAlignmentFitting);
        undoManager.SaveSnapshot("init");
    }


    //Undo和Redo的基础
    //所有跟AlignmentFitting相关的操作需要Redo和Undo，需要提前调用
    private void saveSnapshot(String description) {
        if (undoManager == null) return;
        try {
            undoManager.SaveSnapshot(description);
        } catch (Throwable t) {
            // ignore snapshot errors (native side may be unavailable)
        }
    }

    private void handleUndoClick() {
        if (undoManager == null) {
            System.out.println("UndoManager 未初始化!");
            return;
        }
        if (!undoManager.CanUndo()) {
            System.out.println("没有可撤销的操作!");
            //添加UI及数据库的操作
            return;
        }
        boolean ok = undoManager.Undo();
        System.out.println(ok ? "Undo 成功!" : "Undo 失败!");
    }

    private void handleRedoClick() {
        if (undoManager == null) {
            System.out.println("UndoManager 未初始化!");
            return;
        }
        if (!undoManager.CanRedo()) {
            System.out.println("没有可重做的操作!");
            //添加UI及数据库的操作
            return;
        }
        boolean ok = undoManager.Redo();
        System.out.println(ok ? "Redo 成功!" : "Redo 失败!");
    }

    private void handleBtnComCurveClick() {
        if (mySurveyDatas == null || mySurveyDatas.getM_SurveyImfos() == null || mySurveyDatas.getM_SurveyImfos().size() < 3) {
            System.out.println("请先导入测量数据（至少3点）!");
            return;
        }
        if (myAlignmentFitting != null) {
            new ComCurveFitDialog(this, myAlignmentFitting, undoManager).setVisible(true);
            return;
        }

        new ComCurveFitDialog(this, mySurveyDatas).setVisible(true);
    }

    // 自定义的按钮1响应函数
    private void handleBtn1Click() {
       // JOptionPane.showMessageDialog(this, "按钮1自定义响应");
        String filePath =openFileDialog(this);
        if (filePath == null) {
            System.out.println("未打开文件!\n");
            return;
        }
        //导入数据前使用saveSnapeshot保存当前状态。
        saveSnapshot("导入测量数据");
        SurveyVector newDatas = readCsv(filePath);
        mySurveyDatas.setM_SurveyImfos(newDatas);
        mySurveyDatas.ReCalGMileage();      //加这一句重新计算里程
        System.out.println("打开成功!\n");
        // 这里可以写你自己的业务逻辑
    }

    // 自定义的按钮2响应函数
    private void handleBtn2Click() {
        //System.out.println("按钮2自定义响应");
        // 夹直线拟合前可以保存当前状态
        saveSnapshot("夹直线拟合");
        if(!myAlignmentFitting.FittingHori_Init(true))         //夹直线定位
        {
            System.out.println("夹直线定位失败!");
            return;
        }
        myAlignmentFitting.CalHoriMargin();            //计算偏差值
        System.out.println("夹直线定位成功!");
        InterPtArr interArr = myAlignmentFitting.GetAlignment().GetIntersectionsPts();        //获取交点数据

        for (ALIntersectionPoint point : interArr)
        {
           // point.getM_se
            // 打印交点信息
            System.out.println("X:"+point.getM_Point().getX()+"\tY:"+point.getM_Point().getY()+"\nRadius:"+point.getM_Radius()+"\tPreL:"+point.getM_PreLen()+"\tSubL:"+point.getM_SubLen());
        }
         System.out.println("夹直线定位成功!");
    }

    private void handleBtn3Click() {
        //System.out.println("按钮2自定义响应");
        // 保存当前状态
        saveSnapshot("曲线参数匹配");
        if (!myAlignmentFitting.FittingHori_BestFit())          //曲线参数匹配
        {
            System.out.println("曲线参数匹配失败!");
            return;
        }
        myAlignmentFitting.CalHoriMargin();            //计算偏差值
        System.out.println("曲线参数匹配成功!");
    }

    private void handleBtn4Click() {
        //System.out.println("按钮2自定义响应");
        // 这里可以写你自己的业务逻辑
        InterPtArr interArr = mySingleAlignment.GetIntersectionsPts();        //获取交点数据

        for (ALIntersectionPoint point : interArr)
        {
           // point.getM_se
            // 打印交点信息
            System.out.println("X:"+point.getM_Point().getX()+"\tY:"+point.getM_Point().getY()+"\nRadius:"+point.getM_Radius()+"\tPreL:"+point.getM_PreLen()+"\tSubL:"+point.getM_SubLen());
        }
    }


    private void handleBtn5Click() {
        //System.out.println("按钮2自定义响应");
        // 这里可以写你自己的业务逻辑
        SurveyVector newDatas = mySurveyDatas.getM_SurveyImfos();
        for (SurveyImfo data : newDatas) {
            System.out.println("里程:"+data.getM_mileage()+"\t平面偏差量:"+data.getM_H_margin()*1000+"\t线形:"+data.getM_H_type()+"\t方向:"+data.getM_Direction());
        }
        //打印结果

    }


    private void handleBtn6Click() {
        //System.out.println("按钮2自定义响应");
        // 保存当前状态
        saveSnapshot("纵断面拟合");
        if(!myAlignmentFitting.FittingVert_Init())
        {
            System.out.println("纵断面拟合失败!");
            return;
        }
        myAlignmentFitting.CalVertMargin();
        System.out.println("纵断面拟合成功!");

    }

    private void handleBtn7Click() {
        //System.out.println("按钮2自定义响应");
        // 这里可以写你自己的业务逻辑
        GradeChangePointArr gcps =  mySingleAlignment.GetGradeChangePoints();
        for(ALGradeChangePoint point : gcps)
        {
            System.out.println("里程:"+point.getM_RealMilage()+"\t高程:"+point.getM_Height()+"\t坡长:"+point.getM_SlopeLength()+"\t坡率:"+point.getM_Gradient()*1000
                    +"\t竖曲线半径:"+point.getM_Radius()+"\t是否竖缓:"+point.getM_bOverlaped());
        }
        
        Double dHeight=mySingleAlignment.GetDesignHeightByRM( 138000.0);
        if(dHeight<1e9)
        {
            System.out.println("100米处的高程:" + dHeight);
        }
        else
        {
            System.out.println("获取100米处的高程失败!");
        }       

    }


    private void handleBtn8Click() {
       // System.out.println("按钮2自定义响应");
        // 这里可以写你自己的业务逻辑
        SurveyVector newDatas= myAlignmentFitting.GetSurveyDatas().getM_SurveyImfos();
        SurveyVector oldDatas=mySurveyDatas.getM_SurveyImfos();
        if(newDatas.size()!=oldDatas.size())
        {
            System.out.println("新旧数据点数量不一致!");
            return;
        }
        int nCount=newDatas.size();
        for (SurveyImfo data : newDatas) {
            System.out.println("里程:"+data.getM_mileage()+"\t高程偏差量:"+data.getM_V_margin()*1000+"\t线形:"+data.getM_V_type()+"\t设计高程:"+
            data.getM_V_Height_Design());
        }

    }

    
    private void handleBtn9Click() {
        //System.out.println("按钮2自定义响应");
        // 这里可以写你自己的业务逻辑
         String filePath =saveFileDialog(this);
        if (filePath == null) {
            System.out.println("未打开文件!\n");
            return;
        }
        IntArr inArr=new IntArr();
        if(!mySingleAlignment.ExportGeoData(filePath, inArr,false)) {
            System.out.println("导出线形文件失败!");
            return;
        }
    }


      private void handleBtn10Click() {
        //System.out.println("按钮2自定义响应");
        // 这里可以写你自己的业务逻辑
        saveSnapshot("导入线形并计算偏差量测试");
        InterPtArr interPts= myAlignmentFitting.GetAlignment().GetIntersectionsPts();
        GradeChangePointArr gcps =  mySingleAlignment.GetGradeChangePoints();
        ChainArr chainArr=mySingleAlignment.GetChains();
        // SurveyVector datas = new SurveyVector();
        // for(SurveyImfo data: mySurveyDatas.getM_SurveyImfos())
        // {           
        //     datas.add(new SurveyImfo(data.getM_surveyPoint(),data.getM_mileage()));
        // }
       
        for(ALGradeChangePoint gcp:gcps)
        {
           System.out.println("竖曲线点里程:"+gcp.getM_RealMilage()+"\t高程:"+gcp.getM_Height()+"\t坡长:"+gcp.getM_SlopeLength()+"\t坡率:"+gcp.getM_Gradient()*1000
                    +"\t竖曲线半径:"+gcp.getM_Radius()+"\t是否竖缓:"+gcp.getM_bOverlaped());
        }
        // mySingleAlignment=new SingleAlignmentBase();
        // mySurveyDatas=new SurveyDatas();
        // mySurveyDatas.setM_SurveyImfos(datas);
        mySingleAlignment.CopyFrom(myAlignmentFitting.GetAlignment());
        SurveyVector datas=mySurveyDatas.getM_SurveyImfos();
        for(int i=0;i<100;i++)
        {
            datas.remove(0);
        }
        int nNum=datas.size();
        mySurveyDatas.setM_SurveyImfos(datas);
        myAlignmentFitting.SetSurveyDatas(mySurveyDatas);
        // myAlignmentFitting=new AlignmentFitting(mySingleAlignment,mySurveyDatas);
        if(!myAlignmentFitting.CalHoriMargin())
        {
            System.out.println("计算平面偏差失败!");
            return;
        }
        if(!myAlignmentFitting.CalVertMargin(true))
        {
            System.out.println("计算高程偏差失败!");
            return;
        }
        //  for (SurveyImfo data : mySurveyDatas.getM_SurveyImfos()) {
        //    data.setM_V_margin(0);;
        // }
        System.out.println("导入线形文件成功!");
    }

    public String openFileDialog(Frame parent) {
        FileDialog fileDialog = new FileDialog(parent, "选择文件", FileDialog.LOAD);
        fileDialog.setVisible(true);
        String dir = fileDialog.getDirectory();
        String file = fileDialog.getFile();
        if (file != null) {
            return new File(dir, file).getAbsolutePath();
        }
        return null;
    }

    public String saveFileDialog(Frame parent) {
    FileDialog fileDialog = new FileDialog(parent, "保存文件", FileDialog.SAVE);
    fileDialog.setVisible(true);
    String dir = fileDialog.getDirectory();
    String file = fileDialog.getFile();
    if (file != null) {
        return new File(dir, file).getAbsolutePath();
    }
    return null;
    }


    public SurveyVector readCsv(String filePath) {
        SurveyVector datas = new SurveyVector();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "GBK"))) {
            String line;
            int lineNum = 0;

            while ((line = br.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                // 替换中文逗号 / 兼容 Excel 导出
                line = line.replace("，", ",");

                // -1: 保留空字符串，避免列数被吞
                String[] columns = line.split(",", -1);

                Double[] nums = new Double[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    String c = columns[i] == null ? "" : columns[i].trim();
                    if (c.isEmpty()) {
                        nums[i] = null;
                        continue;
                    }
                    try {
                        nums[i] = Double.parseDouble(c);
                    } catch (NumberFormatException ex) {
                        nums[i] = null;
                    }
                }

                int mileageIdx = -1;
                int xIdx = -1;
                int yIdx = -1;
                int zIdx = -1;

                // 常见格式适配：
                // A) 里程, ?, X, Y, Z
                if (columns.length >= 5 && nums[0] != null && nums[2] != null && nums[3] != null && nums[4] != null) {
                    mileageIdx = 0;
                    xIdx = 2;
                    yIdx = 3;
                    zIdx = 4;
                }
                // B) 里程, X, Y, Z
                else if (columns.length >= 4 && nums[0] != null && nums[1] != null && nums[2] != null && nums[3] != null) {
                    mileageIdx = 0;
                    xIdx = 1;
                    yIdx = 2;
                    zIdx = 3;
                }
                // C) 序号, 里程, X, Y, Z
                else if (columns.length >= 5 && nums[1] != null && nums[2] != null && nums[3] != null && nums[4] != null) {
                    mileageIdx = 1;
                    xIdx = 2;
                    yIdx = 3;
                    zIdx = 4;
                }
                // D) 里程, X, Y
                else if (columns.length >= 3 && nums[0] != null && nums[1] != null && nums[2] != null) {
                    mileageIdx = 0;
                    xIdx = 1;
                    yIdx = 2;
                    zIdx = -1;
                }
                // E) 序号, 里程, X, Y
                else if (columns.length >= 4 && nums[1] != null && nums[2] != null && nums[3] != null) {
                    mileageIdx = 1;
                    xIdx = 2;
                    yIdx = 3;
                    zIdx = -1;
                }

                if (mileageIdx < 0) {
                    // 可能是表头或不支持的格式，前几行打印一下便于排查
                    if (lineNum <= 5) {
                        System.out.println("跳过第 " + lineNum + " 行(可能是表头/格式不匹配): " + line);
                    }
                    continue;
                }

                double mileage = nums[mileageIdx];
                double x = nums[xIdx];
                double y = nums[yIdx];
                double z = (zIdx >= 0 && zIdx < nums.length && nums[zIdx] != null) ? nums[zIdx] : 0.0;

                AL_Point3d pt = new AL_Point3d(x, y, z);
                datas.add(new SurveyImfo(pt, mileage));
            }

            System.out.println("读取完成，共读取 " + datas.size() + " 个点。");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datas;
    }



}
