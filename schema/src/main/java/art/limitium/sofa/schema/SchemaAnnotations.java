package art.limitium.sofa.schema;

import org.apache.avro.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Record level annotations that pin generation intent which cannot be inferred from the dependency
 * graph of a single module.
 * <p>
 * Roles like root/owner/carrier/dependent/child are derived from the schemas a module happens to
 * load, so the same record resolves differently in a library and in its consumers. A library sees
 * only its own side of the boundary: nothing there references the records it exists to publish, and
 * nothing there owns them, so every one of them looks like an aggregate root. These annotations live
 * in the {@code .avsc} itself, so they travel with the schema into every module that reads it.
 *
 * <ul>
 *     <li>{@code "role": "<role>"} - the place on the role ladder the record holds, whatever the
 *     current module can see. One of {@code root}, {@code owner}, {@code carrier}, {@code dependent}
 *     or {@code child}.</li>
 *     <li>{@code "ownership": "polymorphic"} - how an owned entity's link back to its owner is
 *     shaped: an {@code ownerEntity}/{@code ownerId} pair rather than a concrete foreign key, so it
 *     can be owned by records that do not exist yet.</li>
 * </ul>
 *
 * The two answer separate questions and only meet on {@code dependent}: a record pinned to that role
 * is owned by nothing the module can name, so it has to say how the link is represented, which for
 * now means {@code "ownership": "polymorphic"}. On any other role an ownership annotation contradicts
 * the pin and is rejected.
 */
public final class SchemaAnnotations {
    public static final String OWNERSHIP = "ownership";
    public static final String ROLE = "role";
    public static final String PRIMARY = "primary";

    public static final String OWNERSHIP_POLYMORPHIC = "polymorphic";

    public static final String ROLE_ROOT = "root";
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_CARRIER = "carrier";
    public static final String ROLE_DEPENDENT = "dependent";
    public static final String ROLE_CHILD = "child";

    private static final List<String> OWNERSHIP_VALUES = List.of(OWNERSHIP_POLYMORPHIC);
    private static final List<String> ROLE_VALUES =
            List.of(ROLE_ROOT, ROLE_OWNER, ROLE_CARRIER, ROLE_DEPENDENT, ROLE_CHILD);

    private SchemaAnnotations() {
    }

    /**
     * Checks whether the record declares polymorphic ownership
     */
    public static boolean isPolymorphicallyOwned(Schema schema) {
        return OWNERSHIP_POLYMORPHIC.equals(readAnnotation(schema, OWNERSHIP));
    }

    /**
     * Checks whether the record declares itself an aggregate root, even where something embeds it
     */
    public static boolean isDeclaredRoot(Schema schema) {
        return ROLE_ROOT.equals(readAnnotation(schema, ROLE));
    }

    /**
     * Checks whether the record declares itself a carrier, an owner kept inside its parent.
     * <p>
     * Carrying is otherwise read off the shape, from a collection the record reaches. The pin is for
     * the layers no collection gives away: a composite embedding a polymorphically owned record
     * carries it in a denormalized world and points at its row in a normalized one, which splits the
     * composite in two just as a collection would.
     */
    public static boolean isDeclaredCarrier(Schema schema) {
        return ROLE_CARRIER.equals(readAnnotation(schema, ROLE));
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
     * Ownership is otherwise inferred structurally, from holding an array of records. A record
     * pinned to an embedded role holds its records inline instead, so whatever encloses it owns them.
     */
    public static boolean suppressesOwner(Schema schema) {
        return isDeclaredChild(schema) || isDeclaredCarrier(schema);
    }

    /**
     * Checks whether the record is pinned out of being an owned entity.
     * <p>
     * A record pinned to an embedded role is stored inside whatever encloses it, never as rows of its
     * own, so it is not a dependent even when several entities embed it.
     */
    public static boolean suppressesDependent(Schema schema) {
        return isDeclaredChild(schema) || isDeclaredCarrier(schema);
    }

    /**
     * Checks whether the record is pinned to a non root role by any annotation
     */
    public static boolean suppressesRoot(Schema schema) {
        String role = readAnnotation(schema, ROLE);
        return isPolymorphicallyOwned(schema) || (role != null && !ROLE_ROOT.equals(role));
    }

    /**
     * Checks whether the record pins its role by annotation at all, whatever the pin says
     */
    public static boolean pinsRole(Schema schema) {
        return readAnnotation(schema, ROLE) != null || readAnnotation(schema, OWNERSHIP) != null;
    }

    /**
     * Renders the annotations a record declares, for reporting
     *
     * @return The annotations as {@code key: value} pairs, empty for an unannotated record
     */
    public static String describe(Schema schema) {
        List<String> declared = new ArrayList<>();
        String role = readAnnotation(schema, ROLE);
        if (role != null) {
            declared.add(ROLE + ": " + role);
        }
        String ownership = readAnnotation(schema, OWNERSHIP);
        if (ownership != null) {
            declared.add(OWNERSHIP + ": " + ownership);
        }
        return String.join(", ", declared);
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
     * @throws RuntimeException if an annotation carries an unsupported value, if the two annotations
     *                          contradict each other, or if a pin cannot hold for this record
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

        if (ownership != null && role != null && !ROLE_DEPENDENT.equals(role)) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares both `" + OWNERSHIP
                    + ": " + ownership + "` and `" + ROLE + ": " + role
                    + "`, they are mutually exclusive: `" + OWNERSHIP + "` shapes the link an owned"
                    + " entity keeps to its owner, and `" + ROLE + ": " + role + "` is not an owned entity."
                    + " Only `" + ROLE + ": " + ROLE_DEPENDENT + "` takes an `" + OWNERSHIP + "`");
        }

        if (ROLE_DEPENDENT.equals(role) && ownership == null) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares `" + ROLE + ": "
                    + ROLE_DEPENDENT + "` without an `" + OWNERSHIP + "`; nothing here owns it, so the"
                    + " link back to its owner has no name to take. Declare `" + OWNERSHIP + ": "
                    + OWNERSHIP_POLYMORPHIC + "` alongside it");
        }

        if (ROLE_DEPENDENT.equals(role) && SchemaShape.ownsCollection(schema)) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares `" + ROLE + ": "
                    + ROLE_DEPENDENT + "` but holds a collection of records, which makes it an `"
                    + ROLE_OWNER + "`, the role above it on the ladder. Drop the `" + ROLE
                    + "` to keep the owner role, or declare `" + ROLE + ": " + ROLE_OWNER + "`");
        }

        if (ROLE_OWNER.equals(role) && !SchemaShape.ownsCollection(schema)) {
            throw new RuntimeException("Record `" + schema.getFullName() + "` declares `" + ROLE + ": "
                    + ROLE_OWNER + "` but holds no collection of records, so it owns nothing. An owner"
                    + " is a record with rows of its own that holds a collection; declare `" + ROLE + ": "
                    + ROLE_CARRIER + "` for one that holds it while staying embedded");
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
