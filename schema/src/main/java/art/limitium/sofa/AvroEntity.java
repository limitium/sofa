package art.limitium.sofa;

import art.limitium.sofa.schema.Dependency;
import art.limitium.sofa.schema.NamedEntity;
import art.limitium.sofa.schema.Owner;
import art.limitium.sofa.schema.SchemaAnnotations;
import art.limitium.sofa.schema.SchemaShape;
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
        return SchemaShape.ownsCollection(schema);
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

    /**
     * Checks whether this record is a carrier, the embedded flavour of an owner.
     * <p>
     * An owner holds a collection and is a row of its own, so the rows it owns point back at it. A
     * carrier owns the same way but is embedded into its parent, either because {@code "role":
     * "child"} pinned it out of {@link #isOwner()} or because it owns through the composites it
     * embeds rather than directly. Having no row to point at, the rows it owns belong to whatever
     * encloses it, which is the walk {@code flattenOwners} makes.
     * <p>
     * That is what makes a carrier world specific where a plain composite is not: a denormalized
     * world inlines the collection, a normalized one has moved it into a table, so the two cannot
     * share the class, and neither can whatever embeds the carrier, up to the nearest root or
     * dependent. Those are entities with a class per world anyway, which is also why a record
     * placed by an entity role of its own never carries.
     *
     * @return true if the record owns a collection yet is embedded rather than stored as rows
     */
    public boolean isCarrier() {
        return !isRoot && !isOwner() && !isOwnedEntity() && SchemaShape.reachesOwnership(schema);
    }

    /**
     * Lists the roles this entity qualifies for, most specific first.
     * <p>
     * This is the template selection ladder without the templates: it says what the record is, not
     * what a given generator can render it as. A generator narrows the list to the roles it has a
     * template for, while consumers that render every record alike, such as a diagram, take the
     * first entry.
     *
     * @return The roles, most specific first, empty for anything that is neither record nor enum
     */
    public List<String> getRoles() {
        if (schema.getType() == Schema.Type.ENUM) {
            return List.of("enum");
        }
        if (schema.getType() != Schema.Type.RECORD) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        if (isRoot) {
            roles.add("root");
        }
        if (isOwner()) {
            roles.add("owner");
        }
        if (isCarrier()) {
            roles.add("carrier");
        }
        if (isOwnedEntity()) {
            roles.add("dependent");
        }
        if (!isRoot) {
            roles.add("child");
        }
        roles.add("record");
        return roles;
    }

    /**
     * The most specific role this entity qualifies for, regardless of any generator's templates
     *
     * @return The role name, or {@code none} when the entity holds no role
     */
    public String getRole() {
        List<String> roles = getRoles();
        return roles.isEmpty() ? "none" : roles.get(0);
    }
}
