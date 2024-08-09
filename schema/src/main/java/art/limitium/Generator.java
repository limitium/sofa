package art.limitium;

import art.limitium.config.FiltersConfig;
import art.limitium.config.RemapConfig;
import art.limitium.schema.Entity;
import art.limitium.schema.EnumEntity;
import art.limitium.schema.RecordEntity;
import art.limitium.schema.Type;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import org.apache.avro.Schema;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

public class Generator {
    private final String name;
    private final Map<String, PebbleTemplate> mainTemplates;
    public Templates templates;
    private final Map<String, RemapConfig> importRemap;
    private final FiltersConfig filters;
    public PebbleTemplate postCall;
    private final Map<String, Map<String, Entity>> schemas;
    private final String basePath;
    private String folder;

    public Generator(String name, Map<String, PebbleTemplate> mainTemplates, Templates templates, Map<String, RemapConfig> importRemap, FiltersConfig filters, PebbleTemplate postCall, Map<String, Map<String, Entity>> schemas, String basePath) {
        this.name = name;
        this.mainTemplates = mainTemplates;
        this.templates = templates;
        this.importRemap = importRemap;
        this.filters = filters;
        this.postCall = postCall;
        this.schemas = schemas;
        this.basePath = basePath;
    }


    public void evaluateTemplateToFile(PebbleTemplate template, Map<String, Object> context, String filePath) {
        Factory.logger.debug("Evaluate template {} to file {} with context {}", template, filePath, context.keySet());
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            template.evaluate(fileWriter, context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void generate(List<AvroEntity> avroEntities) throws IOException {
        Map<String, Entity> entities = new LinkedHashMap<>();
        Map<String, Entity> mapByAvroName = new HashMap<>();

        for (AvroEntity avroEntity : avroEntities) {
            String namespace = generateNamespace(avroEntity.schema);
            String name = generateName(avroEntity.schema);
            if (importRemap != null && importRemap.containsKey(avroEntity.schema.getFullName())) {
                RemapConfig remapConfig = importRemap.get(avroEntity.schema.getFullName());
                Factory.logger.info("Remap from {} {} to {} {}", namespace, name, remapConfig.namespace, remapConfig.name);
                if (remapConfig.namespace != null) {
                    namespace = remapConfig.namespace;
                }
                if (remapConfig.name != null) {
                    name = remapConfig.name;
                }
            }

            String fullname = generateFullname(namespace, name, avroEntity.schema);

            Factory.logger.debug("Create entity `{}` at `{}` with `{}`", name, namespace, fullname);


            if (avroEntity.schema.getType() == Schema.Type.ENUM) {
                EnumEntity enumEntity = new EnumEntity(namespace, name, fullname, avroEntity.schema, avroEntity.schema.getEnumSymbols());
                entities.put(enumEntity.getFullname(), enumEntity);
                mapByAvroName.put(avroEntity.getFullname(), enumEntity);
            }
            if (avroEntity.schema.getType() == Schema.Type.RECORD) {
                List<RecordEntity.Field> fields = new ArrayList<>();

                for (Schema.Field field : avroEntity.schema.getFields()) {
                    Type type = Type.fromSchema(field.schema(), mapByAvroName);
                    Factory.logger.debug("Create field `{}` from type `{}` to `{}`", field.name(), field.schema().getType(), type);
                    fields.add(new RecordEntity.Field(field.name(), type));
                }

                RecordEntity recordEntity = new RecordEntity(namespace, name, fullname, avroEntity.schema, fields, avroEntity.isRoot);
                mapByAvroName.put(avroEntity.getFullname(), recordEntity);
                entities.put(recordEntity.getFullname(), recordEntity);
            }
        }
        Factory.logger.info("{} Entities created", entities.size());

        for (AvroEntity avroEntity : avroEntities) {
            Entity entity = mapByAvroName.get(avroEntity.getFullname());
            if (entity instanceof RecordEntity recordEntity) {
                //Regular direct dependencies
                for (AvroEntity dependency : avroEntity.dependencies.values()) {
                    recordEntity.getDependencies().add(mapByAvroName.get(dependency.getFullname()));
                }

                //1-N relations
                for (AvroEntity ownerAvro : avroEntity.owners) {
                    Entity ownerEntity = mapByAvroName.get(ownerAvro.getFullname());
                    if (ownerEntity instanceof RecordEntity owner) {
                        recordEntity.getOwners().add(owner);
                    } else {
                        throw new RuntimeException("Record `" + recordEntity.getFullname() + "` can be used only by other record, but used by`" + ownerEntity.getFullname() + "`");
                    }
                }
            }
        }
        Factory.logger.info("Relations created");
        entities.values().stream().filter(RecordEntity.class::isInstance).map(RecordEntity.class::cast)
                .filter(Predicate.not(recordEntity -> recordEntity.getOwners().isEmpty()))
                .flatMap(recordEntity -> recordEntity.getOwners().stream())
                .forEach(RecordEntity::getPrimaryKey);

        List<String> files = new ArrayList<>();
        for (Entity entity : entities.values()) {
            if (filters != null) {
                //@todo: enforce white list items be in scope of work
                if (filters.white != null && !filters.white.isEmpty() && !filters.white.contains(entity.getSchema().getFullName())) {
                    Factory.logger.info("Skip entity `{}` by white filter", entity.getSchema().getFullName());
                    continue;
                }
                if (filters.black != null && filters.black.contains(entity.getSchema().getFullName())) {
                    Factory.logger.info("Skip entity `{}` by black filter", entity.getSchema().getFullName());
                    continue;
                }
            }

            if ((mainTemplates.containsKey("enum") && entity instanceof EnumEntity)
                    || (mainTemplates.containsKey("root") && entity instanceof RecordEntity r && r.isRoot())
                    || (mainTemplates.containsKey("dependent") && entity instanceof RecordEntity re && !re.getOwners().isEmpty())
                    || mainTemplates.containsKey("record")) {

                files.add(generateFor(entity, entities));
            } else {
                Factory.logger.info("Skip entity `{}` no proper template", entity.getFullname());
            }
        }

        schemas.put(name, mapByAvroName);

        String postCall = generatePostCall(files);
        if (postCall != null) {
            if (!postCall.startsWith("/")) {
                postCall = basePath + "/" + postCall;
            }
            Factory.logger.info("Run postCall: {}", postCall);
            Process exec = Runtime.getRuntime().exec(postCall);
            String out = new String(exec.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Factory.logger.info("Output: {}", out);
        }
    }

    public String generateFor(Entity entity, Map<String, Entity> entities) throws IOException {
        Map<String, Object> context = new HashMap<>();
        context.put("namespace", entity.getNamespace());
        context.put("name", entity.getName());
        context.put("entity", entity);
        context.put("entities", entities);

        String generatedFolder = generateFolder(entity);
        folder = basePath + "/" + generatedFolder;
        if (generatedFolder.startsWith("/")) {
            folder = generatedFolder;
        }
        if (!folder.endsWith("/")) {
            folder += "/";
        }
        Files.createDirectories(Paths.get(folder));

        String fileName = folder + generateFilename(entity);
        PebbleTemplate template;
        if (entity instanceof EnumEntity enumEntity) {
            Factory.logger.info("Generate enum {} into {}", enumEntity.getFullname(), fileName);
            context.put("symbols", enumEntity.getSymbols());
            template = mainTemplates.get("enum");

        } else {
            RecordEntity recordEntity = (RecordEntity) entity;
            if (mainTemplates.containsKey("root") && recordEntity.isRoot()) {
                Factory.logger.info("Generate root {} into {}", recordEntity.getFullname(), fileName);
                template = mainTemplates.get("root");
            } else if (mainTemplates.containsKey("dependent") && !recordEntity.getOwners().isEmpty()) {
                Factory.logger.info("Generate dependent {} into {}", recordEntity.getFullname(), fileName);
                template = mainTemplates.get("dependent");
            } else {
                Factory.logger.info("Generate record {} into {}", recordEntity.getFullname(), fileName);
                template = mainTemplates.get("record");
            }
        }
        if (fileName.contains("/")) {
            String[] pathParts = fileName.split("/");
            String folderPath = String.join("/", Arrays.copyOf(pathParts, pathParts.length - 1));
            if (!folderPath.startsWith("/")) {
                folderPath = basePath + "/" + folderPath;
            }

            Files.createDirectories(Paths.get(folderPath));
        }
        evaluateTemplateToFile(template, context, fileName);
        return fileName;
    }


    private String generateName(Schema schema) {
        String name = schema.getName();
        if (templates.name != null) {
            name = Factory.evaluateTemplateToString(templates.name, Collections.singletonMap("schema", schema));
        }
        return name;
    }

    private String generateNamespace(Schema schema) {
        String namespace = schema.getNamespace();
        if (templates.namespace != null) {
            namespace = Factory.evaluateTemplateToString(templates.namespace, Collections.singletonMap("schema", schema));
        }
        return namespace;
    }

    private String generateFullname(String namespace, String name, Schema schema) {
        String fullname = namespace + "." + name;
        if (templates.namespace != null) {
            fullname = Factory.evaluateTemplateToString(templates.fullname, Map.of("namespace", namespace, "name", name, "schema", schema));
        }
        return fullname;
    }

    private String generateFilename(Entity entity) {
        String filename = entity.getName();
        if (templates.filename != null) {
            filename = Factory.evaluateTemplateToString(templates.filename, Map.of("namespace", entity.getNamespace(), "name", entity.getName(), "fullname", entity.getFullname(), "schema", entity.getSchema(), "entity", entity));
        }
        return filename;
    }

    private String generateFolder(Entity entity) {
        String folder = entity.getNamespace();
        if (templates.folder != null) {
            folder = Factory.evaluateTemplateToString(templates.folder, Collections.singletonMap("entity", entity));
        }
        return folder;
    }

    private String generatePostCall(List<String> files) {
        if (this.postCall != null) {
            return Factory.evaluateTemplateToString(this.postCall,
                    Map.of(
                            "files", files,
                            "basePath", basePath
                    ));
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public record Templates(
            PebbleTemplate namespace,
            PebbleTemplate name,
            PebbleTemplate fullname,
            PebbleTemplate folder,
            PebbleTemplate filename) {
    }
}
