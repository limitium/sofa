package art.limitium.sofa;

import art.limitium.sofa.schema.Dependency;
import art.limitium.sofa.schema.NamedEntity;
import art.limitium.sofa.schema.Owner;
import art.limitium.sofa.schema.SchemaAnnotations;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an Avro schema entity that can participate in ownership and dependency relationships.
 * This class wraps an Avro Schema and manages its relationships with other entities.
 */
public class AvroEntity implements Owner<AvroEntity>, Dependency<AvroEntity>, NamedEntity {
    /**
     * The underlying Avro schema
     */
    public Schema schema;

    /**
     * Map of dependent entities keyed by their full names
     */
    public Map<String, AvroEntity> dependencies = new HashMap<>();

    /**
     * List of entities that own this entity in 1-to-many relationships
     */
    public List<AvroEntity> owners = new ArrayList<>();

    /**
     * Flag indicating if this is a root entity with no parent dependencies
     */
    public boolean isRoot = false;

    /**
     * Creates a new AvroEntity wrapping the given schema
     *
     * @param schema The Avro schema to wrap
     */
    AvroEntity(Schema schema) {
        this.schema = schema;
    }

    /**
     * Gets the fully qualified name of this entity
     *
     * @return The full name from the wrapped schema
     */
    @Override
    public String getFullname() {
        return schema.getFullName();
    }

    /**
     * Gets an immutable list of all dependent entities
     *
     * @return List of dependent AvroEntities
     */
    @Override
    public List<AvroEntity> getDependencies() {
        return List.copyOf(dependencies.values());
    }

    /**
     * Gets the list of entities that own this entity
     *
     * @return List of owner AvroEntities
     */
    @Override
    public List<AvroEntity> getOwners() {
        return owners;
    }

    /**
     * Checks if this record owns other records through array fields.
     * A record is considered an owner if it has any array fields whose element type is a record type,
     * representing a one-to-many relationship with the contained record type.
     *
     * @return true if this record owns other records through array fields, false otherwise
     */
    public boolean isOwner() {
        if (SchemaAnnotations.suppressesOwner(schema)) {
            return false;
        }
        return schema.getFields().stream().anyMatch(f -> f.schema().getType() == Schema.Type.ARRAY && f.schema().getElementType().getType() == Schema.Type.RECORD);
    }

    /**
     * Checks whether this record declares polymorphic ownership, which makes it eligible for the
     * dependent template even when no record in the current module owns it.
     *
     * @return true if the schema is annotated {@code "ownership": "polymorphic"}
     */
    public boolean isPolymorphicallyOwned() {
        return SchemaAnnotations.isPolymorphicallyOwned(schema);
    }

    /**
     * Checks whether this record is an entity owned by others, and so eligible for the dependent
     * template. Either something in this module owns it, or it declares polymorphic ownership and is
     * owned by records that may not exist yet.
     *
     * @return true if the record should be treated as an owned entity
     */
    public boolean isOwnedEntity() {
        if (SchemaAnnotations.suppressesDependent(schema)) {
            return false;
        }
        return !owners.isEmpty() || SchemaAnnotations.isPolymorphicallyOwned(schema);
    }
}
