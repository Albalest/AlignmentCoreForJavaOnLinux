import AlignmentCore.AlignmentAllJNI;
import AlignmentCore.AL_Vector3d;

public class TestLogic {
    public static void main(String[] args) {
        try {
            System.out.println("=== Starting Logic Test ===");
            
            // Test 1: Get Constant
            System.out.println("Test 1: Getting AL_PI constant...");
            double pi = AlignmentAllJNI.AL_PI_get();
            System.out.println("AL_PI = " + pi);
            if (Math.abs(pi - 3.14159) < 0.0001) {
                System.out.println("PASS: PI value is reasonable.");
            } else {
                System.out.println("FAIL: PI value seems wrong.");
            }

            // Test 2: Create Object and manipulate
            System.out.println("\nTest 2: Creating AL_Vector3d...");
            long vecPtr = AlignmentAllJNI.new_AL_Vector3d__SWIG_1(1.0, 2.0, 3.0);
            System.out.println("Vector created, ptr = " + vecPtr);
            
            // We need to use the Java wrapper class to pass to JNI methods usually, 
            // but here we are calling JNI directly for simplicity or we can use the Java class if available.
            // Let's use the JNI methods directly with the pointer/object.
            // Wait, the JNI methods take (long jarg1, AL_Vector3d jarg1_). 
            // The second argument is the Java object wrapper.
            
            // Let's try using the Java class wrapper instead, it's easier.
            AL_Vector3d vec = new AL_Vector3d(10.0, 20.0, 30.0);
            System.out.println("AL_Vector3d object created: (" + vec.getX() + ", " + vec.getY() + ", " + vec.getZ() + ")");
            
            vec.setX(100.0);
            System.out.println("Modified X to 100.0");
            
            if (vec.getX() == 100.0) {
                System.out.println("PASS: Getter/Setter working.");
            } else {
                System.out.println("FAIL: Getter/Setter failed. Got " + vec.getX());
            }

            System.out.println("\n=== Logic Test Completed Successfully ===");

        } catch (Throwable e) {
            System.err.println("Test Failed with Exception:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
