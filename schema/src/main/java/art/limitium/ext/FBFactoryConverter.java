package art.limitium.ext;

import art.limitium.schema.Type;
import art.limitium.schema.Type.*;
import art.limitium.schema.TypeConverter;

public class FBFactoryConverter implements TypeConverter {

    @Override
    public String getName() {
        return "fbFactory";
    }

    @Override
    public String getType(Type type) {
        return switch (type) {
            case UUIDType uuidType -> "String";
            case DatetimeType datetimeType -> "String";
            case StringType stringType -> "String";
            case DecimalType decimalType -> "CreateArray";
            case BytesType bytesType -> "CreateArray";
            case DateType dateType -> "int";
            case TimeMillisType timeMillisType -> "int";
            case TimeMicrosType timeMicrosType -> "int";
            case IntType intType -> "int";
            case TimestampMillisType timestampMillisType -> "long";
            case TimestampMicrosType timestampMicrosType -> "long";
            case LocalTimestampMillisType localTimestampMillisType -> "long";
            case LocalTimestampMicrosType localTimestampMicrosType -> "long";
            case LongType longType -> "long";
            case FloatType floatType -> "float";
            case DoubleType doubleType -> "double";
            case BooleanType booleanType -> "bool";
            case FixedType fixedType -> "CreateArray";
            case ArrayType arrayType -> "CreateArray";
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case RecordType recordType -> "CreateRecord";
            case EnumType enumType -> "CreateEnum";
            case MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}
