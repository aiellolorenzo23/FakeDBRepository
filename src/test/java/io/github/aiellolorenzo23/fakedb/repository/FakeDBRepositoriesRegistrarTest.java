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
                    repository.save(new AutoStudent(1L, "Lorenzo"));

                    assertThat(repository.findById(1L))
                            .hasValueSatisfying(student -> assertThat(student.name()).isEqualTo("Lorenzo"));
                });
    }

    @Configuration
    @EnableFakeDBRepositories(basePackageClasses = AutoStudentRepository.class)
    static class RepositoryConfiguration {
    }

    @FakeDBTable(value = "students", schema = "main")
    record AutoStudent(@FakeDBId Long id, String name) {
    }
}

interface AutoStudentRepository extends FakeDBRepository<FakeDBRepositoriesRegistrarTest.AutoStudent, Long> {
}
