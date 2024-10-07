package art.limitium.sofa.ext;

import art.limitium.sofa.schema.Type;
import art.limitium.sofa.schema.TypeConverter;
import org.apache.commons.compress.utils.Sets;

import java.util.Set;

public class ConnectSchemaConverter implements TypeConverter {

    @Override
    public String getName() {
        return "connectSchemaType";
    }

    @Override
    public String getType(Type type) {
        return switch (type) {
            case Type.UUIDType uuidType -> "Schema.OPTIONAL_STRING_SCHEMA";
            case Type.DatetimeType datetimeType -> "Schema.OPTIONAL_STRING_SCHEMA";
            case Type.StringType stringType -> "Schema.OPTIONAL_STRING_SCHEMA";
            case Type.DecimalType decimalType -> "Schema.OPTIONAL_BYTES_SCHEMA";
            case Type.BytesType bytesType -> "Schema.OPTIONAL_BYTES_SCHEMA";
            case Type.DateType dateType -> "Schema.INT32_SCHEMA";
            case Type.TimeMillisType timeMillisType -> "Schema.INT32_SCHEMA";
            case Type.TimeMicrosType timeMicrosType -> "Schema.INT64_SCHEMA";
            case Type.IntType intType -> "Schema.INT32_SCHEMA";
            case Type.TimestampMillisType timestampMillisType -> "Schema.INT64_SCHEMA";
            case Type.TimestampMicrosType timestampMicrosType -> "Schema.INT64_SCHEMA";
            case Type.LocalTimestampMillisType localTimestampMillisType -> "Schema.INT64_SCHEMA";
            case Type.LocalTimestampMicrosType localTimestampMicrosType -> "Schema.INT64_SCHEMA";
            case Type.LongType longType -> "Schema.INT64_SCHEMA";
            case Type.FloatType floatType -> "Schema.FLOAT32_SCHEMA";
            case Type.DoubleType doubleType -> "Schema.FLOAT64_SCHEMA";
            case Type.BooleanType booleanType -> "Schema.BOOLEAN_SCHEMA";
            case Type.FixedType fixedType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.ArrayType arrayType -> "Schema.OPTIONAL_STRING_SCHEMA";
            //            case ArrayType arrayType -> {
//                if (arrayType.getElementType() instanceof ArrayType) {
//                    throw new IllegalArgumentException("Unsupported multidimensional arrays." + type.getName());
//                }
//                yield "[" + this.getType(arrayType.getElementType()) + "]";
//            }
            case Type.RecordType recordType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.EnumType enumType -> "Schema.OPTIONAL_STRING_SCHEMA";
            case Type.MapType mapType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.NullType nullType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            case Type.UnionType unionType -> throw new IllegalArgumentException("Unsupported Avro type: " + type.getName());
            default -> throw new IllegalArgumentException("Unknown Avro type: " + type.getName());
        };
    }
}