package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class EnumEntity extends Entity {
    private final List<String> symbols;

    public EnumEntity(String namespace, String name, String fullName, @Nonnull Schema schema, @Nonnull List<String> symbols) {
        super(namespace, name, fullName, schema);
        this.symbols = Collections.unmodifiableList(symbols);
    }

    public List<String> getSymbols() {
        return symbols;
    }
}
