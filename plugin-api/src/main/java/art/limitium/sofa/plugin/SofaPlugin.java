package art.limitium.sofa.plugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Generator plugin extension point.
 *
 * <p>Plugins are configured explicitly in def.yaml via fully-qualified class names:
 *
 * <pre>
 * plugins:
 *   - "com.mycompany.sofa.MyPlugin"
 *   - "com.other.Plugin"
 * </pre>
 */
public interface SofaPlugin {
    /**
     * Human-readable/plugin identifier for logging & diagnostics.
     *
     * <p>Defaults to the implementation class name.
     */
    default String getId() {
        return getClass().getName();
    }
    /**
     * Generator-side filter API.
     *
     * <p>The generator will proxy this interface into a Pebble filter at runtime.
     */
    interface SofaFilter {
        /**
         * Filter name used in templates (e.g. {{ value | myFilter }}).
         */
        String getName();

        /**
         * Pebble-style argument names. Return {@code null} if your filter does not define
         * positional/named arguments (mirrors Pebble behavior).
         */
        default List<String> getArgumentNames() {
            return null;
        }

        /**
         * Pebble-style filter application contract.
         *
         * <p>{@code args} is the same map Pebble passes into filters. {@code self} and
         * {@code context} are Pebble runtime objects passed through as-is for simplicity.
         */
        Object apply(Object input, Map<String, Object> args, Object self, Object context, int lineNumber)
                throws Exception;
    }

    /**
     * Generator-side type converter API.
     *
     * <p>The generator will proxy this interface into a SOFA {@code TypeConverter} at runtime.
     */
    interface SofaTypeConverter<T> {
        /**
         * Converter name used as a template filter (e.g. {{ field.type | javaType }}).
         */
        String getName();

        /**
         * Converts a SOFA type into target representation.
         */
        Object getType(SofaType type) throws Exception;
    }

    /**
     * Additional filters to register.
     *
     * <p>Returned entries will be merged into the generator's filter map.
     * <p>If a key conflicts with an existing filter name, the plugin's value wins.
     */
    default List<SofaFilter> getFilters() {
        return Collections.emptyList();
    }

    /**
     * Additional type converters to register.
     *
     * <p>Converters are appended after built-in converters. If names conflict, later converters
     * override earlier ones (plugin converters override defaults; later plugins override earlier).
     */
    default List<SofaTypeConverter<?>> getTypeConverters() {
        return Collections.emptyList();
    }
}

