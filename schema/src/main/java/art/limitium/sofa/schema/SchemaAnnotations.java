package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import java.util.List;

/**
 * Record level annotations that pin generation intent which cannot be inferred from the dependency
 * graph of a single module.
 * <p>
 * Roles like root/child/dependent are derived from the schemas a module happens to load, so the same
 * record resolves differently in a library and in its consumers. These annotations live in the
 * {@code .avsc} itself, so they travel with the schema into every module that reads it.
 *
 * <ul>
 *     <li>{@code "ownership": "polymorphic"} - the record is owned through an {@code ownerEntity}/
 *     {@code ownerId} pair rather than a concrete foreign key, so it can be owned by records that do
 *     not exist yet. Makes the record eligible for the {@code dependent} template with no owners at
 *     all, which removes the need for placeholder owner records.</li>
 *     <li>{@code "role": "child"} - the record is a composite always embedded into its parent, never
 *     an aggregate root, regardless of whether anything in the current module references it.</li>
 * </ul>
 *
 * Both annotations suppress root-ness: a composite and a polymorphically owned record are by
 * definition not aggregate roots.
 */
public final class SchemaAnnotations {
    public static final String OWNERSHIP = "ownership";
    public static final String ROLE = "role";
    public static final String PRIMARY = "primary";

    public static final String OWNERSHIP_POLYMORPHIC = "polymorphic";
    public static final String ROLE_CHILD = "child";

    private static final List<String> OWNERSHIP_VALUES = List.of(OWNERSHIP_POLYMORPHIC);
    private static final List<String> ROLE_VALUES = List.of(ROLE_CHILD);

    private SchemaAnnotations() {
    }

    /**
     * Checks whether the record declares polymorphic ownership
     */
    public static boolean isPolymorphicallyOwned(Schema schema) {
        return OWNERSHIP_POLYMORPHIC.equals(readAnnotation(schema, OWNERSHIP));
    }

    /**
     * Checks whether the record declares itself a composite child
     */
    public static boolean isDeclaredChild(Schema schema) {
        return ROLE_CHILD.equals(readAnnotation(schema, ROLE));
    }

    /**
     * Checks whether the record is pinned out of being an entity that owns rows.
     * <p>
     * Ownership is otherwise inferred structurally, from holding an array of records. A declared
     * composite holds its records inline instead, so whatever encloses it owns them.
     */
    public static boolean suppressesOwner(Schema schema) {
        return isDeclaredChild(schema);
    }

    /**
     * Checks whether the record is pinned out of being an owned entity.
     * <p>
     * A declared composite is stored inside whatever encloses it, never as rows of its own, so it is
     * not a dependent even when several entities embed it.
     */
    public static boolean suppressesDependent(Schema schema) {
        return isDeclaredChild(schema);
    }

    /**
     * Checks whether the record is pinned to a non root role by any annotation
     */
    public static boolean suppressesRoot(Schema schema) {
        return isPolymorphicallyOwned(schema) || isDeclaredChild(schema);
    }

    /**
     * Checks whether a field is marked as the primary key.
     * <p>
     * Accepts both placements Avro allows: on the field itself
     * ({@code {"name": "id", "type": "string", "primary": true}}) and inside the field's type
     * ({@code {"name": "id", "type": {"type": "string", "primary": true}}}).
     */
    public static boolean isPrimary(Schema.Field field) {
        return Boolean.TRUE.equals(field.getObjectProp(PRIMARY))
                || Boolean.TRUE.equals(field.schema().getObjectProp(PRIMARY));
    }

    /**
     * Validates the annotations declared on a schema
     *
     * @param schema The schema to validate
     * @throws RuntimeException if an annotation carries an unsupported value, if mutually exclusive
     *                          annotations are combined, or if a precondition is unmet
     */
    public static void validate(Schema schema) {
        String ownership = readAnnotation(schema, OWNERSHIP);
        String role = readAnnotation(schema, ROLE);

        if (ownership == null && role == null) {
            return;
        }

        if (schema.getType() != Schema.Type.RECORD) {
            throw new RuntimeException("`" + schema.getFullName() + "` is a " + schema.getType()
                    + ", but `" + OWNERSHIP + "`/`" + ROLE + "` annotations apply to records only");
        }

        if (ownership != null && !OWNERSHIP_VALUES.contains(ownership)) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares `" + OWNERSHIP
                    + ": " + ownership + "`, supported values: " + String.join(", ", OWNERSHIP_VALUES));
        }

        if (role != null && !ROLE_VALUES.contains(role)) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares `" + ROLE
                    + ": " + role + "`, supported values: " + String.join(", ", ROLE_VALUES));
        }

        if (ownership != null && role != null) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares both `" + OWNERSHIP
                    + ": " + ownership + "` and `" + ROLE + ": " + role
                    + "`, they are mutually exclusive: `" + OWNERSHIP_POLYMORPHIC
                    + "` makes it an owned entity, `" + ROLE_CHILD + "` makes it an embedded composite");
        }

        if (OWNERSHIP_POLYMORPHIC.equals(ownership) && schema.getFields().stream().noneMatch(SchemaAnnotations::isPrimary)) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares `" + OWNERSHIP + ": "
                    + OWNERSHIP_POLYMORPHIC + "` but has no field marked `\"" + PRIMARY
                    + "\": true`; a polymorphically owned record is stored as a row and needs a primary key");
        }
    }

    private static String readAnnotation(Schema schema, String annotation) {
        Object value = schema.getObjectProp(annotation);
        return value instanceof String string ? string : null;
    }
}
