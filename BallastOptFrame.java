import javax.swing.*;


import java.awt.*;
import java.io.File;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;

import AlBallastlessRailOpter.*;

public class BallastOptFrame extends JFrame {
    private RtSurveyPtArr mySurveyDatas;
    private OptConstrain myConstrainH;
    private OptConstrain myConstrainV;       //高程调整量优化的参数
    private OptValArr myMarginOpt;      //优化后的扣件调整量
    private OptValArr myHeightOpt;      //优化后的高程调整量

    private DoubleArr myShortWave_LH;
    private DoubleArr myShortWave_RH;
    private DoubleArr myShortWave_LV;
    private DoubleArr myShortWave_RV;
    public BallastOptFrame() {
        JButton btn1 = new JButton("导入偏差值数据");
        btn1.addActionListener(e -> handleBtn1Click());

        JButton btn2 = new JButton("平面调整量优化");
        btn2.addActionListener(e -> handleBtn2Click());

        JButton btn3 = new JButton("高程调整量优化");
        btn3.addActionListener(e -> handleBtn3Click());

        JButton btn4 = new JButton("输出平面调整量");
        btn4.addActionListener(e -> handleBtn4Click());

        JButton btn5 = new JButton("输出高程调整量");
        btn5.addActionListener(e -> handleBtn5Click());

        JButton btn6 = new JButton("导出调整量文件");
        btn6.addActionListener(e -> handleBtn6Click());

        JButton btn7=new JButton("导出长中短波数据");
        btn7.addActionListener(e -> handleBtn7Click());

        JPanel panel = new JPanel();
        panel.add(btn1);
        panel.add(btn2);
        panel.add(btn3);
        panel.add(btn4);
        panel.add(btn5);      
        panel.add(btn6); 
        panel.add(btn7);
        //panel.add(btn8);

        add(panel);
        setSize(300, 200);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        /// 初始化数据
        myConstrainH =new OptConstrain();       //平面调整量优化的参数
        myConstrainV = new OptConstrain();
        myConstrainV.setUnitL_l(-2.0);         //左轨下限
        myConstrainV.setUnitL_u(8.0);   //左轨上限
        myConstrainV.setUnitR_l(-2.0);         //右轨下限
        myConstrainV.setUnitR_u(8.0);   //右轨上限
        myConstrainV.setMarginL(0.25);  //水平值
    }

    // 自定义的按钮1响应函数
    private void handleBtn1Click() {
       // JOptionPane.showMessageDialog(this, "按钮1自定义响应");
        String filePath =openFileDialog(this);
        if (filePath == null) {
            System.out.println("未打开文件!\n");
            return;
        }
        mySurveyDatas = readCsv(filePath);
        //打印读取的数据       
        System.out.println("打开成功!\n");
        // 这里可以写你自己的业务逻辑
    }

    // 自定义的按钮2响应函数
    private void handleBtn2Click() {
    for (RailTrackSurveyPoint pt : mySurveyDatas) {
        System.out.printf("里程: %.2f, 左轨横偏: %.2f, 右轨横偏: %.2f, 左轨竖偏: %.2f, 右轨竖偏: %.2f%n",
        pt.getMileage(), pt.getX_L(), pt.getX_R(), pt.getY_L(), pt.getY_R());
    }
    DoubleArr originL=new DoubleArr();
    DoubleArr originR=new DoubleArr();
    for (int i = 0; i < mySurveyDatas.size(); i++) {
        RailTrackSurveyPoint pt = mySurveyDatas.get(i);
        originL.add(pt.getX_L());
        originR.add(pt.getX_R());
        //这一段采用的统一的上限下限约束
        //也可以类似于精测精捣方案优化一样，给出分段数据进行约束
        mySurveyDatas.get(i).setX_L_Con_l(myConstrainH.getUnitL_l());
        mySurveyDatas.get(i).setX_L_Con_u(myConstrainH.getUnitL_u());
        mySurveyDatas.get(i).setX_R_Con_l(myConstrainH.getUnitR_l());
        mySurveyDatas.get(i).setX_R_Con_u(myConstrainH.getUnitR_u());
    }
     RailAPI HoriApi= new RailAPI(mySurveyDatas, myConstrainH);
     boolean es=false;
     if (mySurveyDatas.size()>11000 && myConstrainH.getBResultLongWave()) 
        es= HoriApi.StartOptHoriForLong();
     else     
        es= HoriApi.StartOptHori();
     
     if(!es) {
        System.out.println("平面调整量优化失败!\n");
        return;
     }
    myMarginOpt=HoriApi.GetResult();
    
    double classVal=0.5;            //该值为扣件级差 一般取0.5或者1.0  需要系统存储
     if (myConstrainH.getIsDifferential()) {
        RailAPI._RoundForResultHCompare(originL,originR,myMarginOpt,
        myConstrainH.getGaugeL(),myConstrainH.getGaugeRate(),classVal);
     }
     System.out.println("平面调整量优化完成!\n");        
    }

    private void handleBtn3Click() {
          DoubleArr originL=new DoubleArr();
        DoubleArr originR=new DoubleArr();
        for (int i = 0; i < mySurveyDatas.size(); i++) {
            RailTrackSurveyPoint pt = mySurveyDatas.get(i);
            originL.add(pt.getY_L());
            originR.add(pt.getY_R());
            mySurveyDatas.get(i).setY_L_Con_l(myConstrainV.getUnitL_l());
            mySurveyDatas.get(i).setY_L_Con_u(myConstrainV.getUnitL_u());
            mySurveyDatas.get(i).setY_R_Con_l(myConstrainV.getUnitR_l());
            mySurveyDatas.get(i).setY_R_Con_u(myConstrainV.getUnitR_u());
        }
     RailAPI VertApi= new RailAPI(mySurveyDatas, myConstrainV);
     boolean es=false;
     if (mySurveyDatas.size()>11000 && myConstrainV.getBResultLongWave()) {
        es= VertApi.StartOptVertForLong();
     }
     else
     {
        es= VertApi.StartOptVert();
     }
        if(!es) {
            System.out.println("高程调整量优化失败!\n");
            return;
        }
        myHeightOpt=VertApi.GetResult();
      
        double classVal=0.5;            //该值为扣件级差 一般取0.5或者1.0  需要系统存储
        if (myConstrainV.getIsDifferential()) {
            RailAPI._RoundForResultVCompare(originL,originR,myHeightOpt,
            myConstrainV.getMarginL(),-myConstrainV.getMarginL(),classVal
            );
        }
        System.out.println("高程调整量优化完成!\n");
        
    }

    // Removed duplicate handleBtn3Click() method to resolve compilation error.
    //将扣件调整量输出到文件
    private void handleBtn4Click() {       
        for(OptValueData data : myMarginOpt)
        {
            System.out.println("左轨平面调整量:"+data.getXOpt_L()+"\t右轨平面调整量:"+data.getXOpt_R()+"\n");
        }     
    }

    //将高程调整量输出到文件
    private void handleBtn5Click() {
       for(OptValueData data : myHeightOpt)
       {
           System.out.println("左轨高程调整量:"+data.getYOpt_L()+"\t右轨高程调整量:"+data.getYOpt_R()+"\n");
       }
    }

    private void handleBtn6Click() {

        if(mySurveyDatas.size()!=myHeightOpt.size() || mySurveyDatas.size()!=myMarginOpt.size())
        {
            System.out.println("数据不匹配，无法导出调整量文件!");
            return;
        }
        String filePath = saveFileDialog(this);
        if (filePath == null) {
            System.out.println("未保存文件!\n");
            return;
        }
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            // 这里可以将调整量数据写入文件
            // 示例：使用 PrintWriter 写入数据
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.printf("里程,左轨平面偏差量,右轨平面偏差量,左轨高程偏差量,右轨高程偏差量,左轨平面调整量,右轨平面调整量,左轨高程调整量,右轨高程调整量%n");
                for (int i=0;i<mySurveyDatas.size();i++) {
                    RailTrackSurveyPoint pt = mySurveyDatas.get(i);
                    OptValueData marginOpt = myMarginOpt.get(i);
                    OptValueData heightOpt = myHeightOpt.get(i);
                    writer.printf("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            pt.getMileage(),
                            pt.getX_L(), pt.getX_R(),
                            pt.getY_L(), pt.getY_R(),
                            marginOpt.getXOpt_L(), marginOpt.getXOpt_R(),
                            heightOpt.getYOpt_L(), heightOpt.getYOpt_R());
                }
            }
            System.out.println("调整量已保存到文件: " + filePath);
        } catch (IOException e) {
            System.err.println("保存文件失败: " + e.getMessage());
        }
    }  
    
    
    public void handleBtn7Click() {
        if(mySurveyDatas.size()!=myHeightOpt.size() || mySurveyDatas.size()!=myMarginOpt.size())
        {
            System.out.println("数据不匹配，无法导出长中短波数据!");
            return;
        }
        String filePath = saveFileDialog(this);
        if (filePath == null) {
            System.out.println("未保存文件!\n");
            return;
        }
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            // 这里可以将长中短波数据写入文件
            // 示例：使用 PrintWriter 写入数据
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.printf("里程,LH长波,LH长波O,RH长波,RH长波O,LH中波,LH中波O,RH中波,RH中波O,LH短波,LH短波O,RH短波,RH短波O,LV长波,LV长波O,RV长波,RV长波O,LV中波,LV中波O,RV中波,RV中波O,LV短波,LV短波O,RV短波,RV短波O%n");
                DoubleArr originL_H = new DoubleArr();
                DoubleArr originR_H = new DoubleArr();
                DoubleArr marginL_H = new DoubleArr();
                DoubleArr marginR_H = new DoubleArr();

                DoubleArr originL_V = new DoubleArr();
                DoubleArr originR_V = new DoubleArr();
                DoubleArr marginL_V = new DoubleArr();
                DoubleArr marginR_V = new DoubleArr();

                for (int i = 0; i < mySurveyDatas.size(); i++) {
                    RailTrackSurveyPoint pt = mySurveyDatas.get(i);
                    OptValueData marginOpt = myMarginOpt.get(i);
                    OptValueData heightOpt = myHeightOpt.get(i);
                    originL_H.add(pt.getX_L());
                    originR_H.add(pt.getX_R());
                    marginL_H.add(pt.getX_L()+marginOpt.getXOpt_L());
                    marginR_H.add(pt.getX_R()+marginOpt.getXOpt_R());

                    originL_V.add(pt.getY_L());
                    originR_V.add(pt.getY_R());
                    marginL_V.add(pt.getY_L()+heightOpt.getYOpt_L());
                    marginR_V.add(pt.getY_R()+heightOpt.getYOpt_R());
                };
                DoubleArr shortWaveL_H = new DoubleArr();
                DoubleArr MidWaveL_H = new DoubleArr();
                DoubleArr longWaveL_H=new DoubleArr();
                DoubleArr shortWaveR_H = new DoubleArr();
                DoubleArr MidWaveR_H = new DoubleArr();
                DoubleArr longWaveR_H=new DoubleArr();

                //DoubleArr shortWaveL_H_opt = new DoubleArr();
                DoubleArr MidWaveL_H_opt = new DoubleArr();
                DoubleArr longWaveL_H_opt=new DoubleArr();
                //DoubleArr shortWaveR_H_opt = new DoubleArr();
                DoubleArr MidWaveR_H_opt = new DoubleArr();
                DoubleArr longWaveR_H_opt=new DoubleArr();
                RailAPI.ChordLineFor10(originL_H, shortWaveL_H);
                RailAPI.ChordLineFor30(originL_H, MidWaveL_H);
                RailAPI.ChordLineFor300(originL_H, longWaveL_H);
                RailAPI.ChordLineFor10(originR_H, shortWaveR_H);
                RailAPI.ChordLineFor30(originR_H, MidWaveR_H);
                RailAPI.ChordLineFor300(originR_H, longWaveR_H);

                RailAPI.ChordLineFor10(marginL_H, myShortWave_LH);
                RailAPI.ChordLineFor30(marginL_H, MidWaveL_H_opt);
                RailAPI.ChordLineFor300(marginL_H, longWaveL_H_opt);
                RailAPI.ChordLineFor10(marginR_H, myShortWave_RH);
                RailAPI.ChordLineFor30(marginR_H, MidWaveR_H_opt);
                RailAPI.ChordLineFor300(marginR_H, longWaveR_H_opt);


                DoubleArr shortWaveL_V = new DoubleArr();
                DoubleArr MidWaveL_V = new DoubleArr();
                DoubleArr longWaveL_V=new DoubleArr();
                DoubleArr shortWaveR_V = new DoubleArr();
                DoubleArr MidWaveR_V = new DoubleArr();
                DoubleArr longWaveR_V=new DoubleArr();
               // DoubleArr shortWaveL_V_opt = new DoubleArr();
                DoubleArr MidWaveL_V_opt = new DoubleArr();
                DoubleArr longWaveL_V_opt=new DoubleArr();
               // DoubleArr shortWaveR_V_opt = new DoubleArr();
                DoubleArr MidWaveR_V_opt = new DoubleArr();
                DoubleArr longWaveR_V_opt=new DoubleArr();
                RailAPI.ChordLineFor10(originL_V, shortWaveL_V);
                RailAPI.ChordLineFor30(originL_V, MidWaveL_V);
                RailAPI.ChordLineFor300(originL_V, longWaveL_V);
                RailAPI.ChordLineFor10(originR_V, shortWaveR_V);
                RailAPI.ChordLineFor30(originR_V, MidWaveR_V);
                RailAPI.ChordLineFor300(originR_V, longWaveR_V);

                RailAPI.ChordLineFor10(marginL_V, myShortWave_LV);
                RailAPI.ChordLineFor30(marginL_V, MidWaveL_V_opt);
                RailAPI.ChordLineFor300(marginL_V, longWaveL_V_opt);
                RailAPI.ChordLineFor10(marginR_V, myShortWave_RV);
                RailAPI.ChordLineFor30(marginR_V, MidWaveR_V_opt);
                RailAPI.ChordLineFor300(marginR_V, longWaveR_V_opt);


                for (int i=0;i<mySurveyDatas.size();i++) {
                    RailTrackSurveyPoint pt = mySurveyDatas.get(i);
                    writer.printf("%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            pt.getMileage(),longWaveL_H.get(i), longWaveL_H_opt.get(i),longWaveR_H.get(i), longWaveR_H_opt.get(i),
                            MidWaveL_H.get(i), MidWaveL_H_opt.get(i), MidWaveR_H.get(i), MidWaveR_H_opt.get(i),
                            shortWaveL_H.get(i), myShortWave_LH.get(i), shortWaveR_H.get(i), myShortWave_RH.get(i),
                            longWaveL_V.get(i), longWaveL_V_opt.get(i), longWaveR_V.get(i), longWaveR_V_opt.get(i),
                            MidWaveL_V.get(i), MidWaveL_V_opt.get(i), MidWaveR_V.get(i), MidWaveR_V_opt.get(i),
                            shortWaveL_V.get(i), myShortWave_LV.get(i), shortWaveR_V.get(i), myShortWave_RV.get(i));
                }
            }
            System.out.println("长中短波数据已保存到文件: " + filePath);
        } catch (IOException e) {
            System.err.println("保存文件失败: " + e.getMessage());
        }
    }

    public void handleBtn8Click() {

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


    public RtSurveyPtArr readCsv(String filePath) {
        RtSurveyPtArr datas = new RtSurveyPtArr();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line=br.readLine();          //第一行不要
            while ((line = br.readLine()) != null) {
                // 以逗号拆分
                String[] columns = line.split(",");
                RailTrackSurveyPoint pt=new RailTrackSurveyPoint();
                pt.setMileage(Double.parseDouble(columns[1]));  //里程
                pt.setX_L(Double.parseDouble(columns[2]));  //左轨横偏
                pt.setX_R(Double.parseDouble(columns[3]));  //右轨横偏
                pt.setY_L(Double.parseDouble(columns[4]));  //左轨竖偏
                pt.setY_R(Double.parseDouble(columns[5]));  //右轨竖偏
                datas.add(pt);
            }
        } catch (IOException e) {
            //
        }
        return datas;
    }



}