package art.limitium.sofa.schema;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Abstract base class representing Avro schema types with logical type support.
 * Provides factory methods to create specific type instances from Avro schemas and handles type properties.
 */
public abstract class Type {
    // Built-in logical types from org.apache.avro.LogicalTypes
    private static final String DECIMAL = "decimal";
    private static final String UUID = "uuid"; 
    private static final String DATE = "date";
    private static final String TIME_MILLIS = "time-millis";
    private static final String TIME_MICROS = "time-micros";
    private static final String TIMESTAMP_MILLIS = "timestamp-millis";
    private static final String TIMESTAMP_MICROS = "timestamp-micros";
    private static final String LOCAL_TIMESTAMP_MILLIS = "local-timestamp-millis";
    private static final String LOCAL_TIMESTAMP_MICROS = "local-timestamp-micros";

    // Custom logical type for datetime strings
    private static final String DATETIME_STR = "datetime-str";

    /**
     * Custom logical type implementation for datetime strings with format
     */
    public static class DatetimeStr extends LogicalType {
        private final String format;

        public DatetimeStr(String format) {
            super(DATETIME_STR);
            this.format = format;
        }

        public String getFormat() {
            return format;
        }
    }

    // Register custom datetime-str logical type handler
    static {
        LogicalTypes.register(new LogicalTypes.LogicalTypeFactory() {
            @Override
            public LogicalType fromSchema(Schema schema) {
                String format = schema.getProp("datetime-format");
                if (format == null) {
                    throw new IllegalArgumentException("Expected `datetime-format` property for " + schema.getName());
                }
                return new DatetimeStr(format);
            }

            @Override
            public String getTypeName() {
                return DATETIME_STR;
            }
        });
    }

    private final Map<String, Object> properties;

    public Type(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Gets the name of this type
     */
    public abstract String getName();

    /**
     * Gets a type property by name
     */
    public Object getProperty(String prop) {
        return properties.get(prop);
    }

    /**
     * Creates a Type instance from an Avro schema
     */
    public static Type fromSchema(Schema schema, Map<String, Entity> mapByAvroName) {
        LogicalType logicalType = schema.getLogicalType();
        Map<String, Object> props = schema.getObjectProps();

        return switch (schema.getType()) {
            case RECORD -> new RecordType(props, (RecordEntity) mapByAvroName.get(schema.getFullName()));
            case ENUM -> new EnumType(props, (EnumEntity) mapByAvroName.get(schema.getFullName()));
            case ARRAY -> new ArrayType(props, Type.fromSchema(schema.getElementType(), mapByAvroName));
            case MAP -> new MapType(props);
            case UNION -> new UnionType(props, schema.getTypes().stream().map(schema1 -> fromSchema(schema1, mapByAvroName)).collect(Collectors.toList()));
            case FIXED -> new FixedType(props, schema.getFixedSize());
            case STRING -> getStringType(logicalType, props);
            case BYTES -> getBytesType(logicalType, props);
            case INT -> getIntType(logicalType, props);
            case LONG -> getLongType(logicalType, props);
            case FLOAT -> new FloatType(props);
            case DOUBLE -> new DoubleType(props);
            case BOOLEAN -> new BooleanType(props);
            case NULL -> new NullType(props);
        };
    }

    /**
     * Creates string-based types including UUID and datetime
     */
    private static Type getStringType(LogicalType logicalType, Map<String, Object> props) {
        if (logicalType == null) {
            return new StringType(props);
        }
        return switch (logicalType.getName()) {
            case UUID -> new UUIDType(props);
            case DATETIME_STR -> new DatetimeType(props, ((DatetimeStr) logicalType).getFormat());
            default -> throw new IllegalStateException("Unexpected value: " + logicalType.getName());
        };
    }

    /**
     * Creates bytes-based types including decimal
     */
    private static Type getBytesType(LogicalType logicalType, Map<String, Object> props) {
        if (logicalType == null) {
            return new BytesType(props);
        }
        return switch (logicalType.getName()) {
            case DECIMAL ->
                    new DecimalType(props, ((LogicalTypes.Decimal) logicalType).getPrecision(), ((LogicalTypes.Decimal) logicalType).getScale());
            default -> throw new IllegalStateException("Unexpected value: " + logicalType.getName());
        };
    }

    /**
     * Creates int-based types including date and time
     */
    private static Type getIntType(LogicalType logicalType, Map<String, Object> props) {
        if (logicalType == null) {
            return new IntType(props);
        }
        return switch (logicalType.getName()) {
            case DATE -> new DateType(props);
            case TIME_MILLIS -> new TimeMillisType(props);
            case TIME_MICROS -> new TimeMicrosType(props);
            default -> throw new IllegalStateException("Unexpected value: " + logicalType.getName());
        };
    }

    /**
     * Creates long-based types including timestamps
     */
    private static Type getLongType(LogicalType logicalType, Map<String, Object> props) {
        if (logicalType == null) {
            return new LongType(props);
        }
        return switch (logicalType.getName()) {
            case TIMESTAMP_MILLIS -> new TimestampMillisType(props);
            case TIMESTAMP_MICROS -> new TimestampMicrosType(props);
            case LOCAL_TIMESTAMP_MILLIS -> new LocalTimestampMillisType(props);
            case LOCAL_TIMESTAMP_MICROS -> new LocalTimestampMicrosType(props);
            default -> throw new IllegalStateException("Unexpected value: " + logicalType.getName());
        };
    }

    /**
     * String type implementation
     */
    public static class StringType extends Type {
        public StringType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "string";
        }
    }

    /**
     * UUID type implementation
     */
    public static class UUIDType extends StringType {
        public UUIDType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "uuid";
        }
    }

    /**
     * Datetime string type implementation with format
     */
    public static class DatetimeType extends StringType {
        private final String format;

        public DatetimeType(Map<String, Object> properties, String format) {
            super(properties);
            this.format = format;
        }

        @Override
        public String getName() {
            return "datetime";
        }

        public String getFormat() {
            return format;
        }
    }

    /**
     * Bytes type implementation
     */
    public static class BytesType extends Type {
        public BytesType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "bytes";
        }
    }

    /**
     * Integer type implementation
     */
    public static class IntType extends Type {
        public IntType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "int";
        }
    }

    /**
     * Long type implementation
     */
    public static class LongType extends Type {
        public LongType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "long";
        }
    }

    /**
     * Float type implementation
     */
    public static class FloatType extends Type {
        public FloatType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "float";
        }
    }

    /**
     * Double type implementation
     */
    public static class DoubleType extends Type {
        public DoubleType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "double";
        }
    }

    /**
     * Boolean type implementation
     */
    public static class BooleanType extends Type {
        public BooleanType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "boolean";
        }
    }

    /**
     * Null type implementation
     */
    public static class NullType extends Type {
        public NullType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "null";
        }
    }

    /**
     * Record type implementation referencing a RecordEntity
     */
    public static class RecordType extends Type {
        private final RecordEntity recordEntity;
        public RecordType(Map<String, Object> properties, RecordEntity recordEntity) {
            super(properties);
            this.recordEntity = recordEntity;
        }

        @Override
        public String getName() {
            return "record";
        }

        public RecordEntity getRecord() {
            return recordEntity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RecordType that = (RecordType) o;
            return Objects.equals(recordEntity, that.recordEntity);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(recordEntity);
        }
    }

    /**
     * Enum type implementation referencing an EnumEntity
     */
    public static class EnumType extends Type {
        private final EnumEntity enumEntity;
        public EnumType(Map<String, Object> properties, EnumEntity enumEntity) {
            super(properties);
            this.enumEntity = enumEntity;
        }

        @Override
        public String getName() {
            return "enum";
        }

        public EnumEntity getEnum() {
            return enumEntity;
        }
    }

    /**
     * Array type implementation with element type
     */
    public static class ArrayType extends Type {
        private final Type elementType;

        public ArrayType(Map<String, Object> properties, Type elementType) {
            super(properties);
            this.elementType = elementType;
        }

        @Override
        public String getName() {
            return "array";
        }

        public Type getElementType() {
            return elementType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ArrayType arrayType = (ArrayType) o;
            return Objects.equals(elementType, arrayType.elementType);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(elementType);
        }
    }

    /**
     * Map type implementation
     */
    public static class MapType extends Type {
        public MapType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "map";
        }
    }

    /**
     * Union type implementation containing multiple types
     */
    public static class UnionType extends Type {
        private final List<Type> types;

        public UnionType(Map<String, Object> properties, List<Type> types) {
            super(properties);
            this.types = types;
        }

        @Override
        public String getName() {
            return "union";
        }

        public List<Type> getTypes() {
            return types;
        }
    }

    /**
     * Fixed type implementation with size
     */
    public static class FixedType extends Type {
        private final int size;

        public FixedType(Map<String, Object> properties, int size) {
            super(properties);
            this.size = size;
        }

        @Override
        public String getName() {
            return "fixed";
        }

        public int getSize() {
            return size;
        }
    }

    /**
     * Date type implementation based on int
     */
    public static class DateType extends IntType {

        public DateType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "date";
        }
    }

    /**
     * Time in milliseconds type implementation based on int
     */
    public static class TimeMillisType extends IntType {
        public TimeMillisType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "time-millis";
        }
    }

    /**
     * Time in microseconds type implementation based on long
     */
    public static class TimeMicrosType extends LongType {
        public TimeMicrosType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "time-micros";
        }
    }

    /**
     * Timestamp in milliseconds type implementation based on long
     */
    public static class TimestampMillisType extends LongType {
        public TimestampMillisType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "timestamp-millis";
        }
    }

    /**
     * Timestamp in microseconds type implementation based on long
     */
    public static class TimestampMicrosType extends LongType {
        public TimestampMicrosType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "timestamp-micros";
        }
    }

    /**
     * Local timestamp in milliseconds type implementation based on long
     */
    public static class LocalTimestampMillisType extends LongType {
        public LocalTimestampMillisType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "local-timestamp-millis";
        }
    }

    /**
     * Local timestamp in microseconds type implementation based on long
     */
    public static class LocalTimestampMicrosType extends LongType {
        public LocalTimestampMicrosType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "local-timestamp-micros";
        }
    }

    /**
     * Decimal type implementation based on bytes with precision and scale
     */
    public static class DecimalType extends BytesType {
        private final int precision;
        private final int scale;

        public DecimalType(Map<String, Object> properties, int precision, int scale) {
            super(properties);
            this.precision = precision;
            this.scale = scale;
        }

        @Override
        public String getName() {
            return "decimal";
        }

        public int getPrecision() {
            return precision;
        }

        public int getScale() {
            return scale;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Type type = (Type) o;
        return Objects.equals(properties, type.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(properties);
    }
}