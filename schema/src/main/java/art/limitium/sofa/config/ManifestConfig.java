package art.limitium.sofa.config;

public class ManifestConfig {
    /** Maven style {@code groupId:artifactId} this module publishes under */
    public String artifact;
    /** Folder the manifest is written into, templated like any other value */
    public String folder;
    /** Schema resource paths consumers must load, relative to the {@code sofa/} resource root */
    public java.util.List<String> schemas;
}
