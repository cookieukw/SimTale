import com.hypixel.hytale.server.core.io.adapter.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
public class test {
    public void foo(PacketHandler handler, PacketWatcher watcher) {
        handler.addWatcher(watcher);
    }
}
