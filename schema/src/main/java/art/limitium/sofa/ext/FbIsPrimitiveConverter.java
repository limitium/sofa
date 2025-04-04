package art.limitium.sofa.ext;

import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;

public class FbIsPrimitiveConverter implements TypeConverter {

    @Override
    public String getName() {
        return "fbIsPrimitive";
    }

    @Override
    public Boolean getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> false;
            case Type.DatetimeType datetimeType -> false;
            case Type.StringType stringType -> false;
            case Type.DecimalType decimalType -> false;
            case Type.BytesType bytesType -> false;
            case Type.DateType dateType -> true;
            case Type.TimeMillisType timeMillisType -> true;
            case Type.TimeMicrosType timeMicrosType -> true;
            case Type.IntType intType -> true;
            case Type.TimestampMillisType timestampMillisType -> true;
            case Type.TimestampMicrosType timestampMicrosType -> true;
            case Type.LocalTimestampMillisType localTimestampMillisType -> true;
            case Type.LocalTimestampMicrosType localTimestampMicrosType -> true;
            case Type.LongType longType -> true;
            case Type.FloatType floatType -> true;
            case Type.DoubleType doubleType -> true;
            case Type.BooleanType booleanType -> true;
            case Type.FixedType fixedType -> false;
            case Type.ArrayType arrayType -> false;
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case Type.RecordType recordType -> false;
            case Type.EnumType enumType -> false;
            case Type.RecordCloseType recordCloseType -> false;
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}
