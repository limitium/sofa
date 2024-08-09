package art.limitium.ext;

import art.limitium.schema.Type;
import art.limitium.schema.Type.*;
import art.limitium.schema.TypeConverter;

public class FbIsPrimitiveConverter implements TypeConverter {

    @Override
    public String getName() {
        return "fbIsPrimitive";
    }

    @Override
    public Boolean getType(Type type) {
        return switch (type) {
            case UUIDType uuidType -> false;
            case DatetimeType datetimeType -> false;
            case StringType stringType -> false;
            case DecimalType decimalType -> false;
            case BytesType bytesType -> false;
            case DateType dateType -> true;
            case TimeMillisType timeMillisType -> true;
            case TimeMicrosType timeMicrosType -> true;
            case IntType intType -> true;
            case TimestampMillisType timestampMillisType -> true;
            case TimestampMicrosType timestampMicrosType -> true;
            case LocalTimestampMillisType localTimestampMillisType -> true;
            case LocalTimestampMicrosType localTimestampMicrosType -> true;
            case LongType longType -> true;
            case FloatType floatType -> true;
            case DoubleType doubleType -> true;
            case BooleanType booleanType -> true;
            case FixedType fixedType -> false;
            case ArrayType arrayType -> false;
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case RecordType recordType -> false;
            case EnumType enumType -> false;
            case MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}
