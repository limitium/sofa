package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Represents a record entity in the schema that can have fields, dependencies and relationships.
 * Extends Entity and implements Owner and Dependency interfaces to support bidirectional relationships.
 */
public class RecordEntity extends Entity implements Owner<Entity>, Dependency<RecordEntity> {
    /** List of fields in this record */
    @Nonnull
    private final List<Field> fields;

    /** List of entities that this record depends on */
    private final List<Entity> dependencies = new ArrayList<>();

    /** Set of parent records in the dependency hierarchy, used for traversing up to root */
    private final Set<RecordEntity> parents = new HashSet<>();

    /** List of records that own this record in one-to-many relationships */
    private final List<RecordEntity> owners = new ArrayList<>();

    /** Flag indicating if this is a root record with no parent dependencies */
    private final boolean isRoot;

    /**
     * Creates a new RecordEntity
     * @param namespace The namespace of the record
     * @param name The name of the record
     * @param fullName The fully qualified name
     * @param schema The Avro schema
     * @param fields The list of fields
     * @param isRoot Whether this is a root record
     */
    public RecordEntity(String namespace, String name, String fullName, @Nonnull Schema schema, @Nonnull List<Field> fields, boolean isRoot) {
        super(namespace, name, fullName, schema);
        this.fields = Collections.unmodifiableList(fields);
        this.isRoot = isRoot;
    }

    /**
     * Gets the list of fields in this record
     * @return Immutable list of fields
     */
    public List<Field> getFields() {
        return fields;
    }

    /**
     * Checks if this is a root record
     * @return true if root record, false otherwise
     */
    public boolean isRoot() {
        return isRoot;
    }

    /**
     * Gets entities that this record depends
     * @return List of dependent entities
     */
    @Override
    public List<Entity> getDependencies() {
        return dependencies;
    }

    /**
     * Gets records that own this record in one-to-many relationships
     * @return List of owner records
     */
    @Override
    public List<RecordEntity> getOwners() {
        return owners;
    }

    /**
     * Checks if this record owns other records through array fields.
     * A record is considered an owner if it has any array fields whose element type is a record type,
     * representing a one-to-many relationship with the contained record type.
     * @return true if this record owns other records through array fields, false otherwise
     */
    public boolean isOwner(){
        return fields.stream().anyMatch(f->f.type instanceof Type.ArrayType art && art.getElementType() instanceof Type.RecordType);
    }

    /**
     * Checks if this record is dependent on other records in one-to-many relationships
     * @return true if has owners, false otherwise
     */
    public boolean isDependent() {
        return !owners.isEmpty();
    }

    /**
     * Gets parent records in dependency hierarchy traversing up to root
     * @return Set of parent records
     */
    public Set<RecordEntity> getParents() {
        return parents;
    }

    /**
     * Record representing a field with a name and type
     */
    public record Field(
            String name,
            Type type
    ) {
        /**
         * Checks if this field is marked as primary key
         * @return true if primary key, false otherwise
         */
        public boolean isPrimary() {
            return type.getProperty("primary") != null && (Boolean) type.getProperty("primary");
        }
    }

    /**
     * Gets the primary key field for this record
     * @return The primary key field
     * @throws RuntimeException if no primary key or multiple primary keys found
     */
    public Field getPrimaryKey() {
        List<Field> primaryKeys = fields.stream()
                .filter(Field::isPrimary)
                .toList();

        if (primaryKeys.isEmpty()) {
            throw new RuntimeException("Record " + this.getFullname() + " hasn't primary key");
        }
        if (primaryKeys.size() > 1) {
            throw new RuntimeException("Record " + this.getFullname() + " has more than one primary key");
        }
        return primaryKeys.get(0);
    }
}
