package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import java.util.HashSet;
import java.util.Set;

/**
 * Structural questions about a record, answered from the schema alone.
 * <p>
 * Where {@link SchemaAnnotations} reads the intent a schema declares, this reads the shape it has.
 * Both are needed to place a record: an annotation can pin a collection holder out of owner-ness,
 * but it cannot make the collection go away, and normalization still has to deal with it.
 */
public final class SchemaShape {

    private SchemaShape() {
    }

    /**
     * Checks whether the record owns a one-to-many relation, holding an array whose elements are
     * records.
     * <p>
     * This is owner-ness as the schema has it, before any annotation is applied, which is why it
     * lives here rather than on the entities: {@code isOwner()} is this test plus the annotation
     * that can pin a holder out of the role, and {@code isCarrier()} is this test for what the
     * annotation left behind.
     *
     * @param schema The record schema to inspect
     * @return true if any field is an array of records
     */
    public static boolean ownsCollection(Schema schema) {
        if (schema.getType() != Schema.Type.RECORD) {
            return false;
        }
        return schema.getFields().stream().anyMatch(field -> isCollectionOfRecords(field.schema()));
    }

    /**
     * Checks whether the record owns a collection or embeds a record that does.
     * <p>
     * The walk follows embedded record fields only, and stops at arrays: an array is the relation
     * that normalization splits into rows of its own, so what the split off record owns below it is
     * that record's business, not this one's. Ownership does travel the other way, up through
     * embedded records, which is the same path {@code flattenOwners} walks when it looks for the
     * entity a row belongs to.
     *
     * @param schema The record schema to inspect
     * @return true if the record owns a collection of records, directly or through what it embeds
     */
    public static boolean reachesOwnership(Schema schema) {
        return reachesOwnership(schema, new HashSet<>());
    }

    private static boolean reachesOwnership(Schema schema, Set<String> visited) {
        if (schema.getType() != Schema.Type.RECORD || !visited.add(schema.getFullName())) {
            return false;
        }
        if (ownsCollection(schema)) {
            return true;
        }
        return schema.getFields().stream()
                .map(Schema.Field::schema)
                .anyMatch(fieldSchema -> reachesOwnership(fieldSchema, visited));
    }

    private static boolean isCollectionOfRecords(Schema schema) {
        if (schema.getType() != Schema.Type.ARRAY) {
            return false;
        }
        Schema elementType = schema.getElementType();
        return elementType.getType() == Schema.Type.RECORD || isCollectionOfRecords(elementType);
    }
}
