package art.limitium.sofa;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ResourceUtils {
    public static List<String> listResources(String path) throws IOException {
        List<String> resources = new ArrayList<>();
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        
        ClassLoader classLoader = ResourceUtils.class.getClassLoader();
        URL dirURL = classLoader.getResource(normalizedPath);
        
        if (dirURL != null && dirURL.getProtocol().equals("file")) {
            // Handle filesystem-based resources
            File dir = new File(dirURL.getFile());
            for (File file : dir.listFiles()) {
                if (file.isFile()) {
                    resources.add(path + "/" + file.getName());
                }
            }
        } else if (dirURL != null && dirURL.getProtocol().equals("jar")) {
            // Handle jar-based resources
            String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!"));
            JarFile jar = new JarFile(jarPath);
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(normalizedPath) && !name.endsWith("/")) {
                    resources.add("/" + name);
                }
            }
            jar.close();
        }
        
        return resources;
    }
}