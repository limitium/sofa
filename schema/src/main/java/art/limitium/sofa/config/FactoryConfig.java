package art.limitium.sofa.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FactoryConfig {
    public List<String> schemas;
    public LinkedHashMap<String, String> values;
    public List<GeneratorConfig> generators;
    /** Optional list of fully-qualified SofaPlugin implementation class names. */
    public List<String> plugins;
    /**
     * Optional list of {@code groupId:artifactId} coordinates whose generated code is reused rather
     * than regenerated. All or nothing per library: every schema the library declares is loaded.
     */
    public List<String> imports;
    /** Optional manifest to publish, so downstream modules can import this one. */
    public ManifestConfig manifest;
}
