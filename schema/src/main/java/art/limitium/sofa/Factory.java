package art.limitium.sofa;

import art.limitium.sofa.config.FactoryConfig;
import art.limitium.sofa.config.ManifestConfig;
import art.limitium.sofa.imports.Imports;
import art.limitium.sofa.imports.Manifest;
import art.limitium.sofa.schema.Entity;
import art.limitium.sofa.schema.SchemaAnnotations;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.SchemaParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Main entry point for code generation
     * @param args Command line arguments - expects path(s) to generator definition file(s)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Path to generator definition file is missed");
        }
        Instant startTime = Instant.now();
        String[] configPaths = args[0].split(",");

        logger.info("Provided configurations {}:\r\n{}", configPaths.length, String.join("\r\n", configPaths));
        Arrays.stream(configPaths).sequential().forEach(Factory::generateForConfiguration);

        Duration duration = Duration.between(startTime, Instant.now());
        logger.info("Generation successful in {}.{}s", duration.toSeconds(), duration.toMillisPart());
    }

    /**
     * Generates code based on a single configuration file
     * @param configPath Path to the configuration file
     */
    private static void generateForConfiguration(String configPath) {
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

        logger.info("Local schemas {}: \r\n{}", factoryConfig.schemas.size(), String.join("\r\n", factoryConfig.schemas));
        List<String> pluginClasses = factoryConfig.plugins != null ? factoryConfig.plugins : List.of();
        TemplateEngineFactory templateEngines =
                new TemplateEngineFactory(Factory.class.getClassLoader(), pluginClasses);
        if (factoryConfig.imports != null && !factoryConfig.imports.isEmpty()) {
            logger.info("Configured imports {}: \r\n{}", factoryConfig.imports.size(),
                    String.join("\r\n", factoryConfig.imports));
        }
        Imports imports = Imports.load(Factory.class.getClassLoader(), factoryConfig.imports);

        List<String> importedSpecs = imports.schemaSpecs();
        List<String> schemaSpecs = new ArrayList<>(importedSpecs);
        schemaSpecs.addAll(factoryConfig.schemas);
        if (!importedSpecs.isEmpty()) {
            logger.info("Imports contribute {} schemas, loaded ahead of the local ones: \r\n{}",
                    importedSpecs.size(), String.join("\r\n", importedSpecs));
        }
        logger.info("Loading schemas {} in order: \r\n{}", schemaSpecs.size(), String.join("\r\n", schemaSpecs));

        SchemaDefinition schemaDefinition = loadSchema(basePath, schemaSpecs);
        List<AvroEntity> roots = schemaDefinition.findRoots();
        logger.info("Found roots {}: \r\n{}", roots.size(), String.join("\r\n", roots.stream().map(AvroEntity::getFullname).toList()));

        List<AvroEntity> annotated = schemaDefinition.findAnnotatedRecords();
        if (!annotated.isEmpty()) {
            logger.info("Records pinning their role by annotation {}: \r\n{}", annotated.size(),
                    String.join("\r\n", annotated.stream()
                            .map(e -> e.getFullname() + " -> "
                                    + (SchemaAnnotations.isPolymorphicallyOwned(e.schema)
                                            ? SchemaAnnotations.OWNERSHIP + ": " + SchemaAnnotations.OWNERSHIP_POLYMORPHIC
                                            : SchemaAnnotations.ROLE + ": " + SchemaAnnotations.ROLE_CHILD))
                            .toList()));
        }

        List<AvroEntity> declaredEntryPoints = schemaDefinition.findDeclaredEntryPoints();
        if (!declaredEntryPoints.isEmpty()) {
            logger.info("Annotated records no root reaches, added to the scope of work {}: \r\n{}",
                    declaredEntryPoints.size(),
                    String.join("\r\n", declaredEntryPoints.stream().map(AvroEntity::getFullname).toList()));
        }

        List<AvroEntity> scopeOfWork = convertTriesToUniqReverseRecords(schemaDefinition.roots, declaredEntryPoints);
        logger.info("Scope of work sequence {}: \r\n{}", scopeOfWork.size(), String.join("\r\n", scopeOfWork.stream().map(AvroEntity::getFullname).toList()));

        Map<String, Map<String, Entity>> schemas = new HashMap<>();

        List<Generator> generators = factoryConfig.generators.stream().map(generatorConfig -> {
            String generatorPath = generatorConfig.path;

            Map<String, PebbleTemplate> mainTemplates =
                    loadMainTemplatesForGenerator(templateEngines, basePath, generatorPath, schemas);

            logger.info("Evaluate values");
            Map<String, String> valuesContext = new HashMap<>();
            valuesContext.put("basePath", basePath);

            if (factoryConfig.values != null) {
                factoryConfig.values.replaceAll(
                        (k, v) -> {
                            String newValue =
                                    templateEngines.evaluateTemplateToString(
                                            templateEngines.compileInlineTemplate(v), valuesContext);
                            valuesContext.put(k, newValue);
                            return newValue;
                        });
            }

            return new Generator(
                    generatorPath,
                    mainTemplates,
                    new Generator.Templates(
                            templateEngines.compileInlineTemplate(generatorConfig.templates.namespace),
                            templateEngines.compileInlineTemplate(generatorConfig.templates.name),
                            templateEngines.compileInlineTemplate(generatorConfig.templates.fullname),
                            templateEngines.compileInlineTemplate(generatorConfig.templates.folder),
                            templateEngines.compileInlineTemplate(generatorConfig.templates.filename)),
                    generatorConfig.overrides,
                    generatorConfig.filters,
                    templateEngines.compileInlineTemplate(generatorConfig.postCall),
                    schemas,
                    valuesContext,
                    templateEngines.getTemplateEvaluator(),
                    imports);
        }).toList();


        for (Generator generator : generators) {
            logger.info("Start generator `{}`", generator.getName());
            generator.generate(scopeOfWork);
        }

        if (factoryConfig.manifest != null) {
            publishManifest(factoryConfig.manifest, generators, templateEngines, basePath);
        }
    }

    /**
     * Publishes what this module generated so downstream modules can reference it instead of
     * regenerating their own copies.
     * <p>
     * Runs after every generator so the manifest covers the full set of produced artifacts.
     *
     * @param manifestConfig  Manifest section of the configuration
     * @param generators      Generators that ran, in configuration order
     * @param templateEngines Engines used to evaluate the manifest folder template
     * @param basePath        Base directory of the configuration file
     */
    private static void publishManifest(
            ManifestConfig manifestConfig,
            List<Generator> generators,
            TemplateEngineFactory templateEngines,
            String basePath) {
        if (manifestConfig.artifact == null || manifestConfig.artifact.isBlank()) {
            throw new RuntimeException("`manifest.artifact` is required, expected a `groupId:artifactId` coordinate");
        }

        Manifest manifest = new Manifest();
        manifest.artifact = manifestConfig.artifact;
        manifest.schemas = manifestConfig.schemas != null ? manifestConfig.schemas : List.of();

        for (Generator generator : generators) {
            // Every generator that ran is listed, including ones that produced nothing. Consumers
            // rely on this to tell "the library never ran this generator", where generating locally
            // is correct, from "it ran and deliberately emitted nothing for this record", where the
            // roles disagree between the modules and generating locally would duplicate a class.
            manifest.generators.add(generator.getName());

            Map<String, Entity> generated = generator.getGenerated();
            if (generated.isEmpty()) {
                logger.info("Manifest records no artifacts from generator `{}`", generator.getName());
                continue;
            }
            logger.info("Manifest records {} artifacts from generator `{}`", generated.size(), generator.getName());
            generated.forEach((recordFullname, entity) -> manifest.records
                    .computeIfAbsent(recordFullname, k -> new LinkedHashMap<>())
                    .put(generator.getName(),
                            new Manifest.Artifact(entity.getNamespace(), entity.getName(), entity.getFullname())));
        }

        Map<String, String> context = new HashMap<>();
        context.put("basePath", basePath);
        String folder = manifestConfig.folder == null
                ? basePath
                : templateEngines.evaluateTemplateToString(
                        templateEngines.compileInlineTemplate(manifestConfig.folder), context);
        if (!folder.startsWith("/")) {
            folder = basePath + "/" + folder;
        }

        Path manifestPath = Path.of(folder).resolve(Manifest.resourcePath(manifest.artifact));
        manifest.write(manifestPath);
        logger.info("Published manifest for `{}` with {} records across generators {} into {}",
                manifest.artifact, manifest.records.size(), String.join(", ", manifest.generators), manifestPath);
    }

    private static Map<String, PebbleTemplate> loadMainTemplatesForGenerator(
            TemplateEngineFactory templateEngines,
            String basePath,
            String generatorPath,
            Map<String, Map<String, Entity>> schemas) {
        String filePath = basePath + "/" + generatorPath;
        String classPath = "generators/" + generatorPath;
        if (generatorPath.startsWith("/")) {
            filePath = generatorPath;
        }

        //Configure for regular and class loader
        PebbleEngine pebbleEngineForPath =
                templateEngines.createPebbleEngineForPath(filePath, classPath, schemas);

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
                throw new RuntimeException("Unable to load templates for `" + generatorPath + "` generator", e);
            }

        } else {
            URL resource = Factory.class.getClassLoader().getResource(classPath);
            if (resource == null) {
                throw new RuntimeException("Unable to load templates for `" + generatorPath + "` generator");
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
                    throw new RuntimeException("Unable to load templates for `" + generatorPath + "` generator", e);
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

        Map<String, PebbleTemplate> mainTemplates = mainTemplatesNames.stream()
                .map(pebbleEngineForPath::getTemplate)
                .collect(Collectors.toMap(PebbleTemplate::getName, identity()));
        logger.info("Create generator `{}`, with templates: \r\n{}", generatorPath, String.join("\r\n", mainTemplates.keySet().stream().toList()));
        return mainTemplates;
    }

    /**
     * Converts roots and declared entry points into a flattened, dependency first list with unique entries
     * @param roots List of root AvroEntities
     * @param declaredEntryPoints Annotated records no root reaches
     * @return Flattened and reversed list of AvroEntities
     */
    private static List<AvroEntity> convertTriesToUniqReverseRecords(
            List<AvroEntity> roots, List<AvroEntity> declaredEntryPoints) {
        TriesToReverseListConverter<AvroEntity> triesToReverseListConverter = new TriesToReverseListConverter<>();
        for (AvroEntity entryPoint : declaredEntryPoints) {
            triesToReverseListConverter.addNode(entryPoint);
        }
        for (AvroEntity root : roots) {
            triesToReverseListConverter.addNode(root);
        }

        // Dependencies first so record fields can resolve the entities they point at, then the
        // entities nothing else reaches. putIfAbsent keeps the dependency ordered position of
        // anything already flattened.
        LinkedHashMap<String, AvroEntity> ordered = new LinkedHashMap<>();
        triesToReverseListConverter.getFlatted().forEach(entity -> ordered.put(entity.getFullname(), entity));
        declaredEntryPoints.forEach(entity -> ordered.putIfAbsent(entity.getFullname(), entity));
        roots.forEach(entity -> ordered.putIfAbsent(entity.getFullname(), entity));
        return List.copyOf(ordered.values());
    }

    /**
     * Creates a PebbleEngine instance configured for a specific path
     * @param filePath File system path to templates
     * @param classPath Classpath to templates
     * @param schemas Map of schema definitions
     * @return Configured PebbleEngine instance
     */
    /**
     * Loads and parses Avro schemas from files and classpath resources.
     * <p>
     * Supported schema path formats:
     * <ul>
     *     <li>{@code avro/path/Schema.avsc} - resolved relative to {@code basePath}; if the file
     *     does not exist, a classpath resource {@code sofa/avro/path/Schema.avsc} is attempted.</li>
     *     <li>{@code groupId:artifactId:avro/path/Schema.avsc} - resolved only as a classpath
     *     resource {@code sofa/avro/path/Schema.avsc}. The {@code groupId:artifactId} part is used
     *     for logging and error messages only.</li>
     * </ul>
     * Schemas are loaded strictly in the order they are defined in the {@code schemas} list.
     *
     * @param basePath Base directory path
     * @param schemas  List of schema path specifications
     * @return SchemaDefinition containing parsed schemas
     */
    private static SchemaDefinition loadSchema(String basePath, List<String> schemas) {
        SchemaDefinition schemaDefinition = new SchemaDefinition();
        Parser parser = new Parser();
        ClassLoader classLoader = Factory.class.getClassLoader();

        for (String schemaSpec : schemas) {
            long colonCount = schemaSpec.chars().filter(ch -> ch == ':').count();
            try {
                Schema schema;

                if (colonCount == 2) {
                    // Format: groupId:artifactId:path/To/Schema.avsc
                    String[] parts = schemaSpec.split(":", 3);
                    String group = parts[0];
                    String artifact = parts[1];
                    String resourceRelativePath = parts[2];

                    String ga = group + ":" + artifact;
                    String resourcePath = "sofa/" + resourceRelativePath;

                    logger.info("Parsing schema `{}` from classpath resource `{}` (expected in library `{}`)",
                            schemaSpec, resourcePath, ga);

                    try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                        if (is == null) {
                            throw new RuntimeException("Schema `" + schemaSpec + "` not found as classpath resource `" +
                                    resourcePath + "`; ensure dependency `" + ga + "` is on the generator classpath");
                        }
                        schema = parser.parse(is);
                    }
                } else {
                    // Local schema: relative path resolved against basePath, with optional classpath fallback
                    String filePath = basePath + "/" + schemaSpec;
                    File file = new File(filePath);

                    if (file.exists()) {
                        logger.info("Parsing schema from file {}", file.getAbsolutePath());
                        schema = parser.parse(file);
                    } else {
                        String resourcePath = "sofa/" + schemaSpec;
                        logger.info("File `{}` not found, trying to load schema from classpath resource `{}`",
                                file.getAbsolutePath(), resourcePath);
                        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                            if (is == null) {
                                throw new RuntimeException("Schema `" + schemaSpec + "` not found as file `" +
                                        file.getAbsolutePath() + "` or classpath resource `" + resourcePath + "`");
                            }
                            schema = parser.parse(is);
                        }
                    }
                }

                logger.debug("Schema parsed {}", schema);
                if (schema.isUnion()) {
                    schema.getTypes().forEach(schemaDefinition::addRecord);
                } else {
                    schemaDefinition.addRecord(schema);
                }
            } catch (SchemaParseException | IOException e) {
                throw new RuntimeException("Unable to parse schema `" + schemaSpec + "`", e);
            }
        }

        return schemaDefinition;
    }

    /**
     * Loads factory configuration from a YAML file
     * @param configFile Configuration file
     * @return Parsed FactoryConfig object
     * @throws FileNotFoundException if config file not found
     */
    private static FactoryConfig loadFactoryConfig(File configFile) throws FileNotFoundException {
        Yaml yaml = new Yaml(new Constructor(FactoryConfig.class, new LoaderOptions()));
        InputStream targetStream = new FileInputStream(configFile);
        return yaml.load(targetStream);
    }

    /**
     * Compiles a string template into a PebbleTemplate
     * @param template Template string
     * @return Compiled PebbleTemplate
     */
}
//todo: external conditions per template generation
//todo: Do not override if overridden entity structure exactly the same
//todo: Extensions for filters, types from classpath and injection via yaml
//todo: Gradle plugin/script to run¡
//todo: examples tests?
//todo: docs
