import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.util.Enumeration;

public class Main {
    public static void main(String[] args) {
        // 预加载所有依赖库，确保顺序正确
        try {
            String libPath = System.getProperty("user.dir") + "/lib/";
            System.out.println("正在加载库文件，路径: " + libPath);
            
            // 按依赖顺序加载
            System.load(libPath + "libColPack.so");
            System.load(libPath + "libadolc.so");
            System.load(libPath + "libipopt.so");
            // System.load(libPath + "libCOptimizer.so"); // 让JNI类自己加载，或者在这里加载也可以
        } catch (UnsatisfiedLinkError e) {
            System.err.println("库加载失败: " + e.getMessage());
            e.printStackTrace();
        }

        // 尝试设置中文字体，解决乱码问题
        initGlobalFont(new Font("WenQuanYi Micro Hei", Font.PLAIN, 12));
        
        new DemoFrame().setVisible(true);
        //new BallastOptFrame().setVisible(true);
    }

    public static void initGlobalFont(Font font) {
        FontUIResource fontRes = new FontUIResource(font);
        for (Enumeration<Object> keys = UIManager.getDefaults().keys(); keys.hasMoreElements(); ) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, fontRes);
            }
        }
    }
    /// 读取
}
