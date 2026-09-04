package art.limitium.sofa;

import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaDefinitionTest {

    private static final String GARAGE_HOLDING_CARS = """
            {
              "type": "record",
              "name": "Building",
              "namespace": "com.example.yard",
              "fields": [
                {"name": "buildingId", "type": "string", "primary": true},
                {
                  "name": "garage",
                  "type": {
                    "type": "record",
                    "name": "Garage",
                    "fields": [
                      {"name": "capacity", "type": "int"},
                      {
                        "name": "cars",
                        "type": {
                          "type": "array",
                          "items": {
                            "type": "record",
                            "name": "Car",
                            "fields": [{"name": "carId", "type": "string", "primary": true}]
                          }
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """;

    private static final String SECOND_ROOT_ON_THE_SAME_GARAGE = """
            {
              "type": "record",
              "name": "Angar",
              "namespace": "com.example.yard",
              "fields": [
                {"name": "angarId", "type": "string", "primary": true},
                {"name": "garage", "type": "com.example.yard.Garage"}
              ]
            }
            """;

    private static List<String> ownersOf(SchemaDefinition definition, String fullname) {
        return definition.records.get(fullname).owners.stream().map(AvroEntity::getFullname).toList();
    }

    @Test
    void shouldOwnACollectionOnceWhenItsHolderIsReachedFromASingleParent() {
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        definition.addRecord(parser.parse(GARAGE_HOLDING_CARS));

        assertEquals(List.of("com.example.yard.Garage"), ownersOf(definition, "com.example.yard.Car"));
    }

    @Test
    void shouldOwnACollectionOnceWhenItsHolderIsReachedFromTwoParents() {
        // Given a composite that two roots embed, so its fields are visited once per root
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();

        // When
        definition.addRecord(parser.parse(GARAGE_HOLDING_CARS));
        definition.addRecord(parser.parse(SECOND_ROOT_ON_THE_SAME_GARAGE));

        // Then the car is owned by the garage once, not once per path that reaches it
        assertEquals(List.of("com.example.yard.Garage"), ownersOf(definition, "com.example.yard.Car"));

        // And both roots still depend on the composite
        assertTrue(definition.records.get("com.example.yard.Building").dependencies.containsKey("com.example.yard.Garage"));
        assertTrue(definition.records.get("com.example.yard.Angar").dependencies.containsKey("com.example.yard.Garage"));
    }

    @Test
    void shouldTerminateOnARecordThatHoldsACollectionOfItself() {
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        Schema tree = parser.parse("""
                {
                  "type": "record",
                  "name": "Node",
                  "namespace": "com.example.tree",
                  "fields": [
                    {"name": "nodeId", "type": "string", "primary": true},
                    {"name": "children", "type": {"type": "array", "items": "com.example.tree.Node"}}
                  ]
                }
                """);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> definition.addRecord(tree));

        assertEquals(List.of("com.example.tree.Node"), ownersOf(definition, "com.example.tree.Node"));
    }

    private static final String ANNOTATED_YARD = """
            {
              "type": "record",
              "name": "Building",
              "namespace": "com.example.yard",
              "fields": [
                {"name": "buildingId", "type": "string", "primary": true},
                {"name": "state", "type": {"type": "enum", "name": "State", "symbols": ["NEW", "OLD"]}},
                {
                  "name": "garage",
                  "type": {
                    "type": "record",
                    "name": "Garage",
                    "role": "child",
                    "fields": [
                      {"name": "capacity", "type": "int"},
                      {
                        "name": "cars",
                        "type": {
                          "type": "array",
                          "items": {
                            "type": "record",
                            "name": "Car",
                            "ownership": "polymorphic",
                            "fields": [{"name": "carId", "type": "string", "primary": true}]
                          }
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """;

    private static String roleOf(SchemaDefinition definition, String fullname) {
        return definition.records.get(fullname).getRole();
    }

    @Test
    void shouldGiveEveryRecordItsMostSpecificRole() {
        // Given the two world graph, where a composite holds a polymorphically owned collection
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        definition.addRecord(parser.parse(ANNOTATED_YARD));
        definition.addRecord(parser.parse(SECOND_ROOT_ON_THE_SAME_GARAGE));
        definition.findRoots();

        assertEquals("root", roleOf(definition, "com.example.yard.Building"));
        assertEquals("root", roleOf(definition, "com.example.yard.Angar"));
        // The composite is pinned out of owner-ness by its annotation, yet it still holds the
        // collection, which makes it a carrier: a composite no world can share
        assertEquals("carrier", roleOf(definition, "com.example.yard.Garage"));
        // And the car is a dependent even though nothing here names it as a foreign key
        assertEquals("dependent", roleOf(definition, "com.example.yard.Car"));
        assertEquals("enum", roleOf(definition, "com.example.yard.State"));
    }

    @Test
    void shouldCallANonRootHolderOfACollectionAnOwner() {
        // Given the same shape without the annotations
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        definition.addRecord(parser.parse(GARAGE_HOLDING_CARS));
        definition.findRoots();

        assertEquals("root", roleOf(definition, "com.example.yard.Building"));
        assertEquals("owner", roleOf(definition, "com.example.yard.Garage"));
        assertEquals("dependent", roleOf(definition, "com.example.yard.Car"));
    }

    @Test
    void shouldRankRolesMostSpecificFirstSoGeneratorsCanNarrowToTheirTemplates() {
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        definition.addRecord(parser.parse(GARAGE_HOLDING_CARS));
        definition.findRoots();

        // A root that owns a collection qualifies for both, and roots win
        assertEquals(List.of("root", "record"), definition.records.get("com.example.yard.Building").getRoles());
        // A non-root holder is an owner first, and still renderable by a record only generator.
        // It is not a dependent: it sits in a record field, and only array membership makes owners
        assertEquals(List.of("owner", "child", "record"),
                definition.records.get("com.example.yard.Garage").getRoles());
        assertEquals(List.of("dependent", "child", "record"),
                definition.records.get("com.example.yard.Car").getRoles());
    }

    @Test
    void shouldRankCarrierNextToTheOwnerRoleItDerivesFrom() {
        // Given a composite chain where only the innermost composite holds the collection
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        definition.addRecord(parser.parse("""
                {
                  "type": "record",
                  "name": "Building",
                  "namespace": "com.example.yard",
                  "fields": [
                    {"name": "buildingId", "type": "string", "primary": true},
                    {"name": "address", "type": {
                      "type": "record",
                      "name": "Address",
                      "fields": [{"name": "city", "type": "string"}]
                    }},
                    {"name": "zone", "type": {
                      "type": "record",
                      "name": "Zone",
                      "fields": [
                        {"name": "code", "type": "string"},
                        {"name": "garage", "type": {
                          "type": "record",
                          "name": "Garage",
                          "role": "child",
                          "fields": [
                            {"name": "cars", "type": {
                              "type": "array",
                              "items": {
                                "type": "record",
                                "name": "Car",
                                "fields": [{"name": "carId", "type": "string", "primary": true}]
                              }
                            }}
                          ]
                        }}
                      ]
                    }}
                  ]
                }
                """));
        definition.findRoots();

        // Then the holder carries: it owns the cars the way any owner does, but the annotation left
        // it embedded, so it has no row of its own for them to point at. The composite that only
        // embeds it owns them the same way, one step further up.
        assertEquals(List.of("carrier", "child", "record"),
                definition.records.get("com.example.yard.Garage").getRoles());
        assertEquals(List.of("carrier", "child", "record"),
                definition.records.get("com.example.yard.Zone").getRoles());

        // While a composite beside that path is untouched by the split and stays a plain child
        assertEquals(List.of("child", "record"),
                definition.records.get("com.example.yard.Address").getRoles());

        // And a record placed by an entity role of its own never carries: it is a row, so the
        // records it owns point at it and that role handles them
        assertEquals(List.of("root", "record"), definition.records.get("com.example.yard.Building").getRoles());
        assertEquals(List.of("dependent", "child", "record"),
                definition.records.get("com.example.yard.Car").getRoles());
    }

    @Test
    void shouldRankOwnerAboveDependentForARecordInTheMiddleOfAChain() {
        // Given a record that both sits in a collection and holds one of its own
        Schema.Parser parser = new Schema.Parser();
        SchemaDefinition definition = new SchemaDefinition();
        definition.addRecord(parser.parse("""
                {
                  "type": "record",
                  "name": "Order",
                  "namespace": "com.example.shop",
                  "fields": [
                    {"name": "orderId", "type": "string", "primary": true},
                    {
                      "name": "lines",
                      "type": {
                        "type": "array",
                        "items": {
                          "type": "record",
                          "name": "Cart",
                          "fields": [
                            {"name": "cartId", "type": "string", "primary": true},
                            {
                              "name": "items",
                              "type": {
                                "type": "array",
                                "items": {
                                  "type": "record",
                                  "name": "Item",
                                  "fields": [{"name": "itemId", "type": "string", "primary": true}]
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """));
        definition.findRoots();

        // Then it qualifies for both, and owner wins so the collection it holds is not dropped
        assertEquals(List.of("owner", "dependent", "child", "record"),
                definition.records.get("com.example.shop.Cart").getRoles());
        assertEquals("owner", roleOf(definition, "com.example.shop.Cart"));
        assertEquals("dependent", roleOf(definition, "com.example.shop.Item"));
    }
}
