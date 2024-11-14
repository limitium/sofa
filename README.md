# SOFA - Schema-Oriented Framework for Avro

SOFA is a flexible code generation framework that transforms Avro schemas into various target formats using customizable templates. It provides a powerful way to generate code, documentation, or any text-based output while maintaining complex relationships between Avro records.

## Features

- **Template-Based Generation**: Uses Pebble templating engine for flexible code generation
- **Multiple Output Formats**: Can generate multiple outputs from the same schema
- **Relationship Awareness**: Understands and preserves record relationships and dependencies
- **Type System Support**: Built-in type converters for various target platforms:
    - Java
    - Flatbuffers
    - LiquidBase
    - Apache Connect
- **Customizable Naming**: Configurable naming strategies for namespaces, classes, and files
- **Filtering**: Supports white/black listing of entities for selective generation
- **Post-Generation Hooks**: Ability to run commands after generation

## Installation

Add the following dependency to your project:

```xml
<dependency>
    <groupId>art.limitium</groupId>
    <artifactId>sofa</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

1. Create a YAML configuration file defining your generation rules:

```yaml
schemas:
  - path/to/schema1.avsc
  - path/to/schema2.avsc

values:
  packageName: "com.example"
  version: "1.0.0"

generators:
  - path: "generators/java"
    templates:
      namespace: "{{packageName}}"
      name: "{{schema.name}}"
      fullname: "{{namespace}}.{{name}}"
      folder: "src/main/java/{{namespace | replace('.', '/')}}"
      filename: "{{name}}.java"
    filters:
      white:
        - "com.example.User"
        - "com.example.Order"
```

2. Run the generator:

```bash
java -jar sofa.jar path/to/config.yaml
```

## Template Types in Detail

SOFA uses different templates to handle various entity relationships and types. Each template serves a specific purpose in the code generation process:

### Root Template (`root.peb`)
Used for generating root record entities that have no parent dependencies. Root records typically represent:
- Top-level domain objects
- Aggregate roots in DDD terms
- Entry points for object graphs

Example use case: Generating main entity classes that own other entities.

```java
// Example root template usage
public class {{name}} {
    private final String id;
    {% for owned in entity.dependencies %}
    private final List<{{owned.name}}> {{owned.name | toSnakeCase}}s;
    {% endfor %}
}
```

### Child Template (`child.peb`)
Used for records that are neither root nor involved in one-to-many relationships. Child records are typically:
- Value objects
- Component parts of larger entities
- Supporting data structures

Example use case: Generating embedded/component classes.

```java
// Example child template usage
public class {{name}} {
    {% for field in entity.fields %}
    private {{field.type | javaType}} {{field.name}};
    {% endfor %}
}
```

### Owner Template (`owner.peb`)
Used for records that contain one-to-many relationships with other records. Owner records:
- Manage collections of other entities
- Control lifecycle of dependent entities
- Implement parent-side of relationships

Example use case: Generating container classes with collection management.

```java
// Example owner template usage
public class {{name}} {
    {% for field in entity.fields | recordLists %}
    private List<{{field.type.elementType | javaType}}> {{field.name}};

    public void add{{field.name | capitalize}}({{field.type.elementType | javaType}} item) {
        {{field.name}}.add(item);
    }
    {% endfor %}
}
```

### Dependent Template (`dependent.peb`)
Used for records that are owned by other records in one-to-many relationships. Dependent records:
- Belong to parent entities
- Have their lifecycle managed by owners
- Implement child-side of relationships

Example use case: Generating entities that are always part of a collection.

```java
// Example dependent template usage
public class {{name}} {
    private final {{entity.owners[0].name}} owner;

    public {{name}}({{entity.owners[0].name}} owner) {
        this.owner = owner;
    }

    {% for field in entity.fields %}
    private {{field.type | javaType}} {{field.name}};
    {% endfor %}
}
```
### Record Template (`record.peb`)
Used as a fallback template for any record type that doesn't match more specific templates. This template is:
- The most generic template type
- Used when no other template matches
- Suitable for basic record generation regardless of relationships

Record templates typically handle:
- Basic field generation
- Common methods (getters/setters)
- Standard class structure

Example use case: Generating standard data classes or when relationship-specific templates are not needed.

```java
// Example record template usage
public class {{name}} {
  {% for field in entity.fields %}
  private {{field.type | javaType}} {{field.name}};
  {% endfor %}

  public {{name}}() {}

  {% for field in entity.fields %}
  public {{field.type | javaType}} get{{field.name | capitalize}}() {
      return {{field.name}};
  }

  public void set{{field.name | capitalize}}({{field.type | javaType}} {{field.name}}) {
      this.{{field.name}} = {{field.name}};
  }
  {% endfor %}

  {% if entity.fields | recordLists %}
  // Collection management methods
  {% for field in entity.fields | recordLists %}
  public void add{{field.name | capitalize | singular}}({{field.type.elementType | javaType}} item) {
      if (this.{{field.name}} == null) {
            this.{{field.name}} = new ArrayList<>();
      }
      this.{{field.name}}.add(item);
  }
  {% endfor %}
  {% endif %}
}
```
### Enum Template (`enum.peb`)
Used for generating enum types. Supports:
- Basic enum generation
- Enum with additional properties
- Enum with aliases/descriptions

Example use case: Generating type-safe enumeration classes.

```java
// Example enum template usage
public enum {{name}} {
    {% for symbol in symbols %}
    {{symbol}}{% if not loop.last %},{% endif %}
    {% endfor %}
}
```

### Template Selection Priority

When multiple templates are available, SOFA selects the most specific template in this order:
1. `enum.peb` for enum types
2. `root.peb` for root records
3. `owner.peb` for records with collections
4. `child.peb` for non-root records
5. `dependent.peb` for records owned by others
6. `record.peb` as final fallback for any record type

## Template Functions

SOFA provides various template filters to help with code generation:

- Case conversion: `toSnakeCase`, `toCamelCase`
- Type conversion: `javaType`, `fbType`, `liquidBaseType`
- Dependency traversal: `dependenciesRecursiveAll`, `dependenciesRecursiveUpToClosestDependent`
- Structure flattening: `flattenFields`, `flattenRecords`, `flattenOwners`
- Entity filtering: `enums`, `recordLists`, `noRecordLists`

## Example

Given an Avro schema:

```json
{
  "type": "record",
  "name": "User",
  "namespace": "com.example",
  "fields": [
    {"name": "id", "type": "string", "logicalType": "uuid"},
    {"name": "name", "type": "string"},
    {"name": "status", "type": "enum", "name": "UserStatus", "symbols": ["ACTIVE", "INACTIVE"]}
  ]
}
```

And a Java template:

```java
package {{namespace}};

public class {{name}} {
    {% for field in entity.fields %}
    private {{field.type | javaType}} {{field.name}};
    {% endfor %}
}
```

SOFA will generate:

```java
package com.example;

public class User {
    private String id;
    private String name;
    private UserStatus status;
}
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## TODO

- External conditions per template generation
- Smart override detection for unchanged entities
- Extension loading from classpath
- Gradle plugin/script integration
- Example tests
- Comprehensive documentation

## License

This project is licensed under the MIT License - see the LICENSE file for details.
