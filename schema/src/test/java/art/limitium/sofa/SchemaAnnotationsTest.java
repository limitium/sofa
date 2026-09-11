package art.limitium.sofa;

import art.limitium.sofa.schema.SchemaAnnotations;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaAnnotationsTest {

    private static Schema parse(String annotations, String fields) {
        return new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "Car",
                  "namespace": "com.example.car",
                  %s
                  "fields": [%s]
                }
                """.formatted(annotations, fields));
    }

    private static final String PRIMARY_FIELD = """
            {"name": "carId", "type": "string", "primary": true}""";
    private static final String PLAIN_FIELD = """
            {"name": "model", "type": "string"}""";
    private static final String COLLECTION_FIELD = """
            {"name": "wheels", "type": {"type": "array", "items":
              {"type": "record", "name": "Wheel", "fields": [{"name": "position", "type": "string"}]}}}""";

    @Test
    void shouldReadPolymorphicOwnership() {
        Schema schema = parse("\"ownership\": \"polymorphic\",", PRIMARY_FIELD);

        assertTrue(SchemaAnnotations.isPolymorphicallyOwned(schema));
        assertTrue(SchemaAnnotations.suppressesRoot(schema));
        assertFalse(SchemaAnnotations.isDeclaredChild(schema));
    }

    @Test
    void shouldReadChildRole() {
        Schema schema = parse("\"role\": \"child\",", PLAIN_FIELD);

        assertTrue(SchemaAnnotations.isDeclaredChild(schema));
        assertTrue(SchemaAnnotations.suppressesRoot(schema));
        assertFalse(SchemaAnnotations.isPolymorphicallyOwned(schema));
    }

    @Test
    void shouldLeaveUnannotatedRecordsAlone() {
        Schema schema = parse("", PLAIN_FIELD);

        assertFalse(SchemaAnnotations.suppressesRoot(schema));
        SchemaAnnotations.validate(schema);
    }

    @Test
    void shouldRejectUnsupportedOwnership() {
        Schema schema = parse("\"ownership\": \"shared\",", PRIMARY_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("supported values: polymorphic"), e.getMessage());
    }

    @Test
    void shouldRejectUnsupportedRole() {
        Schema schema = parse("\"role\": \"aggregate\",", PLAIN_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("supported values: root, owner, carrier, dependent, child"), e.getMessage());
    }

    @Test
    void shouldReadRootRole() {
        // Given a record a consumer will embed, which the library still wants generated as a root
        Schema schema = parse("\"role\": \"root\",", PRIMARY_FIELD);

        assertTrue(SchemaAnnotations.isDeclaredRoot(schema));

        // Then it is the one role that does not pin the record out of root-ness
        assertFalse(SchemaAnnotations.suppressesRoot(schema));
        assertFalse(SchemaAnnotations.suppressesOwner(schema));
        assertFalse(SchemaAnnotations.suppressesDependent(schema));
        SchemaAnnotations.validate(schema);
    }

    @Test
    void shouldReadOwnerRole() {
        Schema schema = parse("\"role\": \"owner\",", PRIMARY_FIELD + ", " + COLLECTION_FIELD);

        // An owner is a row of its own, so nothing about how it owns is suppressed, only root-ness
        assertTrue(SchemaAnnotations.suppressesRoot(schema));
        assertFalse(SchemaAnnotations.suppressesOwner(schema));
        assertFalse(SchemaAnnotations.suppressesDependent(schema));
        SchemaAnnotations.validate(schema);
    }

    @Test
    void shouldReadCarrierRole() {
        Schema schema = parse("\"role\": \"carrier\",", PLAIN_FIELD);

        assertTrue(SchemaAnnotations.isDeclaredCarrier(schema));

        // A carrier has no row, so it is neither an owner nor an owned entity, the same pin `child`
        // makes; what it adds is carrying where the module can see no collection
        assertTrue(SchemaAnnotations.suppressesRoot(schema));
        assertTrue(SchemaAnnotations.suppressesOwner(schema));
        assertTrue(SchemaAnnotations.suppressesDependent(schema));
        SchemaAnnotations.validate(schema);
    }

    @Test
    void shouldReadDependentRoleAlongsideTheOwnershipItRequires() {
        Schema schema = parse("\"role\": \"dependent\", \"ownership\": \"polymorphic\",", PRIMARY_FIELD);

        assertTrue(SchemaAnnotations.isPolymorphicallyOwned(schema));
        assertTrue(SchemaAnnotations.suppressesRoot(schema));

        // A dependent is stored as rows, so it keeps both the owner and the dependent roles open
        assertFalse(SchemaAnnotations.suppressesOwner(schema));
        assertFalse(SchemaAnnotations.suppressesDependent(schema));
        SchemaAnnotations.validate(schema);
    }

    @Test
    void shouldRejectDependentRoleWithoutOwnership() {
        // Given a record owned by nothing this module can name, and no word on how the link is shaped
        Schema schema = parse("\"role\": \"dependent\",", PRIMARY_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("has no name to take"), e.getMessage());
    }

    @Test
    void shouldRejectDependentRoleOnACollectionHolder() {
        // Given a pin the ladder cannot honour: owner sits above dependent, so the record is an owner
        Schema schema = parse("\"role\": \"dependent\", \"ownership\": \"polymorphic\",",
                PRIMARY_FIELD + ", " + COLLECTION_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("the role above it on the ladder"), e.getMessage());
    }

    @Test
    void shouldRejectOwnerRoleOnARecordThatOwnsNothing() {
        Schema schema = parse("\"role\": \"owner\",", PRIMARY_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("owns nothing"), e.getMessage());
    }

    @Test
    void shouldRejectCombinedAnnotations() {
        Schema schema = parse("\"ownership\": \"polymorphic\", \"role\": \"child\",", PRIMARY_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("mutually exclusive"), e.getMessage());
    }

    @Test
    void shouldReportEveryAnnotationARecordDeclares() {
        assertEquals("role: dependent, ownership: polymorphic",
                SchemaAnnotations.describe(parse("\"role\": \"dependent\", \"ownership\": \"polymorphic\",", PRIMARY_FIELD)));
        assertEquals("role: child", SchemaAnnotations.describe(parse("\"role\": \"child\",", PLAIN_FIELD)));
        assertEquals("", SchemaAnnotations.describe(parse("", PLAIN_FIELD)));
    }

    @Test
    void shouldRejectPolymorphicWithoutPrimaryKey() {
        Schema schema = parse("\"ownership\": \"polymorphic\",", PLAIN_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("needs a primary key"), e.getMessage());
    }

    @Test
    void shouldAcceptPrimaryMarkerOnFieldAndOnFieldType() {
        Schema onField = parse("", """
                {"name": "carId", "type": "string", "primary": true}""");
        Schema onType = parse("", """
                {"name": "carId", "type": {"type": "string", "primary": true}}""");
        Schema neither = parse("", PLAIN_FIELD);

        assertTrue(SchemaAnnotations.isPrimary(onField.getFields().get(0)), "marker on the field itself");
        assertTrue(SchemaAnnotations.isPrimary(onType.getFields().get(0)), "marker inside the field type");
        assertFalse(SchemaAnnotations.isPrimary(neither.getFields().get(0)));
    }

    @Test
    void shouldRejectAnnotationsOnNonRecords() {
        Schema enumSchema = new Schema.Parser().parse("""
                {
                  "type": "enum",
                  "name": "FuelType",
                  "namespace": "com.example.car",
                  "ownership": "polymorphic",
                  "symbols": ["PETROL", "DIESEL"]
                }
                """);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(enumSchema));
        assertTrue(e.getMessage().contains("apply to records only"), e.getMessage());
        assertEquals(Schema.Type.ENUM, enumSchema.getType());
    }
}
