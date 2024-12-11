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

    @SuppressWarnings("unchecked")
    public List<String> getAliases() {
        List<String> symbolAliases = (List<String>) getSchema().getObjectProp("symbol_aliases");
        if (symbolAliases !=null && symbolAliases.size() != symbols.size()) {
            throw new RuntimeException("Symbol aliases list size (" + symbolAliases.size() + 
                                    ") does not match symbols list size (" + symbols.size() + 
                                    ") for enum " + getFullname());
        }
        return symbolAliases;
    }

    public String getAlias(String symbol) {
        for (int i = 0; i < getSymbols().size(); i++) {
            if (symbol.equals(getSymbols().get(i))) {
                List<String> aliases = getAliases();
                return aliases != null ? aliases.get(i) : symbol;
            }
        }
        throw new RuntimeException("Alias for " + symbol + " not found in enum " + getFullname());
    }
}
