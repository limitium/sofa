package art.limitium.sofa;

import art.limitium.sofa.config.FactoryConfig;
import art.limitium.sofa.ext.FBFactoryConverter;
import art.limitium.sofa.ext.FBTypeConverter;
import art.limitium.sofa.ext.FbIsPrimitiveConverter;
import art.limitium.sofa.ext.JavaTypeConverter;
import art.limitium.sofa.schema.Entity;
import art.limitium.sofa.schema.TypeConverter;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.loader.ClasspathLoader;
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
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
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


    public static void main(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Path to generator definition file is missed");
        }
        Instant startTime = Instant.now();
        String configPath = args[0];

        logger.info("Loading configuration from {}", configPath);
        File configFile = new File(configPath);
        String basePath = configFile.getAbsoluteFile().getParent();
        logger.info("Base path is set to {}", basePath);

        FactoryConfig factoryConfig;
        try {
            factoryConfig = loadFactoryConfig(configFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to load config file from " + configPath, e);
        }

        logger.info("Loading schemas {}: \r\n{}", factoryConfig.schemas.size(), String.join("\r\n", factoryConfig.schemas));
        SchemaDefinition schemaDefinition = loadSchema(basePath, factoryConfig.schemas);
        List<AvroEntity> roots = schemaDefinition.findRoots();
        logger.info("Found roots {}: \r\n{}", roots.size(), String.join("\r\n", roots.stream().map(AvroEntity::getFullname).toList()));


        List<AvroEntity> scopeOfWork = convertTriesToUniqReverseRecords(schemaDefinition.roots);
        logger.info("Scope of work sequence {}: \r\n{}", scopeOfWork.size(), String.join("\r\n", scopeOfWork.stream().map(AvroEntity::getFullname).toList()));


        Map<String, Map<String, Entity>> schemas = new HashMap<>();

        List<Generator> generators = factoryConfig.generators.stream().map(generatorConfig -> {
            String filePath = basePath + "/" + generatorConfig.path;
            String classPath = "generators/" + generatorConfig.path;
            if (generatorConfig.path.startsWith("/")) {
                filePath = generatorConfig.path;
            }

            //Configure for regular and class loader
            PebbleEngine pebbleEngineForPath = createPebbleEngineForPath(filePath, classPath, schemas);


            List<String> mainTemplatesNames = new ArrayList<>();
            //regular external filepath
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                logger.info("Generator loaded from regular folder: {}", path.toAbsolutePath());
                try (Stream<Path> files = Files.list(path)) {
                    mainTemplatesNames = files
                            .map(Path::toFile)
                            .filter(File::isFile)
                            .filter(f -> f.getName().endsWith(".peb"))
                            .map(f -> f.getName().replace(".peb", ""))
                            .toList();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to load templates for `" + generatorConfig.path + "` generator", e);
                }

            } else {
                URL resource = Factory.class.getClassLoader().getResource(classPath);
                if (resource == null) {
                    throw new RuntimeException("Unable to load templates for `" + generatorConfig.path + "` generator");
                }

                if (!resource.getProtocol().equals("jar")) {
                    //resources from current filebased classpath
                    logger.info("Generator loaded from resource folder: {}", path.toAbsolutePath());
                    try (Stream<Path> files = Files.list(Path.of(resource.getPath()))) {
                        mainTemplatesNames = files
                                .map(Path::toFile)
                                .filter(File::isFile)
                                .filter(f -> f.getName().endsWith(".peb"))
                                .map(f -> f.getName().replace(".peb", ""))
                                .toList();
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to load templates for `" + generatorConfig.path + "` generator", e);
                    }
                } else {
                    //inner jar resources
                    logger.info("Generator loaded from jar: {}", path.toAbsolutePath());
                    try {
                        JarURLConnection jarConnection = (JarURLConnection) resource.openConnection();
                        JarFile jarFile = jarConnection.getJarFile();
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String entryName = entry.getName();
                            if (entryName.startsWith(classPath) && entryName.endsWith(".peb")) {
                                // Extracting filename and extension
                                String fullName = entry.getName();  // Get the full entry name (path)
                                String fileName = fullName.substring(fullName.lastIndexOf('/') + 1);  // Extract just the file name

                                // Handle cases where there might not be an extension
                                int lastDotIndex = fileName.lastIndexOf('.');
                                String nameWithoutExtension = (lastDotIndex != -1) ? fileName.substring(0, lastDotIndex) : fileName;

                                // Process each entry
                                mainTemplatesNames.add(nameWithoutExtension);

                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            logger.info("Evaluate values");
            Map<String, String> valuesContext = new HashMap<>();
            valuesContext.put("basePath", basePath);

            factoryConfig.values.replaceAll((k, v) -> {
                String newValue = evaluateTemplateToString(compileInlineTemplate(v), valuesContext);
                valuesContext.put(k, newValue);
                return newValue;
            });

            Map<String, PebbleTemplate> mainTemplates = mainTemplatesNames.stream()
                    .map(pebbleEngineForPath::getTemplate)
                    .collect(Collectors.toMap(PebbleTemplate::getName, identity()));
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
                    valuesContext);
        }).toList();


        for (Generator generator : generators) {
            logger.info("Start generator `{}`", generator.getName());
            generator.generate(scopeOfWork);
        }

        Duration duration = Duration.between(startTime, Instant.now());
        logger.info("Generation successful in {}.{}s", duration.toSeconds(), duration.toMillisPart());
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

    private static PebbleEngine createPebbleEngineForPath(String filePath, String classPath, Map<String, Map<String, Entity>> schemas) {
        FileLoader fileLoader = new FileLoader();
        fileLoader.setPrefix(filePath);
        fileLoader.setSuffix(".peb");

        //Expects
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
                if (schema.isUnion()) {
                    schema.getTypes().forEach(schemaDefinition::addRecord);
                } else {
                    schemaDefinition.addRecord(schema);
                }
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

    @SuppressWarnings("unchecked")
    static String evaluateTemplateToString(PebbleTemplate template, Map<String, ?> context) {
        StringWriter stringWriter = new StringWriter();
        try {
            template.evaluate(stringWriter, (Map<String, Object>) context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }
}
//todo: Do not override if overridden entity structure exactly the same
//todo: Extensions for filters, types from CP and injection via yaml
//todo: Gradle plugin/script to run¡
//todo: examples tests?
//todo: docs
