package io.github.aiellolorenzo23.fakedb.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.aiellolorenzo23.fakedb.autoconfigure.FakeDBProperties;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBConfigurationException;
import io.github.aiellolorenzo23.fakedb.exception.FakeDBException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FakeDBStore {

    private final FakeDBProperties properties;
    private final ObjectMapper objectMapper;

    public FakeDBStore(FakeDBProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public FakeDBDatabase load() {
        Path path = resolvePath();
        try {
            if (Files.notExists(path)) {
                if (!properties.isAutoCreate()) {
                    throw new FakeDBConfigurationException("FakeDB file does not exist: " + path);
                }
                createEmptyDatabase(path);
            }
            if (Files.size(path) == 0) {
                return new FakeDBDatabase();
            }
            return objectMapper.readValue(path.toFile(), FakeDBDatabase.class);
        } catch (IOException ex) {
            throw new FakeDBException("Cannot load FakeDB file: " + path, ex);
        }
    }

    public void save(FakeDBDatabase database) {
        Path path = resolvePath();
        try {
            ensureParentDirectory(path);
            if (properties.isBackupOnSave() && Files.exists(path)) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            ObjectWriter writer = properties.isPrettyPrint()
                    ? objectMapper.writerWithDefaultPrettyPrinter()
                    : objectMapper.writer();
            writer.writeValue(path.toFile(), database);
        } catch (IOException ex) {
            throw new FakeDBException("Cannot save FakeDB file: " + path, ex);
        }
    }

    private void createEmptyDatabase(Path path) throws IOException {
        ensureParentDirectory(path);
        save(new FakeDBDatabase());
    }

    private void ensureParentDirectory(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private Path resolvePath() {
        String configuredPath = properties.getPath();
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new FakeDBConfigurationException("FakeDB path is required. Configure fakedb.path");
        }
        return Path.of(configuredPath);
    }
}
