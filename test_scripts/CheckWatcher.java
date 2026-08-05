import com.hypixel.hytale.server.core.io.PacketHandler;
import java.lang.reflect.Method;
public class CheckWatcher {
    public static void main(String[] args) {
        for (Method m : PacketHandler.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
