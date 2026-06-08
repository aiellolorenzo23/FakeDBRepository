package io.github.aiellolorenzo23.fakedb.repository;

import io.github.aiellolorenzo23.fakedb.annotation.EnableFakeDBRepositories;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBTable;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBAutoConfiguration;
import io.github.aiellolorenzo23.fakedb.core.FakeDBRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FakeDBRepositoriesRegistrarTest {

    @TempDir
    Path tempDir;

    @Test
    void registersFakeDBRepositoryInterfaceAsSpringBean() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FakeDBAutoConfiguration.class))
                .withUserConfiguration(RepositoryConfiguration.class)
                .withPropertyValues("fakedb.path=" + tempDir.resolve("database.json"))
                .run(context -> {
                    assertThat(context).hasSingleBean(AutoStudentRepository.class);

                    AutoStudentRepository repository = context.getBean(AutoStudentRepository.class);
                    repository.save(new AutoStudent(1L, "Lorenzo", 35, true, List.of("java", "spring")));

                    assertThat(repository.findById(1L))
                            .hasValueSatisfying(student -> assertThat(student.name()).isEqualTo("Lorenzo"));
                });
    }

    @Test
    void supportsDerivedQueryMethods() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FakeDBAutoConfiguration.class))
                .withUserConfiguration(RepositoryConfiguration.class)
                .withPropertyValues("fakedb.path=" + tempDir.resolve("database.json"))
                .run(context -> {
                    AutoStudentRepository repository = context.getBean(AutoStudentRepository.class);
                    repository.saveAll(List.of(
                            new AutoStudent(1L, "Lorenzo", 35, true, List.of("java", "spring")),
                            new AutoStudent(2L, "Ada", 28, true, List.of("math")),
                            new AutoStudent(3L, "Grace", 42, false, List.of("compiler", "navy")),
                            new AutoStudent(4L, "Linus", 55, false, List.of("linux", "git"))
                    ));

                    assertThat(repository.findByName("Ada"))
                            .extracting(AutoStudent::name)
                            .containsExactly("Ada");
                    assertThat(repository.findByNameAndActiveTrue("Lorenzo"))
                            .hasValueSatisfying(student -> assertThat(student.age()).isEqualTo(35));
                    assertThat(repository.findByAgeGreaterThan(40))
                            .extracting(AutoStudent::name)
                            .containsExactly("Grace", "Linus");
                    assertThat(repository.findByAgeLessThan(30))
                            .extracting(AutoStudent::name)
                            .containsExactly("Ada");
                    assertThat(repository.findByNameContaining("in"))
                            .extracting(AutoStudent::name)
                            .containsExactly("Linus");
                    assertThat(repository.findByTagsContaining("spring"))
                            .extracting(AutoStudent::name)
                            .containsExactly("Lorenzo");
                    assertThat(repository.findByNameIn(List.of("Ada", "Grace")))
                            .extracting(AutoStudent::name)
                            .containsExactly("Ada", "Grace");
                    assertThat(repository.existsByName("Grace")).isTrue();
                    assertThat(repository.deleteByActiveFalse()).isEqualTo(2L);
                    assertThat(repository.count()).isEqualTo(2);
                });
    }

    @Configuration
    @EnableFakeDBRepositories(basePackageClasses = AutoStudentRepository.class)
    static class RepositoryConfiguration {
    }

    @FakeDBTable(value = "students", schema = "main")
    record AutoStudent(@FakeDBId Long id, String name, Integer age, boolean active, List<String> tags) {
    }
}

interface AutoStudentRepository extends FakeDBRepository<FakeDBRepositoriesRegistrarTest.AutoStudent, Long> {

    List<FakeDBRepositoriesRegistrarTest.AutoStudent> findByName(String name);

    Optional<FakeDBRepositoriesRegistrarTest.AutoStudent> findByNameAndActiveTrue(String name);

    List<FakeDBRepositoriesRegistrarTest.AutoStudent> findByAgeGreaterThan(Integer age);

    List<FakeDBRepositoriesRegistrarTest.AutoStudent> findByAgeLessThan(Integer age);

    List<FakeDBRepositoriesRegistrarTest.AutoStudent> findByNameContaining(String fragment);

    List<FakeDBRepositoriesRegistrarTest.AutoStudent> findByTagsContaining(String tag);

    List<FakeDBRepositoriesRegistrarTest.AutoStudent> findByNameIn(List<String> names);

    boolean existsByName(String name);

    long deleteByActiveFalse();
}
