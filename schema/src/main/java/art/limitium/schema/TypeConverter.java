package art.limitium.schema;

public interface TypeConverter {
    String getName();

    Object getType(Type type);
}
