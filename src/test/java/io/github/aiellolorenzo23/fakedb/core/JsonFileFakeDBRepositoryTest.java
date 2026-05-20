package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aiellolorenzo23.fakedb.annotation.FakeDBId;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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

    private record Student(@FakeDBId Long id, String name) {
    }
}
