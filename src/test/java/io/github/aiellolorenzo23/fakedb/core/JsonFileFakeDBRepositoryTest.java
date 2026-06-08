package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBColumn;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBCreatedDate;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBGeneratedValue;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBLastModifiedDate;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBNotNull;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBReference;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBTransient;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBUnique;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void generatesIncrementIdWhenEntityIdIsNull() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<GeneratedStudent, Long> repository =
                template.repository("students", GeneratedStudent.class, Long.class);

        GeneratedStudent first = new GeneratedStudent(null, "Lorenzo");
        GeneratedStudent second = new GeneratedStudent(null, "Ada");

        repository.save(first);
        repository.save(second);

        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
        assertThat(repository.findById(2L))
                .hasValueSatisfying(student -> assertThat(student.getName()).isEqualTo("Ada"));
    }

    @Test
    void generatesIncrementIdsInsideSaveAllBatch() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<GeneratedStudent, Long> repository =
                template.repository("students", GeneratedStudent.class, Long.class);

        List<GeneratedStudent> students = repository.saveAll(List.of(
                new GeneratedStudent(null, "Lorenzo"),
                new GeneratedStudent(null, "Ada"),
                new GeneratedStudent(null, "Grace")
        ));

        assertThat(students)
                .extracting(GeneratedStudent::getId)
                .containsExactly(1L, 2L, 3L);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void generatesUuidStringIdWhenEntityIdIsNull() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<GeneratedToken, String> repository =
                template.repository("tokens", GeneratedToken.class, String.class);

        GeneratedToken token = repository.save(new GeneratedToken(null, "api"));

        assertThat(token.getId()).isNotBlank();
        assertThat(UUID.fromString(token.getId())).isNotNull();
        assertThat(repository.findById(token.getId()))
                .hasValueSatisfying(found -> assertThat(found.getName()).isEqualTo("api"));
    }

    @Test
    void rejectsNullFieldsAnnotatedWithFakeDBNotNull() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<ValidatedUser, Long> repository =
                template.repository("users", ValidatedUser.class, Long.class);

        assertThatThrownBy(() -> repository.save(new ValidatedUser(1L, null, "ada@test.com")))
                .isInstanceOf(FakeDBConstraintViolationException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void rejectsDuplicateFieldsAnnotatedWithFakeDBUnique() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<ValidatedUser, Long> repository =
                template.repository("users", ValidatedUser.class, Long.class);

        repository.save(new ValidatedUser(1L, "Ada", "ada@test.com"));

        assertThatThrownBy(() -> repository.save(new ValidatedUser(2L, "Ada Clone", "ada@test.com")))
                .isInstanceOf(FakeDBConstraintViolationException.class)
                .hasMessageContaining("already contains value");
    }

    @Test
    void rejectsDuplicateUniqueFieldsInsideSaveAllBatch() {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<ValidatedUser, Long> repository =
                template.repository("users", ValidatedUser.class, Long.class);

        assertThatThrownBy(() -> repository.saveAll(List.of(
                new ValidatedUser(1L, "Ada", "ada@test.com"),
                new ValidatedUser(2L, "Ada Clone", "ada@test.com")
        )))
                .isInstanceOf(FakeDBConstraintViolationException.class)
                .hasMessageContaining("already contains value");
    }

    @Test
    void appliesCreatedAndLastModifiedAuditDates() throws InterruptedException {
        FakeDBProperties properties = new FakeDBProperties();
        properties.setPath(tempDir.resolve("database.json").toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<ValidatedUser, Long> repository =
                template.repository("users", ValidatedUser.class, Long.class);

        ValidatedUser user = repository.save(new ValidatedUser(1L, "Ada", "ada@test.com"));
        LocalDateTime createdAt = user.getCreatedAt();
        LocalDateTime updatedAt = user.getUpdatedAt();

        Thread.sleep(5);
        user.setName("Ada Lovelace");
        repository.save(user);

        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getUpdatedAt()).isAfter(updatedAt);
        assertThat(repository.findById(1L))
                .hasValueSatisfying(found -> {
                    assertThat(found.getCreatedAt()).isEqualTo(createdAt);
                    assertThat(found.getUpdatedAt()).isAfter(updatedAt);
                });
    }

    @Test
    void resolvesReferencesOnlyWhenRequestedAndDoesNotPersistTransientFields() throws Exception {
        FakeDBProperties properties = new FakeDBProperties();
        Path dbPath = tempDir.resolve("database.json");
        properties.setPath(dbPath.toString());

        FakeDBTemplate template = new FakeDBTemplate(properties, new ObjectMapper().findAndRegisterModules());
        FakeDBRepository<RelationProduct, Long> productRepository =
                template.repository("products", RelationProduct.class, Long.class);
        FakeDBRepository<RelationUser, Long> userRepository =
                template.repository("users", RelationUser.class, Long.class);
        FakeDBRepository<RelationOrder, Long> orderRepository =
                template.repository("orders", RelationOrder.class, Long.class);

        productRepository.saveAll(List.of(
                new RelationProduct(1L, "Keyboard"),
                new RelationProduct(2L, "Mouse"),
                new RelationProduct(3L, "Monitor")
        ));
        userRepository.save(new RelationUser(10L, "ada"));

        RelationOrder order = new RelationOrder(100L, List.of(1L, 2L), 10L);
        order.setProducts(List.of(new RelationProduct(99L, "Should not be persisted")));
        order.setUser(new RelationUser(99L, "transient-user"));

        orderRepository.save(order);

        String json = Files.readString(dbPath);
        assertThat(json).doesNotContain("Should not be persisted");
        assertThat(json).doesNotContain("transient-user");

        assertThat(orderRepository.findById(100L))
                .hasValueSatisfying(rawOrder -> {
                    assertThat(rawOrder.getProducts()).isNull();
                    assertThat(rawOrder.getUser()).isNull();
                });

        assertThat(orderRepository.findById(100L, FakeDBFetchMode.RESOLVE_REFERENCES))
                .hasValueSatisfying(resolvedOrder -> {
                    assertThat(resolvedOrder.getProducts())
                            .extracting(RelationProduct::getName)
                            .containsExactly("Keyboard", "Mouse");
                    assertThat(resolvedOrder.getUser().getUsername()).isEqualTo("ada");
                });
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

    private static class GeneratedStudent {

        @FakeDBId
        @FakeDBGeneratedValue
        private Long id;

        private String name;

        public GeneratedStudent() {
        }

        private GeneratedStudent(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class GeneratedToken {

        @FakeDBId
        @FakeDBGeneratedValue(strategy = FakeDBGeneratedValue.Strategy.UUID)
        private String id;

        private String name;

        public GeneratedToken() {
        }

        private GeneratedToken(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class ValidatedUser {

        @FakeDBId
        private Long id;

        @FakeDBNotNull
        private String name;

        @FakeDBUnique
        private String email;

        @FakeDBCreatedDate
        private LocalDateTime createdAt;

        @FakeDBLastModifiedDate
        private LocalDateTime updatedAt;

        public ValidatedUser() {
        }

        private ValidatedUser(Long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    private static class RelationProduct {

        @FakeDBId
        private Long id;

        private String name;

        public RelationProduct() {
        }

        private RelationProduct(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static class RelationUser {

        @FakeDBId
        private Long id;

        private String username;

        public RelationUser() {
        }

        private RelationUser(Long id, String username) {
            this.id = id;
            this.username = username;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    private static class RelationOrder {

        @FakeDBId
        private Long id;

        @FakeDBColumn("product_id")
        private List<Long> productIds;

        @FakeDBColumn("user_id")
        private Long userId;

        @FakeDBTransient
        @FakeDBReference(table = "products", localField = "productIds", multiple = true)
        private List<RelationProduct> products;

        @FakeDBTransient
        @FakeDBReference(table = "users", localField = "userId")
        private RelationUser user;

        public RelationOrder() {
        }

        private RelationOrder(Long id, List<Long> productIds, Long userId) {
            this.id = id;
            this.productIds = productIds;
            this.userId = userId;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public List<Long> getProductIds() {
            return productIds;
        }

        public void setProductIds(List<Long> productIds) {
            this.productIds = productIds;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public List<RelationProduct> getProducts() {
            return products;
        }

        public void setProducts(List<RelationProduct> products) {
            this.products = products;
        }

        public RelationUser getUser() {
            return user;
        }

        public void setUser(RelationUser user) {
            this.user = user;
        }
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
