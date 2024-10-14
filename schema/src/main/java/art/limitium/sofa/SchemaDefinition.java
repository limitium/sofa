package art.limitium.sofa;

import org.apache.avro.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SchemaDefinition {
    List<AvroEntity> roots;
    Map<String, AvroEntity> records = new HashMap<>();

    public AvroEntity addRecord(Schema schema) {

        AvroEntity avroEntity = records.computeIfAbsent(schema.getFullName(), s -> new AvroEntity(schema));

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

    public List<AvroEntity> findRoots() {
        List<String> dependencies = records.values().stream().flatMap(n -> n.dependencies.keySet().stream()).collect(Collectors.toList());
        roots = records.values().stream().filter(avroEntity -> avroEntity.schema.getType() == Schema.Type.RECORD && !dependencies.contains(avroEntity.getFullname())).peek(avroEntity -> avroEntity.isRoot = true).collect(Collectors.toList());
        return roots;
    }

}
