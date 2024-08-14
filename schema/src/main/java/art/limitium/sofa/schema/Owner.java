package art.limitium.sofa.schema;

import java.util.List;

public interface Owner<E> {
    List<E> getDependencies();
}
