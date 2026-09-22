import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;

public class FindClass {
    public static void main(String[] args) throws Exception {
        String cp = System.getProperty("java.class.path");
        for (String path : cp.split(File.pathSeparator)) {
            if (path.endsWith(".jar")) {
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(path))) {
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (entry.getName().endsWith("PacketHandler.class")) {
                            System.out.println(entry.getName());
                        }
                    }
                }
            }
        }
    }
}
