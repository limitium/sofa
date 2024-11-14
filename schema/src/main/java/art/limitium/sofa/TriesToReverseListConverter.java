package art.limitium.sofa;

import art.limitium.sofa.schema.NamedEntity;
import art.limitium.sofa.schema.Owner;

import java.util.LinkedHashMap;
import java.util.List;
/**
 * A converter that flattens a hierarchical structure of named entities into a list,
 * preserving dependency order by traversing the tree depth-first.
 *
 * @param <E> Type parameter extending NamedEntity to allow working with different entity types
 */

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
