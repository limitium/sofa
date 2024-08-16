package art.limitium.sofa;

import art.limitium.sofa.config.FactoryConfig;
import art.limitium.sofa.ext.FBFactoryConverter;
import art.limitium.sofa.ext.FBTypeConverter;
import art.limitium.sofa.ext.FbIsPrimitiveConverter;
import art.limitium.sofa.ext.JavaTypeConverter;
import art.limitium.sofa.schema.Entity;
import art.limitium.sofa.schema.TypeConverter;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.DelegatingLoader;
import com.mitchellbosecke.pebble.loader.FileLoader;
import com.mitchellbosecke.pebble.loader.StringLoader;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.SchemaParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;

public class Factory {
    static Logger logger = LoggerFactory.getLogger(Factory.class);


    static PebbleEngine inlineEngine = new PebbleEngine.Builder()
            .loader(new StringLoader())
            .newLineTrimming(true)
            .strictVariables(true)
            .build();


    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new RuntimeException("Path to generator definition file is missed");
        }
        String configPath = args[0];

        logger.info("Loading configuration from {}", configPath);
        File configFile = new File(configPath);
        String basePath = configFile.getParent();
        logger.info("Base path is set to {}", basePath);

        FactoryConfig factoryConfig = loadFactoryConfig(configFile);

        logger.info("Loading schemas {}: \r\n{}", factoryConfig.schemas.size(), String.join("\r\n", factoryConfig.schemas));
        SchemaDefinition schemaDefinition = loadSchema(basePath, factoryConfig.schemas);
        List<AvroEntity> roots = schemaDefinition.findRoots();
        logger.info("Found roots {}: \r\n{}", roots.size(), String.join("\r\n", roots.stream().map(AvroEntity::getFullname).toList()));


        List<AvroEntity> scopeOfWork = convertTriesToUniqReverseRecords(schemaDefinition.roots);
        logger.info("Scope of work sequence {}: \r\n{}", scopeOfWork.size(), String.join("\r\n", scopeOfWork.stream().map(AvroEntity::getFullname).toList()));


        Map<String, Map<String, Entity>> schemas = new HashMap<>();

        List<Generator> generators = factoryConfig.generators.stream().map(generatorConfig -> {
            String path = basePath + "/" + generatorConfig.path;
            if (generatorConfig.path.startsWith("/")) {
                path = generatorConfig.path;
            }


            PebbleEngine pebbleEngineForPath = createPebbleEngineForPath(path, schemas);

            Map<String, PebbleTemplate> mainTemplates;

            try (Stream<Path> files = Files.list(Path.of(path))) {
                mainTemplates = files
                        .map(Path::toFile)
                        .filter(File::isFile)
                        .filter(f -> f.getName().endsWith(".peb"))
                        .map(f -> f.getName().replace(".peb", ""))
                        .map(pebbleEngineForPath::getTemplate)
                        .collect(Collectors.toMap(PebbleTemplate::getName, identity()));
            } catch (IOException e) {
                throw new RuntimeException("Unable to load templates for `" + generatorConfig.path + "` generator", e);
            }

            logger.info("Create generator `{}`, with templates: \r\n{}", generatorConfig.path, String.join("\r\n", mainTemplates.keySet().stream().toList()));

            return new Generator(
                    generatorConfig.path,
                    mainTemplates,
                    new Generator.Templates(
                            compileInlineTemplate(generatorConfig.templates.namespace),
                            compileInlineTemplate(generatorConfig.templates.name),
                            compileInlineTemplate(generatorConfig.templates.fullname),
                            compileInlineTemplate(generatorConfig.templates.folder),
                            compileInlineTemplate(generatorConfig.templates.filename)
                    ),
                    generatorConfig.overrides,
                    generatorConfig.filters,
                    compileInlineTemplate(generatorConfig.postCall),
                    schemas,
                    basePath);
        }).toList();


        for (Generator generator : generators) {
            logger.info("Start generator `{}`", generator.getName());
            generator.generate(scopeOfWork);
        }
    }


    private static List<AvroEntity> convertTriesToUniqReverseRecords(List<AvroEntity> roots) {
        List<AvroEntity> reversedFlattedList = new ArrayList<>();
        TriesToReverseListConverter<AvroEntity> triesToReverseListConverter = new TriesToReverseListConverter<>();
        for (AvroEntity root : roots) {
            triesToReverseListConverter.addNode(root);
        }
        reversedFlattedList.addAll(triesToReverseListConverter.getFlatted());
        reversedFlattedList.addAll(roots);
        return reversedFlattedList;
    }

    private static PebbleEngine createPebbleEngineForPath(String path, Map<String, Map<String, Entity>> schemas) {
        FileLoader loader1 = new FileLoader();
        loader1.setPrefix(path);
        loader1.setSuffix(".peb");

        DelegatingLoader delegatingLoader = new DelegatingLoader(List.of(loader1));


        PebbleEngine engine = new PebbleEngine.Builder()
                .loader(delegatingLoader)
                .newLineTrimming(true)
                .autoEscaping(false)
                .strictVariables(true)
                .build();

        List<TypeConverter> typeConverters = List.of(
                new FBTypeConverter(),
                new FBFactoryConverter(),
                new FbIsPrimitiveConverter(),
                new JavaTypeConverter()
        );

        engine.getExtensionRegistry().addExtension(new CustomExtension(typeConverters, schemas));
        return engine;
    }

    private static SchemaDefinition loadSchema(String basePath, List<String> schemas) {
        SchemaDefinition schemaDefinition = new SchemaDefinition();
        Parser parser = new Parser();
        for (String schemaPath : schemas) {
            String filePath = basePath + "/" + schemaPath;
            try {
                logger.info("Parsing schema from {}", filePath);
                Schema schema = parser.parse(new File(filePath));
                logger.debug("Schema parsed {}", schema);
                schemaDefinition.addRecord(schema);
            } catch (SchemaParseException | IOException e) {
                throw new RuntimeException("Unable to parse file `" + filePath + "`", e);
            }

        }

        return schemaDefinition;
    }

    private static FactoryConfig loadFactoryConfig(File configFile) throws FileNotFoundException {
        Yaml yaml = new Yaml(new Constructor(FactoryConfig.class, new LoaderOptions()));
        InputStream targetStream = new FileInputStream(configFile);
        return yaml.load(targetStream);
    }

    private static PebbleTemplate compileInlineTemplate(String template) {
        return inlineEngine.getTemplate(template);
    }

    static String evaluateTemplateToString(PebbleTemplate template, Map<String, Object> context) {
        StringWriter stringWriter = new StringWriter();
        try {
            template.evaluate(stringWriter, context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }
}
//todo: Do not override if overridden entity structure exactly the same
//todo: Provide references to two owners: direct owner and flatten owner to root or to the upper dependent