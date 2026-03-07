import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.util.Enumeration;

public class Main {
    public static void main(String[] args) {
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
            if (value instanceof FontUIResource || key.toString().endsWith(".font")) {
                UIManager.put(key, fontRes);
            }
        }
    }
    /// 读取
}
