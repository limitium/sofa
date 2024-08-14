package art.limitium.sofa.schema;

import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class Type {
    //Build-in types from org.apache.avro.LogicalTypes
    private static final String DECIMAL = "decimal";
    private static final String UUID = "uuid";
    private static final String DATE = "date";
    private static final String TIME_MILLIS = "time-millis";
    private static final String TIME_MICROS = "time-micros";
    private static final String TIMESTAMP_MILLIS = "timestamp-millis";
    private static final String TIMESTAMP_MICROS = "timestamp-micros";
    private static final String LOCAL_TIMESTAMP_MILLIS = "local-timestamp-millis";
    private static final String LOCAL_TIMESTAMP_MICROS = "local-timestamp-micros";

    //Additional logic types
    private static final String DATETIME_STR = "datetime-str";

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

    public abstract String getName();

    public Object getProperty(String prop) {
        return properties.get(prop);
    }

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

    public static class StringType extends Type {
        public StringType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "string";
        }
    }

    public static class UUIDType extends StringType {
        public UUIDType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "uuid";
        }
    }

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

    public static class BytesType extends Type {
        public BytesType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "bytes";
        }
    }

    public static class IntType extends Type {
        public IntType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "int";
        }
    }

    public static class LongType extends Type {
        public LongType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "long";
        }
    }

    public static class FloatType extends Type {
        public FloatType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "float";
        }
    }

    public static class DoubleType extends Type {
        public DoubleType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "double";
        }
    }

    public static class BooleanType extends Type {
        public BooleanType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "boolean";
        }
    }

    public static class NullType extends Type {
        public NullType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "null";
        }
    }

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

    public static class MapType extends Type {
        public MapType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "map";
        }
    }

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

    public static class DateType extends IntType {

        public DateType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "date";
        }
    }

    public static class TimeMillisType extends IntType {
        public TimeMillisType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "time-millis";
        }
    }

    public static class TimeMicrosType extends LongType {
        public TimeMicrosType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "time-micros";
        }
    }

    public static class TimestampMillisType extends LongType {
        public TimestampMillisType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "timestamp-millis";
        }
    }

    public static class TimestampMicrosType extends LongType {
        public TimestampMicrosType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "timestamp-micros";
        }
    }

    public static class LocalTimestampMillisType extends LongType {
        public LocalTimestampMillisType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "local-timestamp-millis";
        }
    }

    public static class LocalTimestampMicrosType extends LongType {
        public LocalTimestampMicrosType(Map<String, Object> properties) {
            super(properties);
        }

        @Override
        public String getName() {
            return "local-timestamp-micros";
        }
    }

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