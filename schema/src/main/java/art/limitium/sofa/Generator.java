package art.limitium.sofa;

import art.limitium.sofa.config.FiltersConfig;
import art.limitium.sofa.imports.Imports;
import art.limitium.sofa.imports.Manifest;
import art.limitium.sofa.schema.Entity;
import art.limitium.sofa.schema.EnumEntity;
import art.limitium.sofa.schema.RecordEntity;
import art.limitium.sofa.schema.SchemaAnnotations;
import art.limitium.sofa.schema.Type;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.avro.Schema;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Generator {
    private final String name;
    private final Map<String, PebbleTemplate> mainTemplates;
    public Templates templates;
    private final String overrides;
    private final FiltersConfig filters;
    public PebbleTemplate postCall;
    private final Map<String, Map<String, Entity>> schemas;
    private final Map<String, String> valuesContext;
    private final String basePath;
    private final TemplateEvaluator templateEvaluator;
    private final Imports imports;
    private final Map<String, Entity> generated = new LinkedHashMap<>();
    private String folder;

    /**
     * Creates a new Generator instance
     * 
     * @param name Name of the generator
     * @param mainTemplates Map of main templates used for code generation
     * @param templates Templates for namespace/name/path generation
     * @param overrides name generators to override
     * @param filters Configuration for filtering entities
     * @param postCall Template for post-generation command
     * @param schemas Map of schema name to entities
     * @param valuesContext Context values for template evaluation
     * @param imports Generated code published by imported libraries
     */
    public Generator(
            String name,
            Map<String, PebbleTemplate> mainTemplates,
            Templates templates,
            String overrides,
            FiltersConfig filters,
            PebbleTemplate postCall,
            Map<String, Map<String, Entity>> schemas,
            Map<String, String> valuesContext,
            TemplateEvaluator templateEvaluator,
            Imports imports) {
        this.name = name;
        this.mainTemplates = mainTemplates;
        this.templates = templates;
        this.overrides = overrides;
        this.filters = filters;
        this.postCall = postCall;
        this.schemas = schemas;
        this.valuesContext = valuesContext;
        this.basePath = valuesContext.get("basePath");
        this.templateEvaluator = templateEvaluator;
        this.imports = imports;
    }

    /**
     * Evaluates a template and writes output to a file
     *
     * @param template The template to evaluate
     * @param context Context map for template evaluation
     * @param filePath Path of output file
     */
    public void evaluateTemplateToFile(PebbleTemplate template, Map<String, Object> context, String filePath) {
        Factory.logger.debug("Evaluate template {} to file {} with context {}", template, filePath, context.keySet());
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            template.evaluate(fileWriter, context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Main generation method that processes Avro entities and generates code
     *
     * @param avroEntities List of Avro entities to generate code for
     */
    public void generate(List<AvroEntity> avroEntities) {
        Map<String, Entity> entities = new HashMap<>();
        Map<String, Entity> mapByAvroName = new HashMap<>();

        if(mainTemplates.containsKey("schema")){
            String fileName = evaluateFolderFileNameCreateFolder("", Collections.singletonMap("fullname", "schema"), "", Collections.singletonMap("fullname", "schema"));
            evaluateTemplateToFile(mainTemplates.get("schema"), extendValuesContext(Map.of("entities", avroEntities)), fileName);
        }

        for (AvroEntity avroEntity : avroEntities) {
            if (isBlacklisted(avroEntity.schema)) {
                Factory.logger.info("Skip entity `{}` by black filter", avroEntity.schema.getFullName());
                continue;
            }
            boolean shouldBeGenerated = shouldBeGenerated(avroEntity);

            String role = resolveRole(avroEntity);
            String namespace = generateNamespace(avroEntity.schema, role);
            String name = generateName(avroEntity.schema, role);

            Manifest.Artifact imported = imports.resolve(avroEntity.getFullname(), this.name);
            if (imported != null) {
                Factory.logger.info("Take entity `{}` from import `{}` as `{}`",
                        avroEntity.getFullname(), imports.publishersOf(avroEntity.getFullname()), imported.fullname);
                namespace = imported.namespace;
                name = imported.name;
            } else if (overrides != null && (!shouldBeGenerated || isSkipWrite(avroEntity.schema))) {
                Factory.logger.info("Take entity `{}` from `{}`", avroEntity.getFullname(), overrides);
                Entity parentEntity = schemas.get(overrides).get(avroEntity.getFullname());
                namespace = parentEntity.getNamespace();
                name = parentEntity.getName();
            }

            String fullname = imported != null
                    ? imported.fullname
                    : generateFullname(namespace, name, avroEntity.schema, role);

            Factory.logger.debug("Create entity `{}` at `{}` with `{}`", name, namespace, fullname);


            Entity entity = null;
            if (avroEntity.schema.getType() == Schema.Type.ENUM) {
                entity = new EnumEntity(namespace, name, fullname, avroEntity.schema, avroEntity.schema.getEnumSymbols());
            }
            if (avroEntity.schema.getType() == Schema.Type.RECORD) {
                List<RecordEntity.Field> fields = new ArrayList<>();

                for (Schema.Field field : avroEntity.schema.getFields()) {
                    Type type = Type.fromSchema(field.schema(), mapByAvroName);
                    Factory.logger.debug("Create field `{}` from type `{}` to `{}`", field.name(), field.schema().getType(), type);
                    fields.add(new RecordEntity.Field(field.name(), type, SchemaAnnotations.isPrimary(field)));
                }
                entity = new RecordEntity(namespace, name, fullname, avroEntity.schema, fields, avroEntity.isRoot);
            }

            if (entity != null) {
                if (imported != null) {
                    entity.markExternal();
                }
                entities.put(entity.getFullname(), entity);
                mapByAvroName.put(avroEntity.getFullname(), entity);
            }
        }

        schemas.put(name, mapByAvroName);

        long borrowed = entities.values().stream().filter(Entity::isExternal).count();
        if (borrowed > 0) {
            Factory.logger.info("{} Entities created for `{}`, {} of them borrowed from imports",
                    entities.size(), name, borrowed);
        } else {
            Factory.logger.info("{} Entities created for `{}`", entities.size(), name);
        }

        for (AvroEntity avroEntity : avroEntities) {
            Entity entity = mapByAvroName.get(avroEntity.getFullname());
            if (entity instanceof RecordEntity recordEntity) {
                //Regular direct dependencies
                for (AvroEntity dependency : avroEntity.dependencies.values()) {
                    Entity dependencyEntity = mapByAvroName.get(dependency.getFullname());
                    if (dependencyEntity == null) {
                        Factory.logger.info("Skip dependency `{}` for `{}` because dependency is not available in current generator scope", dependency.getFullname(), recordEntity.getFullname());
                        continue;
                    }
                    recordEntity.getDependencies().add(dependencyEntity);
                    if(dependencyEntity instanceof RecordEntity dependencyRecordEntity){
                        dependencyRecordEntity.getParents().add(recordEntity);
                    }
                }

                //1-N relations
                for (AvroEntity ownerAvro : avroEntity.owners) {
                    Entity ownerEntity = mapByAvroName.get(ownerAvro.getFullname());
                    if (ownerEntity == null) {
                        Factory.logger.info("Skip owner `{}` for `{}` because owner is not available in current generator scope", ownerAvro.getFullname(), recordEntity.getFullname());
                        continue;
                    }
                    if (ownerEntity instanceof RecordEntity owner) {
                        recordEntity.getOwners().add(owner);
                    } else {
                        throw new RuntimeException("Record `" + recordEntity.getFullname() + "` can be used only by other record, but used by`" + ownerEntity.getFullname() + "`");
                    }
                }
            }
        }
        Factory.logger.info("Relations created");

        List<Entity> toGenerate = avroEntities.stream()
                .filter(avroEntity -> !isBlacklisted(avroEntity.schema))
                .filter(this::shouldBeGenerated)
                .filter(avroEntity -> {
                    if (isSkipWrite(avroEntity.schema)) {
                        Factory.logger.info("Skip writing entity `{}` by skipWrite filter", avroEntity.schema.getFullName());
                        return false;
                    }
                    return true;
                })
                .map(avroEntity -> Objects.requireNonNull(
                        mapByAvroName.get(avroEntity.getFullname()),
                        "Entity is missing for `" + avroEntity.getFullname() + "`"))
                .filter(this::hasApplicableTemplate)
                .filter(this::isNotProvidedByImport)
                .toList();

        toGenerate.forEach(entity -> generated.put(entity.getSchema().getFullName(), entity));

        Factory.logger.info("Generator `{}` will write {} of {} entities in scope", name, toGenerate.size(), entities.size());

        List<String> files = toGenerate.stream().map(this::generateFor).toList();

        Factory.logger.info("Generator `{}` wrote {} files", name, files.size());

        String postCall = generatePostCall(files);
        if (postCall != null) {
            if (!postCall.startsWith("/")) {
                postCall = basePath + "/" + postCall;
            }
            Factory.logger.info("Run postCall: {}", postCall);
            try {
                // Tokenized on whitespace and run without a shell, so quoting and redirects do not apply.
                // stderr is merged in, otherwise a command that fills the error pipe blocks forever while
                // we wait on its output.
                Process exec = new ProcessBuilder(postCall.trim().split("\\s+"))
                        .redirectErrorStream(true)
                        .start();
                String out = new String(exec.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = exec.waitFor();
                if (exitCode != 0) {
                    Factory.logger.warn("postCall for generator `{}` exited with {}: {}", name, exitCode, out);
                } else if (!out.isBlank()) {
                    Factory.logger.info("Output: {}", out);
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to run postCall", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for postCall", e);
            }
        }
    }

    /**
     * Determines if an entity should have code generated for it based on filters and available templates
     *
     * @param entity The entity to check
     * @return true if code should be generated for this entity
     */
    private boolean shouldBeGenerated(AvroEntity entity) {
        if (filters != null) {
            //@todo: enforce white list items be in scope of work
            if (filters.white != null && !filters.white.isEmpty() && !filters.white.contains(entity.schema.getFullName())) {
                Factory.logger.info("Skip entity `{}` by white filter", entity.schema.getFullName());
                return false;
            }
        }

        if ((mainTemplates.containsKey("enum") && entity.schema.getType() == Schema.Type.ENUM)
                || (mainTemplates.containsKey("root") && entity.schema.getType() == Schema.Type.RECORD && entity.isRoot)
                || (mainTemplates.containsKey("owner") && entity.schema.getType() == Schema.Type.RECORD && entity.isOwner())
                || (mainTemplates.containsKey("carrier") && entity.schema.getType() == Schema.Type.RECORD
                        && entity.isCarrier())
                || (mainTemplates.containsKey("dependent") && entity.schema.getType() == Schema.Type.RECORD
                        && entity.isOwnedEntity())
                || (mainTemplates.containsKey("child") && entity.schema.getType() == Schema.Type.RECORD && !entity.isRoot)
                || mainTemplates.containsKey("record")
        ) {
            return true;
        }

        Factory.logger.info("Skip entity `{}` no proper template", entity.getFullname());
        return false;
    }

    /**
     * Resolves which role this generator will treat a record as, before any file is written.
     * <p>
     * Exposed to the namespace, name and fullname templates as {@code role} so naming can follow the
     * role rather than the generator. A composite and an entity produced by the same generator can
     * then be named differently, for instance {@code Garage} alongside {@code CarEntity}.
     * <p>
     * Mirrors the template selection ladder, and is resolved from the Avro entity because naming
     * happens before relations are wired.
     *
     * @param entity The entity being named
     * @return The role name, or {@code none} when this generator has no template for it
     */
    private String resolveRole(AvroEntity entity) {
        return entity.getRoles().stream()
                .filter(mainTemplates::containsKey)
                .findFirst()
                .orElse("none");
    }

    private boolean isBlacklisted(Schema schema) {
        if (filters == null) {
            return false;
        }
        return matchesFqcnOrPackage(filters.black, schema.getFullName());
    }

    private boolean isSkipWrite(Schema schema) {
        if (filters == null) {
            return false;
        }
        return matchesFqcnOrPackage(filters.skipWrite, schema.getFullName());
    }

    private boolean matchesFqcnOrPackage(List<String> patterns, String fullName) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            String normalizedPattern = pattern.trim();
            if (normalizedPattern.isEmpty()) {
                continue;
            }
            if (normalizedPattern.equals(fullName) || fullName.startsWith(normalizedPattern + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Generates code for a single entity
     *
     * @param entity The entity to generate code for
     * @return The path to the generated file
     */
    public String generateFor(Entity entity) {
        Map<String, Object> context = new HashMap<>();
        context.put("namespace", entity.getNamespace());
        context.put("name", entity.getName());
        context.put("entity", entity);

        String folderName = entity.getNamespace();
        Map<String, Object> folderContext = Collections.singletonMap("entity", entity);
        String fileName = entity.getName();
        Map<String, Object> filenameContext = Map.of("namespace", entity.getNamespace(), "name", entity.getName(), "fullname", entity.getFullname(), "schema", entity.getSchema(), "entity", entity);

        String fullFileName = evaluateFolderFileNameCreateFolder(folderName, folderContext, fileName, filenameContext);

        PebbleTemplate template;
        if (entity instanceof EnumEntity enumEntity) {
            Factory.logger.info("Generate enum {} into {}", enumEntity.getFullname(), fullFileName);
            context.put("symbols", enumEntity.getSymbols());
            template = mainTemplates.get("enum");

        } else {
            RecordEntity recordEntity = (RecordEntity) entity;
            if (mainTemplates.containsKey("root") && recordEntity.isRoot()) {
                Factory.logger.info("Generate root {} into {}", recordEntity.getFullname(), fullFileName);
                template = mainTemplates.get("root");
            } else if (mainTemplates.containsKey("owner") && recordEntity.isOwner()) {
                Factory.logger.info("Generate owner {} into {}", recordEntity.getFullname(), fullFileName);
                template = mainTemplates.get("owner");
            } else if (mainTemplates.containsKey("carrier") && recordEntity.isCarrier()) {
                Factory.logger.info("Generate carrier {} into {}", recordEntity.getFullname(), fullFileName);
                template = mainTemplates.get("carrier");
            } else if (mainTemplates.containsKey("dependent") && recordEntity.isOwnedEntity()) {
                Factory.logger.info("Generate dependent {} into {}", recordEntity.getFullname(), fullFileName);
                template = mainTemplates.get("dependent");
            } else if (mainTemplates.containsKey("child") && !recordEntity.isRoot()) {
                Factory.logger.info("Generate child {} into {}", recordEntity.getFullname(), fullFileName);
                template = mainTemplates.get("child");
            } else if (mainTemplates.containsKey("record")) {
                Factory.logger.info("Generate record {} into {}", recordEntity.getFullname(), fullFileName);
                template = mainTemplates.get("record");
            } else {
                throw new IllegalStateException("No applicable template for entity `" + recordEntity.getFullname() + "`");
            }
        }
        if (fullFileName.contains("/")) {
            String[] pathParts = fullFileName.split("/");
            String folderPath = String.join("/", Arrays.copyOf(pathParts, pathParts.length - 1));
            if (!folderPath.startsWith("/")) {
                folderPath = basePath + "/" + folderPath;
            }

            try {
                Files.createDirectories(Paths.get(folderPath));
            } catch (IOException e) {
                throw new RuntimeException("Unable to create folder " + folder, e);
            }
        }
        evaluateTemplateToFile(template, extendValuesContext(context), fullFileName);
        return fullFileName;
    }

    /**
     * Applies the import rules to an entity this generator would otherwise write.
     * <p>
     * Three outcomes, keyed on whether the publishing library ran this generator at all:
     * <ul>
     *     <li>the import published an artifact for it - reference that, write nothing;</li>
     *     <li>the import ran this generator but published nothing for this record - fail, because the
     *     record resolved to different roles in the two modules and generating locally would produce a
     *     second class for something the library already owns;</li>
     *     <li>the import never ran this generator - generate locally, there is nothing to collide with.</li>
     * </ul>
     *
     * @param entity The entity about to be written
     * @return true if this generator should write the entity itself
     */
    private boolean isNotProvidedByImport(Entity entity) {
        String recordFullname = entity.getSchema().getFullName();
        if (!imports.covers(recordFullname)) {
            return true;
        }
        if (entity.isExternal()) {
            Factory.logger.info("Skip writing entity `{}`, provided by import as `{}`", recordFullname, entity.getFullname());
            return false;
        }
        if (imports.ranGenerator(this.name)) {
            throw new RuntimeException("Import `" + imports.publishersOf(recordFullname) + "` ran generator `"
                    + this.name + "` but published no artifact for `" + recordFullname + "`, while this module needs one."
                    + " The record resolves to different roles in the two modules; pin it in the schema with"
                    + " `\"role\": \"child\"` or `\"ownership\": \"polymorphic\"` and republish the library");
        }
        Factory.logger.info("Generate `{}` locally, no import ran generator `{}`", recordFullname, this.name);
        return true;
    }

    /**
     * Entities this generator actually wrote a file for, keyed by Avro record fullname
     *
     * @return Written entities, for manifest publication
     */
    public Map<String, Entity> getGenerated() {
        return generated;
    }

    private boolean hasApplicableTemplate(Entity entity) {
        if (entity instanceof EnumEntity) {
            return mainTemplates.containsKey("enum");
        }
        RecordEntity recordEntity = (RecordEntity) entity;
        return (mainTemplates.containsKey("root") && recordEntity.isRoot())
                || (mainTemplates.containsKey("owner") && recordEntity.isOwner())
                || (mainTemplates.containsKey("carrier") && recordEntity.isCarrier())
                || (mainTemplates.containsKey("dependent") && recordEntity.isOwnedEntity())
                || (mainTemplates.containsKey("child") && !recordEntity.isRoot())
                || mainTemplates.containsKey("record");
    }

    private String evaluateFolderFileNameCreateFolder(String folderName, Map<String, Object> folderContext, String fileName, Map<String, Object> filenameContext) {
        String generatedFolder = generateFolder(folderName, folderContext);
        folder = basePath + "/" + generatedFolder;
        if (generatedFolder.startsWith("/")) {
            folder = generatedFolder;
        }
        if (!folder.endsWith("/")) {
            folder += "/";
        }
        try {
            Files.createDirectories(Paths.get(folder));
        } catch (IOException e) {
            throw new RuntimeException("Unable to create folder " + folder, e);
        }

        return folder + generateFilename(fileName, filenameContext);
    }

    /**
     * Generates a name for an entity using the name template if available
     *
     * @param schema The schema to generate a name for
     * @return The generated name
     */
    private String generateName(Schema schema, String role) {
        String name = schema.getName();
        if (templates.name != null) {
            name = templateEvaluator.evaluateToString(
                    templates.name, extendValuesContext(Map.of("schema", schema, "role", role)));
        }
        return name;
    }

    /**
     * Generates a namespace for an entity using the namespace template if available
     *
     * @param schema The schema to generate a namespace for
     * @return The generated namespace
     */
    private String generateNamespace(Schema schema, String role) {
        String namespace = schema.getNamespace();
        if (templates.namespace != null) {
            namespace = templateEvaluator.evaluateToString(
                    templates.namespace, extendValuesContext(Map.of("schema", schema, "role", role)));
        }
        return namespace;
    }

    /**
     * Generates a full name for an entity using the fullname template if available
     *
     * @param namespace The namespace
     * @param name The name
     * @param schema The schema
     * @return The generated full name
     */
    private String generateFullname(String namespace, String name, Schema schema, String role) {
        String fullname = namespace + "." + name;
        if (templates.namespace != null) {
            fullname = templateEvaluator.evaluateToString(
                    templates.fullname,
                    extendValuesContext(
                            Map.of("namespace", namespace, "name", name, "schema", schema, "role", role)));
        }
        return fullname;
    }

    /**
     * Generates a filename for an entity using the filename template if available
     *
     * @param filename The entity to generate a filename for
     * @param context
     * @return The generated filename
     */
    private String generateFilename(String filename, Map<String, Object> context) {
        if (templates.filename != null) {
            filename = templateEvaluator.evaluateToString(templates.filename, extendValuesContext(context));
        }
        return filename;
    }

    /**
     * Generates a folder path for an entity using the folder template if available
     *
     * @param folder The entity to generate a folder path for
     * @param context
     * @return The generated folder path
     */
    private String generateFolder(String folder, Map<String, Object> context) {
        if (templates.folder != null) {
            folder = templateEvaluator.evaluateToString(templates.folder, extendValuesContext(context));
        }
        return folder;
    }

    /**
     * Generates a post-generation command using the postCall template if available
     *
     * @param files List of generated files
     * @return The generated command or null if no postCall template
     */
    private String generatePostCall(List<String> files) {
        if (this.postCall != null) {
            return templateEvaluator.evaluateToString(this.postCall, extendValuesContext(Map.of("files", files)));
        }
        return null;
    }

    /**
     * Creates a new context map by combining the values context with additional values
     *
     * @param extension Additional context values to add
     * @return Combined context map
     */
    private HashMap<String, Object> extendValuesContext(Map<String, Object> extension) {
        HashMap<String, Object> extendedContext = new HashMap<>(valuesContext);
        extendedContext.putAll(extension);
        return extendedContext;
    }

    /**
     * Gets the name of this generator
     *
     * @return The generator name
     */
    public String getName() {
        return name;
    }

    /**
     * Record containing templates for generating names and paths
     */
    public record Templates(
            PebbleTemplate namespace,
            PebbleTemplate name,
            PebbleTemplate fullname,
            PebbleTemplate folder,
            PebbleTemplate filename) {
    }
}
