package art.limitium.sofa;

import art.limitium.sofa.plugin.SofaPlugin;
import art.limitium.sofa.plugin.SofaType;
import java.util.List;
import java.util.Map;

/**
 * Test plugin used to verify plugin discovery for filters and type converters.
 */
public class TestSofaPlugin implements SofaPlugin {

    @Override
    public String getId() {
        return "test-sofa-plugin";
    }

    @Override
    public List<SofaFilter> getFilters() {
        return List.of(new ReverseFilter());
    }

    @Override
    public List<SofaTypeConverter<?>> getTypeConverters() {
        return List.of(new KindTypeConverter());
    }

    /**
     * Simple filter that reverses strings.
     */
    static final class ReverseFilter implements SofaFilter {
        @Override
        public String getName() {
            return "reverse";
        }

        @Override
        public Object apply(
                Object input, Map<String, Object> args, Object self, Object context, int lineNumber)
                throws Exception {
            if (input instanceof String s) {
                return new StringBuilder(s).reverse().toString();
            }
            return input;
        }
    }

    /**
     * Type converter that exposes SofaType kind and whether it's numeric.
     */
    static final class KindTypeConverter implements SofaTypeConverter<SofaType> {
        @Override
        public String getName() {
            return "testType";
        }

        @Override
        public Object getType(SofaType type) {
            String suffix = type.isNumeric() ? "-NUM" : "";
            return type.kind().name() + suffix;
        }
    }
}

