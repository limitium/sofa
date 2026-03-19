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
}
