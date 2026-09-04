package art.limitium.sofa;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled generators, all of them, over a graph with carriers in it.
 * <p>
 * <pre>
 *   Building -> [Level] -> Zone -> Garage -> [Car]
 *   Angar              -> Zone -> Garage -> [Car]
 * </pre>
 * Zone and Garage are carriers: composites that own the cars, Garage by holding them and Zone
 * through Garage, while staying embedded rather than becoming rows. Address, Dimensions and Toolbox
 * sit beside that path and own nothing.
 * <p>
 * Each generator answers the same question differently - a class per composite, a column per
 * flattened field, a builder that has to instantiate what the pojo declares - so a carrier is only
 * right when all of them agree about it. That is what this pins down, generator by generator.
 */
class CarrierGeneratorsTest {
    @TempDir
    static Path tempDir;

    private static Path generated;

    @BeforeAll
    static void generate() throws IOException {
        String config = new String(CarrierGeneratorsTest.class
                .getResourceAsStream("/generator-test/test-config-generators.yaml").readAllBytes());
        Files.writeString(tempDir.resolve("test-config-generators.yaml"), config);

        Path schemas = Files.createDirectories(tempDir.resolve("schemas"));
        for (String resource : ResourceUtils.listResources("/generator-test/schemas")) {
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            Files.writeString(schemas.resolve(name), new String(
                    CarrierGeneratorsTest.class.getResourceAsStream(resource).readAllBytes()));
        }

        Factory.main(new String[]{tempDir.resolve("test-config-generators.yaml").toString()});
        generated = tempDir.resolve("generated");
    }

    @Test
    void shouldWriteACarrierInBothWorldsAndShareTheCompositesBesideIt() throws IOException {
        // The shared pojo keeps the cars inline, because the denormalized world is one document
        assertTrue(pojo("common", "yard3", "Garage").contains("java.util.List<com.example.car.common.pojo.Car>"),
                "The shared composite should still inline the cars");

        // The entity world writes the carrier again, without them: there they are rows
        assertFalse(pojo("entities", "yard3", "Garage").contains("cars"),
                "The carrier should not inline rows the entity world stores separately");

        // While the composites beside the path exist once, in the shared world only
        for (String composite : List.of("Address", "Dimensions", "Toolbox")) {
            assertTrue(Files.exists(pojoPath("common", "yard3", composite)), composite + " should be shared");
            assertFalse(Files.exists(pojoPath("entities", "yard3", composite)),
                    composite + " owns nothing, so the entity world has no reason to write it again");
        }
    }

    @Test
    void shouldTypeTheEntityPojosThroughTheCarriersAndAroundThem() throws IOException {
        // Following the chain down from the entity, every carrier is the entity world's own class
        assertTrue(pojo("entities", "yard3", "Level").contains("com.example.yard3.entities.pojo.Zone"),
                "The entity should hold the entity world's carrier");
        assertTrue(pojo("entities", "yard3", "Zone").contains("com.example.yard3.entities.pojo.Garage"),
                "A carrier holds the carrier below it, or the shared one would drag the cars back in");

        // And everything off the chain is the shared class, including from inside a carrier
        assertTrue(pojo("entities", "yard3", "Garage").contains("com.example.yard3.common.pojo.Toolbox"),
                "A composite inside a carrier is still shared");
        assertTrue(pojo("entities", "yard3", "Building").contains("com.example.yard3.common.pojo.Address"));
    }

    @Test
    void shouldInstantiateCarriersFromTheEntityWorldInBuilders() throws IOException {
        // The builder assigns into the pojo's fields, so it has to pick the same class the pojo
        // declares. This is where borrowing the shared carrier stops compiling.
        String builder = read(generated.resolve("java/main/com/example/yard3/entities/builder/LevelBuilder.java"));
        assertTrue(builder.contains("new com.example.yard3.entities.pojo.Zone()"), builder);
        assertTrue(builder.contains("new com.example.yard3.entities.pojo.Garage()"), builder);
        assertTrue(builder.contains("new com.example.yard3.common.pojo.Toolbox()"),
                "The composite beside the path is still built from the shared class");
    }

    @Test
    void shouldFlattenTheCarrierChainIntoColumnsWithoutTheCollection() throws IOException {
        // Generators that flatten never name a composite's class, so they were always right about
        // the fields. What they must not do is carry the collection into the row.
        String table = read(generated.resolve(
                "liquibase/1.0-create-table-COM.EXAMPLE.YARD3.ENTITIES.LIQUIBASE_LEVEL.xml"));
        assertTrue(table.contains("ZONE_CODE") && table.contains("ZONE_GARAGE_CAPACITY"),
                "The chain should flatten into the enclosing row: " + table);
        assertTrue(table.contains("ZONE_GARAGE_TOOLBOX_BRAND"), table);
        assertFalse(table.contains("CARS"), "The cars have a table of their own: " + table);
        assertTrue(table.contains("BUILDING_ID"), "And the row still links to its owner: " + table);

        String struct = read(generated.resolve(
                "java/main/com/example/yard3/entities/converter/LevelConverter.java"));
        assertTrue(struct.contains("ZONE_GARAGE_CAPACITY") && !struct.contains("CARS"), struct);

        String flatbuffer = read(generated.resolve("fbs/com.example.yard3.entities.fb.FbLevel.fbs"));
        assertTrue(flatbuffer.contains("zone_garage_capacity") && !flatbuffer.contains("cars"), flatbuffer);
    }

    @Test
    void shouldStoreTheCarsTheCarrierOwnsAsRowsOfTheirOwn() throws IOException {
        // Two roots reach the same carrier, so no single foreign key names the owner
        String car = pojo("entities", "car", "Car");
        assertTrue(car.contains("ownerEntity") && car.contains("ownerId"), car);

        String table = read(generated.resolve(
                "liquibase/1.0-create-table-COM.EXAMPLE.CAR.ENTITIES.LIQUIBASE_CAR.xml"));
        assertTrue(table.contains("OWNER_ENTITY") && table.contains("OWNER_ID"), table);

        String serde = read(generated.resolve("java/main/com/example/car/entities/serde/CarSerde.java"));
        assertTrue(serde.contains("Serde<com.example.car.entities.pojo.Car>"), serde);
    }

    @Test
    void shouldLeaveTheDenormalizedWorldFat() throws IOException {
        // Nothing above changes the other world: it keeps taking the carrier from the shared
        // generator, collection and all, and its table has a column where the entity world has rows
        assertFalse(Files.exists(pojoPath("messages", "yard3", "Zone")),
                "The fat world has no carrier of its own to write");
        assertTrue(pojo("messages", "yard3", "Building").contains("com.example.yard3.common.pojo.Address"));

        String table = read(generated.resolve(
                "liquibase/1.0-create-table-COM.EXAMPLE.YARD3.MESSAGES.LIQUIBASE_BUILDING.xml"));
        assertTrue(table.contains("LEVELS"), "The message table keeps the collection: " + table);
    }

    @Test
    void shouldStereotypeCarriersInTheDiagram() throws IOException {
        String diagram = read(generated.resolve("puml/schema.puml"));
        assertTrue(diagram.contains("class com.example.yard3.Garage <<carrier>>"), diagram);
        assertTrue(diagram.contains("class com.example.yard3.Zone <<carrier>>"), diagram);
        assertTrue(diagram.contains("class com.example.yard3.Toolbox <<child>>"),
                "A composite that owns nothing is still a plain child");
        assertTrue(diagram.contains("class com.example.yard3.Level <<dependent>>"), diagram);
    }

    private static Path pojoPath(String world, String namespace, String name) {
        return generated.resolve("java/main/com/example/" + namespace + "/" + world + "/pojo/" + name + ".java");
    }

    private static String pojo(String world, String namespace, String name) throws IOException {
        return read(pojoPath(world, namespace, name));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "Not generated: " + tempDir.relativize(path));
        return Files.readString(path);
    }
}
