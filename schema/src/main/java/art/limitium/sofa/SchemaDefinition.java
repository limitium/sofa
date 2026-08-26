package art.limitium.sofa;

import art.limitium.sofa.schema.SchemaAnnotations;
import org.apache.avro.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * Represents a schema definition containing Avro entities and their relationships.
 * This class helps build and analyze the dependency graph between Avro records.
 */
public class SchemaDefinition {
    /**
     * List of root Avro entities that have no parent dependencies
     */
    List<AvroEntity> roots;
    /**
     * Map of all Avro entities keyed by their full name
     */
    Map<String, AvroEntity> records = new HashMap<>();

    /**
     * Registers a record and walks its fields to wire dependencies and 1-N ownership.
     * <p>
     * A record reached again, either from another parent or from itself, returns the entity already
     * registered without a second walk. Walking twice would add the same owner to an owned record
     * once per path, which shows up as duplicated relations downstream, and a record referencing
     * itself would never stop.
     */
    public AvroEntity addRecord(Schema schema) {
        AvroEntity known = records.get(schema.getFullName());
        if (known != null) {
            return known;
        }

        SchemaAnnotations.validate(schema);
        AvroEntity avroEntity = new AvroEntity(schema);
        records.put(schema.getFullName(), avroEntity);

        if (schema.getType() == Schema.Type.RECORD) {
            for (Schema.Field field : schema.getFields()) {
                if (field.schema().getType() == Schema.Type.RECORD || field.schema().getType() == Schema.Type.ENUM) {
                    AvroEntity childRecord = addRecord(field.schema());
                    avroEntity.dependencies.put(field.schema().getFullName(), childRecord);
                }
                if (field.schema().getType() == Schema.Type.ARRAY) {
                    unfoldArray(avroEntity, field.schema().getElementType());
                }
            }
        }
        return avroEntity;
    }

    private void unfoldArray(AvroEntity avroEntity, Schema elementType) {
        if (elementType.getType() == Schema.Type.RECORD || elementType.getType() == Schema.Type.ENUM) {
            AvroEntity arrayAvroEntity = addRecord(elementType);
            arrayAvroEntity.owners.add(avroEntity);
            avroEntity.dependencies.put(elementType.getFullName(), arrayAvroEntity);
        }
        if (elementType.getType() == Schema.Type.ARRAY) {
            unfoldArray(avroEntity, elementType.getElementType());
        }
    }

    /**
     * Finds records nothing depends on and marks them as roots.
     * <p>
     * Records that pin themselves to a non root role via annotations are excluded even when nothing
     * in this module references them: a composite or a polymorphically owned record is not an
     * aggregate root just because its owner happens to live in another module.
     */
    public List<AvroEntity> findRoots() {
        List<String> dependencies = records.values().stream().flatMap(n -> n.dependencies.keySet().stream()).collect(Collectors.toList());
        roots = records.values().stream()
                .filter(avroEntity -> avroEntity.schema.getType() == Schema.Type.RECORD)
                .filter(avroEntity -> !dependencies.contains(avroEntity.getFullname()))
                .filter(avroEntity -> !SchemaAnnotations.suppressesRoot(avroEntity.schema))
                .peek(avroEntity -> avroEntity.isRoot = true)
                .collect(Collectors.toList());
        return roots;
    }

    /**
     * Finds every record that pins its role by annotation, for reporting
     */
    public List<AvroEntity> findAnnotatedRecords() {
        return records.values().stream()
                .filter(avroEntity -> avroEntity.schema.getType() == Schema.Type.RECORD)
                .filter(avroEntity -> SchemaAnnotations.suppressesRoot(avroEntity.schema))
                .collect(Collectors.toList());
    }

    /**
     * Finds annotated records that no root reaches, so they still enter the scope of work.
     * <p>
     * The scope of work is walked from the roots, so a record pinned out of root-ness by an
     * annotation would otherwise become invisible when nothing in this module references it. This is
     * the case a library hits for the records it exists to publish.
     */
    public List<AvroEntity> findDeclaredEntryPoints() {
        return records.values().stream()
                .filter(avroEntity -> avroEntity.schema.getType() == Schema.Type.RECORD)
                .filter(avroEntity -> !avroEntity.isRoot)
                .filter(avroEntity -> SchemaAnnotations.suppressesRoot(avroEntity.schema))
                .collect(Collectors.toList());
    }

}
