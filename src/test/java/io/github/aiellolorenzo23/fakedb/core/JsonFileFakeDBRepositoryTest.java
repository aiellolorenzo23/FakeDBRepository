package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBColumn;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonFileFakeDBRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndFindsEntitiesById() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Student, Long> repository = template.repository("students", Student.class, Long.class);

        repository.save(new Student(1L, "Lorenzo"));
        repository.save(new Student(2L, "Ada"));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(1L)).hasValueSatisfying(student -> assertThat(student.name()).isEqualTo("Lorenzo"));
        assertThat(repository.existsById(2L)).isTrue();
    }

    @Test
    void savesUsingFakeDBStudioCompatibleFormat() throws Exception {
        FakeDBProperties properties = new FakeDBProperties();
        Path dbPath = tempDir.resolve("database.json");

        properties.setPath(dbPath.toString());
        properties.setDatabase("test_database");
        properties.setDefaultSchema("main");

        FakeDBTemplate template = new FakeDBTemplate(
                properties,
                new ObjectMapper().findAndRegisterModules()
        );

        FakeDBRepository<Student, Long> repository =
                template.repository("students", Student.class, Long.class);

        repository.save(new Student(1L, "Lorenzo"));

        String json = Files.readString(dbPath);

        assertThat(json).contains("\"version\"");
        assertThat(json).contains("\"database\"");
        assertThat(json).contains("\"test_database\"");
        assertThat(json).contains("\"schemas\"");
        assertThat(json).contains("\"main\"");
        assertThat(json).contains("\"students\"");
        assertThat(json).doesNotContain("\"tables\"");
    }

    @Test
    void createsDatabaseFileWhenMissingAndAutoCreateIsEnabled() {
        FakeDBProperties properties = new FakeDBProperties();
        Path dbPath = tempDir.resolve("missing-database.json");

        properties.setPath(dbPath.toString());
        properties.setDatabase("test_database");
        properties.setDefaultSchema("main");
        properties.setAutoCreate(true);

        FakeDBTemplate template = new FakeDBTemplate(
                properties,
                new ObjectMapper().findAndRegisterModules()
        );

        FakeDBRepository<Student, Long> repository =
                template.repository("students", Student.class, Long.class);

        repository.save(new Student(1L, "Lorenzo"));

        assertThat(dbPath).exists();
    }

    @Test
    void saveAllLoadsAndSavesOnlyOnce() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CountingFakeDBStore store = new CountingFakeDBStore(properties, objectMapper);
        JsonFileFakeDBRepository<Student, Long> repository = new JsonFileFakeDBRepository<>(
                store,
                objectMapper,
                "main",
                "students",
                Student.class,
                Long.class
        );

        repository.saveAll(List.of(
                new Student(1L, "Lorenzo"),
                new Student(2L, "Ada"),
                new Student(3L, "Grace")
        ));

        assertThat(store.loadCount).isEqualTo(1);
        assertThat(store.saveCount).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void findsEntitiesByPredicate() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Student, Long> repository = template.repository("students", Student.class, Long.class);

        repository.saveAll(List.of(
                new Student(1L, "Lorenzo"),
                new Student(2L, "Ada"),
                new Student(3L, "Luca")
        ));

        assertThat(repository.findAll(student -> student.name().startsWith("L")))
                .extracting(Student::name)
                .containsExactly("Lorenzo", "Luca");
        assertThat(repository.findFirst(student -> student.name().equals("Ada")))
                .hasValueSatisfying(student -> assertThat(student.id()).isEqualTo(2L));
    }

    @Test
    void mapsEntityFieldsUsingFakeDBColumn() throws Exception {
        FakeDBProperties properties = new FakeDBProperties();
        Path dbPath = tempDir.resolve("database.json");
        properties.setPath(dbPath.toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Product, Long> repository = template.repository("products", Product.class, Long.class);

        repository.save(new Product(1L, "Fake Shop", List.of(10L, 20L), true));

        String json = Files.readString(dbPath);

        assertThat(json).contains("\"shop_name\"");
        assertThat(json).contains("\"product_id\"");
        assertThat(json).contains("\"free_ship\"");
        assertThat(json).doesNotContain("\"shopName\"");
        assertThat(json).doesNotContain("\"productIds\"");
        assertThat(json).doesNotContain("\"freeShip\"");
        assertThat(repository.findById(1L))
                .hasValueSatisfying(product -> {
                    assertThat(product.shopName()).isEqualTo("Fake Shop");
                    assertThat(product.productIds()).containsExactly(10L, 20L);
                    assertThat(product.freeShip()).isTrue();
                });
    }

    @Test
    void mapsCamelCaseFieldsUsingSnakeCaseNamingStrategy() throws Exception {
        FakeDBProperties properties = new FakeDBProperties();
        Path dbPath = tempDir.resolve("database.json");
        properties.setPath(dbPath.toString());
        properties.setNamingStrategy(FakeDBProperties.NamingStrategy.SNAKE_CASE);

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<SupplierProduct, Long> repository =
                template.repository("products", SupplierProduct.class, Long.class);

        repository.save(new SupplierProduct(1L, "Fake Shop", true, List.of(10L, 20L)));

        String json = Files.readString(dbPath);

        assertThat(json).contains("\"shop_name\"");
        assertThat(json).contains("\"free_ship\"");
        assertThat(json).contains("\"product_id\"");
        assertThat(json).doesNotContain("\"shopName\"");
        assertThat(json).doesNotContain("\"freeShip\"");
        assertThat(json).doesNotContain("\"productIds\"");
        assertThat(json).doesNotContain("\"product_ids\"");
        assertThat(repository.findById(1L))
                .hasValueSatisfying(product -> {
                    assertThat(product.shopName()).isEqualTo("Fake Shop");
                    assertThat(product.freeShip()).isTrue();
                    assertThat(product.productIds()).containsExactly(10L, 20L);
                });
    }

    @Test
    void ignoresUnknownJsonPropertiesWhenConfigured() throws Exception {
        FakeDBProperties properties = new FakeDBProperties();
        Path dbPath = tempDir.resolve("database.json");
        properties.setPath(dbPath.toString());
        properties.setFailOnUnknownProperties(false);

        Files.writeString(dbPath, """
                {
                  "version": "1.0.0",
                  "database": "test_database",
                  "schemas": {
                    "main": {
                      "students": [
                        {
                          "id": 1,
                          "name": "Lorenzo",
                          "extra_field": "ignored"
                        }
                      ]
                    }
                  }
                }
                """);

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Student, Long> repository = template.repository("students", Student.class, Long.class);

        assertThat(repository.findById(1L))
                .hasValueSatisfying(student -> assertThat(student.name()).isEqualTo("Lorenzo"));
    }

    @Test
    void findsAllSortedByEntityField() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Student, Long> repository = template.repository("students", Student.class, Long.class);

        repository.saveAll(List.of(
                new Student(1L, "Lorenzo"),
                new Student(2L, "Ada"),
                new Student(3L, "Grace")
        ));

        assertThat(repository.findAll(Sort.by("name").ascending()))
                .extracting(Student::name)
                .containsExactly("Ada", "Grace", "Lorenzo");
        assertThat(repository.findAll(Sort.by("name").descending()))
                .extracting(Student::name)
                .containsExactly("Lorenzo", "Grace", "Ada");
    }

    @Test
    void findsAllSortedByFakeDBColumnName() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Product, Long> repository = template.repository("products", Product.class, Long.class);

        repository.saveAll(List.of(
                new Product(1L, "Zeta Shop", List.of(10L), true),
                new Product(2L, "Alpha Shop", List.of(20L), false),
                new Product(3L, "Beta Shop", List.of(30L), true)
        ));

        assertThat(repository.findAll(Sort.by("shop_name").ascending()))
                .extracting(Product::shopName)
                .containsExactly("Alpha Shop", "Beta Shop", "Zeta Shop");
    }

    @Test
    void findsAllPagedAndSorted() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<Student, Long> repository = template.repository("students", Student.class, Long.class);

        repository.saveAll(List.of(
                new Student(1L, "Lorenzo"),
                new Student(2L, "Ada"),
                new Student(3L, "Grace"),
                new Student(4L, "Linus")
        ));

        Page<Student> page = repository.findAll(PageRequest.of(1, 2, Sort.by("name").ascending()));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(Student::name)
                .containsExactly("Linus", "Lorenzo");
    }

    private record Student(@FakeDBId Long id, String name) {
    }

    private record Product(
            @FakeDBId Long id,
            @FakeDBColumn("shop_name") String shopName,
            @FakeDBColumn("product_id") List<Long> productIds,
            @FakeDBColumn("free_ship") boolean freeShip
    ) {
    }

    private record SupplierProduct(
            @FakeDBId Long id,
            String shopName,
            boolean freeShip,
            @FakeDBColumn("product_id") List<Long> productIds
    ) {
    }

    private static class CountingFakeDBStore extends FakeDBStore {

        private int loadCount;

        private int saveCount;

        private CountingFakeDBStore(FakeDBProperties properties, ObjectMapper objectMapper) {
            super(properties, objectMapper);
        }

        @Override
        public synchronized FakeDBDatabase load() {
            loadCount++;
            return super.load();
        }

        @Override
        public synchronized void save(FakeDBDatabase database) {
            saveCount++;
            super.save(database);
        }
    }
}
