package art.limitium.sofa;

import art.limitium.schema.*;
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

public class CustomExtension extends AbstractExtension {
    private final List<TypeConverter> typeConverters;
    private final Map<String, Map<String, Entity>> schemas;

    public CustomExtension(List<TypeConverter> typeConverters, Map<String, Map<String, Entity>> schemas) {
        this.typeConverters = typeConverters;
        this.schemas = schemas;
    }

    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();
//            functions.put("customMessage",¡ new CustomFunction());
        return functions;
    }

    @Override
    public Map<String, Filter> getFilters() {
        Map<String, Filter> filters = new HashMap<>();
        filters.put("toSnakeCase", new SnakeCase());
        filters.put("toCamelCase", new CamelCase());
        filters.put("dependenciesRecursiveAll", new DependenciesRecursiveAll());
        filters.put("dependenciesRecursiveUpToClosestDependent", new DependenciesRecursiveUpToClosestDependent());
        filters.put("dependenciesRecursiveToClosestDependent", new DependenciesRecursiveToClosestDependent());
        filters.put("flattenFields", new FlattenFields());
        filters.put("flattenRecords", new FlattenRecords());
        filters.put("enums", new EnumFilter());
        filters.put("from", new FromFilter(schemas));
        filters.put("noRecordLists", new NoRecordListFilter());
        filters.put("recordLists", new FlattenFields());
        typeConverters.forEach(c -> filters.put(c.getName(), new TypeFilter(c)));
        return filters;
    }

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

    public static class FromFilter implements Filter {

        private final Map<String, Map<String, Entity>> schemas;

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

    public static class DependenciesRecursiveUpToClosestDependent implements Filter {

        static class UpToDependentVisitor {
            Map<String, Entity> visited = new LinkedHashMap<>();

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

    public static class DependenciesRecursiveToClosestDependent implements Filter {

        static class UpDependentVisitor {
            Map<String, Entity> visited = new LinkedHashMap<>();

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

    public static class NoRecordListFilter extends RecordListFilter {
        @Override
        protected boolean isRecordList(Object f) {
            return !super.isRecordList(f);
        }
    }

    public static class TypeFilter implements Filter {
        private final TypeConverter typeConverter;

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
}
