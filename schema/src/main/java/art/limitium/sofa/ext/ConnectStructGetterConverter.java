package art.limitium.sofa.ext;

import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;

public class ConnectStructGetterConverter implements TypeConverter {

    @Override
    public String getName() {
        return "connectStructGetter";
    }

    @Override
    public String getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> "getString";
            case Type.DatetimeType datetimeType -> "getString";
            case Type.StringType stringType -> "getString";
            case Type.DecimalType decimalType -> "getBytes";
            case Type.BytesType bytesType -> "getBytes";
            case Type.DateType dateType -> "getInt32";
            case Type.TimeMillisType timeMillisType -> "getInt32";
            case Type.TimeMicrosType timeMicrosType -> "getInt32";
            case Type.IntType intType -> "getInt32";
            case Type.TimestampMillisType timestampMillisType -> "getInt64";
            case Type.TimestampMicrosType timestampMicrosType -> "getInt64";
            case Type.LocalTimestampMillisType localTimestampMillisType -> "getInt64";
            case Type.LocalTimestampMicrosType localTimestampMicrosType -> "getInt64";
            case Type.LongType longType -> "getInt64";
            case Type.FloatType floatType -> "getFloat32";
            case Type.DoubleType doubleType -> "getFloat64";
            case Type.BooleanType booleanType -> "getBoolean";
            case Type.FixedType fixedType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.ArrayType arrayType -> "getString";
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case Type.RecordType recordType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.EnumType enumType -> "getString";
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}