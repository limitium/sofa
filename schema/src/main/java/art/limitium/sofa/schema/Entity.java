package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import javax.annotation.Nonnull;
import java.util.Objects;

public class Entity implements NamedEntity{
    String namespace;
    String name;
    String fullName;
    Schema schema;

    public Entity(@Nonnull String namespace, @Nonnull String name, @Nonnull String fullName, @Nonnull Schema schema) {
        this.namespace = namespace;
        this.name = name;
        this.fullName = fullName;
        this.schema = schema;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }
    @Override
    public String getFullname() {
        return fullName;
    }

    public Schema getSchema() {
        return schema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return Objects.equals(namespace, entity.namespace) && Objects.equals(name, entity.name) && Objects.equals(fullName, entity.fullName) && Objects.equals(schema, entity.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name, fullName, schema);
    }
}
