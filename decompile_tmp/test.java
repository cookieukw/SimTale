import java.lang.reflect.Method;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;

public class test {
    public static void main(String[] args) {
        for (Method m : InteractiveCustomUIPage.class.getMethods()) {
            if (m.getName().toLowerCase().contains("update")) {
                System.out.println(m);
            }
        }
    }
}
