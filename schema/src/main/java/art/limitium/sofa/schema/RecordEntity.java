package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordEntity extends Entity implements Owner<Entity>, Dependency<RecordEntity> {
    @Nonnull
    private final List<Field> fields;
    private final List<Entity> dependencies = new ArrayList<>();

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

    public boolean isDependent(){
        return !owners.isEmpty();
    }

    public record Field(
            String name,
            Type type
    ) {
    }

    public Field getPrimaryKey() {
        List<Field> primaryKeys = fields.stream()
                .filter(f -> f.type.getProperty("primary") != null)
                .filter(f -> (boolean) (Boolean) f.type.getProperty("primary"))
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
