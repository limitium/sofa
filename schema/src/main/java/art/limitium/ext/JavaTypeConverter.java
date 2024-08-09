package art.limitium.ext;

import art.limitium.schema.Type;
import art.limitium.schema.TypeConverter;
import org.apache.commons.compress.utils.Sets;

import java.util.Set;

public class JavaTypeConverter implements TypeConverter {

    @Override
    public String getName() {
        return "javaType";
    }

    Set<Class<?>> primitivesTypes = Sets.newHashSet(
            Type.DateType.class,
            Type.TimeMillisType.class,
            Type.TimeMicrosType.class,
            Type.IntType.class,
            Type.TimestampMillisType.class,
            Type.TimestampMicrosType.class,
            Type.LocalTimestampMillisType.class,
            Type.LocalTimestampMicrosType.class,
            Type.LongType.class,
            Type.FloatType.class,
            Type.DoubleType.class,
            Type.BooleanType.class
    );

    @Override
    public String getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> "String";
            case Type.DatetimeType datetimeType -> "String";
            case Type.StringType stringType -> "String";
            case Type.DecimalType decimalType -> "byte[]";
            case Type.BytesType bytesType -> "byte[]";
            case Type.DateType dateType -> "int";
            case Type.TimeMillisType timeMillisType -> "int";
            case Type.TimeMicrosType timeMicrosType -> "int";
            case Type.IntType intType -> "int";
            case Type.TimestampMillisType timestampMillisType -> "long";
            case Type.TimestampMicrosType timestampMicrosType -> "long";
            case Type.LocalTimestampMillisType localTimestampMillisType -> "long";
            case Type.LocalTimestampMicrosType localTimestampMicrosType -> "long";
            case Type.LongType longType -> "long";
            case Type.FloatType floatType -> "float";
            case Type.DoubleType doubleType -> "double";
            case Type.BooleanType booleanType -> "boolean";
            case Type.FixedType fixedType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.ArrayType arrayType -> {
                if (primitivesTypes.contains(arrayType.getElementType().getClass())) {
                    throw new IllegalArgumentException("Unsupported collection with primitive type: " + arrayType.getElementType().getName());
                }
                yield "java.util.List<" + this.getType(arrayType.getElementType()) + ">";
            }
            case Type.RecordType recordType -> recordType.getRecord().getName();
            case Type.EnumType enumType -> enumType.getEnum().getName();
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}
