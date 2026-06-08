# FakeDB Spring Boot Starter

FakeDB is a lightweight Spring Boot starter that persists application data in a local JSON file.
It is designed for development, tests, prototypes, and local workflows where a real database driver
is not available or would add unnecessary setup.

The library provides a small repository-style API backed by JSON files. It is not intended to replace
a production database, but to make persistent mock data easy to use inside Spring Boot applications.

## Features

- JSON-file backed persistence.
- Spring Boot 3 auto-configuration.
- Generic CRUD repository API.
- Entity id resolution through `@FakeDBId` or a field named `id`.
- Optional entity-to-table mapping with `@FakeDBTable`.
- Field-to-column mapping with `@FakeDBColumn`.
- Predicate-based repository queries.
- Sorting and pagination through Spring Data `Sort`, `Pageable`, and `Page`.
- Configurable database file path, database name, default schema, pretty printing, auto creation, and backups.
- Jackson support, including Java time modules through `findAndRegisterModules()`.

## Requirements

- Java 17+
- Spring Boot 3.5.x
- Maven

## Installation

FakeDB is currently a local Maven artifact.

Build and install it into your local Maven repository:

```bash
./mvnw clean install
```

On Windows:

```bash
./mvnw.cmd clean install
```

Then add it to another Spring Boot project:

```xml
<dependency>
    <groupId>io.github.aiellolorenzo23</groupId>
    <artifactId>fakedb-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

If the artifact is later published to a package registry or Maven hosting service, the dependency
coordinates can stay the same unless the group, artifact, or version changes.

## Publishing to GitHub Packages

GitHub Packages can host FakeDB as a Maven package. Replace `OWNER` with your GitHub username or
organization and `REPOSITORY` with the GitHub repository that contains this project.

Add this `distributionManagement` block to `pom.xml` before publishing:

```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub OWNER Apache Maven Packages</name>
        <url>https://maven.pkg.github.com/OWNER/REPOSITORY</url>
    </repository>
</distributionManagement>
```

Create or update `~/.m2/settings.xml` with a GitHub personal access token. The server `id` must match
the `distributionManagement` repository id.

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>OWNER</username>
            <password>GITHUB_TOKEN</password>
        </server>
    </servers>
</settings>
```

The token needs permission to publish packages. For a classic personal access token, use `write:packages`
and, if the repository is private, `repo`.

Publish the package:

```bash
./mvnw deploy
```

On Windows:

```bash
./mvnw.cmd deploy
```

To consume the package from another Maven project, add the GitHub Packages repository:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/REPOSITORY</url>
    </repository>
</repositories>
```

Then add the dependency:

```xml
<dependency>
    <groupId>io.github.aiellolorenzo23</groupId>
    <artifactId>fakedb-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

GitHub Packages may require authentication to install packages too, including public packages. In that
case, configure the consuming project's Maven environment with a matching `github` server in
`~/.m2/settings.xml`, using a token with `read:packages`.

## Configuration

Configure FakeDB with the `fakedb.*` properties:

```yaml
fakedb:
  enabled: true
  path: ./data/fakedb.json
  database: local_database
  default-schema: main
  auto-create: true
  pretty-print: true
  backup-on-save: false
  naming-strategy: identity
  fail-on-unknown-properties: true
```

| Property | Default | Description |
| --- | --- | --- |
| `fakedb.enabled` | `true` | Enables or disables FakeDB auto-configuration. |
| `fakedb.path` | required | Path of the JSON database file. |
| `fakedb.database` | `default` | Logical database name stored in the JSON file. |
| `fakedb.default-schema` | `main` | Schema used when no schema is specified. |
| `fakedb.auto-create` | `true` | Creates an empty in-memory database structure when the file does not exist. The file is written on save/delete operations. |
| `fakedb.pretty-print` | `true` | Writes formatted JSON. |
| `fakedb.backup-on-save` | `false` | Creates a `.bak` copy before overwriting an existing database file. |
| `fakedb.naming-strategy` | `identity` | Entity field naming strategy. Supported values: `identity`, `snake_case`. |
| `fakedb.fail-on-unknown-properties` | `true` | Fails when JSON rows contain fields that are not present in the entity. Set to `false` to ignore extra fields. |

`fakedb.path` is required. If it is missing or blank, FakeDB raises a `FakeDBConfigurationException`.

## Basic Usage

Spring Boot auto-configures a `FakeDBTemplate` bean when FakeDB is enabled.

Define an entity:

```java
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBTable;

@FakeDBTable(value = "students", schema = "main")
public record Student(
        @FakeDBId Long id,
        String name
) {
}
```

Create a repository from the template:

```java
import io.github.aiellolorenzo23.fakedb.core.FakeDBRepository;
import io.github.aiellolorenzo23.fakedb.core.FakeDBTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    private final FakeDBRepository<Student, Long> students;

    public StudentService(FakeDBTemplate fakeDBTemplate) {
        this.students = fakeDBTemplate.repository(Student.class, Long.class);
    }

    public Student save(Student student) {
        return students.save(student);
    }

    public List<Student> findAll() {
        return students.findAll();
    }
}
```

You can also create repositories by table name or by schema and table name:

```java
FakeDBRepository<Student, Long> students =
        fakeDBTemplate.repository("students", Student.class, Long.class);

FakeDBRepository<Student, Long> archivedStudents =
        fakeDBTemplate.repository("archive", "students", Student.class, Long.class);
```

## Repository API

`FakeDBRepository<T, ID>` exposes:

```java
List<T> findAll();
List<T> findAll(Sort sort);
Page<T> findAll(Pageable pageable);
List<T> findAll(Predicate<T> predicate);
Optional<T> findFirst(Predicate<T> predicate);
Optional<T> findById(ID id);
T save(T entity);
List<T> saveAll(Collection<T> entities);
boolean existsById(ID id);
long count();
void deleteById(ID id);
void delete(T entity);
void deleteAll();
```

`save` inserts a new row when the id does not exist and replaces the existing row when the id already exists.
The id cannot be `null` unless the id field is annotated with `@FakeDBGeneratedValue`.

`deleteById` raises `FakeDBEntityNotFoundException` when no row exists for the provided id.

Predicate queries are evaluated in memory after loading the table:

```java
List<Student> studentsNamedAda =
        students.findAll(student -> student.name().equals("Ada"));

Optional<Student> firstStudentStartingWithL =
        students.findFirst(student -> student.name().startsWith("L"));
```

`saveAll` performs a single load-modify-save operation for the whole collection.

Sorting and pagination use Spring Data Commons types and are evaluated in memory:

```java
List<Student> sorted =
        students.findAll(Sort.by("name").ascending());

Page<Student> page =
        students.findAll(PageRequest.of(0, 10, Sort.by("name").descending()));
```

Sort properties normally use Java entity field names. Fields annotated with `@FakeDBColumn` can also be
sorted by their JSON column name.

## Entity Mapping

FakeDB resolves the entity id in this order:

1. A field annotated with `@FakeDBId`.
2. A field named `id`.

If neither exists, FakeDB raises a `FakeDBConfigurationException`.

Generated ids are supported for writable id fields:

```java
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBGeneratedValue;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;

public class Student {

    @FakeDBId
    @FakeDBGeneratedValue
    private Long id;

    private String name;

    // getters and setters
}
```

The default strategy is `INCREMENT`, supported for `Long`, `Integer`, and numeric `String` ids.
Use UUID generation for `String` or `UUID` ids:

```java
@FakeDBId
@FakeDBGeneratedValue(strategy = FakeDBGeneratedValue.Strategy.UUID)
private String id;
```

Generated ids require a writable id field. Java records have final components, so they are not suitable
for generated ids unless the id is supplied manually.

Table mapping is optional:

```java
@FakeDBTable("students")
public class Student {
    @FakeDBId
    private Long id;
    private String name;
}
```

When `@FakeDBTable` is not present, FakeDB uses the entity simple class name as the table name.
When the annotation schema is blank, FakeDB uses `fakedb.default-schema`.

Use `@FakeDBColumn` when the JSON field name does not match the Java property name:

```java
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBColumn;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;

public record Product(
        @FakeDBId Long id,
        @FakeDBColumn("shop_name") String shopName,
        @FakeDBColumn("product_id") List<Long> productIds,
        @FakeDBColumn("free_ship") boolean freeShip
) {
}
```

You can also enable automatic snake_case mapping:

```yaml
fakedb:
  naming-strategy: snake_case
```

With this setting, `shopName` maps to `shop_name` and `freeShip` maps to `free_ship`.
Use `@FakeDBColumn` for exceptions such as `productIds` mapped to `product_id` instead of `product_ids`.

This maps to JSON like:

```json
{
  "id": 1,
  "shop_name": "Fake Shop",
  "product_id": [10, 20],
  "free_ship": true
}
```

## JSON Format

FakeDB stores data with a database root, schemas, and table arrays:

```json
{
  "version": "1.0.0",
  "database": "local_database",
  "schemas": {
    "main": {
      "students": [
        {
          "id": 1,
          "name": "Lorenzo"
        }
      ]
    }
  }
}
```

Tables are represented directly inside each schema. There is no extra `tables` wrapper.

## Notes and Limitations

- FakeDB is intended for local development, testing, and prototyping.
- Repository methods are synchronized inside each repository instance, but the JSON file is not a substitute for a transactional database.
- Write operations use an atomic load-modify-save store update inside the current application instance.
- Large datasets, concurrent application instances, relational constraints, query languages, migrations, indexes, and transactions are outside the current scope.
- Use a real database for production workloads or whenever durability, concurrency, and query performance matter.

## Running Tests

```bash
./mvnw test
```

On Windows:

```bash
./mvnw.cmd test
```
