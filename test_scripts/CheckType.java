import com.hypixel.hytale.server.core.universe.PlayerRef;

public class CheckType {
    public static void printType(PlayerRef p) {
        System.out.println("Type is: " + p.getPacketHandler().getClass().getName());
        System.out.println("Interfaces: ");
        for (Class<?> c : p.getPacketHandler().getClass().getInterfaces()) {
            System.out.println(" - " + c.getName());
        }
    }
}
