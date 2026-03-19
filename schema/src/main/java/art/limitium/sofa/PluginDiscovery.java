package art.limitium.sofa;

import art.limitium.sofa.plugin.SofaPlugin;
import art.limitium.sofa.plugin.SofaType;
import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import java.util.*;

final class PluginDiscovery {

    private PluginDiscovery() {}

    static List<TypeConverter> discoverTypeConverters(
            ClassLoader classLoader, List<String> pluginClassNames, List<TypeConverter> coreConverters) {
        List<SofaPlugin> plugins = loadPlugins(classLoader, pluginClassNames);
        List<TypeConverter> converters = new ArrayList<>(coreConverters);
        for (SofaPlugin plugin : plugins) {
            String pluginId = requireNonBlank(plugin.getId(), "Plugin id must be non-blank");
            for (SofaPlugin.SofaTypeConverter<?> tc : plugin.getTypeConverters()) {
                if (tc == null) {
                    continue;
                }
                requireNonBlank(
                        tc.getName(),
                        "Plugin `" + pluginId + "` converter class " + tc.getClass().getName()
                                + " getName() must be non-blank");
                converters.add(adaptTypeConverter(plugin, tc));
            }
        }
        return List.copyOf(converters);
    }

    static Map<String, Filter> discoverFilters(ClassLoader classLoader, List<String> pluginClassNames) {
        List<SofaPlugin> plugins = loadPlugins(classLoader, pluginClassNames);
        if (plugins.isEmpty()) {
            return Map.of();
        }

        Map<String, Filter> filters = new HashMap<>();
        for (SofaPlugin plugin : plugins) {
            String pluginId = requireNonBlank(plugin.getId(), "Plugin id must be non-blank");
            for (SofaPlugin.SofaFilter f : plugin.getFilters()) {
                if (f == null) {
                    continue;
                }
                String name = requireNonBlank(
                        f.getName(),
                        "Plugin `" + pluginId + "` filter " + f.getClass().getName()
                                + " getName() must return non-blank String");
                filters.put(name, adaptFilter(f));
            }
        }
        return Map.copyOf(filters);
    }

    private static List<SofaPlugin> loadPlugins(ClassLoader classLoader, List<String> pluginClassNames) {
        List<SofaPlugin> plugins = new ArrayList<>();
        if (pluginClassNames != null) {
            for (String className : pluginClassNames) {
                try {
                    Class<?> cls = Class.forName(className, true, classLoader);
                    Object instance = cls.getDeclaredConstructor().newInstance();
                    if (!(instance instanceof SofaPlugin plugin)) {
                        throw new IllegalStateException(
                                "Configured plugin class " + className + " does not implement SofaPlugin");
                    }
                    plugins.add(plugin);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Configured plugin class not found: " + className, e);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(
                            "Configured plugin class must have public no-arg constructor: " + className, e);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to instantiate plugin class: " + className, e);
                }
            }
        }
        return List.copyOf(plugins);
    }

    private static Filter adaptFilter(SofaPlugin.SofaFilter sofaFilter) {
        return new Filter() {
            @Override
            public List<String> getArgumentNames() {
                List<String> names = sofaFilter.getArgumentNames();
                if (names == null) {
                    return null;
                }
                for (String name : names) {
                    if (name == null || name.isBlank()) {
                        throw new IllegalStateException(
                                "Filter " + sofaFilter.getClass().getName()
                                        + " getArgumentNames() contains null/blank value");
                    }
                }
                return names;
            }

            @Override
            public Object apply(
                    Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
                    throws PebbleException {
                try {
                    return sofaFilter.apply(input, args, self, context, lineNumber);
                } catch (PebbleException e) {
                    throw e;
                } catch (Exception e) {
                    throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
                }
            }
        };
    }

    private static TypeConverter adaptTypeConverter(SofaPlugin plugin, SofaPlugin.SofaTypeConverter<?> sofaTypeConverter) {
        String name = sofaTypeConverter.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(
                    "Plugin `" + plugin.getId() + "` type converter " + sofaTypeConverter.getClass().getName()
                            + " getName() must return non-blank String");
        }

        return new TypeConverter() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Object getType(Type type) {
                try {
                    return sofaTypeConverter.getType(new SofaTypeView(type));
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(
                            "Plugin `" + plugin.getId() + "` type converter " + sofaTypeConverter.getClass().getName()
                                    + " failed in getType()",
                            e);
                }
            }
        };
    }

    private static String requireNonBlank(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return value;
    }

    private static final class SofaTypeView implements SofaType {
        private final Type type;

        private SofaTypeView(Type type) {
            this.type = type;
        }

        @Override
        public Kind kind() {
            if (type instanceof Type.UUIDType) return Kind.UUID;
            if (type instanceof Type.DatetimeType) return Kind.DATETIME_STR;
            if (type instanceof Type.DateType) return Kind.DATE;
            if (type instanceof Type.TimeMillisType) return Kind.TIME_MILLIS;
            if (type instanceof Type.TimeMicrosType) return Kind.TIME_MICROS;
            if (type instanceof Type.TimestampMillisType) return Kind.TIMESTAMP_MILLIS;
            if (type instanceof Type.TimestampMicrosType) return Kind.TIMESTAMP_MICROS;
            if (type instanceof Type.LocalTimestampMillisType) return Kind.LOCAL_TIMESTAMP_MILLIS;
            if (type instanceof Type.LocalTimestampMicrosType) return Kind.LOCAL_TIMESTAMP_MICROS;
            if (type instanceof Type.DecimalType) return Kind.DECIMAL;
            if (type instanceof Type.NullType) return Kind.NULL;
            if (type instanceof Type.BooleanType) return Kind.BOOLEAN;
            if (type instanceof Type.IntType) return Kind.INT;
            if (type instanceof Type.LongType) return Kind.LONG;
            if (type instanceof Type.FloatType) return Kind.FLOAT;
            if (type instanceof Type.DoubleType) return Kind.DOUBLE;
            if (type instanceof Type.StringType) return Kind.STRING;
            if (type instanceof Type.BytesType) return Kind.BYTES;
            if (type instanceof Type.RecordType) return Kind.RECORD;
            if (type instanceof Type.RecordCloseType) return Kind.RECORD_CLOSE;
            if (type instanceof Type.EnumType) return Kind.ENUM;
            if (type instanceof Type.ArrayType) return Kind.ARRAY;
            if (type instanceof Type.MapType) return Kind.MAP;
            if (type instanceof Type.UnionType) return Kind.UNION;
            if (type instanceof Type.FixedType) return Kind.FIXED;
            return Kind.STRING;
        }

        @Override
        public String getName() {
            return type.getName();
        }

        @Override
        public Map<String, Object> getProperties() {
            return type.getProperties();
        }

        @Override
        public Optional<String> getFullName() {
            if (type instanceof Type.RecordType rt) {
                return Optional.ofNullable(rt.getRecord()).map(r -> r.getFullname());
            }
            if (type instanceof Type.RecordCloseType rct) {
                return Optional.ofNullable(rct.getRecord()).map(r -> r.getFullname());
            }
            if (type instanceof Type.EnumType et) {
                return Optional.ofNullable(et.getEnum()).map(e -> e.getFullname());
            }
            return Optional.empty();
        }

        @Override
        public Optional<List<String>> getEnumSymbols() {
            if (type instanceof Type.EnumType et) {
                return Optional.ofNullable(et.getEnum()).map(e -> e.getSymbols());
            }
            return Optional.empty();
        }

        @Override
        public Optional<SofaType> getElementType() {
            if (type instanceof Type.ArrayType at) {
                return Optional.of(new SofaTypeView(at.getElementType()));
            }
            return Optional.empty();
        }

        @Override
        public List<SofaType> getUnionTypes() {
            if (type instanceof Type.UnionType ut) {
                return ut.getTypes().stream().map(t -> (SofaType) new SofaTypeView(t)).toList();
            }
            return SofaType.super.getUnionTypes();
        }

        @Override
        public Optional<Integer> getFixedSize() {
            if (type instanceof Type.FixedType ft) {
                return Optional.of(ft.getSize());
            }
            return Optional.empty();
        }

        @Override
        public Optional<String> getDatetimeFormat() {
            if (type instanceof Type.DatetimeType dt) {
                return Optional.of(dt.getFormat());
            }
            return Optional.empty();
        }

        @Override
        public Optional<Integer> getDecimalPrecision() {
            if (type instanceof Type.DecimalType dt) {
                return Optional.of(dt.getPrecision());
            }
            return Optional.empty();
        }

        @Override
        public Optional<Integer> getDecimalScale() {
            if (type instanceof Type.DecimalType dt) {
                return Optional.of(dt.getScale());
            }
            return Optional.empty();
        }
    }
}

