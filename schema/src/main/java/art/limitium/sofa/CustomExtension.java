package art.limitium.sofa;

import art.limitium.sofa.schema.*;
import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.extension.AbstractExtension;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.extension.Function;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
/**
 * Custom extension for Pebble template engine that provides additional filters and functions.
 * This extension adds type conversion capabilities and schema-aware operations.
 */
/**
 * Custom extension for Pebble template engine that provides additional filters and functions.
 * This extension adds type conversion capabilities and schema-aware operations.
 */
public class CustomExtension extends AbstractExtension {
    /** List of type converters used for converting between different type systems */
    private final List<TypeConverter> typeConverters;
    
    /** Map of schema names to their entity definitions */
    private final Map<String, Map<String, Entity>> schemas;

    /**
     * Creates a new CustomExtension with the specified type converters and schemas
     * @param typeConverters List of type converters to use
     * @param schemas Map of schema names to their entity definitions
     */
    public CustomExtension(List<TypeConverter> typeConverters, Map<String, Map<String, Entity>> schemas) {
        this.typeConverters = typeConverters;
        this.schemas = schemas;
    }

    /**
     * Returns the custom functions provided by this extension
     * @return Map of function names to Function implementations
     */
    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();
//            functions.put("customMessage",¡ new CustomFunction());
        return functions;
    }

    /**
     * Returns the custom filters provided by this extension
     * @return Map of filter names to Filter implementations
     */
    @Override
    public Map<String, Filter> getFilters() {
        Map<String, Filter> filters = new HashMap<>();
        // String case conversion filters
        filters.put("toSnakeCase", new SnakeCase());
        filters.put("toCamelCase", new CamelCase());
        
        // Dependency traversal filters
        filters.put("dependenciesRecursiveAll", new DependenciesRecursiveAll());
        filters.put("dependenciesRecursiveUpToClosestDependent", new DependenciesRecursiveUpToClosestDependent());
        filters.put("dependenciesRecursiveToClosestDependent", new DependenciesRecursiveToClosestDependent());
        
        // Structure flattening filters
        filters.put("flattenFields", new FlattenFields());
        filters.put("flattenFieldsWithRecords", new FlattenFieldsWithRecords());
        filters.put("flattenRecords", new FlattenRecords());
        filters.put("flattenOwners", new FlattenOwners());
        
        // Entity type filters
        filters.put("enums", new EnumFilter());
        filters.put("from", new FromFilter(schemas));
        filters.put("noRecordLists", new NoRecordListFilter());
        filters.put("recordLists", new RecordListFilter());
        
        // Add type conversion filters from all converters
        typeConverters.forEach(c -> filters.put(c.getName(), new TypeFilter(c)));

        // Add test filters
        filters.put("hasPrimary", new HasPrimaryFilter());
        filters.put("primary", new PrimaryFilter());
        filters.put("field", new FieldFilter());
        filters.put("uncapitalize", new UncapitalizeFilter());
    
        return filters;
    }

    /**
     * Filter that converts camelCase strings to snake_case format
     */
    public static class SnakeCase implements Filter {

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof String str) {
                return Pattern.compile("(?<!^)([A-Z])|(^[A-Z])")
                        .matcher(str)
                        .replaceAll(m -> {
                            if (m.group(2) != null) {
                                return m.group(2).toLowerCase();
                            } else {
                                return "_" + m.group(1).toLowerCase();
                            }
                        });

            }
            return input;
        }
    }

    /**
     * Filter that converts snake_case strings to camelCase format
     */
    public static class CamelCase implements Filter {

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof String str) {
                String camelCase = Pattern.compile("_(.)")
                        .matcher(str)
                        .replaceAll(m -> m.group(1).toUpperCase());
                // Ensure the first character is lowercase
                camelCase = camelCase.substring(0, 1).toLowerCase() + camelCase.substring(1);
                return camelCase;


            }
            return input;
        }
    }

    /**
     * Filter that extracts only enum entities from a list of entities
     */
    public static class EnumFilter implements Filter {

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof List<?> entities) {
                return entities.stream().filter(EnumEntity.class::isInstance).collect(Collectors.toList());
            }
            return input;
        }
    }

    /**
     * Filter that looks up an entity in a different schema by its full name
     */
    public static class FromFilter implements Filter {

        /** Map of schema names to their entity definitions */
        private final Map<String, Map<String, Entity>> schemas;

        /**
         * Creates a new FromFilter with the specified schemas
         * @param schemas Map of schema names to their entity definitions
         */
        public FromFilter(Map<String, Map<String, Entity>> schemas) {

            this.schemas = schemas;
        }

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof Entity entity) {
                if (args.containsKey("0")) {
                    Map<String, Entity> schema = schemas.get(args.get("0"));
                    if (schema == null) {
                        throw new RuntimeException("Unable to find generator `" + args.get("0") + "`, available generators: " + String.join(",", schemas.keySet()));
                    }
                    return schema.get(entity.getSchema().getFullName());
                }
            }
            return input;
        }
    }

    /**
     * Filter that recursively collects all dependencies of a record entity
     */
    public static class DependenciesRecursiveAll implements Filter {

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof RecordEntity recordEntity) {
                TriesToReverseListConverter<Entity> triesToReverseListConverter = new TriesToReverseListConverter<>();
                triesToReverseListConverter.addNode(recordEntity);
                return triesToReverseListConverter.getFlatted();
            }
            return input;
        }
    }

    /**
     * Filter that recursively collects dependencies up to but not including the closest dependent record
     */
    public static class DependenciesRecursiveUpToClosestDependent implements Filter {

        /**
         * Helper class that visits and collects entities up to dependent records
         */
        static class UpToDependentVisitor {
            /** Map to store visited entities, preserving order of insertion */
            Map<String, Entity> visited = new LinkedHashMap<>();

            /**
             * Visits an entity and its dependencies, collecting them up to dependent records
             * @param e Entity to visit
             * @param owner Owner record entity
             */
            public void visit(Entity e, RecordEntity owner) {
                if (!visited.containsKey(e.getFullname())) {
                    //closest 1-N dependent in branch, just stop on it
                    boolean is1NDependent;
                    is1NDependent = owner.getFields().stream()
                            .filter(f -> f.type() instanceof Type.ArrayType)
                            .map(f -> ((Type.ArrayType) f.type()).getElementType())
                            .filter(Type.RecordType.class::isInstance)
                            .anyMatch(r -> ((Type.RecordType) r).getRecord().equals(e));

                    if (is1NDependent) {
                        return;
                    }

                    if (e instanceof RecordEntity re) {
                        for (Entity dependency : re.getDependencies()) {
                            if (dependency instanceof RecordEntity dre) {
                                visit(dre, re);
                            } else {
                                //Enums just add
                                visited.put(dependency.getFullname(), dependency);
                            }
                        }
                    } else {
                        //Enums just add
                        visited.put(e.getFullname(), e);
                    }
                }
            }

            /**
             * Returns list of visited entities
             * @return List of collected entities
             */
            public List<Entity> getVisited() {
                return visited.values().stream().toList();
            }
        }

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof RecordEntity recordEntity) {
                UpToDependentVisitor upToDependentVisitor = new UpToDependentVisitor();
                for (Entity dependency : recordEntity.getDependencies()) {
                    upToDependentVisitor.visit(dependency, recordEntity);
                }

                return upToDependentVisitor.getVisited();
            }
            return input;
        }
    }

    /**
     * Filter that recursively collects dependencies including the closest dependent record
     */
    public static class DependenciesRecursiveToClosestDependent implements Filter {

        /**
         * Helper class that visits and collects entities including dependent records
         */
        static class UpDependentVisitor {
            /** Map to store visited entities, preserving order of insertion */
            Map<String, Entity> visited = new LinkedHashMap<>();

            /**
             * Visits an entity and its dependencies, collecting them including dependent records
             * @param e Entity to visit
             * @param owner Owner record entity
             */
            public void visit(Entity e, RecordEntity owner) {
                if (!visited.containsKey(e.getFullname())) {
                    //closest 1-N dependent in branch, add it and stop on it
                    boolean is1NDependent;
                    is1NDependent = owner.getFields().stream()
                            .filter(f -> f.type() instanceof Type.ArrayType)
                            .map(f -> ((Type.ArrayType) f.type()).getElementType())
                            .filter(Type.RecordType.class::isInstance)
                            .anyMatch(r -> ((Type.RecordType) r).getRecord().equals(e));

                    if (is1NDependent) {
                        visited.put(e.getFullname(), e);
                        return;
                    }

                    if (e instanceof RecordEntity re) {
                        for (Entity dependency : re.getDependencies()) {
                            if (dependency instanceof RecordEntity dre) {
                                visit(dre, re);
                            } else {
                                //Enums just add
                                visited.put(dependency.getFullname(), dependency);
                            }
                        }
                    } else {
                        //Enums just add
                        visited.put(e.getFullname(), e);
                    }
                }
            }

            /**
             * Returns list of visited entities
             * @return List of collected entities
             */
            public List<Entity> getVisited() {
                return visited.values().stream().toList();
            }
        }

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof RecordEntity recordEntity) {
                UpDependentVisitor upToDependentVisitor = new UpDependentVisitor();
                for (Entity dependency : recordEntity.getDependencies()) {
                    upToDependentVisitor.visit(dependency, recordEntity);
                }

                return upToDependentVisitor.getVisited();
            }
            return input;
        }
    }

    /**
     * Filter that flattens nested record fields into a single level with concatenated field names
     */
    public static class FlattenFields implements Filter {

        @Override
        public Object apply(Object o, Map<String, Object> args, PebbleTemplate pebbleTemplate, EvaluationContext evaluationContext, int i) throws PebbleException {
            if (o instanceof RecordEntity record) {
                String joiner = "_";
                if (args.containsKey("0")) {
                    joiner = String.valueOf(args.get("0"));
                }
                List<RecordEntity.Field> flattenFields = new ArrayList<>();
                flattenFields(record, "", flattenFields, joiner);
                return flattenFields;
            }
            return o;
        }

        /**
         * Recursively flattens nested record fields
         * @param record Record entity to flatten
         * @param fieldPrefix Current field name prefix
         * @param flattenFields List to collect flattened fields
         * @param joiner String to join field name parts
         */
        private void flattenFields(RecordEntity record, String fieldPrefix, List<RecordEntity.Field> flattenFields, String joiner) {
            if (!fieldPrefix.isEmpty()) {
                fieldPrefix = fieldPrefix + joiner;
            }

            for (RecordEntity.Field field : record.getFields()) {
                String fieldName = fieldPrefix + field.name();
                if (field.type() instanceof Type.RecordType recordType) {
                    flattenFields(recordType.getRecord(), fieldName, flattenFields, joiner);
                } else {
                    flattenFields.add(new RecordEntity.Field(fieldName, field.type()));
                }
            }
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }

    /**
     * Filter that flattens nested record fields into a single level with concatenated field names, but keep records for util usage
     */
    public static class FlattenFieldsWithRecords implements Filter {

        @Override
        public Object apply(Object o, Map<String, Object> args, PebbleTemplate pebbleTemplate, EvaluationContext evaluationContext, int i) throws PebbleException {
            if (o instanceof RecordEntity record) {
                String joiner = "_";
                if (args.containsKey("0")) {
                    joiner = String.valueOf(args.get("0"));
                }
                List<RecordEntity.Field> flattenFields = new ArrayList<>();
                flattenFields(record, "", flattenFields, joiner);
                return flattenFields;
            }
            return o;
        }

        /**
         * Recursively flattens nested record fields
         * @param record Record entity to flatten
         * @param fieldPrefix Current field name prefix
         * @param flattenFields List to collect flattened fields
         * @param joiner String to join field name parts
         */
        private void flattenFields(RecordEntity record, String fieldPrefix, List<RecordEntity.Field> flattenFields, String joiner) {
            if (!fieldPrefix.isEmpty()) {
                fieldPrefix = fieldPrefix + joiner;
            }

            for (RecordEntity.Field field : record.getFields()) {
                String fieldName = fieldPrefix + field.name();
                if (field.type() instanceof Type.RecordType recordType) {
                    flattenFields.add(new RecordEntity.Field(fieldName, field.type()));
                    flattenFields(recordType.getRecord(), fieldName, flattenFields, joiner);
                    Type.RecordCloseType type = new Type.RecordCloseType(recordType);
                    flattenFields.add(new RecordEntity.Field(fieldName, type));
                } else {
                    flattenFields.add(new RecordEntity.Field(fieldName, field.type()));
                }
            }
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }

    /**
     * Filter that flattens nested records while preserving record type information
     */
    public static class FlattenRecords implements Filter {

        @Override
        public Object apply(Object o, Map<String, Object> args, PebbleTemplate pebbleTemplate, EvaluationContext evaluationContext, int i) throws PebbleException {
            if (o instanceof RecordEntity record) {
                String joiner = "_";
                if (args.containsKey("0")) {
                    joiner = String.valueOf(args.get("0"));
                }
                List<RecordEntity.Field> flattenRecord = new ArrayList<>();
                flattenFields(record, "", flattenRecord, joiner);
                return flattenRecord;
            }
            return o;
        }

        /**
         * Recursively flattens nested records while preserving record type information
         * @param record Record entity to flatten
         * @param fieldPrefix Current field name prefix
         * @param flattenFields List to collect flattened fields
         * @param joiner String to join field name parts
         */
        private void flattenFields(RecordEntity record, String fieldPrefix, List<RecordEntity.Field> flattenFields, String joiner) {
            if (!fieldPrefix.isEmpty()) {
                fieldPrefix = fieldPrefix + joiner;
            }

            for (RecordEntity.Field field : record.getFields()) {
                String fieldName = fieldPrefix + field.name();
                if (field.type() instanceof Type.RecordType recordType) {
                    flattenFields.add(new RecordEntity.Field(fieldName, field.type()));
                    flattenFields(recordType.getRecord(), fieldName, flattenFields, joiner);
                }
            }
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }

    /**
     * Filter that flattens the ownership hierarchy of a record entity
     */
    public static class FlattenOwners implements Filter {

        @Override
        public Object apply(Object o, Map<String, Object> args, PebbleTemplate pebbleTemplate, EvaluationContext evaluationContext, int i) throws PebbleException {
            if (o instanceof RecordEntity record && record.isDependent()) {
                Set<RecordEntity> flattenOwners = new HashSet<>();
                flattenOwners(record.getParents(), flattenOwners);
                return flattenOwners.stream().toList();
            }
            return o;
        }

        /**
         * Recursively flattens the ownership hierarchy
         * @param owners Set of owner record entities
         * @param flattenOwners Set to collect flattened owners
         */
        private void flattenOwners(Set<RecordEntity> owners, Set<RecordEntity> flattenOwners) {
            owners.forEach(o -> {
                if (o.isDependent() || o.isRoot()) {
                    flattenOwners.add(o);
                } else {
                    flattenOwners(o.getParents(), flattenOwners);
                }
            });
        }

        @Override
        public List<String> getArgumentNames() {
            return null;
        }
    }

    /**
     * Filter that identifies fields that are lists of record types
     */
    public static class RecordListFilter implements Filter {

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }


        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof List<?> entities) {
                return entities.stream().filter(this::isRecordList).collect(Collectors.toList());
            }
            return input;
        }

        /**
         * Checks if a field is a list of record types
         * @param o Object to check
         * @return true if the object is a field containing a list of records
         */
        protected boolean isRecordList(Object o) {
            if (o instanceof RecordEntity.Field field) {
                if (field.type() instanceof Type.ArrayType arrayType) {
                    return arrayType.getElementType() instanceof Type.RecordType;
                }
                return false;
            }
            return true;
        }
    }

    /**
     * Filter that identifies fields that are NOT lists of record types
     */
    public static class NoRecordListFilter extends RecordListFilter {
        @Override
        protected boolean isRecordList(Object f) {
            return !super.isRecordList(f);
        }
    }

    /**
     * Filter that converts types using a specified TypeConverter
     */
    public static class TypeFilter implements Filter {
        /** The type converter to use for conversions */
        private final TypeConverter typeConverter;

        /**
         * Creates a new TypeFilter with the specified converter
         * @param typeConverter The type converter to use
         */
        public TypeFilter(TypeConverter typeConverter) {
            this.typeConverter = typeConverter;
        }

        @Override
        public List<String> getArgumentNames() {
            return null; // This filter does not require any arguments
        }

        @Override
        public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
            if (input instanceof Type type) {
                return typeConverter.getType(type);
            }
            return input;
        }
    }


    //Test filters
    // Filter implementations
public static class HasPrimaryFilter implements Filter {
    
    @Override
    public List<String> getArgumentNames() {
        return null; // This filter does not require any arguments
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input instanceof List<?> fields) {
            return fields.stream()
                .anyMatch(f -> f instanceof RecordEntity.Field field && field.isPrimary());
        }
        return false;
    }
}

public static class PrimaryFilter implements Filter {
    
    @Override
    public List<String> getArgumentNames() {
        return null; // This filter does not require any arguments
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input instanceof List<?> fields) {
            return fields.stream()
                .filter(f -> f instanceof RecordEntity.Field field && field.isPrimary())
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

public static class FieldFilter implements Filter {
    
    @Override
    public List<String> getArgumentNames() {
        return List.of("name");
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input instanceof RecordEntity.Field field) {
            String fieldName = (String) args.get("name");
            return switch(fieldName) {
                case "name" -> field.name();
                case "type" -> field.type();
                default -> null;
            };
        }
        return null;
    }
}

public static class UncapitalizeFilter implements Filter {
    
    @Override
    public List<String> getArgumentNames() {
        return null; // This filter does not require any arguments
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input instanceof String str && !str.isEmpty()) {
            return Character.toLowerCase(str.charAt(0)) + str.substring(1);
        }
        return input;
}
}
}
