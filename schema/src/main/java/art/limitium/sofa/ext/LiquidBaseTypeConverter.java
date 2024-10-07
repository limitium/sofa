package art.limitium.sofa.ext;

import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;

public class LiquidBaseTypeConverter implements TypeConverter {

    @Override
    public String getName() {
        return "liquidBaseType";
    }

    @Override
    public String getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> "uuid";
            case Type.DatetimeType datetimeType -> "datetime";
            case Type.StringType stringType -> "varchar2(255)";
            case Type.DecimalType decimalType -> "decimal(" + decimalType.getPrecision() + "," + decimalType.getScale() + ")";
            case Type.BytesType bytesType -> "blob";
            case Type.DateType dateType -> "date";
            case Type.TimeMillisType timeMillisType -> "int";
            case Type.TimeMicrosType timeMicrosType -> "bigint";
            case Type.IntType intType -> "int";
            case Type.TimestampMillisType timestampMillisType -> "bigint";
            case Type.TimestampMicrosType timestampMicrosType -> "bigint";
            case Type.LocalTimestampMillisType localTimestampMillisType -> "bigint";
            case Type.LocalTimestampMicrosType localTimestampMicrosType -> "bigint";
            case Type.LongType longType -> "bigint";
            case Type.FloatType floatType -> "float";
            case Type.DoubleType doubleType -> "double";
            case Type.BooleanType booleanType -> "boolean";
            case Type.FixedType fixedType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.ArrayType arrayType -> "varchar2(255)";
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case Type.RecordType recordType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.EnumType enumType -> "varchar2(255)";
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}