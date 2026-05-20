package io.github.aiellolorenzo23.fakedb.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeDBDatabase {

    private String version = "1.0.0";

    private String database = "default";

    private Map<String, FakeDBSchema> schemas = new LinkedHashMap<>();

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null || version.isBlank() ? "1.0.0" : version;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database == null || database.isBlank() ? "default" : database;
    }

    public Map<String, FakeDBSchema> getSchemas() {
        return schemas;
    }

    public void setSchemas(Map<String, FakeDBSchema> schemas) {
        this.schemas = schemas == null ? new LinkedHashMap<>() : schemas;
    }

    public FakeDBSchema schema(String name) {
        return schemas.computeIfAbsent(name, ignored -> new FakeDBSchema());
    }

    public List<Object> table(String schema, String table) {
        return schema(schema).table(table);
    }
}