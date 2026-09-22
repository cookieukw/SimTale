import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.lang.reflect.Method;
public class test2 {
    public static void main(String[] args) {
        for (Method m : PlayerRef.class.getMethods()) {
            if (m.getName().equals("getPacketHandler")) {
                System.out.println(m.getReturnType().getName());
            }
        }
    }
}
