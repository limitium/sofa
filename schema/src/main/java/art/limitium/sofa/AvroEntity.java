package art.limitium.sofa;

import art.limitium.sofa.schema.Dependency;
import art.limitium.sofa.schema.NamedEntity;
import art.limitium.sofa.schema.Owner;
import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvroEntity implements Owner<AvroEntity>, Dependency<AvroEntity>, NamedEntity {
    public Schema schema;
    public Map<String, AvroEntity> dependencies = new HashMap<>();
    public List<AvroEntity> owners = new ArrayList<>();
    public boolean isRoot = false;

    AvroEntity(Schema schema) {
        this.schema = schema;
    }

    @Override
    public String getFullname() {
        return schema.getFullName();
    }

    @Override
    public List<AvroEntity> getDependencies() {
        return List.copyOf(dependencies.values());
    }

    @Override
    public List<AvroEntity> getOwners() {
        return owners;
    }
}