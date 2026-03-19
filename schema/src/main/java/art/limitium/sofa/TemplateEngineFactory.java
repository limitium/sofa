package art.limitium.sofa;

import art.limitium.sofa.ext.*;
import art.limitium.sofa.schema.Entity;
import art.limitium.sofa.schema.TypeConverter;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.loader.ClasspathLoader;
import com.mitchellbosecke.pebble.loader.DelegatingLoader;
import com.mitchellbosecke.pebble.loader.FileLoader;
import com.mitchellbosecke.pebble.loader.StringLoader;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TemplateEngineFactory {
    private static final Logger logger = LoggerFactory.getLogger(TemplateEngineFactory.class);

    private final List<TypeConverter> typeConverters;
    private final PebbleEngine inlineEngine;
    private final TemplateEvaluator templateEvaluator;
    private final Map<String, Filter> pluginFilters;

    TemplateEngineFactory(ClassLoader classLoader, List<String> pluginClassNames) {
        this.typeConverters = PluginDiscovery.discoverTypeConverters(classLoader, pluginClassNames, loadCoreTypeConverters());
        this.pluginFilters = PluginDiscovery.discoverFilters(classLoader, pluginClassNames);
        this.inlineEngine = createInlineEngine(typeConverters, pluginFilters);
        this.templateEvaluator = TemplateEvaluator.defaultEvaluator();
    }

    List<TypeConverter> getTypeConverters() {
        return typeConverters;
    }

    PebbleTemplate compileInlineTemplate(String template) {
        return inlineEngine.getTemplate(template);
    }

    String evaluateTemplateToString(PebbleTemplate template, Map<String, ?> context) {
        return templateEvaluator.evaluateToString(template, context);
    }

    TemplateEvaluator getTemplateEvaluator() {
        return templateEvaluator;
    }

    PebbleEngine createPebbleEngineForPath(
            String filePath, String classPath, Map<String, Map<String, Entity>> schemas) {
        FileLoader fileLoader = new FileLoader();
        fileLoader.setPrefix(filePath);
        fileLoader.setSuffix(".peb");

        ClasspathLoader classpathLoader = new ClasspathLoader();
        classpathLoader.setPrefix(classPath);
        classpathLoader.setSuffix(".peb");

        DelegatingLoader delegatingLoader = new DelegatingLoader(List.of(fileLoader, classpathLoader));

        PebbleEngine engine = new PebbleEngine.Builder()
                .loader(delegatingLoader)
                .newLineTrimming(true)
                .autoEscaping(false)
                .strictVariables(true)
                .build();

        engine.getExtensionRegistry().addExtension(new CustomExtension(typeConverters, schemas, pluginFilters));
        return engine;
    }

    private static PebbleEngine createInlineEngine(
            List<TypeConverter> typeConverters, Map<String, Filter> pluginFilters) {
        PebbleEngine engine = new PebbleEngine.Builder()
                .loader(new StringLoader())
                .newLineTrimming(true)
                .strictVariables(true)
                .build();
        engine.getExtensionRegistry().addExtension(new CustomExtension(typeConverters, Map.of(), pluginFilters));
        return engine;
    }

    private static List<TypeConverter> loadCoreTypeConverters() {
        return List.of(
                new FBTypeConverter(),
                new FBFactoryConverter(),
                new FbIsPrimitiveConverter(),
                new JavaTypeConverter(),
                new ConnectSchemaConverter(),
                new ConnectStructGetterConverter(),
                new LiquidBaseTypeConverter());
    }
}

