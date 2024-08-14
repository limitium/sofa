package art.limitium.sofa.schema;

public interface TypeConverter {
    String getName();

    Object getType(Type type);
}
