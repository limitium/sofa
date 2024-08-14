package art.limitium.sofa.schema;

import java.util.List;

public interface Dependency<E> {
    List<E> getOwners();
}
