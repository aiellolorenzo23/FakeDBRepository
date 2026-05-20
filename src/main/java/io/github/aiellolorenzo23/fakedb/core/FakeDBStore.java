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

    public synchronized FakeDBDatabase load() {
        Path path = resolvePath();

        try {
            if (Files.notExists(path)) {
                if (!properties.isAutoCreate()) {
                    throw new FakeDBConfigurationException("FakeDB file does not exist: " + path);
                }

                return createEmptyDatabase();
            }

            if (Files.size(path) == 0) {
                return createEmptyDatabase();
            }

            FakeDBDatabase database = objectMapper.readValue(path.toFile(), FakeDBDatabase.class);
            normalizeDatabase(database);
            return database;
        } catch (IOException ex) {
            throw new FakeDBException("Cannot load FakeDB file: " + path, ex);
        }
    }

    public synchronized void save(FakeDBDatabase database) {
        Path path = resolvePath();

        try {
            ensureParentDirectory(path);
            normalizeDatabase(database);

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

    private FakeDBDatabase createEmptyDatabase() {
        FakeDBDatabase database = new FakeDBDatabase();
        database.setVersion("1.0.0");
        database.setDatabase(properties.getDatabase());
        database.schema(properties.getDefaultSchema());
        return database;
    }

    private void normalizeDatabase(FakeDBDatabase database) {
        if (database.getVersion() == null || database.getVersion().isBlank()) {
            database.setVersion("1.0.0");
        }

        if (database.getDatabase() == null || database.getDatabase().isBlank()) {
            database.setDatabase(properties.getDatabase());
        }

        if (database.getSchemas() == null || database.getSchemas().isEmpty()) {
            database.schema(properties.getDefaultSchema());
        }
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