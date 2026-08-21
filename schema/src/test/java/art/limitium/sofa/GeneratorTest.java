package art.limitium.sofa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorTest {
    @TempDir
    Path tempDir;
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Test
    void shouldGenerateAllTemplateTypes() throws IOException {
        // Given
        String configPath = copyTestResources("test-config.yaml", "schemas", "templates");
        
        // When
        Factory.main(new String[]{configPath});
        
        // Then
        verifyGeneratedFiles(
            "Address.json",
                "Cart.json",
                "CartItem.json",
                "CustomerInfo.json",
                "Order.json",
                "OrderItem.json",
                "OrderStatus.json",
                "Product.json"
        );
    }

    @Test
    void shouldLoadSchemasFromExternalLibraryAndLocal() throws IOException {
        // Given
        String configPath = copyTestResources("test-config-external.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then
        verifyGeneratedFiles(
                "ExternalBatch.json",
                "ExternalRecord.json",
                "Address.json",
                "Cart.json",
                "CartItem.json",
                "CustomerInfo.json",
                "Order.json",
                "OrderItem.json",
                "OrderStatus.json",
                "Product.json"
        );
    }

    @Test
    void shouldApplyBlackFilterByFqcnAndPackage() throws IOException {
        // Given
        String configPath = copyTestResources("test-config-black-filter.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then
        verifyGeneratedFiles(
                "Address.json",
                "CustomerInfo.json",
                "OrderStatus.json",
                "Product.json"
        );
        verifyNotGeneratedFiles("Order.json", "Cart.json", "CartItem.json");
    }

    @Test
    void shouldSkipWriteButKeepEntityForRelations() throws IOException {
        String configPath = copyTestResources("test-config-skip-write.yaml", "schemas", "templates");

        Factory.main(new String[]{configPath});

        Path generatedOrderItem = tempDir.resolve("generated").resolve("OrderItemEntity.json");
        assertTrue(Files.exists(generatedOrderItem), "Generated file not found: OrderItemEntity.json");
        String content = Files.readString(generatedOrderItem);
        assertTrue(content.contains("\"owner\": \"Order\""), "Expected owner to use original name from overrides when skipWrite is active");
        verifyNotGeneratedFiles("OrderEntity.json");
    }

    @Test
    void shouldGenerateDependentForPolymorphicRecordWithoutAnyOwner() throws IOException {
        // Given a record annotated `ownership: polymorphic` that nothing in this module owns
        String configPath = copyTestResources("test-config-annotations.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then it still resolves to the dependent template, with the polymorphic owner pair
        String car = Files.readString(tempDir.resolve("generated").resolve("Car.json"));
        assertTrue(car.contains("\"type\": \"dependent\""), "Polymorphic record should use the dependent template");
        assertTrue(car.contains("\"ownerEntity\""), "Polymorphic record should carry ownerEntity");
        assertTrue(car.contains("\"ownerId\""), "Polymorphic record should carry ownerId");
        assertTrue(car.contains("\"primaryKey\": \"carId\""), "Primary key marker on the field should resolve");
    }

    @Test
    void shouldTreatDeclaredChildAsCompositeEvenWhenNothingReferencesIt() throws IOException {
        // Given a record annotated `role: child` that nothing in this module references
        String configPath = copyTestResources("test-config-annotations.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then it is a composite rather than the aggregate root the graph alone would make it
        String engine = Files.readString(tempDir.resolve("generated").resolve("Engine.json"));
        assertTrue(engine.contains("\"type\": \"child\""), "Declared child should use the child template, was: " + engine);
    }

    @Test
    void shouldPublishManifestOfGeneratedArtifacts() throws IOException {
        // Given
        String configPath = copyTestResources("test-config-publish.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then the manifest names what each generator produced, keyed by generator path
        Path manifestPath = tempDir.resolve("published/sofa/manifests/com.example.car-lib.json");
        assertTrue(Files.exists(manifestPath), "Manifest not published");

        JsonNode manifest = objectMapper.readTree(Files.readString(manifestPath));
        assertEquals("com.example:car-lib", manifest.get("artifact").asText());
        assertEquals("templates", manifest.get("generators").get(0).asText());
        assertEquals("avro/car/Car.avsc", manifest.get("schemas").get(0).asText());
        assertEquals(
                "com.example.car.entities.pojo.CarEntity",
                manifest.get("records").get("com.example.car.Car").get("templates").get("fullname").asText());
    }

    @Test
    void shouldReferenceImportedArtifactInsteadOfRegeneratingIt() throws IOException {
        // Given a module importing a library that already published a class for Car
        String configPath = copyTestResources("test-config-import.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then the local record points at the library's class and no second Car is written
        String garage = Files.readString(tempDir.resolve("generated").resolve("Garage.json"));
        assertTrue(garage.contains("\"elementType\": \"CarEntity\""),
                "Owner should reference the imported artifact, was: " + garage);
        verifyNotGeneratedFiles("Car.json", "CarEntity.json");
    }

    @Test
    void shouldFailWhenImportRanGeneratorButPublishedNothingForRecord() throws IOException {
        // Given a library that ran `templates` yet published no artifact for Engine under it,
        // which means Engine resolved to different roles in the two modules
        String configPath = copyTestResources("test-config-import-gap.yaml", "schemas", "templates");

        // When / Then
        RuntimeException e = assertThrows(RuntimeException.class, () -> Factory.main(new String[]{configPath}));
        assertTrue(e.getMessage().contains("published no artifact for `com.example.car.Engine`"), e.getMessage());
        assertTrue(e.getMessage().contains("role"), "Error should point at the annotation that fixes it: " + e.getMessage());
    }

    @Test
    void shouldKeepPolymorphicOwnershipEvenWhenExactlyOneOwnerExists() throws IOException {
        // Given a polymorphic record that this module also happens to own through exactly one owner
        String configPath = copyTestResources("test-config-polymorphic-with-owner.yaml", "schemas", "templates");

        // When
        Factory.main(new String[]{configPath});

        // Then the annotation wins over the single named foreign key the graph alone implies
        String car = Files.readString(tempDir.resolve("generated").resolve("Car.json"));
        assertTrue(car.contains("\"ownerEntity\""), "Annotation should win over the single owner: " + car);
        assertFalse(car.contains("garageId"), "Should not fall back to a named foreign key: " + car);
    }

    @Test
    void shouldGenerateLocallyWhenImportNeverRanThatGenerator() throws IOException {
        // Given a generator path the imported library never ran, so there is nothing to collide with
        String configPath = copyTestResources("test-config-import-new-generator.yaml", "schemas", "templates-base");

        // When
        Factory.main(new String[]{configPath});

        // Then the imported record is generated locally rather than borrowed or rejected
        Path car = tempDir.resolve("generated").resolve("Car.local.json");
        assertTrue(Files.exists(car), "Import should not block a generator the library never ran");
        assertTrue(Files.readString(car).contains("com.example.car.local"),
                "Locally generated record should use this module's naming");
    }

    @Test
    void shouldRejectUnsupportedAnnotationWhileLoadingSchemas() throws IOException {
        // Given a schema carrying an unsupported annotation value
        String configPath = copyTestResources("test-config-bad-annotation.yaml", "schemas", "templates");

        // When / Then the whole run fails at schema load, not mid render
        RuntimeException e = assertThrows(RuntimeException.class, () -> Factory.main(new String[]{configPath}));
        assertTrue(rootCauseMessage(e).contains("supported values: polymorphic"), rootCauseMessage(e));
    }

    @Test
    void shouldFailWhenImportPublishesNoManifest() throws IOException {
        // Given an import whose manifest is not on the classpath
        String configPath = copyTestResources("test-config-missing-import.yaml", "schemas", "templates");

        // When / Then
        RuntimeException e = assertThrows(RuntimeException.class, () -> Factory.main(new String[]{configPath}));
        assertTrue(e.getMessage().contains("publishes no manifest"), e.getMessage());
    }

    @Test
    void shouldFailOnMalformedImportCoordinate() throws IOException {
        // Given an import that is not a `groupId:artifactId` coordinate
        String configPath = copyTestResources("test-config-bad-coordinate.yaml", "schemas", "templates");

        // When / Then
        RuntimeException e = assertThrows(RuntimeException.class, () -> Factory.main(new String[]{configPath}));
        assertTrue(e.getMessage().contains("is not a `groupId:artifactId` coordinate"), e.getMessage());
    }

    @Test
    void shouldFailWhenManifestDeclaresNoArtifact() throws IOException {
        // Given a manifest section without the coordinate it publishes under
        String configPath = copyTestResources("test-config-manifest-no-artifact.yaml", "schemas", "templates");

        // When / Then
        RuntimeException e = assertThrows(RuntimeException.class, () -> Factory.main(new String[]{configPath}));
        assertTrue(e.getMessage().contains("`manifest.artifact` is required"), e.getMessage());
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "" : cause.getMessage();
    }

    @Test
    void shouldPublishBothCommonAndEntityArtifactsForTheSameRecord() throws IOException {
        // Given a library that runs the same two generator paths its consumers will run
        String configPath = copyTestResources(
                "test-config-publish-full.yaml", "schemas", "templates-common", "templates-entities");

        // When
        Factory.main(new String[]{configPath});

        // Then one record yields one artifact per generator, because common and entity shapes differ
        Path common = tempDir.resolve("generated/common/Car.json");
        Path entity = tempDir.resolve("generated/entities/CarEntity.json");
        assertTrue(Files.exists(common), "Library should publish the common shape");
        assertTrue(Files.exists(entity), "Library should publish the entity shape");
        assertTrue(Files.readString(common).contains("\"type\": \"child\""));
        assertTrue(Files.readString(entity).contains("\"type\": \"dependent\""));

        // And the manifest this module publishes is the one consumers resolve from the classpath,
        // so any drift between the two breaks here rather than in a consumer's build
        JsonNode published = objectMapper.readTree(
                Files.readString(tempDir.resolve("published/sofa/manifests/com.example.carlib.json")));
        JsonNode onClasspath = objectMapper.readTree(readResource("/sofa/manifests/com.example.carlib.json"));
        assertEquals(onClasspath, published, "Published manifest drifted from the classpath fixture");
    }

    @Test
    void shouldReuseLibraryEntityFromNestedChildInConsumingModule() throws IOException {
        // Given Building -> Garage -> Car, where Garage is a composite nested in Building and Car is
        // published by a library. This is the case that previously produced a second Car locally.
        String configPath = copyTestResources(
                "test-config-import-full.yaml", "schemas", "templates-common", "templates-entities");

        // When
        Factory.main(new String[]{configPath});

        // Then the consumer writes its own records but never a second Car
        assertTrue(Files.exists(tempDir.resolve("generated/common/Garage.json")), "Garage as a composite");
        assertTrue(Files.exists(tempDir.resolve("generated/entities/GarageEntity.json")), "Garage as an owner");
        assertTrue(Files.exists(tempDir.resolve("generated/entities/BuildingEntity.json")), "Building as a root");
        assertFalse(Files.exists(tempDir.resolve("generated/common/Car.json")), "Car must not be regenerated");
        assertFalse(Files.exists(tempDir.resolve("generated/entities/CarEntity.json")), "Car must not be regenerated");

        // And the same record resolves to a different published class per generator, which is why the
        // manifest is keyed by generator path rather than by record alone
        String garageAsChild = Files.readString(tempDir.resolve("generated/common/Garage.json"));
        assertTrue(garageAsChild.contains("java.util.List<com.example.car.common.pojo.Car>"),
                "The composite shape should reference the library's common class: " + garageAsChild);

        String garageAsOwner = Files.readString(tempDir.resolve("generated/entities/GarageEntity.json"));
        assertTrue(garageAsOwner.contains("\"elementType\": \"CarEntity\""),
                "The entity shape should reference the library's entity class: " + garageAsOwner);
        assertTrue(garageAsOwner.contains("\"ownedTypes\""), "Garage still owns Car in the consumer");
    }

    @Test
    void shouldPublishGeneratorPathsThatProducedNothing() throws IOException {
        // Given a library whose common generator matches none of its records
        String configPath = copyTestResources(
                "test-config-publish-empty-generator.yaml", "schemas", "templates-common", "templates-entities");

        // When
        Factory.main(new String[]{configPath});

        // Then the path is still published. Without it a consumer cannot tell "never ran" from "ran
        // and emitted nothing", and would silently generate its own copy of a library owned record.
        JsonNode manifest = objectMapper.readTree(
                Files.readString(tempDir.resolve("published/sofa/manifests/com.example.emptygen.json")));

        List<String> generators = new ArrayList<>();
        manifest.get("generators").forEach(node -> generators.add(node.asText()));
        assertTrue(generators.contains("templates-common"),
                "A generator that ran but produced nothing must still be listed, was: " + generators);
        assertTrue(generators.contains("templates-entities"), generators.toString());

        JsonNode product = manifest.get("records").get("com.example.product.Product");
        assertTrue(product.has("templates-entities"), "Entity shape should be published");
        assertFalse(product.has("templates-common"), "Common generator produced nothing for it");
    }

    @Test
    void shouldGenerateBothWorldsFromOneSchemaWithoutCrossReferences() throws IOException {
        // Given Building and Angar both embedding the composite Garage, which holds polymorphic Cars
        String configPath = copyTestResources(
                "test-config-two-worlds.yaml", "schemas", "templates-msg", "templates-ent");

        // When
        Factory.main(new String[]{configPath});

        // Then the message world is denormalized: the composite carries the cars inline, and the
        // inlined car has no ownership fields because nesting already says who owns it
        String msgGarage = Files.readString(tempDir.resolve("generated/messages/Garage.json"));
        assertTrue(msgGarage.contains("java.util.List<com.example.car.messages.Car>"), msgGarage);
        String msgCar = Files.readString(tempDir.resolve("generated/messages/Car.json"));
        assertFalse(msgCar.contains("ownerEntity"), "Inlined car should carry no ownership: " + msgCar);

        // And the entity world is normalized: the composite drops the collection entirely and the
        // link lives on the car, pointing at whichever root encloses it
        String entGarage = Files.readString(tempDir.resolve("generated/entities/GarageEntity.json"));
        assertFalse(entGarage.contains("cars"), "Entity composite should not carry the collection: " + entGarage);
        String entCar = Files.readString(tempDir.resolve("generated/entities/CarEntity.json"));
        assertTrue(entCar.contains("\"ownerEntity\""), entCar);
        assertTrue(entCar.contains("BuildingEntity") && entCar.contains("AngarEntity"),
                "Ownership should walk through the composite to both roots: " + entCar);

        // And each generator produced its own closure, so neither world references the other
        assertFalse(msgGarage.contains("entities"), "Message world must not reach into entities");
        assertFalse(entGarage.contains("messages"), "Entity world must not reach into messages");
    }

    @Test
    void shouldNameByRoleSoCompositesKeepTheirPlainName() throws IOException {
        // Given naming templates that branch on `role`
        String configPath = copyTestResources(
                "test-config-role-naming.yaml", "schemas", "templates-msg", "templates-ent");

        // When
        Factory.main(new String[]{configPath});

        // Then the composite keeps its plain name alongside the entities, which take the suffix
        String garage = Files.readString(tempDir.resolve("generated/entities/Garage.json"));
        assertTrue(garage.contains("com.example.yard2.entities.Garage"), garage);

        assertTrue(Files.exists(tempDir.resolve("generated/entities/CarEntity.json")), "Entities keep the suffix");
        assertTrue(Files.exists(tempDir.resolve("generated/entities/BuildingEntity.json")));

        // And roots reference the composite under its role driven name
        String building = Files.readString(tempDir.resolve("generated/entities/BuildingEntity.json"));
        assertTrue(building.contains("\"garage\": \"com.example.yard2.entities.Garage\""), building);
    }

    @Test
    void shouldKeepDeclaredChildOutOfEveryEntityRole() throws IOException {
        // Given a composite that holds an array of records and sits inside two roots, which would
        // otherwise make it an owner and, through its two parents, a polymorphic dependent
        String configPath = copyTestResources(
                "test-config-two-worlds.yaml", "schemas", "templates-msg", "templates-ent");

        // When
        Factory.main(new String[]{configPath});

        // Then it stays a composite in both worlds
        assertTrue(Files.readString(tempDir.resolve("generated/entities/GarageEntity.json"))
                .contains("\"type\": \"child\""), "Declared child must not become an owner or dependent");
        assertTrue(Files.readString(tempDir.resolve("generated/messages/Garage.json"))
                .contains("\"type\": \"child\""));
    }

    @Test
    void shouldPublishBothWorldsForALibraryRecordWithNoOwnersOfItsOwn() throws IOException {
        // Given a library whose only record is a polymorphic entity nothing there owns
        String configPath = copyTestResources(
                "test-config-publish-worlds.yaml", "schemas", "templates-msg", "templates-ent");

        // When
        Factory.main(new String[]{configPath});

        // Then it publishes one artifact per world
        String inlined = Files.readString(tempDir.resolve("generated/messages/Car.json"));
        assertTrue(inlined.contains("com.example.car.libmsg.Car"), inlined);
        assertFalse(inlined.contains("ownerEntity"), "The inlined shape carries no ownership: " + inlined);

        String row = Files.readString(tempDir.resolve("generated/entities/CarEntity.json"));
        assertTrue(row.contains("com.example.car.libent.CarEntity"), row);
        assertTrue(row.contains("\"ownerEntity\""), row);

        // And the owner list is empty rather than absent. A library commits to polymorphic ownership
        // before any owner exists, so templates must still get a list to iterate here.
        assertTrue(row.contains("\"ownedBy\": []"), "Expected an empty owner list, was: " + row);

        JsonNode manifest = objectMapper.readTree(
                Files.readString(tempDir.resolve("published/sofa/manifests/com.example.carworlds.json")));
        JsonNode car = manifest.get("records").get("com.example.car.Car");
        assertEquals("com.example.car.libmsg.Car", car.get("templates-msg").get("fullname").asText());
        assertEquals("com.example.car.libent.CarEntity", car.get("templates-ent").get("fullname").asText());
    }

    @Test
    void shouldBorrowLibraryRecordInBothWorlds() throws IOException {
        // Given Building and Angar embedding a composite whose cars come from the library
        String configPath = copyTestResources(
                "test-config-import-worlds.yaml", "schemas", "templates-msg", "templates-ent");

        // When
        Factory.main(new String[]{configPath});

        // Then each world borrows its own counterpart. The library's namespaces differ from this
        // module's, so borrowing is distinguishable from having regenerated a local copy.
        String msgGarage = Files.readString(tempDir.resolve("generated/messages/Garage.json"));
        assertTrue(msgGarage.contains("java.util.List<com.example.car.libmsg.Car>"),
                "Message composite should inline the library's message class: " + msgGarage);

        String entGarage = Files.readString(tempDir.resolve("generated/entities/Garage.json"));
        assertTrue(entGarage.contains("com.example.yard2.entities.Garage"), entGarage);
        assertFalse(entGarage.contains("cars"), "Entity composite drops the collection: " + entGarage);

        // And nothing of the library's is regenerated here
        assertFalse(Files.exists(tempDir.resolve("generated/messages/Car.json")));
        assertFalse(Files.exists(tempDir.resolve("generated/entities/CarEntity.json")));

        // While this module's own records are generated normally
        String building = Files.readString(tempDir.resolve("generated/entities/BuildingEntity.json"));
        assertTrue(building.contains("\"garage\": \"com.example.yard2.entities.Garage\""), building);
        assertTrue(Files.exists(tempDir.resolve("generated/entities/AngarEntity.json")));
    }

    @Test
    void shouldProduceTheSameClassesWhetherOrNotTheRecordLivesInALibrary() throws IOException {
        // Given the same schemas generated twice: once all in one module, once with Car published by
        // a library and imported. Both use the same naming conventions.
        String singlePath = copyTestResources(
                "test-config-role-naming.yaml", "schemas", "templates-msg", "templates-ent");
        Factory.main(new String[]{singlePath});
        Map<String, JsonNode> single = readGenerated(tempDir.resolve("generated"));
        deleteRecursively(tempDir.resolve("generated"));

        // When the library and the consumer are generated separately
        Factory.main(new String[]{copyTestResources("test-config-publish-same.yaml")});
        Factory.main(new String[]{copyTestResources("test-config-import-same.yaml")});
        Map<String, JsonNode> split = readGenerated(tempDir.resolve("generated"));

        // Then the same set of classes exists, split across the two modules
        assertEquals(single.keySet(), split.keySet(), "Splitting the module changed which classes exist");

        // And every class has the same shape. `ownedBy` is excluded: it reports which entities reach
        // the record in this run, and a library cannot know its future owners. That is the whole
        // reason the record declares polymorphic ownership rather than a named foreign key, and the
        // ownerEntity/ownerId pair those templates emit is compared like everything else.
        single.forEach((name, expected) -> {
            JsonNode actual = split.get(name);
            ((com.fasterxml.jackson.databind.node.ObjectNode) expected).remove("ownedBy");
            ((com.fasterxml.jackson.databind.node.ObjectNode) actual).remove("ownedBy");
            assertEquals(expected, actual, "Class shape changed when moved into a library: " + name);
        });
    }

    private Map<String, JsonNode> readGenerated(Path root) throws IOException {
        Map<String, JsonNode> byRelativePath = new java.util.TreeMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                byRelativePath.put(root.relativize(file).toString(), objectMapper.readTree(Files.readString(file)));
            }
        }
        return byRelativePath;
    }

    private void deleteRecursively(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private String copyTestResources(String configFile, String... directories) throws IOException {
        // Copy config file
        String configContent = readResource("/generator-test/" + configFile);
        Path configPath = tempDir.resolve(configFile);
        Files.writeString(configPath, configContent);
        
        // Copy directories
        for (String dir : directories) {
            copyDirectory("/generator-test/" + dir, tempDir.resolve(dir));
        }
        
        return configPath.toString();
    }
    
    private void verifyGeneratedFiles(String... fileNames) throws IOException {
        Path outputDir = tempDir.resolve("generated");
        
        for (String fileName : fileNames) {
            Path generatedFile = outputDir.resolve(fileName);
            Path expectedFile = Path.of("src/test/resources/generator-test/expected/" + fileName);
            
            assertTrue(Files.exists(generatedFile), "Generated file not found: " + fileName);
            
            JsonNode generated = parseAndFormat(generatedFile);
            JsonNode expected = parseAndFormat(expectedFile);
            
            assertEquals(
                expected,
                generated,
                "Generated file doesn't match expected for: " + fileName
            );
        }
    }

    private void verifyNotGeneratedFiles(String... fileNames) {
        Path outputDir = tempDir.resolve("generated");

        for (String fileName : fileNames) {
            Path generatedFile = outputDir.resolve(fileName);
            assertFalse(Files.exists(generatedFile), "Generated file should not exist: " + fileName);
        }
    }
    
    private JsonNode parseAndFormat(Path jsonFile) throws IOException {
        return objectMapper.readTree(Files.readString(jsonFile));
    }
    
    private String readResource(String path) throws IOException {
        return new String(getClass().getResourceAsStream(path).readAllBytes());
    }
    
    private void copyDirectory(String resourcePath, Path targetPath) throws IOException {
        Files.createDirectories(targetPath);
        
        // List resources in the directory
        List<String> resources = ResourceUtils.listResources(resourcePath);
        
        for (String resource : resources) {
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            String content = readResource(resource);
            Files.writeString(targetPath.resolve(fileName), content);
        }
    }
}