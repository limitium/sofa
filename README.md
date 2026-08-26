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
- **Cross-Module Reuse**: Libraries publish a manifest of what they generated, consumers reference it instead of regenerating
- **Schema Annotations**: Records pin roles that cannot be inferred from a single module's dependency graph
- **Post-Generation Hooks**: Ability to run commands after generation

## Installation

Add the following dependency to your project:

```xml
<dependency>
    <groupId>art.limitium.sofa</groupId>
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

### Messages vs Entities

The framework distinguishes between two key concepts:

- **Messages**: Self-contained documents that include all related data inline. Messages are designed for data transfer and typically denormalized, making them ideal for event-driven systems and API payloads. When a message includes related data, it embeds the complete related object directly in the message structure.

In this model:
* One-to-one relationships are embedded directly in the parent record
* One-to-many relationships are represented as arrays within the parent record
* All related data is included in a single document
* Ideal for event-driven systems and message passing architectures

- **Entities**: Database-oriented structures that follow relational database normalization principles. Entities use references (typically through primary keys) to establish relationships between objects, rather than embedding the complete related data. This approach is optimized for data storage and maintains referential integrity through foreign key relationships.

In this model:
* One-to-one relationships are embedded in the parent entity
* One-to-many relationships are extracted into separate entities with an owner_id reference
* Relationships are maintained through foreign key references
* Ideal for relational databases and systems requiring normalized data

For example, consider an Order with LineItems:

a message:
```json
{
  "orderId": "123",
  "items": [
    { "productId": "A1", "quantity": 2 },
    { "productId": "B2", "quantity": 1 }
  ]
}
```

the same as the entities
```json
Order {
  id: "123"
}

OrderItem {
  id: "item1",
  order_id: "123",
  product_id: "A1",
  quantity: 2
}
OrderItem {
  id: "item2",
  order_id: "123",
  product_id: "B2",
  quantity: 1
}
```

The schema generator can handle both approaches, choosing the appropriate template based on whether the record is marked as an owner (containing arrays) or dependent (owned by another record).

### Child Template (`child.peb`)
Used for records that are neither root nor involved in one-to-many relationships, and for records
pinned to this role with `"role": "child"`. Child records are typically:
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

A record also reaches this template with no owners at all when it declares
`"ownership": "polymorphic"`, which is how a library publishes an owned record before any of its
owners exist. See [Schema Annotations](#schema-annotations).

Owners come from `entity | flattenOwners`, which walks up through composites to the nearest records
that are themselves roots or dependents - so a record nested inside a pure composite is owned by
whatever owns that composite, not by the composite. The list is empty when nothing owns the record
yet. Use `entity.polymorphicallyOwned` rather than the list size to choose between a named foreign
key and the `ownerEntity`/`ownerId` pair.

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
4. `dependent.peb` for records owned by others
5. `child.peb` for non-root records
6. `record.peb` as final fallback for any record type

### Role Aware Naming

The `namespace`, `name` and `fullname` templates receive a `role` variable holding the role this
generator resolved for the record - `enum`, `root`, `owner`, `dependent`, `child`, `record`, or
`none` when the generator has no template for it. Naming can then follow the role rather than the
generator, so one generator can emit composites and entities under different conventions:

```yaml
  - path: pojo_entities
    templates:
      namespace: "{{ schema.namespace }}.entities"
      name: "{{ schema.name }}{% if role != 'child' %}Entity{% endif %}"
```

```
com.example.yard.entities.BuildingEntity -> com.example.yard.entities.Garage
com.example.yard.entities.AngarEntity    -> com.example.yard.entities.Garage
com.example.car.entities.CarEntity       (ownerEntity / ownerId)
```

The two worlds keep separate classes for the same record through their namespaces, so a composite
can be named `Garage` in both without colliding: `messages.Garage` carries its records inline while
`entities.Garage` does not.

The role is resolved before relations are wired, from the same ladder used to pick the template.

Schema wide templates such as `schema.peb` see the same ladder without the template gating, through
`entity.role` for the most specific role and `entity.roles` for every role the record qualifies for,
most specific first. A generator narrows `roles` to the templates it provides, while a consumer that
renders every record alike takes `role`. The bundled `puml` generator uses it for its stereotypes,
so a diagram distinguishes `<<root>>`, `<<owner>>`, `<<dependent>>` and `<<child>>` rather than
labelling everything that is not a root a plain record.

## Schema Annotations

Roles like root, child and dependent are inferred from the dependency graph, which means they are a
property of the set of schemas a module happens to load. The same record resolves differently in a
library and in its consumers. Two record level annotations pin the parts that cannot be inferred.
They live in the `.avsc`, so they travel with the schema into every module that reads it.

```json
{
  "type": "record",
  "name": "Car",
  "namespace": "com.example.car",
  "ownership": "polymorphic",
  "fields": [
    {"name": "carId", "type": "string", "primary": true},
    {"name": "model", "type": "string"}
  ]
}
```

- **`"ownership": "polymorphic"`** - the record is owned through an `ownerEntity`/`ownerId` pair
  rather than a named foreign key, so records that do not exist yet can own it. It becomes eligible
  for `dependent.peb` with no owners at all, which removes the need for placeholder owner records.
  Requires a field marked `"primary": true`, since the record is stored as a row.
- **`"role": "child"`** - the record is a composite always embedded into its parent. It is pinned to
  the `child` role: never a root, never an owner, never a dependent. Ownership is inferred
  structurally from holding an array of records, and a composite embedded in several entities has
  several owners, so without this a pure composite becomes a table of its own.

Both are mutually exclusive, both suppress root-ness, and both reject unknown values at load time.

Note that `ownership` answers two separate questions, and they are kept apart: whether the record is
an owned entity at all, which decides the role, and how its ownership is represented, which decides
between a named foreign key and the `ownerEntity`/`ownerId` pair. Ownership is represented
polymorphically when the schema declares it *or* when more than one entity reaches the record, since
a single named key cannot express that. A record embedded in two roots is not thereby an entity.

The primary key marker is accepted either on the field (`{"name": "id", "type": "string", "primary":
true}`) or inside its type (`{"name": "id", "type": {"type": "string", "primary": true}}`).

## Cross-Module Reuse

A library can publish the code it generated so consumers reference those classes instead of
generating their own copies. Consumers can rebuild the record graph by parsing the same schemas, but
they cannot derive the names the producing module assigned, since those depend on its generator
definitions. A manifest carries exactly that missing piece.

The producing module declares what it publishes:

```yaml
manifest:
  artifact: "com.example:car-lib"
  folder: "build/resources/main"
  schemas:
    - "avro/car/Car.avsc"
```

This writes `sofa/manifests/com.example.car-lib.json` after every generator has run:

```json
{
  "artifact": "com.example:car-lib",
  "schemas": ["avro/car/Car.avsc"],
  "generators": ["pojo_messages", "pojo_entities"],
  "records": {
    "com.example.car.Car": {
      "pojo_messages": { "namespace": "com.example.car.messages", "name": "Car",
                         "fullname": "com.example.car.messages.Car" },
      "pojo_entities": { "namespace": "com.example.car.entities", "name": "CarEntity",
                         "fullname": "com.example.car.entities.CarEntity" }
    }
  }
}
```

Records are keyed **per generator path**, because one record legitimately has a different class in
each world. `generators` lists every generator that ran, including ones that produced nothing - that
is what lets a consumer tell "the library never ran this generator" from "it ran and emitted nothing
for this record".

The consuming module names the coordinate:

```yaml
imports:
  - "com.example:car-lib"
```

Imports are all or nothing per library: every schema the library declared is force loaded ahead of
the local ones. Importing a subset would let records lose owners or regain root-ness in the consumer,
which is the drift the manifest exists to prevent.

For each imported record and each generator, one of three things happens:

| Condition | Outcome |
|---|---|
| The library ran this generator and published an artifact | Reference it, write nothing |
| The library ran this generator and published nothing | Fail, the record resolved to different roles in the two modules |
| The library never ran this generator | Generate locally, there is nothing to collide with |

The middle case is the one worth knowing about. It happens when a record is, say, a root in the
library and a composite in the consumer, so the library's `pojo_common` produced nothing for it while
the consumer's needs it. Pin the role with `"role": "child"` or `"ownership": "polymorphic"` and
republish the library.

## Worked Example

A composite shared by two aggregate roots, holding records from a library.

```
Building -> Garage -> [Car]
Angar    -> Garage -> [Car]
```

`Garage` is a composite, not a table of its own, and `Car` is a library record that either root may
end up owning. Both facts are declared in the schemas:

```json
{ "name": "Car",    "namespace": "com.example.car",  "ownership": "polymorphic", "fields": [ ... ] }
{ "name": "Garage", "role": "child", "fields": [
    {"name": "capacity", "type": "int"},
    {"name": "cars", "type": {"type": "array", "items": "com.example.car.Car"}} ] }
```

Two generators, naming by role so the composite keeps its plain name:

```yaml
  - path: pojo_messages
    templates:
      namespace: "{{ schema.namespace }}.messages"
      name: "{{ schema.name }}"
  - path: pojo_entities
    templates:
      namespace: "{{ schema.namespace }}.entities"
      name: "{{ schema.name }}{% if role != 'child' %}Entity{% endif %}"
```

The message world is denormalized - the composite carries its records inline, and an inlined record
needs no ownership because the nesting already says who owns it:

```
com.example.yard.messages.Building   garage: com.example.yard.messages.Garage
com.example.yard.messages.Angar      garage: com.example.yard.messages.Garage
com.example.yard.messages.Garage     capacity: int, cars: List<com.example.car.messages.Car>
com.example.car.messages.Car         carId: String, model: String
```

The entity world is normalized - the composite drops the collection entirely, and the link lives on
the owned record instead:

```
com.example.yard.entities.BuildingEntity   buildingId: String, garage: com.example.yard.entities.Garage
com.example.yard.entities.AngarEntity      angarId: String,    garage: com.example.yard.entities.Garage
com.example.yard.entities.Garage           capacity: int
com.example.car.entities.CarEntity         carId, model, ownerEntity: String, ownerId: long
```

`Garage` exists in both worlds as two classes with the same simple name, kept apart by namespace. It
has to: one class cannot be denormalized and normalized at once. `pojo_common` is only safe for
records that are identical in both worlds, and a composite reaching an entity is not one of those.

Ownership walks up through the composite: `Garage` is not an entity, so `CarEntity` is owned by
whichever root encloses it. Two possible roots means no single named foreign key works, which is what
`"ownership": "polymorphic"` commits to up front - at runtime a car carries
`ownerEntity = "Building"` with the enclosing row's `ownerId`.

Moving `Car` into a library changes nothing about the generated classes. The library publishes
`messages.Car` and `entities.CarEntity`; the consumer imports the coordinate and references both
instead of regenerating them. The only thing a library cannot know is which entities will eventually
own its records, which is precisely what the annotation makes unnecessary.

## Template Functions

SOFA provides various template filters to help with code generation:

- Case conversion: `toSnakeCase`, `toCamelCase`
- Type conversion: `javaType`, `fbType`, `liquidBaseType`
- Dependency traversal: `dependenciesRecursiveAll`, `dependenciesRecursiveUpToClosestDependent`
- Structure flattening: `flattenFields`, `flattenRecords`, `flattenOwners`
- Entity filtering: `enums`, `recordLists`, `noRecordLists`

## Plugin system

Plugins are ordinary jars on the generator runtime classpath. They can contribute:

- **Pebble filters** (merged into `CustomExtension#getFilters()`)
- **Type converters** (appended to the converter list used by `Factory`)

### How discovery works

Plugins are loaded from `plugins` in def.yaml using fully-qualified class names:

```yaml
plugins:
  - "com.mycompany.sofa.MyPlugin"
  - "com.other.Plugin"
```

Each class must implement `art.limitium.sofa.plugin.SofaPlugin` (published as
`sofa-plugin-api`) and have a public no-arg constructor.

### How to add a plugin jar

- **Drop-in jar**: put your plugin jar into `schema/libs/` (picked up automatically via Gradle `runtimeOnly fileTree(...)`).
- **As a dependency**: add it to `schema/build.gradle`:

```gradle
dependencies {
  runtimeOnly "your.group:your-artifact:1.0.0"
}
```

### How to write a plugin

Your plugin module should:

- depend on `art.limitium.sofa:sofa-plugin-api:<version>` (JDK-only API)
- implement `art.limitium.sofa.plugin.SofaPlugin` and return:
  - `SofaPlugin.SofaFilter` implementations (generator will proxy them into Pebble filters)
  - `SofaPlugin.SofaTypeConverter<?>` implementations operating on `art.limitium.sofa.plugin.SofaType`
    (generator will adapt internal schema `Type` into `SofaType` and proxy into SOFA `TypeConverter`)

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
