package art.limitium;

import art.limitium.schema.NamedEntity;
import art.limitium.schema.Owner;

import java.util.LinkedHashMap;
import java.util.List;

public class TriesToReverseListConverter<E extends NamedEntity> {
    LinkedHashMap<String, E> flatted = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public void addNode(Owner<E> owner) {
        if (owner == null) {
            return;
        }
        for (E dependecy : owner.getDependencies()) {
            if (dependecy instanceof Owner<?> ownerDependency) {
                addNode((Owner<E>) ownerDependency);
            }
            flatted.put(dependecy.getFullname(), dependecy);
        }
    }

    public List<E> getFlatted() {
        return flatted.values().stream().toList();
    }
}
