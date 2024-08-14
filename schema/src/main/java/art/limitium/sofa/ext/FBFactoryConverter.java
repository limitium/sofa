package art.limitium.sofa.ext;

import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;

public class FBFactoryConverter implements TypeConverter {

    @Override
    public String getName() {
        return "fbFactory";
    }

    @Override
    public String getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> "String";
            case Type.DatetimeType datetimeType -> "String";
            case Type.StringType stringType -> "String";
            case Type.DecimalType decimalType -> "CreateArray";
            case Type.BytesType bytesType -> "CreateArray";
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
            case Type.BooleanType booleanType -> "bool";
            case Type.FixedType fixedType -> "CreateArray";
            case Type.ArrayType arrayType -> "CreateArray";
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case Type.RecordType recordType -> "CreateRecord";
            case Type.EnumType enumType -> "CreateEnum";
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}
