package art.limitium.sofa.plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stable, JDK-only view of a SOFA schema type.
 *
 * <p>This interface is intentionally minimal and dependency-free so plugins can implement logic
 * without depending on generator internals.
 */
public interface SofaType {
    enum Kind {
        NULL,
        BOOLEAN,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        STRING,
        BYTES,
        RECORD,
        RECORD_CLOSE,
        ENUM,
        ARRAY,
        MAP,
        UNION,
        FIXED,
        UUID,
        DATE,
        TIME_MILLIS,
        TIME_MICROS,
        TIMESTAMP_MILLIS,
        TIMESTAMP_MICROS,
        LOCAL_TIMESTAMP_MILLIS,
        LOCAL_TIMESTAMP_MICROS,
        DATETIME_STR,
        DECIMAL
    }

    Kind kind();

    /**
     * Avro base type name like {@code "string"}, {@code "record"}, {@code "array"}, etc.
     */
    String getName();

    /**
     * Avro/object properties associated with the type (may be empty).
     */
    Map<String, Object> getProperties();

    default Object getProperty(String key) {
        return getProperties().get(key);
    }

    /**
     * Fullname for RECORD / ENUM (if available).
     */
    default Optional<String> getFullName() {
        return Optional.empty();
    }

    /**
     * Enum symbols for ENUM (if available).
     */
    default Optional<List<String>> getEnumSymbols() {
        return Optional.empty();
    }

    /**
     * Element type for ARRAY (if available).
     */
    default Optional<SofaType> getElementType() {
        return Optional.empty();
    }

    /**
     * Member types for UNION (if available).
     */
    default List<SofaType> getUnionTypes() {
        return List.of();
    }

    /**
     * Fixed size for FIXED (if available).
     */
    default Optional<Integer> getFixedSize() {
        return Optional.empty();
    }

    /**
     * Datetime format for DATETIME_STR (if available).
     */
    default Optional<String> getDatetimeFormat() {
        return Optional.empty();
    }

    /**
     * Decimal metadata for DECIMAL (if available).
     */
    default Optional<Integer> getDecimalPrecision() {
        return Optional.empty();
    }

    default Optional<Integer> getDecimalScale() {
        return Optional.empty();
    }

    // Convenience predicates for plugins

    default boolean isNumeric() {
        return switch (kind()) {
            case INT, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    default boolean isTemporal() {
        return switch (kind()) {
            case DATE,
                    TIME_MILLIS,
                    TIME_MICROS,
                    TIMESTAMP_MILLIS,
                    TIMESTAMP_MICROS,
                    LOCAL_TIMESTAMP_MILLIS,
                    LOCAL_TIMESTAMP_MICROS,
                    DATETIME_STR -> true;
            default -> false;
        };
    }

    default boolean isCollection() {
        return kind() == Kind.ARRAY || kind() == Kind.MAP;
    }
}

