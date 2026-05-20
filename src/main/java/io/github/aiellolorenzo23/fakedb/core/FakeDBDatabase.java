package io.github.aiellolorenzo23.fakedb.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeDBDatabase {

    private Map<String, FakeDBSchema> schemas = new LinkedHashMap<>();

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
