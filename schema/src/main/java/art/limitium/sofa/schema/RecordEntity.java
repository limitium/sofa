package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import javax.annotation.Nonnull;
import java.util.*;

public class RecordEntity extends Entity implements Owner<Entity>, Dependency<RecordEntity> {
    @Nonnull
    private final List<Field> fields;
    private final List<Entity> dependencies = new ArrayList<>();
    //Opposite to dependencies to travel to the root
    private final Set<RecordEntity> parents = new HashSet<>();

    //Tracks 1-N relations
    private final List<RecordEntity> owners = new ArrayList<>();

    private final boolean isRoot;

    public RecordEntity(String namespace, String name, String fullName, @Nonnull Schema schema, @Nonnull List<Field> fields, boolean isRoot) {
        super(namespace, name, fullName, schema);
        this.fields = Collections.unmodifiableList(fields);
        this.isRoot = isRoot;
    }

    public List<Field> getFields() {
        return fields;
    }

    public boolean isRoot() {
        return isRoot;
    }

    @Override
    public List<Entity> getDependencies() {
        return dependencies;
    }

    @Override
    public List<RecordEntity> getOwners() {
        return owners;
    }

    public boolean isDependent() {
        return !owners.isEmpty();
    }

    public Set<RecordEntity> getParents() {
        return parents;
    }

    public record Field(
            String name,
            Type type
    ) {
        public boolean isPrimary() {
            return type.getProperty("primary") != null && (Boolean) type.getProperty("primary");
        }
    }

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
