package art.limitium.sofa.ext;

import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;

public class FBTypeConverter implements TypeConverter {

    @Override
    public String getName() {
        return "fbType";
    }

    @Override
    public String getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> "string";
            case Type.DatetimeType datetimeType -> "string";
            case Type.StringType stringType -> "string";
            case Type.DecimalType decimalType -> "[byte]";
            case Type.BytesType bytesType -> "[byte]";
            case Type.DateType dateType -> "int";
            case Type.TimeMillisType timeMillisType -> "int";
            case Type.TimeMicrosType timeMicrosType -> "long";
            case Type.IntType intType -> "int";
            case Type.TimestampMillisType timestampMillisType -> "long";
            case Type.TimestampMicrosType timestampMicrosType -> "long";
            case Type.LocalTimestampMillisType localTimestampMillisType -> "long";
            case Type.LocalTimestampMicrosType localTimestampMicrosType -> "long";
            case Type.LongType longType -> "long";
            case Type.FloatType floatType -> "float";
            case Type.DoubleType doubleType -> "double";
            case Type.BooleanType booleanType -> "bool";
            case Type.FixedType fixedType -> "[byte:" + fixedType.getSize() + "]";
            case Type.ArrayType arrayType -> "[" + this.getType(arrayType.getElementType()) + "]";
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case Type.RecordType recordType -> recordType.getRecord().getFullname();
            case Type.EnumType enumType -> enumType.getEnum().getFullname();
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}
