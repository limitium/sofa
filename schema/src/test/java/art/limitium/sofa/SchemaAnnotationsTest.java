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
        assertTrue(e.getMessage().contains("supported values: child"), e.getMessage());
    }

    @Test
    void shouldRejectCombinedAnnotations() {
        Schema schema = parse("\"ownership\": \"polymorphic\", \"role\": \"child\",", PRIMARY_FIELD);

        RuntimeException e = assertThrows(RuntimeException.class, () -> SchemaAnnotations.validate(schema));
        assertTrue(e.getMessage().contains("mutually exclusive"), e.getMessage());
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
